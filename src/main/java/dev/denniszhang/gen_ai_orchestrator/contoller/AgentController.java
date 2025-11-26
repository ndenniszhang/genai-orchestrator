package dev.denniszhang.gen_ai_orchestrator.contoller;

import dev.denniszhang.gen_ai_orchestrator.orchestrator.Orchestrator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
class AgentController {

    private final Orchestrator orchestrator;

    public AgentController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("")
    public String prompt(@RequestParam String message) {
        return this.orchestrator.goal(message);
    }
}