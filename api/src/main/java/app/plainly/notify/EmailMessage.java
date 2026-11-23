package app.plainly.notify;

/** One email: who it is for, its subject, and the same words as HTML and as plain text. */
public record EmailMessage(String to, String subject, String html, String text) {}
