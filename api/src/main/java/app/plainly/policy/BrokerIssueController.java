package app.plainly.policy;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import static app.plainly.policy.IssuePolicyService.BAD_EMAIL;
import static app.plainly.policy.IssuePolicyService.EMAIL_SHAPE;
import static app.plainly.policy.IssuePolicyService.MAX_TEXT;
import static app.plainly.policy.IssuePolicyService.NO_DESCRIPTION;
import static app.plainly.policy.IssuePolicyService.NO_LEGAL_NAME;
import static app.plainly.policy.IssuePolicyService.NO_OWNER_NAME;
import static app.plainly.policy.IssuePolicyService.NO_START;
import static app.plainly.policy.IssuePolicyService.TOO_LONG;

import app.plainly.common.LenientDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * B2: the live price preview and the issue button, for a new customer ("New customer and policy") or for one we
 * already have ("New policy for this customer", and a rewrite). Broker only (filter).
 */
@RestController
@RequestMapping("/api/broker")
class BrokerIssueController {

    private final IssuePolicyService service;
    private final CustomerService customers;

    BrokerIssueController(IssuePolicyService service, CustomerService customers) {
        this.service = service;
        this.customers = customers;
    }

    /**
     * What each request accepts, checked before the service runs (input-validation spec). A missing productLiability
     * means "no extra cover". An unreadable date reads as null (LenientDate), so it is a field message, not a 400.
     * The service keeps the rules that need data: the start within a year of today, an email no one else uses.
     */
    record PreviewRequest(
            @NotNull(message = NO_START) @JsonDeserialize(using = LenientDate.class) LocalDate startDate,
            Boolean productLiability) {}

    /**
     * The email may carry the spaces the service trims, so the pattern allows them around the address. The lookahead
     * refuses anything over {@code MAX_TEXT} first: without it, a long run of no-break spaces makes the pattern
     * backtrack for seconds (security review, 2026-11-23).
     */
    record IssueRequest(
            @NotBlank(message = NO_LEGAL_NAME) @Size(max = MAX_TEXT, message = TOO_LONG) String legalName,
            @NotBlank(message = NO_DESCRIPTION) @Size(max = MAX_TEXT, message = TOO_LONG) String businessDescription,
            @NotBlank(message = NO_OWNER_NAME) @Size(max = MAX_TEXT, message = TOO_LONG) String ownerName,
            @NotBlank(message = BAD_EMAIL) @Pattern(regexp = "(?s)(?=.{0," + MAX_TEXT + "}\\z)[\\s\\p{Z}]*" + EMAIL_SHAPE + "[\\s\\p{Z}]*", message = BAD_EMAIL)
                    @Size(max = MAX_TEXT, message = BAD_EMAIL) String ownerEmail,
            @NotNull(message = NO_START) @JsonDeserialize(using = LenientDate.class) LocalDate startDate,
            Boolean productLiability) {}

    /** B2 in customer mode. {@code replacesPolicyId} is set for a rewrite of a cancelled policy. */
    record ForCustomerRequest(
            @NotNull(message = NO_START) @JsonDeserialize(using = LenientDate.class) LocalDate startDate,
            Boolean productLiability, Long replacesPolicyId) {}

    @PostMapping("/policies/preview")
    IssuePlan preview(@Valid @RequestBody PreviewRequest body) {
        return service.preview(body.startDate(), Boolean.TRUE.equals(body.productLiability()));
    }

    @PostMapping("/policies")
    ResponseEntity<IssuePolicyService.Issued> issue(
            @Valid @RequestBody IssueRequest body, @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        var cmd = new IssuePolicyCommand(body.legalName(), body.businessDescription(), body.ownerName(),
                body.ownerEmail(), body.startDate(), Boolean.TRUE.equals(body.productLiability()));
        return created(service.issue(cmd, broker.id()));
    }

    /** The customer B2 shows read-only in customer mode. */
    @GetMapping("/customers/{customerId}")
    CustomerRepository.Row customer(@PathVariable long customerId) {
        return customers.find(customerId);
    }

    @PostMapping("/customers/{customerId}/policies/preview")
    IssuePlan previewForCustomer(@PathVariable long customerId, @Valid @RequestBody ForCustomerRequest body) {
        return service.previewForCustomer(customerId, command(body));
    }

    @PostMapping("/customers/{customerId}/policies")
    ResponseEntity<IssuePolicyService.Issued> issueForCustomer(@PathVariable long customerId,
            @Valid @RequestBody ForCustomerRequest body, @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        return created(service.issueForCustomer(customerId, command(body), broker.id()));
    }

    private static IssueForCustomerCommand command(ForCustomerRequest body) {
        return new IssueForCustomerCommand(body.startDate(),
                Boolean.TRUE.equals(body.productLiability()), body.replacesPolicyId());
    }

    private static ResponseEntity<IssuePolicyService.Issued> created(IssuePolicyService.Issued issued) {
        return ResponseEntity.created(URI.create("/api/broker/policies/" + issued.policyId())).body(issued);
    }
}
