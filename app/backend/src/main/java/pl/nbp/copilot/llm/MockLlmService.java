package pl.nbp.copilot.llm;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.nbp.copilot.domain.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic mock implementation of {@link LlmService} for E2E and integration testing.
 *
 * <p>Active only under the {@code mock-llm} profile.
 *
 * <p>The scenario is derived from {@link CaseIntake#imageFilename()} (case-insensitive contains):
 * <ul>
 *   <li>{@code clean-return}   — device in good condition, APPROVE verdict</li>
 *   <li>{@code damaged-complaint} — manufacturing defect, APPROVE verdict</li>
 *   <li>{@code contradiction}  — findings mismatch declared case, NEEDS_INFO verdict</li>
 *   <li>{@code unreadable}     — image cannot be analyzed</li>
 *   <li>{@code llm-fail}       — throws {@link LlmUnavailableException} from analyzeImage</li>
 *   <li>default (anything else) — same as clean-return</li>
 * </ul>
 *
 * <p>{@link #streamChat(List, Consumer)} emits a fixed sequence of Polish tokens.
 *
 * <p>BE-6, TAC-002.
 */
@Service
@Profile("mock-llm")
public class MockLlmService implements LlmService {

    /** Mandatory Polish non-binding disclaimer (PRD §11, AC-21). */
    private static final String DISCLAIMER =
            "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.";

    /** Deterministic token sequence for streamChat. */
    private static final List<String> CHAT_TOKENS =
            List.of("Oto", " moja", " odpowiedź.", " Proszę", " o", " kontakt.");

    @Override
    public ImageFindings analyzeImage(CaseType caseType, CaseIntake intake,
                                      String imageBase64, String contentType) {
        String filename = intake.imageFilename() != null
                ? intake.imageFilename().toLowerCase()
                : "";

        if (filename.contains("llm-fail")) {
            throw new LlmUnavailableException();
        }
        if (filename.contains("unreadable")) {
            return unreadableFindings();
        }
        if (filename.contains("contradiction")) {
            return contradictionFindings();
        }
        if (filename.contains("damaged-complaint")) {
            return damagedComplaintFindings();
        }
        // clean-return and default
        return cleanReturnFindings();
    }

    @Override
    public DecisionResult decide(CaseType caseType, CaseIntake intake,
                                 ImageFindings findings, String policyText) {
        String filename = intake.imageFilename() != null
                ? intake.imageFilename().toLowerCase()
                : "";

        if (filename.contains("unreadable")) {
            return stubNeedsInfoDecision();
        }
        if (filename.contains("contradiction")) {
            return contradictionDecision();
        }
        if (filename.contains("damaged-complaint")) {
            return damagedComplaintDecision();
        }
        // clean-return and default
        return cleanReturnDecision();
    }

    @Override
    public void streamChat(List<ChatMessage> history, Consumer<String> onToken) {
        for (String token : CHAT_TOKENS) {
            onToken.accept(token);
        }
    }

    // =========================================================================
    // Scenario: clean-return (and default)
    // =========================================================================

    private ImageFindings cleanReturnFindings() {
        return new ImageFindings(
                true,
                "Urządzenie w dobrym stanie, brak śladów użytkowania.",
                false,
                true,
                false,
                null,
                null,
                true,
                null,
                ImageFindings.Confidence.HIGH
        );
    }

    private DecisionResult cleanReturnDecision() {
        return new DecisionResult(
                Verdict.APPROVE,
                "Urządzenie spełnia warunki zwrotu.",
                List.of(
                        "Zapakuj urządzenie w oryginalne opakowanie.",
                        "Dostarcz do punktu obsługi."
                ),
                false,
                null,
                DISCLAIMER
        );
    }

    // =========================================================================
    // Scenario: damaged-complaint
    // =========================================================================

    private ImageFindings damagedComplaintFindings() {
        return new ImageFindings(
                true,
                "Widoczne uszkodzenie ekranu.",
                null,
                null,
                true,
                "Pęknięty ekran",
                ImageFindings.LikelyCause.MANUFACTURING,
                true,
                null,
                ImageFindings.Confidence.HIGH
        );
    }

    private DecisionResult damagedComplaintDecision() {
        return new DecisionResult(
                Verdict.APPROVE,
                "Uszkodzenie powstało z winy producenta.",
                List.of(
                        "Dostarcz urządzenie do serwisu.",
                        "Zachowaj potwierdzenie zgłoszenia."
                ),
                false,
                null,
                DISCLAIMER
        );
    }

    // =========================================================================
    // Scenario: contradiction
    // =========================================================================

    private ImageFindings contradictionFindings() {
        return new ImageFindings(
                true,
                "Urządzenie posiada ślady uszkodzeń mechanicznych.",
                null,
                null,
                true,
                null,
                null,
                false,
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu",
                ImageFindings.Confidence.MEDIUM
        );
    }

    private DecisionResult contradictionDecision() {
        return new DecisionResult(
                Verdict.NEEDS_INFO,
                "Zdjęcie wskazuje na uszkodzenia niespójne z rodzajem zgłoszenia.",
                List.of("Skontaktuj się z obsługą w celu wyjaśnienia."),
                true,
                "Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu.",
                DISCLAIMER
        );
    }

    // =========================================================================
    // Scenario: unreadable
    // =========================================================================

    private ImageFindings unreadableFindings() {
        return new ImageFindings(
                false,
                "Nie można przeanalizować zdjęcia.",
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                ImageFindings.Confidence.LOW
        );
    }

    private DecisionResult stubNeedsInfoDecision() {
        return new DecisionResult(
                Verdict.NEEDS_INFO,
                "Nie można przeanalizować przesłanego zdjęcia.",
                List.of("Prześlij zdjęcie o lepszej jakości."),
                false,
                null,
                DISCLAIMER
        );
    }
}
