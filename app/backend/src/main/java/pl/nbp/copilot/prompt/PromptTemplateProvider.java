package pl.nbp.copilot.prompt;

import pl.nbp.copilot.domain.CaseIntake;
import pl.nbp.copilot.domain.CaseType;

import java.util.List;

/**
 * Provides filled prompt strings for each stage of the LLM pipeline.
 *
 * <p>Templates are stored as editable resource files under
 * {@code src/main/resources/prompts/} so they can be updated without code changes.
 * Placeholders use the {@code {name}} convention and are all replaced before the
 * string is returned.
 *
 * <p>ADR-002 prompt contracts; AC-12, AC-17, AC-18, AC-21, TAC-04, TAC-09.
 */
public interface PromptTemplateProvider {

    /**
     * Returns a fully filled prompt instructing the vision model to analyze
     * the uploaded image and output the {@code ImageFindings} JSON schema.
     *
     * @param caseType the type of case
     * @param intake   the customer's intake form data
     * @return filled prompt text (Polish)
     */
    String imagePrompt(CaseType caseType, CaseIntake intake);

    /**
     * Returns a fully filled prompt instructing the reasoning model to apply
     * the policy and produce a {@code DecisionResult} JSON.
     *
     * @param caseType      the type of case
     * @param intake        the customer's intake form data
     * @param imageFindings serialized summary of the image analysis result
     * @param policyText    full markdown text of the applicable policy
     * @return filled prompt text (Polish)
     */
    String decisionPrompt(CaseType caseType, CaseIntake intake,
                          String imageFindings, String policyText);

    /**
     * Returns a fully filled system prompt for the follow-up chat agent.
     *
     * @param intake        the customer's intake form data
     * @param imageFindings serialized summary of the image analysis result
     * @param verdict       string representation of the initial verdict
     * @param justification the initial decision justification
     * @param nextSteps     the initial next steps
     * @return filled system prompt text (Polish)
     */
    String chatSystemPrompt(CaseIntake intake, String imageFindings,
                            String verdict, String justification,
                            List<String> nextSteps);
}
