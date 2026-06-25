package pl.nbp.copilot.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.config.AppProperties;

/**
 * Builds and caches a single {@link OpenAIClient} configured for OpenRouter (ADR-002).
 *
 * <p>Active only outside the {@code mock-llm} profile. The client is configured with:
 * <ul>
 *   <li>{@code baseUrl} — from {@link AppProperties.OpenRouterProperties#baseUrl()}</li>
 *   <li>{@code apiKey} — from {@link AppProperties.OpenRouterProperties#apiKey()}</li>
 *   <li>{@code HTTP-Referer} — from {@link AppProperties.OpenRouterProperties#appReferer()}</li>
 *   <li>{@code X-Title} — from {@link AppProperties.OpenRouterProperties#appTitle()}</li>
 * </ul>
 *
 * <p>TAC-002-01.
 */
@Component
@Profile("!mock-llm")
public class OpenRouterClientFactory {

    private final OpenAIClient openAIClient;

    public OpenRouterClientFactory(AppProperties props) {
        AppProperties.OpenRouterProperties or = props.openrouter();
        this.openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl(or.baseUrl())
                .apiKey(or.apiKey())
                .putHeader("HTTP-Referer", or.appReferer())
                .putHeader("X-Title", or.appTitle())
                // Disable SDK-level retries: DefaultLlmService owns the retry strategy (ADR-002)
                .maxRetries(0)
                .build();
    }

    /**
     * Returns the shared {@link OpenAIClient} instance.
     *
     * @return configured OpenAI-compatible client pointing at OpenRouter
     */
    public OpenAIClient client() {
        return openAIClient;
    }
}
