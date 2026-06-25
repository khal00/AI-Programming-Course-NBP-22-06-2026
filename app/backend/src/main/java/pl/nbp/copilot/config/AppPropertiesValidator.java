package pl.nbp.copilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * Fail-fast startup validation for critical application properties.
 * If the API key or model IDs are blank the context will fail to start
 * with a clear, non-leaking message (TAC-002-08, ADR-000 §7).
 *
 * <p>This component is NOT instantiated under the {@code mock-llm} profile,
 * so mock/E2E tests can start without a real OpenRouter key.
 */
@Component
@Profile("!mock-llm")
public class AppPropertiesValidator {

    private static final Logger log = LoggerFactory.getLogger(AppPropertiesValidator.class);

    private final AppProperties props;

    public AppPropertiesValidator(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (!StringUtils.hasText(props.openrouter().apiKey())) {
            errors.append("  - app.openrouter.api-key (OPENROUTER_API_KEY / OPENAI_API_KEY) is blank\n");
        }
        if (!StringUtils.hasText(props.openrouter().modelVision())) {
            errors.append("  - app.openrouter.model-vision (OPENROUTER_MODEL_VISION) is blank\n");
        }
        if (!StringUtils.hasText(props.openrouter().modelReasoning())) {
            errors.append("  - app.openrouter.model-reasoning (OPENROUTER_MODEL_REASONING) is blank\n");
        }

        if (!errors.isEmpty()) {
            String message = "Application failed to start: required configuration is missing:\n" + errors +
                    "Set the missing environment variables and restart.";
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("AppProperties validated successfully. Vision model: {}, Reasoning model: {}",
                props.openrouter().modelVision(), props.openrouter().modelReasoning());
    }
}
