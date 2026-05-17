## Context

The payment API sandbox already exposes A2A payment creation, lookup, lifecycle events, mock OAuth, idempotency, OpenAPI docs, and deterministic scenario failures. During UAT onboarding, partner developers need a guided portal that turns those API capabilities into a scenario-based runbook with expected outcomes and lightweight evidence.

## Goals / Non-Goals

**Goals:**

- Provide a developer sandbox portal focused on UAT scenario completion, not a marketing landing page.
- Include a simple user journey map as part of UX/UI design scope so the portal is designed around partner developer onboarding behavior.
- Use a runbook workspace layout with scenario navigation, scenario details, execution helpers, expected outcomes, actual response, and evidence.
- Let the portal run scenarios directly against the local payment API and also provide copyable curl/Postman-style requests.
- Source scenario definitions from backend metadata so the portal does not drift from sandbox behavior.
- Store completion and evidence only in browser/session state for the PoC.

**Non-Goals:**

- Persisted UAT evidence, user accounts, partner workspaces, approvals, audit history, or certification workflows.
- Admin-authored scenario catalogs or test-plan management.
- Replacing Postman; Postman/curl remain optional execution surfaces.
- Real OAuth or production readiness scoring.

## Decisions

- Use a runbook workspace over a wizard or tabbed portal because the PoC's core value is repeated scenario execution and evidence inspection.
- Keep the scenario catalog backend-owned via a read-only metadata endpoint. This keeps scenario names, headers, expected HTTP statuses, expected business outcomes, and evidence guidance aligned with the API behavior.
- Keep evidence and completion local to the browser/session. This avoids storage, identity, retention, access-control, and audit decisions that would turn the PoC into a full UAT platform.
- Mark scenarios complete automatically when a portal-run actual result matches the expected outcome. Allow manual completion for external Postman/curl runs.
- Organize scenarios by positive, negative, and boundary categories so UAT coverage is obvious to partner developers.

## UX Journey Map

**Persona:** Partner developer integrating the Payment API during UAT.

**Objective:** Help the developer understand required scenarios, run or reproduce each scenario, compare actual behavior with expected outcomes, and capture enough local evidence to know their integration is ready for UAT review.

| Stage | Entry / Orientation | Setup | Scenario Execution | Evidence Review | Completion |
| --- | --- | --- | --- | --- | --- |
| Customer Actions | Opens the sandbox portal and confirms they are in the UAT payment sandbox | Reviews base URL, mock token, scenario categories, and required headers | Selects a scenario, runs it in the portal or copies the request to Postman/curl | Reviews HTTP status, response body, payment ID, lifecycle events, and expected-vs-actual match | Marks external runs complete or sees portal-run scenarios auto-complete |
| Touchpoints | Portal landing/workspace, OpenAPI link | Credentials panel, scenario metadata, request setup | Run button, copy request button, Payment API endpoints | Evidence panel, scenario status, lifecycle events endpoint | Scenario checklist/progress indicator |
| Customer Experience | Oriented, wants to know "what do I need to prove?" | Cautious but clearer because setup is explicit | Focused; wants fast feedback and copyable details | Confident when actual behavior matches expected outcome; blocked if mismatch is unclear | Reassured by visible progress, without needing formal certification workflow |
| UX Goals | Make UAT purpose obvious | Reduce setup ambiguity | Support both direct portal execution and developer-preferred external tools | Make evidence readable and tied to expected outcomes | Keep completion lightweight and local-only |
| Success Signals | Developer can identify required scenario coverage | Developer can find token, headers, and sample payload without support | Developer can run or copy every required scenario | Developer can explain success, failure, and boundary outcomes | Developer completes required scenarios in local progress |

**Primary pain points to design against:**

- Developers may confuse the portal with a generic API documentation page.
- Developers may not know which scenarios are required for UAT readiness.
- Developers may prefer Postman but still need scenario truth and expected outcomes.
- Developers need enough evidence to debug and discuss UAT results, without a heavyweight audit system.

