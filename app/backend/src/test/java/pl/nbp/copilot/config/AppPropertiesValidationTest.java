package pl.nbp.copilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — fail-fast startup validation when required config is missing (TAC-002-08).
 * Written BEFORE production code.
 *
 * Uses ApplicationContextRunner to verify that the context fails to start when
 * the API key or model IDs are blank, WITHOUT starting a full web server.
 */
class AppPropertiesValidationTest {

    /**
     * Minimal Spring context that loads only AppProperties and the validation bean.
     */
    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
        @Bean
        AppPropertiesValidator validator(AppProperties props) {
            return new AppPropertiesValidator(props);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void contextFailsWhenApiKeyIsBlank() {
        runner.withPropertyValues(
                "app.openrouter.api-key=",
                "app.openrouter.model-vision=openai/gpt-4o",
                "app.openrouter.model-reasoning=anthropic/claude-sonnet-4"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextFailsWhenVisionModelIsBlank() {
        runner.withPropertyValues(
                "app.openrouter.api-key=sk-or-v1-test",
                "app.openrouter.model-vision=",
                "app.openrouter.model-reasoning=anthropic/claude-sonnet-4"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextFailsWhenReasoningModelIsBlank() {
        runner.withPropertyValues(
                "app.openrouter.api-key=sk-or-v1-test",
                "app.openrouter.model-vision=openai/gpt-4o",
                "app.openrouter.model-reasoning="
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextStartsWithAllRequiredPropertiesPresent() {
        runner.withPropertyValues(
                "app.openrouter.api-key=sk-or-v1-test",
                "app.openrouter.model-vision=openai/gpt-4o",
                "app.openrouter.model-reasoning=anthropic/claude-sonnet-4"
        ).run(context -> assertThat(context).hasNotFailed());
    }
}
