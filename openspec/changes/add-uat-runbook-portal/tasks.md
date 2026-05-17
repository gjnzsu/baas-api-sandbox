## 1. Scenario Metadata

- [ ] 1.1 Add backend scenario metadata models for scenario id, title, category, purpose, request setup, BaaS requester context headers, expected outcome, lifecycle expectations, and evidence fields.
- [ ] 1.2 Add a read-only scenario metadata service containing required positive, negative, and boundary payment scenarios.
- [ ] 1.3 Add a read-only scenario metadata endpoint for the portal.
- [ ] 1.4 Add tests that verify the metadata endpoint includes required categories, scenario fields, and expected outcomes.
- [ ] 1.5 Add lightweight payment API validation for required BaaS requester context headers.

## 2. Portal Shell

- [ ] 2.1 Add a developer sandbox portal entry point served by the Spring Boot application.
- [ ] 2.2 Build the runbook workspace layout with scenario navigation, selected scenario detail, execution controls, and evidence panel.
- [ ] 2.3 Load scenario metadata from the backend endpoint instead of hardcoding scenario definitions in the portal.
- [ ] 2.4 Add basic portal styling optimized for a dense developer/UAT workspace.

## 3. Scenario Execution

- [ ] 3.1 Implement portal-run execution that sends the selected scenario steps to the payment API in order.
- [ ] 3.2 Fetch and display lifecycle events when a portal-run response includes a payment identifier.
- [ ] 3.3 Compare actual outcome with expected HTTP status and business status or error code.
- [ ] 3.4 Provide copyable curl or Postman-style request text for each selected scenario step.

## 4. Local Evidence And Progress

- [ ] 4.1 Capture latest scenario evidence locally with timestamp, request, response, payment identifier, lifecycle events, and completion status.
- [ ] 4.2 Automatically mark portal-run scenarios complete when actual outcome matches expected outcome.
- [ ] 4.3 Allow manual completion for externally run Postman/curl scenarios.
- [ ] 4.4 Preserve progress only in browser/session state and avoid backend persistence.

## 5. Verification

- [ ] 5.1 Add tests for portal static asset availability, scenario metadata behavior, and required BaaS requester context headers.
- [ ] 5.2 Add tests for scenario execution comparison behavior where practical.
- [ ] 5.3 Run `mvn test` and `npx.cmd openspec validate add-uat-runbook-portal --strict`.
