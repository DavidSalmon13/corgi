package app.plainly.ledger;

import java.time.LocalDate;

/** One ledger line as read back, with the balance after it and the records it links to. */
public record LedgerLine(
        long id,
        String displayId,
        LocalDate entryDate,
        EntryType type,
        String description,
        long amountCents,
        Long calcAnnualCents,
        Integer calcDays,
        Integer calcTermDays,
        LocalDate coverFrom,
        LocalDate coverTo,
        Long coverageId,
        String policyTransactionDisplayId,
        String reversesDisplayId,
        String reversedByDisplayId,
        /** For a PAYMENT line: the Stripe PaymentIntent (pi_…) and the event (evt_…) that settled it. */
        String stripePaymentIntentId,
        String stripeEventId,
        long balanceAfterCents) {}
