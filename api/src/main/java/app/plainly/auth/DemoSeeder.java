package app.plainly.auth;

import app.plainly.common.Today;
import app.plainly.policy.AddCoverageCommand;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CancelPolicyCommand;
import app.plainly.policy.CancelPolicyService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.CoverKind;
import app.plainly.policy.IssueForCustomerCommand;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import app.plainly.policy.PolicyService;
import app.plainly.policy.RemoveCoverCommand;
import app.plainly.policy.RemoveCoverService;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo data for reviewers (real-sign-in AC12): Maya Okafor of Juniper &amp; Rye Bakery LLC, with a
 * policy issued by Sam three weeks ago and an account she has already signed up for. Fictional
 * people, test credentials only. Runs at startup when {@code plainly.demo.seed} is true, only adds
 * what's missing, and is off in tests. The broker, Sam, is seeded by the V5 migration.
 *
 * <p>Next to Maya, a small book of business shows every step of the policy lifecycle on the broker's list: a
 * mid-term endorsement, a corrected effective date (reversal and rebill), a removed cover (return premium), a
 * cancellation, a rewrite and a policy that hasn't started yet. Each is made through the same services a broker's
 * clicks call, so every ledger line traces to a real policy transaction. No payments are seeded: a payment must
 * trace to a Stripe event, so reviewers pay with a test card themselves. Dates are relative to the day the app
 * starts, and every transaction is recorded on that day.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    /** The password of every demo account; V5 stores Sam's hash of the same value. */
    public static final String DEMO_PASSWORD = "plainly-demo-2026";
    public static final String BROKER_EMAIL = "sam@plainly.test";
    public static final String POLICYHOLDER_EMAIL = "maya@juniperandrye.test";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    /**
     * One fictional customer and what the broker did to their policy. {@code signedUp} gives the owner the demo
     * password; otherwise their account waits for them, as it does after a real issue.
     */
    public record Story(String legalName, String description, String ownerName, String ownerEmail, int startDaysAgo,
            boolean productLiability, boolean signedUp, BiConsumer<Long, LocalDate> then) {}

    private final boolean enabled;
    private final AccountRepository accounts;
    private final IssuePolicyService issue;
    private final AddCoverageService addCoverage;
    private final CorrectCoverageService correct;
    private final RemoveCoverService removeCover;
    private final CancelPolicyService cancel;
    private final PolicyService policies;
    private final TransactionTemplate tx;
    private final Passwords passwords;
    private final Today today;

    public DemoSeeder(@Value("${plainly.demo.seed:false}") boolean enabled, AccountRepository accounts, IssuePolicyService issue,
            AddCoverageService addCoverage, CorrectCoverageService correct, RemoveCoverService removeCover,
            CancelPolicyService cancel, PolicyService policies, TransactionTemplate tx, Passwords passwords, Today today) {
        this.enabled = enabled;
        this.accounts = accounts;
        this.issue = issue;
        this.addCoverage = addCoverage;
        this.correct = correct;
        this.removeCover = removeCover;
        this.cancel = cancel;
        this.policies = policies;
        this.tx = tx;
        this.passwords = passwords;
        this.today = today;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
            seedBook();
        }
    }

    @Transactional
    public void seed() {
        if (!accounts.emailExists(POLICYHOLDER_EMAIL)) {
            long sam = accounts.idByEmail(BROKER_EMAIL).orElseThrow();
            issue.issue(new IssuePolicyCommand("Juniper & Rye Bakery LLC", "A neighborhood bakery and café", "Maya Okafor",
                    POLICYHOLDER_EMAIL, today.date().minusDays(21), false), sam);
            log.info("Demo seed: issued Maya Okafor's policy for Juniper & Rye Bakery LLC");
        }
        int signedUp = accounts.setPasswordIfUnset(POLICYHOLDER_EMAIL, passwords.hash(DEMO_PASSWORD));
        if (signedUp == 1) {
            log.info("Demo seed: Maya Okafor can sign in as {}", POLICYHOLDER_EMAIL);
        }
    }

    /**
     * Adds each story whose owner email isn't taken yet, each all or nothing. A story that fails is logged and
     * skipped, so demo data can never stop the app from starting.
     */
    public void seedBook() {
        long sam = accounts.idByEmail(BROKER_EMAIL).orElseThrow();
        for (Story story : stories(sam)) {
            if (accounts.emailExists(story.ownerEmail())) {
                continue;
            }
            try {
                tx.executeWithoutResult(status -> tell(story, sam));
                log.info("Demo seed: added {} for {}", story.legalName(), story.ownerName());
            } catch (RuntimeException e) {
                log.warn("Demo seed: skipped {}: {}", story.legalName(), e.getMessage());
            }
        }
    }

    private void tell(Story story, long sam) {
        LocalDate start = today.date().minusDays(story.startDaysAgo());
        long policyId = issue.issue(new IssuePolicyCommand(story.legalName(), story.description(), story.ownerName(),
                story.ownerEmail(), start, story.productLiability()), sam).policyId();
        story.then().accept(policyId, today.date());
        if (story.signedUp()) {
            accounts.setPasswordIfUnset(story.ownerEmail(), passwords.hash(DEMO_PASSWORD));
        }
    }

    /** The fictional book of business. Owner emails are on the reserved {@code .test} domain. */
    List<Story> stories(long sam) {
        return List.of(
                // A mid-term endorsement: product liability added six weeks ago.
                new Story("Herrera Cycle Works LLC", "Bicycle repair and a small parts counter", "Tomás Herrera",
                        "tomas@herreracycle.test", 120, false, true,
                        (policy, t) -> addCoverage.add(policy,
                                new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, t.minusDays(45)), sam)),
                // A corrected effective date: the endorsement really began five weeks earlier, so it is reversed
                // and rebilled.
                new Story("Saffron Lane Candle Co.", "Hand-poured candles sold online and at weekend markets",
                        "Priya Raman", "priya@saffronlane.test", 180, false, true,
                        (policy, t) -> {
                            addCoverage.add(policy, new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, t.minusDays(60)), sam);
                            correct.correct(policy, new CorrectCoverageCommand(t.minusDays(95),
                                    "Market sales began five weeks earlier than first reported."), sam);
                        }),
                // A removed cover: product liability from day one, ended a month ago with a return premium.
                new Story("Brightside Dog Grooming LLC", "Mobile dog grooming and a line of shampoos", "Owen Fitzgerald",
                        "owen@brightsidegrooming.test", 200, true, false,
                        (policy, t) -> removeCover.remove(policy,
                                new RemoveCoverCommand(t.minusDays(30), "Stopped selling the shampoo line."), sam)),
                // A cancellation with a pro-rata return premium.
                new Story("Kowalski Print Studio", "Screen printing and custom posters", "Lena Kowalski",
                        "lena@kowalskiprint.test", 240, false, true,
                        (policy, t) -> cancel.cancel(policy,
                                new CancelPolicyCommand(t.minusDays(40), "The studio closed when its lease ended."), sam)),
                // A rewrite: cancelled on the move date and reissued from that day without product liability.
                new Story("Achebe Tailoring", "Bespoke tailoring and alterations", "Daniel Achebe",
                        "daniel@achebetailoring.test", 300, true, true,
                        (policy, t) -> {
                            LocalDate moved = t.minusDays(20);
                            cancel.cancel(policy, new CancelPolicyCommand(moved,
                                    "Moved to a new shop and stopped selling ready-made garments."), sam);
                            long customer = policies.detail(policy).customerId();
                            issue.issueForCustomer(customer, new IssueForCustomerCommand(moved, false, policy), sam);
                        }),
                // A policy that starts in two weeks; the owner hasn't signed up yet.
                new Story("Marigold Tea House", "A tea room with packaged loose-leaf blends", "Grace Liu",
                        "grace@marigoldtea.test", -14, true, false, (policy, t) -> {}));
    }
}
