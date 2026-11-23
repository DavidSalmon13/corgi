package app.plainly.common;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /**
     * A request record's annotations failed (input-validation spec): the same body as a {@link ValidationException},
     * one message per field, in the order the record declares its fields, which is the form's order.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e) {
        return validation(new ValidationException(fieldsInFormOrder(e.getBindingResult())));
    }

    /** Annotations whose failure means "nothing was entered"; that message wins over a length or format one. */
    private static final Set<String> MISSING = Set.of("NotNull", "NotBlank", "NotEmpty");

    private static Map<String, String> fieldsInFormOrder(BindingResult result) {
        Map<String, FieldError> byField = new HashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            byField.merge(error.getField(), error, (kept, next) -> missing(next) && !missing(kept) ? next : kept);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Object target = result.getTarget();
        if (target != null && target.getClass().isRecord()) {
            for (RecordComponent component : target.getClass().getRecordComponents()) {
                FieldError error = byField.remove(component.getName());
                if (error != null) {
                    fields.put(component.getName(), error.getDefaultMessage());
                }
            }
        }
        byField.keySet().stream().sorted().forEach(field -> fields.put(field, byField.get(field).getDefaultMessage()));
        return fields;
    }

    private static boolean missing(FieldError error) {
        return MISSING.contains(error.getCode());
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
