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

- **Owned responsibilities.** The assignment decision, exclusively; and, per [ADR-040](ADR/ADR-040-Assignment-Ride-Lifecycle.md), the ride's own progress as an Assignment lifecycle — arrival, ride start, and ride completion.
- **Information it controls.** Assignment information, including the assignment's own ride-progress status.
- **Operations it provides.** Assign Order, Accept Assignment, Retrieve Assignment; Arrive, Start and Complete Assignment (ADR-040 Decision item 4); OrderAssigned, AssignmentAccepted, AssignmentArrived, AssignmentStarted, AssignmentCompleted.
- **Operations it does not own.** An order's own status (Order Management) — including the `SUBMITTED → COMPLETED` transition that Order Management performs on its own authority when it consumes AssignmentCompleted (ADR-041 Decision item 2); a driver's availability (Driver Management); payment or pricing information (Payments; ADR-034 Part 1).

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
- **Conceptual interaction.** Order Management relies on OrderAssigned, AssignmentAccepted and AssignmentCompleted (EVENT_CATALOG.md Sections 6, 9; APPLICATION_ARCHITECTURE.md Section 4) to reflect an order's progression through its lifecycle (DOMAIN_MODEL.md Section 12).
- **Extension (Sprint 3 — Order Consistency; [ADR-041](ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md)).** AssignmentCompleted was added to this contract by ADR-041, which makes it *"the single trigger that completes an Order"* (Decision item 1). What crosses the boundary remains a plain order identifier resolved locally — never a foreign key, never a server-to-server call on the write path, and never a copy of Dispatch-owned state (ADR-041 Decision item 2; ADR-005, ADR-019, ADR-027). Receiving it grants Order Management no authority over the Assignment, and Dispatch gains no awareness that Order Management listens. **AssignmentArrived and AssignmentStarted are deliberately excluded from this contract**: ADR-041 Decision item 4 binds only `assignment.completed` alongside the existing `assignment.accepted`, because ride-progress states stay exclusively on Assignment.

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

This section originally read: *"Only the six events already catalogued in EVENT_CATALOG.md are described below; none is added."* That constraint is unchanged in substance — this table still adds no event of its own and remains strictly downstream of EVENT_CATALOG.md. It now describes nine events rather than six, because EVENT_CATALOG.md Section 6 itself has since grown: the three Dispatch ride-lifecycle events (ADR-040, ADR-041) are added below as part of Sprint 3's Documentation Hygiene item (see Section 14). The four Proposal events EVENT_CATALOG.md Section 6 catalogued in Sprint 1 are still not listed here, deliberately: that catalog's own text records that none of them "is published outside Dispatch's own process boundary today," so none crosses a module interaction boundary this document governs. That gap is recorded here as examined, not overlooked.

| Event | Event Owner | Meaning | Possible Consumers |
| --- | --- | --- | --- |
| OrderSubmitted | Order Management | An order has been received. | None specifically established beyond the owner; Notifications and Analytics generically (Section 5). |
| OrderCancelled | Order Management | An order or its assignment has been terminated before completion. | None specifically established beyond the owner; Notifications and Analytics generically. |
| OrderCompleted | Order Management | An order has reached completion. | None specifically established beyond the owner; Notifications and Analytics generically. |
| OrderAssigned | Dispatch | Dispatch has connected an order to a driver. | Order Management (Contract, Section 5); the Driver actor externally (API_SPECIFICATION.md Section 8); Notifications and Analytics generically. |
| AssignmentAccepted | Dispatch | A proposed assignment has been confirmed. | Order Management (Contract, Section 5); Notifications and Analytics generically. |
| AssignmentArrived | Dispatch | The driver has reached the passenger. | None. Not bound by any consumer (ADR-041 Decision item 4); Notifications and Analytics generically. |
| AssignmentStarted | Dispatch | The ride itself has begun. | None. Not bound by any consumer (ADR-041 Decision item 4); Notifications and Analytics generically. |
| AssignmentCompleted | Dispatch | The ride connected to an assignment has finished. | Order Management (Contract, Section 5) — the single trigger for `SUBMITTED → COMPLETED`; Notifications and Analytics generically. |
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
| 12. MVP Contract Verification and Transport Selection (Addendum) | ADR-027, ADR-028 | — | Section 10 | — | — | — | — |
| 13. Notifications Module Boundary Confirmation (Addendum) | ADR-017, ADR-018, ADR-025, ADR-026, ADR-033 | — | — | Section 3 (Notifications) | — | Section 9 | — | — |
| 14. Dispatch Ride Lifecycle Events (Addendum) | ADR-003, ADR-040, ADR-041 | — | — | Section 3 (Dispatch, Order Management) | Sections 12–13 | Sections 6, 9, 11 | — | — |

