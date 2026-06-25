package pl.nbp.copilot.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — Jackson (de)serialization round-trip and validation.
 * Written BEFORE production code.
 *
 * Verifies:
 * 1. CaseResponse round-trip produces documented field names.
 * 2. MetadataResponse round-trip produces documented field names.
 * 3. ErrorResponse round-trip produces documented field names, no stack traces.
 * 4. ChatRequest blank message fails Jakarta validation.
 * 5. ImageFindings round-trip produces documented field names.
 * 6. DecisionResult round-trip produces documented field names.
 */
class DtoSerializationTest {

    private ObjectMapper mapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CaseResponse
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void caseResponseRoundTrip_hasExpectedFieldNames() throws Exception {
        CaseResponse.CaseSummary summary = new CaseResponse.CaseSummary(
                CaseType.COMPLAINT, "Reklamacja",
                EquipmentCategory.SMARTPHONES, "Smartfony",
                "iPhone 15", LocalDate.of(2024, 1, 15));

        CaseResponse response = new CaseResponse(
                "session-abc-123", summary, Verdict.APPROVE, "Witaj, Twoja sprawa została zatwierdzona.");

        String json = mapper.writeValueAsString(response);
        JsonNode node = mapper.readTree(json);

        assertThat(node.has("sessionId")).isTrue();
        assertThat(node.has("caseSummary")).isTrue();
        assertThat(node.has("verdict")).isTrue();
        assertThat(node.has("firstMessage")).isTrue();

        JsonNode summaryNode = node.get("caseSummary");
        assertThat(summaryNode.has("caseType")).isTrue();
        assertThat(summaryNode.has("caseTypeLabel")).isTrue();
        assertThat(summaryNode.has("category")).isTrue();
        assertThat(summaryNode.has("categoryLabel")).isTrue();
        assertThat(summaryNode.has("modelName")).isTrue();
        assertThat(summaryNode.has("purchaseDate")).isTrue();

        // Round-trip
        CaseResponse deserialized = mapper.readValue(json, CaseResponse.class);
        assertThat(deserialized.sessionId()).isEqualTo("session-abc-123");
        assertThat(deserialized.verdict()).isEqualTo(Verdict.APPROVE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MetadataResponse
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void metadataResponseRoundTrip_hasExpectedFieldNames() throws Exception {
        MetadataResponse response = new MetadataResponse(
                List.of(new MetadataResponse.Option("COMPLAINT", "Reklamacja"),
                        new MetadataResponse.Option("RETURN", "Zwrot")),
                List.of(new MetadataResponse.Option("SMARTPHONES", "Smartfony"))
        );

        String json = mapper.writeValueAsString(response);
        JsonNode node = mapper.readTree(json);

        assertThat(node.has("caseTypes")).isTrue();
        assertThat(node.has("categories")).isTrue();

        JsonNode firstCaseType = node.get("caseTypes").get(0);
        assertThat(firstCaseType.has("code")).isTrue();
        assertThat(firstCaseType.has("label")).isTrue();

        // Round-trip
        MetadataResponse deserialized = mapper.readValue(json, MetadataResponse.class);
        assertThat(deserialized.caseTypes()).hasSize(2);
        assertThat(deserialized.categories()).hasSize(1);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ErrorResponse — must NOT contain stack trace fields (AC-29)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void errorResponseRoundTrip_hasExpectedFieldNames_noStackTrace() throws Exception {
        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR",
                "Wystąpiły błędy walidacji formularza.",
                List.of(new ErrorResponse.FieldError("modelName", "Nazwa modelu nie może być pusta."))
        );

        String json = mapper.writeValueAsString(response);
        JsonNode node = mapper.readTree(json);

        assertThat(node.has("code")).isTrue();
        assertThat(node.has("message")).isTrue();
        assertThat(node.has("fieldErrors")).isTrue();

        // AC-29: no stack trace / internal fields
        assertThat(node.has("trace")).isFalse();
        assertThat(node.has("stackTrace")).isFalse();
        assertThat(node.has("exception")).isFalse();

        JsonNode firstError = node.get("fieldErrors").get(0);
        assertThat(firstError.has("field")).isTrue();
        assertThat(firstError.has("message")).isTrue();

        // Round-trip
        ErrorResponse deserialized = mapper.readValue(json, ErrorResponse.class);
        assertThat(deserialized.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(deserialized.fieldErrors()).hasSize(1);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ChatRequest — blank message fails Jakarta validation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void chatRequest_blankMessage_failsValidation() {
        ChatRequest request = new ChatRequest("   ");
        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void chatRequest_nullMessage_failsValidation() {
        ChatRequest request = new ChatRequest(null);
        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void chatRequest_validMessage_passesValidation() {
        ChatRequest request = new ChatRequest("Czy mogę zapytać o szczegóły?");
        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ImageFindings — ADR-002 structured schema
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void imageFindingsRoundTrip_hasExpectedFieldNames() throws Exception {
        pl.nbp.copilot.domain.ImageFindings findings = new pl.nbp.copilot.domain.ImageFindings(
                true,
                "Urządzenie widoczne, brak uszkodzeń",
                false,
                true,
                false,
                null,
                pl.nbp.copilot.domain.ImageFindings.LikelyCause.UNKNOWN,
                true,
                null,
                pl.nbp.copilot.domain.ImageFindings.Confidence.HIGH
        );

        String json = mapper.writeValueAsString(findings);
        JsonNode node = mapper.readTree(json);

        assertThat(node.has("readable")).isTrue();
        assertThat(node.has("description")).isTrue();
        assertThat(node.has("signsOfUse")).isTrue();
        assertThat(node.has("resellable")).isTrue();
        assertThat(node.has("damagePresent")).isTrue();
        assertThat(node.has("damageType")).isTrue();
        assertThat(node.has("likelyCauseCategory")).isTrue();
        assertThat(node.has("matchesDeclaredCase")).isTrue();
        assertThat(node.has("discrepancyNote")).isTrue();
        assertThat(node.has("confidence")).isTrue();

        // Round-trip
        pl.nbp.copilot.domain.ImageFindings deserialized =
                mapper.readValue(json, pl.nbp.copilot.domain.ImageFindings.class);
        assertThat(deserialized.readable()).isTrue();
        assertThat(deserialized.confidence()).isEqualTo(pl.nbp.copilot.domain.ImageFindings.Confidence.HIGH);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DecisionResult — ADR-002 structured schema
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void decisionResultRoundTrip_hasExpectedFieldNames() throws Exception {
        pl.nbp.copilot.domain.DecisionResult result = new pl.nbp.copilot.domain.DecisionResult(
                Verdict.APPROVE,
                "Urządzenie jest w dobrym stanie i spełnia warunki zwrotu.",
                List.of("Zapakuj urządzenie", "Dostarcz do punktu odbioru"),
                false,
                null,
                "To jest wstępna, niewiążąca ocena. Ostateczną decyzję podejmuje pracownik obsługi."
        );

        String json = mapper.writeValueAsString(result);
        JsonNode node = mapper.readTree(json);

        assertThat(node.has("verdict")).isTrue();
        assertThat(node.has("justification")).isTrue();
        assertThat(node.has("nextSteps")).isTrue();
        assertThat(node.has("discrepancyNoted")).isTrue();
        assertThat(node.has("discrepancyExplanation")).isTrue();
        assertThat(node.has("disclaimer")).isTrue();

        // Round-trip
        pl.nbp.copilot.domain.DecisionResult deserialized =
                mapper.readValue(json, pl.nbp.copilot.domain.DecisionResult.class);
        assertThat(deserialized.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(deserialized.nextSteps()).hasSize(2);
        assertThat(deserialized.disclaimer()).contains("wstępna");
    }
}
