package dev.denniszhang.gen_ai_orchestrator.api.contoller;

import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Arrays;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/agent")
class AgentController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private OrchestratorService orchestrator;

    @GetMapping("chat")
    public AssistantMessage chat(@RequestParam String message, @RequestParam String conversationId) {
        return orchestrator.chat(conversationId, message);
    }

    @GetMapping("stream")
    public Flux<AssistantMessage> stream(@RequestParam String message, @RequestParam String conversationId) {
        return orchestrator.stream(conversationId, message);
    }

    @PostMapping
    public void upload(@RequestParam MultipartFile[] files) {
        orchestrator.store(
            (Resource[]) Arrays.stream(files).map(f -> {
                try {
                    return new InputStreamResource(f.getInputStream());
                } catch (IOException e) {
                    logger.error("File Read Exception.", e);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Files can't be processed.");
                }
            }).toArray());
    }
}