package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

public interface MessageFactory {
    SystemMessage createSystem();
    SystemMessage createSystem(String message);
    UserMessage createUser(String message);
    AssistantMessage createAssistant(String message);
}
