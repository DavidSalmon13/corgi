package app.plainly.catalog;

import app.plainly.policy.CoverKind;

/**
 * One cover we offer, as a guest sees it on the landing page: no customer or policy data.
 *
 * @param role BASE for the policy itself, ENDORSEMENT for a cover added to it
 */
public record Coverage(CoverKind kind, String label, Role role, long annualPremiumCents) {

    public enum Role { BASE, ENDORSEMENT }
}
