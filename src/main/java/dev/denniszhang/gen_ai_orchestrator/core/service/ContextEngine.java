package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.Resource;

import java.util.List;

public interface ContextEngine {
    List<Message> getMessages(String conversationID);
    void addUserMessage(String conversationId, String message);
    void addMessage(String conversationId, Message message);
    void addResource(Resource[] resources);
}
