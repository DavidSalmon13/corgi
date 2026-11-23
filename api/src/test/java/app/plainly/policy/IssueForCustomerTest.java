package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.TestData;
import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A broker issues another policy for a customer we already have, or rewrites a cancelled one: flat-cancel the policy
 * issued wrong, then issue a corrected policy linked to it.
 */
// covers more-policies AC1, AC2, AC3, AC4, AC5, T1
@IntegrationTest
@AutoConfigureMockMvc
class IssueForCustomerTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    static final String WHY = "Issued from Sep 1 instead of Oct 1.";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired CancelPolicyService canceller;

    long broker;
    Cookie brokerSession;

    /** A customer with one policy from Sep 1, 2026 (base cover only, $1,200.00), as the worked example. */
    record Existing(long customerId, long policyId, String policyNumber, String email) {}

    @BeforeEach
    void signIn() throws Exception {
        broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        brokerSession = AuthTestSupport.signInAsBroker(mvc);
    }

    Existing existing(String business) {
        String email = TestData.uniqueEmail("rewrite");
        var issued = issue.issue(new IssuePolicyCommand(business, "a test shop", "Rae Writer", email, SEP_1, false), broker);
        long customerId = jdbc.sql("select customer_id from policies where id = ?").param(issued.policyId())
                .query(Long.class).single();
        return new Existing(customerId, issued.policyId(), issued.policyNumber(), email);
    }

    void cancelFlat(long policyId) {
        canceller.cancel(policyId, new CancelPolicyCommand(SEP_1, WHY), broker);
    }

    static IssueForCustomerCommand fromOct1(Long replaces) {
        return new IssueForCustomerCommand(OCT_1, false, replaces);
    }

    Long replacesOf(long policyId) {
        return jdbc.sql("select replaces_policy_id from policies where id = ?").param(policyId)
                .query(Long.class).optional().orElse(null);
    }

    String replacesError(Runnable call) {
        try {
            call.run();
        } catch (ValidationException e) {
            return e.fields().get("replaces");
        }
        throw new AssertionError("expected a 422 under replaces");
    }

    // ---- AC1, AC5: another policy for a customer we already have

    @Test
    void issuingForAnExistingCustomerWritesNoCustomerOrAccountRows() {
        Existing c = existing("Second Policy Bakery LLC");

        var second = issue.issueForCustomer(c.customerId(), fromOct1(null), broker);

        assertThat(second.policyId()).isNotEqualTo(c.policyId());
        assertThat(second.policyNumber()).matches("GL-2026-\\d{4}").isNotEqualTo(c.policyNumber());
        assertThat(second.totalCents()).isEqualTo(120_000);
        assertThat(PaymentTestData.balance(jdbc, second.policyId())).isEqualTo(120_000);
        assertThat(jdbc.sql("select customer_id from policies where id = ?").param(second.policyId())
                .query(Long.class).single()).isEqualTo(c.customerId());
        assertThat(jdbc.sql("select count(*) from customers where owner_email = ?").param(c.email())
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from app_users where customer_id = ?").param(c.customerId())
                .query(Long.class).single()).isEqualTo(1);
        assertThat(replacesOf(second.policyId())).isNull();
    }

    @Test
    void thePreviewForACustomerIsTheSamePriceAsIssuing() {
        Existing c = existing("Preview Customer Bakery LLC");

        IssuePlan plan = issue.previewForCustomer(c.customerId(), new IssueForCustomerCommand(OCT_1, true, null));

        assertThat(plan.start()).isEqualTo(OCT_1);
        assertThat(plan.end()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(plan.lines()).extracting(IssuePlan.Line::kind)
                .containsExactly(CoverKind.BASE, CoverKind.PRODUCT_LIABILITY);
        assertThat(plan.totalCents()).isEqualTo(193_000);
        assertThat(jdbc.sql("select count(*) from policies where customer_id = ?").param(c.customerId())
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void anUnknownCustomerIsNotFound() {
        assertThatThrownBy(() -> issue.previewForCustomer(Long.MAX_VALUE, fromOct1(null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> issue.issueForCustomer(Long.MAX_VALUE, fromOct1(null), broker))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void theStartDateIsCheckedAsForANewCustomer() {
        Existing c = existing("No Start Bakery LLC");

        assertThatThrownBy(() -> issue.issueForCustomer(c.customerId(), new IssueForCustomerCommand(null, false, null), broker))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.fields()).containsEntry("startDate", "Pick the day cover should start."));
    }

    // ---- AC2, AC3: rewrite a cancelled policy

    @Test
    void aRewriteOfAFlatCancelledPolicyRecordsWhatItReplaces() {
        Existing c = existing("Rewrite Bakery LLC");
        cancelFlat(c.policyId());

        var rewrite = issue.issueForCustomer(c.customerId(), fromOct1(c.policyId()), broker);

        assertThat(replacesOf(rewrite.policyId())).isEqualTo(c.policyId());
        assertThat(rewrite.totalCents()).isEqualTo(120_000);
        assertThat(PaymentTestData.balance(jdbc, c.policyId())).as("the old policy is credited in full").isZero();
        assertThat(PaymentTestData.balance(jdbc, rewrite.policyId())).as("the new one is due").isEqualTo(120_000);
    }

    @Test
    void onlyACancelledPolicyCanBeRewritten() {
        Existing c = existing("Still Active Rewrite LLC");

        assertThat(replacesError(() -> issue.issueForCustomer(c.customerId(), fromOct1(c.policyId()), broker)))
                .isEqualTo(c.policyNumber() + " isn't cancelled. Cancel it before rewriting it.");
        assertThat(jdbc.sql("select count(*) from policies where customer_id = ?").param(c.customerId())
                .query(Long.class).single()).as("nothing written").isEqualTo(1);
    }

    @Test
    void aPolicyOfAnotherCustomerCannotBeReplaced() {
        Existing mine = existing("Mine Rewrite LLC");
        Existing theirs = existing("Theirs Rewrite LLC");
        cancelFlat(theirs.policyId());

        assertThat(replacesError(() -> issue.issueForCustomer(mine.customerId(), fromOct1(theirs.policyId()), broker)))
                .isEqualTo(theirs.policyNumber() + " belongs to another customer.");
    }

    @Test
    void aPolicyThatDoesNotExistCannotBeReplaced() {
        Existing c = existing("Ghost Rewrite LLC");

        assertThat(replacesError(() -> issue.issueForCustomer(c.customerId(), fromOct1(Long.MAX_VALUE), broker)))
                .isEqualTo("We couldn't find the policy to replace.");
    }

    @Test
    void aPolicyCanBeReplacedOnlyOnce() {
        Existing c = existing("Twice Rewrite LLC");
        cancelFlat(c.policyId());
        var first = issue.issueForCustomer(c.customerId(), fromOct1(c.policyId()), broker);

        assertThat(replacesError(() -> issue.issueForCustomer(c.customerId(), fromOct1(c.policyId()), broker)))
                .isEqualTo(c.policyNumber() + " was already rewritten as " + first.policyNumber() + ".");
    }

    @Test
    void twoRewritesAtOnceOnlyOneWins() throws Exception {
        Existing c = existing("Race Rewrite LLC");
        cancelFlat(c.policyId());
        Callable<String> rewrite = () -> {
            try {
                issue.issueForCustomer(c.customerId(), fromOct1(c.policyId()), broker);
                return "issued";
            } catch (ValidationException e) {
                return e.fields().containsKey("replaces") ? "refused" : "other: " + e.fields();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> answers = pool.invokeAll(List.of(rewrite, rewrite));
            assertThat(answers.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return "crashed: " + e;
                }
            })).containsExactlyInAnyOrder("issued", "refused");
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.sql("select count(*) from policies where replaces_policy_id = ?").param(c.policyId())
                .query(Long.class).single()).isEqualTo(1);
    }

    // ---- HTTP: POST /api/broker/customers/{customerId}/policies and /preview

    @Test
    void theEndpointIssuesForTheCustomerAndSaysWhereToFindIt() throws Exception {
        Existing c = existing("Endpoint Customer LLC");

        mvc.perform(post("/api/broker/customers/" + c.customerId() + "/policies/preview").cookie(brokerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-10-01\",\"productLiability\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-10-01"))
                .andExpect(jsonPath("$.totalCents").value(193_000));
        mvc.perform(post("/api/broker/customers/" + c.customerId() + "/policies").cookie(brokerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-10-01\",\"productLiability\":false}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/broker/policies/\\d+")))
                .andExpect(jsonPath("$.policyNumber", matchesPattern("GL-2026-\\d{4}")))
                .andExpect(jsonPath("$.totalCents").value(120_000));
    }

    @Test
    void theEndpointPutsAReplaceProblemUnderReplaces() throws Exception {
        Existing c = existing("Endpoint Replace LLC");

        mvc.perform(post("/api/broker/customers/" + c.customerId() + "/policies").cookie(brokerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-10-01\",\"productLiability\":false,\"replacesPolicyId\":%d}"
                                .formatted(c.policyId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.replaces").value(c.policyNumber() + " isn't cancelled. Cancel it before rewriting it."));
    }

    @Test
    void theEndpointAnswers404ForAnUnknownCustomer() throws Exception {
        mvc.perform(post("/api/broker/customers/" + Long.MAX_VALUE + "/policies").cookie(brokerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-10-01\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/broker/customers/" + Long.MAX_VALUE + "/policies/preview").cookie(brokerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-10-01\"}"))
                .andExpect(status().isNotFound());
    }

    // covers AC1, T6 — B2 shows the customer read-only
    @Test
    void theBrokerReadsTheCustomerB2ShowsReadOnly() throws Exception {
        Existing c = existing("Read Only Card LLC");

        mvc.perform(get("/api/broker/customers/" + c.customerId()).cookie(brokerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) c.customerId()))
                .andExpect(jsonPath("$.legalName").value("Read Only Card LLC"))
                .andExpect(jsonPath("$.businessDescription").value("a test shop"))
                .andExpect(jsonPath("$.ownerName").value("Rae Writer"))
                .andExpect(jsonPath("$.ownerEmail").value(c.email()));
        mvc.perform(get("/api/broker/customers/" + Long.MAX_VALUE).cookie(brokerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPolicyholderCannotIssue() throws Exception {
        Existing c = existing("Owner Cannot Issue LLC");
        Cookie owner = AuthTestSupport.signUp(mvc, c.email());

        mvc.perform(post("/api/broker/customers/" + c.customerId() + "/policies").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-10-01\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- AC4: the two policies link to each other on B3 and B1

    @Test
    void theBrokerSeesWhichPolicyReplacedWhich() throws Exception {
        Existing c = existing("Linked Rewrite LLC");
        cancelFlat(c.policyId());
        var rewrite = issue.issueForCustomer(c.customerId(), fromOct1(c.policyId()), broker);

        mvc.perform(get("/api/broker/policies/" + rewrite.policyId()).cookie(brokerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value((int) c.customerId()))
                .andExpect(jsonPath("$.replaces.policyId").value((int) c.policyId()))
                .andExpect(jsonPath("$.replaces.policyNumber").value(c.policyNumber()))
                .andExpect(jsonPath("$.replacedBy").doesNotExist());
        mvc.perform(get("/api/broker/policies/" + c.policyId()).cookie(brokerSession))
                .andExpect(jsonPath("$.replacedBy.policyId").value((int) rewrite.policyId()))
                .andExpect(jsonPath("$.replacedBy.policyNumber").value(rewrite.policyNumber()))
                .andExpect(jsonPath("$.replaces").doesNotExist());

        String oldRow = "$[?(@.policyId == " + c.policyId() + ")]";
        mvc.perform(get("/api/broker/policies").cookie(brokerSession))
                .andExpect(jsonPath(oldRow + ".customerId").value((int) c.customerId()))
                .andExpect(jsonPath(oldRow + ".status").value("CANCELLED"))
                .andExpect(jsonPath(oldRow + ".replacedBy.policyNumber").value(rewrite.policyNumber()));
    }
}
