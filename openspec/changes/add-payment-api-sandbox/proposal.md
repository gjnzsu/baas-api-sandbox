## Why

Banking-as-a-Service API consumers need a safe sandbox for learning payment flows and testing failure handling before integrating with real banking rails. The first PoC should prove the payment API contract and deterministic failure simulation for A2A transfers.

## What Changes

- Add a Spring Boot Maven API sandbox for A2A payment creation, status lookup, event inspection, and health checks.
- Add REST + OpenAPI documentation for payment requests, responses, errors, auth, idempotency, and sandbox scenarios.
- Add mock OAuth bearer-token validation with scope checks.
- Add in-memory payment storage with idempotency-key replay and conflict behavior.
- Add deterministic failure simulation via `X-Sandbox-Scenario` for insufficient funds, invalid beneficiary, duplicate payment, and authorization rejection.
- Add automated tests covering success, auth failures, scenario failures, idempotency, and OpenAPI availability.

## Capabilities

### New Capabilities

- `payment-api-sandbox`: A BaaS-style A2A payment API sandbox with mock OAuth, idempotency, in-memory lifecycle state, OpenAPI docs, and deterministic failure scenarios.

### Modified Capabilities

- None.

## Impact

- Creates the initial Spring Boot Maven application, tests, and OpenSpec artifacts.
- Introduces public endpoints under `/api/v1/payments`, `/api/v1/payments/{paymentId}`, `/api/v1/payments/{paymentId}/events`, and `/actuator/health`.
- Introduces OpenAPI/Swagger dependencies, Spring validation, Spring Security, and Spring Boot Actuator.
