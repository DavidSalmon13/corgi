package app.plainly.billing;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** B3's "Retry refund" on a refund that didn't go through. Broker only (filter). */
@RestController
class RefundController {

    private final RefundService service;

    RefundController(RefundService service) {
        this.service = service;
    }

    @PostMapping("/api/broker/refunds/{refundId}/retry")
    RefundService.Attempted retry(@PathVariable long refundId) {
        return service.retry(refundId);
    }
}
