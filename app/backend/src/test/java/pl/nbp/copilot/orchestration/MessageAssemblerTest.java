package pl.nbp.copilot.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — MessageAssembler (BE-7).
 * Tests written BEFORE implementation.
 */
class MessageAssemblerTest {

    private MessageAssembler assembler;

    private CaseIntake returnIntake;
    private ImageFindings cleanFindings;
    private DecisionResult approveDecision;
    private DecisionResult needsInfoDecision;

    @BeforeEach
    void setUp() {
        assembler = new MessageAssembler();

        returnIntake = new CaseIntake(
                CaseType.RETURN, EquipmentCategory.LAPTOPS,
                "Laptop XYZ", LocalDate.of(2025, 3, 10),
                null, "image/jpeg", "clean-return.jpg");

        cleanFindings = new ImageFindings(
                true, "Dobry stan urządzenia.",
                false, true, false, null, null,
                true, null, ImageFindings.Confidence.HIGH);

        approveDecision = new DecisionResult(
                Verdict.APPROVE,
                "Urządzenie spełnia warunki zwrotu.",
                List.of("Zapakuj urządzenie.", "Dostarcz do punktu obsługi."),
                false, null,
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");

        needsInfoDecision = new DecisionResult(
                Verdict.NEEDS_INFO,
                "Zdjęcie wskazuje na uszkodzenia.",
                List.of("Skontaktuj się z obsługą."),
                true,
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu.",
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.");
    }

    @Test
    void buildFirstMessageContainsGreeting() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, approveDecision);
        // Should have some greeting-like text
        assertThat(msg).isNotBlank();
        // Polish content - should contain "Dzień dobry" or similar
        assertThat(msg.toLowerCase()).contains("dzień");
    }

    @Test
    void buildFirstMessageContainsPolishVerdictLabel() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, approveDecision);
        // Verdict APPROVE → "Zatwierdzone"
        assertThat(msg).contains("Zatwierdzone");
    }

    @Test
    void buildFirstMessageContainsJustification() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, approveDecision);
        assertThat(msg).contains("Urządzenie spełnia warunki zwrotu.");
    }

    @Test
    void buildFirstMessageContainsNextSteps() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, approveDecision);
        assertThat(msg).contains("Zapakuj urządzenie.");
        assertThat(msg).contains("Dostarcz do punktu obsługi.");
    }

    @Test
    void buildFirstMessageContainsMandatoryDisclaimer() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, approveDecision);
        assertThat(msg).contains("niewiążąca ocena");
    }

    @Test
    void buildFirstMessageForNeedsInfoContainsDiscrepancy() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, needsInfoDecision);
        assertThat(msg).contains("Wymaga uzupełnienia");
        // discrepancyNoted=true so explanation should appear
        assertThat(msg).contains("uszkodzeń mechanicznych");
    }

    @Test
    void buildFirstMessageIsMarkdown() {
        String msg = assembler.buildFirstMessage(returnIntake, cleanFindings, approveDecision);
        // Should use markdown formatting (bold or header markers)
        assertThat(msg).matches("(?s).*[#*_].*");
    }

    @Test
    void buildSystemMessageIsNonBlank() {
        String sysMsg = assembler.buildSystemMessage(returnIntake);
        assertThat(sysMsg).isNotBlank();
    }

    @Test
    void buildSystemMessageMentionsCaseType() {
        String sysMsg = assembler.buildSystemMessage(returnIntake);
        // should mention the case type label or context
        assertThat(sysMsg.toLowerCase()).containsAnyOf("zwrot", "reklamacja", "asystent", "copilot");
    }
}
