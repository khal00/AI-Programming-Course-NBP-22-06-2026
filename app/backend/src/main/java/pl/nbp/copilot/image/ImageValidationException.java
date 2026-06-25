package pl.nbp.copilot.image;

/**
 * Thrown when an uploaded image fails validation (wrong MIME type, oversize,
 * or undecodable bytes). Maps to HTTP 400 via the global exception handler.
 *
 * <p>AC-07, AC-08, TAC-01, TAC-02.
 */
public class ImageValidationException extends RuntimeException {

    public ImageValidationException(String message) {
        super(message);
    }

    public ImageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
