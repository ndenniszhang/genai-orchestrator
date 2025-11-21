package dev.denniszhang.gen_ai_orchestrator.orchestrator;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("custom")
public class OrchestratorCustomImpl implements Orchestrator{

    private final ChatClient chatClient;

    public OrchestratorCustomImpl(
            ChatClient.Builder builder,
            ToolCallbackProvider tools,
            ChatMemory memory
    ) {
        this.chatClient = builder
                .defaultToolCallbacks(tools.getToolCallbacks())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public String goal(String message) {
        return this.chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "1"))
                .call()
                .content();
    }
}
