package pl.nbp.copilot.llm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring context test: under mock-llm profile, the active LlmService bean is MockLlmService.
 * BE-6, TAC-mock-context.
 */
@SpringBootTest
@ActiveProfiles("mock-llm")
@TestPropertySource(properties = {"app.openrouter.api-key=mock-key-placeholder"})
class MockLlmServiceContextTest {

    @Autowired
    LlmService llmService;

    @Test
    void underMockLlmProfileActiveBeanIsMockLlmService() {
        assertThat(llmService).isInstanceOf(MockLlmService.class);
    }
}
