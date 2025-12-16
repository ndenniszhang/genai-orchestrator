package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.Arrays;
import java.util.Map;

@Service
@Profile("custom")
public class MessageFactoryImpl implements MessageFactory {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public SystemMessage createSystem(ToolCallback[] tools) {
        return new SystemMessage(getSystemPrompt(tools));
    }

    @Override
    public SystemMessage createSystem(String message) {
        return new SystemMessage(message);
    }

    @Override
    public UserMessage createUser(String message) {
        return new UserMessage(message);
    }

    @Override
    public AssistantMessage createAssistant(String message) {
        return new AssistantMessage(message);
    }


    private final STGroup templateGroup = new STGroupFile("templates.stg", '$', '$');

    private String getSystemPrompt(ToolCallback[] tools) {
        var toolDefinitions = Arrays.stream(tools)
                .map(tool -> {
                    var def = tool.getToolDefinition();
                    return Map.of(
                            "name", def.name(),
                            "description", def.description(),
                            "schema", def.inputSchema()).toString();
                }).toArray();

        SystemPromptTemplate.builder().template(templateGroup
                .getInstanceOf("systemPrompt")
                .add("tools", toolDefinitions).render());

        String pretty = null;
        try {
            pretty = new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toolDefinitions);
        } catch (JsonProcessingException e) {
            logger.error("JSON Pretty print failed!", e);
        }

        return templateGroup
                .getInstanceOf("systemPrompt")
                .add("tools", pretty)
                .render();
    }
}