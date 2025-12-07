package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import dev.denniszhang.gen_ai_orchestrator.core.service.PromptService;
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
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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
    private PromptService prompt;

    public String goal(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, new SystemMessage(
                    prompt.getSystemPrompt(toolProvider.getToolCallbacks())));
        }
        chatMemory.add(conversationId, new UserMessage("%s\n\n%s".formatted(getKnowledge(message), message )));

        int iteration = 0;
        ChatResponse chatResponse = null;
        var toolCallingManager = DefaultToolCallingManager.builder().build();
        do{
            var promptWithMemory = new Prompt(prompt.map(chatMemory.get(conversationId)), getChatOptions());
            chatResponse = chatModel.call(promptWithMemory);
            chatMemory.add(conversationId, chatResponse.getResult().getOutput());

            if(chatResponse.hasToolCalls()) {
                var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, chatResponse);
                chatMemory.add(conversationId,
                        toolExecutionResult.conversationHistory()
                        .get(toolExecutionResult.conversationHistory().size() - 1));
            }

            ++iteration;
        }while(iteration <= 10 && chatResponse.hasToolCalls());

        return chatResponse.getResult().getOutput().getText();
    }

    private List<Document> getKnowledge(String message) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .similarityThreshold(0.8d)
                .topK(6)
                .query(message)
                .build());
    }

    private ToolCallingChatOptions getChatOptions() {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(toolProvider.getToolCallbacks())
                .internalToolExecutionEnabled(false)
                .temperature(0.0)
                .build();
    }

    @Autowired
    private TokenTextSplitter textSplitter;

    public void store(Resource[] resources) {
        if(resources.length != 0) {
            Arrays.stream(resources)
            .map(r -> textSplitter.apply(new TikaDocumentReader(r).get()))
            .forEach(l -> vectorStore.accept(l));
        }
    }
}
