package app.plainly.policy;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import app.plainly.common.LenientDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

/** B4 "Change this policy", Add cover tab: the live price and the save button. Broker only (filter). */
@RestController
@RequestMapping("/api/broker/policies/{policyId}/coverages")
class AddCoverageController {

    private final AddCoverageService service;

    AddCoverageController(AddCoverageService service) {
        this.service = service;
    }

    /**
     * Checked before the service runs (input-validation spec). A cover we don't know by name (CoverKindName) and an
     * unreadable date (LenientDate) both read as null, so each is a field message, not a 400. The service keeps the rules that need the policy: only product
     * liability can be added, once, inside the policy period.
     */
    record AddCoverageRequest(
            @NotNull(message = AddCoverageService.NO_KIND)
                    @JsonDeserialize(using = CoverKindName.class) CoverKind kind,
            @NotNull(message = AddCoverageService.NO_START) @JsonDeserialize(using = LenientDate.class)
                    LocalDate startDate) {

        AddCoverageCommand command() {
            return new AddCoverageCommand(kind, startDate);
        }
    }

    @PostMapping("/preview")
    AddCoveragePlan preview(@PathVariable long policyId, @Valid @RequestBody AddCoverageRequest body) {
        return service.preview(policyId, body.kind(), body.startDate());
    }

    @PostMapping
    ResponseEntity<AddCoverageService.Added> add(@PathVariable long policyId, @Valid @RequestBody AddCoverageRequest body,
            @RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser broker) {
        AddCoverageService.Added added = service.add(policyId, body.command(), broker.id());
        return ResponseEntity.created(URI.create("/api/broker/policies/" + policyId)).body(added);
    }
}
