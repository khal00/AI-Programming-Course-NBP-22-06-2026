package pl.nbp.copilot.web;

import pl.nbp.copilot.web.dto.ErrorResponse;

import java.util.List;

/**
 * Thrown when multipart form validation fails (one or more field errors).
 * Maps to HTTP 400 via GlobalExceptionHandler.
 *
 * <p>ADR-001 §3, TAC-001-01.
 */
public class ValidationException extends RuntimeException {

    private final List<ErrorResponse.FieldError> fieldErrors;

    public ValidationException(List<ErrorResponse.FieldError> fieldErrors) {
        super("Wystąpiły błędy walidacji formularza.");
        this.fieldErrors = fieldErrors;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
