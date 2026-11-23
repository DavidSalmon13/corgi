package app.plainly.policy;

import java.util.Arrays;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a cover kind only by its exact name ("PRODUCT_LIABILITY"). Anything else, including a number, which Jackson
 * would otherwise read as the enum's position, becomes null, so {@code @NotNull} answers "Pick a cover to add."
 * instead of quietly picking a cover by its place in the list.
 */
public class CoverKindName extends ValueDeserializer<CoverKind> {

    @Override
    public CoverKind deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            p.skipChildren(); // an object or array is skipped whole, so the rest of the body is still read
            return null;
        }
        String name = p.getString();
        return Arrays.stream(CoverKind.values()).filter(k -> k.name().equals(name)).findFirst().orElse(null);
    }
}
