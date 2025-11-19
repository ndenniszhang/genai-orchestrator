package dev.denniszhang.gen_ai_orchestrator.orchestrator;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("custom")
public class OrchestratorCustomImpl implements Orchestrator{

    private final ChatClient chatClient;

    public OrchestratorCustomImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String goal(String message) {
        return this.chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
