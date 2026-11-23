package app.plainly.notify;

/** What an email is about. With the source's id it names the email, so each one is sent once. */
public enum EmailKind {
    /** Source: the policy. Sent when the broker issues it. */
    POLICY_ISSUED,
    /** Source: the payment. Sent when the bank takes the money. */
    PAYMENT_RECEIPT,
    /** Source: the refund. Sent when the money is on its way back to the card. */
    REFUND_RECEIPT
}
