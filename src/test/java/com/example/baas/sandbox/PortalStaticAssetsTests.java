package com.example.baas.sandbox;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PortalStaticAssetsTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesPortalEntryPointWithoutAuthorization() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BaaS Payment Sandbox")))
                .andExpect(content().string(containsString("scenario-runbook")))
                .andExpect(content().string(containsString("evidence-panel")));
    }

    @Test
    void servesPortalAssetsWithoutAuthorization() throws Exception {
        mockMvc.perform(get("/portal.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".workspace")));

        mockMvc.perform(get("/portal.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("loadScenarioCatalog")))
                .andExpect(content().string(containsString("\"Content-Type\": \"application/json\"")))
                .andExpect(content().string(containsString("Request setup")))
                .andExpect(content().string(containsString("Actual result summary")))
                .andExpect(content().string(containsString("scopeIdempotencyKey")))
                .andExpect(content().string(containsString("paymentStatusFrom")))
                .andExpect(content().string(containsString("latest?.expected?.eventStatuses")));
    }
}
