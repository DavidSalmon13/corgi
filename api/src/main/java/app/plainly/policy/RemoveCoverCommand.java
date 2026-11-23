package app.plainly.policy;

import java.time.LocalDate;

/**
 * What the broker entered on B4's Remove tab: the day the extra cover stops (at the start of that day) and why. Either
 * may be missing; the service validates. The customer sees the reason word for word.
 */
public record RemoveCoverCommand(LocalDate endDate, String reason) {}
