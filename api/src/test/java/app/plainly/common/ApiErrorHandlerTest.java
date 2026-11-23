package app.plainly.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/** Edge validation answers with the same 422 body the services give, fields in the order the form shows them. */
// covers input-validation AC2, AC5, T3
class ApiErrorHandlerTest {

    record Form(String legalName, String ownerEmail, String startDate) {}

    @SuppressWarnings("unused")
    void save(Form body) {}

    final ApiErrorHandler handler = new ApiErrorHandler();

    static MethodArgumentNotValidException invalid(FieldError... errors) throws Exception {
        var result = new BeanPropertyBindingResult(new Form("", "x", null), "body");
        for (FieldError error : errors) {
            result.addError(error);
        }
        var parameter = new MethodParameter(ApiErrorHandlerTest.class.getDeclaredMethod("save", Form.class), 0);
        return new MethodArgumentNotValidException(parameter, result);
    }

    static FieldError error(String field, String code, String message) {
        return new FieldError("body", field, null, false, new String[] {code}, null, message);
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> fields(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, String>) response.getBody().get("fields");
    }

    @Test
    void answersTheSame422BodyWithFieldsInRecordOrder() throws Exception {
        var response = handler.invalidBody(invalid(
                error("startDate", "NotNull", "Pick the day cover should start."),
                error("legalName", "NotBlank", "Enter the business's legal name.")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).containsEntry("error", "validation")
                .containsEntry("message", "2 things to fix before you can save.");
        assertThat(fields(response).keySet()).containsExactly("legalName", "startDate");
        assertThat(fields(response)).containsEntry("startDate", "Pick the day cover should start.");
    }

    @Test
    void oneMessagePerFieldAndAMissingValueWinsOverItsLength() throws Exception {
        var response = handler.invalidBody(invalid(
                error("legalName", "Size", "Keep this under 200 characters."),
                error("legalName", "NotBlank", "Enter the business's legal name.")));

        assertThat(response.getBody()).containsEntry("message", "1 thing to fix before you can save.");
        assertThat(fields(response)).containsExactly(Map.entry("legalName", "Enter the business's legal name."));
    }

    @Test
    void theWordingIsTheSameAsTheServices() {
        assertThat(new ValidationException(Map.of("a", "x")).getMessage()).isEqualTo(ValidationException.messageFor(1));
        assertThat(ValidationException.messageFor(1)).isEqualTo("1 thing to fix before you can save.");
        assertThat(ValidationException.messageFor(3)).isEqualTo("3 things to fix before you can save.");
    }
}
