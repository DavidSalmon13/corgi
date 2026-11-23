package app.plainly.timeline;

import app.plainly.policy.CoverKind;
import app.plainly.statement.Statement;
import java.time.LocalDate;
import java.util.List;

/**
 * P2 "Cover timeline" for one policy: the term, and one band per live cover (base first) with what it costs. The
 * money comes from the statement, so every amount here is the statement's amount to the cent.
 */
public record Timeline(Term term, List<Cover> covers) {

    /** {@code end} is always the original term end; {@code cancelledOn} is the day all cover stopped, or null. */
    public record Term(LocalDate start, LocalDate end, LocalDate cancelledOn, LocalDate today) {}

    /**
     * One band. {@code from}/{@code to} are the cover's dates before any cancellation; {@code previousStart} is the
     * start a correction replaced, else null. {@code perDayCents} is the annual price ÷ days in the term when that is
     * a whole number of cents, else null. {@code daysKept} runs from {@code from} to {@code to} or to the cancellation
     * day. {@code chargedCents} is the sum of {@code rows}: the cover's settled charge and credit lines still in force
     * (lines a later change undid, and their undos, are left out). {@code correction} is the latest correction of
     * this cover, else null.
     */
    public record Cover(long id, CoverKind kind, LocalDate from, LocalDate to, LocalDate previousStart,
            long annualCents, Long perDayCents, int daysKept, long chargedCents, List<Statement.Row> rows,
            Statement.Correction correction) {}
}
