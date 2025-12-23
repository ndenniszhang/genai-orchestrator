GenAI agent capable of understanding user requests, reasoning about them, and using a set of tools to accomplish complex tasks. This project serves as a robust backend orchestrator, built with Spring Boot, that follows a ReAct-style reasoning loop to interact with Large Language Models (LLMs) and external APIs.

---

## 🏛️ System Architecture

The core of this project is built around the **Strategy Design Pattern** to create a flexible and pluggable orchestration layer. This approach insulates the application from the volatile nature of AI technology, allowing different agentic engines to be developed, tested, and swapped with a simple configuration change.

This design provides two key benefits:

1.  **Initial Speed:** Allows for rapid prototyping with high-level frameworks.
2.  **Long-term Flexibility:** Enables the development of custom, fine-tuned orchestration logic without a system-wide rewrite.

### Architectural Diagram

The diagram below illustrates this pluggable architecture. The `API Controller` depends only on the `AIOrchestrator` interface, completely decoupling it from the specific implementation details.

```mermaid
flowchart TB
    %% ======================================================
    %% Agentic AI Backend
    %% ======================================================
    subgraph backend["Agentic AI Backend"]
        agent_controller["Agent Controller<br/><i>Spring @RestController</i><br/>Handles user requests.<br/>Depends *only* on the AIOrchestrator interface."]
        ai_orchestrator["AIOrchestrator<br/><i>Java Interface</i><br/>Facade defining the service contract for all agentic implementations."]
        agent_controller -->|Uses| ai_orchestrator

        %% --------------------------------------------------
        %% AI Orchestrator Implementations
        %% --------------------------------------------------
        subgraph impl["AI Orchestrator Impl"]
            %% Embabel Adapter
            subgraph embabel["Embabel Adapter"]
                embabel_adapter["EmbabelAgentAdapter<br/><i>Spring @Service (@Profile('embabel'))</i><br/>Implements orchestrator using the Embabel framework."]
                %% embabel_adapter -->|Implements| ai_orchestrator
            end

            %% Custom Orchestrator
            subgraph custom["Custom Orchestrator"]
                custom_orchestrator["CustomAgentOrchestrator<br/><i>Spring @Service (@Profile('custom'))</i><br/>Implements orchestrator using custom components."]
                model_client["Model Client<br/><i>Spring @Service</i><br/>Abstracts LLM communication."]
                mcp_client_registry["MCP Client Registry<br/><i>Spring @Service</i><br/>Discovers and manages MCP Clients."]
                mcp_client_1["MCP Client<br/><i>Java Class</i><br/>Client for a single MCP server."]
                mcp_client_2["MCP Client<br/><i>Java Class</i><br/>Client for a single MCP server."]
                task_state_repository["Task State Repository<br/><i>Spring @Repository</i><br/>Interface for state persistence."]

                custom_orchestrator -->|Uses| model_client
                custom_orchestrator -->|Uses| mcp_client_registry
                custom_orchestrator -->|Uses| task_state_repository
                mcp_client_registry -->|Manages| mcp_client_1
                mcp_client_registry -->|Manages| mcp_client_2
            end
        end

        embabel_adapter -->|Implements| ai_orchestrator
        custom_orchestrator -->|Implements| ai_orchestrator
    end

    %% ======================================================
    %% External Systems
    %% ======================================================
    llm["Large Language Model (LLM)<br/><i>Ollama Container / OpenAI API</i><br/>Provides reasoning capabilities."]
    mcp_server_1["MCP Server<br/><i>External Service</i><br/>Exposes tools via MCP. (Web Search)"]
    mcp_server_2["MCP Server<br/><i>External Service</i><br/>Exposes tools via MCP. (Weather Info)"]
    database[("NoSQL Database<br/><i>MongoDB / Redis</i><br/>Stores agent task state.")]

    %% ======================================================
    %% External Relationships
    %% ======================================================
    model_client -->|Abstracts reasoning from| llm
    mcp_client_1 -->|Abstracts tool use from| mcp_server_1
    mcp_client_2 -->|Abstracts tool use from| mcp_server_2
    task_state_repository -.->|Abstracts state persistence to| database
```

---

### 🧱 Key Components

#### Shared Components

- **API Controller**: The REST entry point. It depends _only_ on the `AIOrchestrator` interface.
- **AIOrchestrator (Interface)**: The core service contract that defines how the application interacts with any agentic engine.

#### Pluggable Implementations

- **Custom ReAct Orchestrator (`@Profile("custom")`)**: The default, hand-built implementation that uses a ReAct-style reasoning loop. It contains the following sub-components:
  - **LLM Client**: Makes calls to the external LLM.
  - **Tool Dispatcher**: Executes tool logic.
  - **Tool Registry**: Stores definitions of available tools.
- **Embabel Orchestrator (`@Profile("embabel")`)**: An alternative implementation that delegates orchestration logic to the high-level Embabel framework.

---

### 🔄 Interaction Flow (Custom ReAct Implementation)

The custom orchestrator uses an orchestrated, multistep reasoning loop:

```mermaid
flowchart TB
    %% === Container Boundary for Orchestrator ===
    subgraph orchestrator["ReAct Loop Orchestrator"]
        A["Receives User prompt"]
        B["Reason: Break down task and decide next action"]
        C{"Is action a tool call?"}
        D["Act: Call tools and return observation"]
        E["Return final LLM response"]
    end
    class orchestrator container

    %% === Reasoning Flow ===
    A --> B --> C
    C -- "Yes" --> D
    C -- "No" --> E
    D --> B

    %% === Styling ===
    classDef process fill:#dfe7fd,stroke:#7b8ab8,stroke-width:1px,color:#000
    classDef decision fill:#fff3cd,stroke:#b1953d,stroke-width:1px,color:#000
    class A,B,D,E process
    class C decision
```

---

## 🛠️ Technology Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 25+
- **Build Tool**: Maven / Gradle
- **Containerization**: Docker

---

## 🚀 Getting Started

### Prerequisites

- Java JDK 25 or later
- Maven or Gradle installed
- An API Key for an LLM provider (e.g., OpenAI)

### Installation & Running

1.  **Clone the repository:**

    ```sh
    git clone https://github.com/ndenniszhang/genai-orchestrator
    cd genai-orchestrator
    ```

2.  **Configure your API Key:**
    Open `src/main/resources/application.properties` and add your credentials:

    ```properties
    llm.api.key=YOUR_API_KEY_HERE
    ```

3.  **Build and run the application:**
    ```sh
    # Run the Embabel orchestrator (default)
    ./mvnw spring-boot:run -Dspring.profiles.active=embabel
    ```
    ```sh
    # Or run the Custom ReAct orchestrator
    ./mvnw spring-boot:run -Dspring.profiles.active=custom
    ```

The application will be running on `http://localhost:8080`.

### Example API Call

You can interact with GenAI Orchestrator using a simple cURL request:

```sh
curl -X POST http://localhost:8080/api/v1/agent/chat \
-H "Content-Type: application/json" \
-d '{"conversationId": "test-1", "message": "What is the weather in New York?"}'
```
