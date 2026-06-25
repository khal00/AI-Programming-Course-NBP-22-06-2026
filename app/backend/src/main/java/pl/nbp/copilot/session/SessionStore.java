package pl.nbp.copilot.session;

import pl.nbp.copilot.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for customer sessions (ADR-004).
 *
 * <p>The MVP implementation is {@link InMemorySessionStore}. A future SQLite-backed
 * implementation can be swapped in via Spring configuration without changing the
 * orchestration code.
 *
 * <p>Raw image bytes are NEVER stored here — only derived {@link ImageFindings}
 * (ADR-004 privacy constraint).
 *
 * <p>TAC-004-01..05.
 */
public interface SessionStore {

    /**
     * Creates a new session with a generated opaque session ID, computing
     * {@code createdAt} and {@code expiresAt} from the configured TTL.
     *
     * @param caseSummary    case metadata (no image bytes)
     * @param imageFindings  structured findings from the image analysis
     * @param decisionResult first LLM decision
     * @param initialMessages seed messages (e.g. the first assistant response)
     * @return the newly created session
     */
    Session create(Session.CaseSummary caseSummary,
                   ImageFindings imageFindings,
                   DecisionResult decisionResult,
                   List<ChatMessage> initialMessages);

    /**
     * Returns the session for the given ID, or empty if unknown or evicted.
     *
     * @param sessionId opaque session ID
     * @return the session, or {@link Optional#empty()} if not found
     */
    Optional<Session> get(String sessionId);

    /**
     * Appends a message to the conversation history of the given session.
     * Silently ignored if the session no longer exists (e.g. was evicted).
     *
     * @param sessionId opaque session ID
     * @param message   the message to append
     */
    void appendMessage(String sessionId, ChatMessage message);
}
