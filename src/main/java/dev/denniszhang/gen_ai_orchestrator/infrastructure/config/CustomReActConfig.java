package dev.denniszhang.gen_ai_orchestrator.infrastructure.config;

import dev.denniszhang.gen_ai_orchestrator.core.service.ContextEngine;
import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import dev.denniszhang.gen_ai_orchestrator.infrastructure.service.ContextEngineImpl;
import dev.denniszhang.gen_ai_orchestrator.infrastructure.service.ToolMessageWindowChatMemoryImpl;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("custom")
public class CustomReActConfig {
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository, VectorStore vectorStore) {
        return ToolMessageWindowChatMemoryImpl.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .vectorStore(vectorStore)
                .build();
    }

    @Bean
    public ContextEngine contextEngine(ChatMemory chatMemory, VectorStore vectorStore, MessageFactory messageFactory) {
        return new ContextEngineImpl(chatMemory, vectorStore, messageFactory);
    }
}
