package pl.nbp.copilot.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/cases/{sessionId}/messages.
 * ADR-001 §4.
 */
public record ChatRequest(

        /**
         * The customer's follow-up message.
         * Must be non-blank (TAC-001-05, ADR-001 §5).
         * Returns 400 when blank or null.
         */
        @NotBlank(message = "Wiadomość nie może być pusta.")
        String message

) {}
