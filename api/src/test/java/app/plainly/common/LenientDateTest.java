package app.plainly.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

/** A date the form couldn't fill becomes null, so {@code @NotNull} reports it on its field instead of a 400. */
// covers input-validation AC4, T2
class LenientDateTest {

    record Form(@JsonDeserialize(using = LenientDate.class) LocalDate startDate, String next) {}

    final JsonMapper json = JsonMapper.builder().build();

    Form read(String startDate) {
        return json.readValue("{\"startDate\":" + startDate + ",\"next\":\"kept\"}", Form.class);
    }

    @Test
    void anIsoDateIsRead() {
        assertThat(read("\"2026-09-01\"").startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \"", "\"2026-13-45\"", "\"Sep 1\"", "\"2026-9-1\"", "42", "true",
            "{\"y\":2026}", "[2026,9,1]"})
    void anythingElseIsNullAndTheRestOfTheBodyIsStillRead(String value) {
        Form form = read(value);
        assertThat(form.startDate()).isNull();
        assertThat(form.next()).isEqualTo("kept");
    }
}
