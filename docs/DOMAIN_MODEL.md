# PIOS Domain Model

Status: Draft — Derived from Approved Foundation. This document is the technology-independent domain model of PIOS: the business concepts, their boundaries, and the rules that govern them, independent of any database, API, event schema, programming language, or deployment choice. It is intended to remain valid regardless of what technology later implements it.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), and the ADR Foundation (ADR-001 through ADR-020), in that order of authority. Where a concept would require a decision that no existing document makes, it is marked **Domain Decision Required** rather than resolved by invention, consistent with the Documentation Driven Development discipline in [ADR-006](ADR/ADR-006-Documentation-Driven-Development.md). The Proposal aggregate (Section 4) additionally derives from [ADR-035](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) and the Product Decision chain it implements — [Opportunity Before Assignment v1.0](PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md), [Opportunity, Acceptance & Commitment Semantics v1.0](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), and [Electronic Dispatcher MVP Blockers v1.0](PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md) — each already cited at its point of use below, consistent with the Documentation Hierarchy in PROJECT_CONSTITUTION.md Section 5.

---

## 1. Purpose

This document formalizes the business domain already established by the Constitution, the Product Foundation, and the System Architecture into the concepts a domain model addresses: domains, aggregates, entities, value objects, domain services, events, commands, queries, invariants, lifecycles, and relationships. It becomes the single source of truth that any future API, database, event, or implementation design must remain consistent with. It contains no database, API, event schema, or implementation detail, so that it remains valid regardless of what technology later realizes it.

## 2. Domain Philosophy

The domain is capability-driven because the architecture it sits within already is. ADR-001 established that PIOS is composed of loosely coupled, boundary-first components rather than technical layers. ADR-016 ratified that organization as Capability-Based architecture. ADR-017 named eight bounded contexts, and ADR-018 ratified them as architectural components, each with an exclusive responsibility (ADR-009) and exclusive data ownership (ADR-005, ADR-019). A domain model that introduced a different decomposition — grouping business concepts by data shape or convenience rather than by these already-ratified capability boundaries — would create a second, conflicting source of truth, contradicting the Single Source of Truth principle in ADR-001. This domain model therefore mirrors the eight capability boundaries exactly: every domain concept described here belongs to exactly one of them.

## 3. Core Domains

The domain model recognizes the eight architectural components ratified in ADR-018, each with the responsibility already established there:

- **Dispatch.** The assignment decision connecting a transportation request to a driver.
- **Order Management.** Receiving and tracking transportation demand from any source through its lifecycle.
- **Driver Management.** Representing drivers within the platform, including their standing, availability, and participation.
- **Passenger Experience.** Representing passengers and corporate customers and their interaction with the platform.
- **Payments.** The platform's own record of payment-related information associated with transportation demand.
- **Administration.** Supporting platform administrators in overseeing and governing the ecosystem.
- **Analytics.** Producing insight into how the platform and its ecosystem are behaving and performing.
- **Notifications.** Informing participants of information relevant to them.

## 4. Aggregates

An aggregate is described here only where a purpose, an invariant, and a lifecycle are each already derivable from approved documentation.

### Order (Order Management)

- **Purpose.** Represents a request for transportation from the point it is submitted through its completion or cancellation.
- **Invariant.** An order has exactly one current status at any time, and originates from exactly one source — platform-aggregated demand, a corporate customer, or a driver's personal client relationship.
- **Lifecycle.** An order is submitted; it may be assigned to a driver by Dispatch; an assigned order is subject to acceptance; an accepted order proceeds to the transportation itself (referred to as a ride once underway); and it is completed or, at any point before completion, cancelled.

### Proposal (Dispatch)

