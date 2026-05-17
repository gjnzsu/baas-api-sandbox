package com.example.baas.sandbox.api;

import com.example.baas.sandbox.scenario.SandboxScenarioService;
import com.example.baas.sandbox.scenario.ScenarioCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sandbox/scenarios")
public class SandboxScenarioController {

    private final SandboxScenarioService scenarioService;

    public SandboxScenarioController(SandboxScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    ScenarioCatalog scenarios() {
        return scenarioService.catalog();
    }
}
