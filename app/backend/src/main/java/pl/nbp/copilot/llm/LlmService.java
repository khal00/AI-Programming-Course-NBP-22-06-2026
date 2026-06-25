package pl.nbp.copilot.llm;

import pl.nbp.copilot.domain.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service interface for all LLM interactions.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link DefaultLlmService} — active under all profiles except {@code mock-llm};
 *       connects to OpenRouter via the official openai-java SDK.</li>
 *   <li>{@link MockLlmService} — active under the {@code mock-llm} profile;
 *       returns deterministic responses based on the image filename.</li>
 * </ul>
 *
 * <p>ADR-002 §4; TAC-002-01..07.
 */
public interface LlmService {

    /**
     * Analyzes the uploaded image and returns structured findings.
     *
     * @param caseType     the case type (RETURN or COMPLAINT)
     * @param intake       the customer's intake form data
     * @param imageBase64  Base64-encoded image bytes (no data URI prefix)
     * @param contentType  MIME type of the image (e.g. {@code image/jpeg})
     * @return structured image analysis result
     * @throws LlmUnavailableException if the LLM cannot produce a valid response
     */
    ImageFindings analyzeImage(CaseType caseType, CaseIntake intake,
                               String imageBase64, String contentType);

    /**
     * Applies the policy to the image findings and returns a decision.
     *
     * @param caseType   the case type
     * @param intake     the customer's intake form data
     * @param findings   the result of the image analysis
     * @param policyText the full markdown text of the applicable policy
     * @return structured decision result
     * @throws LlmUnavailableException if the LLM cannot produce a valid response
     */
    DecisionResult decide(CaseType caseType, CaseIntake intake,
                          ImageFindings findings, String policyText);

    /**
     * Streams the assistant's reply token-by-token to the provided callback.
     *
     * <p>This method is synchronous — it blocks until all tokens are emitted or an error occurs.
     * The caller receives tokens via {@code onToken.accept(token)} as they arrive.
     *
     * @param history the full conversation history including system and prior turns
     * @param onToken callback invoked once per token as they stream in
     * @throws LlmUnavailableException if the streaming call fails
     */
    void streamChat(List<ChatMessage> history, Consumer<String> onToken);
}
