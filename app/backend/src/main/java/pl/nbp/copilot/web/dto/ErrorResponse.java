package pl.nbp.copilot.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Uniform error response body (ADR-001 §4, AC-29).
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>Never contains stack traces, internal IDs, prompts, or raw provider payloads (AC-29).</li>
 *   <li>All user-facing strings ({@code message} and field messages) are in Polish (AC-28).</li>
 *   <li>{@code fieldErrors} is omitted from JSON when null/empty (only present on 400 validation).</li>
 * </ul>
 */
public record ErrorResponse(

        /**
         * Stable machine-readable error code.
         * Examples: VALIDATION_ERROR, IMAGE_UNREADABLE, LLM_UNAVAILABLE, SESSION_NOT_FOUND.
         */
        String code,

        /** Polish user-facing error summary. */
        String message,

        /**
         * Per-field validation errors; present only on 400 validation failures.
         * Null/empty is serialized as an empty array when fieldErrors are relevant,
         * or omitted when not applicable.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldError> fieldErrors

) {

    /**
     * A single field-level validation error.
     */
    public record FieldError(

            /** The name of the form field that failed validation. */
            String field,

            /** Polish description of the validation failure. */
            String message

    ) {}
}
