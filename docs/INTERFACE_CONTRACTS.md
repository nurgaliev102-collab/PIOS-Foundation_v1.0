# PIOS Interface Contracts

Status: Draft — Derived from Approved Foundation. This document defines architectural interaction contracts between modules: interaction boundaries, ownership of operations, allowed communication directions, and exchanged conceptual information. It is not an API specification, a REST design, GraphQL, gRPC, a programming interface, a class interface, a function signature, or a message schema.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-021), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [EVENT_CATALOG.md](EVENT_CATALOG.md), [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), [API_SPECIFICATION.md](API_SPECIFICATION.md), [DATABASE_DESIGN.md](DATABASE_DESIGN.md), and [MODULE_STRUCTURE.md](MODULE_STRUCTURE.md), in that order of authority. It consolidates, rather than duplicates, what these documents already establish about which module depends on which.

---

## 1. Purpose

Interface Contracts exist to state, in one place and individually justified, exactly which module-to-module interactions are architecturally sanctioned: who provides a capability, who consumes it, and what conceptual information crosses the boundary. MODULE_STRUCTURE.md Section 5, EVENT_CATALOG.md Section 9, and APPLICATION_ARCHITECTURE.md each already establish pieces of this; this document brings them together as explicit contracts, without becoming a programming interface, an API endpoint, or a message schema.

## 2. Interface Principles

- **Explicit Ownership.** Every interaction contract names exactly one provider module — the domain that owns the capability or event being exposed (ADR-005, ADR-018).
- **Domain Isolation.** A contract never grants a consumer access to the provider's internals; only the specific command, query, or event named in the contract crosses the boundary (ADR-009).
- **Minimal Coupling.** A contract exists only where a consumer's own responsibility genuinely depends on it; none is defined speculatively, consistent with the Low Coupling principle in MODULE_STRUCTURE.md Section 2.
- **Contract Stability.** A contract's meaning does not change without a new version, consistent with ADR-010; a consumer may rely on a contract's stated meaning until it is explicitly superseded.
- **Information Responsibility.** A consumer that receives information through a contract never becomes responsible for its accuracy or meaning; that responsibility remains with the provider (EVENT_CATALOG.md Section 10).

## 3. Module Interaction Model

Among the eight modules (MODULE_STRUCTURE.md Section 3), four conceptual relationships are currently justified: Passenger Experience provides originating context to Order Management; Driver Management provides availability information to Dispatch; Dispatch provides assignment outcomes to Order Management; and Notifications and Analytics each consume events from other modules generically, without a specific provider named for either. Administration and Payments currently interact with no other module — each is self-contained within the responsibility already established for it (ADR-018). No other module-to-module relationship is established by any approved document, and none is invented here.

## 4. Module Ownership Rules

### Order Management

- **Owned responsibilities.** An order's lifecycle from submission through completion or cancellation.
- **Information it controls.** Order information (LOGICAL_DATA_MODEL.md Section 6).
- **Operations it provides.** Submit Order, Cancel Order, Retrieve Order Status; OrderSubmitted, OrderCompleted, OrderCancelled.
- **Operations it does not own.** The assignment decision (Dispatch); the passenger's or corporate customer's own representation (Passenger Experience); payment information (Payments).

### Dispatch

- **Owned responsibilities.** The assignment decision, exclusively.
- **Information it controls.** Assignment information.
- **Operations it provides.** Assign Order, Accept Assignment, Retrieve Assignment; OrderAssigned, AssignmentAccepted.
- **Operations it does not own.** An order's own status (Order Management); a driver's availability (Driver Management).

### Driver Management

- **Owned responsibilities.** A driver's standing, availability, and participation.
- **Information it controls.** Driver information, including availability.
- **Operations it provides.** Declare Availability, Retrieve Driver Availability; DriverAvailabilityChanged.
- **Operations it does not own.** Any assignment a driver is connected to (Dispatch).

### Passenger Experience

