package dev.denniszhang.gen_ai_orchestrator.infrastructure.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record MessageDTO(
        @Schema(description = "Unique ID for the chat session", example = "session-123")
        String conversationId,

        @Schema(description = "The user prompt", example = "What is the current time?")
        String message) {
}
