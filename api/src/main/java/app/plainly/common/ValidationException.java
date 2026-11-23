package app.plainly.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Input the user can fix: one plain-language message per form field, in form order. */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ValidationException(Map<String, String> fields) {
        super(messageFor(fields.size()));
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** "1 thing to fix before you can save." The one wording for service and edge (input-validation) errors. */
    public static String messageFor(int count) {
        return count + (count == 1 ? " thing" : " things") + " to fix before you can save.";
    }

    public Map<String, String> fields() {
        return fields;
    }
}
