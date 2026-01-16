package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STRawGroupDir;

import java.util.ArrayList;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceCustomImplTest {

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
    private OrchestratorServiceCustomImpl orchestratorService;

    @BeforeEach
    void setUp() {
        // Setup default behavior for memory to avoid NullPointerExceptions
        // When the code asks "what happened before?", we say "nothing yet"
        when(chatMemory.get(anyString())).thenReturn(new ArrayList<>());
        when(toolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
    }
}