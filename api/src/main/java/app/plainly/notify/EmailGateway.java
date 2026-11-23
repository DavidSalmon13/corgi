package app.plainly.notify;

/** Sends email. The real one is Resend's sandbox ({@link ResendEmailGateway}); tests use a fake. */
public interface EmailGateway {

    /** False when email isn't set up: emails are then recorded as skipped, and nothing else changes. */
    boolean enabled();

    /**
     * Sends {@code message} and returns the provider's message id. The same {@code idempotencyKey} never sends
     * twice on the provider's side either.
     *
     * @throws EmailProblem if email is off, or the provider refused or didn't answer
     */
    String send(EmailMessage message, String idempotencyKey);
}
