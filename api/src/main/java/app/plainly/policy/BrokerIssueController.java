package app.plainly.policy;

import app.plainly.auth.DemoAuthFilter;
import app.plainly.auth.DemoUser;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B2 "New customer and policy": the live price preview and the create button. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies")
class BrokerIssueController {

    private final IssuePolicyService service;

    BrokerIssueController(IssuePolicyService service) {
        this.service = service;
    }

    /** A missing productLiability means "no extra cover". Dates arrive as yyyy-MM-dd strings so a bad one becomes a field message, not a 400. */
    record PreviewRequest(String startDate, Boolean productLiability) {}

    record IssueRequest(String legalName, String businessDescription, String ownerName, String ownerEmail,
            String startDate, Boolean productLiability) {}

    @PostMapping("/preview")
    IssuePlan preview(@RequestBody PreviewRequest body) {
        return service.preview(date(body.startDate()).orElse(null), Boolean.TRUE.equals(body.productLiability()));
    }

    @PostMapping
    ResponseEntity<IssuePolicyService.Issued> issue(
            @RequestBody IssueRequest body, @RequestAttribute(DemoAuthFilter.CURRENT_USER) DemoUser broker) {
        var cmd = new IssuePolicyCommand(body.legalName(), body.businessDescription(), body.ownerName(),
                body.ownerEmail(), date(body.startDate()).orElse(null), Boolean.TRUE.equals(body.productLiability()));
        IssuePolicyService.Issued issued = service.issue(cmd, broker.id());
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
