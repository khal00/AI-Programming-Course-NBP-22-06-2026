package pl.nbp.copilot.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory session holding the full state for one customer case (ADR-000 §5, ADR-004).
 *
 * <p>The session is mutable only via {@link #addMessage(ChatMessage)}; all other fields
 * are set at creation time and are immutable. Raw image bytes are NOT stored (ADR-004).
 *
 * <p>Thread-safety: the message list is synchronized; the session-store wraps concurrent
 * access at a higher level.
 */
public final class Session {

    private final String sessionId;
    private final CaseSummary caseSummary;
    private final ImageFindings imageFindings;
    private final DecisionResult decisionResult;
    private final List<ChatMessage> messages;
    private final Instant createdAt;
    private final Instant expiresAt;

    public Session(
            String sessionId,
            CaseSummary caseSummary,
            ImageFindings imageFindings,
            DecisionResult decisionResult,
            List<ChatMessage> initialMessages,
            Instant createdAt,
            Instant expiresAt) {
        this.sessionId = sessionId;
        this.caseSummary = caseSummary;
        this.imageFindings = imageFindings;
        this.decisionResult = decisionResult;
        this.messages = new ArrayList<>(initialMessages);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Appends a message to the conversation history. */
    public synchronized void addMessage(ChatMessage message) {
        messages.add(message);
    }

    /** Returns an unmodifiable snapshot of the current message list. */
    public synchronized List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public String getSessionId() { return sessionId; }
    public CaseSummary getCaseSummary() { return caseSummary; }
    public ImageFindings getImageFindings() { return imageFindings; }
    public DecisionResult getDecisionResult() { return decisionResult; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    /** Returns true if the session has passed its TTL. */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * Lightweight case summary stored in the session.
     * Contains no raw image bytes (ADR-004 constraint).
     */
    public record CaseSummary(
            CaseType caseType,
            EquipmentCategory category,
            String modelName,
            java.time.LocalDate purchaseDate
    ) {}
}
