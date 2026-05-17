package com.example.baas.sandbox.scenario;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SandboxScenarioService {

    private static final List<String> EVIDENCE_FIELDS = List.of(
            "timestamp", "request", "httpStatus", "responseBody", "paymentId", "expectedMatch");

    public ScenarioCatalog catalog() {
        return new ScenarioCatalog(
                "http://localhost:8080",
                new ScenarioCatalog.AuthMetadata(
                        "bearer",
                        "sandbox-payment-token",
                        "sandbox-readonly-token",
                        "Authorization"),
                List.of("positive", "negative", "boundary"),
                List.of(
                        successfulPayment(),
                        paymentLookup(),
                        lifecycleEventsLookup(),
                        authScenario("missing-token", "Missing bearer token", Map.of(), 401, "AUTH_REQUIRED"),
                        authScenario("invalid-token", "Invalid bearer token",
                                headers("Bearer unknown", "req-uat-invalid-token", null), 401, "AUTH_INVALID"),
                        authScenario("insufficient-scope", "Insufficient payment scope",
                                headers("Bearer sandbox-readonly-token", "req-uat-insufficient-scope", null),
                                403, "INSUFFICIENT_SCOPE"),
                        failureScenario("insufficient-funds", "Insufficient funds",
                                "insufficient_funds", 422, "INSUFFICIENT_FUNDS"),
                        failureScenario("invalid-beneficiary", "Invalid beneficiary",
                                "invalid_beneficiary", 422, "INVALID_BENEFICIARY"),
                        failureScenario("authorization-rejected", "Authorization rejected",
                                "authorization_rejected", 403, "AUTHORIZATION_REJECTED"),
                        failureScenario("duplicate-payment", "Duplicate payment",
                                "duplicate_payment", 409, "DUPLICATE_PAYMENT"),
                        minimumValidAmount(),
                        validationScenario("invalid-amount", "Invalid amount",
                                paymentBody(new BigDecimal("0.00"), "USD"), "VALIDATION_ERROR"),
                        validationScenario("invalid-currency", "Invalid currency",
                                paymentBody(new BigDecimal("10.00"), "US"), "VALIDATION_ERROR"),
                        idempotentReplay(),
                        idempotencyConflict()));
    }

    private SandboxScenario successfulPayment() {
        return scenario("successful-payment", "Successful A2A payment initialization", "positive",
                "Proves the partner can initialize a valid A2A payment and receive a payment identifier.",
                List.of(step("create-payment", "Create payment", "POST", "/api/v1/payments",
                        headers("Bearer sandbox-payment-token", "req-uat-successful-payment-001",
                                "uat-successful-payment-001"),
                        paymentBody(new BigDecimal("125.50"), "USD"),
                        expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                        Map.of("paymentId", "$.paymentId"))));
    }

    private SandboxScenario paymentLookup() {
        return scenario("payment-lookup", "Payment lookup", "positive",
                "Proves the partner can retrieve a created payment by identifier.",
                List.of(
                        step("create-payment", "Create payment", "POST", "/api/v1/payments",
                                headers("Bearer sandbox-payment-token", "req-uat-lookup-create",
                                        "uat-payment-lookup-001"),
                                paymentBody(new BigDecimal("42.00"), "USD"),
                                expected(201, "EXECUTED", null,
                                        List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of("paymentId", "$.paymentId")),
                        step("get-payment", "Get payment", "GET", "/api/v1/payments/{paymentId}",
                                headers("Bearer sandbox-payment-token", "req-uat-lookup-get", null),
                                Map.of(),
                                expected(200, "EXECUTED", null, List.of()),
                                Map.of())));
    }

    private SandboxScenario lifecycleEventsLookup() {
        return scenario("lifecycle-events-lookup", "Lifecycle events lookup", "positive",
                "Proves the partner can retrieve lifecycle evidence for a created payment.",
                List.of(
                        step("create-payment", "Create payment", "POST", "/api/v1/payments",
                                headers("Bearer sandbox-payment-token", "req-uat-events-create", "uat-events-001"),
                                paymentBody(new BigDecimal("64.00"), "USD"),
                                expected(201, "EXECUTED", null,
                                        List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of("paymentId", "$.paymentId")),
                        step("get-events", "Get lifecycle events", "GET", "/api/v1/payments/{paymentId}/events",
                                headers("Bearer sandbox-payment-token", "req-uat-events-get", null),
                                Map.of(),
                                expected(200, null, null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of())));
    }

    private SandboxScenario failureScenario(String id, String title, String scenarioHeader, int status, String code) {
        Map<String, String> headers = new LinkedHashMap<>(
                headers("Bearer sandbox-payment-token", "req-uat-" + id, null));
        headers.put("X-Sandbox-Scenario", scenarioHeader);
        return scenario(id, title, "negative",
                "Proves the partner handles " + title.toLowerCase() + " responses.",
                List.of(step("create-payment", title, "POST", "/api/v1/payments", headers,
                        paymentBody(new BigDecimal("125.50"), "USD"),
                        expected(status, null, code, List.of()), Map.of())));
    }

    private SandboxScenario authScenario(String id, String title, Map<String, String> headers, int status, String code) {
        return scenario(id, title, "negative", "Proves the partner handles " + title.toLowerCase() + ".",
                List.of(step("create-payment", title, "POST", "/api/v1/payments", headers,
                        paymentBody(new BigDecimal("125.50"), "USD"),
                        expected(status, null, code, List.of()), Map.of())));
    }

    private SandboxScenario minimumValidAmount() {
        return scenario("minimum-valid-amount", "Minimum valid amount", "boundary",
                "Proves the partner can submit the smallest supported payment amount.",
                List.of(step("create-payment", "Create minimum amount payment", "POST", "/api/v1/payments",
                        headers("Bearer sandbox-payment-token", "req-uat-minimum-amount",
                                "uat-minimum-amount-001"),
                        paymentBody(new BigDecimal("0.01"), "USD"),
                        expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                        Map.of("paymentId", "$.paymentId"))));
    }

    private SandboxScenario validationScenario(String id, String title, Map<String, Object> body, String code) {
        return scenario(id, title, "boundary", "Proves the partner handles request validation errors.",
                List.of(step("create-payment", title, "POST", "/api/v1/payments",
                        headers("Bearer sandbox-payment-token", "req-uat-" + id, null),
                        body, expected(400, null, code, List.of()), Map.of())));
    }

    private SandboxScenario idempotentReplay() {
        Map<String, String> headers = headers("Bearer sandbox-payment-token", "req-uat-idem-replay",
                "uat-idem-replay-001");
        return scenario("idempotent-replay", "Idempotent replay", "boundary",
                "Proves the partner can safely replay the same create request with the same idempotency key.",
                List.of(
                        step("first-create", "First create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("77.00"), "USD"),
                                expected(201, "EXECUTED", null,
                                        List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of("paymentId", "$.paymentId")),
                        step("replay-create", "Replay create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("77.00"), "USD"),
                                expected(200, "EXECUTED", null,
                                        List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of())));
    }

    private SandboxScenario idempotencyConflict() {
        Map<String, String> headers = headers("Bearer sandbox-payment-token", "req-uat-idem-conflict",
                "uat-idem-conflict-001");
        return scenario("idempotency-conflict", "Idempotency conflict", "boundary",
                "Proves the partner handles a reused idempotency key with a different payload.",
                List.of(
                        step("first-create", "First create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("88.00"), "USD"),
                                expected(201, "EXECUTED", null,
                                        List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of()),
                        step("conflicting-create", "Conflicting create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("89.00"), "USD"),
                                expected(409, null, "IDEMPOTENCY_CONFLICT", List.of()), Map.of())));
    }

    private SandboxScenario scenario(String id, String title, String category, String purpose,
                                     List<ScenarioStep> steps) {
        return new SandboxScenario(id, title, category, purpose, List.of(), null, steps, EVIDENCE_FIELDS);
    }

    private ScenarioStep step(String id, String title, String method, String path, Map<String, String> headers,
                              Map<String, Object> body, ExpectedOutcome expected, Map<String, String> capture) {
        return new ScenarioStep(id, title, method, path, headers, body, expected, capture);
    }

    private ExpectedOutcome expected(int httpStatus, String paymentStatus, String errorCode,
                                     List<String> eventStatuses) {
        return new ExpectedOutcome(httpStatus, paymentStatus, errorCode, eventStatuses);
    }

    private Map<String, String> headers(String authorization, String requestId, String idempotencyKey) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (authorization != null) {
            headers.put("Authorization", authorization);
        }
        headers.put("X-Partner-Id", "fintech-partner-001");
        headers.put("X-On-Behalf-Of-Customer-Id", "bank-customer-456");
        headers.put("X-Customer-Consent-Id", "consent-payment-uat-001");
        if (requestId != null) {
            headers.put("X-Request-Id", requestId);
        }
        if (idempotencyKey != null) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private Map<String, Object> paymentBody(BigDecimal amount, String currency) {
        return Map.of(
                "debtorAccountId", "acct-debtor-001",
                "creditorAccountId", "acct-creditor-001",
                "amount", amount,
                "currency", currency,
                "reference", "uat-runbook");
    }
}
