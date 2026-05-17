# UAT Runbook Portal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot-served developer sandbox portal that guides partner developers through BaaS payment UAT scenarios using backend-owned scenario metadata, direct portal execution, external request helpers, and browser-local evidence.

**Architecture:** Keep the existing Payment API as the system under test. Add a small scenario metadata backend surface and a static vanilla HTML/CSS/JS portal served by Spring Boot. Add lightweight BaaS requester-context header validation to payment endpoints so the sandbox models partner-on-behalf-of-customer API calls without introducing real JWT parsing or persisted authorization state.

**Tech Stack:** Java 21, Spring Boot 3.3.6, Maven, Spring MVC, Spring Security filter chain, MockMvc tests, static HTML/CSS/JavaScript under `src/main/resources/static`.

---

## OpenSpec Alignment

Source OpenSpec change: `openspec/changes/add-uat-runbook-portal`.

| OpenSpec task | Implementation task |
| --- | --- |
| 1.1 Scenario metadata models including BaaS headers | Task 2 |
| 1.2 Scenario metadata service | Task 2 |
| 1.3 Scenario metadata endpoint | Task 2 |
| 1.4 Metadata endpoint tests | Task 2 |
| 1.5 BaaS requester context validation | Task 1 |
| 2.1 Portal entry point | Task 3 |
| 2.2 Runbook workspace layout | Task 3 |
| 2.3 Load metadata from backend | Task 4 |
| 2.4 Dense UAT workspace styling | Task 3 |
| 3.1 Portal-run ordered scenario steps | Task 5 |
| 3.2 Fetch/display lifecycle events | Task 5 |
| 3.3 Compare actual vs expected | Task 5 |
| 3.4 Copyable request text per step | Task 4 |
| 4.1 Local evidence capture | Task 5 |
| 4.2 Auto-complete expected matches | Task 5 |
| 4.3 Manual external completion | Task 5 |
| 4.4 Browser/session-only progress | Task 5 |
| 5.1 Portal/static/metadata/BaaS tests | Tasks 1, 2, 3 |
| 5.2 Scenario comparison behavior tests | Task 5 |
| 5.3 `mvn test` and OpenSpec validate | Task 6 |

## File Structure

- Modify `src/main/java/com/example/baas/sandbox/config/SecurityConfig.java`: permit scenario metadata and static portal routes; register BaaS context filter after mock OAuth.
- Modify `src/main/java/com/example/baas/sandbox/security/MockOAuthFilter.java`: skip static portal and scenario metadata paths.
- Create `src/main/java/com/example/baas/sandbox/security/BaasContextFilter.java`: validate required BaaS requester context headers for protected payment API calls.
- Create `src/main/java/com/example/baas/sandbox/scenario/ScenarioCatalog.java`: top-level metadata response.
- Create `src/main/java/com/example/baas/sandbox/scenario/SandboxScenario.java`: scenario metadata record.
- Create `src/main/java/com/example/baas/sandbox/scenario/ScenarioStep.java`: executable step metadata.
- Create `src/main/java/com/example/baas/sandbox/scenario/ExpectedOutcome.java`: expected result metadata.
- Create `src/main/java/com/example/baas/sandbox/scenario/SandboxScenarioService.java`: backend-owned scenario catalog.
- Create `src/main/java/com/example/baas/sandbox/api/SandboxScenarioController.java`: `GET /api/v1/sandbox/scenarios`.
- Create `src/main/resources/static/index.html`: portal entry point and runbook workspace markup.
- Create `src/main/resources/static/portal.css`: dense developer workspace styling.
- Create `src/main/resources/static/portal.js`: metadata loading, scenario rendering, copy helpers, ordered execution, expected matching, local/session evidence.
- Modify `src/test/java/com/example/baas/sandbox/PaymentApiSandboxApplicationTests.java`: update existing payment calls with BaaS headers and add missing-context test.
- Create `src/test/java/com/example/baas/sandbox/SandboxScenarioMetadataTests.java`: metadata endpoint tests.
- Create `src/test/java/com/example/baas/sandbox/PortalStaticAssetsTests.java`: static portal asset availability tests.

---

### Task 1: BaaS Requester Context Validation

**Files:**
- Create: `src/main/java/com/example/baas/sandbox/security/BaasContextFilter.java`
- Modify: `src/main/java/com/example/baas/sandbox/config/SecurityConfig.java`
- Modify: `src/main/java/com/example/baas/sandbox/security/MockOAuthFilter.java`
- Modify: `src/test/java/com/example/baas/sandbox/PaymentApiSandboxApplicationTests.java`

