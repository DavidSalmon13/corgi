package app.plainly.policy;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.util.List;

/**
 * The priced result of issuing, before anything is saved. The live preview shows it, and issuing
 * posts exactly these lines, so what the broker saw is what the customer is charged.
 */
public record IssuePlan(LocalDate start, LocalDate end, List<Line> lines, long totalCents) {

    public record Line(CoverKind kind, String description, ProRata.Charge calc, LocalDate from, LocalDate to) {}
}
