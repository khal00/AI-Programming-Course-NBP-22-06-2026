package pl.nbp.copilot.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.CaseIntake;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.orchestration.CaseOrchestrationService;
import pl.nbp.copilot.orchestration.NewCaseResult;
import pl.nbp.copilot.web.dto.CaseResponse;
import pl.nbp.copilot.web.dto.ErrorResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * POST /api/cases — multipart intake form submission, validation, and orchestration.
 *
 * <p>Validates all fields manually (multipart cannot use @Valid on individual @RequestParam).
 * On any validation error, throws {@link ValidationException} which the
 * {@link GlobalExceptionHandler} maps to 400 with per-field Polish messages.
 *
 * <p>ADR-001 §3/§5, TAC-001-01, TAC-001-02, TAC-05..08.
 */
@RestController
@RequestMapping("/api")
public class CaseController {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");

    private final CaseOrchestrationService orchestration;
    private final long maxImageBytes;

    public CaseController(CaseOrchestrationService orchestration, AppProperties props) {
        this.orchestration = orchestration;
        this.maxImageBytes = props.image().maxBytes();
    }

    @PostMapping(value = "/cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse submitCase(
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String purchaseDate,
            @RequestParam(required = false) String reason,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {

        List<ErrorResponse.FieldError> errors = new ArrayList<>();

        // Validate caseType
        CaseType parsedCaseType = null;
        if (isBlank(caseType)) {
            errors.add(new ErrorResponse.FieldError("caseType",
                    "Typ zgłoszenia jest wymagany."));
        } else {
            try {
                parsedCaseType = CaseType.valueOf(caseType.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new ErrorResponse.FieldError("caseType",
                        "Nieprawidłowy typ zgłoszenia. Dozwolone wartości: COMPLAINT, RETURN."));
            }
        }

        // Validate category
        EquipmentCategory parsedCategory = null;
        if (isBlank(category)) {
            errors.add(new ErrorResponse.FieldError("category",
                    "Kategoria sprzętu jest wymagana."));
        } else {
            try {
                parsedCategory = EquipmentCategory.valueOf(category.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new ErrorResponse.FieldError("category",
                        "Nieprawidłowa kategoria sprzętu."));
            }
        }

        // Validate modelName
        if (isBlank(modelName) || modelName.trim().isEmpty()) {
            errors.add(new ErrorResponse.FieldError("modelName",
                    "Nazwa modelu urządzenia jest wymagana."));
        }

        // Validate purchaseDate
        LocalDate parsedDate = null;
        if (isBlank(purchaseDate)) {
            errors.add(new ErrorResponse.FieldError("purchaseDate",
                    "Data zakupu jest wymagana."));
        } else {
            try {
                parsedDate = LocalDate.parse(purchaseDate.trim());
                if (parsedDate.isAfter(LocalDate.now())) {
                    errors.add(new ErrorResponse.FieldError("purchaseDate",
                            "Data zakupu nie może być w przyszłości."));
                    parsedDate = null;
                }
            } catch (Exception e) {
                errors.add(new ErrorResponse.FieldError("purchaseDate",
                        "Nieprawidłowy format daty. Wymagany format: rrrr-mm-dd."));
            }
        }

        // Validate reason (required for COMPLAINT)
        if (parsedCaseType == CaseType.COMPLAINT && isBlank(reason)) {
            errors.add(new ErrorResponse.FieldError("reason",
                    "Opis usterki jest wymagany dla reklamacji."));
        }

        // Validate image
        String imageContentType = null;
        String imageFilename = null;
        byte[] imageBytes = null;
        if (image == null || image.isEmpty()) {
            errors.add(new ErrorResponse.FieldError("image",
                    "Zdjęcie urządzenia jest wymagane."));
        } else {
            imageContentType = image.getContentType();
            imageFilename = image.getOriginalFilename() != null
                    ? image.getOriginalFilename() : "image.jpg";

            if (!isAllowedMimeType(imageContentType)) {
                errors.add(new ErrorResponse.FieldError("image",
                        "Niedozwolony format obrazu. Akceptowane formaty: JPEG, PNG, WebP."));
            } else if (image.getSize() > maxImageBytes) {
                errors.add(new ErrorResponse.FieldError("image",
                        "Rozmiar zdjęcia przekracza dozwolony limit 10 MB."));
            } else {
                imageBytes = image.getBytes();
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // Build intake and delegate to orchestration
        CaseIntake intake = new CaseIntake(
                parsedCaseType, parsedCategory,
                modelName.trim(), parsedDate,
                reason != null ? reason.trim() : null,
                imageContentType, imageFilename);

        NewCaseResult result = orchestration.handleNewCase(intake, imageBytes);

        CaseResponse.CaseSummary caseSummary = new CaseResponse.CaseSummary(
                result.caseSummary().caseType(),
                result.caseSummary().caseType().getLabel(),
                result.caseSummary().category(),
                result.caseSummary().category().getLabel(),
                result.caseSummary().modelName(),
                result.caseSummary().purchaseDate());

        return new CaseResponse(result.sessionId(), caseSummary, result.verdict(), result.firstMessage());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isAllowedMimeType(String contentType) {
        return contentType != null && ALLOWED_MIME_TYPES.contains(contentType.toLowerCase());
    }
}
