## Context

This repository starts as an empty Git project. The PoC will establish the first runnable service: a Spring Boot Maven API sandbox for BaaS-style A2A payments. The sandbox is for product exploration and client integration testing, so deterministic contract behavior matters more than production-grade payment rails.

## Goals / Non-Goals

**Goals:**

- Provide a runnable local Spring Boot service with REST + OpenAPI documentation.
- Model A2A payment creation, lookup, lifecycle events, mock OAuth authorization, idempotency, and sandbox failure scenarios.
- Keep business logic isolated from controllers so payment behavior can be tested without HTTP.
- Keep persistence in-memory while hiding it behind a repository interface for future H2/PostgreSQL replacement.

**Non-Goals:**

- Real OAuth, JWT signing, or integration with an identity provider.
- Real account balance management, clearing, settlement, reconciliation, or ledger posting.
- Database migrations or containerized infrastructure.
- Webhook/event delivery beyond local lifecycle event inspection.

## Decisions

- Use Spring Boot 3 with Maven because it fits banking-style APIs, validation, actuator health checks, and contract-heavy tests while matching the repository's approved tooling.
- Use REST endpoints under `/api/v1/payments` with springdoc OpenAPI because sandbox consumers need a discoverable contract and examples.
- Use mock OAuth through a request filter that accepts static bearer tokens with payment scopes. This gives realistic integration behavior without building an OAuth server.
- Use an in-memory repository keyed by payment ID and idempotency key. This keeps the PoC deterministic and easy to reset while preserving a swap point for later persistence.
- Use `X-Sandbox-Scenario` to trigger failures. Explicit scenario headers avoid hidden magic values in payment amounts or account identifiers.
- Use a consistent error envelope containing `code`, `message`, and `details` so clients can test failure handling predictably.

## Risks / Trade-offs

- In-memory state resets on restart -> acceptable for v1; repository abstraction keeps later persistence simple.
- Static mock tokens are not secure -> document them as sandbox-only and keep real OAuth out of scope.
- Synchronous lifecycle transitions simplify demos but do not match every production rail -> expose lifecycle events so future async/webhook behavior has a natural extension point.
- OpenSpec 1.3.1 generated only core Codex skills in this environment -> use strict validation plus task/spec alignment as the review gate.

## Migration Plan

No production migration is required. Implement the service as the initial codebase, then archive the OpenSpec change after tests and OpenSpec validation pass.

## Open Questions

None for v1. Defaults are Spring Boot Maven, in-memory storage, REST + OpenAPI, mock OAuth, and scenario-header failure simulation.
