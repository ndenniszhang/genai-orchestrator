package dev.denniszhang.gen_ai_orchestrator.orchestrator;

import org.springframework.ai.document.Document;

import java.util.List;

public interface Orchestrator {
    String goal(String conversationId, String message);
    Orchestrator store(List<Document> documents);
}
