package app.plainly.policy;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B4 "Change this policy", Correct tab: the live preview and the save. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies/{policyId}/corrections")
class CorrectionController {

    private final CorrectCoverageService service;

    CorrectionController(CorrectCoverageService service) {
        this.service = service;
    }

    /** Plain strings, so a missing or unreadable date becomes a field message, not a 400. */
    record CorrectionRequest(String startDate, String reason) {

        CorrectCoverageCommand command() {
            return new CorrectCoverageCommand(date(startDate), reason);
        }
    }

    @PostMapping("/preview")
    CorrectionPlan preview(@PathVariable long policyId, @RequestBody CorrectionRequest body) {
        return service.preview(policyId, body.command().startDate());
    }

    @PostMapping
    ResponseEntity<CorrectCoverageService.Corrected> correct(@PathVariable long policyId,
            @RequestBody CorrectionRequest body, @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        CorrectCoverageService.Corrected saved = service.correct(policyId, body.command(), broker.id());
        return ResponseEntity.created(URI.create("/api/broker/policies/" + policyId)).body(saved);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null; // reported as "Pick the endorsement's real effective date."
        }
    }
}
