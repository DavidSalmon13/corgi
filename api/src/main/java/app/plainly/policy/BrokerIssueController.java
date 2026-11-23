package app.plainly.policy;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** A missing productLiability means "no extra cover". Dates arrive as yyyy-MM-dd strings so a bad one becomes a field message, not a 400. */
    record PreviewRequest(String startDate, Boolean productLiability) {}

    record IssueRequest(String legalName, String businessDescription, String ownerName, String ownerEmail,
            String startDate, Boolean productLiability) {}

    /** B2 in customer mode. {@code replacesPolicyId} is set for a rewrite of a cancelled policy. */
    record ForCustomerRequest(String startDate, Boolean productLiability, Long replacesPolicyId) {}

    @PostMapping("/policies/preview")
    IssuePlan preview(@RequestBody PreviewRequest body) {
        return service.preview(date(body.startDate()).orElse(null), Boolean.TRUE.equals(body.productLiability()));
    }

    @PostMapping("/policies")
    ResponseEntity<IssuePolicyService.Issued> issue(
            @RequestBody IssueRequest body, @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        var cmd = new IssuePolicyCommand(body.legalName(), body.businessDescription(), body.ownerName(),
                body.ownerEmail(), date(body.startDate()).orElse(null), Boolean.TRUE.equals(body.productLiability()));
        return created(service.issue(cmd, broker.id()));
    }

    /** The customer B2 shows read-only in customer mode. */
    @GetMapping("/customers/{customerId}")
    CustomerRepository.Row customer(@PathVariable long customerId) {
        return customers.find(customerId);
    }

    @PostMapping("/customers/{customerId}/policies/preview")
    IssuePlan previewForCustomer(@PathVariable long customerId, @RequestBody ForCustomerRequest body) {
        return service.previewForCustomer(customerId, command(body));
    }

    @PostMapping("/customers/{customerId}/policies")
    ResponseEntity<IssuePolicyService.Issued> issueForCustomer(@PathVariable long customerId,
            @RequestBody ForCustomerRequest body, @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        return created(service.issueForCustomer(customerId, command(body), broker.id()));
    }

    private static IssueForCustomerCommand command(ForCustomerRequest body) {
        return new IssueForCustomerCommand(date(body.startDate()).orElse(null),
                Boolean.TRUE.equals(body.productLiability()), body.replacesPolicyId());
    }

    private static ResponseEntity<IssuePolicyService.Issued> created(IssuePolicyService.Issued issued) {
        return ResponseEntity.created(URI.create("/api/broker/policies/" + issued.policyId())).body(issued);
    }

    private static Optional<LocalDate> date(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty(); // reported with the other fields as "Pick the day cover should start."
        }
    }
}
