package app.plainly.auth;

/**
 * The person a session belongs to, as the API and the web app see them. Never carries a password
 * or hash. {@code customerId} is set for policyholders.
 */
public record SignedInUser(long id, Role role, String displayName, String subtitle, Long customerId, String email) {}
