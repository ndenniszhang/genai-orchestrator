package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.ContextEngine;
import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Profile("custom")
public class OrchestratorAgentImpl implements OrchestratorAgent {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final MessageFactory messageFactory;
    private final ToolCallbackProvider toolProvider;
    private final ChatModel chatModel;
    private final ContextEngine contextEngine;

    public OrchestratorAgentImpl(
            ChatModel chatModel,
            ContextEngine contextEngine,
            MessageFactory messageFactory,
            ToolCallbackProvider toolProvider
    ) {
        this.chatModel = chatModel;
        this.toolProvider = toolProvider;
        this.contextEngine = contextEngine;
        this.messageFactory = messageFactory;
    }

    @Override
    public Flux<Message> stream(String conversationId, String message) {
        contextEngine.addUserMessage(conversationId, message);
        return recursiveStreamLoop(0, conversationId, contextEngine, DefaultToolCallingManager.builder().build());
    }

    private Flux<Message> recursiveStreamLoop(int iteration, String conversationId, ContextEngine contextEngine, ToolCallingManager toolCallingManager) {
        if(iteration == 10) {
            var assistantMessage = messageFactory.createAssistant("Can't generate answer");
            contextEngine.addMessage(conversationId, assistantMessage);
            return Flux.just(assistantMessage);
        }

        Prompt promptWithMemory = getPrompt(conversationId, getChatOptions(false));
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
                        contextEngine.addMessage(conversationId, message);

                        if(message.hasToolCalls()) {
                            var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, response);
                            var toolMessage = toolExecutionResult.conversationHistory().getLast(); // ToolResponseMessage.builder().build();
                            contextEngine.addMessage(conversationId, toolMessage);

                            return Flux.just(toolMessage)
                                    .doOnNext(m -> {
                                        m.getMetadata().remove("conversationId");
                                        m.getMetadata().remove("index");
                                    })
                                    .concatWith(recursiveStreamLoop(iteration + 1, conversationId, contextEngine, toolCallingManager));
                        }

                        return Mono.just(message);
                    }));
    }

    private Prompt getPrompt(String conversationID, ChatOptions chatOptions){
        return  Prompt.builder()
                .messages(contextEngine.getMessages(conversationID))
                .chatOptions(chatOptions)
                .build();
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
}
