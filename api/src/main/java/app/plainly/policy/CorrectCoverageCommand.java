package app.plainly.policy;

import java.time.LocalDate;

/**
 * What the broker entered on B4's Correct tab: the day the extra cover really started and why it changed.
 * Either may be missing; the service validates. The customer sees the reason word for word.
 */
public record CorrectCoverageCommand(LocalDate startDate, String reason) {}
