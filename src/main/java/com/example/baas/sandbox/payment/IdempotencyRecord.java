package com.example.baas.sandbox.payment;

public record IdempotencyRecord(String requestFingerprint, PaymentResponse response) {
}
