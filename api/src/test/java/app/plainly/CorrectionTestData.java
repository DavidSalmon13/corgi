package app.plainly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.policy.AddCoverageCommand;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CoverKind;
import app.plainly.policy.IssuePolicyService;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The trial's starting point for correction tests: a Sep 1, 2026 → Sep 1, 2027 policy whose base cover is paid,
 * with extra cover from Oct 15 ($642.00) added and paid too, both by card ending 4242 through the signed webhook.
 */
public final class CorrectionTestData {

    public static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
    public static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    public static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);
    public static final String REASON = "Customer's signed request was dated Oct 8; cover was entered a week late.";

    /** What the scenario built: the owner, and the payment that paid for the extra cover (the latest one). */
    public record Paid(Owner owner, long extraPaymentId, String extraIntentId) {}

    private CorrectionTestData() {}

    public static long brokerId(JdbcClient jdbc) {
        return jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
    }

    public static Paid paidWithExtraCoverFromOct15(MockMvc mvc, JdbcClient jdbc, IssuePolicyService issuer,
            AddCoverageService adder, String business) throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, business);
        payInFull(mvc, jdbc, owner);
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, OCT_15), brokerId(jdbc));
        Started extra = payInFull(mvc, jdbc, owner);
        assertThat(PaymentTestData.balance(jdbc, owner.policyId())).isZero();
        return new Paid(owner, extra.paymentId(), extra.intentId());
    }

    /** Pays the whole balance due by card ending 4242, settled by a signed charge.succeeded. */
    public static Started payInFull(MockMvc mvc, JdbcClient jdbc, Owner owner) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(),
                started.intentId(), started.amountCents(), "4242", "pm_4242"))).andExpect(status().isOk());
        assertThat(PaymentTestData.paymentStatus(jdbc, started.paymentId())).isEqualTo("SUCCEEDED");
        return started;
    }
}
