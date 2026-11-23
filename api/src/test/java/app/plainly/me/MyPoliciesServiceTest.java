package app.plainly.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.CorrectionTestData;
import app.plainly.IntegrationTest;
import app.plainly.TestData;
import app.plainly.auth.Role;
import app.plainly.auth.SignedInUser;
import app.plainly.common.NotFoundException;
import app.plainly.policy.IssueForCustomerCommand;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers more-policies AC6, AC9, T8
/** The owner's policies, newest first, the total due counting only what is owed, and ownership as a 404. */
@IntegrationTest
class MyPoliciesServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired MyPoliciesService myPolicies;

    record Holder(SignedInUser me, long firstPolicyId) {}

    Holder holder(String business) {
        long broker = CorrectionTestData.brokerId(jdbc);
        var first = issue.issue(new IssuePolicyCommand(business, "a test shop", "Mo Many", TestData.uniqueEmail("many"),
                SEP_1, false), broker);
        long customerId = jdbc.sql("select customer_id from policies where id = ?").param(first.policyId())
                .query(Long.class).single();
        return new Holder(new SignedInUser(0, Role.POLICYHOLDER, "Mo Many", business, customerId, "mo@example.com"),
                first.policyId());
    }

    @Test
    void listsEveryPolicyNewestFirstAndAddsUpWhatIsDue() {
        Holder h = holder("Two Policies LLC");
        long broker = CorrectionTestData.brokerId(jdbc);
        var second = issue.issueForCustomer(h.me().customerId(), new IssueForCustomerCommand(OCT_1, true, null), broker);

        var mine = myPolicies.list(h.me());

        assertThat(mine.policies()).extracting(MyPoliciesService.Row::policyId)
                .containsExactly(second.policyId(), h.firstPolicyId());
        assertThat(mine.policies().get(0)).satisfies(r -> {
            assertThat(r.policyNumber()).isEqualTo(second.policyNumber());
            assertThat(r.start()).isEqualTo(OCT_1);
            assertThat(r.status()).isEqualTo("ACTIVE");
            assertThat(r.balanceCents()).isEqualTo(193_000);
            assertThat(r.paymentWaiting()).isFalse();
            assertThat(r.refundWaiting()).isNull();
        });
        assertThat(mine.totalDueCents()).isEqualTo(120_000 + 193_000);
    }

    @Test
    void anotherOwnersPolicyOrNoPolicyAtAllIsNotFound() {
        Holder mine = holder("Mine Only LLC");
        Holder theirs = holder("Theirs Only LLC");

        assertThat(myPolicies.ownedPolicy(mine.me(), mine.firstPolicyId())).isEqualTo(mine.firstPolicyId());
        assertThatThrownBy(() -> myPolicies.ownedPolicy(mine.me(), theirs.firstPolicyId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> myPolicies.ownedPolicy(mine.me(), Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class);
    }
}
