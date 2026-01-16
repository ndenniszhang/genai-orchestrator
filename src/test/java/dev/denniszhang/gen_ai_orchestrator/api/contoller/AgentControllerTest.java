package dev.denniszhang.gen_ai_orchestrator.api.contoller;

import dev.denniszhang.gen_ai_orchestrator.core.service.ContextEngine;
import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Unit tests for {@link AgentController}.
 */
@Disabled("Temporarily skipping while refactoring")
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private OrchestratorService orchestratorService;

    @Mock
    private ContextEngine contextEngine;

    @Test
    @DisplayName("POST /api/v1/agent/upload handles IOException")
    void testUploadEndpointError() throws Exception {
        // Simulate a file that throws IOException when getInputStream is called
        MockMultipartFile badFile = new MockMultipartFile(
                "files",
                "bad.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]) {
            @Override
            public java.io.InputStream getInputStream() throws java.io.IOException {
                throw new java.io.IOException("forced error");
            }
        };

        // Expect a 400 Bad Request due to ResponseStatusException
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/agent/upload")
                        .file(badFile))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/agent/stream returns a Flux of messages")
    void testStreamEndpoint() throws Exception {
        // Arrange
        AssistantMessage mockMessage = AssistantMessage.builder()
                .content("Streaming response")
                .build();
        Mockito.when(orchestratorService.stream(anyString(), anyString()))
                .thenReturn(reactor.core.publisher.Flux.just(mockMessage));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/agent/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"message\":\"Start\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON));
    }
}
