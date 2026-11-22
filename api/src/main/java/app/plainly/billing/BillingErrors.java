package app.plainly.billing;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns a {@link PaymentProblem} into the API's usual error body. */
@RestControllerAdvice
class BillingErrors {

    @ExceptionHandler(PaymentProblem.class)
    ResponseEntity<Map<String, String>> problem(PaymentProblem p) {
        return ResponseEntity.status(p.status()).body(Map.of("error", p.error(), "message", p.getMessage()));
    }
}
