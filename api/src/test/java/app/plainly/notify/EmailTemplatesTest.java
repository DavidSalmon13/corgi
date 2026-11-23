package app.plainly.notify;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.notify.EmailTemplates.PaymentReceipt;
import app.plainly.notify.EmailTemplates.PolicyIssued;
import app.plainly.notify.EmailTemplates.RefundReason;
import app.plainly.notify.EmailTemplates.RefundReceipt;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// covers AC2, AC3, AC4, AC7, T5
/** The three emails' words, as HTML and as plain text, in plain language with no unexplained insurance terms. */
class EmailTemplatesTest {

    /** The same words web/src/jargon.test.ts keeps off the screens. */
    private static final List<Pattern> JARGON = List.of(
            Pattern.compile("\\bpremiums?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpro[- ]?rata\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bprorat", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bendorsements?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bunearned\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\beffective date\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bunderwrit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdeductible\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\binsured party\\b", Pattern.CASE_INSENSITIVE));

    private static final String WEB = "https://plainly.example.test";

    private final EmailTemplates templates = new EmailTemplates(WEB);

    private static final PolicyIssued ISSUED = new PolicyIssued("Maya Okafor", "maya@example.test",
            "Okafor Ceramics LLC", "PL-2026-0001", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1), 120_000,
            "Sam Reyes", 7, false);

    @Test
    void theIssueEmailSaysWhatIsCoveredFromWhenAndWhatItCosts() {
        EmailMessage m = templates.policyIssued(ISSUED);

        assertThat(m.to()).isEqualTo("maya@example.test");
        assertThat(m.subject()).isEqualTo("Your cover with Plainly starts Sep 1, 2026");
        assertThat(m.text()).contains("Hi Maya Okafor,", "Sam Reyes", "Okafor Ceramics LLC", "PL-2026-0001",
                "Sep 1, 2026 to Sep 1, 2027", "$1,200.00 for the year", WEB + "/signup");
        assertThat(m.html()).contains("Set up your account", "href=\"" + WEB + "/signup\"", "$1,200.00");
    }

    // covers more-policies AC5, AC10, T11: an owner who can already sign in is asked to, and lands on the new policy
    @Test
    void anOwnerWithAnAccountIsAskedToSignInToTheNewPolicy() {
        EmailMessage m = templates.policyIssued(new PolicyIssued("Maya Okafor", "maya@example.test",
                "Okafor Ceramics LLC", "PL-2026-0014", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1), 120_000,
                "Sam Reyes", 14, true));

        assertThat(m.text()).contains("Sign in to see it", WEB + "/me/policies/14")
                .doesNotContain("Set up your account", "/signup");
        assertThat(m.html()).contains("href=\"" + WEB + "/me/policies/14\"");
    }

    @Test
    void withNoKnownBrokerItSaysYourBroker() {
        EmailMessage m = templates.policyIssued(new PolicyIssued("Maya Okafor", "maya@example.test",
                "Okafor Ceramics LLC", "PL-1", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1), 120_000, null, 7, false));