- [ ] **Step 1: Add test helpers and update existing payment tests with BaaS headers**

In `PaymentApiSandboxApplicationTests`, add a helper that applies the headers:

```java
private static MockHttpServletRequestBuilder withBaasContext(MockHttpServletRequestBuilder request) {
    return request
            .header("Authorization", "Bearer sandbox-payment-token")
            .header("X-Partner-Id", "fintech-partner-001")
            .header("X-On-Behalf-Of-Customer-Id", "bank-customer-456")
            .header("X-Customer-Consent-Id", "consent-payment-uat-001")
            .header("X-Request-Id", "req-test-001");
}
```

Replace successful protected calls like:

```java
mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer sandbox-payment-token")
                .header("Idempotency-Key", "idem-success-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT_JSON))
```

with:

```java
mockMvc.perform(withBaasContext(post("/api/v1/payments"))
                .header("Idempotency-Key", "idem-success-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT_JSON))
```

- [ ] **Step 2: Add failing test for missing BaaS context**

Add this test to `PaymentApiSandboxApplicationTests`:

```java
@Test
void rejectsMissingBaasRequesterContext() throws Exception {
    mockMvc.perform(post("/api/v1/payments")
                    .header("Authorization", "Bearer sandbox-payment-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PAYMENT_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAAS_CONTEXT_REQUIRED"))
            .andExpect(jsonPath("$.details.missingHeaders", hasSize(3)));
}
```

- [ ] **Step 3: Run focused tests and confirm failure**

Run:

```powershell
mvn test -Dtest=PaymentApiSandboxApplicationTests
```

Expected: `rejectsMissingBaasRequesterContext` fails because missing BaaS context is not yet validated.

- [ ] **Step 4: Implement BaaS context filter**

Create `src/main/java/com/example/baas/sandbox/security/BaasContextFilter.java`:

```java
package com.example.baas.sandbox.security;

import com.example.baas.sandbox.api.ErrorEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BaasContextFilter extends OncePerRequestFilter {

    public static final String PARTNER_ID = "X-Partner-Id";
    public static final String CUSTOMER_ID = "X-On-Behalf-Of-Customer-Id";
    public static final String CONSENT_ID = "X-Customer-Consent-Id";

    private static final List<String> REQUIRED_HEADERS = List.of(PARTNER_ID, CUSTOMER_ID, CONSENT_ID);

    private final ObjectMapper objectMapper;

    public BaasContextFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/payments");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> isBlank(request.getHeader(header)))
                .toList();

        if (!missingHeaders.isEmpty()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorEnvelope.of(
                    "BAAS_CONTEXT_REQUIRED",
                    "BaaS requester context headers are required",
                    Map.of("missingHeaders", missingHeaders)));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

- [ ] **Step 5: Register filter and permit portal/scenario routes**

Update `SecurityConfig`:

```java
package com.example.baas.sandbox.config;

import com.example.baas.sandbox.security.BaasContextFilter;
import com.example.baas.sandbox.security.MockOAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            MockOAuthFilter mockOAuthFilter,
            BaasContextFilter baasContextFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/portal.css",
                                "/portal.js",
                                "/api/v1/sandbox/scenarios",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(mockOAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(baasContextFilter, MockOAuthFilter.class)
                .build();
    }
}
```

Update `MockOAuthFilter.shouldNotFilter` to skip portal and scenario metadata:

```java
return path.equals("/")
        || path.equals("/index.html")
        || path.equals("/portal.css")
        || path.equals("/portal.js")
        || path.equals("/api/v1/sandbox/scenarios")
        || path.equals("/actuator/health")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui")
        || path.equals("/swagger-ui.html");
