package com.example.baas.sandbox.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public CreateResult create(PaymentRequest request, String idempotencyKey, String scenario) {
        String fingerprint = fingerprint(request);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyRecord record = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (record != null) {
                if (!record.requestFingerprint().equals(fingerprint)) {
                    throw new PaymentException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "Idempotency key was already used for a different payment request");
                }
                return new CreateResult(record.response(), true);
            }
        }

        return switch (normalizeScenario(scenario)) {
            case "insufficient_funds" -> failAndStore(request, idempotencyKey, fingerprint, PaymentStatus.FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Debtor account has insufficient funds");
            case "invalid_beneficiary" -> failAndStore(request, idempotencyKey, fingerprint, PaymentStatus.REJECTED,
                    HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BENEFICIARY", "Creditor account could not be validated");
            case "authorization_rejected" -> failAndStore(request, idempotencyKey, fingerprint, PaymentStatus.REJECTED,
                    HttpStatus.FORBIDDEN, "AUTHORIZATION_REJECTED", "Payment authorization was rejected");
            case "duplicate_payment" -> failAndStore(request, idempotencyKey, fingerprint, PaymentStatus.REJECTED,
                    HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", "Payment matches a duplicate sandbox scenario");
            default -> {
                PaymentResponse response = buildResponse(request, PaymentStatus.EXECUTED, List.of(
                        PaymentEvent.of(PaymentStatus.RECEIVED, "Payment instruction received"),
                        PaymentEvent.of(PaymentStatus.VALIDATED, "Payment instruction validated"),
                        PaymentEvent.of(PaymentStatus.EXECUTED, "Sandbox transfer executed")
                ));
                save(response, idempotencyKey, fingerprint);
                yield new CreateResult(response, false);
            }
        };
    }

    public PaymentResponse get(String paymentId) {
        return repository.findById(paymentId)
                .map(StoredPayment::response)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                        "Payment was not found", Map.of("paymentId", paymentId)));
    }

    public List<PaymentEvent> events(String paymentId) {
        return get(paymentId).events();
    }

    private CreateResult failAndStore(
            PaymentRequest request,
            String idempotencyKey,
            String fingerprint,
            PaymentStatus status,
            HttpStatus httpStatus,
            String code,
            String message
    ) {
        PaymentResponse response = buildResponse(request, status, List.of(
                PaymentEvent.of(PaymentStatus.RECEIVED, "Payment instruction received"),
                PaymentEvent.of(status, message)
        ));
        save(response, idempotencyKey, fingerprint);
        throw new PaymentException(httpStatus, code, message, Map.of("paymentId", response.paymentId()));
    }

    private PaymentResponse buildResponse(PaymentRequest request, PaymentStatus status, List<PaymentEvent> events) {
        return new PaymentResponse(
                "pay_" + UUID.randomUUID(),
                status,
                request.debtorAccountId(),
                request.creditorAccountId(),
                request.amount(),
                request.currency(),
                request.reference(),
                events,
                Instant.now());
    }

    private void save(PaymentResponse response, String idempotencyKey, String fingerprint) {
        repository.save(new StoredPayment(response));
        repository.saveIdempotencyRecord(idempotencyKey, new IdempotencyRecord(fingerprint, response));
    }

    private String normalizeScenario(String scenario) {
        return scenario == null ? "" : scenario.trim().toLowerCase();
    }

    private String fingerprint(PaymentRequest request) {
        String raw = request.debtorAccountId() + "|" + request.creditorAccountId() + "|" + request.amount()
                + "|" + request.currency() + "|" + request.reference();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CreateResult(PaymentResponse response, boolean replayed) {
    }
}
