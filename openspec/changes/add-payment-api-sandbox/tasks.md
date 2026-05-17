## 1. Project Setup

- [ ] 1.1 Create Spring Boot Maven scaffold with web, validation, security, actuator, springdoc OpenAPI, and test dependencies.
- [ ] 1.2 Add application configuration for mock OAuth tokens, actuator health, and OpenAPI metadata.
- [ ] 1.3 Add `.gitignore` entries for Java build output and local worktree directories.

## 2. Payment Domain

- [ ] 2.1 Add payment request, response, status, event, and error models.
- [ ] 2.2 Add payment repository interface and in-memory implementation for payment ID and idempotency-key lookup.
- [ ] 2.3 Add payment service for validation, lifecycle transitions, scenario simulation, lookup, events, and idempotency behavior.

## 3. API And Security

- [ ] 3.1 Add mock OAuth security filter for missing, invalid, and insufficient-scope bearer tokens.
- [ ] 3.2 Add payment controller endpoints for creation, lookup, and event lookup.
- [ ] 3.3 Add exception handling that returns structured error envelopes.
- [ ] 3.4 Add OpenAPI annotations and examples for success and failure flows.

## 4. Tests And Verification

- [ ] 4.1 Add tests for successful payment creation, payment lookup, payment events, and health/OpenAPI docs.
- [ ] 4.2 Add tests for missing token, invalid token, and insufficient payment scope.
- [ ] 4.3 Add tests for insufficient funds, invalid beneficiary, authorization rejected, and duplicate payment scenarios.
- [ ] 4.4 Add tests for idempotent replay and idempotency conflict.
- [ ] 4.5 Run `mvn test` and `npx.cmd openspec validate add-payment-api-sandbox --strict`.
