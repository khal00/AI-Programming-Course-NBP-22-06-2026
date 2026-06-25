package pl.nbp.copilot.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.domain.CaseIntake;
import pl.nbp.copilot.domain.CaseType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Classpath-backed implementation of {@link PromptTemplateProvider}.
 *
 * <p>Loads template files from {@code src/main/resources/prompts/} and fills
 * {@code {placeholder}} tokens. All prompts are in Polish as required (AC-28).
 * The mandatory non-binding disclaimer is embedded in the templates (AC-21).
 *
 * <p>ADR-002 prompt contracts; AC-12, AC-17, AC-18, AC-21, TAC-04, TAC-09.
 */
@Component
public class ResourcePromptTemplateProvider implements PromptTemplateProvider {

    static final String DISCLAIMER =
            "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi.";

    private static final String VERDICT_LABELS =
            "APPROVE (Zatwierdzone) | REJECT (Odrzucone) | NEEDS_INFO (Wymaga uzupełnienia) | ESCALATE (Eskalacja do specjalisty)";

    // -------------------------------------------------------------------------
    // Image prompts
    // -------------------------------------------------------------------------

    @Override
    public String imagePrompt(CaseType caseType, CaseIntake intake) {
        String template = loadTemplate(imageTemplatePath(caseType));
        return fillFormPlaceholders(template, intake);
    }

    // -------------------------------------------------------------------------
    // Decision prompts
    // -------------------------------------------------------------------------

    @Override
    public String decisionPrompt(CaseType caseType, CaseIntake intake,
                                 String imageFindings, String policyText) {
        String template = loadTemplate(decisionTemplatePath(caseType));
        String filled   = fillFormPlaceholders(template, intake);
        filled = filled.replace("{imageFindings}", imageFindings);
        filled = filled.replace("{policyText}",    policyText);
        filled = filled.replace("{verdictLabels}", VERDICT_LABELS);
        filled = filled.replace("{disclaimer}",    DISCLAIMER);
        return filled;
    }

    // -------------------------------------------------------------------------
    // Chat system prompt
    // -------------------------------------------------------------------------

    @Override
    public String chatSystemPrompt(CaseIntake intake, String imageFindings,
                                   String verdict, String justification,
                                   List<String> nextSteps) {
        String template = loadTemplate("prompts/chat-system.txt");
        String filled   = fillFormPlaceholders(template, intake);
        filled = filled.replace("{imageFindings}", imageFindings);
        filled = filled.replace("{verdict}",       verdict);
        filled = filled.replace("{justification}", justification);
        filled = filled.replace("{nextSteps}",     formatNextSteps(nextSteps));
        filled = filled.replace("{disclaimer}",    DISCLAIMER);
        return filled;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String fillFormPlaceholders(String template, CaseIntake intake) {
        String result = template;
        result = result.replace("{caseType}",      intake.caseType().name());
        result = result.replace("{caseTypeLabel}", intake.caseType().getLabel());
        result = result.replace("{category}",      intake.category().name());
        result = result.replace("{categoryLabel}", intake.category().getLabel());
        result = result.replace("{modelName}",     intake.modelName());
        result = result.replace("{purchaseDate}",  intake.purchaseDate().toString());
        result = result.replace("{reason}",        intake.reason() != null ? intake.reason() : "brak opisu");
        return result;
    }

    private String formatNextSteps(List<String> nextSteps) {
        if (nextSteps == null || nextSteps.isEmpty()) {
            return "brak";
        }
        return IntStream.range(0, nextSteps.size())
                .mapToObj(i -> (i + 1) + ". " + nextSteps.get(i))
                .collect(Collectors.joining("\n"));
    }

    private String imageTemplatePath(CaseType caseType) {
        return switch (caseType) {
            case RETURN    -> "prompts/image-return.txt";
            case COMPLAINT -> "prompts/image-complaint.txt";
        };
    }

    private String decisionTemplatePath(CaseType caseType) {
        return switch (caseType) {
            case RETURN    -> "prompts/decision-return.txt";
            case COMPLAINT -> "prompts/decision-complaint.txt";
        };
    }

    private String loadTemplate(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new PromptTemplateNotFoundException(path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PromptTemplateNotFoundException(path, e);
        }
    }
}
