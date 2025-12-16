package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private MessageFactory messageFactory;

    public AssistantMessage chat(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, messageFactory.createSystem(toolProvider.getToolCallbacks()));
        }
        chatMemory.add(conversationId, messageFactory.createUser("%s\n\n%s".formatted(getKnowledge(message), message )));

        var promptWithMemory = new Prompt(chatMemory.get(conversationId), getChatOptions(true));
        var assistantMessage = chatModel.call(promptWithMemory).getResult().getOutput();
        chatMemory.add(conversationId, assistantMessage);

        return assistantMessage;
    }

    @Override
    public Flux<AssistantMessage> stream(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, messageFactory.createSystem(toolProvider.getToolCallbacks()));
        }
        chatMemory.add(conversationId, messageFactory.createUser("%s\n\n%s".formatted(getKnowledge(message), message )));

        return recursiveStreamLoop(0, conversationId, chatMemory, DefaultToolCallingManager.builder().build());
    }

    private Flux<AssistantMessage> recursiveStreamLoop(int iteration, String conversationId, ChatMemory chatMemory, ToolCallingManager toolCallingManager) {
        if(iteration == 10) {
            return Flux.just(AssistantMessage.builder()
                    .content("Can't generate answer")
                    .build());
        }

        var promptWithMemory = new Prompt(chatMemory.get(conversationId), getChatOptions(false));
        var source = chatModel.stream(promptWithMemory);

        var aggregatedResponse = source
            .collectList() // Collect all chunks
            .map(chunks -> {
                if (chunks == null || chunks.isEmpty()) {
                    return null;
                }

                var fullContent = new StringBuilder();
                List<AssistantMessage.ToolCall> accumulatedToolCalls = new ArrayList<>();
                ChatResponseMetadata lastMetadata = null;

                for (var chunk : chunks) {
                    chunk.getResult();// Append text content if present
                    String content = (String) chunk.getResult().getOutput().getMetadata().get("reasoningContent");
                    if (!content.isEmpty()) {
                        fullContent.append(content);
                    }

                    var toolCalls = chunk.getResult().getOutput().getToolCalls();
                    if (!toolCalls.isEmpty()) {
                        accumulatedToolCalls.addAll(toolCalls);
                    }

                    lastMetadata = chunk.getMetadata();
                }

                var aggregateMetaData = lastMetadata.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        ));
                aggregateMetaData.put("reasoningContent", fullContent);

                var aggregatedMessage = AssistantMessage.builder()
                    .toolCalls(accumulatedToolCalls)
                    .properties(aggregateMetaData)
                    .content("")
                    .build();

                return new ChatResponse(
                        List.of(new Generation(aggregatedMessage)),
                        lastMetadata
                );
            });

        return source
                .map(response -> response.getResult().getOutput())
                .concatWith(aggregatedResponse.flatMapMany(response -> {
                    var generations = response.getResults();
                    var message = generations.getFirst().getOutput();
                    chatMemory.add(conversationId, message);

                    if(!generations.isEmpty() && message.hasToolCalls()) {
                        var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, response);
                        var toolMessage = toolExecutionResult.conversationHistory().getLast(); // ToolResponseMessage.builder().build();
                        chatMemory.add(conversationId, toolMessage);
                        return recursiveStreamLoop(iteration + 1, conversationId, chatMemory, toolCallingManager);
                    }

                    return Flux.just(message);
                }));
    }

    private List<Document> getKnowledge(String message) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                .similarityThreshold(0.8d)
                .topK(6)
                .query(message)
                .build());
    }

    private ToolCallingChatOptions getChatOptions(boolean toolExecution) {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(toolProvider.getToolCallbacks())
                .internalToolExecutionEnabled(toolExecution)
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
