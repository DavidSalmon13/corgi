package app.plainly.me;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.FakeStripeGateway;
import app.plainly.PaymentTestData;
import app.plainly.IntegrationTest;
import app.plainly.MePaths;
import app.plainly.StripeTestSupport;
import app.plainly.TestData;
import app.plainly.policy.AddCoverageCommand;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CancelPolicyCommand;
import app.plainly.policy.CancelPolicyService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.CoverKind;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import app.plainly.policy.RemoveCoverCommand;
import app.plainly.policy.RemoveCoverService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// covers AC10, T11; correct-effective-date AC13, T2; cancel-refund AC10, AC11, T7
@IntegrationTest
@AutoConfigureMockMvc
class MyCoverTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired AddCoverageService addCover;
    @Autowired CorrectCoverageService corrector;
    @Autowired FakeStripeGateway stripe;
    @Autowired CancelPolicyService canceller;
    @Autowired RemoveCoverService remover;

    /** Issues a policy, then signs its owner up with the email the broker entered, as a real owner would. */
    record Signed(Cookie session, long policyId) {}

    Signed ownerOf(String business, boolean extra) throws Exception {
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        String email = TestData.uniqueEmail("maya");
        long policyId = issue.issue(new IssuePolicyCommand(business, "a neighborhood bakery", "Maya Okafor", email, LocalDate.of(2026, 9, 1), extra),
                broker).policyId();
        return new Signed(AuthTestSupport.signUp(mvc, email), policyId);
    }

    @Test
    void theOwnerSeesTheirCoverWhatTheyOweAndWhy() throws Exception {
        Signed s = ownerOf("My Cover Bakery LLC", true);
        mvc.perform(get(MePaths.cover(s.policyId())).cookie(s.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.legalName").value("My Cover Bakery LLC"))
                .andExpect(jsonPath("$.business.description").value("a neighborhood bakery"))
                .andExpect(jsonPath("$.business.ownerName").value("Maya Okafor"))
                .andExpect(jsonPath("$.policy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.policy.start").value("2026-09-01"))
                .andExpect(jsonPath("$.policy.end").value("2027-09-01"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.amountDueCents").value(193000))
                .andExpect(jsonPath("$.dueReason").value("FIRST_YEAR"))
                .andExpect(jsonPath("$.broker.name").value("Sam Reyes"))
                .andExpect(jsonPath("$.today").value("2026-11-22"));
    }

    @Test
    void eachOwnerOnlySeesTheirOwnBusiness() throws Exception {
        Signed a = ownerOf("Owner A Florist LLC", false);
        Signed b = ownerOf("Owner B Tacos LLC", false);
        mvc.perform(get(MePaths.cover(a.policyId())).cookie(a.session())).andExpect(jsonPath("$.business.legalName").value("Owner A Florist LLC"));
        mvc.perform(get(MePaths.cover(b.policyId())).cookie(b.session())).andExpect(jsonPath("$.business.legalName").value("Owner B Tacos LLC"));
        // covers more-policies AC9, T1: another owner's policy is not found, not shown
        mvc.perform(get(MePaths.cover(b.policyId())).cookie(a.session())).andExpect(status().isNotFound());
        mvc.perform(get(MePaths.cover(Long.MAX_VALUE)).cookie(a.session())).andExpect(status().isNotFound());
    }

    // covers add-coverage AC9, T2 — after extra cover is added, P1 says that is why more is due
    @Test
    void addedExtraCoverIsWhyMoreIsDueAndFromWhen() throws Exception {
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        String email = TestData.uniqueEmail("maya");
        long policyId = issue.issue(new IssuePolicyCommand("Extra Cover Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                email, LocalDate.of(2026, 9, 1), false), broker).policyId();
        addCover.add(policyId, new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, LocalDate.of(2026, 10, 15)), broker);

        mvc.perform(get(MePaths.cover(policyId)).cookie(AuthTestSupport.signUp(mvc, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountDueCents").value(120000 + 64200))
                .andExpect(jsonPath("$.dueReason").value("EXTRA_COVER"))
                .andExpect(jsonPath("$.latestChangeFrom").value("2026-10-15"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.covers[1].from").value("2026-10-15"));
    }

    // --- correct-effective-date AC13, T2: after a correction P1 says why, and shows one extra cover ---

    @Test
    void aCorrectionThatOwesMoreIsWhyMoneyIsDueAndShowsTheCorrectedStart() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Owes More Bakery LLC");
        corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_8,
                CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountDueCents").value(1400))
                .andExpect(jsonPath("$.dueReason").value("CORRECTION"))
                .andExpect(jsonPath("$.latestChangeFrom").value("2026-10-08"))
                .andExpect(jsonPath("$.latestChangeReason").value(CorrectionTestData.REASON))
                .andExpect(jsonPath("$.refundWaiting").doesNotExist())
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.covers[1].from").value("2026-10-08"));
    }

    @Test
    void aRefundOnItsWayShowsHowMuchAndToWhichCardUntilItSettles() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Refund Due Bakery LLC");
        CorrectCoverageService.Corrected saved = corrector.correct(paid.owner().policyId(),
                new CorrectCoverageCommand(CorrectionTestData.OCT_22, CorrectionTestData.REASON),
                CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountDueCents").value(0))
                .andExpect(jsonPath("$.dueReason").value("NOTHING_DUE"))
                .andExpect(jsonPath("$.refundWaiting.amountCents").value(1400))
                .andExpect(jsonPath("$.refundWaiting.cardLast4").value("4242"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[1].from").value("2026-10-22"));

        long refundId = jdbc.sql("select id from refunds where display_id = ?")
                .param(saved.refunds().getFirst().displayId()).query(Long.class).single();
        String stripeRefundId = jdbc.sql("select stripe_refund_id from refunds where id = ?").param(refundId)
                .query(String.class).single();
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.refundSucceeded(StripeTestSupport.newEventId(),
                stripeRefundId, refundId, paid.extraIntentId(), 1400))).andExpect(status().isOk());

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(jsonPath("$.amountDueCents").value(0))
                .andExpect(jsonPath("$.refundWaiting").doesNotExist());
    }

    // covers correct-effective-date AC11, AC13, T13 — a refund that failed is still owed, so P1 still says so
    @Test
    void aFailedRefundIsStillShownAsOwedWhileTheBrokerRetries() throws Exception {
        stripe.failRefunds("The card can't take refunds.");
        Paid paid;
        try {
            paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Refund Failed Home LLC");
            corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_22,
                    CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));
        } finally {
            stripe.failRefunds(null);
        }

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(jsonPath("$.amountDueCents").value(0))
                .andExpect(jsonPath("$.refundWaiting.amountCents").value(1400))
                .andExpect(jsonPath("$.refundWaiting.cardLast4").value("4242"));
    }

    // --- cancel-refund AC10, AC11, T7: what P1 and P6 read once the policy is cancelled ---

    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);

    /** The spec's worked example: all paid, extra cover corrected to Oct 8 ($14.00 owed), cancelled from Dec 1. */
    CancelPolicyService.Cancelled cancelWorkedExample(Paid paid) {
        long broker = CorrectionTestData.brokerId(jdbc);
        corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_8,
                CorrectionTestData.REASON), broker);
        return canceller.cancel(paid.owner().policyId(), new CancelPolicyCommand(DEC_1, "Closing the bakery."), broker);
    }

    @Test
    void aCancelledPolicyShowsHowTheRefundWasWorkedOutWhileItIsOnItsWay() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Cancelled Home LLC");
        cancelWorkedExample(paid);

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.status").value("CANCELLED"))
                .andExpect(jsonPath("$.policy.cancelledOn").value("2026-12-01"))
                .andExpect(jsonPath("$.policy.end").value("2027-09-01"))
                .andExpect(jsonPath("$.amountDueCents").value(0))
                .andExpect(jsonPath("$.dueReason").value("NOTHING_DUE"))
                .andExpect(jsonPath("$.cancellation.endsOn").value("2026-12-01"))
                .andExpect(jsonPath("$.cancellation.reason").value("Closing the bakery."))
                .andExpect(jsonPath("$.cancellation.usedDays").value(91))
                .andExpect(jsonPath("$.cancellation.unusedDays").value(274))
                .andExpect(jsonPath("$.cancellation.termDays").value(365))
                .andExpect(jsonPath("$.cancellation.credits.length()").value(2))
                .andExpect(jsonPath("$.cancellation.credits[0].kind").value("BASE"))
                .andExpect(jsonPath("$.cancellation.credits[0].annualCents").value(120000))
                .andExpect(jsonPath("$.cancellation.credits[0].days").value(274))
                .andExpect(jsonPath("$.cancellation.credits[0].amountCents").value(-90082))
                .andExpect(jsonPath("$.cancellation.credits[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.cancellation.credits[1].amountCents").value(-54800))
                .andExpect(jsonPath("$.cancellation.keptBackCents").value(1400))
                .andExpect(jsonPath("$.cancellation.keptBackFor").value("CORRECT"))
                .andExpect(jsonPath("$.cancellation.refundCents").value(143482))
                .andExpect(jsonPath("$.cancellation.refundStatus").value("PENDING"))
                .andExpect(jsonPath("$.cancellation.cardLast4").value("4242"))
                .andExpect(jsonPath("$.cancellation.settledOn").doesNotExist())
                // the refund still reads as money on its way, next to the cancelled state
                .andExpect(jsonPath("$.refundWaiting.amountCents").value(143482))
                .andExpect(jsonPath("$.refundWaiting.cardLast4").value("4242"));
    }

    @Test
    void onceEveryCancellationRefundSettlesItReadsAsRefundedWithTheDay() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Refunded Home LLC");
        CancelPolicyService.Cancelled saved = cancelWorkedExample(paid);

        for (var r : saved.refunds()) {
            var row = jdbc.sql("""
                    select r.id, r.stripe_refund_id, p.stripe_payment_intent_id from refunds r
                    join payments p on p.id = r.payment_id where r.display_id = ?""")
                    .param(r.displayId())
                    .query((rs, n) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3)}).single();
            mvc.perform(StripeTestSupport.delivery(StripeTestSupport.refundSucceeded(StripeTestSupport.newEventId(),
                    (String) row[1], (Long) row[0], (String) row[2], r.amountCents()))).andExpect(status().isOk());
        }

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(jsonPath("$.cancellation.refundCents").value(143482))
                .andExpect(jsonPath("$.cancellation.refundStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.cancellation.settledOn").value("2026-11-22"))
                .andExpect(jsonPath("$.refundWaiting").doesNotExist())
                .andExpect(jsonPath("$.amountDueCents").value(0));
    }

    @Test
    void anUnpaidCancelledPolicyStillOwesForTheDaysCoveredAndCanPayIt() throws Exception {
        PaymentTestData.Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Still Owes Home LLC");
        canceller.cancel(owner.policyId(), new CancelPolicyCommand(DEC_1, "Closing the bakery."),
                CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.cover(owner.policyId())).cookie(owner.session()))
                .andExpect(jsonPath("$.amountDueCents").value(29918))
                .andExpect(jsonPath("$.dueReason").value("CANCELLATION"))
                .andExpect(jsonPath("$.cancellation.usedDays").value(91))
                .andExpect(jsonPath("$.cancellation.keptBackCents").value(90082))
                .andExpect(jsonPath("$.cancellation.keptBackFor").value("ISSUE"))
                .andExpect(jsonPath("$.cancellation.refundCents").value(0))
                .andExpect(jsonPath("$.cancellation.refundStatus").doesNotExist())
                .andExpect(jsonPath("$.refundWaiting").doesNotExist());

        // Paying what's left still works on a cancelled policy.
        PaymentTestData.Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        org.assertj.core.api.Assertions.assertThat(started.amountCents()).isEqualTo(29918);
    }

    @Test
    void anActivePolicyHasNoCancellation() throws Exception {
        Signed s = ownerOf("Still Active Home LLC", false);
        mvc.perform(get(MePaths.cover(s.policyId())).cookie(s.session()))
                .andExpect(jsonPath("$.policy.cancelledOn").doesNotExist())
                .andExpect(jsonPath("$.cancellation").doesNotExist());
    }

    // --- remove-cover AC7, T1: P1 after the extra cover is removed ---

    static final String REMOVAL_NOTE = "Stopped selling packaged goods to shops.";

    // covers remove-cover AC7, T1
    @Test
    void aRemovedExtraCoverEndsOnItsDayAndItsReturnPremiumIsOnItsWay() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Removed Home LLC");
        remover.remove(paid.owner().policyId(), new RemoveCoverCommand(DEC_1, REMOVAL_NOTE),
                CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.cover(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amountDueCents").value(0))
                .andExpect(jsonPath("$.dueReason").value("NOTHING_DUE"))
                .andExpect(jsonPath("$.latestChangeFrom").value("2026-12-01"))
                .andExpect(jsonPath("$.latestChangeReason").value(REMOVAL_NOTE))
                .andExpect(jsonPath("$.refundWaiting.amountCents").value(54800))
                .andExpect(jsonPath("$.refundWaiting.cardLast4").value("4242"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[0].endedOn").doesNotExist())
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.covers[1].endedOn").value("2026-12-01"))
                .andExpect(jsonPath("$.cancellation").doesNotExist());
    }

    // covers remove-cover AC7, T1
    @Test
    void anUnpaidExtraCoverRemovedLeavesLessOwedBecauseOfTheRemoval() throws Exception {
        PaymentTestData.Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Removed Unpaid Home LLC");
        CorrectionTestData.payInFull(mvc, jdbc, owner);
        long broker = CorrectionTestData.brokerId(jdbc);
        addCover.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, CorrectionTestData.OCT_15),
                broker); // $642.00 owed
        remover.remove(owner.policyId(), new RemoveCoverCommand(DEC_1, REMOVAL_NOTE), broker);

        mvc.perform(get(MePaths.cover(owner.policyId())).cookie(owner.session()))
                .andExpect(jsonPath("$.amountDueCents").value(9400))
                .andExpect(jsonPath("$.dueReason").value("COVER_REMOVED"))
                .andExpect(jsonPath("$.latestChangeFrom").value("2026-12-01"))
                .andExpect(jsonPath("$.latestChangeReason").value(REMOVAL_NOTE))
                .andExpect(jsonPath("$.refundWaiting").doesNotExist());
    }
}
