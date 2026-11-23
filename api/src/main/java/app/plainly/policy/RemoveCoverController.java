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

/** B4 "Change this policy", Remove tab: the live preview and the save. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies/{policyId}/cover-removal")
class RemoveCoverController {

    private final RemoveCoverService service;

    RemoveCoverController(RemoveCoverService service) {
        this.service = service;
    }

    /**
     * Checked before the service runs (input-validation spec). An unreadable date reads as null (LenientDate), so it
     * is a field message, not a 400. The reason is needed only to save ({@link OnSave}), not for the live preview.
     * The service keeps the rules that need the policy and its ledger.
     */
    record RemovalRequest(
            @NotNull(message = RemoveCoverService.NO_DATE) @JsonDeserialize(using = LenientDate.class) LocalDate endDate,
            @NotBlank(groups = OnSave.class, message = RemoveCoverService.NO_REASON)
                    @Size(max = RemoveCoverService.REASON_MAX, groups = OnSave.class, message = RemoveCoverService.REASON_TOO_LONG)
                    String reason) {

        RemoveCoverCommand command() {
            return new RemoveCoverCommand(endDate, reason);
        }
    }

    @PostMapping("/preview")
    RemoveCoverPlan preview(@PathVariable long policyId, @Valid @RequestBody RemovalRequest body) {
        return service.preview(policyId, body.endDate());
    }

    @PostMapping
    ResponseEntity<RemoveCoverService.Removed> remove(@PathVariable long policyId,
            @Validated({Default.class, OnSave.class}) @RequestBody RemovalRequest body,
            @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        RemoveCoverService.Removed saved = service.remove(policyId, body.command(), broker.id());
        return ResponseEntity.created(URI.create("/api/broker/policies/" + policyId)).body(saved);
    }
}
