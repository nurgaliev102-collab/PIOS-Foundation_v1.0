# PIOS Module Structure

Status: Draft — Derived from Approved Foundation. This document defines software module responsibilities, dependency boundaries, ownership, and architectural isolation, before any implementation. It is not code, not a repository or folder structure, not a framework or programming language choice, and not a microservice deployment design.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-021), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), [API_SPECIFICATION.md](API_SPECIFICATION.md), and [DATABASE_DESIGN.md](DATABASE_DESIGN.md), in that order of authority. Every module here maps to a capability already ratified in ADR-018; none is introduced.

This document maps modules one-to-one with the eight capabilities already ratified as a Capability-Based architecture (ADR-016, ADR-017, ADR-018). It does not decide, and remains consistent with ADR-016's explicit non-decision on, how any single module structures its own internals; that remains open for a future, separate decision if and when it is needed.

---

## 1. Purpose

Module Structure exists to give each of the eight already-ratified architectural components a corresponding software organization unit — a module — with an explicit responsibility, boundary, and set of owned capabilities, before any code is written. It is the point at which the architecture already approved (SYSTEM_ARCHITECTURE.md, APPLICATION_ARCHITECTURE.md, DATABASE_DESIGN.md) becomes recognizable as software organization, without yet becoming a repository, a package, or a class.

## 2. Module Design Principles

- **Domain Alignment.** Every module aligns exactly with one of the eight architectural components ratified in ADR-018; no module spans more than one domain, and no domain lacks a corresponding module.
- **Low Coupling.** A module depends on another module only through the interaction and event mechanisms already established (ADR-003, ADR-004), never through direct access to another module's internals, consistent with ADR-009.
- **High Cohesion.** A module contains only the responsibility already assigned to its domain (ADR-018); it does not absorb responsibility belonging to another domain.
- **Explicit Ownership.** A module is owned by exactly the domain it aligns with; no module is owned jointly or by convention.
- **Dependency Direction.** A module never depends on another module's internals, only on that module's declared interaction or published events; dependency flows only through these declared boundaries.

## 3. Core Modules

### Order Management Module

- **Purpose.** The software organization unit responsible for receiving and tracking transportation demand.
- **Responsibility.** An order's lifecycle from submission through completion or cancellation (ADR-018).
- **Owned capabilities.** Submit Order and Cancel Order (APPLICATION_ARCHITECTURE.md Section 6); Retrieve Order Status (Section 7); OrderSubmitted, OrderCompleted, and OrderCancelled (EVENT_CATALOG.md Section 5).
- **Boundary.** No other module contains or changes Order information (ADR-005, ADR-009); its own physical structures are those defined for Order in DATABASE_DESIGN.md Section 5.

### Dispatch Module

