package dev.denniszhang.gen_ai_orchestrator.contoller;

import dev.denniszhang.gen_ai_orchestrator.orchestrator.Orchestrator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {

    private final Orchestrator orchestrator;

    public AgentController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/goal")
    public String goal(@RequestParam String message) {
        return this.orchestrator.goal(message);
    }
}