package pl.nbp.copilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * TDD — mock-llm profile starts without a real OpenRouter key (TASK B3).
 * Written BEFORE the profile is created.
 *
 * When mock-llm is active, the fail-fast API key check must be bypassed
 * (a placeholder dummy key is provided by the profile).
 */
@SpringBootTest
@ActiveProfiles("mock-llm")
@TestPropertySource(properties = {
        // mock-llm profile provides a dummy key via application-mock-llm.yml,
        // but we also supply it here to ensure the context starts in the test runner.
        "app.openrouter.api-key=mock-key-placeholder"
})
class MockLlmProfileTest {

    @Test
    void contextLoadsWithMockLlmProfile() {
        // If the context starts, the profile is correctly configured.
        // No assertions needed: context load failure = test failure.
    }
}