```

- [ ] **Step 6: Run focused tests and confirm pass**

Run:

```powershell
mvn test -Dtest=PaymentApiSandboxApplicationTests
```

Expected: all tests in `PaymentApiSandboxApplicationTests` pass.

- [ ] **Step 7: Commit**

```powershell
git add -- src/main/java/com/example/baas/sandbox/config/SecurityConfig.java src/main/java/com/example/baas/sandbox/security/MockOAuthFilter.java src/main/java/com/example/baas/sandbox/security/BaasContextFilter.java src/test/java/com/example/baas/sandbox/PaymentApiSandboxApplicationTests.java
git commit -m "feat: require baas requester context headers"
```

---

### Task 2: Scenario Metadata Backend

**Files:**
- Create: `src/main/java/com/example/baas/sandbox/scenario/ScenarioCatalog.java`
- Create: `src/main/java/com/example/baas/sandbox/scenario/SandboxScenario.java`
- Create: `src/main/java/com/example/baas/sandbox/scenario/ScenarioStep.java`
- Create: `src/main/java/com/example/baas/sandbox/scenario/ExpectedOutcome.java`
- Create: `src/main/java/com/example/baas/sandbox/scenario/SandboxScenarioService.java`
- Create: `src/main/java/com/example/baas/sandbox/api/SandboxScenarioController.java`
- Create: `src/test/java/com/example/baas/sandbox/SandboxScenarioMetadataTests.java`

- [ ] **Step 1: Write metadata endpoint tests**

Create `SandboxScenarioMetadataTests`:

```java
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
                .andExpect(jsonPath("$.scenarios[0].steps[0].headers['X-Partner-Id']").value("fintech-partner-001"))
                .andExpect(jsonPath("$.scenarios[0].steps[0].headers['X-On-Behalf-Of-Customer-Id']").value("bank-customer-456"))
                .andExpect(jsonPath("$.scenarios[0].steps[0].headers['X-Customer-Consent-Id']").value("consent-payment-uat-001"));
    }

    @Test
    void representsMultiStepScenariosWithCaptureRules() throws Exception {
        mockMvc.perform(get("/api/v1/sandbox/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios[?(@.id == 'payment-lookup')].steps[0].length()").value(2))
                .andExpect(jsonPath("$.scenarios[?(@.id == 'payment-lookup')].steps[0][0].capture.paymentId").value("$.paymentId"))
                .andExpect(jsonPath("$.scenarios[?(@.id == 'payment-lookup')].steps[0][1].path").value("/api/v1/payments/{paymentId}"));
    }
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run:

```powershell
mvn test -Dtest=SandboxScenarioMetadataTests
```

Expected: FAIL because controller and scenario records do not exist.

- [ ] **Step 3: Add metadata records**

Create `ScenarioCatalog`:

```java
package com.example.baas.sandbox.scenario;

import java.util.List;

public record ScenarioCatalog(
        String baseUrl,
        AuthMetadata auth,
        List<String> categories,
        List<SandboxScenario> scenarios
) {
    public record AuthMetadata(
            String type,
            String tokenHint,
            String readonlyTokenHint,
            String headerName
    ) {
    }
}
```

Create `SandboxScenario`:

```java
package com.example.baas.sandbox.scenario;

import java.util.List;

public record SandboxScenario(
        String id,
        String title,
        String category,
        String purpose,
        List<String> preconditions,
        String dependsOn,
        List<ScenarioStep> steps,
        List<String> evidenceFields
) {
}
```

Create `ScenarioStep`:

```java
package com.example.baas.sandbox.scenario;

import java.util.Map;

public record ScenarioStep(
        String id,
        String title,
        String method,
        String path,
        Map<String, String> headers,
        Map<String, Object> body,
        ExpectedOutcome expected,
        Map<String, String> capture
) {
}
```

Create `ExpectedOutcome`:

```java
package com.example.baas.sandbox.scenario;

import java.util.List;

public record ExpectedOutcome(
        int httpStatus,
        String paymentStatus,
        String errorCode,
        List<String> eventStatuses
) {
}
```

- [ ] **Step 4: Implement scenario service**

Create `SandboxScenarioService` with helper methods for headers, payment bodies, expected outcomes, and scenarios. Include all required IDs from the test. Use `List.of(...)` and `Map.of(...)` only; no persistence.