- **Purpose.** Represents that Dispatch has proposed a specific driver for a specific order, prior to that driver's own confirmation — ratified as existing, and as distinct from Assignment, by Product Decision: Opportunity Before Assignment v1.0 and by [ADR-035](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md). **Documentation sync note (Sprint 1 — Foundation Stabilization):** neither that Product Decision nor ADR-035 itself names this aggregate; the name **Proposal** used here is the name already established and consistently used across implementation since Sprint IMPLEMENTATION-002, recorded here to bring this document into agreement with that already-settled usage, not to originate a new naming decision.
- **Invariant.** An order may have at most one open Proposal at any time — ratified by Product Decision: Electronic Dispatcher MVP Blockers v1.0 (Concurrent Proposal Policy: "strictly sequential, max one `OPEN` Proposal per order, no broadcast without a new decision"). The mechanism by which a Proposal's outcome is recognized is now fully assigned: what triggers a lapse is Dispatch's own application layer, per [ADR-051](ADR/ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) (ownership) and [ADR-052](ADR/ADR-052-Proposal-Lapse-Resolution-Mechanism.md) (mechanism) — both Accepted, closing what this document previously marked **Domain Decision Required**; what triggers a withdrawal is Order Management's own `OrderCancelled`, consumed by Dispatch, per [ADR-053](ADR/ADR-053-Proposal-Resolution-on-Order-Cancellation.md) (Accepted).
- **Lifecycle.** A Proposal is created open; it resolves, exactly once, to one of **four** outcomes — confirmed by the driver, refused by the driver, lapsed for want of a timely response, or withdrawn because the order it was for was cancelled before any driver acceptance (Product Decision: Opportunity, Acceptance & Commitment Semantics v1.0 Section 9; Product Decision: Electronic Dispatcher MVP Blockers v1.0; ADR-053). Its positive resolution is a precondition for Assignment's own creation (see Assignment, below); it has no supported transition back to open once resolved.

### Assignment (Dispatch)

- **Purpose.** Represents the outcome of Dispatch allocating a specific order to a specific driver. Per ADR-035, this outcome exists only once the Proposal above has resolved positively; Assignment itself is not, and does not represent, that prior, unresolved proposal.
- **Invariant.** An order cannot be connected to more than one active assignment at a time, and only Dispatch may create or change an assignment.
- **Lifecycle.** An assignment is made by Dispatch in connection with an order; it is subject to acceptance; an accepted assignment stands until the order it is connected to is completed or cancelled.

### Driver (Driver Management)

- **Purpose.** Represents a driver's standing, availability, and participation within the platform.
- **Invariant.** A driver has exactly one current availability state at any time.
- **Lifecycle.** A driver's standing and participation evolve over the driver's relationship with the platform; independently, their availability changes as they declare their own readiness to receive an assignment.

Product Foundation defines an order as existing "before or as it becomes a ride"; a ride is therefore treated here as a phase of the Order aggregate's lifecycle, not a separate aggregate, and is not separately owned.

## 5. Entities

The aggregate roots in Section 4 are themselves entities. The following are additional conceptual entities, described without attributes, identifiers, or any database representation:

- **Passenger** (Passenger Experience) — a person represented within the platform as the originator or recipient of transportation demand.
- **Corporate Customer** (Passenger Experience) — an organization represented within the platform as the originator of aggregated transportation demand.
- **Personal Client Relationship** (spanning Driver Management and Passenger Experience) — the recognized, recurring relationship between a driver and a passenger or corporate customer.
- **Administrator** (Administration) — a participant represented within the platform as responsible for oversight and governance of the ecosystem.
- **Payment Record** (Payments) — the platform's own record of payment-related information associated with an order.
- **Notification** (Notifications) — a record of what has been communicated to a participant.
- **Insight** (Analytics) — a unit of derived, aggregate understanding about platform or ecosystem behavior.

**Domain Decision Required:** Fleet is recognized as an ecosystem participant in PRODUCT_FOUNDATION.md Sections 6–7 and as an external actor in SYSTEM_ARCHITECTURE.md Section 3, but no ratified domain (ADR-018) or ownership assignment (ADR-019) currently covers fleet-related information. This document does not assign Fleet to a domain by invention; that assignment awaits a future ADR.

## 6. Value Objects

The following concepts are described by their meaning, not their structure, and carry no identity of their own:

- **Status** — the current state of an order, assignment, or ride within its lifecycle.
- **Availability** — a driver's own declaration of their readiness to receive an assignment.
- **Acceptance** — a driver's or passenger's confirmation that a proposed assignment or order will proceed.
- **Cancellation** — the indication that an order or assignment has been terminated before completion.
- **Order Origin** — the distinction of whether an order arose from platform-aggregated demand, a corporate customer, or a driver's personal client relationship.

