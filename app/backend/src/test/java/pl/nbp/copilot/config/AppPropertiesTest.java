package pl.nbp.copilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — typed configuration loads with documented defaults (TAC-14).
 * Written BEFORE production code.
 *
 * Uses only OPENROUTER_API_KEY; all other settings should fall back to documented defaults.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.openrouter.api-key=sk-or-v1-test-key",
        "app.openrouter.model-vision=openai/gpt-4o",
        "app.openrouter.model-reasoning=anthropic/claude-sonnet-4"
})
class AppPropertiesTest {

    @Autowired
    private AppProperties props;

    @Test
    void apiKeyIsLoaded() {
        assertThat(props.openrouter().apiKey()).isEqualTo("sk-or-v1-test-key");
    }

    @Test
    void baseUrlDefaultsToOpenRouter() {
        assertThat(props.openrouter().baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
    }

    @Test
    void visionModelIsLoaded() {
        assertThat(props.openrouter().modelVision()).isEqualTo("openai/gpt-4o");
    }

    @Test
    void reasoningModelIsLoaded() {
        assertThat(props.openrouter().modelReasoning()).isEqualTo("anthropic/claude-sonnet-4");
    }

    @Test
    void imageMaxBytesDefaultsToTenMb() {
        assertThat(props.image().maxBytes()).isEqualTo(10_485_760L);
    }

    @Test
    void sessionTtlDefaultsTo120Minutes() {
        assertThat(props.session().ttlMinutes()).isEqualTo(120);
    }
}
