package pl.nbp.copilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.prompt.PromptTemplateProvider;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * TAC-002-02..07: DefaultLlmService integration tests against MockWebServer.
 * Tests are written BEFORE the implementation (TDD).
 */
class DefaultLlmServiceTest {

    private MockWebServer mockServer;
    private DefaultLlmService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        objectMapper = new ObjectMapper();

        String baseUrl = mockServer.url("/v1").toString();
        AppProperties props = buildProps(baseUrl, "test-key",
                "Hardware Service Decision Copilot", "http://localhost:4200");

        OpenRouterClientFactory factory = new OpenRouterClientFactory(props);

        PromptTemplateProvider promptProvider = new StubPromptTemplateProvider();

        service = new DefaultLlmService(factory.client(), props, promptProvider, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    // =========================================================================
    // TAC-002-02: analyzeImage sends base64 image part and uses vision model
    // =========================================================================

    @Test
    void analyzeImageSendsBase64ImagePartAndUsesVisionModel() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(validImageFindingsResponse())
                .addHeader("Content-Type", "application/json"));

        String imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAUA";
        String contentType = "image/jpeg";

        ImageFindings result = service.analyzeImage(
                CaseType.RETURN, buildIntake("clean.jpg"), imageBase64, contentType);

        // Verify result is parsed correctly
        assertThat(result).isNotNull();
        assertThat(result.readable()).isTrue();

        // Verify the request body contains the base64 image
        RecordedRequest request = mockServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        String requestBody = request.getBody().readUtf8();

        // Body should contain the data URI for the image
        assertThat(requestBody).contains("data:image/jpeg;base64," + imageBase64);

