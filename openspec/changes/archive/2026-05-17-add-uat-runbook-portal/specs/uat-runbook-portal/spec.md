## ADDED Requirements

### Requirement: Runbook workspace
The system SHALL provide a developer sandbox portal organized as a UAT scenario runbook workspace.

#### Scenario: Scenario workspace layout
- **WHEN** a partner developer opens the portal
- **THEN** the system shows scenario navigation, selected scenario details, expected outcome, execution controls, and evidence display in one focused workspace

### Requirement: Scenario coverage categories
The portal SHALL organize scenarios into positive, negative, and boundary categories.

#### Scenario: Categorized scenario list
- **WHEN** the portal loads scenario metadata
- **THEN** the scenario list groups or labels scenarios by positive, negative, and boundary coverage

### Requirement: Scenario detail guidance
The portal SHALL show scenario purpose, request setup, sample payload, expected result, and pass criteria for the selected scenario.

#### Scenario: Selected scenario details
- **WHEN** a partner developer selects a scenario
- **THEN** the portal displays the scenario purpose, required headers, request body, expected HTTP status, expected business result, and evidence fields

#### Scenario: BaaS requester context guidance
- **WHEN** a partner developer views request setup for a payment API scenario
- **THEN** the portal displays the partner, on-behalf-of customer, and customer consent headers needed to model the BaaS request context

### Requirement: Portal-run execution
The portal SHALL allow a partner developer to run a selected scenario directly against the sandbox API.

#### Scenario: Run successful scenario
- **WHEN** a partner developer runs a scenario from the portal
- **THEN** the portal sends the configured request to the sandbox API and displays the actual HTTP status and response body

#### Scenario: Run multi-step scenario
- **WHEN** a partner developer runs a scenario that contains multiple ordered steps
- **THEN** the portal runs the steps in order, makes captured values from earlier steps available to later steps, and displays each step result

#### Scenario: Fetch lifecycle events
- **WHEN** a portal-run response includes a payment identifier
- **THEN** the portal retrieves and displays lifecycle events for that payment

### Requirement: External execution helper
The portal SHALL provide copyable external execution helpers for each scenario.

#### Scenario: Copy external request
- **WHEN** a partner developer views a scenario
- **THEN** the portal provides a copyable curl or Postman-style request using the scenario headers and payload

### Requirement: Local evidence capture
The portal SHALL capture lightweight scenario evidence locally in browser or session state.

#### Scenario: Capture portal-run evidence
- **WHEN** a partner developer runs a scenario from the portal
- **THEN** the portal stores the latest scenario name, timestamp, step request headers, step request payloads, step HTTP statuses, step response bodies, payment identifier when present, lifecycle events when present, and completion status locally

#### Scenario: Completion after expected match
- **WHEN** a portal-run actual result matches the scenario expected outcome
- **THEN** the portal marks the scenario complete locally

#### Scenario: Manual external completion
- **WHEN** a partner developer runs a scenario externally in Postman or curl
- **THEN** the portal allows the developer to mark the scenario complete manually without persisted backend evidence

### Requirement: PoC scope boundary
The portal MUST NOT require backend persistence for scenario progress or evidence in v1.

#### Scenario: Browser-local progress
- **WHEN** a partner developer completes scenarios in the portal
- **THEN** completion progress exists only in browser or session state and does not require user accounts or backend storage
