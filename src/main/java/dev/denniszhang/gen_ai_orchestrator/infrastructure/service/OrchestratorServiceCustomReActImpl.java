package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Profile("custom")
public class OrchestratorServiceCustomReActImpl implements OrchestratorService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ToolCallbackProvider toolProvider;
    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private MessageFactory messageFactory;

    @Override
    public AssistantMessage chat(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, messageFactory.createSystem(toolProvider.getToolCallbacks()));
        }
        chatMemory.add(conversationId, messageFactory.createUser("%s\n\n%s".formatted(getKnowledge(message), message )));

        var promptWithHistory = new Prompt(chatMemory.get(conversationId), getChatOptions(true));
        var assistantMessage = chatModel.call(promptWithHistory).getResult().getOutput();
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

        var promptWithMemory = new Prompt(getHistory(conversationId), getChatOptions(false));
        var source = chatModel.stream(promptWithMemory).share();

        var aggregatedResponse = source
            .collectList() // Collect all chunks
            .map(this::aggregateChunks);
        var messages = source
            .map(ChatResponse::getResult)
            .map(Generation::getOutput);

        return messages
                .concatWith(aggregatedResponse
                    .flatMapMany(response -> {
                        var generations = response.getResults();
                        var message = generations.getFirst().getOutput();

                        if(message.hasToolCalls()) {
                            var idx = chatMemory.get(conversationId).size();
                            vectorStore.add(toDocuments(List.of(message), conversationId, idx));

                            var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, response);
                            var toolMessage = toolExecutionResult.conversationHistory().getLast(); // ToolResponseMessage.builder().build();
                            vectorStore.add(toDocuments(List.of(toolMessage), conversationId, idx + 1));

                            return recursiveStreamLoop(iteration + 1, conversationId, chatMemory, toolCallingManager);
                        }else {
                            chatMemory.add(conversationId, message);
                            return Mono.just(message);
                        }
                    }));
    }

    private List<Document> toDocuments(List<Message> messages, String conversationId, int index) {
        return messages.stream()
                .filter(m -> m.getMessageType() == MessageType.ASSISTANT || m.getMessageType() == MessageType.TOOL)
                .map(message -> {
                    Map<String, Object> metadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
                    metadata.put("index", index);
                    metadata.put("conversationId", conversationId);
//                    metadata.put("messageType", message.getMessageType().name());
                    if (message instanceof AssistantMessage assistantMessage) {
                        metadata.put("toolCalls",  assistantMessage.getToolCalls());

                        return Document.builder()
                                .text(assistantMessage.getText())
                                .metadata(metadata)
                                .build();
                    } else if (message instanceof ToolResponseMessage toolMessage) {
                        metadata.put("toolResponses", toolMessage.getResponses());

                        return Document.builder()
                                .text(toolMessage.getText())
                                .metadata(metadata)
                                .build();
                    }
                    throw new RuntimeException("Unknown message type: " + message.getMessageType());
                })
                .toList();
    }

    private ToolCallingChatOptions getChatOptions(boolean toolExecution) {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(toolProvider.getToolCallbacks())
                .internalToolExecutionEnabled(toolExecution)
                .build();
    }

    private ChatResponse aggregateChunks(List<ChatResponse> chunks) {
            if (chunks == null || chunks.isEmpty()) {
                return null;
            }

            var fullContent = new StringBuilder();
            var fullReasoning = new StringBuilder();
            List<AssistantMessage.ToolCall> accumulatedToolCalls = new ArrayList<>();
            ChatResponseMetadata lastMetadata = null;

            for (var chunk : chunks) {
                String content = chunk.getResult().getOutput().getText();
                if (content != null) {
                    fullContent.append(content);
                }

                String reasoning = (String) chunk.getResult().getOutput().getMetadata().get("reasoningContent");
                if (!reasoning.isEmpty()) {
                    fullReasoning.append(reasoning);
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
            aggregateMetaData.put("reasoningContent", fullReasoning.toString());

            var aggregatedMessage = AssistantMessage.builder()
                    .toolCalls(accumulatedToolCalls)
                    .properties(aggregateMetaData)
                    .content(fullContent.toString())
                    .build();

            return new ChatResponse(
                    List.of(new Generation(aggregatedMessage)),
                    lastMetadata
            );
    }

    @Autowired
    private VectorStore vectorStore;

    private List<Document> getKnowledge(String message) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                .similarityThreshold(0.8d)
                .topK(5)
                .query(message)
                .build());
    }

    private List<Document> getToolCalls(String conversationId) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .filterExpression("conversationId == '" + conversationId + "'") // Only this chat's data
                .similarityThreshold(0.7d) // Lower threshold to catch exact data matches
                .build());
    }

    private List<Message> getHistory(String conversationId) {
        var memory = chatMemory.get(conversationId);
        Integer idx = null;

        for (var toolCall : getToolCalls(conversationId)) {
            var metadata = toolCall.getMetadata();
            idx = (Integer) metadata.get("index");
            metadata.remove("index");
            metadata.remove("conversationId");
            Message message = null;

            if (metadata.get("messageType").equals("ASSISTANT")) {
                var toolCalls = ((List<LinkedHashMap<String, String>>) metadata.get("toolCalls")).stream()
                        .map(map -> new AssistantMessage.ToolCall(
                                map.get("id"),
                                map.get("type"),
                                map.get("name"),
                                map.get("arguments")
                        ))
                        .toList();
                metadata.remove("toolCalls");

                message = AssistantMessage.builder()
                        .content(toolCall.getText())
                        .toolCalls(toolCalls)
                        .properties(metadata)
                        .build();
            }
            if (metadata.get("messageType").equals("TOOL")) {
                var toolResponses = ((List<LinkedHashMap<String, String>>) metadata.get("toolResponses")).stream()
                        .map(map -> new ToolResponseMessage.ToolResponse(
                                map.get("id"),
                                map.get("type"),
                                map.get("responseData")
                        ))
                        .toList();
                metadata.remove("toolResponses");

                message = ToolResponseMessage.builder()
                        .responses(toolResponses)
                        .metadata(metadata)
                        .build();
            }

            memory.add(idx, message);
        }

        return memory;
    }

    @Autowired
    private TokenTextSplitter textSplitter;

    public void store(Resource[] resources) {
        if(resources.length != 0) {
            Arrays.stream(resources)
            .map(resource -> textSplitter.apply(new TikaDocumentReader(resource).get()))
            .forEach(docs -> vectorStore.add(docs));
        }
    }
}
