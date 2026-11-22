package app.plainly.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Input the user can fix: one plain-language message per form field, in form order. */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ValidationException(Map<String, String> fields) {
        super(fields.size() + (fields.size() == 1 ? " thing" : " things") + " to fix before you can save.");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> fields() {
        return fields;
    }
}
