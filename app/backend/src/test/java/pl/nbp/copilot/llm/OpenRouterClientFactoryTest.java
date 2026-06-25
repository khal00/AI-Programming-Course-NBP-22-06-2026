package pl.nbp.copilot.llm;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.config.AppProperties;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAC-002-01: Verifies OpenRouterClientFactory builds a client that targets the configured
 * base-url, uses the correct api key, and sends HTTP-Referer and X-Title headers.
 * Uses MockWebServer to intercept the call and verify RecordedRequest headers.
 */
class OpenRouterClientFactoryTest {

    private MockWebServer mockServer;
    private OpenRouterClientFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void clientTargetsConfiguredBaseUrlAndSendsRequiredHeaders() throws InterruptedException {
        // Given: factory configured with MockWebServer URL
        String baseUrl = mockServer.url("/v1").toString();
        String apiKey  = "test-api-key-12345";
        String appTitle   = "Hardware Service Decision Copilot";
        String appReferer = "http://localhost:4200";

        AppProperties props = buildProps(baseUrl, apiKey, appTitle, appReferer);
        factory = new OpenRouterClientFactory(props);

        // Enqueue a minimal valid chat completion JSON response
        mockServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "created": 1700000000,
                          "model": "test-model",
                          "choices": [{
                            "index": 0,
                            "message": {"role": "assistant", "content": "Odpowiedź testowa"},
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 5, "completion_tokens": 3, "total_tokens": 8}
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        // When: we make a chat completion call through the client
        OpenAIClient client = factory.client();
        client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                        .model("test-model")
                        .addUserMessage("Test")
                        .build()
        );

        // Then: recorded request must have the correct headers
        RecordedRequest request = mockServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + apiKey);
        assertThat(request.getHeader("HTTP-Referer")).isEqualTo(appReferer);
        assertThat(request.getHeader("X-Title")).isEqualTo(appTitle);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
}
