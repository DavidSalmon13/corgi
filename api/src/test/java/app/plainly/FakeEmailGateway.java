package app.plainly;

import app.plainly.notify.EmailGateway;
import app.plainly.notify.EmailMessage;
import app.plainly.notify.EmailProblem;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for Resend in tests: no network, and a record of every email sent so a test can check its words
 * and count them. Shared by every test in the context, so tests look up their own emails by address.
 */
public class FakeEmailGateway implements EmailGateway {

    public record Sent(EmailMessage message, String idempotencyKey, String id) {}

    private final AtomicInteger counter = new AtomicInteger();
    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean enabled = true;
    private volatile boolean failing = false;

    @Override
    public boolean enabled() {
        return enabled;
    }

    /** Turns email off, as when the Resend key is missing. Always turn it back on in a finally block. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Makes every send fail, as when Resend is down. Always turn it back off in a finally block. */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public String send(EmailMessage message, String idempotencyKey) {
        if (!enabled) {
            throw new EmailProblem("Emails are off");
        }
        if (failing) {
            throw new EmailProblem("Resend refused the email (503): fake outage");
        }
        String id = "re_fake_" + counter.incrementAndGet();
        sent.add(new Sent(message, idempotencyKey, id));
        return id;
    }

    /** Every email sent to {@code to}, oldest first. */
    public List<Sent> sentTo(String to) {
        return sent.stream().filter(s -> s.message().to().equalsIgnoreCase(to)).toList();
    }
}