        assertThat(m.text()).contains("Your broker has set up cover for Okafor Ceramics LLC.");
    }

    @Test
    void thePaymentReceiptSaysWhatWasPaidAndWhatIsLeft() {
        EmailMessage m = templates.paymentReceipt(new PaymentReceipt("Maya Okafor", "maya@example.test", 64_200,
                "4242", LocalDate.of(2026, 11, 22), 1_400, 7));

        assertThat(m.subject()).isEqualTo("We got your payment of $642.00");
        assertThat(m.text()).contains("$642.00", "card ending 4242", "Nov 22, 2026", "Left to pay: $14.00",
                WEB + "/me/policies/7/statement");
        assertThat(m.html()).contains("See your statement", "href=\"" + WEB + "/me/policies/7/statement\"");
    }

    @Test
    void aPaymentThatClearsTheBalanceSaysYouAreAllPaidUp() {
        EmailMessage m = templates.paymentReceipt(new PaymentReceipt("Maya Okafor", "maya@example.test", 64_200,
                "4242", LocalDate.of(2026, 11, 22), 0, 7));

        assertThat(m.text()).contains("You're all paid up").doesNotContain("Left to pay");
    }

    @Test
    void moneyStillOwedBackToTheOwnerIsNotCalledLeftToPay() {
        EmailMessage m = templates.paymentReceipt(new PaymentReceipt("Maya Okafor", "maya@example.test", 64_200,
                "4242", LocalDate.of(2026, 11, 22), -1_400, 7));

        assertThat(m.text()).contains("We owe you $14.00").doesNotContain("Left to pay");
    }

    @Test
    void theRefundReceiptSaysHowMuchWhereWhyAndWhen() {
        EmailMessage m = templates.refundReceipt(new RefundReceipt("Maya Okafor", "maya@example.test", 1_400,
                "4242", RefundReason.CORRECTION, 7));

        assertThat(m.subject()).isEqualTo("$14.00 is on its way back to you");
        assertThat(m.text()).contains("card ending 4242", "because we moved your cover's start date",
                "Banks usually take 5–10 working days to show it", WEB + "/me/policies/7/statement");
    }

    @Test
    void aCancellationRefundSaysTheCoverEndedEarly() {
        EmailMessage m = templates.refundReceipt(new RefundReceipt("Maya Okafor", "maya@example.test", 102_700,
                "4242", RefundReason.CANCELLATION, 7));

        assertThat(m.subject()).isEqualTo("$1,027.00 is on its way back to you");
        assertThat(m.text()).contains("because your cover ended early");
    }

    @Test
    void whenStripeDoesNotSayWhichCardItSaysYourCard() {
        assertThat(templates.paymentReceipt(new PaymentReceipt("Maya Okafor", "maya@example.test", 500, null,
                LocalDate.of(2026, 11, 22), 0, 7)).text()).contains("Paid with: your card").doesNotContain("null");
        assertThat(templates.refundReceipt(new RefundReceipt("Maya Okafor", "maya@example.test", 500, null,
                RefundReason.CORRECTION, 7)).text()).contains("on its way back to your card.").doesNotContain("null");
    }

    @Test
    void everyEmailNamesTheOwnersAddressAndSaysItIsADemo() {
        for (EmailMessage m : all()) {
            assertThat(m.text()).contains("Sent to maya@example.test",
                    "Demo with fictional data, delivered to a test inbox.");
            assertThat(m.html()).contains("Sent to maya@example.test",
                    "Demo with fictional data, delivered to a test inbox.");
        }
    }

    @Test
    void noEmailUsesInsuranceJargon() {
        for (EmailMessage m : all()) {
            for (Pattern word : JARGON) {
                assertThat(word.matcher(m.subject() + "\n" + m.text() + "\n" + m.html()).find())
                        .as("%s in \"%s\"", word, m.subject()).isFalse();
            }
        }
    }

    @Test
    void namesTypedByABrokerAreEscapedInTheHtml() {
        EmailMessage m = templates.policyIssued(new PolicyIssued("<script>alert(1)</script>", "x@example.test",
                "Tom & Jerry's \"Shop\"", "PL-1", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1), 100, "Sam", 7, false));

        assertThat(m.html()).doesNotContain("<script>").contains("&lt;script&gt;", "Tom &amp; Jerry");
        assertThat(m.text()).contains("<script>alert(1)</script>"); // plain text is shown as text, not markup
    }

    private List<EmailMessage> all() {
        return List.of(templates.policyIssued(ISSUED),
                templates.paymentReceipt(new PaymentReceipt("Maya Okafor", "maya@example.test", 64_200, "4242",
                        LocalDate.of(2026, 11, 22), 1_400, 7)),
                templates.refundReceipt(new RefundReceipt("Maya Okafor", "maya@example.test", 1_400, "4242",
                        RefundReason.CORRECTION, 7)),
                templates.refundReceipt(new RefundReceipt("Maya Okafor", "maya@example.test", 1_400, "4242",
                        RefundReason.CANCELLATION, 7)));
    }
}
