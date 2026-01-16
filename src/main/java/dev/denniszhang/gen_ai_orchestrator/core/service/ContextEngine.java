package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.Resource;

public interface ContextEngine {
    Prompt getPrompt(String conversationID, ChatOptions chatOptions);
    void appendUserMessage(String conversationId, String message);
    void appendMessage(String conversationId, Message message);
    void addResource(Resource[] resources);
}
