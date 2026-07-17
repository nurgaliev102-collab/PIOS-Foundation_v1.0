# PIOS API Specification

Status: Draft — Derived from Approved Foundation. This document defines what interactions cross PIOS's system boundaries, what capabilities are exposed at those boundaries, and what contracts exist conceptually. It does not define technical transport. It is not a REST specification, a GraphQL schema, a gRPC contract, an OpenAPI document, a database contract, a frontend contract, or code documentation.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [EVENT_CATALOG.md](EVENT_CATALOG.md), [USE_CASE_CATALOG.md](USE_CASE_CATALOG.md), and [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), in that order of authority. It introduces no capability, command, query, or event beyond what these documents already establish.

---

## 1. Purpose

An API contract is the conceptual boundary at which a capability, already coordinated by the application layer (APPLICATION_ARCHITECTURE.md), becomes available to a consumer outside the capability that owns it. This document exists so that what crosses each boundary — which commands, queries, and events — is named and owned in one place, before any transport technology is chosen to carry it.

## 2. API Philosophy

- **Contract-First Thinking.** A contract is agreed and recorded before any technical interface implements it, consistent with Documentation Driven Development (ADR-006).
- **Consumer-Oriented Design.** A contract is shaped around what a consumer (Section 4) needs from a capability, not around that capability's internal structure, consistent with the resource-oriented, consistent interaction style already established in ADR-004.
- **Stable Boundaries.** A capability's contract remains stable for its consumers even as the capability's own internals evolve, consistent with the boundary-first composition in ADR-001 and the domain isolation rule in ADR-009.
- **Backward Compatibility.** A change to a contract that alters what a consumer can rely on is a new version, never a silent change, consistent with ADR-010.
- **Explicit Ownership.** A contract is owned by exactly the domain whose capability it exposes (ADR-005, ADR-018); no other domain may define or change it.

## 3. API Boundaries

- **Internal boundaries.** Between PIOS's own architectural components — Order Management, Dispatch, Driver Management, Passenger Experience, and Administration, among the components ratified in ADR-018 — crossed only through each component's declared interaction (ADR-004) or the events it publishes (ADR-003).
- **External boundaries.** Between PIOS as a whole and the ecosystem participants who are not part of any internal component — Passenger, Driver, Corporate Customer, and Platform Administrator — exposed through the same capability contracts described in Section 5, from outside rather than between components.
- **Partner boundaries.** PRODUCT_FOUNDATION.md Section 6 recognizes Partner as an ecosystem participant, but no domain, use case, or application capability currently grounds a concrete Partner interaction; SYSTEM_ARCHITECTURE.md Section 4 marks specific future extension points as "Architecture Decision Required." No Partner boundary is defined here.
- **User-facing application boundaries.** The point at which an actor's intention (USE_CASE_CATALOG.md) is coordinated by an application capability (APPLICATION_ARCHITECTURE.md Section 5) into a command or query directed at the domain that owns the relevant capability.

## 4. API Consumers

Each consumer below interacts with PIOS for the purpose already established in USE_CASE_CATALOG.md; no permission or authentication model is described.

- **Passenger.** Interacts to request transportation (UC-001) and to maintain an ongoing platform relationship (UC-002).
- **Driver.** Interacts to participate in the platform (UC-003), declare availability (UC-004), and receive (UC-005) and accept (UC-006) assignments.
- **Operational Role / Dispatcher.** PRODUCT_FOUNDATION.md Section 7 states this role's allocation responsibility is carried by the platform's own Dispatch capability, not by this actor directly. No use case attributes an independent API interaction to this role; none is defined here.
- **Corporate Customer.** Interacts to originate demand on behalf of its own members or clients (UC-009).
- **Platform Administrator.** Interacts to oversee and govern the platform (UC-010).
- **Partner.** No use case or domain grounds a Partner interaction (USE_CASE_CATALOG.md, Areas Not Catalogued). None is defined here.
- **External Systems.** Interact as an integration boundary under ADR-020, not as a goal-driven consumer; PIOS depends on them rather than serving them. See Section 12.

## 5. Capability Contracts

Each contract below is owned exclusively by the domain named, consistent with ADR-005 and ADR-019. No endpoint, method, or transport is described.

### Order Management

