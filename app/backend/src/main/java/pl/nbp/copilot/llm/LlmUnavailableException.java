package pl.nbp.copilot.llm;

/**
 * Thrown when the LLM service is temporarily unavailable or cannot produce a valid response
 * after exhausting all retry attempts (TAC-002-06).
 *
 * <p>This is an unchecked exception. No payload from the LLM response is leaked into
 * the message — only a generic Polish message is included.
 */
public class LlmUnavailableException extends RuntimeException {

    /**
     * Creates a new exception with a generic Polish message indicating service unavailability.
     */
    public LlmUnavailableException() {
        super("Usługa analizy AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.");
    }

    /**
     * Creates a new exception with a generic Polish message and the underlying cause.
     * The cause is retained for logging but must not be propagated to API responses.
     *
     * @param cause the underlying technical cause (not exposed to the API client)
     */
    public LlmUnavailableException(Throwable cause) {
        super("Usługa analizy AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.", cause);
    }
}
