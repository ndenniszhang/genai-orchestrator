package dev.denniszhang.gen_ai_orchestrator.infrastructure.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
                800,  // defaultChunkSize: Target ~800 tokens per chunk
                350,  // minChunkSizeChars: Avoid creating tiny, useless chunks
                5,    // minChunkLengthToEmbed: Discard artifacts/noise
                10000, // maxNumChunks: Safety limit
                true   // keepSeparator: Preserve sentence boundaries
        );

    }
}
