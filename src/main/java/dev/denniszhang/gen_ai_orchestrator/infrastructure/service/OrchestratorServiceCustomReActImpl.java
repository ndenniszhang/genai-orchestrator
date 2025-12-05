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
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("custom")
public class OrchestratorServiceCustomReActImpl implements OrchestratorService {

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ToolCallbackProvider toolProvider;
    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ChatMemory chatMemory;

    @Value("classpath:/templates/Prompt-Template.st")
    private Resource PROMPT_TEMPLATE;
    @Value("classpath:/templates/System-Template.st")
    private Resource SYSTEM_TEMPLATE;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public String goal(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, new Prompt(getSystemMessage(), getChatOptions()).getInstructions());
        }

        int iteration = 0;
        ChatResponse chatResponse = null;
        var template = new PromptTemplate(PROMPT_TEMPLATE);
        var toolCallingManager = DefaultToolCallingManager.builder().build();
        do{
            var map = new HashMap<String, Object>();
            map.put("MEMORY", chatMemory.get(conversationId));
            map.put("KNOWLEDGE", getKnowledge(message));
            map.put("MESSAGE", message);
            var promptWithMemory = template.create(map, getChatOptions());

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
            .map(ToolCallback::getToolDefinition)
            .collect(Collectors
            .toMap(d -> d.name(), d -> d.toString()));

        return new SystemMessage(SYSTEM_TEMPLATE.toString().formatted(toolDefinitions));
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
