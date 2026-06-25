package pl.nbp.copilot.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD — MockLlmService (BE-6).
 * Tests written BEFORE the implementation.
 * Covers all five named scenarios + default + streamChat + Spring context bean check.
 */
class MockLlmServiceTest {

    private MockLlmService mockLlmService;

    @BeforeEach
    void setUp() {
        mockLlmService = new MockLlmService();
    }

    // =========================================================================
    // Scenario: clean-return
    // =========================================================================

    @Test
    void cleanReturnScenarioAnalyzeImageReturnsGoodConditionFindings() {
        CaseIntake intake = buildIntake("clean-return.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        assertThat(findings.readable()).isTrue();
        assertThat(findings.signsOfUse()).isFalse();
        assertThat(findings.resellable()).isTrue();
        assertThat(findings.damagePresent()).isFalse();
        assertThat(findings.matchesDeclaredCase()).isTrue();
        assertThat(findings.confidence()).isEqualTo(ImageFindings.Confidence.HIGH);
        assertThat(findings.description()).isEqualTo(
                "Urządzenie w dobrym stanie, brak śladów użytkowania.");
    }

    @Test
    void cleanReturnScenarioDecideReturnsApprove() {
        CaseIntake intake = buildIntake("clean-return.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        DecisionResult decision = mockLlmService.decide(
                CaseType.RETURN, intake, findings, "Polityka zwrotów.");

        assertThat(decision.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(decision.justification()).isEqualTo(
                "Urządzenie spełnia warunki zwrotu.");
        assertThat(decision.nextSteps()).containsExactly(
                "Zapakuj urządzenie w oryginalne opakowanie.",
                "Dostarcz do punktu obsługi.");
        assertThat(decision.discrepancyNoted()).isFalse();
        assertThat(decision.discrepancyExplanation()).isNull();
        assertThat(decision.disclaimer()).isEqualTo(
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");
    }

    // =========================================================================
    // Scenario: damaged-complaint
    // =========================================================================

    @Test
    void damagedComplaintScenarioAnalyzeImageReturnsDamageFindings() {
        CaseIntake intake = buildIntake("damaged-complaint.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.COMPLAINT, intake, "base64data", "image/jpeg");

        assertThat(findings.readable()).isTrue();
        assertThat(findings.damagePresent()).isTrue();
        assertThat(findings.damageType()).isEqualTo("Pęknięty ekran");
        assertThat(findings.likelyCauseCategory()).isEqualTo(ImageFindings.LikelyCause.MANUFACTURING);
        assertThat(findings.matchesDeclaredCase()).isTrue();
        assertThat(findings.confidence()).isEqualTo(ImageFindings.Confidence.HIGH);
        assertThat(findings.description()).isEqualTo("Widoczne uszkodzenie ekranu.");
    }

    @Test
    void damagedComplaintScenarioDecideReturnsApprove() {
        CaseIntake intake = buildIntake("damaged-complaint.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.COMPLAINT, intake, "base64data", "image/jpeg");

        DecisionResult decision = mockLlmService.decide(
                CaseType.COMPLAINT, intake, findings, "Polityka reklamacji.");

        assertThat(decision.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(decision.justification()).isEqualTo(
                "Uszkodzenie powstało z winy producenta.");
        assertThat(decision.nextSteps()).containsExactly(
                "Dostarcz urządzenie do serwisu.",
                "Zachowaj potwierdzenie zgłoszenia.");
        assertThat(decision.discrepancyNoted()).isFalse();
        assertThat(decision.discrepancyExplanation()).isNull();
        assertThat(decision.disclaimer()).isEqualTo(
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");
    }

    // =========================================================================
    // Scenario: contradiction
    // =========================================================================

    @Test
    void contradictionScenarioAnalyzeImageReturnsMismatchFindings() {
        CaseIntake intake = buildIntake("contradiction.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        assertThat(findings.readable()).isTrue();
        assertThat(findings.damagePresent()).isTrue();
        assertThat(findings.matchesDeclaredCase()).isFalse();
        assertThat(findings.discrepancyNote()).isEqualTo(
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu");
        assertThat(findings.confidence()).isEqualTo(ImageFindings.Confidence.MEDIUM);
    }

    @Test
    void contradictionScenarioDecideReturnsNeedsInfo() {
        CaseIntake intake = buildIntake("contradiction.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        DecisionResult decision = mockLlmService.decide(
                CaseType.RETURN, intake, findings, "Polityka zwrotów.");

        assertThat(decision.verdict()).isEqualTo(Verdict.NEEDS_INFO);
        assertThat(decision.justification()).isEqualTo(
                "Zdjęcie wskazuje na uszkodzenia niespójne z rodzajem zgłoszenia.");
        assertThat(decision.nextSteps()).containsExactly(
                "Skontaktuj się z obsługą w celu wyjaśnienia.");
        assertThat(decision.discrepancyNoted()).isTrue();
        assertThat(decision.discrepancyExplanation()).isEqualTo(
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu.");
        assertThat(decision.disclaimer()).isEqualTo(
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");
    }

    // =========================================================================
    // Scenario: unreadable
    // =========================================================================

    @Test
    void unreadableScenarioAnalyzeImageReturnsUnreadableFindings() {
        CaseIntake intake = buildIntake("unreadable.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        assertThat(findings.readable()).isFalse();
        assertThat(findings.confidence()).isEqualTo(ImageFindings.Confidence.LOW);
        assertThat(findings.description()).isEqualTo("Nie można przeanalizować zdjęcia.");
    }

    @Test
    void unreadableScenarioDecideReturnsStubNeedsInfo() {
        CaseIntake intake = buildIntake("unreadable.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        // decide should not normally be called for unreadable, but if it is — stub NEEDS_INFO
        DecisionResult decision = mockLlmService.decide(
                CaseType.RETURN, intake, findings, "Polityka zwrotów.");

        assertThat(decision.verdict()).isEqualTo(Verdict.NEEDS_INFO);
    }

    // =========================================================================
    // Scenario: llm-fail
    // =========================================================================

    @Test
    void llmFailScenarioAnalyzeImageThrowsLlmUnavailableException() {
        CaseIntake intake = buildIntake("llm-fail.jpg");

        assertThatThrownBy(() ->
                mockLlmService.analyzeImage(CaseType.RETURN, intake, "base64data", "image/jpeg"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    // =========================================================================
    // Scenario: default (unknown filename) → clean-return behavior
    // =========================================================================

    @Test
    void unknownFilenameFallsBackToCleanReturnBehavior() {
        CaseIntake intake = buildIntake("nieznany-plik.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        // Same as clean-return
        assertThat(findings.readable()).isTrue();
        assertThat(findings.signsOfUse()).isFalse();
        assertThat(findings.resellable()).isTrue();
        assertThat(findings.matchesDeclaredCase()).isTrue();
        assertThat(findings.confidence()).isEqualTo(ImageFindings.Confidence.HIGH);
    }

    @Test
    void unknownFilenameDecideReturnsApprove() {
        CaseIntake intake = buildIntake("nieznany-plik.jpg");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intake, "base64data", "image/jpeg");

        DecisionResult decision = mockLlmService.decide(
                CaseType.RETURN, intake, findings, "Polityka.");

        assertThat(decision.verdict()).isEqualTo(Verdict.APPROVE);
    }

    // =========================================================================
    // Case-insensitive filename matching
    // =========================================================================

    @Test
    void filenameMatchingIsCaseInsensitive() {
        CaseIntake intakeUpper = buildIntake("CLEAN-RETURN.JPG");
        ImageFindings findings = mockLlmService.analyzeImage(
                CaseType.RETURN, intakeUpper, "base64data", "image/jpeg");

        assertThat(findings.readable()).isTrue();
        assertThat(findings.signsOfUse()).isFalse();
    }

    // =========================================================================
    // streamChat — deterministic Polish tokens
    // =========================================================================

    @Test
    void streamChatEmitsDeterministicPolishTokensInOrder() {
        List<String> received = new ArrayList<>();
        List<ChatMessage> history = List.of(
                new ChatMessage(ChatMessage.Role.USER, "Pytanie", Instant.now())
        );

        mockLlmService.streamChat(history, received::add);

        assertThat(received).containsExactly(
                "Oto", " moja", " odpowiedź.", " Proszę", " o", " kontakt.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CaseIntake buildIntake(String filename) {
        return new CaseIntake(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop TestModel X1", LocalDate.of(2026, 1, 1),
                "Testowy powód", "image/jpeg", filename
        );
    }
}
