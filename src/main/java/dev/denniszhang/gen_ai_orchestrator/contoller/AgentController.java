package dev.denniszhang.gen_ai_orchestrator.contoller;

import dev.denniszhang.gen_ai_orchestrator.orchestrator.Orchestrator;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
class AgentController {
    @Autowired
    private Orchestrator orchestrator;

    @GetMapping()
    public String prompt(@RequestParam String message, @RequestParam(required = false) String conversationId) {
        conversationId = "1";
        return orchestrator.goal(conversationId, message);
    }

    @PostMapping()
    public Void upload(@RequestParam(required = false) MultipartFile[] files) {
        orchestrator.store(List.of(
            new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
            new Document("The World is Big and Salvation Lurks Around the Corner"),
            new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2"))
        ));
        return null;
    }
}