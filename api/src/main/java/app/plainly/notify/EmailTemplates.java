package app.plainly.notify;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The words of the three emails, as HTML and as plain text. Pure: it is given everything it says, so the same
 * inputs always give the same email. The style follows Plainly's screens: warm paper, one card, plain sentences,
 * the facts in a short list, and one amber button. Anything a person typed (names) is escaped in the HTML.
 */
@Component
public class EmailTemplates {

    /** The policy was issued: what it covers, when, and what it costs for the year. {@code brokerName} may be null. */
    public record PolicyIssued(String ownerName, String ownerEmail, String legalName, String policyNumber,
            LocalDate start, LocalDate end, long priceForTheYearCents, String brokerName) {}

    /** A payment went through. {@code leftToPayCents} is the balance once it settled; below zero, we owe them. */
    public record PaymentReceipt(String ownerName, String ownerEmail, long amountCents, String cardLast4,
            LocalDate settledOn, long leftToPayCents) {}

    /** Why money went back: the only two changes that give money back. */
    public enum RefundReason { CORRECTION, CANCELLATION, COVER_REMOVED }

    /** A refund went through: the money is on its way back to the card. */
    public record RefundReceipt(String ownerName, String ownerEmail, long amountCents, String cardLast4,
            RefundReason reason) {}

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final String FOOTER = "Demo with fictional data, delivered to a test inbox.";

    // Plainly's light tokens (web/src/theme.css); email clients can't read CSS variables.
    private static final String BG = "#f8f4ec";
    private static final String CARD = "#fffdf8";
    private static final String INK = "#1d2433";
    private static final String INK_2 = "#555c69";
    private static final String LINE = "#e4dccd";
    private static final String ACCENT = "#e9a23b";

    private final String webUrl;

    public EmailTemplates(@Value("${plainly.web-url}") String webUrl) {
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }

    public EmailMessage policyIssued(PolicyIssued p) {
        String subject = "Your cover with Plainly starts " + day(p.start());
        return new Email(p.ownerEmail(), subject, "Hi " + p.ownerName() + ",")
                .para((p.brokerName() == null ? "Your broker" : p.brokerName() + ", your broker,")
                        + " has set up cover for " + p.legalName() + ".")
                .fact("Policy number", p.policyNumber())
                .fact("Covered", day(p.start()) + " to " + day(p.end()))
                .fact("Price", money(p.priceForTheYearCents()) + " for the year")
                .para("Set up your account with this email address to see what you're covered for, what you owe "
                        + "and why, and to pay by card.")
                .button("Set up your account", webUrl + "/signup")
                .build();
    }

    public EmailMessage paymentReceipt(PaymentReceipt p) {
        String subject = "We got your payment of " + money(p.amountCents());
        String balance = p.leftToPayCents() > 0 ? "Left to pay: " + money(p.leftToPayCents())
                : p.leftToPayCents() < 0 ? "We owe you " + money(-p.leftToPayCents()) + ". It will come back to your card."
                : "You're all paid up. Thank you.";
        return new Email(p.ownerEmail(), subject, "Hi " + p.ownerName() + ",")
                .para("Thanks, your payment went through.")
                .fact("Paid", money(p.amountCents()))
                .fact("Paid with", card(p.cardLast4()))
                .fact("On", day(p.settledOn()))
                .para(balance)
                .button("See your statement", webUrl + "/me/statement")
                .build();
    }

    public EmailMessage refundReceipt(RefundReceipt r) {
        String subject = money(r.amountCents()) + " is on its way back to you";
        String why = switch (r.reason()) {
            case CORRECTION -> "We sent it back because we moved your cover's start date, so you'd paid for days "
                    + "you aren't covered.";
            case CANCELLATION -> "We sent it back because your cover ended early, so you'd paid for days after it "
                    + "ended.";
            case COVER_REMOVED -> "We sent it back because your extra cover was removed, so you'd paid for days it "
                    + "no longer runs.";
        };
        return new Email(r.ownerEmail(), subject, "Hi " + r.ownerName() + ",")
                .para(money(r.amountCents()) + " is on its way back to " + yourCard(r.cardLast4()) + ".")
                .para(why)
                .para("Banks usually take 5–10 working days to show it.")
                .button("See your statement", webUrl + "/me/statement")
                .build();
    }

