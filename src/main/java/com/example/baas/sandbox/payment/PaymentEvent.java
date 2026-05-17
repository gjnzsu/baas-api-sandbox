package com.example.baas.sandbox.payment;

import java.time.Instant;

public record PaymentEvent(
        PaymentStatus status,
        String message,
        Instant timestamp
) {
    public static PaymentEvent of(PaymentStatus status, String message) {
        return new PaymentEvent(status, message, Instant.now());
    }
}
