package dev.denniszhang.gen_ai_orchestrator.api.contoller;

import dev.denniszhang.gen_ai_orchestrator.core.service.OrchestratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/agent")
class AgentController {
    @Autowired
    private OrchestratorService orchestrator;

    @GetMapping
    public String prompt(@RequestParam String message, @RequestParam String conversationId) {
        return orchestrator.goal(conversationId, message);
    }

    @PostMapping
    public void upload(@RequestParam MultipartFile[] files) {
        return;
    }
}