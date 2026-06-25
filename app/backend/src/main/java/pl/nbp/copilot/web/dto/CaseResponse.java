package pl.nbp.copilot.web.dto;

import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.Verdict;

import java.time.LocalDate;

/**
 * Response body for POST /api/cases (201 Created).
 * ADR-001 §4, ADR-000 §6.
 *
 * <p>The frontend uses {@code caseTypeLabel} and {@code categoryLabel} to display
 * Polish strings without a separate lookup (AC-28).
 */
public record CaseResponse(

        /** Opaque session identifier for follow-up chat calls. */
        String sessionId,

        /** Compact summary of the submitted intake form (no raw image). */
        CaseSummary caseSummary,

        /** AI-generated verdict enum value. */
        Verdict verdict,

        /**
         * The agent's first message in Markdown, containing: greeting, verdict,
         * justification, next steps, and mandatory disclaimer (AC-23, AC-21).
         */
        String firstMessage

) {

    /**
     * Compact, displayable case summary.
     * Carries both enum codes (for logic) and Polish labels (for display — AC-28).
     */
    public record CaseSummary(

            /** Enum code (e.g. COMPLAINT). */
            CaseType caseType,

            /** Polish label (e.g. "Reklamacja"). */
            String caseTypeLabel,

            /** Enum code (e.g. SMARTPHONES). */
            EquipmentCategory category,

            /** Polish label (e.g. "Smartfony"). */
            String categoryLabel,

            /** Free-text device model/name. */
            String modelName,

            /** Purchase date (ISO format in JSON: yyyy-MM-dd). */
            LocalDate purchaseDate

    ) {}
}
