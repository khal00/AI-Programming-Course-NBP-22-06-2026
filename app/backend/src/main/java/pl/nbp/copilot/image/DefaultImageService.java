package pl.nbp.copilot.image;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;

/**
 * Default implementation of {@link ImageService} using Thumbnailator for
 * image resize/compression.
 *
 * <p>Validation guards run strictly before any image decoding to prevent OOM
 * from large malformed payloads (TAC-001-02). Re-encodes output as JPEG at
 * quality 0.8 to fit vision model input limits.
 *
 * <p>Max edge cap: {@code maxEdgePx} (default 1024). Quality: 0.8.
 *
 * <p>AC-07, AC-08, AC-11; TAC-01, TAC-02, TAC-03, TAC-001-02, TAC-001-03.
 */
public class DefaultImageService implements ImageService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final String OUTPUT_CONTENT_TYPE = "image/jpeg";
    private static final String OUTPUT_FORMAT       = "JPEG";
    private static final float  JPEG_QUALITY        = 0.80f;

    private final long maxBytes;
    private final int  maxEdgePx;

    /**
     * Creates a service with the given limits.
     *
     * @param maxBytes  maximum accepted input size in bytes
     * @param maxEdgePx maximum edge (width or height) of the output image in pixels
     */
    public DefaultImageService(long maxBytes, int maxEdgePx) {
        this.maxBytes  = maxBytes;
        this.maxEdgePx = maxEdgePx;
    }

    @Override
    public ImagePayload process(String contentType, byte[] bytes) {
        // Guard 1: content type (before decode)
        validateContentType(contentType);

        // Guard 2: size (before decode)
        validateSize(bytes);

        // Guard 3: decode
        BufferedImage image = decode(bytes);

        // Resize and re-encode
        return compress(image);
    }

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ImageValidationException(
                    "Niedozwolony format obrazu. Akceptowane formaty: JPEG, PNG, WebP. " +
                    "Przesłany typ: " + contentType);
        }
    }

    private void validateSize(byte[] bytes) {
        if (bytes == null || bytes.length > maxBytes) {
            long limitMb = maxBytes / (1024 * 1024);
            throw new ImageValidationException(
                    "Rozmiar obrazu przekracza dozwolony limit " + limitMb + " MB " +
                    "(" + maxBytes + " bajtów). Prześlij mniejsze zdjęcie.");
        }
    }

    // -------------------------------------------------------------------------
    // Decode
    // -------------------------------------------------------------------------

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ImageValidationException(
                        "Nie udało się odczytać obrazu. Plik może być uszkodzony lub " +
                        "w nieobsługiwanym formacie.");
            }
            return image;
        } catch (IOException e) {
            throw new ImageValidationException(
                    "Nie udało się odczytać obrazu: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Resize and re-encode
    // -------------------------------------------------------------------------

    private ImagePayload compress(BufferedImage source) {
        try {
            int origWidth  = source.getWidth();
            int origHeight = source.getHeight();

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            if (origWidth <= maxEdgePx && origHeight <= maxEdgePx) {
                // No resize needed — just re-encode at target quality
                Thumbnails.of(source)
                        .scale(1.0)
                        .outputFormat(OUTPUT_FORMAT)
                        .outputQuality(JPEG_QUALITY)
                        .toOutputStream(out);
            } else {
                // Resize so the longer edge == maxEdgePx (preserve aspect ratio)
                Thumbnails.of(source)
                        .size(maxEdgePx, maxEdgePx)
                        .keepAspectRatio(true)
                        .outputFormat(OUTPUT_FORMAT)
                        .outputQuality(JPEG_QUALITY)
                        .toOutputStream(out);
            }

            byte[] compressed = out.toByteArray();

            // Read back to get actual dimensions
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(compressed));
            int outWidth  = (result != null) ? result.getWidth()  : maxEdgePx;
            int outHeight = (result != null) ? result.getHeight() : maxEdgePx;

            return new ImagePayload(
                    Base64.getEncoder().encodeToString(compressed),
                    OUTPUT_CONTENT_TYPE,
                    outWidth,
                    outHeight,
                    compressed.length
            );
        } catch (IOException e) {
            throw new ImageValidationException(
                    "Nie udało się skompresować obrazu: " + e.getMessage(), e);
        }
    }
}
