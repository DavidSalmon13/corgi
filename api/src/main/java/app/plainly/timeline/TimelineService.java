package app.plainly.timeline;

import app.plainly.auth.SignedInUser;
import app.plainly.common.NotFoundException;
import app.plainly.ledger.EntryType;
import app.plainly.ledger.LedgerLine;
import app.plainly.policy.CoverKind;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import app.plainly.statement.Statement;
import app.plainly.statement.StatementService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Builds P2 "Cover timeline" from the policy's live covers and the statement's money. Read-only, and built on
 * {@link StatementService} so a cover's "charged by the day" is the statement's own lines, never a second sum.
 */
@Service
public class TimelineService {

    private final PolicyService policies;
    private final StatementService statements;

    public TimelineService(PolicyService policies, StatementService statements) {
        this.policies = policies;
        this.statements = statements;
    }

    /** The timeline of a policy: its term and one band per live cover, base first. */
    public Timeline forPolicy(long policyId) {
        return build(policies.detail(policyId));
    }

    /** P2 for the signed-in owner: the timeline of their own (latest) policy, never anyone else's. */
    public Timeline forOwner(SignedInUser me) {
        return build(policies.latestForCustomer(me.customerId())
                .orElseThrow(() -> new NotFoundException("You don't have a policy yet.")));
    }

    private Timeline build(PolicyViews.Detail p) {
        Statement statement = statements.forPolicy(p.policyId());
        Map<String, Long> coverOfLine = new HashMap<>();
        for (LedgerLine l : p.ledger()) {
            if (l.coverageId() != null) {
                coverOfLine.put(l.displayId(), l.coverageId());
            }
        }
        int termDays = days(p.start(), p.end());
        List<Timeline.Cover> covers = p.coverages().stream()
                .sorted(Comparator.comparing((PolicyViews.Coverage c) -> c.kind() != CoverKind.BASE)
                        .thenComparing(PolicyViews.Coverage::from))
                .map(c -> cover(c, p.cancelledOn(), termDays, statement, coverOfLine))
                .toList();
        return new Timeline(new Timeline.Term(p.start(), p.end(), p.cancelledOn(), p.today()), covers);
    }

    private static Timeline.Cover cover(PolicyViews.Coverage c, LocalDate cancelledOn, int termDays,
            Statement statement, Map<String, Long> coverOfLine) {
        List<Statement.Row> rows = statement.rows().stream()
                .filter(r -> !r.pending() && r.undoneOn() == null)
                .filter(r -> r.kind() == EntryType.CHARGE || r.kind() == EntryType.CREDIT)
                .filter(r -> Objects.equals(coverOfLine.get(r.id()), c.id()))
                .toList();
        long charged = rows.stream().mapToLong(Statement.Row::amountCents).sum();
        LocalDate keptTo = earliest(c.to(), cancelledOn, c.endedOn());
        int daysKept = Math.max(0, days(c.from(), keptTo));
        Long perDay = termDays > 0 && c.annualPremiumCents() % termDays == 0
                ? c.annualPremiumCents() / termDays : null;
        Statement.Correction correction = c.previousStart() == null ? null : statement.corrections().stream()
                .filter(k -> c.from().equals(k.newStart()) && c.previousStart().equals(k.oldStart()))
                .reduce((a, b) -> b).orElse(null);
        return new Timeline.Cover(c.id(), c.kind(), c.from(), c.to(), c.endedOn(), c.previousStart(),
                c.annualPremiumCents(), perDay, daysKept, charged, rows, correction);
    }

    /** The cover is kept up to whichever comes first: its end, the cancel day or the day a removal ended it. */
    private static LocalDate earliest(LocalDate to, LocalDate... others) {
        LocalDate first = to;
        for (LocalDate d : others) {
            if (d != null && d.isBefore(first)) {
                first = d;
            }
        }
        return first;
    }

    private static int days(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to);
    }
}