## 7. Domain Services

The following responsibilities do not belong to a single aggregate and are described here by responsibility only:

- **Dispatch (domain service).** Determines, within the Dispatch domain, which driver an order is assigned to, producing an Assignment. The criteria used are explicitly not a domain modeling concern, consistent with ADR-002.
- **Notification Delivery (domain service).** Determines, within the Notifications domain, what is communicated to a participant in response to information obtained from other domains, without owning the underlying information that triggers it.
- **Analytics Aggregation (domain service).** Produces, within the Analytics domain, derived insight from information obtained from other domains, without owning the underlying information it draws on.

## 8. Domain Events

The following are conceptual occurrences only; no payload or schema is defined for any of them, consistent with ADR-003:

- **Order Submitted** — an order has been received by Order Management.
- **Order Proposed** — Dispatch has proposed a specific driver for a specific order, prior to that driver's own confirmation (Section 4, Proposal).
- **Proposal Accepted** — a proposed driver has confirmed the Proposal.
- **Proposal Declined** — a proposed driver has refused the Proposal.
- **Proposal Lapsed** — no response arrived to a Proposal while waiting remained appropriate.
- **Order Assigned** — Dispatch has connected an order to a driver.
- **Assignment Accepted** — a proposed assignment has been confirmed.
- **Order Cancelled** — an order or its assignment has been terminated before completion.
- **Order Completed** — an order, having proceeded through its ride, has reached completion.
- **Driver Availability Changed** — a driver's declared readiness has changed.

The four Proposal events above are Dispatch-internal domain events only — no other domain currently relies on any of them, so none is a business event under ADR-003, and none is currently published outside Dispatch's own boundary (Section 9's Cross-Domain Events equivalent in EVENT_CATALOG.md records this explicitly, mirroring the same treatment already given to Order Submitted/Cancelled/Completed, which are likewise domain-internal today).

Each of these is a domain event within the domain where it occurs and becomes a business event, per ADR-003, wherever another domain relies on it — for example, Dispatch depending on Driver Availability Changed, or Order Management depending on Assignment Accepted.

## 9. Commands

The following are conceptual requests for a domain to act within its own boundary, consistent with the Communication Model in SYSTEM_ARCHITECTURE.md Section 8; no mechanism is defined for any of them:

- **Submit Order** — directed at Order Management.
- **Propose Driver** — directed at Dispatch.
- **Accept Proposal** — directed at Dispatch.
- **Decline Proposal** — directed at Dispatch.
- **Lapse Proposal** — directed at Dispatch.
- **Assign Order** — directed at Dispatch.
- **Accept Assignment** — directed at Dispatch.
- **Cancel Order** — directed at Order Management.
- **Declare Availability** — directed at Driver Management.
- **Record Payment** — directed at Payments.

## 10. Queries

The following are conceptual requests for information, consistent with the Communication Model in SYSTEM_ARCHITECTURE.md Section 8; no mechanism is defined for any of them:

- **Retrieve Order Status** — from Order Management.
- **Retrieve Assignment** — from Dispatch.
- **Retrieve Driver Availability** — from Driver Management.
- **Retrieve Payment Record** — from Payments.
- **Retrieve Insight** — from Analytics.

## 11. Business Invariants

These are structural invariants derivable from approved documentation, not business rules governing how a decision is made:

- An order has exactly one current status at any time.
- An order may have at most one open Proposal at any time.
- An order cannot be connected to more than one active assignment at a time.
- A driver has exactly one current availability state at any time.
- A completed order cannot return to an active state.
- A cancellation may occur only before an order or assignment is completed.
- Only Dispatch may create or change an Assignment.
- Only the domain that owns a piece of information, per ADR-019, may change it.
- A ride cannot exist without a prior accepted assignment.
- An order originates from exactly one source at the time it is submitted.

## 12. State Lifecycles

Described in prose, conceptually, without workflow diagrams:

