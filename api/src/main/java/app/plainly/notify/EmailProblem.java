package app.plainly.notify;

/** An email couldn't be sent: email is off, or Resend refused or didn't answer. The money change still stands. */
public class EmailProblem extends RuntimeException {
    public EmailProblem(String message) {
        super(message);
    }

    public EmailProblem(String message, Throwable cause) {
        super(message, cause);
    }
}
