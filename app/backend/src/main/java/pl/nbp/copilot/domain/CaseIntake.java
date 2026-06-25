package pl.nbp.copilot.domain;

import java.time.LocalDate;

/**
 * Immutable value object capturing the customer's intake form submission (ADR-000 §5).
 * Raw image bytes are NOT stored here — they are transient, passed through the pipeline
 * and discarded after image analysis (ADR-004 / privacy constraint).
 * The content type and a reference name are retained for context only.
 */
public record CaseIntake(

        /** Type of case: complaint or return. */
        CaseType caseType,

        /** Equipment category selected by the customer. */
        EquipmentCategory category,

        /** Free-text model or product name (non-blank). */
        String modelName,

        /** Purchase date (not in the future). */
        LocalDate purchaseDate,

        /** Customer's description of the issue; required when caseType=COMPLAINT, optional for RETURN. */
        String reason,

        /** MIME type of the uploaded image (e.g. "image/jpeg"). Retained for metadata; no bytes. */
        String imageContentType,

        /**
         * Original filename of the uploaded image.
         * Used by the mock-llm profile to derive the scenario from the filename (see api-contract.md).
         */
        String imageFilename

) {}