## 12. MVP Contract Verification and Transport Selection (Addendum)

Until ADR-028's selected transport category (message broker for events, REST for direct request-driven interaction) has a specific product chosen and implemented, compatibility between a contract's provider and consumer is verified through the mechanism ADR-027 establishes: a minimal, primitive-typed application-layer boundary on each side, checked only through test-scoped module references, never through a production-code dependency between modules. This addendum does not alter the contracts already stated in Section 5; it records how their correctness is checked ahead of transport implementation, and which category of transport (ADR-028) will eventually carry them.

## 13. Notifications Module Boundary Confirmation (Addendum)

[ADR-033: Notifications Module and Event Consumption Boundary](ADR/ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) confirmed that Notifications is entitled, per ADR-017/ADR-018/ADR-026, to become an independently deployable module, but found no approved document authorizes a specific event-consumption relationship for it — the generic contract in Section 5 and the generic entries in Section 7's table remain exactly as stated, unaltered. ADR-033 also reaffirmed that ADR-025's own database-technology gate for Notifications remains a separate, still-open prerequisite. This addendum does not change any contract in Sections 4, 5, or 7; it records that the silence they already stated on Notifications' specific consumption was examined, not overlooked, and remains open pending a product/business decision.

## 14. Dispatch Ride Lifecycle Events (Addendum)

[ADR-040: Assignment Ride Lifecycle](ADR/ADR-040-Assignment-Ride-Lifecycle.md) placed ride progress (`ARRIVED`, `IN_PROGRESS`, `COMPLETED`) on Dispatch's own Assignment rather than on Order Management's Order, and [ADR-041](ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) then made exactly one of the resulting events — AssignmentCompleted — a cross-domain business event. Neither ADR's documentation impact had been reflected here; ADR-041's own Consequences named that debt explicitly (*"`INTERFACE_CONTRACTS.md` Section 5's 'Contract: Dispatch → Order Management' names only `OrderAssigned` and `AssignmentAccepted`, with Section 7's event table matching"*) and assigned it to Sprint 3's Documentation Hygiene item. This addendum records that it has now been discharged: Section 4 (Dispatch), Section 5 (Contract: Dispatch → Order Management), and Section 7's table are brought into agreement with EVENT_CATALOG.md Sections 6 and 9.

**What this addendum does not do.** It establishes no new contract and no new consumer. AssignmentArrived and AssignmentStarted acquire no consumer here, and Order Management acquires no authority over Assignment. Order Management's own `SUBMITTED → COMPLETED` transition remains its own, exercised on its own aggregate through its own application service (ADR-041 Decision items 2 and 6) — Dispatch provides a fact, never an instruction (EVENT_CATALOG.md Section 2). The reverse direction remains equally unchanged: no Order Management → Dispatch contract exists, and this document creates none; ADR-041 Decision item 3 records that Dispatch has no cancellation capability to propagate and that Order cancellation is not communicated to Dispatch.

Where this document is silent — including on which specific events Notifications and Analytics consume (examined, and left open pending a product decision, by ADR-033) — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
