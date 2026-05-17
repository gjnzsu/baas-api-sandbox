package com.example.baas.sandbox;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.baas.sandbox.scenario.SandboxScenario;
import com.example.baas.sandbox.scenario.SandboxScenarioService;
import com.example.baas.sandbox.scenario.ScenarioStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class SandboxScenarioMetadataTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SandboxScenarioService scenarioService;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void allScenarioDefinitionsExecuteAgainstSandboxApi() throws Exception {
        List<SandboxScenario> scenarios = scenarioService.catalog().scenarios();

        assertThat(scenarios, hasSize(15));

        for (SandboxScenario scenario : scenarios) {
            Map<String, String> captures = new LinkedHashMap<>();
            String runId = scenario.id() + "-" + UUID.randomUUID();

            for (ScenarioStep step : scenario.steps()) {
                ScenarioStep prepared = prepareStep(step, captures, runId);
                MvcResult result = mockMvc.perform(toRequest(prepared)).andReturn();
                JsonNode response = parseResponse(result);

                String label = scenario.id() + "/" + step.id();
                assertThat(label + " HTTP status",
                        result.getResponse().getStatus(), is(prepared.expected().httpStatus()));

                if (prepared.expected().paymentStatus() != null) {
                    assertThat(label + " payment status",
                            response.path("status").asText(), is(prepared.expected().paymentStatus()));
                }
                if (prepared.expected().errorCode() != null) {
                    assertThat(label + " error code",
                            response.path("code").asText(), is(prepared.expected().errorCode()));
                }
                if (!prepared.expected().eventStatuses().isEmpty()) {
                    assertThat(label + " event statuses",
                            eventStatuses(response), is(prepared.expected().eventStatuses()));
                }
                if ("$.paymentId".equals(prepared.capture().get("paymentId"))) {
                    captures.put("paymentId", response.path("paymentId").asText());
                }
            }
        }
    }

    private ScenarioStep prepareStep(ScenarioStep step, Map<String, String> captures, String runId) {
        Map<String, String> headers = new LinkedHashMap<>(step.headers());
        if (headers.containsKey("Idempotency-Key")) {
            headers.put("Idempotency-Key", headers.get("Idempotency-Key") + "-" + runId);
        }

        String path = step.path();
        for (Map.Entry<String, String> capture : captures.entrySet()) {
            path = path.replace("{" + capture.getKey() + "}", capture.getValue());
        }

        return new ScenarioStep(step.id(), step.title(), step.method(), path, headers, step.body(),
                step.expected(), step.capture());
    }

    private MockHttpServletRequestBuilder toRequest(ScenarioStep step) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(step.method()), step.path());
        step.headers().forEach(builder::header);

        if (!"GET".equals(step.method())) {
            builder.contentType(MediaType.APPLICATION_JSON);
            builder.content(objectMapper.writeValueAsString(step.body()));
        }

        return builder;
    }

    private JsonNode parseResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> eventStatuses(JsonNode response) {
        JsonNode events = response.isArray() ? response : response.path("events");
        List<String> statuses = new ArrayList<>();
        events.forEach(event -> statuses.add(event.path("status").asText()));
        return statuses;
    }
}
