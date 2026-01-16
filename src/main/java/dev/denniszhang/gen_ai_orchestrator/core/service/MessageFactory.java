package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;

import java.util.List;

public interface MessageFactory {
    SystemMessage createSystem();
    UserMessage createUser(String message, List<Document> documents);
    AssistantMessage createAssistant(String message);
}
