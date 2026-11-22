package app.plainly.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error bodies the web app can show as they are:
 * {@code {"error":"validation","message":"3 things to fix before you can save.","fields":{...}}}.
 */
@RestControllerAdvice
class ApiErrorHandler {

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<Map<String, Object>> validation(ValidationException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation");
        body.put("message", e.getMessage());
        body.put("fields", e.fields());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "bad_request", "message", "The request body isn't valid JSON."));
    }
}
