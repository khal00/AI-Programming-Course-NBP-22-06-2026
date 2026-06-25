package pl.nbp.copilot.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured output from the multimodal image-analysis LLM call (ADR-002 §4).
 * This is an internal record — never exposed raw to the client (AC-29).
 * Null-valued optional fields are included in JSON for structured-output schema compliance.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ImageFindings(

        /** False means the image could not be confidently analyzed → orchestration returns 422. */
        boolean readable,

        /** Concise human-readable summary of the image (internal use only). */
        String description,

        /** Return-relevant: whether the device shows signs of previous use. */
        Boolean signsOfUse,

        /** Return-relevant: whether the device appears resellable. */
        Boolean resellable,

        /** Complaint-relevant: whether visible damage is present. */
        Boolean damagePresent,

        /** Complaint-relevant: free-text description of the damage type. */
        String damageType,

        /** Complaint-relevant: most likely cause category of any damage. */
        LikelyCause likelyCauseCategory,

        /** Whether the image findings are consistent with the declared case type. */
        boolean matchesDeclaredCase,

        /** Non-null when matchesDeclaredCase=false; describes the discrepancy. */
        String discrepancyNote,

        /** Confidence of the image analysis result. */
        Confidence confidence

) {

    /**
     * Likely cause category for damage identified during image analysis (ADR-002 §4).
     */
    public enum LikelyCause {
        MANUFACTURING,
        MECHANICAL,
        LIQUID,
        WEAR,
        UNKNOWN
    }

    /**
     * Confidence level of the image analysis result.
     */
    public enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }
}
