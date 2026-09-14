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
- **Extension ([ADR-069: Test/Real Segregation in Fallback Driver Selection](ADR/ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md), Accepted).** DriverAvailabilityChanged's payload gains one additional field, `isTest` (boolean) — Driver Management's own `Driver.isTest` classification, published by the owning module on its own event, additive and non-breaking (`eventVersion` stays `1`; ADR-030 lines 33–36, 58). Dispatch projects it into its local `driver_availability` projection as a nullable, three-valued column (`NULL` meaning "not yet told," never coerced to `false`) and uses it, alongside availability, as a second eligibility gate on Fallback Dispatch's own selection queries (ADR-068 Part 2, property 3, as narrowed by ADR-069) — a real order is never offered to a test driver and a test order is never offered to a real driver. It carries no profile, rating, or reputation data, and grants Dispatch no new authority over Driver Management's own record.

### Contract: Dispatch → Order Management

- **Provider Module.** Dispatch.
- **Consumer Module.** Order Management.
- **Purpose.** Make an order's assignment outcome known so Order Management can track the order's status.
- **Conceptual interaction.** Order Management relies on OrderAssigned, AssignmentAccepted and AssignmentCompleted (EVENT_CATALOG.md Sections 6, 9; APPLICATION_ARCHITECTURE.md Section 4) to reflect an order's progression through its lifecycle (DOMAIN_MODEL.md Section 12).
- **Extension (Sprint 3 — Order Consistency; [ADR-041](ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md)).** AssignmentCompleted was added to this contract by ADR-041, which makes it *"the single trigger that completes an Order"* (Decision item 1). What crosses the boundary remains a plain order identifier resolved locally — never a foreign key, never a server-to-server call on the write path, and never a copy of Dispatch-owned state (ADR-041 Decision item 2; ADR-005, ADR-019, ADR-027). Receiving it grants Order Management no authority over the Assignment, and Dispatch gains no awareness that Order Management listens. **AssignmentArrived and AssignmentStarted are deliberately excluded from this contract**: ADR-041 Decision item 4 binds only `assignment.completed` alongside the existing `assignment.accepted`, because ride-progress states stay exclusively on Assignment.

### Contract: Order Management → Dispatch

- **Provider Module.** Order Management.
- **Consumer Module.** Dispatch.
- **Purpose.** Make an order's cancellation, before any driver acceptance, known to Dispatch so its own Proposal for that order does not stay dangling.
- **Conceptual interaction.** Dispatch relies on OrderCancelled (EVENT_CATALOG.md Sections 6, 9) to withdraw the cancelled order's own `OPEN` Proposal, if one exists.
- **Added by [ADR-053: Proposal Resolution on Order Cancellation](ADR/ADR-053-Proposal-Resolution-on-Order-Cancellation.md) (Accepted).** The reverse direction from "Contract: Dispatch → Order Management," above, and the first Order Management → Dispatch relationship this document names — where [ADR-034](ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) Part 2 counted four justified cross-module relationships, this is the fifth. What crosses the boundary remains a plain order identifier resolved locally — never a foreign key, never a server-to-server call on the write path, and never a copy of Order Management-owned state (ADR-053 Part 1; ADR-005, ADR-009, ADR-019, mirroring the identical constraint already governing the Dispatch → Order Management direction). Receiving it grants Dispatch no authority over the Order, and Order Management gains no awareness that Dispatch listens. **This corrects Section 14's own prior statement** (*"no Order Management → Dispatch contract exists, and this document creates none... Order cancellation is not communicated to Dispatch"*), accurate when Section 14 was written and superseded now by ADR-053 alone — see Section 16.

### Contract: Passenger Experience → Dispatch

- **Provider Module.** Passenger Experience.
- **Consumer Module.** Dispatch.
- **Purpose.** Make a passenger's Primary Driver designation (Circle of Trust, `ADR-054`) known to Dispatch, solely so a new order may offer First Refusal to that driver before falling through to normal matching (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 3–4).
- **Conceptual interaction.** Dispatch relies on PrimaryConnectionDesignated and PrimaryConnectionCleared (Section 7) to maintain its own local, derived, non-authoritative projection of "does this passenger currently have a Primary Driver, and who" — never a copy treated as more authoritative than Passenger Experience's own `Connection`/`isPrimary`, and never exposed to any caller beyond Dispatch's own internal routing logic.
- **Added by [ADR-062: Primary Driver / First Refusal — Passenger Experience → Dispatch Contract](ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) (Accepted).** The sixth justified cross-module relationship this document names, following the same event-fed, non-shared-storage shape every prior addition to this section already uses. See Section 17 for the full addendum.
- **Explicit boundary, restated from `ADR-062` itself.** This contract carries only two opaque identifiers (a passenger reference and a driver reference) — never passenger name, phone, rating, reputation, or any other profile or trust data. It grants Dispatch no authority over `Connection`/`isPrimary`, and grants Passenger Experience no new write path. It is not a general grant of Personal Client Relationship visibility to Dispatch — see `ADR-034` Part 2 and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2, both reconciled alongside this addition.
- **Status as of this entry.** Architecturally ratified; **not yet implemented** — no code, migration, or RabbitMQ topology exists for this contract as of this entry's own addition (Task 10B). This entry describes TARGET architecture, distinguished here explicitly per this document's own convention of never blurring ratified-but-unbuilt with currently-running behavior.

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

