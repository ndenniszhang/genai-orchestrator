package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.core.io.Resource;

public interface OrchestratorService {
    String goal(String conversationId, String message);
    void store(Resource[] resources);
}
