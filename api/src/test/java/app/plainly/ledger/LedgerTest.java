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
    @Autowired LedgerPoster poster;
    @Autowired LedgerQueries queries;

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
}
