package app.plainly.auth;

/** Someone you can "continue as" on the demo sign-in screen. {@code customerId} is set for policyholders. */
public record DemoUser(long id, Role role, String displayName, String subtitle, Long customerId) {}
