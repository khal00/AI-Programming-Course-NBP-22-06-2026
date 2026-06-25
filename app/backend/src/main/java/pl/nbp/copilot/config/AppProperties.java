package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed configuration properties for the Hardware Service Decision Copilot.
 * Binds to the {@code app.*} namespace in {@code application.yml} (ADR-000 §7).
 *
 * <p>Fail-fast validation is enforced by {@link AppPropertiesValidator} — the application
 * context will not start if the API key or model IDs are blank (TAC-002-08).
 *
 * <p>All defaults match ADR-000 §7; they can be overridden via environment variables.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        OpenRouterProperties openrouter,
        ImageProperties image,
        SessionProperties session
) {

    /**
     * OpenRouter / LLM client configuration.
     * Env vars: OPENROUTER_API_KEY (primary), OPENAI_API_KEY (fallback),
     *           OPENROUTER_BASE_URL, OPENROUTER_MODEL_VISION, OPENROUTER_MODEL_REASONING,
     *           OPENROUTER_APP_TITLE, OPENROUTER_APP_REFERER.
     */
    public record OpenRouterProperties(
            /** Primary OpenRouter API key; falls back to OPENAI_API_KEY when unset. */
            String apiKey,
            /** OpenRouter OpenAI-compatible base URL. */
            @DefaultValue("https://openrouter.ai/api/v1") String baseUrl,
            /** Vision/multimodal model ID for the image-analysis call (Call 1). */
            @DefaultValue("openai/gpt-4o") String modelVision,
            /** Reasoning model ID for the decision and chat calls (Call 2+). */
            @DefaultValue("anthropic/claude-sonnet-4") String modelReasoning,
            /** Sent as the X-Title header for OpenRouter attribution. */
            @DefaultValue("Hardware Service Decision Copilot") String appTitle,
            /** Sent as the HTTP-Referer header for OpenRouter attribution. */
            @DefaultValue("http://localhost:4200") String appReferer
    ) {}

    /**
     * Image upload constraints.
     * Env var: APP_IMAGE_MAX_BYTES (default 10 MB).
     */
    public record ImageProperties(
            /** Maximum accepted image file size in bytes (default 10 MB). */
            @DefaultValue("10485760") long maxBytes
    ) {}

    /**
     * Session state configuration.
     * Env var: APP_SESSION_TTL_MINUTES (default 120).
     */
    public record SessionProperties(
            /** In-memory session time-to-live in minutes (default 2 h). */
            @DefaultValue("120") int ttlMinutes
    ) {}
}
