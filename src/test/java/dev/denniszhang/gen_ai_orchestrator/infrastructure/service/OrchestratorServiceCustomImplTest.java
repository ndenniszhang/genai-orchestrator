package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.ContextEngine;
import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Unit tests for {@link OrchestratorServiceCustomImpl}.
 */
@Disabled("Temporarily skipping while refactoring")
class OrchestratorServiceCustomImplTest {

    @Test
    @DisplayName("stream() should stop after iteration limit and return fallback message")
    void testStreamIterationLimit() {
        // Mock dependencies
        AssistantMessage.ToolCall toolcall = new AssistantMessage
                .ToolCall("id", "type", "name", "arguments");
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(toolcall))
                .build();
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(generation))
                .build();
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chatResponse));

        ContextEngine contextEngine = Mockito.mock(ContextEngine.class);
        Mockito.when(contextEngine.getMessages(anyString())).thenReturn(new ArrayList<>());

        MessageFactory messageFactory = Mockito.mock(MessageFactory.class);
        AssistantMessage fallbackMessage = AssistantMessage.builder()
                .content("Can't generate answer")
                .build();
        Mockito.when(messageFactory.createAssistant(Mockito.anyString()))
                .thenReturn(fallbackMessage);

        ToolCallbackProvider toolProvider = Mockito.mock(ToolCallbackProvider.class);
        Mockito.when(toolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);


        OrchestratorServiceCustomImpl service = new OrchestratorServiceCustomImpl(
                chatModel,
                contextEngine,
                messageFactory,
                toolProvider
        );
        Flux<Message> resultFlux = service.stream("conv-1", "Start");


        // Verify that after 10 iterations the fallback message is emitted
        StepVerifier.create(resultFlux)
                    .thenConsumeWhile(msg -> msg.getMessageType() == MessageType.ASSISTANT && ((AssistantMessage) msg).hasToolCalls())
                    .expectNextMatches(msg -> "Can't generate answer".equals(msg.getText()))
                    .verifyComplete();

        // Verify that contextEngine.addUserMessage was called once
        Mockito.verify(contextEngine).addUserMessage("conv-1", "Start");
    }
}
