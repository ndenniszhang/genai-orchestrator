package dev.denniszhang.gen_ai_orchestrator.infrastructure.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STRawGroupDir;

@Configuration
@Profile("custom")
public class CustomReActConfig {
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .build();
    }

    @Bean
    public STGroup stGroup() {
        return new STRawGroupDir("templates");
    }
}
