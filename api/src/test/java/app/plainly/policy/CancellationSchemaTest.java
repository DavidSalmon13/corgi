package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC4, AC7, AC8, T1
/** The V9 schema: a cancelled policy has a cancel day inside its term, and a CREDIT line traces to its cover. */
@IntegrationTest
class CancellationSchemaTest {

    @Autowired JdbcClient jdbc;

    long policyId;
    long cancelTxId;
    long coverageId;

    @BeforeEach
    void anActivePolicyWithACancelTransaction() {
        long customerId = jdbc.sql("""
                insert into customers (legal_name, business_description, owner_name, owner_email)
                values ('Cancel Schema LLC', 'a test shop', 'Test Owner', ?) returning id""")
                .param(TestData.uniqueEmail("owner")).query(Long.class).single();
        policyId = jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date)
                values ('TEST-' || nextval('policy_number_seq'), ?, date '2026-09-01', date '2027-09-01') returning id""")
                .param(customerId).query(Long.class).single();
        coverageId = jdbc.sql("""
                insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                values (?, 'BASE', 120000, date '2026-09-01', date '2027-09-01') returning id""")
                .param(policyId).query(Long.class).single();
        cancelTxId = jdbc.sql("""
                insert into policy_transactions (policy_id, type, effective_date, reason)
                values (?, 'CANCEL', date '2026-12-01', 'Closing the shop') returning id""")
                .param(policyId).query(Long.class).single();
    }

    @Test
    void aCancelledPolicyKeepsItsTermAndRecordsTheCancelDay() {
        cancel("2026-12-01");

        assertThat(jdbc.sql("select status, cancelled_on::text, end_date::text from policies where id = ?")
                .param(policyId).query((rs, n) -> rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3))
                .single()).isEqualTo("CANCELLED 2026-12-01 2027-09-01");
    }

    @Test
    void cancellingOnTheStartDayIsAllowed() {
        assertThatCode(() -> cancel("2026-09-01")).doesNotThrowAnyException();
    }

    @Test
    void aCancelledPolicyMustHaveACancelDayAndAnActiveOneMustNot() {
        assertThatThrownBy(() -> jdbc.sql("update policies set status = 'CANCELLED' where id = ?")
                .param(policyId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("policies_cancelled_has_day");
        assertThatThrownBy(() -> jdbc.sql("update policies set cancelled_on = date '2026-12-01' where id = ?")
                .param(policyId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("policies_cancelled_has_day");
    }

    @Test
    void theCancelDayMustFallInsideTheTerm() {
        assertThatThrownBy(() -> cancel("2026-08-31"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("policies_cancelled_in_term");
        assertThatThrownBy(() -> cancel("2027-09-01"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("policies_cancelled_in_term");
    }

    @Test
    void aCreditForUnusedDaysIsAccepted() {
        assertThatCode(() -> credit(-90_082, cancelTxId, coverageId, 274)).doesNotThrowAnyException();
    }

    @Test
    void aCreditMustBeNegativeAndTraceToItsTransactionCoverAndArithmetic() {
        assertThatThrownBy(() -> credit(90_082, cancelTxId, coverageId, 274))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ledger_credit_traced");
        assertThatThrownBy(() -> credit(-90_082, null, coverageId, 274))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ledger_credit_traced");
        assertThatThrownBy(() -> credit(-90_082, cancelTxId, null, 274))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ledger_credit_traced");
        assertThatThrownBy(() -> credit(-90_082, cancelTxId, coverageId, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ledger_credit_traced");
    }

    @Test
    void unknownEntryTypesAreStillRejected() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into ledger_entries (policy_id, policy_transaction_id, entry_type, entry_date, description, amount_cents)
                values (?, ?, 'WRITE_OFF', date '2026-12-01', 'nope', -100)""").params(policyId, cancelTxId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ledger_entries_entry_type_check");
    }

    void cancel(String day) {
        jdbc.sql("update policies set status = 'CANCELLED', cancelled_on = cast(? as date) where id = ?")
                .params(day, policyId).update();
    }

    void credit(long amountCents, Long txId, Long coverId, Integer days) {
        jdbc.sql("""
                insert into ledger_entries (policy_id, policy_transaction_id, coverage_id, entry_type, entry_date,
                                            description, amount_cents, calc_annual_cents, calc_days, calc_term_days)
                values (?, ?, ?, 'CREDIT', date '2026-12-01', 'Base cover, unused days refunded', ?,
                        case when cast(? as integer) is null then null else 120000 end, ?,
                        case when cast(? as integer) is null then null else 365 end)""")
                .params(policyId, txId, coverId, amountCents, days, days, days).update();
    }
}
