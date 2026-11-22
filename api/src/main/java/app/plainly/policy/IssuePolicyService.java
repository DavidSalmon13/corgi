package app.plainly.policy;

import app.plainly.auth.AccountService;
import app.plainly.common.Today;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerPostingService;
import app.plainly.ledger.NewEntry;
import app.plainly.ledger.PostedEntry;
import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a customer and issues a one-year General Liability policy. {@link #preview} and
 * {@link #issue} share {@link #plan}, so the price shown before saving is the price posted.
 */
@Service
public class IssuePolicyService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_TEXT = 200;
    static final String EMAIL_TAKEN = "Another customer already uses this email.";

    private final JdbcClient jdbc;
    private final LedgerPostingService ledger;
    private final Today today;

    public IssuePolicyService(JdbcClient jdbc, LedgerPostingService ledger, Today today) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.today = today;
    }

    /** What issuing created, with the ids the broker and the customer will see. */
    public record Issued(long policyId, String policyNumber, String transactionDisplayId, List<PostedEntry> entries,
            long totalCents) {}

    /** Prices a policy without saving anything (the live price panel on B2). */
    public IssuePlan preview(LocalDate startDate, boolean productLiability) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkStart(startDate, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return plan(startDate, productLiability);
    }

    /** Validates, then writes customer, policy, covers, the ISSUE transaction and its charges, all or nothing. */
    @Transactional
    public Issued issue(IssuePolicyCommand cmd, long brokerUserId) {
        validate(cmd);
        IssuePlan plan = plan(cmd.startDate(), cmd.productLiability());
        LocalDate recordedOn = today.date();
        String legalName = cmd.legalName().strip();
        String ownerName = cmd.ownerName().strip();

        // Normalised once, in Java, so sign-up (AccountService.normalize) finds exactly what was stored.
        String ownerEmail = AccountService.trim(cmd.ownerEmail());
        long customerId;
        try {
            customerId = jdbc.sql("""
                    insert into customers (legal_name, business_description, owner_name, owner_email)
                    values (?, ?, ?, ?) returning id""")
                    .params(legalName, cmd.businessDescription().strip(), ownerName, ownerEmail)
                    .query(Long.class).single();
            // The owner's account waits with their email and no password until they sign up (real-sign-in AC4).
            jdbc.sql("""
                    insert into app_users (role, display_name, subtitle, customer_id, email)
                    values ('POLICYHOLDER', ?, ?, ?, ?)""")
                    .params(ownerName, "Owner, " + legalName, customerId, AccountService.normalize(ownerEmail)).update();
        } catch (DuplicateKeyException e) {
            // A concurrent issue, or an account (a broker's) that took this email after validation.
            throw new ValidationException(Map.of("ownerEmail", EMAIL_TAKEN));
        }

        var policy = jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date)
                values ('GL-' || ? || '-' || lpad(nextval('policy_number_seq')::text, 4, '0'), ?, ?, ?)
                returning id, policy_number""")
                .params(plan.start().getYear(), customerId, plan.start(), plan.end())
                .query((rs, n) -> Map.entry(rs.getLong("id"), rs.getString("policy_number"))).single();
        long policyId = policy.getKey();

        var tx = jdbc.sql("""
                insert into policy_transactions (policy_id, type, effective_date, recorded_on, created_by)
                values (?, 'ISSUE', ?, ?, ?) returning id, display_id""")
                .params(policyId, plan.start(), recordedOn, brokerUserId)
                .query((rs, n) -> Map.entry(rs.getLong("id"), rs.getString("display_id"))).single();

        List<NewEntry> entries = new ArrayList<>();
        for (IssuePlan.Line line : plan.lines()) {
            long coverageId = jdbc.sql("""
                    insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                    values (?, ?, ?, ?, ?) returning id""")
                    .params(policyId, line.kind().name(), line.calc().annualCents(), line.from(), line.to())
                    .query(Long.class).single();
            entries.add(NewEntry.charge(recordedOn, line.description(), coverageId, line.calc(), line.from(), line.to()));
        }
        List<PostedEntry> posted = ledger.post(policyId, tx.getKey(), entries);
        return new Issued(policyId, policy.getValue(), tx.getValue(), posted, plan.totalCents());
    }

    private IssuePlan plan(LocalDate start, boolean productLiability) {
        LocalDate end = start.plusYears(1); // end-exclusive: Sep 1 → Sep 1 covers 365 (or 366) days
        List<IssuePlan.Line> lines = new ArrayList<>();
        lines.add(fullYear(CoverKind.BASE, start, end));
        if (productLiability) {
            lines.add(fullYear(CoverKind.PRODUCT_LIABILITY, start, end));
        }
        long total = lines.stream().mapToLong(l -> l.calc().amountCents()).sum();
        return new IssuePlan(start, end, List.copyOf(lines), total);
    }

    private static IssuePlan.Line fullYear(CoverKind kind, LocalDate start, LocalDate end) {
        ProRata.Charge calc = ProRata.charge(kind.annualPremiumCents(), start, end, start, end);
        return new IssuePlan.Line(kind, kind.label() + ", full year", calc, start, end);
    }

    private void validate(IssuePolicyCommand cmd) {
        Map<String, String> errors = new LinkedHashMap<>();
        text(cmd.legalName(), "legalName", "Enter the business's legal name.", errors);
        text(cmd.businessDescription(), "businessDescription", "Say in a few words what the business does.", errors);
        text(cmd.ownerName(), "ownerName", "Enter the owner's name.", errors);
        String email = AccountService.trim(cmd.ownerEmail());
        if (!EMAIL.matcher(email).matches() || email.length() > MAX_TEXT) {
            errors.put("ownerEmail", "Enter an email like name@business.com.");
        } else if (emailTaken(email)) {
            errors.put("ownerEmail", EMAIL_TAKEN);
        }
        checkStart(cmd.startDate(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** One email belongs to one person, so sign-up knows whose policy it claims (real-sign-in AC6). */
    private boolean emailTaken(String email) {
        return jdbc.sql("""
                select exists (select 1 from customers where lower(owner_email) = lower(?))
                    or exists (select 1 from app_users where email = ?)""")
                .params(email, AccountService.normalize(email)).query(Boolean.class).single();
    }

    private static void text(String value, String field, String message, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            errors.put(field, message);
        } else if (value.strip().length() > MAX_TEXT) {
            errors.put(field, "Keep this under " + MAX_TEXT + " characters.");
        }
    }

    private void checkStart(LocalDate start, Map<String, String> errors) {
        if (start == null) {
            errors.put("startDate", "Pick the day cover should start.");
            return;
        }
        LocalDate now = today.date();
        if (start.isBefore(now.minusYears(1)) || start.isAfter(now.plusYears(1))) {
            errors.put("startDate", "Pick a start date within a year of today.");
        }
    }
}
