package pl.nbp.copilot.prompt;

/**
 * Thrown when a required prompt template resource file is missing from the classpath.
 * This is a programming/configuration error, not a user error.
 */
public class PromptTemplateNotFoundException extends RuntimeException {

    public PromptTemplateNotFoundException(String resourcePath) {
        super("Prompt template resource not found on classpath: " + resourcePath);
    }

    public PromptTemplateNotFoundException(String resourcePath, Throwable cause) {
        super("Prompt template resource not found on classpath: " + resourcePath, cause);
    }
}
