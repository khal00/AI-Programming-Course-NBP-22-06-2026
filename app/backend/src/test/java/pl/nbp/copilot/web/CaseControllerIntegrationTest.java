package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.nbp.copilot.web.dto.ChatRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BE-8.
 * TDD — written BEFORE controllers are implemented.
 * Uses mock-llm profile (no real OpenRouter calls).
 * TAC-05..14, TAC-001-01..07.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock-llm")
class CaseControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static byte[] validJpegBytes;

    @BeforeAll
    static void generateTestJpeg() throws Exception {
        // Generate a minimal valid JPEG using Java ImageIO
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", bos);
        validJpegBytes = bos.toByteArray();
    }

    // =========================================================================
    // TAC-14, TAC-001-07: /api/health returns 200 without OpenRouter
    // =========================================================================

    @Test
    void healthEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // =========================================================================
    // GET /api/metadata returns 200 with all case types and categories
    // =========================================================================

    @Test
    void metadataReturnsAllCaseTypesAndCategories() throws Exception {
        mockMvc.perform(get("/api/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseTypes").isArray())
                .andExpect(jsonPath("$.caseTypes.length()").value(2))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories.length()").value(12))
                .andExpect(jsonPath("$.caseTypes[?(@.code=='COMPLAINT')].label").value("Reklamacja"))
                .andExpect(jsonPath("$.caseTypes[?(@.code=='RETURN')].label").value("Zwrot"))
                .andExpect(jsonPath("$.categories[?(@.code=='SMARTPHONES')].label").value("Smartfony"));
    }

    // =========================================================================
    // TAC-001-01: missing image → 400 with fieldError
    // =========================================================================

    @Test
    void missingImageReturns400WithFieldError() throws Exception {
        mockMvc.perform(multipart("/api/cases")
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop Test")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    // =========================================================================
    // TAC-001-01: wrong image MIME type → 400
    // =========================================================================

    @Test
    void wrongImageMimeTypeReturns400() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile(
                "image", "test.txt", "text/plain", "not an image".getBytes());

        mockMvc.perform(multipart("/api/cases")
                        .file(badFile)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop Test")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // TAC-001-02: future purchaseDate → 400 with field error
    // =========================================================================

    @Test
    void futurePurchaseDateReturns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "clean-return.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop Test")
                        .param("purchaseDate", "2099-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='purchaseDate')]").exists());
    }

    // =========================================================================
    // TAC-001-01: missing reason for COMPLAINT → 400
    // =========================================================================

    @Test
    void missingReasonForComplaintReturns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "damaged-complaint.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop Test")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='reason')]").exists());
    }

    // =========================================================================
    // TAC-001-01: blank modelName → 400
    // =========================================================================

    @Test
    void blankModelNameReturns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "clean-return.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "   ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='modelName')]").exists());
    }

    // =========================================================================
    // TAC-05: valid multipart clean-return → 201 CaseResponse
    // =========================================================================

    @Test
    void validCleanReturnReturns201CaseResponse() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "clean-return.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.verdict").value("APPROVE"))
                .andExpect(jsonPath("$.firstMessage").isString())
                .andExpect(jsonPath("$.caseSummary.caseType").value("RETURN"))
                .andExpect(jsonPath("$.caseSummary.caseTypeLabel").value("Zwrot"))
                .andExpect(jsonPath("$.caseSummary.category").value("LAPTOPS"))
                .andExpect(jsonPath("$.caseSummary.categoryLabel").value("Laptopy"))
                .andExpect(jsonPath("$.caseSummary.modelName").value("Laptop XYZ"));
    }

    // =========================================================================
    // TAC-06: contradiction → 201 NEEDS_INFO
    // =========================================================================

    @Test
    void contradictionImageReturns201NeedsInfo() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "contradiction.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdict").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.firstMessage").isString());
    }

    // =========================================================================
    // TAC-07: unreadable image → 422 IMAGE_UNREADABLE, no verdict
    // =========================================================================

    @Test
    void unreadableImageReturns422() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "unreadable.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMAGE_UNREADABLE"))
                .andExpect(jsonPath("$.message").isString());
    }

    // =========================================================================
    // TAC-08: LLM failure → 503 LLM_UNAVAILABLE, no session opened
    // =========================================================================

    @Test
    void llmFailureReturns503() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "llm-fail.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));
    }

    // =========================================================================
    // TAC-10/11: POST .../messages → text/event-stream, ≥1 token + done
    // =========================================================================

    @Test
    void chatMessagesReturnsEventStream() throws Exception {
        // First create a session
        MockMultipartFile image = new MockMultipartFile(
                "image", "clean-return.jpg", "image/jpeg", validJpegBytes);

        MvcResult caseResult = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = caseResult.getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(responseBody).get("sessionId").asText();

        // Now send a chat message
        ChatRequest chatReq = new ChatRequest("Czy mogę zwrócić urządzenie?");
        MvcResult chatResult = mockMvc.perform(post("/api/cases/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
                .andReturn();

        String sseContent = chatResult.getResponse().getContentAsString();
        // Should have at least one token event and a done event
        assertThat(sseContent).contains("event:token");
        assertThat(sseContent).contains("event:done");
        assertThat(sseContent).contains("[DONE]");
    }

    // =========================================================================
    // TAC-12: unknown sessionId → 404
    // =========================================================================

    @Test
    void unknownSessionIdReturns404() throws Exception {
        ChatRequest chatReq = new ChatRequest("Pytanie");
        mockMvc.perform(post("/api/cases/{sessionId}/messages", "non-existent-session-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    // =========================================================================
    // TAC-001-05: empty message → 400
    // =========================================================================

    @Test
    void emptyMessageReturns400() throws Exception {
        // First create a session
        MockMultipartFile image = new MockMultipartFile(
                "image", "clean-return.jpg", "image/jpeg", validJpegBytes);

        MvcResult caseResult = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = caseResult.getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(responseBody).get("sessionId").asText();

        ChatRequest emptyMsg = new ChatRequest("   ");
        mockMvc.perform(post("/api/cases/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyMsg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // TAC-13: no error body leaks stack/prompt/payload
    // =========================================================================

    @Test
    void errorResponseNeverLeaksInternals() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "llm-fail.jpg", "image/jpeg", validJpegBytes);

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("category", "LAPTOPS")
                        .param("modelName", "Laptop XYZ")
                        .param("purchaseDate", "2025-01-15"))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at pl.");
        assertThat(body).doesNotContain("stackTrace");
        assertThat(body).doesNotContain("System message:");
        assertThat(body).doesNotContain("prompt");
    }

    // =========================================================================
    // COMPLAINT with reason → 201
    // =========================================================================

    @Test
    void validComplaintWithReasonReturns201() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "damaged-complaint.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("category", "SMARTPHONES")
                        .param("modelName", "Phone ABC")
                        .param("purchaseDate", "2025-06-01")
                        .param("reason", "Ekran przestał działać."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.verdict").value("APPROVE"))
                .andExpect(jsonPath("$.caseSummary.caseType").value("COMPLAINT"))
                .andExpect(jsonPath("$.caseSummary.caseTypeLabel").value("Reklamacja"));
    }
}
