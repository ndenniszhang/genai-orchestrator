package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.document.Document;

import java.util.List;

public interface OrchestratorService {
    String goal(String conversationId, String message);
    OrchestratorService store(List<Document> documents);
}
