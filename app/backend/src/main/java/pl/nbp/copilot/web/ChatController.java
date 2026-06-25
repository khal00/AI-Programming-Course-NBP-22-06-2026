package pl.nbp.copilot.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.orchestration.CaseOrchestrationService;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionStore;
import pl.nbp.copilot.web.dto.ChatRequest;

/**
 * POST /api/cases/{sessionId}/messages — streams the assistant's reply as SSE.
 *
 * <p>Uses Java 21 virtual threads to keep the blocking LLM relay off the servlet
 * thread pool. Emits {@code token} events per chunk, a terminal {@code done} event,
 * or an {@code error} event on failure (conversation state preserved on error).
 *
 * <p>ADR-001 §3/§6 (POST + text/event-stream, consumed via fetch+ReadableStream),
 * TAC-001-04, TAC-10, TAC-11.
 */
@RestController
@RequestMapping("/api/cases")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final long SSE_TIMEOUT_MS = 120_000L; // 2 minutes

    private final CaseOrchestrationService orchestration;
    private final SessionStore sessionStore;

    public ChatController(CaseOrchestrationService orchestration, SessionStore sessionStore) {
        this.orchestration = orchestration;
        this.sessionStore = sessionStore;
    }

    @PostMapping(value = "/{sessionId}/messages",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReply(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatRequest request,
            HttpServletResponse response) {

        // DEF-001: disable reverse-proxy buffering (Nginx X-Accel-Buffering, Vite/Angular dev proxy).
        // Cache-Control: no-cache is also required by the SSE spec so intermediaries don't buffer.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        // Synchronous session check: throw 404 before spawning the virtual thread.
        // This ensures SessionNotFoundException propagates to GlobalExceptionHandler as HTTP 404.
        if (sessionStore.get(sessionId).isEmpty()) {
            throw new SessionNotFoundException(sessionId);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Thread.ofVirtual().start(() -> {
            try {
                orchestration.streamReply(sessionId, request.message(), token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (Exception e) {
                        log.debug("SSE send error for token: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (SessionNotFoundException e) {
                // This shouldn't normally be reached here (caught pre-thread), but handle defensively
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("Sesja nie istnieje lub wygasła. Rozpocznij nową sprawę."));
                } catch (Exception ignored) {}
                emitter.complete();
            } catch (Exception e) {
                log.error("Error during SSE chat stream for session {}: {}", sessionId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("Wystąpił błąd podczas generowania odpowiedzi. Spróbuj ponownie."));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }
}
