package app.plainly.policy;

import java.time.LocalDate;

/**
 * What the broker entered on B4's Cancel tab: the day all cover stops (at the start of that day) and why. Either may
 * be missing; the service validates. The customer sees the reason word for word.
 */
public record CancelPolicyCommand(LocalDate cancelDate, String reason) {}
