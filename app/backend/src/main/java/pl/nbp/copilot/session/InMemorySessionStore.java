package pl.nbp.copilot.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import pl.nbp.copilot.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SessionStore} backed by a
 * {@link ConcurrentHashMap}.
 *
 * <p>Thread-safety:
 * - Map operations (put/get/remove) are handled by {@code ConcurrentHashMap}.
 * - Message appends delegate to {@link Session#addMessage(ChatMessage)} which is
 *   {@code synchronized} on the session, preserving insertion order under concurrency.
 *
 * <p>No raw image bytes are ever stored — only the derived {@link ImageFindings}
 * (ADR-004 privacy constraint).
 *
 * <p>The {@link #evictExpiredSessions()} method is scheduled and also exposed
 * for direct invocation in tests.
 *
 * <p>ADR-004, TAC-004-01..05, TAC-12.
 */
public class InMemorySessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionStore.class);

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final int ttlMinutes;

    /**
     * Creates a store with the given session TTL.
     *
     * @param ttlMinutes time-to-live in minutes; 0 means sessions expire immediately
     */
    public InMemorySessionStore(int ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public Session create(Session.CaseSummary caseSummary,
                          ImageFindings imageFindings,
                          DecisionResult decisionResult,
                          List<ChatMessage> initialMessages) {
        String  id        = UUID.randomUUID().toString();
        Instant now       = Instant.now();
        Instant expiresAt = now.plusSeconds((long) ttlMinutes * 60);

        Session session = new Session(id, caseSummary, imageFindings, decisionResult,
                initialMessages, now, expiresAt);
        sessions.put(id, session);
        log.debug("Session created: {}, expiresAt={}", id, expiresAt);
        return session;
    }

    @Override
    public Optional<Session> get(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        // Lazy expiry check — also remove expired sessions on access
        if (session.isExpired(Instant.now())) {
            sessions.remove(sessionId);
            log.debug("Session expired on access: {}", sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            log.debug("appendMessage: session not found (possibly evicted): {}", sessionId);
            return;
        }
        session.addMessage(message);
    }

    /**
     * Scheduled TTL eviction job — runs every 5 minutes.
     * Also callable directly in tests for deterministic behavior.
     *
     * <p>TAC-004-02, ADR-004 §3.
     */
    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void evictExpiredSessions() {
        Instant now = Instant.now();
        int removed = 0;
        for (var entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Session eviction: removed {} expired session(s)", removed);
        }
    }
}
