package app.plainly.policy;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B4 "Change this policy", Add cover tab: the live price and the save button. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies/{policyId}/coverages")
class AddCoverageController {

    private final AddCoverageService service;

    AddCoverageController(AddCoverageService service) {
        this.service = service;
    }

    /** Plain strings, so an unknown cover or a bad date becomes a field message, not a 400. */
    record AddCoverageRequest(String kind, String startDate) {

        AddCoverageCommand command() {
            return new AddCoverageCommand(coverKind(kind), date(startDate));
        }
    }

    @PostMapping("/preview")
    AddCoveragePlan preview(@PathVariable long policyId, @RequestBody AddCoverageRequest body) {
        AddCoverageCommand cmd = body.command();
        return service.preview(policyId, cmd.kind(), cmd.startDate());
    }

    @PostMapping
    ResponseEntity<AddCoverageService.Added> add(@PathVariable long policyId, @RequestBody AddCoverageRequest body,
            @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        AddCoverageService.Added added = service.add(policyId, body.command(), broker.id());
        return ResponseEntity.created(URI.create("/api/broker/policies/" + policyId)).body(added);
    }

    /** Null for anything we don't sell; the service answers "Pick a cover to add." */
    private static CoverKind coverKind(String value) {
        return Arrays.stream(CoverKind.values()).filter(k -> k.name().equals(value)).findFirst().orElse(null);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null; // reported as "Pick the endorsement's effective date."
        }
    }
}
