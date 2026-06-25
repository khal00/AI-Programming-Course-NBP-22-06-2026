package pl.nbp.copilot.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD — PromptTemplateProvider selects the correct template, fills placeholders,
 * and injects the required content per ADR-002 prompt contracts.
 * Tests written BEFORE the implementation (BE-2, AC-12, AC-17, AC-18, AC-21, TAC-04, TAC-09).
 */
class PromptTemplateProviderTest {

    private static final String DISCLAIMER =
            "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.";

    private PromptTemplateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ResourcePromptTemplateProvider();
    }

    // =========================================================================
    // Image prompts — template selection
    // =========================================================================

    @Test
    void imageReturnPromptIsSelectedForReturnCaseType() {
        String prompt = provider.imagePrompt(CaseType.RETURN, sampleIntake(CaseType.RETURN));
        // Return prompt must mention return-specific fields
        assertThat(prompt).containsIgnoringCase("zwrot");
    }

    @Test
    void imageComplaintPromptIsSelectedForComplaintCaseType() {
        String prompt = provider.imagePrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT));
        assertThat(prompt).containsIgnoringCase("reklamacja");
    }

    @Test
    void imageReturnAndComplaintPromptsAreDifferent() {
        String returnPrompt    = provider.imagePrompt(CaseType.RETURN,    sampleIntake(CaseType.RETURN));
        String complaintPrompt = provider.imagePrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT));
        assertThat(returnPrompt).isNotEqualTo(complaintPrompt);
    }

    // =========================================================================
    // Image prompts — form fields are filled (no leftover {…})
    // =========================================================================

    @Test
    void imageReturnPromptHasNoUnfilledPlaceholders() {
        String prompt = provider.imagePrompt(CaseType.RETURN, sampleIntake(CaseType.RETURN));
        assertThat(prompt).doesNotContainPattern("\\{[a-zA-Z]+\\}");
    }

    @Test
    void imageComplaintPromptHasNoUnfilledPlaceholders() {
        String prompt = provider.imagePrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT));
        assertThat(prompt).doesNotContainPattern("\\{[a-zA-Z]+\\}");
    }

    @Test
    void imageReturnPromptContainsModelName() {
        CaseIntake intake = sampleIntake(CaseType.RETURN);
        String prompt = provider.imagePrompt(CaseType.RETURN, intake);
        assertThat(prompt).contains(intake.modelName());
    }

    @Test
    void imageReturnPromptContainsPurchaseDate() {
        CaseIntake intake = sampleIntake(CaseType.RETURN);
        String prompt = provider.imagePrompt(CaseType.RETURN, intake);
        assertThat(prompt).contains(intake.purchaseDate().toString());
    }

    // =========================================================================
    // Decision prompts — template selection
    // =========================================================================

    @Test
    void decisionReturnPromptIsSelectedForReturnCaseType() {
        String policy   = "Regulamin zwrotów — zasada testowa";
        String findings = "Urządzenie bez śladów użytkowania.";
        String prompt   = provider.decisionPrompt(CaseType.RETURN, sampleIntake(CaseType.RETURN),
                findings, policy);
        assertThat(prompt).containsIgnoringCase("zwrot");
    }

    @Test
    void decisionComplaintPromptIsSelectedForComplaintCaseType() {
        String policy   = "Regulamin reklamacji — zasada testowa";
        String findings = "Pęknięty ekran.";
        String prompt   = provider.decisionPrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT),
                findings, policy);
        assertThat(prompt).containsIgnoringCase("reklamacja");
    }

    // =========================================================================
    // Decision prompts — policy injected
    // =========================================================================

    @Test
    void decisionReturnPromptContainsPolicyText() {
        String policy = "UNIQUE-POLICY-CONTENT-RETURN";
        String prompt = provider.decisionPrompt(CaseType.RETURN, sampleIntake(CaseType.RETURN),
                "some findings", policy);
        assertThat(prompt).contains(policy);
    }

    @Test
    void decisionComplaintPromptContainsPolicyText() {
        String policy = "UNIQUE-POLICY-CONTENT-COMPLAINT";
        String prompt = provider.decisionPrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT),
                "some findings", policy);
        assertThat(prompt).contains(policy);
    }

    // =========================================================================
    // Decision prompts — disclaimer is present (AC-21)
    // =========================================================================

    @Test
    void decisionReturnPromptContainsDisclaimerText() {
        String prompt = provider.decisionPrompt(CaseType.RETURN, sampleIntake(CaseType.RETURN),
                "findings", "policy");
        assertThat(prompt).contains("niewiążąca");
    }

    @Test
    void decisionComplaintPromptContainsDisclaimerText() {
        String prompt = provider.decisionPrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT),
                "findings", "policy");
        assertThat(prompt).contains("niewiążąca");
    }

    // =========================================================================
    // Decision prompts — no leftover placeholders
    // =========================================================================

    @Test
    void decisionReturnPromptHasNoUnfilledPlaceholders() {
        String prompt = provider.decisionPrompt(CaseType.RETURN, sampleIntake(CaseType.RETURN),
                "findings text", "policy text");
        assertThat(prompt).doesNotContainPattern("\\{[a-zA-Z]+\\}");
    }

    @Test
    void decisionComplaintPromptHasNoUnfilledPlaceholders() {
        String prompt = provider.decisionPrompt(CaseType.COMPLAINT, sampleIntake(CaseType.COMPLAINT),
                "findings text", "policy text");
        assertThat(prompt).doesNotContainPattern("\\{[a-zA-Z]+\\}");
    }

    // =========================================================================
    // Chat system prompt — disclaimer and context placeholders
    // =========================================================================

    @Test
    void chatSystemPromptContainsDisclaimerText() {
        String prompt = provider.chatSystemPrompt(sampleIntake(CaseType.COMPLAINT),
                "findings text",
                "NEEDS_INFO",
                "Uzasadnienie.",
                List.of("Krok 1"));
        assertThat(prompt).contains("niewiążąca");
    }

    @Test
    void chatSystemPromptContainsCaseContextModelName() {
        CaseIntake intake = sampleIntake(CaseType.RETURN);
        String prompt = provider.chatSystemPrompt(intake, "findings", "APPROVE",
                "Uzasadnienie.", List.of("Krok 1"));
        assertThat(prompt).contains(intake.modelName());
    }

    @Test
    void chatSystemPromptContainsCaseContextPurchaseDate() {
        CaseIntake intake = sampleIntake(CaseType.RETURN);
        String prompt = provider.chatSystemPrompt(intake, "findings", "APPROVE",
                "Uzasadnienie.", List.of("Krok 1"));
        assertThat(prompt).contains(intake.purchaseDate().toString());
    }

    @Test
    void chatSystemPromptContainsVerdict() {
        String prompt = provider.chatSystemPrompt(sampleIntake(CaseType.RETURN),
                "findings", "APPROVE", "Uzasadnienie.", List.of("Krok 1"));
        assertThat(prompt).contains("APPROVE");
    }

    @Test
    void chatSystemPromptHasNoUnfilledPlaceholders() {
        String prompt = provider.chatSystemPrompt(sampleIntake(CaseType.COMPLAINT),
                "findings text", "REJECT", "Uzasadnienie.", List.of("Krok 1", "Krok 2"));
        assertThat(prompt).doesNotContainPattern("\\{[a-zA-Z]+\\}");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CaseIntake sampleIntake(CaseType caseType) {
        return new CaseIntake(
                caseType,
                EquipmentCategory.SMARTPHONES,
                "Samsung Galaxy S24",
                LocalDate.of(2026, 1, 15),
                caseType == CaseType.COMPLAINT ? "Ekran przestał działać" : null,
                "image/jpeg",
                "test-image.jpg"
        );
    }
}
