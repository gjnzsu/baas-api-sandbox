package com.example.baas.sandbox;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SandboxScenarioMetadataTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesScenarioCatalogWithoutAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/sandbox/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("http://localhost:8080"))
                .andExpect(jsonPath("$.categories", hasSize(3)))
                .andExpect(jsonPath("$.categories", hasItem("positive")))
                .andExpect(jsonPath("$.categories", hasItem("negative")))
                .andExpect(jsonPath("$.categories", hasItem("boundary")));
    }

    @Test
    void includesRequiredScenarioCoverageAndBaasHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/sandbox/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'successful-payment')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'payment-lookup')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'lifecycle-events-lookup')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'missing-token')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'invalid-token')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'insufficient-scope')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'insufficient-funds')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'invalid-beneficiary')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'authorization-rejected')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'duplicate-payment')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'minimum-valid-amount')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'invalid-amount')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'invalid-currency')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'idempotent-replay')]").exists())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'idempotency-conflict')]").exists())
                .andExpect(jsonPath("$.scenarios[0].steps[0].headers['X-Partner-Id']")
                        .value("fintech-partner-001"))
                .andExpect(jsonPath("$.scenarios[0].steps[0].headers['X-On-Behalf-Of-Customer-Id']")
                        .value("bank-customer-456"))
                .andExpect(jsonPath("$.scenarios[0].steps[0].headers['X-Customer-Consent-Id']")
                        .value("consent-payment-uat-001"));
    }

    @Test
    void representsMultiStepScenariosWithCaptureRules() throws Exception {
        mockMvc.perform(get("/api/v1/sandbox/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios[1].id").value("payment-lookup"))
                .andExpect(jsonPath("$.scenarios[1].steps", hasSize(2)))
                .andExpect(jsonPath("$.scenarios[1].steps[0].capture.paymentId").value("$.paymentId"))
                .andExpect(jsonPath("$.scenarios[1].steps[1].path").value("/api/v1/payments/{paymentId}"));
    }
}
