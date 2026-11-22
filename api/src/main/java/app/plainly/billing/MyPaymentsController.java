package app.plainly.billing;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** The policyholder's pay page (P5). Under {@code /api/me}, so only a signed-in policyholder gets here. */
@RestController
class MyPaymentsController {

    private final PaymentService payments;

    MyPaymentsController(PaymentService payments) {
        this.payments = payments;
    }

    /** Opens a payment for the balance due. Any request body is ignored: the server sets the amount. */
    @PostMapping("/api/me/payments")
    PaymentService.Started start(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me) {
        return payments.start(me);
    }

    /** Polled by the pay page until Stripe settles the payment. */
    @GetMapping("/api/me/payments/{id}")
    PaymentService.PaymentView view(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me, @PathVariable long id) {
        return payments.view(me, id);
    }

    @PostMapping("/api/me/payments/{id}/saved-card")
    PaymentService.SavedCardConfirm payWithSavedCard(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me,
            @PathVariable long id) {
        return payments.payWithSavedCard(me, id);
    }
}
