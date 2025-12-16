package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

public interface OrchestratorService {
    AssistantMessage chat(String conversationId, String message);
    Flux<AssistantMessage> stream(String conversationId, String message);
    void store(Resource[] resources);
}
