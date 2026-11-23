package app.plainly.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a {@code yyyy-MM-dd} request date, and anything it can't read (blank, "2026-13-45", a number) as null, so the
 * field's {@code @NotNull} answers with that field's message instead of a 400 for the whole body. Applied per field
 * with {@code @JsonDeserialize(using = LenientDate.class)}; other dates keep Jackson's strict reading.
 */
public class LenientDate extends ValueDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            p.skipChildren(); // an object or array is skipped whole, so the rest of the body is still read
            return null;
        }
        String value = p.getString().strip();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
