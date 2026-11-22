package app.plainly.policy;

import java.time.LocalDate;

/** What the broker picked on B4's Add cover tab. Either field may be missing; the service validates. */
public record AddCoverageCommand(CoverKind kind, LocalDate startDate) {}
