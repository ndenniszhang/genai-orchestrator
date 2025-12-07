package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.denniszhang.gen_ai_orchestrator.core.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Profile("custom")
public class PromptServiceLlamaImpl implements PromptService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final STGroup templateGroup = new STGroupFile("templates.stg", '$', '$');

    @Override
    public String map(List<Message> messages) {
        var prompt = new StringBuilder();
        for(var message : messages) {
            var part = templateGroup
                    .getInstanceOf("%sMessage".formatted(message.getMessageType().name().toLowerCase()))
                    .add("message", message.getText())
                    .render();
            prompt.append(part);
        }
        prompt.append("<|start_header_id|>assistant<|end_header_id|>");

        return prompt.toString();
    }

    @Override
    public String getSystemPrompt(ToolCallback[] tools) {
        var toolDefinitions = Arrays.stream(tools)
                .map(tool -> {
                    var def = tool.getToolDefinition();
                    return Map.of(
                            "name", def.name(),
                            "description", def.description(),
                            "schema", def.inputSchema()).toString();
                }).toArray();

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