        // Body should reference the vision model
        assertThat(requestBody).contains("gpt-4o");
    }

    // =========================================================================
    // TAC-002-03: decide includes policy text and returns valid verdict enum
    // =========================================================================

    @Test
    void decideIncludesPolicyTextAndReturnsValidVerdictEnum() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(validDecisionResponse("APPROVE"))
                .addHeader("Content-Type", "application/json"));

        ImageFindings findings = buildFindings(true, true);
        DecisionResult result = service.decide(
                CaseType.RETURN, buildIntake("clean.jpg"), findings, "Polityka zwrotów testowa.");

        assertThat(result).isNotNull();
        assertThat(result.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(result.justification()).isNotBlank();

        // Verify policy text is sent in the request
        RecordedRequest request = mockServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody).contains("Polityka zwrotów testowa.");
    }

    // =========================================================================
    // TAC-002-04: matchesDeclaredCase=false → coerced to NEEDS_INFO
    // =========================================================================

    @Test
    void whenMatchesDeclaredCaseFalseVerdictIsCoercedToNeedsInfo() throws Exception {
        // LLM returns APPROVE but findings say matchesDeclaredCase=false
        mockServer.enqueue(new MockResponse()
                .setBody(validDecisionResponse("APPROVE"))
                .addHeader("Content-Type", "application/json"));

        // findings with matchesDeclaredCase=false and a discrepancyNote
        ImageFindings contradictingFindings = new ImageFindings(
                true, "Urządzenie ma ślady uszkodzeń.", false, null,
                true, "Pęknięty ekran", ImageFindings.LikelyCause.MECHANICAL,
                false, "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu",
                ImageFindings.Confidence.MEDIUM
        );

        DecisionResult result = service.decide(
                CaseType.RETURN, buildIntake("contradiction.jpg"),
                contradictingFindings, "Polityka zwrotów.");

        // Verdict must be coerced to NEEDS_INFO
        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_INFO);
        // discrepancyNoted must be true
        assertThat(result.discrepancyNoted()).isTrue();
        // discrepancyExplanation must be non-empty
        assertThat(result.discrepancyExplanation()).isNotBlank();
    }

    @Test
    void whenMatchesDeclaredCaseFalseRejectIsAlsoCoercedToNeedsInfo() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(validDecisionResponse("REJECT"))
                .addHeader("Content-Type", "application/json"));

        ImageFindings contradictingFindings = new ImageFindings(
                true, "Rozbieżność.", false, null,
                false, null, null,
                false, "Rozbieżność w zgłoszeniu.",
                ImageFindings.Confidence.LOW
        );

        DecisionResult result = service.decide(
                CaseType.COMPLAINT, buildIntake("contradiction.jpg"),
                contradictingFindings, "Polityka reklamacji.");

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_INFO);
        assertThat(result.discrepancyNoted()).isTrue();
        assertThat(result.discrepancyExplanation()).isNotBlank();
    }

    // =========================================================================
    // TAC-002-05: malformed JSON triggers exactly one re-ask, then LlmUnavailableException
    // =========================================================================

    @Test
    void malformedJsonTriggersOneReAskThenThrows() throws Exception {
        // First response: malformed JSON
        mockServer.enqueue(new MockResponse()
                .setBody(malformedJsonCompletionResponse())
                .addHeader("Content-Type", "application/json"));

        // Second response (re-ask): also malformed — exhaust retries
        mockServer.enqueue(new MockResponse()
                .setBody(malformedJsonCompletionResponse())
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() ->
                service.analyzeImage(CaseType.RETURN, buildIntake("test.jpg"),
                        "base64data", "image/jpeg"))
                .isInstanceOf(LlmUnavailableException.class);

        // Exactly 2 requests total (original + 1 re-ask)
        assertThat(mockServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void whenFirstResponseMalformedButSecondValidReturnsResult() throws Exception {
        // First response: malformed JSON
        mockServer.enqueue(new MockResponse()
                .setBody(malformedJsonCompletionResponse())
                .addHeader("Content-Type", "application/json"));

        // Second response: valid
        mockServer.enqueue(new MockResponse()
                .setBody(validImageFindingsResponse())
                .addHeader("Content-Type", "application/json"));

        ImageFindings result = service.analyzeImage(
                CaseType.RETURN, buildIntake("test.jpg"), "base64data", "image/jpeg");

        assertThat(result).isNotNull();
        assertThat(result.readable()).isTrue();
        assertThat(mockServer.getRequestCount()).isEqualTo(2);
    }

    // =========================================================================
    // TAC-002-06: 429/5xx retried then LlmUnavailableException
    // =========================================================================

    @Test
    void serverError5xxRetriedThenThrows() throws Exception {
        // Enqueue 3 server error responses (original + 2 retries)
        for (int i = 0; i < 3; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\":{\"message\":\"Wewnętrzny błąd serwera\",\"type\":\"server_error\"}}")
                    .addHeader("Content-Type", "application/json"));
        }

        assertThatThrownBy(() ->
                service.analyzeImage(CaseType.RETURN, buildIntake("test.jpg"),
                        "base64data", "image/jpeg"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageNotContaining("APPROVE")
                .hasMessageNotContaining("REJECT");
    }

    @Test
    void rateLimitRetryExhaustionThrowsWithoutFabricatedVerdict() throws Exception {
        // 429 Too Many Requests
        for (int i = 0; i < 3; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody("{\"error\":{\"message\":\"Zbyt wiele żądań\",\"type\":\"rate_limit_error\"}}")
                    .addHeader("Content-Type", "application/json"));
        }

        assertThatThrownBy(() ->
                service.decide(CaseType.RETURN, buildIntake("test.jpg"),
                        buildFindings(true, true), "Polityka."))
                .isInstanceOf(LlmUnavailableException.class);
    }

    // =========================================================================
    // TAC-002-07: streamChat emits ordered tokens; upstream error → LlmUnavailableException
    // =========================================================================

    @Test
    void streamChatEmitsOrderedTokensViaCallback() throws Exception {
        String sseBody =
                "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1700000000," +
                "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hej\"},\"finish_reason\":null}]}\n\n" +
                "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1700000000," +
                "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" świecie\"},\"finish_reason\":null}]}\n\n" +
                "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1700000000," +
                "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                "data: [DONE]\n\n";

        mockServer.enqueue(new MockResponse()
                .setBody(sseBody)
                .addHeader("Content-Type", "text/event-stream"));

        List<String> tokens = new ArrayList<>();
        List<ChatMessage> history = List.of(
                new ChatMessage(ChatMessage.Role.USER, "Cześć", java.time.Instant.now())
        );

        service.streamChat(history, tokens::add);

        assertThat(tokens).containsExactly("Hej", " świecie");
    }

    @Test
    void streamChatUpstreamErrorThrowsLlmUnavailableException() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\":{\"message\":\"Błąd serwera\",\"type\":\"server_error\"}}")
                    .addHeader("Content-Type", "application/json"));
        }

        List<ChatMessage> history = List.of(
                new ChatMessage(ChatMessage.Role.USER, "Test", java.time.Instant.now())
        );

        assertThatThrownBy(() -> service.streamChat(history, token -> {}))
                .isInstanceOf(LlmUnavailableException.class);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CaseIntake buildIntake(String filename) {
        return new CaseIntake(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop TestModel X1", LocalDate.of(2026, 1, 1),
                "Zwrot urządzenia", "image/jpeg", filename
        );
    }

    private ImageFindings buildFindings(boolean readable, boolean matchesDeclaredCase) {
        return new ImageFindings(
                readable, "Opis testowy.", false, true,
                null, null, null, matchesDeclaredCase, null,
                ImageFindings.Confidence.HIGH
        );
    }

    /** Wraps a content string in a valid OpenAI chat completion JSON envelope. */
    private String wrapCompletion(String content) {
        // Escape JSON content properly
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": "%s"},
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
                }
                """.formatted(escaped);
    }

    private String validImageFindingsResponse() {
        String json = """
                {"readable":true,"description":"Urządzenie w dobrym stanie.","signsOfUse":false,"resellable":true,"damagePresent":false,"damageType":null,"likelyCauseCategory":null,"matchesDeclaredCase":true,"discrepancyNote":null,"confidence":"HIGH"}
                """.trim();
        return wrapCompletion(json);
    }

    private String validDecisionResponse(String verdict) {
        String json = String.format(
                "{\"verdict\":\"%s\",\"justification\":\"Urządzenie spełnia warunki.\",\"nextSteps\":[\"Zapakuj urządzenie.\",\"Dostarcz do punktu.\"],\"discrepancyNoted\":false,\"discrepancyExplanation\":null,\"disclaimer\":\"To jest wstępna, niewiążąca ocena.\"}",
                verdict);
        return wrapCompletion(json);
    }

    private String malformedJsonCompletionResponse() {
        return wrapCompletion("To nie jest prawidłowy JSON {incomplete");
    }

    private AppProperties buildProps(String baseUrl, String apiKey,
                                     String appTitle, String appReferer) {
        AppProperties.OpenRouterProperties or = new AppProperties.OpenRouterProperties(
                apiKey, baseUrl,
                "openai/gpt-4o", "anthropic/claude-sonnet-4",
                appTitle, appReferer
        );
        AppProperties.ImageProperties img    = new AppProperties.ImageProperties(10_485_760L);
        AppProperties.SessionProperties sess = new AppProperties.SessionProperties(120);
        return new AppProperties(or, img, sess);
    }

    // =========================================================================
    // Stub PromptTemplateProvider — returns simple Polish prompts
    // =========================================================================

    private static class StubPromptTemplateProvider implements PromptTemplateProvider {

        @Override
        public String imagePrompt(CaseType caseType, pl.nbp.copilot.domain.CaseIntake intake) {
            return "Przeanalizuj obraz urządzenia i zwróć wyniki w formacie JSON.";
        }

        @Override
        public String decisionPrompt(CaseType caseType, pl.nbp.copilot.domain.CaseIntake intake,
                                     String imageFindings, String policyText) {
            return "Podejmij decyzję na podstawie polityki: " + policyText
                    + " Wyniki analizy obrazu: " + imageFindings;
        }

        @Override
        public String chatSystemPrompt(pl.nbp.copilot.domain.CaseIntake intake,
                                       String imageFindings, String verdict,
                                       String justification, java.util.List<String> nextSteps) {
            return "Jesteś asystentem obsługi klienta.";
        }
    }
}
