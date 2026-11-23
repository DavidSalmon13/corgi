package app.plainly.policy;

import app.plainly.auth.AccountService;
import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerPostingService;
import app.plainly.ledger.NewEntry;
import app.plainly.ledger.PostedEntry;
import app.plainly.notify.NotifyEvents;
import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues a one-year General Liability policy: for a new customer ({@link #issue}), or for one we already have, as
 * another policy or a rewrite of a cancelled one ({@link #issueForCustomer}). Previews and issues share
 * {@link #plan}, so the price shown before saving is the price posted, and both issues share {@link #write}.
 */
@Service
public class IssuePolicyService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_TEXT = 200;
    static final String EMAIL_TAKEN = "Another customer already uses this email.";
    static final String REPLACED_NOT_FOUND = "We couldn't find the policy to replace.";

    private final CustomerRepository customers;
    private final PolicyRepository policies;
    private final CoverageRepository coverages;
    private final PolicyTransactionRepository transactions;
    private final AccountService accounts;
    private final LedgerPostingService ledger;
    private final Today today;
    private final ApplicationEventPublisher events;

    public IssuePolicyService(CustomerRepository customers, PolicyRepository policies, CoverageRepository coverages,
            PolicyTransactionRepository transactions, AccountService accounts, LedgerPostingService ledger, Today today,
            ApplicationEventPublisher events) {
        this.customers = customers;
        this.policies = policies;
        this.coverages = coverages;
        this.transactions = transactions;
        this.accounts = accounts;
        this.ledger = ledger;
        this.today = today;
        this.events = events;
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
        Map<String, String> errors = new LinkedHashMap<>();
        checkCustomer(cmd, errors);
        checkStart(cmd.startDate(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        IssuePlan plan = plan(cmd.startDate(), cmd.productLiability());
        String legalName = cmd.legalName().strip();
        String ownerName = cmd.ownerName().strip();

        // Normalised once, in Java, so sign-up (AccountService.normalize) finds exactly what was stored.
        String ownerEmail = AccountService.trim(cmd.ownerEmail());
        long customerId;
        try {
            customerId = customers.insert(legalName, cmd.businessDescription().strip(), ownerName, ownerEmail);
            // The owner's account waits with their email and no password until they sign up (real-sign-in AC4).
            accounts.createWaitingOwner(ownerName, legalName, customerId, ownerEmail);
        } catch (DuplicateKeyException e) {
            // A concurrent issue, or an account (a broker's) that took this email after validation.
            throw new ValidationException(Map.of("ownerEmail", EMAIL_TAKEN));
        }

        return write(customerId, plan, null, brokerUserId);
    }

    /** Prices a new policy for a customer we already have (B2 customer mode), checking a rewrite as issuing would. */
    @Transactional
    public IssuePlan previewForCustomer(long customerId, IssueForCustomerCommand cmd) {
        checkForCustomer(customerId, cmd);
        return plan(cmd.startDate(), cmd.productLiability());
    }

    /**
     * Issues another policy for a customer we already have: no customer or account rows are written (AC5). With
     * {@code replacesPolicyId} it is a rewrite, allowed only for a cancelled policy of the same customer that isn't
     * replaced yet. That policy's row is held until this commits, so two rewrites at once can't both pass the check,
     * and the unique index on {@code replaces_policy_id} backs it up (AC3).
     */
    @Transactional
    public Issued issueForCustomer(long customerId, IssueForCustomerCommand cmd, long brokerUserId) {
        checkForCustomer(customerId, cmd);
        IssuePlan plan = plan(cmd.startDate(), cmd.productLiability());
        return write(customerId, plan, cmd.replacesPolicyId(), brokerUserId);
    }

    /** Throws NotFoundException for an unknown customer, else ValidationException with every problem found. */
    private void checkForCustomer(long customerId, IssueForCustomerCommand cmd) {
        if (customers.find(customerId).isEmpty()) {
            throw new NotFoundException("No customer " + customerId + ".");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        checkStart(cmd.startDate(), errors);
        if (cmd.replacesPolicyId() != null) {
            checkReplaces(customerId, cmd.replacesPolicyId(), errors);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Locks the policy being replaced, then applies the rewrite rules; a breach goes under {@code replaces}. */
    private void checkReplaces(long customerId, long replacesPolicyId, Map<String, String> errors) {
        policies.lockReplaceTarget(replacesPolicyId).ifPresentOrElse(old -> {
            if (old.customerId() != customerId) {
                errors.put("replaces", old.number() + " belongs to another customer.");
            } else if (!"CANCELLED".equals(old.status())) {
                errors.put("replaces", old.number() + " isn't cancelled. Cancel it before rewriting it.");
            } else if (old.replacedByNumber() != null) {
                errors.put("replaces", old.number() + " was already rewritten as " + old.replacedByNumber() + ".");
            }
        }, () -> errors.put("replaces", REPLACED_NOT_FOUND));
    }

    /** The steps both issues share: the policy, its ISSUE transaction and covers, the charges, and the email event. */
    private Issued write(long customerId, IssuePlan plan, Long replacesPolicyId, long brokerUserId) {
        LocalDate recordedOn = today.date();
        PolicyRepository.Created policy;
        try {
            policy = policies.insert(customerId, plan.start(), plan.end(), replacesPolicyId);
        } catch (DuplicateKeyException e) {
            // Only replaces_policy_id is unique here (numbers come from a sequence). The lock above makes this rare.
            throw new ValidationException(Map.of("replaces", "That policy was already rewritten."));
        }
        long policyId = policy.id();
        PolicyTransactionRepository.Recorded tx = transactions.insert(policyId, "ISSUE", plan.start(), recordedOn, brokerUserId, null);

        List<NewEntry> entries = new ArrayList<>();
        for (IssuePlan.Line line : plan.lines()) {
            long coverageId = coverages.insert(policyId, line.kind(), line.calc().annualCents(), line.from(), line.to());
            entries.add(NewEntry.charge(recordedOn, line.description(), coverageId, line.calc(), line.from(), line.to()));
        }
        List<PostedEntry> posted = ledger.post(policyId, tx.id(), entries);
        // The owner is emailed once this commits (emails spec AC2).
        events.publishEvent(new NotifyEvents.PolicyIssued(policyId, plan.totalCents()));
        return new Issued(policyId, policy.policyNumber(), tx.displayId(), posted, plan.totalCents());
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

    /** The new customer's details; the start date is checked apart, since every issue needs it. */
    private void checkCustomer(IssuePolicyCommand cmd, Map<String, String> errors) {
        text(cmd.legalName(), "legalName", "Enter the business's legal name.", errors);
        text(cmd.businessDescription(), "businessDescription", "Say in a few words what the business does.", errors);
        text(cmd.ownerName(), "ownerName", "Enter the owner's name.", errors);
        String email = AccountService.trim(cmd.ownerEmail());
        if (!EMAIL.matcher(email).matches() || email.length() > MAX_TEXT) {
            errors.put("ownerEmail", "Enter an email like name@business.com.");
        } else if (emailTaken(email)) {
            errors.put("ownerEmail", EMAIL_TAKEN);
        }
    }

    /** One email belongs to one person, so sign-up knows whose policy it claims (real-sign-in AC6). */
    private boolean emailTaken(String email) {
        return customers.ownerEmailExists(email) || accounts.emailInUse(email);
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