This section originally read: *"Only the six events already catalogued in EVENT_CATALOG.md are described below; none is added."* That constraint is unchanged in substance — this table still adds no event of its own and remains strictly downstream of EVENT_CATALOG.md. It now describes eleven events rather than six, because EVENT_CATALOG.md Section 6 itself has since grown: the three Dispatch ride-lifecycle events (ADR-040, ADR-041) were added as part of Sprint 3's Documentation Hygiene item (see Section 14), and two Passenger Experience events (`ADR-062`, Task 10B, see Section 17) were added alongside the new contract above. The four Proposal events EVENT_CATALOG.md Section 6 catalogued in Sprint 1 are still not listed here, deliberately: that catalog's own text records that none of them "is published outside Dispatch's own process boundary today," so none crosses a module interaction boundary this document governs. That gap is recorded here as examined, not overlooked.

**CURRENT vs. TARGET, marked explicitly in the table below.** Every row through `DriverAvailabilityChanged` describes an event that is actually published by running code today (CURRENT). The two `PrimaryConnection*` rows describe events `ADR-062` has architecturally ratified but that **no code yet publishes or consumes** (TARGET) — listed here so the ratified contract is discoverable, not to imply it is already live.

| Event | Event Owner | Meaning | Possible Consumers | Runtime status |
| --- | --- | --- | --- | --- |
| OrderSubmitted | Order Management | An order has been received. | None specifically established beyond the owner; Notifications and Analytics generically (Section 5). | CURRENT |
| OrderCancelled | Order Management | An order or its assignment has been terminated before completion. | Dispatch (Contract, Section 5, added by ADR-053) — withdraws the order's own `OPEN` Proposal, if any; Notifications and Analytics generically. | CURRENT |
| OrderCompleted | Order Management | An order has reached completion. | None specifically established beyond the owner; Notifications and Analytics generically. | CURRENT |
| OrderAssigned | Dispatch | Dispatch has connected an order to a driver. | Order Management (Contract, Section 5); the Driver actor externally (API_SPECIFICATION.md Section 8); Notifications and Analytics generically. | CURRENT |
| AssignmentAccepted | Dispatch | A proposed assignment has been confirmed. | Order Management (Contract, Section 5); Notifications and Analytics generically. | CURRENT |
| AssignmentArrived | Dispatch | The driver has reached the passenger. | None. Not bound by any consumer (ADR-041 Decision item 4); Notifications and Analytics generically. | CURRENT — kept unmodified as the legacy half of a dual-publish window; see Section 19. |
| AssignmentStarted | Dispatch | The ride itself has begun. | None. Not bound by any consumer (ADR-041 Decision item 4); Notifications and Analytics generically. | CURRENT — kept unmodified as the legacy half of a dual-publish window; see Section 19. |
| AssignmentCompleted | Dispatch | The ride connected to an assignment has finished. | Order Management (Contract, Section 5) — the single trigger for `SUBMITTED → COMPLETED`; Notifications and Analytics generically. | CURRENT — kept unmodified as the legacy half of a dual-publish window; see Section 19. |
| TripArrived | Dispatch | The driver connected to a Trip has reached the passenger. | None. Not bound by any consumer. | CURRENT (Task 12) — new half of the same dual-publish window; see Section 19. |
| TripStarted | Dispatch | The ride itself has begun. | None. Not bound by any consumer. | CURRENT (Task 12) — new half of the same dual-publish window; see Section 19. |
| TripCompleted | Dispatch | The ride connected to a Trip has finished. | None yet — Order Management's consumer was deliberately left bound to `AssignmentCompleted` (Task 12's own Order Management Rule); switching it is a distinct, not-yet-authorized future task. | CURRENT (Task 12) — new half of the same dual-publish window; see Section 19. |
| DriverAvailabilityChanged | Driver Management | A driver's declared readiness has changed. | Dispatch (Contract, Section 5); Notifications and Analytics generically. | CURRENT |
| **PrimaryConnectionDesignated** | Passenger Experience | A passenger has designated (or changed) their Primary Driver. | Dispatch (Contract, Section 5, added by `ADR-062`) — maintains its own `PrimaryDriverRecord` projection. | **TARGET — ratified by `ADR-062`, not yet implemented.** |
| **PrimaryConnectionCleared** | Passenger Experience | A passenger's Primary Driver designation has been removed. | Dispatch (Contract, Section 5, added by `ADR-062`) — clears the corresponding projection entry. | **TARGET — ratified by `ADR-062`, not yet implemented.** |

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
| 15. Owner Observation Boundary — No New Relationship (Addendum) | ADR-037, ADR-043, ADR-044 | — | — | — | — | — | Section 14 | — |

