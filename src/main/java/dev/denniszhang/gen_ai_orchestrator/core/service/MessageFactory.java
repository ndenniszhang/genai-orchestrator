package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

public interface MessageFactory {
    SystemMessage createSystem(ToolCallback[] tools);
    SystemMessage createSystem(String message);
    UserMessage createUser(String message);
    AssistantMessage createAssistant(String message);
}
