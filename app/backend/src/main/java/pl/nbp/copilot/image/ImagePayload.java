package pl.nbp.copilot.image;

/**
 * Result of image validation and compression.
 *
 * <p>Contains the base64-encoded image ready for the vision model, along with
 * metadata about the compressed output (used for tests and logging).
 *
 * <p>AC-11, TAC-001-03.
 */
public record ImagePayload(

        /** Base64-encoded compressed image bytes (no data-URL prefix). */
        String base64,

        /** MIME type of the compressed output (always "image/jpeg" in current impl). */
        String contentType,

        /** Width of the compressed output in pixels. */
        int width,

        /** Height of the compressed output in pixels. */
        int height,

        /** Byte length of the compressed output. */
        int compressedBytes

) {}