## 12. MVP Contract Verification and Transport Selection (Addendum)

Until ADR-028's selected transport category (message broker for events, REST for direct request-driven interaction) has a specific product chosen and implemented, compatibility between a contract's provider and consumer is verified through the mechanism ADR-027 establishes: a minimal, primitive-typed application-layer boundary on each side, checked only through test-scoped module references, never through a production-code dependency between modules. This addendum does not alter the contracts already stated in Section 5; it records how their correctness is checked ahead of transport implementation, and which category of transport (ADR-028) will eventually carry them.

## 13. Notifications Module Boundary Confirmation (Addendum)

[ADR-033: Notifications Module and Event Consumption Boundary](ADR/ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) confirmed that Notifications is entitled, per ADR-017/ADR-018/ADR-026, to become an independently deployable module, but found no approved document authorizes a specific event-consumption relationship for it — the generic contract in Section 5 and the generic entries in Section 7's table remain exactly as stated, unaltered. ADR-033 also reaffirmed that ADR-025's own database-technology gate for Notifications remains a separate, still-open prerequisite. This addendum does not change any contract in Sections 4, 5, or 7; it records that the silence they already stated on Notifications' specific consumption was examined, not overlooked, and remains open pending a product/business decision.

## 14. Dispatch Ride Lifecycle Events (Addendum)

[ADR-040: Assignment Ride Lifecycle](ADR/ADR-040-Assignment-Ride-Lifecycle.md) placed ride progress (`ARRIVED`, `IN_PROGRESS`, `COMPLETED`) on Dispatch's own Assignment rather than on Order Management's Order, and [ADR-041](ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) then made exactly one of the resulting events — AssignmentCompleted — a cross-domain business event. Neither ADR's documentation impact had been reflected here; ADR-041's own Consequences named that debt explicitly (*"`INTERFACE_CONTRACTS.md` Section 5's 'Contract: Dispatch → Order Management' names only `OrderAssigned` and `AssignmentAccepted`, with Section 7's event table matching"*) and assigned it to Sprint 3's Documentation Hygiene item. This addendum records that it has now been discharged: Section 4 (Dispatch), Section 5 (Contract: Dispatch → Order Management), and Section 7's table are brought into agreement with EVENT_CATALOG.md Sections 6 and 9.

**What this addendum does not do.** It establishes no new contract and no new consumer. AssignmentArrived and AssignmentStarted acquire no consumer here, and Order Management acquires no authority over Assignment. Order Management's own `SUBMITTED → COMPLETED` transition remains its own, exercised on its own aggregate through its own application service (ADR-041 Decision items 2 and 6) — Dispatch provides a fact, never an instruction (EVENT_CATALOG.md Section 2).

**Correction (Section 16, below).** The sentence that followed here — *"The reverse direction remains equally unchanged: no Order Management → Dispatch contract exists, and this document creates none; ADR-041 Decision item 3 records that Dispatch has no cancellation capability to propagate and that Order cancellation is not communicated to Dispatch"* — was accurate when this addendum was written and is retained above for the historical record (`CLAUDE.md`, "Never Delete Documentation"), but is **no longer current**. [ADR-053: Proposal Resolution on Order Cancellation](ADR/ADR-053-Proposal-Resolution-on-Order-Cancellation.md) (Accepted) has since opened exactly that reverse direction, for the narrow purpose Section 16 describes. Nothing else in this addendum, or in ADR-041 itself, is affected.

Where this document is silent — including on which specific events Notifications and Analytics consume (examined, and left open pending a product decision, by ADR-033) — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.

## 15. Owner Observation Boundary — No New Relationship (Addendum)

