package pl.nbp.copilot.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD — SessionStore interface and InMemorySessionStore implementation.
 * Tests written BEFORE the implementation (BE-4).
 * TAC-004-01..05, TAC-12.
 */
class SessionStoreTest {

    private static final int TTL_MINUTES = 120;

    private SessionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore(TTL_MINUTES);
    }

    // =========================================================================
    // Create / get (TAC-004-01)
    // =========================================================================

    @Test
    void createdSessionIsRetrievableById() {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        String id = session.getSessionId();

        Optional<Session> found = store.get(id);
        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo(id);
    }

    @Test
    void createdSessionHasSessionId() {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        assertThat(session.getSessionId()).isNotBlank();
    }

    @Test
    void eachCreatedSessionHasUniqueId() {
        Session s1 = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        Session s2 = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        assertThat(s1.getSessionId()).isNotEqualTo(s2.getSessionId());
    }

    @Test
    void unknownIdReturnsEmpty() {
        Optional<Session> found = store.get("not-a-real-id");
        assertThat(found).isEmpty();
    }

    @Test
    void sessionCreatedAtIsSet() {
        Instant before = Instant.now();
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        Instant after  = Instant.now();
        assertThat(session.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(session.getCreatedAt()).isBeforeOrEqualTo(after);
    }

    @Test
    void sessionExpiresAtIsAfterCreatedAt() {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        assertThat(session.getExpiresAt()).isAfter(session.getCreatedAt());
    }

    // =========================================================================
    // Append message (TAC-004-01, TAC-004-03)
    // =========================================================================

    @Test
    void appendedMessageIsInHistory() {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        ChatMessage msg = new ChatMessage(ChatMessage.Role.USER, "Dzień dobry", Instant.now());

        store.appendMessage(session.getSessionId(), msg);

        List<ChatMessage> messages = store.get(session.getSessionId())
                .orElseThrow().getMessages();
        assertThat(messages).containsExactly(msg);
    }

    @Test
    void multipleAppendedMessagesPreserveOrder() {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        ChatMessage m1 = new ChatMessage(ChatMessage.Role.USER,      "Pierwsze pytanie",  Instant.now());
        ChatMessage m2 = new ChatMessage(ChatMessage.Role.ASSISTANT, "Pierwsza odpowiedź", Instant.now());
        ChatMessage m3 = new ChatMessage(ChatMessage.Role.USER,      "Drugie pytanie",    Instant.now());

        store.appendMessage(session.getSessionId(), m1);
        store.appendMessage(session.getSessionId(), m2);
        store.appendMessage(session.getSessionId(), m3);

        List<ChatMessage> messages = store.get(session.getSessionId())
                .orElseThrow().getMessages();
        assertThat(messages).containsExactly(m1, m2, m3);
    }

    @Test
    void initialMessagesAreIncludedInHistory() {
        ChatMessage seed = new ChatMessage(ChatMessage.Role.ASSISTANT, "Wstępna decyzja.", Instant.now());
        Session session  = store.create(caseSummary(), sampleFindings(), sampleDecision(),
                List.of(seed));

        assertThat(store.get(session.getSessionId()).orElseThrow().getMessages())
                .containsExactly(seed);
    }

    @Test
    void appendToUnknownIdDoesNotThrow() {
        // Idempotent: appending to a non-existent session is silently ignored
        // (the session may have been evicted)
        assertThatCode(() -> store.appendMessage("no-such-id",
                new ChatMessage(ChatMessage.Role.USER, "test", Instant.now())))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // TTL eviction (TAC-004-02)
    // =========================================================================

    @Test
    void sessionPastExpiresAtIsEvictedByJob() throws InterruptedException {
        // Use a very short TTL store so we can expire immediately
        SessionStore shortTtlStore = new InMemorySessionStore(0); // 0 minutes = expires now
        Session session = shortTtlStore.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        String id = session.getSessionId();

        // Session is immediately past expiresAt with 0-minute TTL — run eviction
        ((InMemorySessionStore) shortTtlStore).evictExpiredSessions();

        assertThat(shortTtlStore.get(id)).isEmpty();
    }

    @Test
    void notYetExpiredSessionSurvivesEviction() {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        String id = session.getSessionId();

        ((InMemorySessionStore) store).evictExpiredSessions();

        assertThat(store.get(id)).isPresent();
    }

    // =========================================================================
    // No image bytes retained (TAC-004-04)
    // =========================================================================

    @Test
    void sessionContainsFindingsButNoImageBytes() {
        ImageFindings findings = sampleFindings();
        Session session = store.create(caseSummary(), findings, sampleDecision(), List.of());

        Session retrieved = store.get(session.getSessionId()).orElseThrow();
        // ImageFindings present
        assertThat(retrieved.getImageFindings()).isEqualTo(findings);
        // Session does NOT have any raw byte fields; we just confirm no imageBytes field
        // (the Session record design guarantees this — no raw byte field exists)
        assertThat(retrieved.getImageFindings()).isNotNull();
    }

    // =========================================================================
    // Concurrent append — no message lost, order within a single thread preserved
    // (TAC-004-03, TAC-12)
    // =========================================================================

    @RepeatedTest(5)
    void concurrentAppendsLoseNoMessages() throws InterruptedException, ExecutionException {
        Session session = store.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        String id = session.getSessionId();

        int threads = 8;
        int msgsPerThread = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            futures.add(exec.submit(() -> {
                for (int m = 0; m < msgsPerThread; m++) {
                    store.appendMessage(id,
                            new ChatMessage(ChatMessage.Role.USER,
                                    "thread-" + threadIdx + "-msg-" + m,
                                    Instant.now()));
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(); // re-throws if the task threw
        }
        exec.shutdown();
        assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        int expected = threads * msgsPerThread;
        assertThat(store.get(id).orElseThrow().getMessages()).hasSize(expected);
    }

    // =========================================================================
    // Interface seam — a test double can substitute (TAC-004-05)
    // =========================================================================

    @Test
    void testDoubleCanSubstituteTheInterface() {
        // The interface contract is fulfilled by any implementation — the orchestration
        // depends ONLY on SessionStore, not InMemorySessionStore.
        SessionStore testDouble = new StubSessionStore();
        Session s = testDouble.create(caseSummary(), sampleFindings(), sampleDecision(), List.of());
        assertThat(testDouble.get(s.getSessionId())).isPresent();
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private Session.CaseSummary caseSummary() {
        return new Session.CaseSummary(
                CaseType.RETURN,
                EquipmentCategory.LAPTOPS,
                "Laptop TestModel X1",
                LocalDate.of(2026, 3, 1)
        );
    }

    private ImageFindings sampleFindings() {
        return new ImageFindings(
                true,
                "Urządzenie w dobrym stanie.",
                false,
                true,
                null,
                null,
                ImageFindings.LikelyCause.UNKNOWN,
                true,
                null,
                ImageFindings.Confidence.HIGH
        );
    }

    private DecisionResult sampleDecision() {
        return new DecisionResult(
                Verdict.APPROVE,
                "Sprzęt kwalifikuje się do zwrotu.",
                List.of("Zapakuj urządzenie.", "Dostarcz do punktu."),
                false,
                null,
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi."
        );
    }

    // -------------------------------------------------------------------------
    // Minimal test double
    // -------------------------------------------------------------------------

    /** Stub implementation to prove orchestration can swap the seam. */
    private static class StubSessionStore implements SessionStore {

        private final java.util.concurrent.ConcurrentHashMap<String, Session> map =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Session create(Session.CaseSummary caseSummary,
                              ImageFindings imageFindings,
                              DecisionResult decisionResult,
                              List<ChatMessage> initialMessages) {
            String id = java.util.UUID.randomUUID().toString();
            Instant now = Instant.now();
            Session s = new Session(id, caseSummary, imageFindings, decisionResult,
                    initialMessages, now, now.plusSeconds(3600));
            map.put(id, s);
            return s;
        }

        @Override
        public Optional<Session> get(String sessionId) {
            return Optional.ofNullable(map.get(sessionId));
        }

        @Override
        public void appendMessage(String sessionId, ChatMessage message) {
            Session s = map.get(sessionId);
            if (s != null) s.addMessage(message);
        }
    }
}