- **Purpose.** Exposes the ability to request and track transportation demand.
- **Available interactions.** Submit Order and Cancel Order (APPLICATION_ARCHITECTURE.md Section 6); Retrieve Order Status (Section 7); OrderSubmitted, OrderCompleted, and OrderCancelled made available to consumers who depend on them (EVENT_CATALOG.md Section 5).
- **Owned responsibility.** Orders and their status, from submission through completion or cancellation (ADR-019). Order Management alone decides an order's status.

### Dispatch

- **Purpose.** Exposes the outcome of connecting an order to a driver.
- **Available interactions.** Assign Order and Accept Assignment (APPLICATION_ARCHITECTURE.md Section 6); Retrieve Assignment (Section 7); OrderAssigned and AssignmentAccepted made available to consumers who depend on them (EVENT_CATALOG.md Section 6).
- **Owned responsibility.** The assignment decision, exclusively (ADR-002, ADR-019). No consumer may substitute its own assignment logic.

### Driver Management

- **Purpose.** Exposes a driver's own declared readiness and standing.
- **Available interactions.** Declare Availability (APPLICATION_ARCHITECTURE.md Section 6); Retrieve Driver Availability (Section 7); DriverAvailabilityChanged made available to consumers who depend on it (EVENT_CATALOG.md Section 7).
- **Owned responsibility.** Drivers, their standing, availability, and participation (ADR-019). Driver Management alone records a driver's availability state.

### Passenger Experience

- **Purpose.** Exposes the representation of a passenger or corporate customer and their relationship with the platform.
- **Available interactions.** No dedicated command or query is currently established beyond the originating context it provides to Order Management's Submit Order (APPLICATION_ARCHITECTURE.md Section 5); this contract is limited to representation, not a discrete transactional interaction.
- **Owned responsibility.** Passengers, corporate customers, and their interaction with the platform, including any Personal Client relationship (ADR-019).

### Administration

- **Purpose.** Exposes platform governance and participant standing to platform administrators.
- **Available interactions.** No dedicated command or query is currently established in DOMAIN_MODEL.md or APPLICATION_ARCHITECTURE.md beyond the administrator's access already described for UC-010 (APPLICATION_ARCHITECTURE.md Section 5); none is invented here.
- **Owned responsibility.** Platform governance and participant standing (ADR-019).

No capability contract is defined for Analytics, Notifications, or Payments. APPLICATION_ARCHITECTURE.md Section 5 found no use case attributing a grounded, consumer-facing responsibility to Analytics or Notifications beyond internal event consumption, and no use case attributing Payments' Record Payment command to a specific actor's goal. This document does not resolve that absence by invention.

## 6. Commands Contract

Each command below is the same conceptual intention already established in APPLICATION_ARCHITECTURE.md Section 6; none is renamed or added. A command represents a consumer's expressed intent toward the capability that owns it. No payload, schema, or request format is defined.

- **Submit Order** — toward Order Management, available to Passenger and Corporate Customer (UC-001, UC-009).
- **Assign Order** — toward Dispatch, available to Dispatch itself as the domain capability that invokes it (UC-007).
- **Accept Assignment** — toward Dispatch, available to Driver (UC-006).
- **Cancel Order** — toward Order Management, available to Passenger and Corporate Customer (UC-001, UC-009).
- **Declare Availability** — toward Driver Management, available to Driver (UC-004).
- **Record Payment** — toward Payments; no use case currently attributes this to a specific consumer, consistent with Section 5.

## 7. Query Contract

Each query below is the same conceptual information request already established in APPLICATION_ARCHITECTURE.md Section 7; none is renamed or added. No response structure, database, or read model is defined.

- **Retrieve Order Status** — from Order Management.
- **Retrieve Assignment** — from Dispatch.
- **Retrieve Driver Availability** — from Driver Management.
- **Retrieve Payment Record** — from Payments; no use case currently attributes a consumer to this query, consistent with Section 5.
- **Retrieve Insight** — from Analytics; no use case currently attributes a consumer to this query, consistent with Section 5.

## 8. Event Exposure Philosophy

An event crosses an API boundary only when it is a business event — one that a domain or consumer other than its owner relies on — consistent with ADR-003 and the cross-domain events already identified in EVENT_CATALOG.md Section 9: DriverAvailabilityChanged, relied upon by Dispatch, and AssignmentAccepted, relied upon by Order Management. OrderAssigned is likewise exposed to the Driver consumer it concerns (UC-005). A domain event with no identified consumer beyond the domain where it occurs is not exposed across a boundary.

