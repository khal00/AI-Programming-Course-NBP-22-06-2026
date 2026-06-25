package pl.nbp.copilot.orchestration;

import org.springframework.stereotype.Service;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.image.ImagePayload;
import pl.nbp.copilot.image.ImageService;
import pl.nbp.copilot.image.ImageUnreadableException;
import pl.nbp.copilot.llm.LlmService;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates the two-stage LLM pipeline for new cases and handles chat-turn streaming.
 *
 * <p>New-case flow (ADR-000 §9.3):
 * validate image → analyzeImage → check readability → decide → assemble message → create session.
 *
 * <p>Chat-turn flow: load session → append user message → stream reply → append assistant message.
 *
 * <p>ADR-001 §3, TAC-06..11.
 */
@Service
public class CaseOrchestrationService {

    private final ImageService imageService;
    private final LlmService llmService;
    private final PolicyProvider policyProvider;
    private final SessionStore sessionStore;
    private final MessageAssembler messageAssembler;

    public CaseOrchestrationService(ImageService imageService,
                                    LlmService llmService,
                                    PolicyProvider policyProvider,
                                    SessionStore sessionStore,
                                    MessageAssembler messageAssembler) {
        this.imageService = imageService;
        this.llmService = llmService;
        this.policyProvider = policyProvider;
        this.sessionStore = sessionStore;
        this.messageAssembler = messageAssembler;
    }

    /**
     * Runs the new-case pipeline: image validation → analysis → decision → first message → session.
     *
     * @param intake     the submitted intake form data (with imageFilename)
     * @param imageBytes raw uploaded image bytes
     * @return result containing sessionId, caseSummary, verdict, firstMessage
     * @throws pl.nbp.copilot.image.ImageValidationException if the image is invalid
     * @throws ImageUnreadableException                      if the LLM cannot analyze the image
     * @throws pl.nbp.copilot.llm.LlmUnavailableException   if the LLM is unavailable
     */
    public NewCaseResult handleNewCase(CaseIntake intake, byte[] imageBytes) {
        // Step 1: validate and compress image
        ImagePayload payload = imageService.process(intake.imageContentType(), imageBytes);

        // Step 2: analyze image
        ImageFindings findings = llmService.analyzeImage(
                intake.caseType(), intake, payload.base64(), payload.contentType());

        // Step 3: guard on readability
        if (!findings.readable()) {
            throw new ImageUnreadableException();
        }

        // Step 4: get policy and decide
        String policyText = policyProvider.policyFor(intake.caseType());
        DecisionResult decision = llmService.decide(intake.caseType(), intake, findings, policyText);

        // Step 5: assemble messages
        String firstMessage = messageAssembler.buildFirstMessage(intake, findings, decision);
        String systemMessage = messageAssembler.buildSystemMessage(intake);

        // Step 6: seed session
        Session.CaseSummary caseSummary = new Session.CaseSummary(
                intake.caseType(), intake.category(),
                intake.modelName(), intake.purchaseDate());

        List<ChatMessage> initialMessages = List.of(
                new ChatMessage(ChatMessage.Role.SYSTEM, systemMessage, Instant.now()),
                new ChatMessage(ChatMessage.Role.ASSISTANT, firstMessage, Instant.now())
        );

        Session session = sessionStore.create(caseSummary, findings, decision, initialMessages);

        return new NewCaseResult(session.getSessionId(), caseSummary, decision.verdict(), firstMessage);
    }

    /**
     * Streams the assistant's reply for a chat follow-up turn.
     *
     * @param sessionId session ID from the new-case response
     * @param message   user's follow-up message (must be non-blank)
     * @param onToken   callback invoked for each streamed token
     * @throws IllegalArgumentException   if message is blank/null
     * @throws SessionNotFoundException   if the session is unknown or expired
     * @throws pl.nbp.copilot.llm.LlmUnavailableException if the LLM is unavailable
     */
    public void streamReply(String sessionId, String message, Consumer<String> onToken) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Wiadomość nie może być pusta.");
        }

        Session session = sessionStore.get(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Append user message
        ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, message.strip(), Instant.now());
        sessionStore.appendMessage(sessionId, userMsg);

        // Assemble full history (session messages + just-appended user message)
        List<ChatMessage> history = new ArrayList<>(session.getMessages());
        history.add(userMsg);

        // Stream reply, accumulating all tokens
        StringBuilder assembled = new StringBuilder();
        llmService.streamChat(history, token -> {
            assembled.append(token);
            onToken.accept(token);
        });

        // Append assembled assistant reply
        ChatMessage assistantMsg = new ChatMessage(
                ChatMessage.Role.ASSISTANT, assembled.toString(), Instant.now());
        sessionStore.appendMessage(sessionId, assistantMsg);
    }
}