[ADR-043: Owner Control Center — Observation Boundary](ADR/ADR-043-Owner-Control-Center-Observation-Boundary.md) and [ADR-044: Owner Authentication — PIOS's First Authentication Mechanism](ADR/ADR-044-Owner-Authentication-Mechanism.md) add a `GET`-only, per-module self-report ("Retrieve Module Health", API_SPECIFICATION.md Section 14) to Order Management, Dispatch, Driver Management, Passenger Experience, and Identity. This addendum exists only to record, for this document specifically, what ADR-043 Decision 1 already states: **Section 3's module interaction model is unchanged, and no row is added to it.**

- No module calls another module to answer this capability. Each module reports only its own process, its own datasource, and — for the three that have one — its own outbox table.
- No new provider/consumer pair is created; the reader is the owner's own browser, which is not a module and holds no place in Section 3's model.
- All correlation across what different modules separately report happens in that browser and is never persisted, queried by any module, or treated as a fifth module's own information (ADR-043 Decision 1). Section 8's Data Access Boundaries are therefore not engaged by this capability at all — there is no module obtaining information it does not own, because no module obtains anything from another module here.
- Network Management (Module Structure, ADR-037) is not part of this capability in any way, consistent with its existing absence from every contract in this document.

This addendum establishes no new contract, in Section 5 or anywhere else, and none should be inferred from the existence of the health capability described in API_SPECIFICATION.md Section 14.

## 16. Order Cancellation Propagation to Dispatch (Addendum)

[ADR-053: Proposal Resolution on Order Cancellation](ADR/ADR-053-Proposal-Resolution-on-Order-Cancellation.md) (Accepted) opens the one cross-module relationship Section 14 had explicitly recorded as absent: Order Management's `OrderCancelled` is now consumed by Dispatch, so that a Proposal left `OPEN` when its order is cancelled before driver acceptance does not stay dangling (P0-2 Tier 1, `docs/SPRINT_PILOT_BLOCKERS.md`). This addendum discharges the documentation-sync obligation ADR-053 itself named (Consequences: *"`INTERFACE_CONTRACTS.md` §5 requires a new Order Management → Dispatch contract entry"*): Section 5 (new "Contract: Order Management → Dispatch"), Section 7's table (`OrderCancelled` row), and this note are brought into agreement with EVENT_CATALOG.md Sections 6 and 9.

**What this addendum does not do.** It does not touch Tier 2 (cancellation after driver acceptance, arrival, or ride start) — ADR-041's own exclusion of that stays fully in force, unmodified, exactly as ADR-053 Part 7 confirms. It creates no authority for Dispatch over `Order.status`, and no authority for Order Management over `Proposal.status` — each module continues to write only its own aggregate (ADR-005, ADR-009, ADR-019). It adds no manual, directly-callable withdraw endpoint; the only path into `ProposalStatus.WITHDRAWN` is the automatic one this contract describes (ADR-053 Part 4).

Where this document is silent, no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in `PROJECT_CONSTITUTION.md` Section 5 — the same rule Section 14 already states, restated here for this addendum's own reader.

## 17. Primary Driver Routing Signal — Passenger Experience → Dispatch (Addendum)

[ADR-062: Primary Driver / First Refusal — Passenger Experience → Dispatch Contract](ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) (Accepted, Task 10B) opens the one cross-module relationship `ADR-034` Part 2 and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 had both explicitly recorded as absent: Dispatch may now consume a narrow, binary Primary Driver signal from Passenger Experience, solely to offer First Refusal (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 3–4) before falling through to normal matching. This addendum discharges the documentation-sync obligation `ADR-062` itself named (Consequences): Section 5 (new "Contract: Passenger Experience → Dispatch"), Section 7's table (`PrimaryConnectionDesignated`/`PrimaryConnectionCleared` rows), and this note are brought into agreement with `ADR-062`.

**What this addendum does not do.** It does not grant Dispatch any authority over `Connection`/`isPrimary` — Passenger Experience remains sole owner and sole writer (`ADR-054`, `ADR-005`, `ADR-009`, `ADR-019`). It does not grant Dispatch visibility into any passenger profile, contact, rating, or reputation data — the contract's own data shape is limited to two opaque identifiers (Section 5, above). It does not authorize Personal Client Relationship as an input to any future ranking or scoring mechanism — `ADR-034` Part 2's own Forbidden-inputs list is otherwise unchanged, and this contract's narrow, binary purpose is explicitly distinguished from that question (see `ADR-034` Part 2's own reconciliation note). It does not implement anything — no code, migration, or RabbitMQ topology exists yet for this contract (Section 7's own "TARGET" marking, above).

**Correction, historical record preserved.** `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2's own statement — *"Dispatch. No ratified role at all"* — was accurate when written and remains in that document, unmodified, per `CLAUDE.md`'s "Never Delete Documentation," but is **no longer current** for the narrow purpose this addendum describes. See that document's own Part 2 for the superseding note.

## 18. Order/Assignment/Trip — Target Event Rename, Not Yet Live (Addendum)

[ADR-063: Trip as a New Dispatch-Owned Aggregate, Distinct from Assignment](ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md) (Accepted, after correction, Task 10B) ratifies splitting ride-progress facts (`ARRIVED`, `IN_PROGRESS`, `COMPLETED`) off `Assignment` into a new, Dispatch-internal `Trip` aggregate, and ratifies the eventual rename of `AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted` to `TripArrived`/`TripStarted`/`TripCompleted`.

**What this addendum does not do — stated with particular care, since this is a rename of already-live production events.** It does **not** rename any event in Section 7's table above — every `Assignment*` row remains exactly as it is, because that is what the running system actually publishes today. It does **not** create any new contract entry in Section 5 — `Trip` lives entirely inside Dispatch's own module (`ADR-063`'s own placement decision), so no new cross-module relationship exists for this document to record. It does **not** authorize implementation — `ADR-063`'s own Constraints are explicit that it "does not itself rename or republish any event, create any migration, or write any code." When Trip is actually implemented, `ADR-063`'s own Consequences section requires a dual-publish migration window (both `Assignment*` and `Trip*` events published, neither removed) before Order Management's consumer is switched and the old names retired — at that point, and not before, this document's Section 7 table and Section 5's `Contract: Dispatch → Order Management` entry will need updating to reflect the new names, following the same discipline this addendum itself follows now: describe what is actually running, mark what is ratified-but-not-yet-built as TARGET, never blur the two.

**Superseded in part, 2026-09-03 (Task 12) — see Section 19.** Trip is now implemented and the dual-publish window described above is live. This section's own text above is preserved verbatim, not rewritten, per `CLAUDE.md`'s "Never Delete Documentation" — it accurately describes the state of the system as of Task 10B. Section 19 records what changed.

## 19. Trip Ride-Progress Convergence — Dual-Publish Window Live (Addendum, Task 12)

Implements the migration Section 18 (above) anticipated. `DispatchAssignmentApplicationService.arriveAssignment`/`startAssignment`/`completeAssignment` (Dispatch) now transition the `Trip` aggregate Task 11 introduced — `Trip`, not `Assignment`, validates and persists `ARRIVED`/`IN_PROGRESS`/`COMPLETED` from this point forward. `Assignment`'s own `arrive`/`start`/`complete` methods and ride-progress fields are **not removed** (a deliberate, documented deviation from `ADR-063`'s own Decision-section wording that they be "removed from Assignment" — see `docs/PIOS_TAXI_TASK_12_TRIP_CONVERGENCE_REPORT.md` Section 11 for the full reasoning: a live consumer, `DriverHome.tsx`, and existing pilot data with `Assignment.status` already at `ARRIVED`/`IN_PROGRESS`/`COMPLETED`, make deletion unsafe today).

**What changed:**
- Each of the three transitions now publishes **two** outbox records in the same transaction: the original `Assignment*` one (`eventType`/routing key byte-for-byte unchanged — `assignment.arrived`/`assignment.started`/`assignment.completed`) and a new `Trip*` one (`trip.arrived`/`trip.started`/`trip.completed`). Section 7's table above reflects both.
- `AssignmentController`'s `GET /v1/assignments`, `.../arrive`, `.../start`, `.../complete` — this contract's own REST surface for the ride lifecycle (Section 14) — are **unchanged in shape**: same URLs, same `AssignmentResponse` fields. Internally, the response is now projected from both `Assignment` (identity, order, driver, isTest) and `Trip` (status/timestamps, once the connected Trip has moved past its own initial state) — invisible to every caller, including the frontend.
- An `Assignment` saved before Trip existed self-heals: its first ride-progress action creates the missing `Trip` on the spot, so no backfill migration was required.

**What this addendum does not do.** It does not switch Order Management's own consumer — `AssignmentCompletedListener` still binds `assignment.completed`, unmodified, per Task 12's own Order Management Rule. It does not remove any `Assignment*` event, row, or field. It does not touch Section 5's `Contract: Dispatch → Order Management` entry, since that contract's own event name has not changed from Order Management's point of view. Retiring the `Assignment*` half of the dual-publish window (switching Order Management's binding to `trip.completed` and then removing `AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted`) remains a distinct, not-yet-authorized future task, exactly as Section 18 anticipated.
