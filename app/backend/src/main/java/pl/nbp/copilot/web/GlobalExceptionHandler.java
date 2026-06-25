package pl.nbp.copilot.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import pl.nbp.copilot.image.ImageUnreadableException;
import pl.nbp.copilot.image.ImageValidationException;
import pl.nbp.copilot.llm.LlmUnavailableException;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.web.dto.ErrorResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central exception handler that maps domain exceptions to uniform Polish HTTP responses.
 *
 * <p>Rules:
 * <ul>
 *   <li>All user-facing messages are in Polish (AC-28).</li>
 *   <li>NEVER leaks stack traces, internal IDs, prompt text, or raw provider payloads (AC-29).</li>
 *   <li>Logs technical detail server-side only.</li>
 * </ul>
 *
 * <p>ADR-001 §3/§6, TAC-001-06, TAC-13.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ─── 400 — Validation errors (Jakarta Bean Validation on @RequestBody) ─────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        log.warn("Validation error: {}", fieldErrors);
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR",
                "Wystąpiły błędy walidacji formularza.",
                fieldErrors));
    }

    // ─── 400 — Multipart/manual validation errors ──────────────────────────────

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.warn("Form validation error: {}", ex.getFieldErrors());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR",
                "Wystąpiły błędy walidacji formularza.",
                ex.getFieldErrors()));
    }

    // ─── 400 — Image validation error ─────────────────────────────────────────

    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<ErrorResponse> handleImageValidation(ImageValidationException ex) {
        log.warn("Image validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR",
                ex.getMessage(),
                List.of()));
    }

    // ─── 400 — Blank message (IllegalArgumentException from orchestration) ─────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR",
                "Wiadomość nie może być pusta.",
                List.of()));
    }

    // ─── 400 — Upload too large ────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR",
                "Przesłany plik jest za duży. Maksymalny rozmiar zdjęcia to 10 MB.",
                List.of(new ErrorResponse.FieldError("image",
                        "Przesłany plik jest za duży. Maksymalny rozmiar zdjęcia to 10 MB."))));
    }

    // ─── 404 — Session not found ───────────────────────────────────────────────

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex) {
        log.warn("Session not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                "SESSION_NOT_FOUND",
                "Sesja nie istnieje lub wygasła. Rozpocznij nową sprawę.",
                null));
    }

    // ─── 422 — Image unreadable ────────────────────────────────────────────────

    @ExceptionHandler(ImageUnreadableException.class)
    public ResponseEntity<ErrorResponse> handleImageUnreadable(ImageUnreadableException ex) {
        log.warn("Image unreadable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(
                "IMAGE_UNREADABLE",
                "Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.",
                null));
    }

    // ─── 503 — LLM unavailable ────────────────────────────────────────────────

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLlmUnavailable(LlmUnavailableException ex) {
        log.error("LLM unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                "LLM_UNAVAILABLE",
                "Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.",
                null));
    }
}
