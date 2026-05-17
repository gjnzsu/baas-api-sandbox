## ADDED Requirements

### Requirement: Payment creation
The system SHALL expose an A2A payment creation endpoint at `POST /api/v1/payments`.

#### Scenario: Successful payment creation
- **WHEN** a client submits a valid payment request with a valid bearer token and payment scope
- **THEN** the system returns HTTP 201 with a payment identifier, status `EXECUTED`, submitted request values, and lifecycle events

#### Scenario: Invalid payment request
- **WHEN** a client submits a payment request missing required debtor, creditor, amount, or currency fields
- **THEN** the system returns HTTP 400 with a structured validation error

### Requirement: Payment lookup
The system SHALL expose payment lookup at `GET /api/v1/payments/{paymentId}`.

#### Scenario: Existing payment lookup
- **WHEN** a client requests an existing payment identifier with a valid bearer token and payment scope
- **THEN** the system returns HTTP 200 with the stored payment state

#### Scenario: Missing payment lookup
- **WHEN** a client requests an unknown payment identifier with a valid bearer token and payment scope
- **THEN** the system returns HTTP 404 with a structured `PAYMENT_NOT_FOUND` error

### Requirement: Payment lifecycle events
The system SHALL expose payment lifecycle events at `GET /api/v1/payments/{paymentId}/events`.

#### Scenario: Existing payment events lookup
- **WHEN** a client requests events for an existing payment with a valid bearer token and payment scope
- **THEN** the system returns HTTP 200 with ordered lifecycle events for that payment

### Requirement: Mock OAuth authorization
The system MUST require bearer-token authorization for payment API endpoints.

#### Scenario: Missing bearer token
- **WHEN** a client calls a payment API endpoint without an `Authorization` header
- **THEN** the system returns HTTP 401 with an `AUTH_REQUIRED` error

#### Scenario: Invalid bearer token
- **WHEN** a client calls a payment API endpoint with an unknown bearer token
- **THEN** the system returns HTTP 401 with an `AUTH_INVALID` error

#### Scenario: Missing payment scope
- **WHEN** a client calls a payment API endpoint with a known bearer token that lacks payment scope
- **THEN** the system returns HTTP 403 with an `INSUFFICIENT_SCOPE` error

### Requirement: Idempotency handling
The system SHALL support idempotent payment creation through the `Idempotency-Key` header.

#### Scenario: Idempotent replay
- **WHEN** a client repeats the same payment request with the same `Idempotency-Key`
- **THEN** the system returns the original payment response without creating a second payment

#### Scenario: Idempotency conflict
- **WHEN** a client repeats a different payment request with a previously used `Idempotency-Key`
- **THEN** the system returns HTTP 409 with an `IDEMPOTENCY_CONFLICT` error

### Requirement: Sandbox failure simulation
The system SHALL support deterministic sandbox failures through `X-Sandbox-Scenario`.

#### Scenario: Insufficient funds
- **WHEN** a valid payment request includes `X-Sandbox-Scenario: insufficient_funds`
- **THEN** the system returns HTTP 422 with an `INSUFFICIENT_FUNDS` error and stores the payment as `FAILED`

#### Scenario: Invalid beneficiary
- **WHEN** a valid payment request includes `X-Sandbox-Scenario: invalid_beneficiary`
- **THEN** the system returns HTTP 422 with an `INVALID_BENEFICIARY` error and stores the payment as `REJECTED`

#### Scenario: Authorization rejected
- **WHEN** a valid payment request includes `X-Sandbox-Scenario: authorization_rejected`
- **THEN** the system returns HTTP 403 with an `AUTHORIZATION_REJECTED` error and stores the payment as `REJECTED`

#### Scenario: Duplicate payment
- **WHEN** a valid payment request includes `X-Sandbox-Scenario: duplicate_payment`
- **THEN** the system returns HTTP 409 with a `DUPLICATE_PAYMENT` error and stores the payment as `REJECTED`

### Requirement: OpenAPI and health
The system SHALL publish OpenAPI documentation and a health endpoint.

#### Scenario: OpenAPI documentation
- **WHEN** a client requests `/v3/api-docs`
- **THEN** the system returns HTTP 200 with an OpenAPI document containing payment API paths

#### Scenario: Health endpoint
- **WHEN** a client requests `/actuator/health`
- **THEN** the system returns HTTP 200 with service health status
