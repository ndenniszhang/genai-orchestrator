package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STRawGroupDir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceCustomReActImplTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private ToolCallbackProvider toolProvider;
    @Mock
    private ChatModel chatModel;
    @Mock
    private ChatMemory chatMemory;

    // We use a real STGroup because mocking StringTemplate is painful and unnecessary
    @Spy
    private STGroup stGroup = new STRawGroupDir("templates");

    @InjectMocks
    private OrchestratorServiceCustomReActImpl orchestratorService;

    @BeforeEach
    void setUp() {
        // Setup default behavior for memory to avoid NullPointerExceptions
        // When the code asks "what happened before?", we say "nothing yet"
        when(chatMemory.get(anyString())).thenReturn(new ArrayList<>());
        when(toolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
    }

    @Test
    void goal_ShouldReturnFinalAnswer_WhenLLMProvidesOneDirectly() {
        // --- ARRANGE ---
        String conversationId = "test-conv-1";
        String userMessage = "Hello, who are you?";
        String expectedAnswer = "I am a custom ReAct Agent.";

        // 1. Mock the Knowledge Retrieval (VectorStore)
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        // 2. Mock the LLM Response (The "Brain")
        // We simulate the LLM immediately returning a "Final Answer" without needing tools
        String llmOutputRaw = """
            Thought: The user is greeting me. I don't need tools for this.
            Final Answer: %s
            """.formatted(expectedAnswer);

        ChatResponse mockResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(llmOutputRaw))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // --- ACT ---
        Message result = orchestratorService.chat(conversationId, userMessage);

        // --- ASSERT ---
        // 1. Verify the result matches what we extracted from the LLM
        assertThat(result.getText()).contains(expectedAnswer);

        // 2. Verify the loop ran exactly once (since we gave a Final Answer)
        verify(chatModel, times(1)).call(any(Prompt.class));

        // 3. Verify state was saved to memory
        verify(chatMemory, atLeastOnce()).add(eq(conversationId), any(Message.class));
    }
}