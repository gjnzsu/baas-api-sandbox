package com.example.baas.sandbox;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiSandboxApplicationTests {

    private static final String PAYMENT_JSON = """
            {
              "debtorAccountId": "acct-debtor-001",
              "creditorAccountId": "acct-creditor-001",
              "amount": 125.50,
              "currency": "USD",
              "reference": "invoice-1001"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndFetchesSuccessfulPayment() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-payment-token")
                        .header("Idempotency-Key", "idem-success-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId", notNullValue()))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.debtorAccountId").value("acct-debtor-001"))
                .andExpect(jsonPath("$.creditorAccountId").value("acct-creditor-001"))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andReturn();

        String paymentId = JsonTestSupport.read(created, "paymentId");

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header("Authorization", "Bearer sandbox-payment-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        mockMvc.perform(get("/api/v1/payments/{paymentId}/events", paymentId)
                        .header("Authorization", "Bearer sandbox-payment-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[2].status").value("EXECUTED"));
    }

    @Test
    void rejectsInvalidPaymentRequestAndUnknownPaymentLookup() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-payment-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/payments/{paymentId}", "pay_missing")
                        .header("Authorization", "Bearer sandbox-payment-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void rejectsMissingInvalidAndInsufficientScopeTokens() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID"));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-readonly-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
    }

    @Test
    void simulatesSandboxFailures() throws Exception {
        assertScenario("insufficient_funds", 422, "INSUFFICIENT_FUNDS");
        assertScenario("invalid_beneficiary", 422, "INVALID_BENEFICIARY");
        assertScenario("authorization_rejected", 403, "AUTHORIZATION_REJECTED");
        assertScenario("duplicate_payment", 409, "DUPLICATE_PAYMENT");
    }

    @Test
    void handlesIdempotencyReplayAndConflict() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-payment-token")
                        .header("Idempotency-Key", "idem-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-payment-token")
                        .header("Idempotency-Key", "idem-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        String differentPayment = PAYMENT_JSON.replace("125.50", "126.50");

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-payment-token")
                        .header("Idempotency-Key", "idem-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentPayment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void exposesOpenApiAndHealth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/payments']").exists());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void assertScenario(String scenario, int status, String code) throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer sandbox-payment-token")
                        .header("X-Sandbox-Scenario", scenario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }
}
