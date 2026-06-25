package pl.nbp.copilot.policy;

/**
 * Thrown when a required policy resource file is missing from the classpath.
 * This is a programming error (resources should always be present after build).
 * Maps to a 503 or startup failure rather than a user-facing 4xx.
 */
public class PolicyResourceNotFoundException extends RuntimeException {

    public PolicyResourceNotFoundException(String resourcePath) {
        super("Policy resource not found on classpath: " + resourcePath);
    }

    public PolicyResourceNotFoundException(String resourcePath, Throwable cause) {
        super("Policy resource not found on classpath: " + resourcePath, cause);
    }
}
