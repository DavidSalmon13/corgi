package app.plainly.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC6, AC9, T7
@IntegrationTest
class LedgerTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate SEP_1_NEXT = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    @Autowired JdbcClient jdbc;
    @Autowired LedgerPostingService poster;
    @Autowired LedgerQueryService queries;

    long policyId;
    long issueTx;

    @BeforeEach
    void policy() {
        policyId = TestData.policy(jdbc, SEP_1, SEP_1_NEXT);
        issueTx = TestData.transaction(jdbc, policyId, "ISSUE", SEP_1);
    }

    @Test
    void postedChargesComeBackOldestFirstWithArithmeticAndRunningBalance() {
        var base = ProRata.charge(120_000, SEP_1, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        var extra = ProRata.charge(73_000, OCT_15, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        List<PostedEntry> posted = poster.post(policyId, issueTx, List.of(
                NewEntry.charge(SEP_1, "Base cover, full year", null, base, SEP_1, SEP_1_NEXT),
                NewEntry.charge(SEP_1, "Extra cover from Oct 15", null, extra, OCT_15, SEP_1_NEXT)));

        assertThat(posted).hasSize(2);
        assertThat(posted).allSatisfy(p -> assertThat(p.displayId()).matches("LE-\\d{4,}"));

        List<LedgerLine> lines = queries.lines(policyId);
        assertThat(lines).extracting(LedgerLine::displayId).containsExactly(posted.get(0).displayId(), posted.get(1).displayId());

        LedgerLine first = lines.get(0);
        assertThat(first.type()).isEqualTo(EntryType.CHARGE);
        assertThat(first.amountCents()).isEqualTo(120_000);
        assertThat(first.calcAnnualCents()).isEqualTo(120_000);
        assertThat(first.calcDays()).isEqualTo(365);
        assertThat(first.calcTermDays()).isEqualTo(365);
        assertThat(first.coverFrom()).isEqualTo(SEP_1);
        assertThat(first.coverTo()).isEqualTo(SEP_1_NEXT);
        assertThat(first.policyTransactionDisplayId()).matches("PTX-\\d{4,}");
        assertThat(first.balanceAfterCents()).isEqualTo(120_000);

        LedgerLine second = lines.get(1);
        assertThat(second.amountCents()).isEqualTo(64_200);
        assertThat(second.calcDays()).isEqualTo(321);
        assertThat(second.balanceAfterCents()).isEqualTo(184_200);

        assertThat(queries.balance(policyId)).isEqualTo(184_200);
    }

    @Test
    void aReversalLinksBothWaysAndNetsTheBalance() {
        var extra = ProRata.charge(73_000, OCT_15, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        PostedEntry original = poster.post(policyId, issueTx, List.of(
                NewEntry.charge(SEP_1, "Extra cover from Oct 15", null, extra, OCT_15, SEP_1_NEXT))).getFirst();
        long correctTx = TestData.transaction(jdbc, policyId, "CORRECT", OCT_15);
        poster.post(policyId, correctTx, List.of(
                NewEntry.reversal(LocalDate.of(2026, 11, 5), "Extra cover from Oct 15 reversed", original.id(), -64_200)));

        List<LedgerLine> lines = queries.lines(policyId);
        assertThat(lines.get(0).reversedByDisplayId()).isEqualTo(lines.get(1).displayId());
        assertThat(lines.get(1).reversesDisplayId()).isEqualTo(original.displayId());
        assertThat(lines.get(1).type()).isEqualTo(EntryType.REVERSAL);
        assertThat(lines.get(1).balanceAfterCents()).isZero();
        assertThat(queries.balance(policyId)).isZero();
    }

    @Test
    void balancesForSeveralPoliciesIncludeOnesWithNoLines() {
        long empty = TestData.policy(jdbc, SEP_1, SEP_1_NEXT);
        poster.post(policyId, issueTx, List.of(NewEntry.charge(SEP_1, "Base cover, full year", null,
                ProRata.charge(120_000, SEP_1, SEP_1_NEXT, SEP_1, SEP_1_NEXT), SEP_1, SEP_1_NEXT)));

        Map<Long, Long> balances = queries.balances(List.of(policyId, empty));
        assertThat(balances).containsEntry(policyId, 120_000L).containsEntry(empty, 0L);
        assertThat(queries.balance(empty)).isZero();
        assertThat(queries.lines(empty)).isEmpty();
    }

    @Test
    void chargesAndReversalsMustComeFromAPolicyTransaction() {
        var c = ProRata.charge(120_000, SEP_1, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        assertThatThrownBy(() -> poster.post(policyId, null,
                List.of(NewEntry.charge(SEP_1, "Base cover, full year", null, c, SEP_1, SEP_1_NEXT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(queries.lines(policyId)).isEmpty();
    }

    @Test
    void aReversalMustExactlyMirrorTheLineItUndoes() {
        var extra = ProRata.charge(73_000, OCT_15, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        PostedEntry original = poster.post(policyId, issueTx, List.of(
                NewEntry.charge(SEP_1, "Extra cover from Oct 15", null, extra, OCT_15, SEP_1_NEXT))).getFirst();
        long correctTx = TestData.transaction(jdbc, policyId, "CORRECT", OCT_15);
        assertThatThrownBy(() -> poster.post(policyId, correctTx, List.of(
                NewEntry.reversal(LocalDate.of(2026, 11, 5), "wrong amount", original.id(), -64_100))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(queries.lines(policyId)).hasSize(1);
    }

    // --- collect-premium: payment lines (covers AC5, AC6, T6) ---

    private long payment(long policy, String intentId) {
        return jdbc.sql("""
                insert into payments (policy_id, stripe_payment_intent_id, amount_cents) values (?, ?, 120000) returning id""")
                .params(policy, intentId).query(Long.class).single();
    }

    @Test
    void aPaymentLineIsACreditThatTracesToItsPaymentAndStripeEvent() {
        long paymentId = payment(policyId, "pi_ledger_" + policyId);
        poster.post(policyId, null, List.of(NewEntry.payment(SEP_1, "Payment, card ending 4242", -120_000, paymentId, "evt_ledger_" + policyId)));

        Map<String, Object> row = jdbc.sql("select entry_type, amount_cents, payment_id, stripe_event_id, policy_transaction_id from ledger_entries where policy_id = ?")
                .param(policyId).query().singleRow();
        assertThat(row).containsEntry("entry_type", "PAYMENT").containsEntry("amount_cents", -120_000L)
                .containsEntry("payment_id", paymentId).containsEntry("stripe_event_id", "evt_ledger_" + policyId);
        assertThat(row.get("policy_transaction_id")).isNull();
    }

    @Test
    void aDeclinedAttemptIsAZeroLine() {
        long paymentId = payment(policyId, "pi_ledger_zero_" + policyId);
        poster.post(policyId, null, List.of(NewEntry.payment(SEP_1, "Payment attempt, card ending 0002", 0, paymentId, "evt_zero_" + policyId)));

        assertThat(queries.balance(policyId)).isZero();
    }

    @Test
    void aPaymentCantChargeTheCustomer() {
        long paymentId = payment(policyId, "pi_ledger_pos_" + policyId);
        assertThatThrownBy(() -> NewEntry.payment(SEP_1, "Payment", 500, paymentId, "evt_pos_" + policyId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPaymentLineMustNameItsPaymentAndEvent() {
        long paymentId = payment(policyId, "pi_ledger_untraced_" + policyId);
        assertThatThrownBy(() -> poster.post(policyId, null, List.of(NewEntry.payment(SEP_1, "Payment", -1, paymentId, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPaymentLineMustBelongToTheSamePolicyAsItsPayment() {
        long other = TestData.policy(jdbc, SEP_1, SEP_1_NEXT);
        long othersPayment = payment(other, "pi_ledger_other_" + other);
        assertThatThrownBy(() -> poster.post(policyId, null,
                List.of(NewEntry.payment(SEP_1, "Payment", -1, othersPayment, "evt_other_" + other))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- correct-effective-date: undo keeps its cover, the live charge, refund lines (covers AC8, AC10, AC12, T5) ---

    private long coverage(LocalDate from) {
        return jdbc.sql("""
                insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                values (?, 'PRODUCT_LIABILITY', 73000, ?, ?) returning id""")
                .params(policyId, from, SEP_1_NEXT).query(Long.class).single();
    }

    private long refund(long policy, long paymentId, long cents) {
        long ptx = TestData.transaction(jdbc, policy, "CORRECT", OCT_15);
        jdbc.sql("update payments set status = 'SUCCEEDED', card_last4 = '4242' where id = ?").param(paymentId).update();
        return jdbc.sql("""
                insert into refunds (policy_id, payment_id, policy_transaction_id, amount_cents, stripe_refund_id)
                values (?, ?, ?, ?, 're_ledger_' || nextval('refund_seq')) returning id""")
                .params(policy, paymentId, ptx, cents).query(Long.class).single();
    }

    @Test
    void anUndoKeepsTheCoverOfTheLineItUndoes() {
        long cover = coverage(OCT_15);
        var extra = ProRata.charge(73_000, OCT_15, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        PostedEntry original = poster.post(policyId, issueTx, List.of(
                NewEntry.charge(SEP_1, "Extra cover from Oct 15", cover, extra, OCT_15, SEP_1_NEXT))).getFirst();
        long correctTx = TestData.transaction(jdbc, policyId, "CORRECT", OCT_15);
        poster.post(policyId, correctTx, List.of(
                NewEntry.reversal(LocalDate.of(2026, 11, 5), "Undo", original.id(), -64_200)));

        assertThat(queries.lines(policyId).get(1).coverageId()).isEqualTo(cover);
    }

    @Test
    void theLiveChargeIsTheOneNobodyHasUndone() {
        long oldCover = coverage(OCT_15);
        var extra = ProRata.charge(73_000, OCT_15, SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        PostedEntry original = poster.post(policyId, issueTx, List.of(
                NewEntry.charge(SEP_1, "Extra cover from Oct 15", oldCover, extra, OCT_15, SEP_1_NEXT))).getFirst();

        assertThat(queries.liveChargeFor(oldCover)).get().satisfies(c -> {
            assertThat(c.entryId()).isEqualTo(original.id());
            assertThat(c.displayId()).isEqualTo(original.displayId());
            assertThat(c.amountCents()).isEqualTo(64_200);
            assertThat(c.calc()).isEqualTo(extra);
            assertThat(c.coverFrom()).isEqualTo(OCT_15);
        });

        long correctTx = TestData.transaction(jdbc, policyId, "CORRECT", OCT_15);
        long newCover = coverage(LocalDate.of(2026, 10, 8));
        var rebill = ProRata.charge(73_000, LocalDate.of(2026, 10, 8), SEP_1_NEXT, SEP_1, SEP_1_NEXT);
        poster.post(policyId, correctTx, List.of(
                NewEntry.reversal(LocalDate.of(2026, 11, 5), "Undo", original.id(), -64_200),
                NewEntry.charge(LocalDate.of(2026, 11, 5), "Extra cover from Oct 8", newCover, rebill,
                        LocalDate.of(2026, 10, 8), SEP_1_NEXT)));

        assertThat(queries.liveChargeFor(oldCover)).isEmpty();
        assertThat(queries.liveChargeFor(newCover)).get().satisfies(c -> assertThat(c.amountCents()).isEqualTo(65_600));
    }

    @Test
    void aRefundLineSendsMoneyBackAndTracesToItsRefundAndEvent() {
        long paymentId = payment(policyId, "pi_ledger_refund_" + policyId);
        long refundId = refund(policyId, paymentId, 1_400);
        poster.post(policyId, null, List.of(
                NewEntry.refund(SEP_1, "Refund to card ending 4242", 1_400, refundId, "evt_refund_" + policyId)));

        LedgerLine line = queries.lines(policyId).getFirst();
        assertThat(line.type()).isEqualTo(EntryType.REFUND);
        assertThat(line.amountCents()).isEqualTo(1_400);
        assertThat(line.stripeEventId()).isEqualTo("evt_refund_" + policyId);
        assertThat(line.refundDisplayId()).matches("RF-\\d{4,}");
        assertThat(line.stripeRefundId()).startsWith("re_ledger_");
    }

    @Test
    void aRefundLineMustMatchItsRefund() {
        long paymentId = payment(policyId, "pi_ledger_refund_bad_" + policyId);
        long refundId = refund(policyId, paymentId, 1_400);
        long other = TestData.policy(jdbc, SEP_1, SEP_1_NEXT);

        assertThatThrownBy(() -> NewEntry.refund(SEP_1, "Refund", -1_400, refundId, "evt_neg_" + policyId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> poster.post(policyId, null,
                List.of(NewEntry.refund(SEP_1, "Refund", 1_400, refundId, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> poster.post(policyId, null,
                List.of(NewEntry.refund(SEP_1, "Refund", 1_300, refundId, "evt_amt_" + policyId))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> poster.post(other, null,
                List.of(NewEntry.refund(SEP_1, "Refund", 1_400, refundId, "evt_other_r_" + policyId))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(queries.lines(policyId)).isEmpty();
    }
}
