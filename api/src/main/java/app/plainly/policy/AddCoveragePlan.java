package app.plainly.policy;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;

/**
 * Extra cover priced by the day, before anything is saved. The B4 preview shows it and adding posts
 * exactly this charge, so what the broker saw is what the customer is asked to pay.
 */
public record AddCoveragePlan(long policyId, CoverKind kind, String label, LocalDate from, LocalDate to,
        ProRata.Charge calc, long totalCents) {}