- **Owned responsibilities.** Passenger and corporate customer representation, including Personal Client relationships.
- **Information it controls.** Passenger, Corporate Customer, and its own side of Personal Client Relationship information.
- **Operations it provides.** Its own representation, as originating context for Order Management's Submit Order.
- **Operations it does not own.** The resulting order once submitted (Order Management).

### Administration

- **Owned responsibilities.** Platform governance and participant standing.
- **Information it controls.** Administrator and governance information.
- **Operations it provides.** Administrator access to governance and standing information; self-contained, with no cross-module contract currently justified.
- **Operations it does not own.** The operational information — orders, assignments, availability — it oversees but does not originate.

### Analytics

- **Owned responsibilities.** Derived, aggregate insight only.
- **Information it controls.** Insight information only.
- **Operations it provides.** Retrieve Insight; generic consumption of other modules' events (DOMAIN_MODEL.md Section 7), with no specific provider named.
- **Operations it does not own.** The underlying information insight is derived from.

### Notifications

- **Owned responsibilities.** Informing participants only.
- **Information it controls.** Notification information only.
- **Operations it provides.** Generic consumption of other modules' events (DOMAIN_MODEL.md Section 7), with no specific provider named.
- **Operations it does not own.** The underlying information that triggers a notification.

### Payments

- **Owned responsibilities.** The platform's own payment record.
- **Information it controls.** Payment Record information.
- **Operations it provides.** Record Payment, Retrieve Payment Record; no use case currently attributes these to a specific consumer.
- **Operations it does not own.** The order itself (Order Management); external settlement information.

## 5. Interaction Contracts

No method, payload, or schema is defined for any contract below.

### Contract: Passenger Experience → Order Management

- **Provider Module.** Passenger Experience.
- **Consumer Module.** Order Management.
- **Purpose.** Provide the originating passenger's or corporate customer's context for a submitted order.
- **Conceptual interaction.** When an order is submitted, Order Management relies on Passenger Experience's own representation of the originating passenger or corporate customer (APPLICATION_ARCHITECTURE.md Section 5); no copy of that representation becomes Order Management's own information.

### Contract: Driver Management → Dispatch

- **Provider Module.** Driver Management.
- **Consumer Module.** Dispatch.
- **Purpose.** Make a driver's current availability known so Dispatch can determine an assignment.
- **Conceptual interaction.** Dispatch relies on DriverAvailabilityChanged (EVENT_CATALOG.md Sections 7, 9) to know which drivers are currently available.

### Contract: Dispatch → Order Management

- **Provider Module.** Dispatch.
- **Consumer Module.** Order Management.
- **Purpose.** Make an order's assignment outcome known so Order Management can track the order's status.
- **Conceptual interaction.** Order Management relies on OrderAssigned and AssignmentAccepted (EVENT_CATALOG.md Sections 6, 9; APPLICATION_ARCHITECTURE.md Section 4) to reflect an order's progression through its lifecycle (DOMAIN_MODEL.md Section 12).

### Contract: Any Module → Notifications and Analytics (Generic)

- **Provider Module.** Not specified; any module.
- **Consumer Module.** Notifications and Analytics.
- **Purpose.** Inform participants (Notifications) and produce derived insight (Analytics), per their own responsibilities.
- **Conceptual interaction.** Both domain services (DOMAIN_MODEL.md Section 7) consume events from other modules through the mechanism already established (ADR-003). No specific provider-event pairing is established by any approved document, and none is invented here (EVENT_CATALOG.md Section 9).

## 6. Command Interaction Principles

- **Commands represent requests for capability execution.** A command names an intention (APPLICATION_ARCHITECTURE.md Section 6) directed at the module that owns the capability; issuing a command never performs the action itself.
- **Ownership remains with the receiving module.** The module that receives a command decides whether and how to act on it, consistent with the Domain Decides Business Meaning principle in APPLICATION_ARCHITECTURE.md Section 2; neither the issuing module nor any consumer has authority over that decision.

No command signature or API format is defined here.

## 7. Event Interaction Principles

Only the six events already catalogued in EVENT_CATALOG.md are described below; none is added.

