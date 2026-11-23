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

/** B4 "Change this policy", Cancel tab: the live preview and the save. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies/{policyId}/cancellation")
class CancellationController {

    private final CancelPolicyService service;

    CancellationController(CancelPolicyService service) {
        this.service = service;
    }

    /**
     * Checked before the service runs (input-validation spec). An unreadable date reads as null (LenientDate), so it
     * is a field message, not a 400. The reason is needed only to save ({@link OnSave}), not for the live preview.
     * The service keeps the rules that need the policy and its ledger.
     */
    record CancellationRequest(
            @NotNull(message = CancelPolicyService.NO_DATE) @JsonDeserialize(using = LenientDate.class) LocalDate cancelDate,
            @NotBlank(groups = OnSave.class, message = CancelPolicyService.NO_REASON)
                    @Size(max = CancelPolicyService.REASON_MAX, groups = OnSave.class, message = CancelPolicyService.REASON_TOO_LONG)
                    String reason) {

        CancelPolicyCommand command() {
            return new CancelPolicyCommand(cancelDate, reason);
        }
    }

    @PostMapping("/preview")
    CancelPolicyPlan preview(@PathVariable long policyId, @Valid @RequestBody CancellationRequest body) {
        return service.preview(policyId, body.cancelDate());
    }

    @PostMapping
    ResponseEntity<CancelPolicyService.Cancelled> cancel(@PathVariable long policyId,
            @Validated({Default.class, OnSave.class}) @RequestBody CancellationRequest body,
            @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        CancelPolicyService.Cancelled saved = service.cancel(policyId, body.command(), broker.id());
        return ResponseEntity.created(URI.create("/api/broker/policies/" + policyId)).body(saved);
    }
}
