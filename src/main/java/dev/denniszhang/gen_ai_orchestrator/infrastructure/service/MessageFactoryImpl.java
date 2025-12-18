package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

@Service
@Profile("custom")
public class MessageFactoryImpl implements MessageFactory {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final STGroup templateGroup = new STGroupFile("templates.stg", '$', '$');

    @Override
    public SystemMessage createSystem() {
        return SystemMessage.builder()
                .text(templateGroup
                    .getInstanceOf("systemPrompt")
                    .render())
                .build();
    }

    @Override
    public SystemMessage createSystem(String message) {
        return SystemMessage.builder().text(message).build();
    }

    @Override
    public UserMessage createUser(String message) {
        return UserMessage.builder().text(message).build();
    }

    @Override
    public AssistantMessage createAssistant(String message) {
        return AssistantMessage.builder().content(message).build();
    }
}