**Ownership.** The domain that owns an event, per EVENT_CATALOG.md Section 10, retains sole authority over its meaning even when it is exposed across a boundary; exposing an event never transfers ownership.

**Consumer responsibility.** A consumer that receives an exposed event acts only on the fact it represents; it never reinterprets, overrides, or supplements what the owning domain intended by it (EVENT_CATALOG.md Section 10). No event schema is defined here.

## 9. Versioning Strategy

Every contract in Section 5 is versioned per ADR-010: a change that alters what a consumer can rely on is a new version, distinguishable from a change that does not. **Evolution** proceeds incrementally — a new version is introduced alongside the one it replaces, never as an outright replacement. **Compatibility** is maintained for a deprecated version for a stated migration window before removal, consistent with ADR-015. **Change control** follows the same rule: an existing contract is changed only by a decision that explicitly supersedes it, per ADR-007 and ADR-015, never silently.

## 10. Error Philosophy

A contract communicates that a request could not proceed without exposing the internal reasoning of the domain that rejected it, consistent with the Error Handling Philosophy in APPLICATION_ARCHITECTURE.md Section 11: only the owning domain has authority over why its own invariant was violated (ADR-005). No HTTP status code, exception type, or error format is defined here.

## 11. Security Boundary Philosophy

Every capability contract in Section 5 enforces its own authentication and authorization at its own boundary; no consumer is trusted by default, and access follows least privilege, consistent with ADR-011 and the Authorization Responsibility already established in APPLICATION_ARCHITECTURE.md Section 12. No authentication mechanism, protocol, or authorization model is defined here.

## 12. External Integration Contracts

Consistent with ADR-020, PIOS treats every external dependency the same way it treats an internal capability's boundary: never assuming direct access to the external system's internals, and never assuming its own record and the external system's record are simultaneously consistent.

- **Payment boundary.** The Payments capability's own record (Section 5) is internal; settlement through external banking or financial infrastructure is reached only through a declared interaction, per ADR-020.
- **Location and Routing boundary.** Physical road, traffic, and routing infrastructure is outside PIOS (PRODUCT_FOUNDATION.md Sections 8, 14); any dependency on externally provided location or routing information follows the same declared-boundary philosophy.
- **Notification boundary.** Informing participants is an internal responsibility with no capability contract of its own (Section 5); any dependency on an external delivery channel follows the same declared-boundary philosophy.
- **Identity boundary.** Driver licensing and certification remain the responsibility of external regulatory authorities; ADR-011 requires authentication and authorization at that boundary, and ADR-020 governs the dependency itself.

No protocol, message format, or technology is selected for any of these boundaries.

## 13. Traceability

| Section | Constitution | ADR | System Architecture | Application Architecture | Use Case Catalog | Event Catalog |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 4 | ADR-001, ADR-006 | Section 8 | Section 1 | — | — |
| 2. API Philosophy | Section 4 | ADR-001, ADR-004, ADR-005, ADR-006, ADR-009, ADR-010, ADR-018 | Section 8 | Section 2 | — | — |
| 3. API Boundaries | — | ADR-003, ADR-004, ADR-009, ADR-018 | Sections 4, 11 | Section 3 | Areas Not Catalogued | — |
| 4. API Consumers | — | — | Section 3 | Section 4 | UC-001 through UC-010 | — |
| 5. Capability Contracts | — | ADR-002, ADR-005, ADR-018, ADR-019 | Section 6 | Section 5 | UC-001 through UC-010 | Sections 5–7 |
| 6. Commands Contract | — | ADR-004 | Section 8 | Section 6 | UC-001, UC-004, UC-006, UC-007, UC-009 | — |
| 7. Query Contract | — | ADR-004 | Section 8 | Section 7 | — | — |
| 8. Event Exposure Philosophy | — | ADR-003 | Section 9 | Section 9 | — | Sections 5–7, 9–10 |
| 9. Versioning Strategy | Section 7 (ADR Policy) | ADR-007, ADR-010, ADR-015 | Section 16 | — | — | — |
| 10. Error Philosophy | — | ADR-005 | — | Section 11 | — | — |
| 11. Security Boundary Philosophy | Section 3 (Trust) | ADR-011 | Section 14 | Section 12 | — | — |
| 12. External Integration Contracts | — | ADR-011, ADR-020 | Section 11 | — | — | — |

Where this document is silent — including on Analytics, Notifications, and Payments having no capability contract, and on Partner having no boundary — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
