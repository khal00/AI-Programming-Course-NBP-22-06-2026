package pl.nbp.copilot.orchestration;

import pl.nbp.copilot.domain.Session;
import pl.nbp.copilot.domain.Verdict;

/**
 * Result returned by CaseOrchestrationService.handleNewCase.
 * Contains everything needed to build the CaseResponse (ADR-001 §4).
 */
public record NewCaseResult(
        String sessionId,
        Session.CaseSummary caseSummary,
        Verdict verdict,
        String firstMessage
) {}
