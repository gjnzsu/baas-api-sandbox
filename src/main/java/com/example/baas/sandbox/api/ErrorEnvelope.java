package com.example.baas.sandbox.api;

import java.time.Instant;
import java.util.Map;

public record ErrorEnvelope(
        String code,
        String message,
        Map<String, Object> details,
        Instant timestamp
) {
    public static ErrorEnvelope of(String code, String message, Map<String, Object> details) {
        return new ErrorEnvelope(code, message, details == null ? Map.of() : details, Instant.now());
    }
}
