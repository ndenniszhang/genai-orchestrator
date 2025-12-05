package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.STGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Profile("custom")
public class OrchestratorServiceCustomReActImpl implements OrchestratorService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ToolCallbackProvider toolProvider;
    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private STGroup stGroup;

    public String goal(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, new Prompt(getSystemMessage(), getChatOptions()).getInstructions());
        }

        int iteration = 0;
        ChatResponse chatResponse = null;
        var toolCallingManager = DefaultToolCallingManager.builder().build();
        do{
            var template = stGroup.getInstanceOf("Prompt-Template");
            template.add("MEMORY", chatMemory.get(conversationId));
            template.add("KNOWLEDGE", getKnowledge(message));
            template.add("MESSAGE", message);
            var promptWithMemory = new Prompt(template.render());

            chatMemory.add(conversationId, new Prompt(new UserMessage(message), getChatOptions()).getInstructions());
            chatResponse = chatModel.call(promptWithMemory);
            chatMemory.add(conversationId, chatResponse.getResult().getOutput());

            if(chatResponse.hasToolCalls()) {
                var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, chatResponse);
                chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
                        .get(toolExecutionResult.conversationHistory().size() - 1));
            }

            ++iteration;
        }while(iteration <= 10 && chatResponse.hasToolCalls());

        return chatResponse.getResult().getOutput().getText();
    }

    private SystemMessage getSystemMessage() {
        var toolDefinitions =  Arrays.stream(toolProvider.getToolCallbacks())
                .map(tool -> {
                    var def = tool.getToolDefinition();
                    return Map.of(
                            "name", def.name(),
                            "description", def.description(),
                            "schema", def.inputSchema()).toString();
                }).toArray();

        var template = stGroup.getInstanceOf("System-Template");
        template.add("TOOLS", toolDefinitions);
        return new SystemMessage(template.render());
    }

    private List<Document> getKnowledge(String message) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(message)
                .similarityThreshold(0.8d)
                .topK(6)
                .build());
    }

    private ToolCallingChatOptions getChatOptions() {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(toolProvider.getToolCallbacks())
                .internalToolExecutionEnabled(false)
                .build();
    }

    public OrchestratorService store(List<Document> documents) {
        if(!documents.isEmpty())
            this.vectorStore.add(documents);
        return this;
    }
}