```java
package com.example.baas.sandbox.scenario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SandboxScenarioService {

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
                        authScenario("invalid-token", "Invalid bearer token", headers("Bearer unknown", null, null), 401, "AUTH_INVALID"),
                        authScenario("insufficient-scope", "Insufficient payment scope", headers("Bearer sandbox-readonly-token", null, null), 403, "INSUFFICIENT_SCOPE"),
                        failureScenario("insufficient-funds", "Insufficient funds", "insufficient_funds", 422, "INSUFFICIENT_FUNDS"),
                        failureScenario("invalid-beneficiary", "Invalid beneficiary", "invalid_beneficiary", 422, "INVALID_BENEFICIARY"),
                        failureScenario("authorization-rejected", "Authorization rejected", "authorization_rejected", 403, "AUTHORIZATION_REJECTED"),
                        failureScenario("duplicate-payment", "Duplicate payment", "duplicate_payment", 409, "DUPLICATE_PAYMENT"),
                        minimumValidAmount(),
                        validationScenario("invalid-amount", "Invalid amount", paymentBody(new BigDecimal("0.00"), "USD"), "VALIDATION_ERROR"),
                        validationScenario("invalid-currency", "Invalid currency", paymentBody(new BigDecimal("10.00"), "US"), "VALIDATION_ERROR"),
                        idempotentReplay(),
                        idempotencyConflict()));
    }

    private SandboxScenario successfulPayment() {
        return scenario("successful-payment", "Successful A2A payment initialization", "positive",
                "Proves the partner can initialize a valid A2A payment and receive a payment identifier.",
                List.of(step("create-payment", "Create payment", "POST", "/api/v1/payments",
                        headers("Bearer sandbox-payment-token", "req-uat-successful-payment-001", "uat-successful-payment-001"),
                        paymentBody(new BigDecimal("125.50"), "USD"),
                        expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                        Map.of("paymentId", "$.paymentId"))));
    }

    private SandboxScenario paymentLookup() {
        return scenario("payment-lookup", "Payment lookup", "positive",
                "Proves the partner can retrieve a created payment by identifier.",
                List.of(
                        step("create-payment", "Create payment", "POST", "/api/v1/payments",
                                headers("Bearer sandbox-payment-token", "req-uat-lookup-create", "uat-payment-lookup-001"),
                                paymentBody(new BigDecimal("42.00"), "USD"),
                                expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
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
                                expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of("paymentId", "$.paymentId")),
                        step("get-events", "Get lifecycle events", "GET", "/api/v1/payments/{paymentId}/events",
                                headers("Bearer sandbox-payment-token", "req-uat-events-get", null),
                                Map.of(),
                                expected(200, null, null, List.of("RECEIVED", "VALIDATED", "EXECUTED")),
                                Map.of())));
    }

    private SandboxScenario failureScenario(String id, String title, String scenarioHeader, int status, String code) {
        Map<String, String> headers = new java.util.LinkedHashMap<>(headers("Bearer sandbox-payment-token", "req-uat-" + id, null));
        headers.put("X-Sandbox-Scenario", scenarioHeader);
        return scenario(id, title, "negative", "Proves the partner handles " + title.toLowerCase() + " responses.",
                List.of(step("create-payment", title, "POST", "/api/v1/payments", headers,
                        paymentBody(new BigDecimal("125.50"), "USD"), expected(status, null, code, List.of()), Map.of())));
    }

    private SandboxScenario authScenario(String id, String title, Map<String, String> headers, int status, String code) {
        return scenario(id, title, "negative", "Proves the partner handles " + title.toLowerCase() + ".",
                List.of(step("create-payment", title, "POST", "/api/v1/payments", headers,
                        paymentBody(new BigDecimal("125.50"), "USD"), expected(status, null, code, List.of()), Map.of())));
    }

    private SandboxScenario minimumValidAmount() {
        return scenario("minimum-valid-amount", "Minimum valid amount", "boundary",
                "Proves the partner can submit the smallest supported payment amount.",
                List.of(step("create-payment", "Create minimum amount payment", "POST", "/api/v1/payments",
                        headers("Bearer sandbox-payment-token", "req-uat-minimum-amount", "uat-minimum-amount-001"),
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
        Map<String, String> headers = headers("Bearer sandbox-payment-token", "req-uat-idem-replay", "uat-idem-replay-001");
        return scenario("idempotent-replay", "Idempotent replay", "boundary",
                "Proves the partner can safely replay the same create request with the same idempotency key.",
                List.of(
                        step("first-create", "First create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("77.00"), "USD"), expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")), Map.of("paymentId", "$.paymentId")),
                        step("replay-create", "Replay create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("77.00"), "USD"), expected(200, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")), Map.of())));
    }

    private SandboxScenario idempotencyConflict() {
        Map<String, String> headers = headers("Bearer sandbox-payment-token", "req-uat-idem-conflict", "uat-idem-conflict-001");
        return scenario("idempotency-conflict", "Idempotency conflict", "boundary",
                "Proves the partner handles a reused idempotency key with a different payload.",
                List.of(
                        step("first-create", "First create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("88.00"), "USD"), expected(201, "EXECUTED", null, List.of("RECEIVED", "VALIDATED", "EXECUTED")), Map.of()),
                        step("conflicting-create", "Conflicting create", "POST", "/api/v1/payments", headers,
                                paymentBody(new BigDecimal("89.00"), "USD"), expected(409, null, "IDEMPOTENCY_CONFLICT", List.of()), Map.of())));
    }

    private SandboxScenario scenario(String id, String title, String category, String purpose, List<ScenarioStep> steps) {
        return new SandboxScenario(id, title, category, purpose, List.of(), null, steps,
                List.of("timestamp", "request", "httpStatus", "responseBody", "paymentId", "expectedMatch"));
    }

    private ScenarioStep step(String id, String title, String method, String path, Map<String, String> headers,
                              Map<String, Object> body, ExpectedOutcome expected, Map<String, String> capture) {
        return new ScenarioStep(id, title, method, path, headers, body, expected, capture);
    }

    private ExpectedOutcome expected(int httpStatus, String paymentStatus, String errorCode, List<String> eventStatuses) {
        return new ExpectedOutcome(httpStatus, paymentStatus, errorCode, eventStatuses);
    }

    private Map<String, String> headers(String authorization, String requestId, String idempotencyKey) {
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
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
```

