package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import dev.denniszhang.gen_ai_orchestrator.core.service.ContextEngine;
import dev.denniszhang.gen_ai_orchestrator.core.service.MessageFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.List;

public class ContextEngineImpl implements ContextEngine {

    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final MessageFactory messageFactory;

    public ContextEngineImpl(
            ChatMemory chatMemory,
            VectorStore vectorStore,
            MessageFactory messageFactory
    ) {
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.messageFactory = messageFactory;
    }

    @Override
    public List<Message> getMessages(String conversationID) {
        return chatMemory.get(conversationID);
    }

    @Override
    public void addUserMessage(String conversationId, String message) {
        final String AgentName = "orchestratorAgent";

        if(chatMemory.get(conversationId).isEmpty()) {
            chatMemory.add(conversationId, messageFactory.createSystem(AgentName));
        }
        chatMemory.add(conversationId, messageFactory.createUser(message, getKnowledge(message)));
    }

    @Override
    public void addMessage(String conversationId, Message message) {
        chatMemory.add(conversationId, message);
    }

    @Override
    public void addResource(Resource[] resources) {
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

    private List<Document> getKnowledge(String message) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .similarityThreshold(0.8d)
                        .topK(5)
                        .query(message)
                        .build());
    }
}
