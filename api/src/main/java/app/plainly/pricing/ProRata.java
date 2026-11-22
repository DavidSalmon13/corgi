package app.plainly.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pro-rata pricing: annual premium × days covered ÷ days in term.
 *
 * <p>Dates are end-exclusive calendar days (America/New_York). Money is integer cents; the
 * single {@link RoundingMode#HALF_UP} step below is the only place a fraction of a cent exists.
 * Pure and side-effect free.
 */
public final class ProRata {

    private ProRata() {}

    /** The arithmetic behind one ledger line, kept so the UI can show "how we got this". */
    public record Charge(long annualCents, long days, long termDays, long amountCents) {}

    public static Charge charge(
            long annualCents, LocalDate coverFrom, LocalDate coverTo, LocalDate termStart, LocalDate termEnd) {
        if (annualCents < 0) {
            throw new IllegalArgumentException("annual premium must not be negative");
        }
        if (!termStart.isBefore(termEnd)) {
            throw new IllegalArgumentException("term must end after it starts");
        }
        if (!coverFrom.isBefore(coverTo)) {
            throw new IllegalArgumentException("cover must end after it starts");
        }
        if (coverFrom.isBefore(termStart) || coverTo.isAfter(termEnd)) {
            throw new IllegalArgumentException("cover must fall inside the policy term");
        }
        long days = ChronoUnit.DAYS.between(coverFrom, coverTo);
        long termDays = ChronoUnit.DAYS.between(termStart, termEnd);
        long amount = BigDecimal.valueOf(annualCents)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(termDays), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Charge(annualCents, days, termDays, amount);
    }
}
