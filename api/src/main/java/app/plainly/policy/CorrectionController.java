package app.plainly.policy;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import app.plainly.common.LenientDate;
import app.plainly.common.OnSave;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

/** B4 "Change this policy", Correct tab: the live preview and the save. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies/{policyId}/corrections")
class CorrectionController {

    private final CorrectCoverageService service;

    CorrectionController(CorrectCoverageService service) {
        this.service = service;
    }

    /**
     * Checked before the service runs (input-validation spec). An unreadable date reads as null (LenientDate), so it
     * is a field message, not a 400. The reason is needed only to save ({@link OnSave}), not for the live preview.
     * The service keeps the rules that need the policy and its ledger.
     */
    record CorrectionRequest(
            @NotNull(message = CorrectCoverageService.NO_START) @JsonDeserialize(using = LenientDate.class) LocalDate startDate,
            @NotBlank(groups = OnSave.class, message = CorrectCoverageService.NO_REASON)
                    @Size(max = CorrectCoverageService.REASON_MAX, groups = OnSave.class, message = CorrectCoverageService.REASON_TOO_LONG)
                    String reason) {

        CorrectCoverageCommand command() {
            return new CorrectCoverageCommand(startDate, reason);
        }
    }

    @PostMapping("/preview")
    CorrectionPlan preview(@PathVariable long policyId, @Valid @RequestBody CorrectionRequest body) {
        return service.preview(policyId, body.startDate());
    }

    @PostMapping
    ResponseEntity<CorrectCoverageService.Corrected> correct(@PathVariable long policyId,
            @Validated({Default.class, OnSave.class}) @RequestBody CorrectionRequest body,
            @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        CorrectCoverageService.Corrected saved = service.correct(policyId, body.command(), broker.id());
        return ResponseEntity.created(URI.create("/api/broker/policies/" + policyId)).body(saved);
    }
}
