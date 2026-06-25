package pl.nbp.copilot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.prompt.PromptTemplateProvider;

import java.util.List;
import java.util.function.Consumer;

/**
 * Real LLM service connecting to OpenRouter via the openai-java SDK (ADR-002).
 *
 * <p>Active under all profiles except {@code mock-llm}.
 *
 * <p>Retry strategy: up to 2 retries (3 total attempts) with exponential-ish backoff
 * (1 s × attempt index) for 429/5xx and connection errors.
 * After exhaustion, throws {@link LlmUnavailableException}.
 *
 * <p>JSON parsing: the LLM is asked to return JSON via {@code response_format: json_object}.
 * The raw content is deserialized with Jackson. On malformed JSON, exactly one re-ask is made;
 * on continued failure, {@link LlmUnavailableException} is thrown.
 *
 * <p>Contradiction enforcement (AC-20, ADR-002 §5):
 * If {@link ImageFindings#matchesDeclaredCase()} is {@code false} and the LLM returned
 * {@link Verdict#APPROVE} or {@link Verdict#REJECT}, the verdict is coerced to
 * {@link Verdict#NEEDS_INFO} and the discrepancy fields are filled from the findings.
 *
 * <p>TAC-002-02..07.
 */
@Service
@Profile("!mock-llm")
public class DefaultLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmService.class);

    /** Maximum total attempts (1 original + 2 retries). */
    private static final int MAX_ATTEMPTS = 3;

    /** Maximum re-ask attempts for malformed JSON (1 original parse + 1 re-ask). */
    private static final int MAX_PARSE_ATTEMPTS = 2;

    private final OpenAIClient client;
    private final AppProperties props;
    private final PromptTemplateProvider promptProvider;
    private final ObjectMapper objectMapper;

    @Autowired
    public DefaultLlmService(OpenRouterClientFactory clientFactory,
                             AppProperties props,
                             PromptTemplateProvider promptProvider,
                             ObjectMapper objectMapper) {
        this.client         = clientFactory.client();
        this.props          = props;
        this.promptProvider = promptProvider;
        this.objectMapper   = objectMapper;
    }

    /**
     * Package-private constructor for tests that provide an OpenAIClient directly
     * (e.g. configured against MockWebServer).
     */
    DefaultLlmService(OpenAIClient client,
                      AppProperties props,
                      PromptTemplateProvider promptProvider,
                      ObjectMapper objectMapper) {
        this.client         = client;
        this.props          = props;
        this.promptProvider = promptProvider;
        this.objectMapper   = objectMapper;
    }

    // =========================================================================
    // analyzeImage
    // =========================================================================

    @Override
    public ImageFindings analyzeImage(CaseType caseType, CaseIntake intake,
                                      String imageBase64, String contentType) {
        String prompt = promptProvider.imagePrompt(caseType, intake);
        String visionModel = props.openrouter().modelVision();

        // Build the user message with text + image content parts
        ChatCompletionContentPartText textPart = ChatCompletionContentPartText.builder()
                .text(prompt)
                .build();

        ChatCompletionContentPartImage imagePart = ChatCompletionContentPartImage.builder()
                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                        .url("data:" + contentType + ";base64," + imageBase64)
                        .build())
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(visionModel)
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .addUserMessageOfArrayOfContentParts(List.of(
                        ChatCompletionContentPart.ofText(textPart),
                        ChatCompletionContentPart.ofImageUrl(imagePart)
                ))
                .build();

        return callWithRetryAndParse(params, ImageFindings.class, "analyzeImage");
    }

    // =========================================================================
    // decide
    // =========================================================================

    @Override
    public DecisionResult decide(CaseType caseType, CaseIntake intake,
                                 ImageFindings findings, String policyText) {
        String imageFindings = serializeFindings(findings);
        String prompt = promptProvider.decisionPrompt(caseType, intake, imageFindings, policyText);
        String reasoningModel = props.openrouter().modelReasoning();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(reasoningModel)
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .addSystemMessage(prompt)
                .build();

        DecisionResult result = callWithRetryAndParse(params, DecisionResult.class, "decide");

        // Validate verdict is a known enum value (parsing should have caught this, but be explicit)
        if (result.verdict() == null) {
            throw new LlmUnavailableException();
        }

        // Contradiction enforcement: AC-20, ADR-002 §5
        // If image contradicts the declared case, the verdict MUST be NEEDS_INFO or ESCALATE.
        if (!findings.matchesDeclaredCase()
                && (result.verdict() == Verdict.APPROVE || result.verdict() == Verdict.REJECT)) {
            String discrepancy = resolveDiscrepancyExplanation(findings, result);
            result = new DecisionResult(
                    Verdict.NEEDS_INFO,
                    result.justification(),
                    result.nextSteps(),
                    true,
                    discrepancy,
                    result.disclaimer()
            );
        }

        return result;
    }

    // =========================================================================
    // streamChat
    // =========================================================================

    @Override
    public void streamChat(List<ChatMessage> history, Consumer<String> onToken) {
        String reasoningModel = props.openrouter().modelReasoning();

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(reasoningModel);

        for (ChatMessage msg : history) {
            switch (msg.role()) {
                case SYSTEM    -> builder.addSystemMessage(msg.content());
                case USER      -> builder.addUserMessage(msg.content());
                case ASSISTANT -> builder.addAssistantMessage(msg.content());
            }
        }

        ChatCompletionCreateParams params = builder.build();
        streamWithRetry(params, onToken);
    }

    // =========================================================================
    // Retry-aware LLM call with JSON parsing
    // =========================================================================

    /**
     * Executes a chat completion call with retry on HTTP 429/5xx/IO errors,
     * followed by JSON parsing with one re-ask on malformed output.
     */
    private <T> T callWithRetryAndParse(ChatCompletionCreateParams params,
                                        Class<T> targetClass,
                                        String operationName) {
        for (int attempt = 0; attempt < MAX_PARSE_ATTEMPTS; attempt++) {
            String rawContent = callWithRetry(params, operationName);

            try {
                T result = objectMapper.readValue(rawContent, targetClass);
                validateNotNull(result, operationName);
                return result;
            } catch (JsonProcessingException e) {
                log.warn("[{}] Malformed JSON response (attempt {}/{}): {}",
                        operationName, attempt + 1, MAX_PARSE_ATTEMPTS, e.getMessage());
                if (attempt + 1 >= MAX_PARSE_ATTEMPTS) {
                    throw new LlmUnavailableException(e);
                }
                // Re-ask: append the bad response and ask for correction
                params = appendReAskMessage(params, rawContent);
            }
        }
        // Should never reach here, but compiler requires it
        throw new LlmUnavailableException();
    }

    /**
     * Executes the HTTP call with retry logic for 429/5xx and connection failures.
     *
     * @return the raw content string from the first choice message
     */
    private String callWithRetry(ChatCompletionCreateParams params, String operationName) {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleepBetweenAttempts(attempt);
            }
            try {
                ChatCompletion completion = client.chat().completions().create(params);
                return extractContent(completion);
            } catch (OpenAIException e) {
                lastException = e;
                log.warn("[{}] OpenAI API error on attempt {}/{}: {}",
                        operationName, attempt + 1, MAX_ATTEMPTS, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("[{}] Network/IO error on attempt {}/{}: {}",
                        operationName, attempt + 1, MAX_ATTEMPTS, e.getMessage());
            }
        }

        throw new LlmUnavailableException(lastException);
    }

    /**
     * Streams a chat completion with retry on failure.
     */
    private void streamWithRetry(ChatCompletionCreateParams params, Consumer<String> onToken) {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleepBetweenAttempts(attempt);
            }
            try {
                try (var stream = client.chat().completions().createStreaming(params)) {
                    stream.stream().forEach(chunk ->
                            chunk.choices().forEach(choice ->
                                    choice.delta().content().ifPresent(onToken)));
                }
                return; // success
            } catch (OpenAIException e) {
                lastException = e;
                log.warn("[streamChat] OpenAI API error on attempt {}/{}: {}",
                        attempt + 1, MAX_ATTEMPTS, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("[streamChat] Network/IO error on attempt {}/{}: {}",
                        attempt + 1, MAX_ATTEMPTS, e.getMessage());
            }
        }

        throw new LlmUnavailableException(lastException);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String extractContent(ChatCompletion completion) {
        return completion.choices().get(0)
                .message()
                .content()
                .orElseThrow(LlmUnavailableException::new);
    }

    private <T> void validateNotNull(T result, String operationName) {
        if (result == null) {
            throw new LlmUnavailableException();
        }
    }

    private String serializeFindings(ImageFindings findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            return findings.toString();
        }
    }

    /**
     * Appends a re-ask message to an existing params, requesting corrected JSON.
     * Rebuilds the params from the existing message list to stay compatible with the SDK.
     */
    private ChatCompletionCreateParams appendReAskMessage(ChatCompletionCreateParams params,
                                                           String badResponse) {
        // Rebuild from the existing messages list and add re-ask context
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(params.model().toString())
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .messages(params.messages());

        builder.addAssistantMessage(badResponse)
               .addUserMessage("Odpowiedź nie była prawidłowym JSON. " +
                       "Zwróć WYŁĄCZNIE poprawny obiekt JSON zgodny z opisanym schematem, bez dodatkowego tekstu.");

        return builder.build();
    }

    /**
     * Determines the discrepancy explanation to use when enforcing contradiction rule.
     * Prefers the discrepancyNote from findings; falls back to discrepancyExplanation
     * from the LLM result; as a last resort uses a generic Polish message.
     */
    private String resolveDiscrepancyExplanation(ImageFindings findings,
                                                  DecisionResult llmResult) {
        if (findings.discrepancyNote() != null && !findings.discrepancyNote().isBlank()) {
            return findings.discrepancyNote();
        }
        if (llmResult.discrepancyExplanation() != null
                && !llmResult.discrepancyExplanation().isBlank()) {
            return llmResult.discrepancyExplanation();
        }
        return "Zdjęcie nie jest spójne z rodzajem zgłoszenia. Wymagane dodatkowe wyjaśnienie.";
    }

    private void sleepBetweenAttempts(int attemptIndex) {
        try {
            Thread.sleep(1000L * attemptIndex);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(ie);
        }
    }
}
