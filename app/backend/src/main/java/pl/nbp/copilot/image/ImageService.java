package pl.nbp.copilot.image;

/**
 * Validates the uploaded image and produces a model-ready payload.
 *
 * <p>Validation order (guards applied before any decoding):
 * <ol>
 *   <li>Content-type must be JPEG, PNG, or WebP.</li>
 *   <li>Byte size must be ≤ the configured maximum.</li>
 *   <li>Bytes must be decodable as an image.</li>
 * </ol>
 *
 * <p>After validation, the image is resized to a max edge and re-encoded as JPEG
 * to fit within vision model input limits.
 *
 * <p>AC-07, AC-08, AC-11; TAC-01, TAC-02, TAC-03.
 */
public interface ImageService {

    /**
     * Validates the image, resizes it if necessary, and returns a model-ready payload.
     *
     * @param contentType declared MIME type (e.g. "image/jpeg")
     * @param bytes       raw image bytes
     * @return {@link ImagePayload} with base64-encoded compressed image
     * @throws ImageValidationException if the content-type is not allowed,
     *                                  the size exceeds the limit,
     *                                  or the bytes cannot be decoded
     */
    ImagePayload process(String contentType, byte[] bytes);
}
