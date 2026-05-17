## ADDED Requirements

### Requirement: Scenario metadata API
The backend SHALL expose read-only scenario metadata for the developer sandbox portal.

#### Scenario: Retrieve scenario catalog
- **WHEN** the portal requests the scenario catalog
- **THEN** the backend returns scenario definitions for required UAT payment scenarios

### Requirement: Scenario definition fields
Each scenario definition SHALL include the data needed to render and execute the runbook scenario.

#### Scenario: Scenario definition content
- **WHEN** the backend returns a scenario definition
- **THEN** it includes scenario identifier, title, category, purpose, ordered executable steps, expected outcomes, optional dependencies or preconditions, and evidence fields

#### Scenario: Scenario step content
- **WHEN** a scenario includes an executable step
- **THEN** the step includes method, path, headers, optional request payload, expected HTTP status, optional expected payment status or error code, optional expected lifecycle events, and optional captured values for later steps

#### Scenario: BaaS requester context content
- **WHEN** the backend returns a payment API scenario step
- **THEN** the step headers include partner, on-behalf-of customer, and customer consent context headers in addition to mock bearer authorization

#### Scenario: Multi-step scenario definition
- **WHEN** a UAT scenario requires a prior API result such as a payment identifier or idempotency replay state
- **THEN** the scenario definition represents the flow as ordered steps with captured values available for later step interpolation

### Requirement: Required payment scenarios
The backend SHALL describe the required payment UAT scenarios for positive, negative, and boundary coverage.

#### Scenario: Positive scenarios
- **WHEN** the backend returns scenario metadata
- **THEN** it includes successful payment creation, payment lookup, and lifecycle events lookup scenarios

#### Scenario: Negative scenarios
- **WHEN** the backend returns scenario metadata
- **THEN** it includes missing token, invalid token, insufficient scope, insufficient funds, invalid beneficiary, authorization rejected, and duplicate payment scenarios

#### Scenario: Boundary scenarios
- **WHEN** the backend returns scenario metadata
- **THEN** it includes minimum valid amount, invalid amount, invalid currency, idempotent replay, and idempotency conflict scenarios

### Requirement: Metadata and API behavior alignment
Scenario metadata SHALL match the sandbox API behavior used to execute payment scenarios.

#### Scenario: Expected outcome alignment
- **WHEN** a scenario definition specifies an expected HTTP status and business outcome
- **THEN** a portal-run scenario using that definition can compare the actual API response against the expected outcome
