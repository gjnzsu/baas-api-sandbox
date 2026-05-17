## Why

Partner developers need more than static API examples during UAT onboarding: they need a guided way to complete required payment scenarios, understand expected outcomes, and capture lightweight evidence that their integration handles success, failure, and boundary cases. The existing Payment API sandbox provides the API engine; this change adds the developer-facing runbook portal that makes the sandbox useful for onboarding.

## What Changes

- Add a developer sandbox portal centered on a scenario runbook workspace for UAT onboarding.
- Add backend-owned scenario metadata so the portal renders required payment scenarios from the same source of truth as sandbox behavior.
- Add lightweight BaaS requester context headers to payment API scenarios so the sandbox models a partner acting on behalf of a customer.
- Support both portal-run execution and copyable curl/Postman-style external execution helpers.
- Track scenario completion and latest evidence locally in the browser/session only.
- Show scenario categories for positive, negative, and boundary coverage.
- Keep the PoC lightweight: no persisted evidence, user accounts, approvals, audit trail, test-plan authoring, or certification workflow.

## Capabilities

### New Capabilities

- `uat-runbook-portal`: A developer sandbox portal with a lightweight UAT scenario runbook, backend scenario catalog, local completion progress, and evidence capture for partner onboarding.
- `sandbox-scenario-metadata`: Backend metadata describing supported sandbox scenarios, expected outcomes, request setup, and evidence fields for portal rendering.

### Modified Capabilities

- `payment-api-sandbox`: Add required mock BaaS requester context headers for protected payment API calls without changing payment lifecycle behavior.

## Impact

- Adds a frontend portal experience to the existing Spring Boot sandbox application.
- Adds a read-only scenario metadata API for the portal.
- Reuses the existing payment API endpoints as the system under test, with lightweight requester context header validation for BaaS realism.
- May add frontend build/runtime dependencies depending on the chosen portal implementation.
