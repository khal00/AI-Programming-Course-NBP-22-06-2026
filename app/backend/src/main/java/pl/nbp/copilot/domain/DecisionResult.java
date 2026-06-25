package pl.nbp.copilot.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured output from the reasoning decision LLM call (ADR-002 §4).
 * This is an internal record — never exposed raw to the client (AC-29).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DecisionResult(

        /** One of four verdicts (AC-16). */
        Verdict verdict,

        /**
         * Polish text justifying the verdict, referencing form data, image findings,
         * and applicable policy rules (AC-19).
         */
        String justification,

        /** Ordered list of concrete next steps for the customer (AC-23). */
        List<String> nextSteps,

        /**
         * True when the image contradicts the declared case type.
         * In that case verdict must be NEEDS_INFO or ESCALATE (AC-20, ADR-002 §5).
         */
        boolean discrepancyNoted,

        /** Non-null when discrepancyNoted=true; explains the contradiction in Polish. */
        String discrepancyExplanation,

        /**
         * Mandatory Polish non-binding disclaimer (PRD §11, AC-21).
         * E.g. "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi."
         */
        String disclaimer

) {}