    /** Integer cents as US dollars, never through floating point: 120000 → "$1,200.00". */
    static String money(long cents) {
        long magnitude = Math.abs(cents);
        String body = String.format(Locale.US, "$%,d.%02d", magnitude / 100, magnitude % 100);
        return cents < 0 ? "−" + body : body;
    }

    /** "card ending 4242", or "your card" when Stripe didn't say which. */
    private static String card(String last4) {
        return last4 == null || last4.isBlank() ? "your card" : "card ending " + last4;
    }

    private static String yourCard(String last4) {
        return last4 == null || last4.isBlank() ? "your card" : "your card ending " + last4;
    }

    private static String day(LocalDate date) {
        return DAY.format(date);
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }

    /** Builds one email's text and HTML side by side, so the two always say the same thing. */
    private final class Email {
        private final String to;
        private final String subject;
        private final List<String> text = new ArrayList<>();
        private final StringBuilder html = new StringBuilder();
        private final List<String[]> facts = new ArrayList<>();

        Email(String to, String subject, String greeting) {
            this.to = to;
            this.subject = subject;
            text.add(greeting);
            html.append(p(esc(greeting)));
        }

        Email para(String sentence) {
            flushFacts();
            text.add(sentence);
            html.append(p(esc(sentence)));
            return this;
        }

        Email fact(String label, String value) {
            facts.add(new String[] {label, value});
            return this;
        }

        Email button(String label, String href) {
            flushFacts();
            text.add(label + ": " + href);
            html.append("<p style=\"margin:24px 0 8px\"><a href=\"").append(esc(href))
                    .append("\" style=\"display:inline-block;background:").append(ACCENT).append(";color:").append(INK)
                    .append(";text-decoration:none;font-weight:600;padding:12px 20px;border-radius:10px\">")
                    .append(esc(label)).append("</a></p>");
            return this;
        }

        EmailMessage build() {
            flushFacts();
            String sentTo = "Sent to " + to + ".";
            text.add("—");
            text.add(sentTo);
            text.add(FOOTER);
            String page = "<!doctype html><html><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width\"><meta name=\"color-scheme\" content=\"light\">"
                    + "<title>" + esc(subject) + "</title></head>"
                    + "<body style=\"margin:0;padding:24px 16px;background:" + BG + ";color:" + INK
                    + ";font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:16px;line-height:1.5\">"
                    + "<div style=\"max-width:480px;margin:0 auto\">"
                    + "<p style=\"margin:0 0 16px;font-weight:700;font-size:18px\">Plainly</p>"
                    + "<div style=\"background:" + CARD + ";border:1px solid " + LINE + ";border-radius:16px;padding:24px\">"
                    + "<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3\">" + esc(subject) + "</h1>"
                    + html
                    + "</div>"
                    + "<p style=\"margin:16px 0 0;color:" + INK_2 + ";font-size:13px\">" + esc(sentTo) + "<br>"
                    + esc(FOOTER) + "</p>"
                    + "</div></body></html>";
            return new EmailMessage(to, subject, page, String.join("\n\n", text) + "\n");
        }

        private void flushFacts() {
            if (facts.isEmpty()) {
                return;
            }
            StringBuilder lines = new StringBuilder();
            html.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;margin:8px 0 16px\">");
            for (String[] f : facts) {
                lines.append(lines.isEmpty() ? "" : "\n").append(f[0]).append(": ").append(f[1]);
                html.append("<tr><td style=\"padding:8px 0;border-top:1px solid ").append(LINE).append(";color:")
                        .append(INK_2).append("\">").append(esc(f[0])).append("</td>")
                        .append("<td style=\"padding:8px 0;border-top:1px solid ").append(LINE)
                        .append(";text-align:right;font-weight:600\">").append(esc(f[1])).append("</td></tr>");
            }
            html.append("</table>");
            text.add(lines.toString());
            facts.clear();
        }

        private static String p(String escaped) {
            return "<p style=\"margin:0 0 12px\">" + escaped + "</p>";
        }
    }
}
