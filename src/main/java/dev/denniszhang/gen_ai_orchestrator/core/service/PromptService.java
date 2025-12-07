package dev.denniszhang.gen_ai_orchestrator.core.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface PromptService {
    String map(List<Message> messages);
    String getSystemPrompt(ToolCallback[] tools);
}
