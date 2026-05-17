package com.example.baas.sandbox.scenario;

import java.util.List;

public record SandboxScenario(
        String id,
        String title,
        String category,
        String purpose,
        List<String> preconditions,
        String dependsOn,
        List<ScenarioStep> steps,
        List<String> evidenceFields
) {
}
