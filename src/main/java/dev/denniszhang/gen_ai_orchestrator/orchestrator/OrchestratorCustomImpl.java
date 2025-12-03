package dev.denniszhang.gen_ai_orchestrator.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("custom")
public class OrchestratorCustomImpl implements Orchestrator{

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ToolCallbackProvider toolProvider;
    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ChatMemory chatMemory;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String SYSTEM_TEMPLATE = """
    You are a precise, analytical AI agent designed to solve problems using strict Chain of Thought (CoT) reasoning and the ReAct (Reasoning + Acting) pattern.
   
    ## CRITICAL INSTRUCTION: NO LOOPING
    You must execute **one single turn** of the ReAct cycle at a time.
    1. Analyze the request.
    2. Formulate a Thought.
    3. Select an Tool (if necessary).
    4. **STOP.** Do not generate the "Observation". Do not hallucinate the outcome of the Tool use.
   
    ## 1. AVAILABLE TOOLS
    You have access to the following tools. You must choose the tool that best fits the current step of your reasoning.
    %s
   
    ## 2. KNOWLEDGE BASE
    %s
   
    ## 3. RESPONSE FORMAT
    You must strictly adhere to the following format. Do not deviate.
  
    **Option A: If you need to use a tool:**
    Thought: [Your detailed Chain of Thought reasoning. Break down the problem, analyze previous observations, and decide what data is missing]
    Action: [The exact name of the tool to use]
    Action Input: [The arguments for the tool, strictly formatted as JSON or the required syntax]
   
    **Option B: If you have sufficient information to answer the user:**
    Thought: [Your reasoning on why you now have the answer]
    Final Answer: [Your comprehensive, evidence-based answer to the user]
   
    ## 4. GUIDELINES
    * **Chain of Thought:** Never skip the "Thought" line. Your reasoning must be explicit, logical, and evidence-based.
    * **Tool Selection:** Only pick tools defined in the "Available Tools" section.
    * **Input Sanitization:** Ensure "Action Input" is precise and matches the tool's signature.
    * **Wait for Observation:** Once you output "Action Input", you must stop generating text. The system will provide the "Observation" in the next message.
   
    ## 5. EXAMPLE INTERACTION (For Context Only)
   
    User:
    Message: What is the weather in Tokyo?
   
    Assistant:
    Thought: The user is asking for current weather data. I do not have this information in my internal knowledge base. I should use the search tool to find the current weather in Tokyo.
    Action: search_tool
    Action Input: "current weather in Tokyo"
   
    use the given context to answer the users request
   """;

    public String goal(String conversationId, String message) {
        var toolCallbacks = toolProvider.getToolCallbacks();
        var toolDefinitions =  Arrays.stream(toolCallbacks)
                                .map(ToolCallback::getToolDefinition)
                                .collect(Collectors
                                .toMap(d -> d.name(), d -> d.toString()));
        var context = vectorStore.similaritySearch(message);
//                            .similarityThreshold(0.8d)
//                            .topK(6)
        var chatOptions = ToolCallingChatOptions.builder()
            .toolCallbacks(toolCallbacks)
            .internalToolExecutionEnabled(false)
            .build();

        var promptWithMemory = new Prompt();
        if(chatMemory.get(conversationId).isEmpty()) {
            promptWithMemory = new Prompt(
            List.of(new SystemMessage(SYSTEM_TEMPLATE.formatted(toolDefinitions, context)), new UserMessage(message)),
            chatOptions);
            chatMemory.add(conversationId, promptWithMemory.getInstructions());
        }else {
            chatMemory.add(conversationId, new Prompt(new UserMessage(message), chatOptions).getInstructions());
            promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
        }

        var toolCallingManager = DefaultToolCallingManager.builder().build();
        int iteration = 0;
        ChatResponse chatResponse = null;
        do{
            promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
            chatResponse = chatModel.call(promptWithMemory);
            chatMemory.add(conversationId, chatResponse.getResult().getOutput());

            if(chatResponse.hasToolCalls()) {
                var toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory, chatResponse);
                chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
                        .get(toolExecutionResult.conversationHistory().size() - 1));
            }

            ++iteration;
        }while(iteration <= 10 && chatResponse.hasToolCalls());

        return chatResponse.getResult().getOutput().getText();
    }

    public Orchestrator store(List<Document> documents) {
        if(!documents.isEmpty())
            this.vectorStore.add(documents);
        return this;
    }
}
