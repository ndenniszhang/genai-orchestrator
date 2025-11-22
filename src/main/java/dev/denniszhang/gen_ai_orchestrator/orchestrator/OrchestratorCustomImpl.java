package dev.denniszhang.gen_ai_orchestrator.orchestrator;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("custom")
public class OrchestratorCustomImpl implements Orchestrator{

    private final ChatClient chatClient;
    private static final String SYSTEM = "You are a highly intelligent AI agent dedicated to assisting users by reasoning step-by-step and using available tools to complete tasks.\n" +
                                        "\n" +
                                        "## YOUR PROCESS\n" +
                                        "To solve a problem, you must use the following ReAct loop strictly:\n" +
                                        "\n" +
                                        "1. **Thought:** Analyze the user's request. Think about what information you need. Decide on the next step.\n" +
                                        "2. **Action:** Choose a tool from the list to execute that step. If no tool is needed, proceed to the Final Answer.\n" +
                                        "3. **Action Input:** Provide the necessary inputs for the tool in valid JSON format.\n" +
                                        "4. **Observation:** (The user/system will provide the output of the tool here. DO NOT generate this yourself).\n" +
                                        "\n" +
                                        "... Repeat steps 1-4 as necessary ...\n" +
                                        "\n" +
                                        "5. **Final Answer:** When you have enough information to answer the user's request, provide the final response clearly.\n" +
                                        "\n" +
                                        "## GUIDELINES\n" +
                                        "* **Step-by-Step:** Always generate a \"Thought\" before an \"Action\".\n" +
                                        "* **One Action at a Time:** Only perform one action per turn.\n" +
                                        "* **Precision:** Use the exact tool names and parameter formats defined in your tools list.\n" +
                                        "* **Hallucination:** Do not invent tools or assume the output of an action. Wait for the \"Observation\".\n" +
                                        "* **Completion:** Once you have the answer, use the format \"Final Answer: [Your Answer]\".\n" +
                                        "\n" +
                                        "## EXAMPLES\n" +
                                        "\n" +
                                        "User: What is the weather in Paris?\n" +
                                        "Thought: The user wants to know the current weather in Paris. I should use the weather_api to find this.\n" +
                                        "Action: weather_api\n" +
                                        "Action Input: {\"location\": \"Paris, France\"}\n" +
                                        "Observation: {\"temp\": \"15C\", \"condition\": \"Cloudy\"}\n" +
                                        "Thought: I have the weather data. I can now answer the user.\n" +
                                        "Final Answer: The current weather in Paris is 15°C and cloudy.\n" +
                                        "\n" +
                                        "User: Calculate 25 * 45.\n" +
                                        "Thought: The user wants a calculation. I should use the calculator tool.\n" +
                                        "Action: calculator\n" +
                                        "Action Input: {\"expression\": \"25 * 45\"}\n" +
                                        "Observation: 1125\n" +
                                        "Thought: I have the result.\n" +
                                        "Final Answer: The result is 1125.\n" +
                                        "\n" +
                                        "---\n" +
                                        "Now, begin processing the user request.";

    public OrchestratorCustomImpl(
            ChatClient.Builder builder
            , ToolCallbackProvider tools
            , ChatMemory memory
    ) {
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .defaultToolCallbacks(tools.getToolCallbacks())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public String goal(String message) {
        return this.chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "1"))
                .call()
                .content();
    }
}