- **Order.** Submitted, then either assigned by Dispatch and carried through acceptance, the ride itself, and completion, or cancelled at any point before completion, or — **amended 2026-09-16 by `ADR-077`, recorded here per `ADR-015`** — closed as **unfulfilled** when Dispatch's own routing window for that order expires with no offer ever having been made (`DispatchExhausted` → `OrderUnfulfilled`). Unfulfilled is terminal, reachable only from submitted, and cannot be left; Section 11's invariants ("exactly one current status", "a completed order cannot return to an active state") are unchanged by it. Distinct from cancellation (an act by a participant) and from completion (a ride that happened). **Not deployed as of this amendment's own date** — see `ADR-077` Status.
- **Proposal.** Created open; resolves, exactly once, to confirmed, refused, lapsed, or withdrawn (Product Decision: Electronic Dispatcher MVP Blockers v1.0; ADR-053). Its resolution-recognition mechanism is fully assigned: lapse to Dispatch's own application layer (ADR-051, ADR-052, both Accepted); withdrawal to Dispatch's consumption of Order Management's `OrderCancelled` (ADR-053, Accepted).
- **Assignment.** Made by Dispatch in connection with an order, then accepted; it stands until its order is completed or cancelled.
- **Driver Availability.** Declared by the driver and changed by the driver at will; it exists independently of any single order or assignment.

## 13. Relationships

Described conceptually, with no cardinality or foreign-key semantics:

- An Order becomes connected to an Assignment once Dispatch allocates it to a driver.
- A Proposal precedes the Assignment its positive resolution produces, per ADR-035; a Proposal never becomes an Assignment, it only precedes one.
- An Assignment connects an Order to a Driver.
- An Order relates to a Passenger or a Corporate Customer, depending on its origin.
- A Driver may hold a Personal Client Relationship with a Passenger or Corporate Customer, independent of any single Order.
- A Payment Record relates to the Order it is associated with.
- A Notification relates to the participant it informs and the occurrence that triggered it, without owning that occurrence's underlying information.
- An Insight relates to the domains whose behavior it describes, without owning their underlying information.

## 14. Boundaries

**Belongs to the Domain Model:** the domains, aggregates, entities, value objects, domain services, events, commands, queries, invariants, lifecycles, and relationships described above, at a level of abstraction independent of any technology.

**Does not belong to the Domain Model:** database schemas, tables, or storage design; API endpoints or protocols; event payloads or schemas; programming languages or frameworks; deployment or infrastructure; the specific criteria Dispatch uses to determine an assignment, which ADR-002 explicitly excludes from architecture and this document likewise excludes from the domain model; the mechanics of any integration with an external system, which belong to ADR-020 and SYSTEM_ARCHITECTURE.md Section 11; and any pricing, commission, or regulatory rule, none of which is established by any approved document.

## 15. Traceability

| Section | Constitution | Product Foundation | ADR | System Architecture |
| --- | --- | --- | --- | --- |
| 1. Purpose | Section 5 | — | ADR-006 | — |
| 2. Domain Philosophy | Section 4 | — | ADR-001, ADR-009, ADR-016, ADR-017, ADR-018 | — |
| 3. Core Domains | — | Section 9 | ADR-018 | Section 6 |
| 4. Aggregates | — | Section 10 | ADR-002, ADR-005, ADR-019, ADR-035 | Section 9 |
| 5. Entities | — | Sections 6–7, 10 | ADR-018, ADR-019 | Section 3 |
| 6. Value Objects | — | Section 10 | — | — |
| 7. Domain Services | — | Section 9 | ADR-002, ADR-019 | Section 6 |
| 8. Domain Events | — | Section 10 | ADR-003 | Section 9 |
| 9. Commands | — | Section 10 | — | Section 8 |
| 10. Queries | — | Section 10 | — | Section 8 |
| 11. Business Invariants | — | Section 10 | ADR-002, ADR-005, ADR-009, ADR-019 | Section 9 |
| 12. State Lifecycles | — | Section 10 | ADR-002, ADR-003, ADR-035 | Section 9 |
| 13. Relationships | — | Sections 6, 10 | ADR-005, ADR-019 | Section 10 |
| 14. Boundaries | Section 15 | Sections 8, 14 | ADR-002, ADR-020 | Section 11 |

Where this document is silent or marks a concept **Domain Decision Required**, no lower-priority document may resolve it; resolution requires a new ADR, consistent with the authority order established in Constitution Section 5.
