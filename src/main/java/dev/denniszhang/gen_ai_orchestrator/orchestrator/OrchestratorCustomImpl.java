package dev.denniszhang.gen_ai_orchestrator.orchestrator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("custom")
public class OrchestratorCustomImpl implements Orchestrator{
}
