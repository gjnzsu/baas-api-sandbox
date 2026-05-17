package com.example.baas.sandbox.scenario;

import java.util.Map;

public record ScenarioStep(
        String id,
        String title,
        String method,
        String path,
        Map<String, String> headers,
        Map<String, Object> body,
        ExpectedOutcome expected,
        Map<String, String> capture
) {
}
