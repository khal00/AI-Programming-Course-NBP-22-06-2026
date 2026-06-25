package pl.nbp.copilot.image;

/**
 * Thrown when the LLM cannot analyze the uploaded image (readable=false).
 * Maps to HTTP 422 via the global exception handler.
 *
 * <p>TAC-07, AC-12.
 */
public class ImageUnreadableException extends RuntimeException {

    public ImageUnreadableException() {
        super("Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.");
    }
}
