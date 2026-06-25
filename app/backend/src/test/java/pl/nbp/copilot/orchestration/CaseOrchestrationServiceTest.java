package pl.nbp.copilot.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.image.ImagePayload;
import pl.nbp.copilot.image.ImageService;
import pl.nbp.copilot.image.ImageUnreadableException;
import pl.nbp.copilot.llm.LlmService;
import pl.nbp.copilot.llm.LlmUnavailableException;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionStore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD — CaseOrchestrationService (BE-7).
 * Tests written BEFORE implementation.
 */
@ExtendWith(MockitoExtension.class)
class CaseOrchestrationServiceTest {

    @Mock private ImageService imageService;
    @Mock private LlmService llmService;
    @Mock private PolicyProvider policyProvider;
    @Mock private SessionStore sessionStore;
    @Mock private MessageAssembler messageAssembler;

    private CaseOrchestrationService orchestration;

    private CaseIntake returnIntake;
    private byte[] fakeImageBytes;
    private ImagePayload fakePayload;
    private ImageFindings readableFindings;
    private ImageFindings unreadableFindings;
    private ImageFindings contradictionFindings;
    private DecisionResult approveDecision;
    private DecisionResult needsInfoDecision;
    private Session fakeSession;

    @BeforeEach
    void setUp() {
        orchestration = new CaseOrchestrationService(
                imageService, llmService, policyProvider, sessionStore, messageAssembler);

        returnIntake = new CaseIntake(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop XYZ", LocalDate.of(2025, 3, 10),
                null, "image/jpeg", "clean-return.jpg");

        fakeImageBytes = new byte[]{1, 2, 3};

        fakePayload = new ImagePayload("base64abc", "image/jpeg", 100, 100, 500);

        readableFindings = new ImageFindings(
                true, "Good condition.", false, true, false, null, null,
                true, null, ImageFindings.Confidence.HIGH);

        unreadableFindings = new ImageFindings(
                false, "Cannot read.", null, null, null, null, null,
                false, null, ImageFindings.Confidence.LOW);

        contradictionFindings = new ImageFindings(
                true, "Damage found.",
                null, null, true, null, null,
                false,
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu",
                ImageFindings.Confidence.MEDIUM);

        approveDecision = new DecisionResult(
                Verdict.APPROVE,
                "Spełnia warunki.",
                List.of("Krok 1.", "Krok 2."),
                false, null,
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");

        needsInfoDecision = new DecisionResult(
                Verdict.NEEDS_INFO,
                "Niezgodność z deklarowanym typem.",
                List.of("Skontaktuj się z obsługą."),
                true,
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu.",
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");

        Session.CaseSummary summary = new Session.CaseSummary(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop XYZ", LocalDate.of(2025, 3, 10));

        fakeSession = new Session(
                "session-123", summary, readableFindings, approveDecision,
                new ArrayList<>(List.of(
                        new ChatMessage(ChatMessage.Role.SYSTEM, "System context.", Instant.now()),
                        new ChatMessage(ChatMessage.Role.ASSISTANT, "Pierwsza odpowiedź.", Instant.now())
                )),
                Instant.now(), Instant.now().plusSeconds(7200));
    }

    // =========================================================================
    // TAC-07: unreadable → ImageUnreadableException, NO session created
    // =========================================================================

    @Test
    void unreadableImageThrowsImageUnreadableExceptionAndNoSessionCreated() {
        when(imageService.process(anyString(), any())).thenReturn(fakePayload);
        when(llmService.analyzeImage(any(), any(), anyString(), anyString()))
                .thenReturn(unreadableFindings);

        assertThatThrownBy(() -> orchestration.handleNewCase(returnIntake, fakeImageBytes))
                .isInstanceOf(ImageUnreadableException.class);

        verify(sessionStore, never()).create(any(), any(), any(), any());
    }

    // =========================================================================
    // TAC-06: contradiction → NEEDS_INFO, NOT an exception, discrepancy in message
    // =========================================================================

    @Test
    void contradictionReturnsNeedsInfoResultNotException() {
        when(imageService.process(anyString(), any())).thenReturn(fakePayload);
        when(llmService.analyzeImage(any(), any(), anyString(), anyString()))
                .thenReturn(contradictionFindings);
        when(policyProvider.policyFor(any())).thenReturn("Policy text.");
        when(llmService.decide(any(), any(), any(), anyString())).thenReturn(needsInfoDecision);
        when(messageAssembler.buildFirstMessage(any(), any(), any()))
                .thenReturn("## Wynik\n**Wymaga uzupełnienia** — Niezgodność z deklarowanym typem. Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu.");
        when(messageAssembler.buildSystemMessage(any())).thenReturn("System message.");

        Session.CaseSummary summary = new Session.CaseSummary(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop XYZ", LocalDate.of(2025, 3, 10));
        Session sess = new Session("sess-456", summary, contradictionFindings, needsInfoDecision,
                List.of(), Instant.now(), Instant.now().plusSeconds(7200));
        when(sessionStore.create(any(), any(), any(), any())).thenReturn(sess);

        NewCaseResult result = orchestration.handleNewCase(returnIntake, fakeImageBytes);

        assertThat(result.verdict()).isIn(Verdict.NEEDS_INFO, Verdict.ESCALATE);
        assertThat(result.firstMessage()).contains("uszkodzeń");
        verify(sessionStore).create(any(), any(), any(), any());
    }

    // =========================================================================
    // TAC-08: LlmUnavailable during analyzeImage → propagated, NO session created
    // =========================================================================

    @Test
    void llmUnavailableDuringAnalyzeImagePropagatesAndNoSessionCreated() {
        when(imageService.process(anyString(), any())).thenReturn(fakePayload);
        when(llmService.analyzeImage(any(), any(), anyString(), anyString()))
                .thenThrow(new LlmUnavailableException());

        assertThatThrownBy(() -> orchestration.handleNewCase(returnIntake, fakeImageBytes))
                .isInstanceOf(LlmUnavailableException.class);

        verify(sessionStore, never()).create(any(), any(), any(), any());
    }

    // =========================================================================
    // TAC-08: LlmUnavailable during decide → propagated, NO session created
    // =========================================================================

    @Test
    void llmUnavailableDuringDecidePropagatesAndNoSessionCreated() {
        when(imageService.process(anyString(), any())).thenReturn(fakePayload);
        when(llmService.analyzeImage(any(), any(), anyString(), anyString()))
                .thenReturn(readableFindings);
        when(policyProvider.policyFor(any())).thenReturn("Policy text.");
        when(llmService.decide(any(), any(), any(), anyString()))
                .thenThrow(new LlmUnavailableException());

        assertThatThrownBy(() -> orchestration.handleNewCase(returnIntake, fakeImageBytes))
                .isInstanceOf(LlmUnavailableException.class);

        verify(sessionStore, never()).create(any(), any(), any(), any());
    }

    // =========================================================================
    // TAC-09: first message contains all required sections
    // =========================================================================

    @Test
    void happyPathReturnsResultWithVerdictAndFirstMessage() {
        String expectedFirstMsg =
                "Dzień dobry!\n\n**Decyzja: Zatwierdzone**\n\nSpełnia warunki.\n\n1. Krok 1.\n2. Krok 2.\n\nTo jest wstępna, niewiążąca ocena.";

        when(imageService.process(anyString(), any())).thenReturn(fakePayload);
        when(llmService.analyzeImage(any(), any(), anyString(), anyString()))
                .thenReturn(readableFindings);
        when(policyProvider.policyFor(any())).thenReturn("Policy text.");
        when(llmService.decide(any(), any(), any(), anyString())).thenReturn(approveDecision);
        when(messageAssembler.buildFirstMessage(any(), any(), any())).thenReturn(expectedFirstMsg);
        when(messageAssembler.buildSystemMessage(any())).thenReturn("System context.");

        Session.CaseSummary summary = new Session.CaseSummary(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop XYZ", LocalDate.of(2025, 3, 10));
        Session sess = new Session("sess-789", summary, readableFindings, approveDecision,
                List.of(), Instant.now(), Instant.now().plusSeconds(7200));
        when(sessionStore.create(any(), any(), any(), any())).thenReturn(sess);

        NewCaseResult result = orchestration.handleNewCase(returnIntake, fakeImageBytes);

        assertThat(result.sessionId()).isEqualTo("sess-789");
        assertThat(result.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(result.firstMessage()).contains("niewiążąca ocena");
    }

    // =========================================================================
    // TAC-11: chat turn passes FULL history to streamChat
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    void chatTurnPassesFullHistoryToStreamChat() {
        when(sessionStore.get("session-123")).thenReturn(Optional.of(fakeSession));

        // Capture history passed to streamChat
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);

        List<String> tokens = new ArrayList<>();
        doAnswer(invocation -> {
            List<ChatMessage> hist = invocation.getArgument(0);
            Consumer<String> cb = invocation.getArgument(1);
            cb.accept("Token1");
            return null;
        }).when(llmService).streamChat(historyCaptor.capture(), any());

        orchestration.streamReply("session-123", "Nova wiadomość użytkownika", tokens::add);

        List<ChatMessage> capturedHistory = historyCaptor.getValue();
        // history must contain: system (index 0) + first assistant (index 1) + new user message
        assertThat(capturedHistory).hasSizeGreaterThanOrEqualTo(3);
        assertThat(capturedHistory.get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(capturedHistory.get(1).role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        // last entry should be the new user message
        assertThat(capturedHistory.get(capturedHistory.size() - 1).role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(capturedHistory.get(capturedHistory.size() - 1).content()).isEqualTo("Nova wiadomość użytkownika");
    }

    @Test
    void chatTurnRelaysTokensToConsumer() {
        when(sessionStore.get("session-123")).thenReturn(Optional.of(fakeSession));

        List<String> received = new ArrayList<>();
        doAnswer(inv -> {
            Consumer<String> cb = inv.getArgument(1);
            cb.accept("Tok1");
            cb.accept("Tok2");
            return null;
        }).when(llmService).streamChat(any(), any());

        orchestration.streamReply("session-123", "Pytanie", received::add);

        assertThat(received).containsExactly("Tok1", "Tok2");
    }

    @Test
    void chatTurnAppendsAssembledReplyToSession() {
        when(sessionStore.get("session-123")).thenReturn(Optional.of(fakeSession));

        doAnswer(inv -> {
            Consumer<String> cb = inv.getArgument(1);
            cb.accept("Odpowiedź ");
            cb.accept("asystenta.");
            return null;
        }).when(llmService).streamChat(any(), any());

        orchestration.streamReply("session-123", "Pytanie", t -> {});

        // sessionStore.appendMessage should be called for user message and assistant reply
        verify(sessionStore, atLeast(1)).appendMessage(eq("session-123"), any());
        // The assistant reply must be appended after streamChat completes
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(sessionStore, atLeast(2)).appendMessage(eq("session-123"), msgCaptor.capture());
        List<ChatMessage> appended = msgCaptor.getAllValues();
        // Last appended message should be ASSISTANT with full assembled content
        ChatMessage lastMsg = appended.get(appended.size() - 1);
        assertThat(lastMsg.role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(lastMsg.content()).isEqualTo("Odpowiedź asystenta.");
    }

    // =========================================================================
    // Blank message → IllegalArgumentException
    // =========================================================================

    @Test
    void blankMessageThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> orchestration.streamReply("session-123", "   ", t -> {}))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sessionStore, never()).get(anyString());
    }

    @Test
    void nullMessageThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> orchestration.streamReply("session-123", null, t -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // Unknown session → SessionNotFoundException
    // =========================================================================

    @Test
    void unknownSessionThrowsSessionNotFoundException() {
        when(sessionStore.get("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestration.streamReply("unknown-id", "Wiadomość", t -> {}))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
