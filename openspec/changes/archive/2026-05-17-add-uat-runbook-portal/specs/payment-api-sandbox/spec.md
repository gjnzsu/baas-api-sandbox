## ADDED Requirements

### Requirement: BaaS requester context headers
The payment API SHALL require lightweight Banking-as-a-Service requester context headers on protected payment API calls.

#### Scenario: Valid BaaS requester context
- **WHEN** a client calls a payment API endpoint with valid mock bearer authorization and includes `X-Partner-Id`, `X-On-Behalf-Of-Customer-Id`, and `X-Customer-Consent-Id`
- **THEN** the system processes the request according to the existing payment API behavior

#### Scenario: Missing BaaS requester context
- **WHEN** a client calls a payment API endpoint with valid mock bearer authorization but omits a required BaaS requester context header
- **THEN** the system returns HTTP 400 with a structured `BAAS_CONTEXT_REQUIRED` error

#### Scenario: BaaS requester context in sandbox evidence
- **WHEN** a payment API request succeeds or fails in the sandbox
- **THEN** the requester context headers can be included in portal evidence and copied external requests without requiring real partner-customer authorization storage
