package com.example.baas.sandbox.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        String paymentId,
        PaymentStatus status,
        String debtorAccountId,
        String creditorAccountId,
        BigDecimal amount,
        String currency,
        String reference,
        List<PaymentEvent> events,
        Instant createdAt
) {
}
