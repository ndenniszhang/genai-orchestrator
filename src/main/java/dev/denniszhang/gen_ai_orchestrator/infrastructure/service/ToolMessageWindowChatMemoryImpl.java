package dev.denniszhang.gen_ai_orchestrator.infrastructure.service;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.Assert;

import java.util.*;

public class ToolMessageWindowChatMemoryImpl implements ChatMemory {
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final ChatMemoryRepository chatMemoryRepository;

    private final VectorStore vectorStore;

    private final int maxMessages;

    private ToolMessageWindowChatMemoryImpl(ChatMemoryRepository chatMemoryRepository, VectorStore vectorStore, int maxMessages) {
        Assert.notNull(vectorStore, "vectorStore cannot be null");
        Assert.notNull(chatMemoryRepository, "chatMemoryRepository cannot be null");
        Assert.isTrue(maxMessages > 0, "maxMessages must be greater than 0");

        this.chatMemoryRepository = chatMemoryRepository;
        this.vectorStore = vectorStore;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        List<Message> memoryMessages = getAll(conversationId);
        List<Message> processedMessages = process(memoryMessages, messages);
        saveAll(conversationId, processedMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return getAll(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        throw new NotImplementedException();
    }

    private List<Message> process(List<Message> memoryMessages, List<Message> newMessages) {
        List<Message> processedMessages = new ArrayList<>();

        Set<Message> memoryMessagesSet = new HashSet<>(memoryMessages);
        boolean hasNewSystemMessage = newMessages.stream()
                .filter(SystemMessage.class::isInstance)
                .anyMatch(message -> !memoryMessagesSet.contains(message));

        memoryMessages.stream()
                .filter(message -> !(hasNewSystemMessage && message instanceof SystemMessage))
                .forEach(processedMessages::add);

        processedMessages.addAll(newMessages);

        if (processedMessages.size() <= this.maxMessages) {
            return processedMessages;
        }

        int messagesToRemove = processedMessages.size() - this.maxMessages;

        List<Message> trimmedMessages = new ArrayList<>();
        int removed = 0;
        for (Message message : processedMessages) {
            if (message instanceof SystemMessage || removed >= messagesToRemove) {
                trimmedMessages.add(message);
            }
            else {
                removed++;
            }
        }

        return trimmedMessages;
    }

    private List<Document> getDocs(String conversationId) {
        return this.vectorStore.similaritySearch(SearchRequest.builder()
                    .filterExpression("conversationId == '" + conversationId + "'") // Only this chat's data
                    .similarityThreshold(0.7d) // Lower threshold to catch exact data matches
                    .build());
    }

    private List<Message> getAll(String conversationId) {
        var memory = this.chatMemoryRepository.findByConversationId(conversationId);
        var documents = getDocs(conversationId);

        Integer idx = null;
        for (var document : documents) {
            var metadata = document.getMetadata();
            idx = (Integer) metadata.get("index");

            metadata.remove("index");
            metadata.remove("conversationId");
            metadata.put("id", document.getId());

            Message message = null;
            if (metadata.get("messageType").equals("ASSISTANT")) {
                var toolCalls = ((List<LinkedHashMap<String, String>>) metadata.get("toolCalls")).stream()
                        .map(map ->
                                new AssistantMessage.ToolCall(
                                                                map.get("id"),
                                                                map.get("type"),
                                                                map.get("name"),
                                                                map.get("arguments")
                                                        ))
                        .toList();
                metadata.remove("toolCalls");

                message = AssistantMessage.builder()
                        .content(document.getText())
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

    private void saveAll(String conversationId, List<Message> messages) {
        final String ID = "id";
        List<Message> chatMessages = new ArrayList<>();
        List<Document> toolMessages = new ArrayList<>();

        for(int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            Map<String, Object> metadata = message.getMetadata();
            switch (message.getMessageType()) {

                case MessageType.SYSTEM, MessageType.USER:
                    chatMessages.add(message);
                    break;

                case MessageType.ASSISTANT:
                    if (!message.getText().isEmpty())
                        chatMessages.add(message);
                    else if (!metadata.containsKey(ID)) {
                        metadata.put("conversationId", conversationId);
                        metadata.put("index", i);
                        toolMessages.add(toDocument(message));
                    }
                    break;

                case MessageType.TOOL:
                    if (!metadata.containsKey(ID)) {
                        metadata.put("conversationId", conversationId);
                        metadata.put("index", i);
                        toolMessages.add(toDocument(message));
                    }
                    break;

                default:
                    throw new RuntimeException("Unhandled message type: " + message.getMessageType());
            }
        };

        this.chatMemoryRepository.saveAll(conversationId, chatMessages);
        this.vectorStore.accept(toolMessages);
    }

    private Document toDocument(Message message) {
        Map<String, Object> metadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());

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
    }

    public static ToolMessageWindowChatMemoryImpl.Builder builder() {
        return new ToolMessageWindowChatMemoryImpl.Builder();
    }

    public static final class Builder {

        private ChatMemoryRepository chatMemoryRepository;

        private int maxMessages = DEFAULT_MAX_MESSAGES;

        private  VectorStore vectorStore;

        private Builder() {
        }

        public ToolMessageWindowChatMemoryImpl.Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
            this.chatMemoryRepository = chatMemoryRepository;
            return this;
        }

        public ToolMessageWindowChatMemoryImpl.Builder vectorStore(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
            return this;
        }

        public ToolMessageWindowChatMemoryImpl.Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public ToolMessageWindowChatMemoryImpl build() {
            if (this.chatMemoryRepository == null) {
                this.chatMemoryRepository = new InMemoryChatMemoryRepository();
            }
            Assert.notNull(this.vectorStore, "vectorStore must be provided");
            return new ToolMessageWindowChatMemoryImpl(this.chatMemoryRepository, this.vectorStore, this.maxMessages);
        }
    }
}
