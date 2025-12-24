package dev.denniszhang.gen_ai_orchestrator.infrastructure.model;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        LocalDateTime timestamp,
        int status
) {}