**UX implications:**

- The first screen should be the runbook workspace, not a marketing overview.
- Scenario status and category coverage should always be visible.
- Expected outcome and actual result should be visually adjacent.
- Copyable external requests should be available without making external execution the only path.
- Evidence should be local, transparent, and framed as PoC onboarding support.

## Portal UI Layout

Use a desktop-first runbook workbench optimized for partner developers doing focused UAT work. The PoC does not need a marketing landing page or a broad dashboard; the first screen should immediately support scenario selection, execution, comparison, and evidence capture.

**Figma wireframe:** https://www.figma.com/design/H6sLRERJ0dYKzPo7iTmqId

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ Top Bar: BaaS Payment Sandbox | Base URL | Mock token hint | OpenAPI link   │
├──────────────────────┬────────────────────────────────┬────────────────────┤
│ Scenario Runbook     │ Scenario Detail                │ Evidence Panel     │
│                      │                                │                    │
│ Progress summary     │ Purpose                        │ Completion status  │
│ Positive             │ Required headers               │ Timestamp          │
│ - Successful payment │ Request body                   │ HTTP status        │
│ - Payment lookup     │ Expected result                │ Response body      │
│ - Events lookup      │ Pass criteria                  │ Payment ID         │
│ Negative             │                                │ Lifecycle events   │
│ - Auth failures      │ [Run scenario] [Copy request]  │ Expected match     │
│ - Business failures  │                                │ Manual complete    │
│ Boundary             │ Actual result summary          │                    │
└──────────────────────┴────────────────────────────────┴────────────────────┘
```

**Top bar**

- Show the product context as "BaaS Payment Sandbox" and keep the UAT purpose visible.
- Show API base URL and mock token guidance so setup is always close at hand.
- Link to `/v3/api-docs` or Swagger UI for developers who need full contract detail.

**Scenario runbook panel**

- Group scenarios by `positive`, `negative`, and `boundary`.
- Show completion state per scenario using compact status indicators.
- Show a progress summary such as `3/12 scenarios complete`.
- Keep the selected scenario obvious and stable while the developer compares details and evidence.

**Scenario detail panel**

- Show scenario purpose in UAT language: what this scenario proves about the integration.
- Show request setup: method, path, headers, and sample request body.
- Show expected result: HTTP status, payment status or error code, and expected lifecycle events.
- Provide primary `Run scenario` action for portal-run execution.
- Provide secondary `Copy request` action for curl/Postman-style external execution.
- Show a concise actual result summary after portal-run execution.

**Evidence panel**

- Show the latest locally captured evidence for the selected scenario.
- Include timestamp, request headers, request payload, HTTP status, response body, payment ID when present, lifecycle events when present, and expected-vs-actual match.
- Auto-mark complete when portal-run results match the expected outcome.
- Provide manual completion for external Postman/curl runs.
- Make clear that evidence is local to the browser/session and not persisted as audit history.

**Responsive behavior**

- Desktop is the primary design target for the PoC.
- On narrow screens, stack panels in this order: scenario runbook, scenario detail, evidence panel.
- Do not remove runbook or evidence features on small screens; only change layout.

## UX/UI Validation Approach

Use the OpenSpec design as the implementation contract and the Figma wireframe as the visual reference. Implementation does not need to be pixel-perfect, but it must preserve the runbook workspace structure and the UAT onboarding intent.

**Validation criteria:**

- The first screen is the runbook workspace, not a landing page.
- The scenario runbook, selected scenario detail, and evidence panel are visible together on desktop.
- Scenario categories, completion status, and progress summary are visible before running a scenario.
- The selected scenario shows purpose, request setup, expected outcome, run action, and copy request action.
- Portal-run execution shows actual HTTP status and response body.
- If a payment identifier is returned, lifecycle events are displayed in the evidence panel.
- Expected-vs-actual match is visible and drives automatic local completion.
- External Postman/curl use is supported through copyable request text and manual completion.
- Evidence is clearly labeled as local browser/session state, not persisted audit history.
- Narrow screens keep the same content but stack panels in runbook, detail, evidence order.

**Recommended implementation validation:**

- Add automated API tests for scenario metadata and portal asset availability.
- Add browser-level checks that load the portal and verify the key workspace regions exist.
- Capture desktop and narrow screenshots after implementation and compare them against the Figma wireframe at the structural level: panel presence, ordering, visible actions, and no overlapping text.
- Treat visual polish as iterative, but block completion if the implemented UI loses the scenario-first UAT workflow.

## Scenario Metadata API Shape

Expose a read-only catalog at `GET /api/v1/sandbox/scenarios`. The endpoint is intentionally part of the sandbox support surface, not the payment API contract itself. It should not require mock OAuth because the portal needs to load the runbook before the developer executes payment scenarios. Payment API calls still use the mock bearer tokens defined by each scenario step.

Use ordered `steps` for every scenario, including single-request scenarios. This keeps the metadata simple for the UI while still supporting lookup, events, idempotent replay, and idempotency conflict flows without adding special-case frontend logic.

## Integration With Existing Payment API

The existing payment lifecycle behavior does not need to change for the portal PoC. The portal should treat the current sandbox API as the system under test and layer a guided UAT experience on top of it.

One contract gap should be closed before implementation: this is a Banking-as-a-Service payment API, so calls should model a partner acting on behalf of a customer. The mock bearer token represents partner application authorization, while BaaS requester context headers represent the customer relationship and consent context for the request.

**Existing endpoints reused by the portal:**

- `POST /api/v1/payments` for payment initialization, validation failures, business failures, idempotency replay, and idempotency conflict.
- `GET /api/v1/payments/{paymentId}` for payment lookup scenarios.
- `GET /api/v1/payments/{paymentId}/events` for lifecycle evidence.
- `/v3/api-docs` or Swagger UI for full API contract reference.

**Existing sandbox controls reused by the portal:**

- `Authorization: Bearer sandbox-payment-token` for successful write scenarios.
- `Authorization: Bearer sandbox-readonly-token` for insufficient-scope scenarios.
- Missing or invalid bearer token values for authentication failure scenarios.
- `X-Sandbox-Scenario` for deterministic business failure simulation.
- `Idempotency-Key` for replay and conflict scenarios.

**BaaS requester context headers:**

- `X-Partner-Id`: identifies the partner making the API call.
- `X-On-Behalf-Of-Customer-Id`: identifies the customer represented by the partner.
- `X-Customer-Consent-Id`: identifies the sandbox consent or mandate that allows the partner to initiate the payment for that customer.
- `X-Request-Id`: optional trace identifier for support and evidence readability.

For v1, the sandbox should validate that `X-Partner-Id`, `X-On-Behalf-Of-Customer-Id`, and `X-Customer-Consent-Id` are present on payment API calls. The sandbox does not need a real partner-customer authorization graph yet; fixed sample values in scenario metadata are enough for UAT onboarding.

This header-based model is a PoC simplification of a production-like BaaS bearer token where partner tenant, on-behalf-of customer, user, audience, issuer, expiry, and payment scopes may be expressed as JWT claims. The portal should explain the headers as sandbox request context rather than production security design.

**New backend surface for this change:**

- Add only the read-only scenario metadata endpoint and supporting metadata service/models.
- Add lightweight validation for required BaaS requester context headers on payment API calls.
- Do not alter payment lifecycle rules, status transitions, mock OAuth token behavior, failure triggers, or idempotency semantics unless metadata validation exposes a mismatch.

This keeps the PoC clean: the payment sandbox remains the API behavior layer, while the portal becomes the onboarding and UAT guidance layer.

```json
{
  "baseUrl": "http://localhost:8080",
  "auth": {
    "type": "bearer",
    "tokenHint": "sandbox-payment-token",
    "readonlyTokenHint": "sandbox-readonly-token",
    "headerName": "Authorization"
  },
  "categories": ["positive", "negative", "boundary"],
  "scenarios": [
    {
      "id": "successful-payment",
      "title": "Successful A2A payment initialization",
      "category": "positive",
      "purpose": "Proves the partner can initialize a valid A2A payment and receive a payment identifier.",
      "steps": [
        {
          "id": "create-payment",
          "title": "Create payment",
          "method": "POST",
          "path": "/api/v1/payments",
          "headers": {
            "Authorization": "Bearer sandbox-payment-token",
            "X-Partner-Id": "partner-bank-uat",
            "X-On-Behalf-Of-Customer-Id": "customer-uat-001",
            "X-Customer-Consent-Id": "consent-payment-uat-001",
            "X-Request-Id": "req-uat-successful-payment-001",
            "Idempotency-Key": "uat-successful-payment-001"
          },
          "body": {
            "debtorAccount": "DEBTOR-001",
            "creditorAccount": "CREDITOR-001",
            "amount": 125.5,
            "currency": "USD"
          },
          "expected": {
            "httpStatus": 201,
            "paymentStatus": "EXECUTED",
            "eventStatuses": ["RECEIVED", "VALIDATED", "EXECUTED"]
          },
          "capture": {
            "paymentId": "$.paymentId"
          }
        }
      ],
      "evidenceFields": ["timestamp", "request", "httpStatus", "responseBody", "paymentId", "expectedMatch"]
    }
  ]
}
```

**Scenario fields**

- `id`, `title`, `category`, and `purpose` drive runbook navigation and UAT orientation.
- `preconditions` is optional and explains setup dependencies in human-readable form.
- `dependsOn` is optional and references another scenario when the portal should suggest running a prerequisite first.
- `steps` is required and contains one or more executable API calls.
- `evidenceFields` tells the portal which evidence fields to highlight for the scenario.

**Step fields**

- `method`, `path`, `headers`, and `body` define the request to run or copy.
- `expected.httpStatus` is required for every executable step.
- `expected.paymentStatus`, `expected.errorCode`, and `expected.eventStatuses` are optional match targets depending on scenario type.
- `capture` is optional and extracts values from one step for later display or later step interpolation.
- Later steps may use placeholders such as `{paymentId}` in `path`, `headers`, or `body` when a previous step captured that value.

**Multi-step examples**

- Payment lookup: create a payment, capture `paymentId`, then `GET /api/v1/payments/{paymentId}`.
- Lifecycle events lookup: create a payment, capture `paymentId`, then `GET /api/v1/payments/{paymentId}/events`.
- Idempotent replay: send the same create-payment request twice with the same `Idempotency-Key` and expect the second response to replay the first payment.
- Idempotency conflict: send a valid create-payment request, then send a different payload with the same `Idempotency-Key` and expect an idempotency conflict response.

## Risks / Trade-offs

- Local evidence resets on refresh or browser clear -> acceptable for PoC; document that persisted evidence is out of scope.
- Portal-run execution may hide details developers would inspect in Postman -> provide copyable curl/Postman-style requests alongside direct execution.
- Backend metadata can become verbose -> keep v1 metadata limited to what the runbook needs: purpose, setup, expected outcome, and evidence fields.
- No formal audit trail means the PoC cannot be used for regulated certification -> explicitly frame it as onboarding support, not certification.

## Migration Plan

Add portal and scenario metadata as a new change on top of the completed payment API sandbox. No data migration is required because v1 portal progress is local-only.

## Open Questions

None for v1. Defaults are runbook workspace, backend-owned scenario metadata, portal-run plus external execution helpers, and browser/session-only evidence.
