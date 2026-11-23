package app.plainly;

/** The policyholder's per-policy API routes (more-policies AC6, AC9): every read and payment names its policy. */
public final class MePaths {

    public static final String POLICIES = "/api/me/policies";
    public static final String PAYMENTS = "/api/me/payments";

    private MePaths() {}

    public static String cover(long policyId) {
        return POLICIES + "/" + policyId + "/cover";
    }

    public static String statement(long policyId) {
        return POLICIES + "/" + policyId + "/statement";
    }

    public static String timeline(long policyId) {
        return POLICIES + "/" + policyId + "/timeline";
    }

    /** The body of POST /api/me/payments: which policy's balance to pay. */
    public static String payFor(long policyId) {
        return "{\"policyId\":" + policyId + "}";
    }
}
