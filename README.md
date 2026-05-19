# BaaS Payment API Sandbox

A local Spring Boot sandbox for Banking-as-a-Service A2A payment onboarding. It gives partner developers a safe place to exercise payment creation, status lookup, lifecycle events, idempotency, mock OAuth authorization, BaaS requester context headers, and deterministic failure scenarios before connecting to real payment rails.

The project also includes a lightweight browser portal for UAT runbook execution. The portal renders backend-owned scenario metadata, lets a developer run scenarios directly against the sandbox API, copies curl-ready requests for external tools, and stores latest evidence in the browser session.

## High-Level Purpose

The sandbox is intended for product exploration, integration testing, and partner onboarding. It focuses on predictable contract behavior rather than production payment processing.

Use it to:

- Learn the BaaS payment API shape without touching live banking systems.
- Test positive, negative, and boundary payment flows.
- Verify client handling for authentication, requester context, validation, idempotency, and payment-domain failures.
- Capture lightweight UAT evidence during onboarding runs.
- Inspect the API through OpenAPI/Swagger UI.

This is not a production rail, ledger, settlement engine, OAuth server, or persistent evidence store.

## Design

The application is a Spring Boot 3 / Java 21 Maven service with a small static portal served by the same app.

Core design choices:

- `PaymentController` exposes REST endpoints under `/api/v1/payments`.
- `PaymentService` owns payment lifecycle behavior, failure simulation, idempotency checks, and lookup behavior.
- `PaymentRepository` hides persistence behind an interface; the current implementation is in-memory and resets on restart.
- `MockOAuthFilter` accepts static sandbox bearer tokens and returns structured auth errors.
- `BaasContextFilter` requires lightweight partner/customer/consent context headers on protected payment API calls.
- `SandboxScenarioService` defines the UAT scenario catalog used by the portal.
- Static assets in `src/main/resources/static` render the browser runbook at `/`.

The implementation favors deterministic behavior: scenario failures are triggered explicitly with `X-Sandbox-Scenario`, and idempotent replay is controlled by `Idempotency-Key`.

## Conceptual UI Layout

The sandbox portal is designed as a focused UAT workspace, not a marketing page. The first screen should help a partner developer choose a scenario, understand the required request, execute it, and collect evidence without switching context.

![UAT runbook workspace wireframe](docs/images/uat-runbook-workspace.png)

Source wireframe: [Figma UX design](https://www.figma.com/design/H6sLRERJ0dYKzPo7iTmqId).

### Partner Developer Journey

The Figma document also captures the intended onboarding journey: discover the sandbox, prepare the request context, execute required scenarios, review evidence, and complete UAT progress.

![Partner developer UAT journey map](docs/images/partner-developer-uat-journey-map.png)

UX intent:

- Left rail: scenario navigation grouped by positive, negative, and boundary coverage, with completion status visible while scanning.
- Center workspace: selected scenario purpose, ordered steps, request setup, expected result, and action controls.
- Right rail: latest evidence summary, expected-match status, payment ID, response body, lifecycle events, and manual completion for external Postman/curl runs.
- Top bar: base URL awareness, direct OpenAPI access, and progress reset.

The layout keeps the partner's UAT loop tight: select a scenario, run or copy it, compare actual result with expected outcome, and record lightweight evidence.

## API Surface

Primary endpoints:

- `POST /api/v1/payments` creates an A2A payment.
- `GET /api/v1/payments/{paymentId}` returns a stored payment.
- `GET /api/v1/payments/{paymentId}/events` returns lifecycle events.
- `GET /api/v1/sandbox/scenarios` returns portal scenario metadata.
- `GET /actuator/health` returns service health.
- `GET /v3/api-docs` returns the OpenAPI document.
- `GET /swagger-ui.html` opens Swagger UI.
- `GET /` opens the UAT runbook portal.

Payment API calls require:

```http
Authorization: Bearer sandbox-payment-token
X-Partner-Id: fintech-partner-001
X-On-Behalf-Of-Customer-Id: bank-customer-456
X-Customer-Consent-Id: consent-payment-uat-001
```

Optional payment creation headers:

```http
Idempotency-Key: uat-successful-payment-001
X-Sandbox-Scenario: insufficient_funds
```

Mock tokens:

- `sandbox-payment-token` has payment write access.
- `sandbox-readonly-token` is intentionally insufficient for protected payment calls and is used by negative UAT scenarios.

## Sandbox Scenarios

The runbook covers positive, negative, and boundary scenarios:

- Positive: successful payment, payment lookup, lifecycle events lookup.
- Negative: missing token, invalid token, insufficient scope, insufficient funds, invalid beneficiary, authorization rejected, duplicate payment.
- Boundary: minimum valid amount, invalid amount, invalid currency, idempotent replay, idempotency conflict.

Failure simulation values for `X-Sandbox-Scenario`:

- `insufficient_funds`
- `invalid_beneficiary`
- `authorization_rejected`
- `duplicate_payment`

All errors use a structured envelope with a stable `code`, human-readable `message`, and optional `details`.

## Run Locally

Prerequisites:

- Java 21
- Maven

Start the service:

```powershell
mvn spring-boot:run
```

Then open:

- Portal: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Example Request

```powershell
curl -X POST "http://localhost:8080/api/v1/payments" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer sandbox-payment-token" `
  -H "X-Partner-Id: fintech-partner-001" `
  -H "X-On-Behalf-Of-Customer-Id: bank-customer-456" `
  -H "X-Customer-Consent-Id: consent-payment-uat-001" `
  -H "Idempotency-Key: demo-payment-001" `
  -d '{"debtorAccountId":"acct-debtor-001","creditorAccountId":"acct-creditor-001","amount":125.50,"currency":"USD","reference":"uat-runbook"}'
```

## Test And Validate

Run the automated test suite:

```powershell
mvn test
```

Validate the active OpenSpec change:

```powershell
npx.cmd openspec validate add-payment-api-sandbox --strict
```

## Documentation Map

Useful project documents:

- [Payment API sandbox change](openspec/changes/add-payment-api-sandbox/README.md)
- [Payment API sandbox design](openspec/changes/add-payment-api-sandbox/design.md)
- [Payment API sandbox tasks](openspec/changes/add-payment-api-sandbox/tasks.md)
- [UAT runbook portal change](openspec/changes/archive/2026-05-17-add-uat-runbook-portal/README.md)
- [Current payment API spec](openspec/specs/payment-api-sandbox/spec.md)
- [Current portal spec](openspec/specs/uat-runbook-portal/spec.md)
- [Current scenario metadata spec](openspec/specs/sandbox-scenario-metadata/spec.md)

Recommended next documents:

- `docs/api-quickstart.md` for a concise partner-facing API walkthrough.
- `docs/uat-runbook.md` for onboarding evidence expectations and scenario sign-off guidance.
- `docs/architecture.md` for a longer-lived architecture record once persistence, real OAuth, or webhooks are introduced.

## Current Limitations

- In-memory storage resets when the app restarts.
- Mock OAuth tokens are static and sandbox-only.
- Payment lifecycle transitions are synchronous.
- The portal stores evidence only in browser session storage.
- There is no real account balance, ledger, settlement, reconciliation, webhook delivery, or partner authorization store.
