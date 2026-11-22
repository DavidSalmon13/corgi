package app.plainly.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC8, AC10, T4
/** The V8 schema and the SQL on {@code refunds}: a refund settles once, and a REFUND line must trace to it. */
@IntegrationTest
class RefundRepositoryTest {

    @Autowired JdbcClient jdbc;
    @Autowired RefundRepository refunds;

    long policyId;
    long ptxId;
    long paymentId;

    @BeforeEach
    void policyWithOnePaidPayment() {
        long customerId = jdbc.sql("""
                insert into customers (legal_name, business_description, owner_name, owner_email)
                values ('Refund Repo LLC', 'a test shop', 'Test Owner', ?) returning id""")
                .param(TestData.uniqueEmail("owner")).query(Long.class).single();
        policyId = jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date)
                values ('TEST-' || nextval('policy_number_seq'), ?, date '2026-09-01', date '2027-09-01') returning id""")
                .param(customerId).query(Long.class).single();
        ptxId = jdbc.sql("""
                insert into policy_transactions (policy_id, type, effective_date, reason)
                values (?, 'CORRECT', date '2026-10-22', 'test') returning id""")
                .param(policyId).query(Long.class).single();
        paymentId = jdbc.sql("""
                insert into payments (policy_id, stripe_payment_intent_id, amount_cents, status, card_last4)
                values (?, 'pi_repo_' || nextval('payment_seq'), 64200, 'SUCCEEDED', '4242') returning id""")
                .param(policyId).query(Long.class).single();
    }

    @Test
    void aNewRefundIsPendingOnItsFirstAttempt() {
        RefundRepository.Inserted r = refunds.insert(policyId, paymentId, ptxId, 1_400);

        assertThat(r.displayId()).matches("RF-\\d{4,}");
        assertThat(refunds.findByPolicy(policyId)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("PENDING");
            assertThat(l.attempt()).isEqualTo(1);
            assertThat(l.amountCents()).isEqualTo(1_400);
            assertThat(l.cardLast4()).isEqualTo("4242");
            assertThat(l.stripeRefundId()).isNull();
        });
        assertThat(refunds.lockById(r.id())).get().satisfies(l -> assertThat(l.intentId()).startsWith("pi_repo_"));
    }

    @Test
    void succeedsOnlyOnceAndFailedRefundsDontCountAgainstThePayment() {
        long a = refunds.insert(policyId, paymentId, ptxId, 1_000).id();
        long b = refunds.insert(policyId, paymentId, ptxId, 400).id();

        assertThat(refunds.markSucceeded(a)).isTrue();
        assertThat(refunds.markSucceeded(a)).isFalse();
        assertThat(refunds.markFailed(a, "too late")).isFalse();
        assertThat(refunds.markFailed(b, "The card can't take refunds.")).isTrue();

        assertThat(refunds.refundedPerPayment(policyId))
                .containsExactly(new RefundRepository.Refunded(paymentId, 1_000));
    }

    @Test
    void onlyAFailedRefundStartsANewAttempt() {
        long id = refunds.insert(policyId, paymentId, ptxId, 1_400).id();
        refunds.markSent(id, "re_first");

        assertThat(refunds.startNextAttempt(id)).isEmpty();
        refunds.markFailed(id, "declined");
        assertThat(refunds.startNextAttempt(id)).contains(2);
        assertThat(refunds.lockById(id)).get().satisfies(l -> assertThat(l.status()).isEqualTo("PENDING"));
    }

    @Test
    void aRefundLineMustTraceToItsRefundAndEvent() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into ledger_entries (policy_id, entry_type, entry_date, description, amount_cents)
                values (?, 'REFUND', date '2026-10-22', 'Refund', 1400)""").param(policyId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger_refund_traced");
    }

    @Test
    void aCoverRowIsReplacedAtMostOnce() {
        long oldId = coverage("2026-10-15");
        long first = coverage("2026-10-08");
        long second = coverage("2026-10-01");
        jdbc.sql("update coverages set replaced_by_coverage_id = ? where id = ?").params(first, oldId).update();

        assertThatThrownBy(() -> jdbc.sql("update coverages set replaced_by_coverage_id = ? where id = ?")
                .params(first, second).update())
                .isInstanceOf(DataAccessException.class);
    }

    long coverage(String from) {
        return jdbc.sql("""
                insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                values (?, 'PRODUCT_LIABILITY', 73000, cast(? as date), date '2027-09-01') returning id""")
                .params(policyId, from).query(Long.class).single();
    }
}
