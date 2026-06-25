package pl.nbp.copilot.domain;

import java.time.Instant;

/**
 * A single message in the conversation history (ADR-000 §5).
 * The full ordered list of ChatMessages is held in Session and sent
 * with every chat turn to the LLM (ADR-002).
 */
public record ChatMessage(

        /** Role of the message sender. */
        Role role,

        /** Message content — plain text or markdown (for assistant messages). */
        String content,

        /** Server-assigned creation timestamp. */
        Instant createdAt

) {

    /**
     * Conversation roles aligned with the OpenAI Chat Completions API.
     */
    public enum Role {
        SYSTEM,
        ASSISTANT,
        USER
    }
}