| Event | Event Owner | Meaning | Possible Consumers |
| --- | --- | --- | --- |
| OrderSubmitted | Order Management | An order has been received. | None specifically established beyond the owner; Notifications and Analytics generically (Section 5). |
| OrderCancelled | Order Management | An order or its assignment has been terminated before completion. | None specifically established beyond the owner; Notifications and Analytics generically. |
| OrderCompleted | Order Management | An order has reached completion. | None specifically established beyond the owner; Notifications and Analytics generically. |
| OrderAssigned | Dispatch | Dispatch has connected an order to a driver. | Order Management (Contract, Section 5); the Driver actor externally (API_SPECIFICATION.md Section 8); Notifications and Analytics generically. |
| AssignmentAccepted | Dispatch | A proposed assignment has been confirmed. | Order Management (Contract, Section 5); Notifications and Analytics generically. |
| DriverAvailabilityChanged | Driver Management | A driver's declared readiness has changed. | Dispatch (Contract, Section 5); Notifications and Analytics generically. |

No event schema is defined here.

## 8. Data Access Boundaries

No module accesses another module's owned information directly. A module obtains information it does not own only through the contracts in Section 5 — never through direct access to another module's physical structures (DATABASE_DESIGN.md Sections 4–5), consistent with the exclusive-ownership rule in ADR-005 and its concrete mapping in ADR-019, and with the Forbidden Ownership principle already established in PERSISTENCE_ARCHITECTURE.md Section 5.

## 9. External Boundary Interfaces

Consistent with API_SPECIFICATION.md Section 4; no endpoint is designed here.

- **Passenger.** Interacts with Order Management (UC-001) and Passenger Experience (UC-002).
- **Driver.** Interacts with Driver Management (UC-003, UC-004) and Dispatch (UC-005, UC-006); receives OrderAssigned, per API_SPECIFICATION.md Section 8.
- **Corporate Customer.** Interacts with Order Management through Passenger Experience's originating context (UC-009).
- **Administrator.** Interacts with Administration (UC-010).
- **External Systems.** Interact as an integration boundary under ADR-020, not as a module consumer; see API_SPECIFICATION.md Section 12.

## 10. Evolution Strategy

A contract evolves only through a decision that explicitly supersedes it, consistent with ADR-015. A new version of a command, query, or event is introduced alongside the one it replaces, with a stated migration window, before the old version is retired, consistent with ADR-010. Because each module's storage technology is evaluated independently (ADR-021), a technology change on the provider side never requires a change to the contract itself — only to the provider's own physical implementation.

## 11. Traceability

| Section | ADR | System Architecture | Application Architecture | Module Structure | Domain Model | Event Catalog | API Specification | Database Design |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | ADR-001 | Section 8 | Section 1 | Section 5 | — | Section 9 | — | — |
| 2. Interface Principles | ADR-005, ADR-009, ADR-010, ADR-018 | — | Section 2 | Section 2 | — | Section 10 | — | — |
| 3. Module Interaction Model | ADR-018 | — | Section 4 | Section 5 | — | Section 9 | — | — |
| 4. Module Ownership Rules | ADR-005, ADR-018, ADR-019 | — | Section 5 | Section 3 | — | — | Section 5 | Section 4 |
| 5. Interaction Contracts | ADR-002, ADR-003 | — | Sections 4–5 | Section 5 | Section 7 | Sections 5–7, 9 | — | — |
| 6. Command Interaction Principles | ADR-004 | Section 8 | Sections 2, 6 | — | Section 9 | — | Section 6 | — |
| 7. Event Interaction Principles | ADR-003 | Section 9 | Section 9 | — | Section 8 | Sections 5–10 | Section 8 | — |
| 8. Data Access Boundaries | ADR-005, ADR-009, ADR-019 | — | — | Section 7 | — | — | — | Sections 4–5 |
| 9. External Boundary Interfaces | ADR-011, ADR-020 | Section 3 | — | — | — | — | Sections 3–4, 8, 12 | — |
| 10. Evolution Strategy | ADR-010, ADR-015, ADR-021 | Section 16 | — | Section 8 | — | — | Section 9 | Section 10 |

Where this document is silent — including on which specific events Notifications and Analytics consume — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
