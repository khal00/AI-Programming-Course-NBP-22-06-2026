package pl.nbp.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: the Spring context loads. This is the first TDD checkpoint —
 * feature tests (validation, pipeline, SSE) are added per ADR §10 as code lands.
 *
 * Provides the minimum required properties so the fail-fast validator passes
 * without needing a real OpenRouter key in CI (TAC-14, TAC-002-08).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.openrouter.api-key=sk-test-smoke",
        "app.openrouter.model-vision=openai/gpt-4o",
        "app.openrouter.model-reasoning=anthropic/claude-sonnet-4"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: fails if the application context cannot start.
    }
}
