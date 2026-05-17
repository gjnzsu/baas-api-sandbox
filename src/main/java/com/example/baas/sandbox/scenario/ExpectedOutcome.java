package com.example.baas.sandbox.scenario;

import java.util.List;

public record ExpectedOutcome(
        int httpStatus,
        String paymentStatus,
        String errorCode,
        List<String> eventStatuses
) {
}
