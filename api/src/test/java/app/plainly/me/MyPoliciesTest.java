package app.plainly.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.IntegrationTest;
import app.plainly.MePaths;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.StripeTestSupport;
import app.plainly.policy.CancelPolicyCommand;
import app.plainly.policy.CancelPolicyService;
import app.plainly.policy.IssueForCustomerCommand;
import app.plainly.policy.IssuePolicyService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/me/policies: every policy the signed-in owner holds, newest first, with what is due in total; and every
 * per-policy read and payment answers 404 for a policy the owner doesn't hold.
 */
// covers more-policies AC6, AC9, T1
@IntegrationTest
@AutoConfigureMockMvc
class MyPoliciesTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired CancelPolicyService canceller;

    long broker() {
        return CorrectionTestData.brokerId(jdbc);
    }

    /** A second policy for the owner's customer, from Oct 1, 2026. */
    IssuePolicyService.Issued another(Owner owner, boolean productLiability, Long replaces) {
        return issue.issueForCustomer(owner.customerId(),
                new IssueForCustomerCommand(OCT_1, productLiability, replaces), broker());
    }

    String policyNumber(long policyId) {
        return jdbc.sql("select policy_number from policies where id = ?").param(policyId).query(String.class).single();
    }

    @Test
    void withOnePolicyTheListHasJustThatPolicy() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "One Policy Bakery LLC");

        mvc.perform(get(MePaths.POLICIES).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].policyId").value((int) owner.policyId()))
                .andExpect(jsonPath("$.policies[0].policyNumber").value(policyNumber(owner.policyId())))
                .andExpect(jsonPath("$.policies[0].start").value("2026-09-01"))
                .andExpect(jsonPath("$.policies[0].end").value("2027-09-01"))
                .andExpect(jsonPath("$.policies[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.policies[0].balanceCents").value(120_000))
                .andExpect(jsonPath("$.policies[0].paymentWaiting").value(false))
                .andExpect(jsonPath("$.policies[0].refundWaiting").doesNotExist())
                .andExpect(jsonPath("$.policies[0].replaces").doesNotExist())
                .andExpect(jsonPath("$.policies[0].replacedBy").doesNotExist())
                .andExpect(jsonPath("$.totalDueCents").value(120_000));
    }

    /** The worked example: GL-…13 paid, cancelled flat on Sep 1 with its refund on its way, rewritten from Oct 1. */
    @Test
    void aRewriteListsBothPoliciesNewestFirstAndOnlyWhatIsOwedIsDue() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Rewritten Bakery LLC");
        CorrectionTestData.payInFull(mvc, jdbc, owner);
        canceller.cancel(owner.policyId(), new CancelPolicyCommand(SEP_1, "Issued from Sep 1 instead of Oct 1."), broker());
        var rewrite = another(owner, false, owner.policyId());
        String oldNumber = policyNumber(owner.policyId());

        mvc.perform(get(MePaths.POLICIES).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(2))
                .andExpect(jsonPath("$.policies[0].policyId").value((int) rewrite.policyId()))
                .andExpect(jsonPath("$.policies[0].start").value("2026-10-01"))
                .andExpect(jsonPath("$.policies[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.policies[0].balanceCents").value(120_000))
                .andExpect(jsonPath("$.policies[0].replaces.policyId").value((int) owner.policyId()))
                .andExpect(jsonPath("$.policies[0].replaces.policyNumber").value(oldNumber))
                .andExpect(jsonPath("$.policies[1].policyId").value((int) owner.policyId()))
                .andExpect(jsonPath("$.policies[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.policies[1].balanceCents").value(-120_000))
                .andExpect(jsonPath("$.policies[1].refundWaiting.amountCents").value(120_000))
                .andExpect(jsonPath("$.policies[1].refundWaiting.cardLast4").value("4242"))
                .andExpect(jsonPath("$.policies[1].replacedBy.policyNumber").value(rewrite.policyNumber()))
                // the refund on its way is not netted against the new policy: only positive balances are due
                .andExpect(jsonPath("$.totalDueCents").value(120_000));
    }

    @Test
    void aPaymentWaitingWithTheBankShowsOnItsPolicy() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Waiting Policies Bakery LLC");
        var started = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(),
                started.intentId(), started.amountCents()))).andExpect(status().isOk());

        mvc.perform(get(MePaths.POLICIES).cookie(owner.session()))
                .andExpect(jsonPath("$.policies[0].paymentWaiting").value(true));
    }

    @Test
    void eachPolicyOpensItsOwnCoverStatementAndTimeline() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Two Covers Bakery LLC");
        var second = another(owner, true, null);

        mvc.perform(get(MePaths.cover(owner.policyId())).cookie(owner.session()))
                .andExpect(jsonPath("$.policy.policyNumber").value(policyNumber(owner.policyId())))
                .andExpect(jsonPath("$.amountDueCents").value(120_000));
        mvc.perform(get(MePaths.cover(second.policyId())).cookie(owner.session()))
                .andExpect(jsonPath("$.policy.policyNumber").value(second.policyNumber()))
                .andExpect(jsonPath("$.policy.start").value("2026-10-01"))
                .andExpect(jsonPath("$.amountDueCents").value(193_000));
        mvc.perform(get(MePaths.statement(second.policyId())).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.balanceCents").value(193_000));
        mvc.perform(get(MePaths.timeline(second.policyId())).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.start").value("2026-10-01"))
                .andExpect(jsonPath("$.covers.length()").value(2));
        mvc.perform(get(MePaths.POLICIES).cookie(owner.session()))
                .andExpect(jsonPath("$.totalDueCents").value(313_000));
    }

    @Test
    void aPaymentPaysOnlyThePolicyItNames() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Pay One Bakery LLC");
        var second = another(owner, true, null);

        mvc.perform(post(MePaths.PAYMENTS).cookie(owner.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(MePaths.payFor(second.policyId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(193_000));
        long policyOfPayment = jdbc.sql("select policy_id from payments where policy_id in (?, ?)")
                .params(owner.policyId(), second.policyId()).query(Long.class).single();
        assertThat(policyOfPayment).isEqualTo(second.policyId());
    }

    // ---- AC9: another owner's policy is not found, on every per-policy read and on pay

    @Test
    void anotherOwnersPolicyIsNotFoundEverywhere() throws Exception {
        Owner a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Policies Owner A LLC");
        Owner b = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Policies Owner B LLC");

        for (String url : new String[] {MePaths.cover(b.policyId()), MePaths.statement(b.policyId()),
                MePaths.timeline(b.policyId())}) {
            mvc.perform(get(url).cookie(a.session())).andExpect(status().isNotFound());
        }
        mvc.perform(post(MePaths.PAYMENTS).cookie(a.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(MePaths.payFor(b.policyId())))
                .andExpect(status().isNotFound());
        mvc.perform(post(MePaths.PAYMENTS).cookie(a.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(MePaths.payFor(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(get(MePaths.POLICIES).cookie(a.session()))
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].policyId").value((int) a.policyId()));
    }

    @Test
    void theListIsForPolicyholdersOnly() throws Exception {
        mvc.perform(get(MePaths.POLICIES).cookie(AuthTestSupport.signInAsBroker(mvc))).andExpect(status().isForbidden());
        mvc.perform(get(MePaths.POLICIES)).andExpect(status().isUnauthorized());
    }

    @Test
    void theOneCoverRouteIsGone() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, "Old Cover Route LLC");

        mvc.perform(get("/api/me/cover").cookie(owner.session())).andExpect(status().isNotFound());
    }
}
