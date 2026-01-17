package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

public interface OrchestratorAgent {
    Flux<Message> stream(String conversationId, String message);
}