- **Purpose.** The software organization unit responsible for the assignment decision.
- **Responsibility.** Connecting a transportation request to a driver, exclusively (ADR-002).
- **Owned capabilities.** Assign Order and Accept Assignment (APPLICATION_ARCHITECTURE.md Section 6); Retrieve Assignment (Section 7); OrderAssigned and AssignmentAccepted (EVENT_CATALOG.md Section 6).
- **Boundary.** No other module determines or changes an assignment; the specific assignment criteria are excluded from this document as from every other (ADR-002).
- **Assignment decision architecture.** [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) defines the input boundary (what Dispatch's eventual assignment decision may and must never use) and the Assignment Policy abstraction the decision will sit behind, without choosing or authorizing any algorithm.

### Driver Management Module

- **Purpose.** The software organization unit responsible for representing drivers.
- **Responsibility.** A driver's standing, availability, and participation (ADR-018).
- **Owned capabilities.** Declare Availability (APPLICATION_ARCHITECTURE.md Section 6); Retrieve Driver Availability (Section 7); DriverAvailabilityChanged (EVENT_CATALOG.md Section 7).
- **Boundary.** No other module changes a driver's availability state.

### Passenger Experience Module

- **Purpose.** The software organization unit responsible for representing passengers and corporate customers.
- **Responsibility.** Passenger and corporate customer interaction with the platform, including Personal Client relationships (ADR-018).
- **Owned capabilities.** Representation of passengers and corporate customers as the originating context for orders (APPLICATION_ARCHITECTURE.md Section 5); no dedicated command or query beyond that context is currently established.
- **Boundary.** No other module owns this representation.

### Administration Module

- **Purpose.** The software organization unit responsible for supporting platform administrators.
- **Responsibility.** Overseeing and governing the ecosystem (ADR-018).
- **Owned capabilities.** Administrator access to governance and standing information; no dedicated command or query beyond that access is currently established.
- **Boundary.** No other module owns governance and standing information.

### Analytics Module

- **Purpose.** The software organization unit responsible for producing insight into platform and ecosystem behavior.
- **Responsibility.** Derived, aggregate understanding only (ADR-018, ADR-019).
- **Owned capabilities.** Retrieve Insight (APPLICATION_ARCHITECTURE.md Section 7); consumption of events from other modules (DOMAIN_MODEL.md Section 7).
- **Boundary.** Never owns the underlying information it derives insight from.

### Notifications Module

- **Purpose.** The software organization unit responsible for informing participants.
- **Responsibility.** What has been communicated to participants, not the underlying trigger (ADR-018, ADR-019).
- **Owned capabilities.** Consumption of events from other modules in order to inform participants (DOMAIN_MODEL.md Section 7).
- **Boundary.** Never owns the underlying information that triggers a notification.
- **Scaffolding status.** [ADR-033: Notifications Module and Event Consumption Boundary](ADR/ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) confirms this module's entitlement to eventual independent deployment but has not authorized scaffolding it yet: ADR-025's database-technology evaluation for this domain remains open, and no specific event-consumption relationship (including OrderSubmitted) is yet authorized.

### Payments Module

- **Purpose.** The software organization unit responsible for the platform's own record of payment-related information.
- **Responsibility.** Payment information associated with orders (ADR-018).
- **Owned capabilities.** Record Payment; Retrieve Payment Record (APPLICATION_ARCHITECTURE.md Sections 6–7); no use case currently attributes these to a specific actor's goal, consistent with USE_CASE_CATALOG.md.
- **Boundary.** Never owns external settlement information (ADR-020).

## 4. Shared Capabilities

No module shares business ownership with another; this section describes only uniform principles that every module implements independently, never a shared owning module and never shared business data:

- **Observability.** Every module provides its own logs, metrics, and tracing as an intrinsic part of its own design, consistent with ADR-012; this is a uniform expectation of every module, not a capability owned by any single one.
- **Security enforcement.** Every module enforces its own authentication and authorization at its own boundary, consistent with ADR-011; no module is exempt, and no module enforces this on another's behalf.
- **Versioning discipline.** Every module's interactions and events are versioned the same way, consistent with ADR-010; this is a shared discipline, not a shared piece of business logic.

No common utility module is created here. Where a genuinely shared, non-business concern is later found to warrant its own module, that is a new architectural decision requiring an ADR, not an assumption of this document.

## 5. Dependency Rules

- **Allowed dependencies.** A module may depend on another module's declared interaction (ADR-004) or published events (ADR-003) — for example, Dispatch depends on Driver Management's DriverAvailabilityChanged event, and Order Management depends on Dispatch's AssignmentAccepted event, consistent with the cross-domain events already identified in EVENT_CATALOG.md Section 9.
- **Forbidden dependencies.** A module never depends on another module's internal structure or physical storage; no module shares physical storage with another (ADR-005; DATABASE_DESIGN.md Section 6).
- **Domain isolation.** A dependency never creates ownership; a module that depends on another's event or interaction never gains authority over the information it received, consistent with the Event Ownership Rules in EVENT_CATALOG.md Section 10.

## 6. Application Coordination

Modules interact only through the coordination responsibility already established in APPLICATION_ARCHITECTURE.md: an actor's goal is translated into a sequence of commands, queries, and event reactions across the modules it touches, without any module itself knowing which use case it is serving. A module recognizes only the commands, queries, and events at its own boundary (APPLICATION_ARCHITECTURE.md Section 2, Section 8); it does not orchestrate another module or reach into it directly. No interface or code is described here.

## 7. Data Access Boundaries

A module accesses only the physical structures already assigned to it in DATABASE_DESIGN.md Sections 4–5; its access to its own data is total and exclusive, and its access to another module's data is never direct — only through that module's declared interaction or published events, consistent with the Forbidden Ownership principle in PERSISTENCE_ARCHITECTURE.md Section 5. No repository pattern or ORM is described, as both are implementation mechanisms outside the scope of this document.

## 8. Extension Strategy

A new module is added only when a new bounded context is ratified through a decision that extends or supersedes ADR-017 and ADR-018, consistent with ADR-015. An existing module's owned capabilities evolve incrementally — a new command, query, or event version is introduced alongside the one it replaces, per ADR-010 — never as a silent change. No module is removed or merged with another without an explicit superseding decision, consistent with the ADR lifecycle in ADR-015.

## 9. Traceability

| Section | ADR | System Architecture | Domain Model | Application Architecture | Database Design |
| --- | --- | --- | --- | --- | --- |
| 1. Purpose | ADR-016, ADR-018 | Section 5 | Section 3 | Section 1 | Section 1 |
| 2. Module Design Principles | ADR-001, ADR-003, ADR-004, ADR-009, ADR-018 | — | — | Section 2 | — |
| 3. Core Modules | ADR-002, ADR-005, ADR-018, ADR-019, ADR-020, ADR-033 (Notifications) | Section 6 | Section 3 | Sections 5–7 | Sections 4–5 |
| 4. Shared Capabilities | ADR-010, ADR-011, ADR-012 | Sections 14–15 | — | — | Sections 8–9 |
| 5. Dependency Rules | ADR-003, ADR-004, ADR-005, ADR-009 | Section 8 | — | Section 8 | Section 6 |
| 6. Application Coordination | — | Section 8 | Section 7 | Sections 2, 8 | — |
| 7. Data Access Boundaries | ADR-005, ADR-009 | — | — | — | Sections 4–5 |
| 8. Extension Strategy | ADR-010, ADR-015, ADR-017, ADR-018 | Section 16 | — | — | Section 10 |

Where this document is silent — including on any module's internal structure — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