- [ ] **Step 5: Add controller**

Create `SandboxScenarioController`:

```java
package com.example.baas.sandbox.api;

import com.example.baas.sandbox.scenario.SandboxScenarioService;
import com.example.baas.sandbox.scenario.ScenarioCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sandbox/scenarios")
public class SandboxScenarioController {

    private final SandboxScenarioService scenarioService;

    public SandboxScenarioController(SandboxScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    ScenarioCatalog scenarios() {
        return scenarioService.catalog();
    }
}
```

- [ ] **Step 6: Run metadata tests and confirm pass**

Run:

```powershell
mvn test -Dtest=SandboxScenarioMetadataTests
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```powershell
git add -- src/main/java/com/example/baas/sandbox/scenario src/main/java/com/example/baas/sandbox/api/SandboxScenarioController.java src/test/java/com/example/baas/sandbox/SandboxScenarioMetadataTests.java
git commit -m "feat: expose sandbox scenario metadata"
```

---

### Task 3: Portal Shell And Static Assets

**Files:**
- Create: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/portal.css`
- Create: `src/main/resources/static/portal.js`
- Create: `src/test/java/com/example/baas/sandbox/PortalStaticAssetsTests.java`

- [ ] **Step 1: Write static asset tests**

Create `PortalStaticAssetsTests`:

```java
package com.example.baas.sandbox;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(content().string(containsString("loadScenarioCatalog")));
    }
}
```

- [ ] **Step 2: Run static tests and confirm failure**

Run:

```powershell
mvn test -Dtest=PortalStaticAssetsTests
```

Expected: FAIL because static assets do not exist.

- [ ] **Step 3: Create portal HTML**

Create `index.html` with the first screen as the runbook workspace. Include these required landmarks:

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>BaaS Payment Sandbox</title>
  <link rel="stylesheet" href="/portal.css">
</head>
<body>
  <header class="topbar">
    <div>
      <h1>BaaS Payment Sandbox</h1>
      <p>UAT runbook workspace for partner payment API onboarding</p>
    </div>
    <div class="topbar-actions">
      <span id="base-url">Base URL loading</span>
      <a href="/swagger-ui.html">OpenAPI</a>
      <button id="reset-progress" type="button">Reset</button>
    </div>
  </header>

  <main class="workspace">
    <aside id="scenario-runbook" class="panel scenario-runbook" aria-label="Scenario runbook">
      <div class="panel-heading">
        <h2>Scenario Runbook</h2>
        <span id="progress-summary">0/0 complete</span>
      </div>
      <div id="scenario-list"></div>
    </aside>

    <section id="scenario-detail" class="panel scenario-detail" aria-label="Scenario detail">
      <div class="panel-heading">
        <h2 id="scenario-title">Select a scenario</h2>
        <span id="scenario-category"></span>
      </div>
      <p id="scenario-purpose">Choose a scenario from the runbook.</p>
      <div id="scenario-steps"></div>
      <div class="actions">
        <button id="run-scenario" type="button">Run scenario</button>
        <button id="copy-request" type="button">Copy request</button>
      </div>
      <pre id="copy-output"></pre>
    </section>

    <aside id="evidence-panel" class="panel evidence-panel" aria-label="Evidence panel">
      <div class="panel-heading">
        <h2>Evidence</h2>
        <span id="completion-status">Not complete</span>
      </div>
      <div id="evidence-content">No run captured yet. Evidence is local to this browser session.</div>
      <button id="manual-complete" type="button">Mark external run complete</button>
    </aside>
  </main>

  <script src="/portal.js"></script>
</body>
</html>
```

- [ ] **Step 4: Create portal CSS**

Create `portal.css` with dense three-panel layout, responsive stacking, compact typography, and no nested card styling:

```css
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: Arial, Helvetica, sans-serif;
  color: #1f2937;
  background: #f5f7fb;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 72px;
  padding: 14px 20px;
  border-bottom: 1px solid #d8dee9;
  background: #ffffff;
}

.topbar h1,
.panel h2 {
  margin: 0;
  letter-spacing: 0;
}

.topbar h1 {
  font-size: 20px;
}

