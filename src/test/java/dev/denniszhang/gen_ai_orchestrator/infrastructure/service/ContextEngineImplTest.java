package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for {@link ContextEngineImpl}.
 */
class ContextEngineImplTest {

    @Test
    @DisplayName("getPrompt returns Prompt with messages from ChatMemory and supplied ChatOptions")
    void testGetMessages() {
        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        MessageFactory messageFactory = Mockito.mock(MessageFactory.class);
        ContextEngineImpl engine = new ContextEngineImpl(chatMemory, vectorStore, messageFactory);

        List<Message> storedMessages = List.of(
                AssistantMessage.builder().content("prev").build(),
                UserMessage.builder().text("user").build()
        );
        Mockito.when(chatMemory.get("conv-1")).thenReturn(storedMessages);

        List<Message> messages = engine.getMessages("conv-1");

        Mockito.verify(chatMemory).get("conv-1");
        assert messages != null;
        assert messages.equals(storedMessages);
    }

    @Test
    @DisplayName("appendUserMessage adds system message when conversation is new, then adds user message")
    void testAddUserMessage_NewConversation() {
        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        MessageFactory messageFactory = Mockito.mock(MessageFactory.class);
        ContextEngineImpl engine = new ContextEngineImpl(chatMemory, vectorStore, messageFactory);

        // Simulate empty history for new conversation
        Mockito.when(chatMemory.get("new-conv")).thenReturn(Collections.emptyList());

        SystemMessage sysMsg = SystemMessage.builder().text("system").build();
        UserMessage userMsg = UserMessage.builder().text("hello").build();
        Mockito.when(messageFactory.createSystem()).thenReturn(sysMsg);
        Mockito.when(messageFactory.createUser(eq("hello"), any())).thenReturn(userMsg);
        // getKnowledge returns empty list (vectorStore not used in this test)
        Mockito.when(vectorStore.similaritySearch((String) any())).thenReturn(Collections.<org.springframework.ai.document.Document>emptyList());

        engine.addUserMessage("new-conv", "hello");

        // Verify that system and user messages were added in order
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Mockito.verify(chatMemory, Mockito.times(2)).add(eq("new-conv"), captor.capture());

        List<Message> added = captor.getAllValues();
        assert added.get(0) == sysMsg;
        assert added.get(1) == userMsg;
    }

    @Test
    @DisplayName("appendMessage forwards the message to ChatMemory")
    void testAddMessage() {
        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        MessageFactory messageFactory = Mockito.mock(MessageFactory.class);
        ContextEngineImpl engine = new ContextEngineImpl(chatMemory, vectorStore, messageFactory);

        AssistantMessage msg = AssistantMessage.builder().content("reply").build();
        engine.addMessage("conv-2", msg);

        Mockito.verify(chatMemory).add("conv-2", msg);
    }

    @Test
    @DisplayName("addResource does nothing when given an empty array")
    void testAddResource_Empty() {
        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        MessageFactory messageFactory = Mockito.mock(MessageFactory.class);
        ContextEngineImpl engine = new ContextEngineImpl(chatMemory, vectorStore, messageFactory);

        engine.addResource(new Resource[0]);

        // No interaction with vectorStore should occur
        Mockito.verifyNoInteractions(vectorStore);
    }
}
