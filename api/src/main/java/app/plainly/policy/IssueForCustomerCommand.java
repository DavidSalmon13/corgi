package app.plainly.policy;

import java.time.LocalDate;

/**
 * What the broker entered on B2 in customer mode: a new policy for a customer we already have. The customer is
 * read-only, so only the start date and extra cover are asked. {@code replacesPolicyId} is set for a rewrite of a
 * cancelled policy. Fields may be missing; the service validates.
 */
public record IssueForCustomerCommand(LocalDate startDate, boolean productLiability, Long replacesPolicyId) {}
