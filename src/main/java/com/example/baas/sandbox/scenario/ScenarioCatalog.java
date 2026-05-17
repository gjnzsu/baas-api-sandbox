package com.example.baas.sandbox.scenario;

import java.util.List;

public record ScenarioCatalog(
        String baseUrl,
        AuthMetadata auth,
        List<String> categories,
        List<SandboxScenario> scenarios
) {
    public record AuthMetadata(
            String type,
            String tokenHint,
            String readonlyTokenHint,
            String headerName
    ) {
    }
}
