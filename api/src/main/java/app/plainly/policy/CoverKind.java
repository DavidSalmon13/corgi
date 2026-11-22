package app.plainly.policy;

/**
 * The covers Plainly sells. Prices are fictional and flat (DECISIONS.md, "Pricing rules
 * confirmed"): $730 a year is exactly $2.00 a day, so anyone can check it by hand.
 */
public enum CoverKind {
    BASE(120_000, "Base cover"),
    PRODUCT_LIABILITY(73_000, "Extra cover");

    private final long annualPremiumCents;
    private final String label;

    CoverKind(long annualPremiumCents, String label) {
        this.annualPremiumCents = annualPremiumCents;
        this.label = label;
    }

    public long annualPremiumCents() {
        return annualPremiumCents;
    }

    public String label() {
        return label;
    }
}
