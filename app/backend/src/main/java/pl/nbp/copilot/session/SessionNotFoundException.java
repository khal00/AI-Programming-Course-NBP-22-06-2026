package pl.nbp.copilot.session;

/**
 * Thrown when a session ID is not found in the store (unknown or expired).
 * Maps to HTTP 404 via the global exception handler.
 *
 * <p>TAC-12, AC-25.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("Sesja nie istnieje lub wygasła: " + sessionId);
    }
}
