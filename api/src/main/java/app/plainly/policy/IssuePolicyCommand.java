package app.plainly.policy;

import java.time.LocalDate;

/** What the broker typed on "New customer and policy" (B2). Fields may be blank; the service validates. */
public record IssuePolicyCommand(
        String legalName,
        String businessDescription,
        String ownerName,
        String ownerEmail,
        LocalDate startDate,
        boolean productLiability) {}
