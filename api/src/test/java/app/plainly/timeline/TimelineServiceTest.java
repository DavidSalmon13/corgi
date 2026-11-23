package app.plainly.timeline;

import static app.plainly.CorrectionTestData.OCT_15;
import static app.plainly.CorrectionTestData.OCT_22;
import static app.plainly.CorrectionTestData.OCT_8;
import static app.plainly.CorrectionTestData.REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.CorrectionTestData;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.common.NotFoundException;
import app.plainly.ledger.EntryType;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CancelPolicyCommand;
import app.plainly.policy.CancelPolicyService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.CoverKind;
import app.plainly.policy.IssuePolicyService;
import app.plainly.statement.Statement;
import app.plainly.statement.StatementService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P2 Cover timeline, built from real postings. Worked numbers: a Sep 1, 2026 → Sep 1, 2027 policy (365 days), base
 * cover $1,200.00, extra cover $730.00 a year ($2.00 a day). From Oct 15 the extra cover is 321 days · $642.00; moved
 * to Oct 8 it is 328 days · $656.00. Cancelled on Dec 1: base keeps 91 days · $299.18 ($1,200.00 less $900.82 back),
 * extra keeps 54 days · $108.00 ($656.00 less $548.00 back).
 */
// covers AC2, AC3, AC4, AC5, AC7, AC9, T1
@IntegrationTest
@AutoConfigureMockMvc
class TimelineServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate POLICY_END = LocalDate.of(2027, 9, 1);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest

    @Autowired TimelineService service;
    @Autowired StatementService statements;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;
    @Autowired CancelPolicyService canceller;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    long withExtraFromOct15(String business) throws Exception {
        return CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, business).owner().policyId();
    }

    CorrectCoverageService.Corrected correct(long policyId, LocalDate start) {
        return corrector.correct(policyId, new CorrectCoverageCommand(start, REASON), CorrectionTestData.brokerId(jdbc));
    }

    void cancel(long policyId) {
        canceller.cancel(policyId, new CancelPolicyCommand(DEC_1, "The bakery closed for good."),
                CorrectionTestData.brokerId(jdbc));
    }

    static Timeline.Cover cover(Timeline t, CoverKind kind) {
        return t.covers().stream().filter(c -> c.kind() == kind).findFirst().orElseThrow();
    }

    // --- the term and the bands (AC2, AC3) ---

    @Test
    void baseOnlyIsOneBandForTheWholeYear() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Base Only Timeline LLC");

        Timeline t = service.forPolicy(owner.policyId());

        assertThat(t.term()).isEqualTo(new Timeline.Term(SEP_1, POLICY_END, null, TODAY));
        assertThat(t.covers()).extracting(Timeline.Cover::kind).containsExactly(CoverKind.BASE);
        Timeline.Cover base = cover(t, CoverKind.BASE);
        assertThat(base.from()).isEqualTo(SEP_1);
        assertThat(base.to()).isEqualTo(POLICY_END);
        assertThat(base.previousStart()).isNull();
        assertThat(base.correction()).isNull();
        assertThat(base.annualCents()).isEqualTo(120_000L);
        assertThat(base.daysKept()).isEqualTo(365);
        assertThat(base.chargedCents()).isEqualTo(120_000L);
    }

    @Test
    void extraCoverIsASecondBandAfterTheBaseWithItsDailyPrice() throws Exception {
        Timeline t = service.forPolicy(withExtraFromOct15("Extra Band Timeline LLC"));

        assertThat(t.covers()).extracting(Timeline.Cover::kind)
                .containsExactly(CoverKind.BASE, CoverKind.PRODUCT_LIABILITY);
        Timeline.Cover extra = cover(t, CoverKind.PRODUCT_LIABILITY);
        assertThat(extra.from()).isEqualTo(OCT_15);
        assertThat(extra.to()).isEqualTo(POLICY_END);
        assertThat(extra.annualCents()).isEqualTo(73_000L);
        assertThat(extra.perDayCents()).isEqualTo(200L);
        assertThat(extra.daysKept()).isEqualTo(321);
        assertThat(extra.chargedCents()).isEqualTo(64_200L);
    }

    // --- a correction (AC4, AC7) ---

    @Test
    void theTrialCorrectionMovesTheBandAndKeepsTheOldStartAndWhy() throws Exception {
        long id = withExtraFromOct15("Corrected Timeline LLC");
        CorrectCoverageService.Corrected corrected = correct(id, OCT_8);

        Timeline.Cover extra = cover(service.forPolicy(id), CoverKind.PRODUCT_LIABILITY);

        assertThat(extra.from()).isEqualTo(OCT_8);
        assertThat(extra.previousStart()).isEqualTo(OCT_15);
        assertThat(extra.daysKept()).isEqualTo(328);
        assertThat(extra.chargedCents()).isEqualTo(65_600L);
        Statement.Correction c = extra.correction();
        assertThat(c.transactionDisplayId()).isEqualTo(corrected.transactionDisplayId());
        assertThat(c.oldStart()).isEqualTo(OCT_15);
        assertThat(c.newStart()).isEqualTo(OCT_8);
        assertThat(c.dayDelta()).isEqualTo(7);
        assertThat(c.reason()).isEqualTo(REASON);
    }

    @Test
    void theChargedAmountIsOnlyTheLinesStillInForceAndOpensTheirTraces() throws Exception {
        long id = withExtraFromOct15("In Force Timeline LLC");
        correct(id, OCT_8);

        Timeline.Cover extra = cover(service.forPolicy(id), CoverKind.PRODUCT_LIABILITY);

        // The $642.00 original and its −$642.00 undo cancel out; only the $656.00 corrected charge is in force.
        assertThat(extra.rows()).hasSize(1);
        Statement.Row row = extra.rows().getFirst();
        assertThat(row.kind()).isEqualTo(EntryType.CHARGE);
        assertThat(row.amountCents()).isEqualTo(65_600L);
        assertThat(row.undoneOn()).isNull();
        assertThat(row.trace().maths().days()).isEqualTo(328);
    }

    @Test
    void aSecondCorrectionShowsTheNewestStartAndItsOwnCorrection() throws Exception {
        long id = withExtraFromOct15("Recorrected Timeline LLC");
        correct(id, OCT_8);
        CorrectCoverageService.Corrected second = correct(id, OCT_22);

        Timeline.Cover extra = cover(service.forPolicy(id), CoverKind.PRODUCT_LIABILITY);

        assertThat(extra.from()).isEqualTo(OCT_22);
        assertThat(extra.previousStart()).isEqualTo(OCT_8);
        assertThat(extra.correction().transactionDisplayId()).isEqualTo(second.transactionDisplayId());
        assertThat(extra.correction().oldStart()).isEqualTo(OCT_8);
        assertThat(extra.daysKept()).isEqualTo(314);
        assertThat(extra.chargedCents()).isEqualTo(62_800L);
    }

    // --- a cancellation (AC5, AC7) ---

    @Test
    void aCancellationEndsEveryBandOnTheDayAndKeepsOnlyTheDaysCovered() throws Exception {
        long id = withExtraFromOct15("Cancelled Timeline LLC");
        correct(id, OCT_8);
        cancel(id);

        Timeline t = service.forPolicy(id);

        assertThat(t.term().cancelledOn()).isEqualTo(DEC_1);
        assertThat(t.term().end()).isEqualTo(POLICY_END);
        Timeline.Cover base = cover(t, CoverKind.BASE);
        Timeline.Cover extra = cover(t, CoverKind.PRODUCT_LIABILITY);
        assertThat(base.daysKept()).isEqualTo(91);
        assertThat(base.chargedCents()).isEqualTo(29_918L);
        assertThat(extra.daysKept()).isEqualTo(54);
        assertThat(extra.chargedCents()).isEqualTo(10_800L);
        assertThat(extra.rows()).extracting(Statement.Row::kind).containsExactly(EntryType.CHARGE, EntryType.CREDIT);
    }

    // --- one calculation (AC7) ---

    @Test
    void theCoversAddUpToTheStatementsSettledChargesAndCredits() throws Exception {
        long id = withExtraFromOct15("Same Numbers Timeline LLC");
        correct(id, OCT_8);
        cancel(id);

        long onTimeline = service.forPolicy(id).covers().stream().mapToLong(Timeline.Cover::chargedCents).sum();
        long onStatement = statements.forPolicy(id).rows().stream()
                .filter(r -> !r.pending())
                .filter(r -> r.kind() == EntryType.CHARGE || r.kind() == EntryType.CREDIT
                        || r.kind() == EntryType.REVERSAL)
                .mapToLong(Statement.Row::amountCents).sum();

        assertThat(onTimeline).isEqualTo(onStatement);
    }

    // --- who can see it (AC1) ---

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.forPolicy(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }
}