.topbar p {
  margin: 4px 0 0;
  color: #526070;
}

.topbar-actions,
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

button,
a {
  min-height: 36px;
  border: 1px solid #aeb8c6;
  border-radius: 6px;
  padding: 8px 12px;
  background: #ffffff;
  color: #15324f;
  text-decoration: none;
  cursor: pointer;
}

button.primary,
#run-scenario {
  border-color: #1f6feb;
  background: #1f6feb;
  color: #ffffff;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(420px, 1.5fr) minmax(300px, 1fr);
  gap: 12px;
  padding: 12px;
}

.panel {
  min-height: calc(100vh - 96px);
  border: 1px solid #d8dee9;
  border-radius: 8px;
  background: #ffffff;
  padding: 14px;
  overflow: auto;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.category-title {
  margin: 14px 0 6px;
  font-size: 12px;
  text-transform: uppercase;
  color: #667085;
}

.scenario-button {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 8px;
  width: 100%;
  margin-bottom: 6px;
  text-align: left;
}

.scenario-button[aria-selected="true"] {
  border-color: #1f6feb;
  background: #eef5ff;
}

.status-dot {
  width: 10px;
  height: 10px;
  margin-top: 4px;
  border-radius: 50%;
  background: #aeb8c6;
}

.status-dot.complete {
  background: #0f8a5f;
}

.step {
  border-top: 1px solid #e5eaf0;
  padding: 12px 0;
}

pre {
  max-width: 100%;
  overflow: auto;
  border: 1px solid #e5eaf0;
  border-radius: 6px;
  padding: 10px;
  background: #f8fafc;
  white-space: pre-wrap;
  word-break: break-word;
}

.match-pass {
  color: #0f8a5f;
  font-weight: 700;
}

.match-fail {
  color: #b42318;
  font-weight: 700;
}

@media (max-width: 980px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .panel {
    min-height: auto;
  }
}
```

- [ ] **Step 5: Create minimal portal JS placeholder**

Create `portal.js` with a load function stub so static tests pass; Task 4 and Task 5 expand it.

```javascript
const state = {
  catalog: null,
  selectedScenarioId: null,
  evidence: JSON.parse(sessionStorage.getItem("uatEvidence") || "{}")
};

document.addEventListener("DOMContentLoaded", () => {
  loadScenarioCatalog();
});

async function loadScenarioCatalog() {
  const response = await fetch("/api/v1/sandbox/scenarios");
  state.catalog = await response.json();
  document.getElementById("base-url").textContent = state.catalog.baseUrl;
}
```

- [ ] **Step 6: Run static tests and confirm pass**

Run:

```powershell
mvn test -Dtest=PortalStaticAssetsTests
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```powershell
git add -- src/main/resources/static/index.html src/main/resources/static/portal.css src/main/resources/static/portal.js src/test/java/com/example/baas/sandbox/PortalStaticAssetsTests.java
git commit -m "feat: add uat runbook portal shell"
```

---

### Task 4: Portal Metadata Rendering And Copy Helpers

**Files:**
- Modify: `src/main/resources/static/portal.js`

- [ ] **Step 1: Replace portal JS with metadata rendering**

Implement `loadScenarioCatalog`, `renderScenarioList`, `selectScenario`, `renderScenarioDetail`, and `buildCurl`. Keep all scenario definitions backend-owned.

```javascript
const state = {
  catalog: null,
  selectedScenarioId: null,
  evidence: JSON.parse(sessionStorage.getItem("uatEvidence") || "{}")
};

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("reset-progress").addEventListener("click", resetProgress);
  document.getElementById("copy-request").addEventListener("click", copySelectedScenario);
  document.getElementById("manual-complete").addEventListener("click", markManualComplete);
  document.getElementById("run-scenario").addEventListener("click", runSelectedScenario);
  loadScenarioCatalog();
});

async function loadScenarioCatalog() {
  const response = await fetch("/api/v1/sandbox/scenarios");
  state.catalog = await response.json();
  document.getElementById("base-url").textContent = state.catalog.baseUrl;
  state.selectedScenarioId = state.catalog.scenarios[0]?.id || null;
  renderScenarioList();
  renderSelectedScenario();
}

function renderScenarioList() {
  const host = document.getElementById("scenario-list");
  host.innerHTML = "";
  for (const category of state.catalog.categories) {
    const title = document.createElement("h3");
    title.className = "category-title";
    title.textContent = category;
    host.appendChild(title);

    state.catalog.scenarios
      .filter((scenario) => scenario.category === category)
      .forEach((scenario) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "scenario-button";
        button.setAttribute("aria-selected", String(scenario.id === state.selectedScenarioId));
        button.addEventListener("click", () => {
          state.selectedScenarioId = scenario.id;
          renderScenarioList();
          renderSelectedScenario();
        });

        const dot = document.createElement("span");
        dot.className = `status-dot ${state.evidence[scenario.id]?.complete ? "complete" : ""}`;
        const label = document.createElement("span");
        label.textContent = scenario.title;
        button.append(dot, label);
        host.appendChild(button);
      });
  }
  renderProgress();
}

function renderProgress() {
  const total = state.catalog.scenarios.length;
  const complete = state.catalog.scenarios.filter((scenario) => state.evidence[scenario.id]?.complete).length;
  document.getElementById("progress-summary").textContent = `${complete}/${total} complete`;
}

function selectedScenario() {
  return state.catalog.scenarios.find((scenario) => scenario.id === state.selectedScenarioId);
}

function renderSelectedScenario() {
  const scenario = selectedScenario();
  if (!scenario) {
    return;
  }

  document.getElementById("scenario-title").textContent = scenario.title;
  document.getElementById("scenario-category").textContent = scenario.category;
  document.getElementById("scenario-purpose").textContent = scenario.purpose;

  const steps = document.getElementById("scenario-steps");
  steps.innerHTML = "";
  scenario.steps.forEach((step, index) => {
    const section = document.createElement("section");
    section.className = "step";
    section.innerHTML = `
      <h3>${index + 1}. ${step.title}</h3>
      <p><strong>${step.method}</strong> ${step.path}</p>
      <h4>Headers</h4>
      <pre>${escapeHtml(JSON.stringify(step.headers, null, 2))}</pre>
      <h4>Body</h4>
      <pre>${escapeHtml(JSON.stringify(step.body || {}, null, 2))}</pre>
      <h4>Expected</h4>
      <pre>${escapeHtml(JSON.stringify(step.expected, null, 2))}</pre>
    `;
    steps.appendChild(section);
  });

  renderEvidence();
}

function copySelectedScenario() {
  const scenario = selectedScenario();
  const text = scenario.steps.map((step) => buildCurl(step)).join("\n\n");
  document.getElementById("copy-output").textContent = text;
  navigator.clipboard?.writeText(text);
}

function buildCurl(step) {
  const url = `${state.catalog.baseUrl}${step.path}`;
  const headers = Object.entries(step.headers || {})
    .map(([name, value]) => `  -H "${name}: ${value}"`)
    .join(" \\\n");
  const body = step.body && Object.keys(step.body).length > 0
    ? ` \\\n  -d '${JSON.stringify(step.body)}'`
    : "";
  return `curl -X ${step.method} "${url}" \\\n${headers}${body}`;
}

function renderEvidence() {
  const scenario = selectedScenario();
  const evidence = state.evidence[scenario.id];
  document.getElementById("completion-status").textContent = evidence?.complete ? "Complete" : "Not complete";
  document.getElementById("evidence-content").innerHTML = evidence
    ? `<pre>${escapeHtml(JSON.stringify(evidence, null, 2))}</pre>`
    : "No run captured yet. Evidence is local to this browser session.";
}

function saveEvidence() {
  sessionStorage.setItem("uatEvidence", JSON.stringify(state.evidence));
  renderScenarioList();
  renderEvidence();
}

function markManualComplete() {
  const scenario = selectedScenario();
  state.evidence[scenario.id] = {
    scenarioId: scenario.id,
    timestamp: new Date().toISOString(),
    complete: true,
    external: true,
    note: "Manually marked complete after external Postman/curl execution"
  };
  saveEvidence();
}

function resetProgress() {
  state.evidence = {};
  sessionStorage.removeItem("uatEvidence");
  renderScenarioList();
  renderEvidence();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
```

- [ ] **Step 2: Run Java tests**

Run:

```powershell
mvn test
```

Expected: all Java tests pass; this JS is verified later through browser smoke checks.

- [ ] **Step 3: Commit**

```powershell
git add -- src/main/resources/static/portal.js
git commit -m "feat: render scenario metadata in portal"
```

---

### Task 5: Portal Ordered Execution, Matching, And Local Evidence

**Files:**
- Modify: `src/main/resources/static/portal.js`

- [ ] **Step 1: Add ordered execution functions to portal JS**

Append these functions to `portal.js`, replacing the placeholder `runSelectedScenario` if present:

```javascript
async function runSelectedScenario() {
  const scenario = selectedScenario();
  const captures = {};
  const results = [];

  for (const step of scenario.steps) {
    const preparedStep = interpolateStep(step, captures);
    const response = await fetch(preparedStep.path, {
      method: preparedStep.method,
      headers: {
        "Content-Type": "application/json",
        ...(preparedStep.headers || {})
      },
      body: preparedStep.method === "GET" ? undefined : JSON.stringify(preparedStep.body || {})
    });

    const body = await readResponseBody(response);
    applyCaptures(step.capture || {}, body, captures);

    results.push({
      stepId: step.id,
      request: preparedStep,
      httpStatus: response.status,
      responseBody: body,
      expected: step.expected,
      match: matchesExpected(response.status, body, step.expected)
    });
  }

  const complete = results.every((result) => result.match);
  const paymentId = captures.paymentId || findPaymentId(results);

  state.evidence[scenario.id] = {
    scenarioId: scenario.id,
    timestamp: new Date().toISOString(),
    complete,
    expectedMatch: complete,
    paymentId,
    stepResults: results,
    localOnly: true
  };

  saveEvidence();
}

function interpolateStep(step, captures) {
  return {
    ...step,
    path: interpolateValue(step.path, captures),
    headers: interpolateObject(step.headers || {}, captures),
    body: interpolateObject(step.body || {}, captures)
  };
}

function interpolateObject(value, captures) {
  return JSON.parse(JSON.stringify(value), (key, item) => {
    if (typeof item === "string") {
      return interpolateValue(item, captures);
    }
    return item;
  });
}

function interpolateValue(value, captures) {
  return Object.entries(captures).reduce(
    (result, [name, capturedValue]) => result.replaceAll(`{${name}}`, capturedValue),
    value
  );
}

async function readResponseBody(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function applyCaptures(captureRules, body, captures) {
  for (const [name, path] of Object.entries(captureRules)) {
    if (path === "$.paymentId" && body.paymentId) {
      captures[name] = body.paymentId;
    }
  }
}

function matchesExpected(httpStatus, body, expected) {
  if (httpStatus !== expected.httpStatus) {
    return false;
  }
  if (expected.paymentStatus && body.status !== expected.paymentStatus) {
    return false;
  }
  if (expected.errorCode && body.code !== expected.errorCode) {
    return false;
  }
  if (expected.eventStatuses && expected.eventStatuses.length > 0) {
    const statuses = Array.isArray(body)
      ? body.map((event) => event.status)
      : (body.events || []).map((event) => event.status);
    return expected.eventStatuses.every((status, index) => statuses[index] === status);
  }
  return true;
}

function findPaymentId(results) {
  for (const result of results) {
    if (result.responseBody?.paymentId) {
      return result.responseBody.paymentId;
    }
  }
  return null;
}
```

- [ ] **Step 2: Start app and manually smoke test portal**

Run:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080/
```

Expected:
- Scenario list loads from `/api/v1/sandbox/scenarios`.
- Successful payment scenario runs and marks complete.
- Payment lookup scenario runs both steps and marks complete.
- Copy request shows curl text for each selected step.
- Manual complete marks a scenario complete in session storage.
- Reset clears local progress.

- [ ] **Step 3: Run Java verification**

Run:

```powershell
mvn test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```powershell
git add -- src/main/resources/static/portal.js
git commit -m "feat: execute portal runbook scenarios"
```

---

### Task 6: Final Verification And OpenSpec Progress

**Files:**
- Modify: `openspec/changes/add-uat-runbook-portal/tasks.md`

- [ ] **Step 1: Run full Java test suite**

Run:

```powershell
mvn test
```

Expected: build success with all tests passing.

- [ ] **Step 2: Validate OpenSpec change**

Run:

```powershell
npx.cmd openspec validate add-uat-runbook-portal --strict
```

Expected:

```text
Change 'add-uat-runbook-portal' is valid
```

- [ ] **Step 3: Mark OpenSpec tasks complete**

Update `openspec/changes/add-uat-runbook-portal/tasks.md` by changing every checkbox from `- [ ]` to `- [x]` after verification succeeds.

- [ ] **Step 4: Commit OpenSpec progress**

```powershell
git add -- openspec/changes/add-uat-runbook-portal/tasks.md
git commit -m "chore: mark uat portal openspec tasks complete"
```

## Plan Self-Review

- Spec coverage: every OpenSpec task in `tasks.md` maps to at least one plan task in the alignment table.
- Placeholder scan: all tasks include concrete file paths, commands, and code examples.
- Type consistency: scenario model names are consistent across records, service, controller, tests, and portal JavaScript.
- Scope check: implementation remains a PoC portal plus metadata and lightweight BaaS header validation; no persisted evidence, real OAuth, user accounts, or certification workflow.
