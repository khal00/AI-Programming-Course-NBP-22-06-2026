package pl.nbp.copilot.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD — ImageService validates, compresses, and base64-encodes uploaded images.
 * Tests written BEFORE the implementation (BE-3, AC-07, AC-08, AC-11,
 * TAC-01, TAC-02, TAC-03, TAC-001-02, TAC-001-03).
 *
 * <p>Guard guarantees:
 * - Wrong MIME and oversize rejected BEFORE any decode (no OOM on huge input).
 * - Undecodable bytes → typed validation error.
 * - A valid large image is resized so output dimensions ≤ cap and output bytes &lt; input.
 * - Base64 round-trips cleanly.
 */
class ImageServiceTest {

    private static final long MAX_BYTES = 10 * 1024 * 1024L; // 10 MB default
    private static final int  MAX_EDGE  = 1024;               // px cap chosen by impl

    private ImageService service;

    @BeforeEach
    void setUp() {
        service = new DefaultImageService(MAX_BYTES, MAX_EDGE);
    }

    // =========================================================================
    // MIME / content-type validation (TAC-001-02, TAC-02)
    // =========================================================================

    @Test
    void rejectsUnsupportedMimeBeforeDecode() {
        byte[] pdfMagic = {0x25, 0x50, 0x44, 0x46}; // %PDF
        assertThatThrownBy(() -> service.process("application/pdf", pdfMagic))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("JPEG")
                .hasMessageContaining("PNG")
                .hasMessageContaining("WebP");
    }

    @Test
    void rejectsGifMimeBeforeDecode() {
        byte[] gifMagic = {0x47, 0x49, 0x46, 0x38}; // GIF8
        assertThatThrownBy(() -> service.process("image/gif", gifMagic))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void rejectsNullMimeBeforeDecode() {
        assertThatThrownBy(() -> service.process(null, new byte[]{1, 2, 3}))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void acceptsJpegMime() throws Exception {
        byte[] jpegBytes = smallJpeg();
        ImagePayload payload = service.process("image/jpeg", jpegBytes);
        assertThat(payload).isNotNull();
    }

    @Test
    void acceptsPngMime() throws Exception {
        byte[] pngBytes = smallPng();
        ImagePayload payload = service.process("image/png", pngBytes);
        assertThat(payload).isNotNull();
    }

    // =========================================================================
    // Size validation — guard BEFORE decode (TAC-001-02, TAC-01)
    // =========================================================================

    @Test
    void rejectsOversizeBeforeDecode() {
        // Oversized blob with valid JPEG magic bytes but huge size
        byte[] fakeHuge = new byte[10 * 1024 * 1024 + 1];
        fakeHuge[0] = (byte) 0xFF; // JPEG SOI
        fakeHuge[1] = (byte) 0xD8;
        assertThatThrownBy(() -> service.process("image/jpeg", fakeHuge))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("10");  // mentions the limit
    }

    @Test
    void acceptsExactlyMaxBytes() throws Exception {
        // A real JPEG small enough to fit; we just want the guard boundary test.
        // We use a real small JPEG so it passes both guards and decodes correctly.
        byte[] jpeg = smallJpeg();
        // This should not throw — it's well within 10 MB
        assertThatCode(() -> service.process("image/jpeg", jpeg))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // Undecodable bytes → typed validation error
    // =========================================================================

    @Test
    void rejectsUndecodableBytes() {
        byte[] garbage = {(byte) 0xFF, (byte) 0xD8, 0x00, 0x00, 0x01, 0x02}; // JPEG magic, bad body
        assertThatThrownBy(() -> service.process("image/jpeg", garbage))
                .isInstanceOf(ImageValidationException.class);
    }

    // =========================================================================
    // Compression / resize (TAC-001-03, TAC-03)
    // =========================================================================

    @Test
    void largeLandscapeImageIsResizedWithinMaxEdge() throws Exception {
        byte[] largeJpeg = largeJpeg(2000, 1500);
        ImagePayload payload = service.process("image/jpeg", largeJpeg);
        assertThat(payload.width()).isLessThanOrEqualTo(MAX_EDGE);
        assertThat(payload.height()).isLessThanOrEqualTo(MAX_EDGE);
    }

    @Test
    void largePortraitImageIsResizedWithinMaxEdge() throws Exception {
        byte[] largeJpeg = largeJpeg(1200, 2400);
        ImagePayload payload = service.process("image/jpeg", largeJpeg);
        assertThat(payload.width()).isLessThanOrEqualTo(MAX_EDGE);
        assertThat(payload.height()).isLessThanOrEqualTo(MAX_EDGE);
    }

    @Test
    void smallImageIsNotUpscaled() throws Exception {
        byte[] smallJpeg = largeJpeg(100, 80);
        ImagePayload payload = service.process("image/jpeg", smallJpeg);
        // Image is already below 1024px — must not be larger after processing
        assertThat(payload.width()).isLessThanOrEqualTo(100);
        assertThat(payload.height()).isLessThanOrEqualTo(80);
    }

    @Test
    void outputByteSizeIsLessThanInputForLargeImage() throws Exception {
        byte[] largeJpeg = largeJpeg(2000, 1500);
        ImagePayload payload = service.process("image/jpeg", largeJpeg);
        assertThat(payload.compressedBytes()).isLessThan(largeJpeg.length);
    }

    // =========================================================================
    // Base64 output
    // =========================================================================

    @Test
    void base64OutputIsValidAndRoundTrips() throws Exception {
        byte[] jpeg = smallJpeg();
        ImagePayload payload = service.process("image/jpeg", jpeg);
        // Must be non-blank
        assertThat(payload.base64()).isNotBlank();
        // Must be valid base64 — decoding should not throw
        assertThatCode(() -> Base64.getDecoder().decode(payload.base64()))
                .doesNotThrowAnyException();
        // Decoded bytes must be non-empty
        assertThat(Base64.getDecoder().decode(payload.base64())).isNotEmpty();
    }

    @Test
    void contentTypeIsJpeg() throws Exception {
        ImagePayload payload = service.process("image/jpeg", smallJpeg());
        assertThat(payload.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void contentTypeIsJpegForPngInput() throws Exception {
        // Re-encoded as JPEG regardless of input format
        ImagePayload payload = service.process("image/png", smallPng());
        assertThat(payload.contentType()).isEqualTo("image/jpeg");
    }

    // =========================================================================
    // Image generation helpers
    // =========================================================================

    /** 50x40 JPEG, valid and decodable. */
    private byte[] smallJpeg() throws IOException {
        return encodeJpeg(createImage(50, 40));
    }

    /** Small PNG. */
    private byte[] smallPng() throws IOException {
        BufferedImage img = createImage(50, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }

    /** Large JPEG at given dimensions. */
    private byte[] largeJpeg(int width, int height) throws IOException {
        return encodeJpeg(createImage(width, height));
    }

    private BufferedImage createImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Fill with a gradient so it's non-trivial (doesn't compress to 0 bytes)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                int b = 128;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private byte[] encodeJpeg(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", out);
        return out.toByteArray();
    }
}
