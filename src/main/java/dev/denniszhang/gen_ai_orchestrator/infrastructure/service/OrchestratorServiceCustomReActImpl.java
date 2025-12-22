package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Profile("custom")
public class OrchestratorServiceCustomReActImpl implements OrchestratorService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ToolCallbackProvider toolProvider;
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final MessageFactory messageFactory;
    private final VectorStore vectorStore;

    public OrchestratorServiceCustomReActImpl(
            ChatModel chatModel,
            ChatMemory chatMemory,
            ToolCallbackProvider toolProvider,
            MessageFactory messageFactory,
            VectorStore vectorStore
    ) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.toolProvider = toolProvider;
        this.messageFactory = messageFactory;
    }

    @Override
    public AssistantMessage chat(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, messageFactory.createSystem());
        }
        chatMemory.add(conversationId, messageFactory.createUser(message, getKnowledge(message)));

        var promptWithHistory = new Prompt(chatMemory.get(conversationId), getChatOptions(true));
        var assistantMessage = chatModel.call(promptWithHistory).getResult().getOutput();
        chatMemory.add(conversationId, assistantMessage);

        return assistantMessage;
    }

    @Override
    public Flux<Message> stream(String conversationId, String message) {
        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, messageFactory.createSystem());
        }
        chatMemory.add(conversationId, messageFactory.createUser(message, getKnowledge(message)));

        return recursiveStreamLoop(0, conversationId, chatMemory, DefaultToolCallingManager.builder().build());
    }

    private Flux<Message> recursiveStreamLoop(int iteration, String conversationId, ChatMemory chatMemory, ToolCallingManager toolCallingManager) {
        if(iteration == 10) {
            final String answer = "Can't generate answer";
            chatMemory.add(conversationId, AssistantMessage.builder()
                                            .content(answer)
                                            .build());
            return Flux.just(AssistantMessage.builder()
                    .content(answer)
                    .build());
        }

        Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), getChatOptions(false));
        Flux<ChatResponse> source = chatModel.stream(promptWithMemory).share();

        Mono <ChatResponse> aggregatedResponse = source.collectList()
                                                        .filter(list -> !list.isEmpty())
                                                        .map(this::aggregateChunks);
        Flux<Message> messages = source.map(response -> response.getResults().getFirst().getOutput());

        return messages
                .concatWith(aggregatedResponse
                    .flatMapMany(response -> {
                        var generations = response.getResults();
                        var message = generations.getFirst().getOutput();
                        chatMemory.add(conversationId, message);

                        if(message.hasToolCalls()) {
                            var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, response);
                            var toolMessage = toolExecutionResult.conversationHistory().getLast(); // ToolResponseMessage.builder().build();
                            chatMemory.add(conversationId, toolMessage);

                            return Flux.just(toolMessage)
                                    .doOnNext(m -> {
                                        m.getMetadata().remove("conversationId");
                                        m.getMetadata().remove("index");
                                    })
                                    .concatWith(recursiveStreamLoop(iteration + 1, conversationId, chatMemory, toolCallingManager));
                        }

                        return Mono.just(message);
                    }));
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

            if(!accumulatedToolCalls.isEmpty() && fullReasoning.isEmpty()) {
                aggregateMetaData.put("reasoningContent", fullContent.toString());
                fullContent.setLength(0);
            }

            var aggregatedMessage = AssistantMessage.builder()
                    .toolCalls(accumulatedToolCalls)
                    .properties(aggregateMetaData)
                    .content(fullContent.toString())
                    .build();

            return ChatResponse.builder()
                    .generations(List.of(new Generation (aggregatedMessage)))
                    .metadata(lastMetadata)
                    .build();
    }

    private List<Document> getKnowledge(String message) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                .similarityThreshold(0.8d)
                .topK(5)
                .query(message)
                .build());
    }

    public void store(Resource[] resources) {
        if (resources.length != 0) {
            Arrays.stream(resources)
                    .map(resource -> new TokenTextSplitter(
                            800,  // defaultChunkSize: Target ~800 tokens per chunk
                            350,  // minChunkSizeChars: Avoid creating tiny, useless chunks
                            5,    // minChunkLengthToEmbed: Discard artifacts/noise
                            10000, // maxNumChunks: Safety limit
                            true   // keepSeparator: Preserve sentence boundaries
                    ).apply(new TikaDocumentReader(resource).get()))
                    .forEach(docs -> vectorStore.add(docs));
        }
    }
}
