# PIOS Event Catalog

Status: Draft — Derived from Approved Foundation. This document is a business-language catalog of the domain events that exist within PIOS: what meaningful facts occur, why they matter, which domain owns them, and what they represent. It is not a message broker design, a technical event architecture, an integration specification, an API contract, a database model, or a workflow engine.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), and [DOMAIN_MODEL.md](DOMAIN_MODEL.md), in that order of authority. Every event catalogued here already appears in DOMAIN_MODEL.md Section 8; this document does not introduce any event the Domain Model does not already establish.

---

## 1. Purpose

Events give PIOS a shared, business-readable record of what has actually happened in the domain, independent of how any single domain is implemented. Because each domain owns its data exclusively (ADR-005, ADR-009) and no domain may access another domain's internals directly, an event is the only means by which one domain's facts become known to another (ADR-003, ADR-020). This catalog exists so that every meaningful fact PIOS recognizes is named, owned, and explained in one place, consistent with the Single Source of Truth principle in ADR-001.

## 2. Event Philosophy

- **Events represent completed facts.** An event describes something that has already happened, never an instruction for something to happen; ADR-003 is explicit that events are not used to issue commands.
- **Events belong to domain ownership.** An event is created and owned by exactly the domain that owns the fact it describes (ADR-005, ADR-009); no other domain may create an event on another domain's behalf or redefine what an event means.
- **Events improve transparency and traceability.** Because a business event is available to every domain that depends on it, the platform's behavior becomes explainable after the fact, consistent with the Transparency principle in PROJECT_CONSTITUTION.md Section 3 and the Traceability principle in ADR-001.

## 3. Event Naming Principles

- An event name is a compound noun phrase in the past tense, describing what happened — for example, a subject followed by a past-tense verb, such as "SomethingHappened."
- An event name never takes an imperative or command form; "CreateOrder" or "AssignDriver" are commands, not events, and are catalogued separately in DOMAIN_MODEL.md Section 9, not here.
- An event name uses business language only. It never names a technology, a table, a queue, or any implementation detail.
- An event name identifies what occurred, not who will act on it or how.

## 4. Domain Event Catalog

Every event in this catalog is documented with exactly five properties: Event Name, Owner Domain, Meaning, Why It Exists, and Related Aggregate. No payload, field, or schema is defined for any event. The catalog is organized by the domain area each event belongs to in Sections 5 through 9.

## 5. Order Domain Events

Owner Domain: Order Management. These correspond to the Order lifecycle in DOMAIN_MODEL.md Sections 4 and 12.

| Event Name | Owner Domain | Meaning | Why It Exists | Related Aggregate |
| --- | --- | --- | --- | --- |
| OrderSubmitted | Order Management | An order has been received, marking the beginning of its lifecycle. | Establishes that a transportation request now exists and is being tracked, before any assignment has occurred. | Order |
| OrderCancelled | Order Management | An order or its assignment has been terminated before completion. | Records that the order will not proceed to completion, consistent with the invariant that cancellation may occur only before completion. | Order |
| OrderCompleted | Order Management | An order, having proceeded through its ride, has reached completion. | Marks the order's terminal state, after which it cannot return to an active state. | Order |

## 6. Dispatch Domain Events (Proposal and Assignment)

Owner Domain: Dispatch. These correspond to the Proposal and Assignment aggregates in DOMAIN_MODEL.md Section 4. No matching, candidate-selection, or assignment algorithm is described or implied; ADR-002 explicitly excludes that from every level of this documentation. **Documentation sync note (Sprint 1 — Foundation Stabilization):** this section was retitled from "Assignment Domain Events" and the four Proposal rows below were added to bring this catalog into agreement with DOMAIN_MODEL.md Section 8 and the Proposal implementation already in place since Sprint IMPLEMENTATION-002; the section number is kept unchanged so existing cross-references to "EVENT_CATALOG.md Section 6" elsewhere in this repository remain valid.

| Event Name | Owner Domain | Meaning | Why It Exists | Related Aggregate |
| --- | --- | --- | --- | --- |
| OrderProposed | Dispatch | Dispatch has proposed a specific driver for a specific order, prior to that driver's own confirmation. | Records the existence of the pre-commitment business fact ratified by Product Decision: Opportunity Before Assignment v1.0, distinct from the settled outcome Assignment represents. | Proposal |
| ProposalAccepted | Dispatch | A proposed driver has confirmed the Proposal. | Marks the point at which the Proposal resolves positively, which is the precondition for Assignment's own creation, per ADR-035. | Proposal |
| ProposalDeclined | Dispatch | A proposed driver has refused the Proposal. | Records a negative resolution before any obligation existed, per Product Decision: Opportunity, Acceptance & Commitment Semantics v1.0 Section 3 — frees the order for a new Proposal. | Proposal |
| ProposalLapsed | Dispatch | No response arrived to a Proposal while waiting remained appropriate. | Records a non-response resolution, per Product Decision: Electronic Dispatcher MVP Blockers v1.0 (Proposal Timeout Policy) — frees the order for a new Proposal. | Proposal |
| OrderAssigned | Dispatch | Dispatch has connected an order to a driver. | Records the outcome of the assignment decision that only Dispatch may make, per ADR-002. | Assignment |
| AssignmentAccepted | Dispatch | A proposed assignment has been confirmed. | Marks the point at which an assignment is confirmed to proceed, which Order Management depends on to know the order will proceed toward a ride. | Assignment |
| AssignmentArrived | Dispatch | The driver connected to an assignment has reached the passenger. | Records the first ride-progress fact in the assignment's own lifecycle (ADR-040 Decision item 1). Ride progress belongs to Assignment, not to Order — `Order.kt`'s own KDoc states the ride states "remain outside this scope; they are not modeled here." | Assignment |
| AssignmentStarted | Dispatch | The ride itself has begun. | Records the transition from the driver's arrival to the ride in progress (ADR-040 Decision item 1), a fact only Dispatch owns because only Assignment models it. | Assignment |
| AssignmentCompleted | Dispatch | The ride connected to an assignment has finished. | Records the terminal ride fact. Order Management relies on it to complete the corresponding Order (ADR-041 Decision item 1), which makes it a cross-domain business event — see Section 9. | Assignment |

**Documentation sync note (Sprint 3 — Order Consistency).** The three `Assignment*` ride-lifecycle rows above were added to close the documentation debt [ADR-041](ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) recorded in its own Consequences section: *"`EVENT_CATALOG.md` Section 6 (Dispatch domain events) and Section 9 (cross-domain events) currently list neither `AssignmentCompleted` nor `AssignmentArrived`/`AssignmentStarted` at all ... it is assigned to Sprint 3's Documentation Hygiene item and is a prerequisite for closing that item, not optional cleanup."* All three facts were already ratified by ADR-040 Decision item 1 and already implemented; this catalog was simply not updated at the time. No event is introduced here that ADR-040/ADR-041 do not already establish, and no payload or schema is defined (Section 4). The section number is again kept unchanged so existing cross-references to "EVENT_CATALOG.md Section 6" remain valid.

The four Proposal events above are not currently business events under ADR-003 — no other domain relies on any of them, and none is published outside Dispatch's own process boundary today (no outbox record is written for them; see `ProposalApplicationService`'s own documented reasoning, unchanged by this sync). This mirrors the same treatment Section 5 already gives OrderSubmitted/OrderCancelled/OrderCompleted before Section 9 identifies which events actually cross a domain boundary.

## 7. Driver Domain Events

Owner Domain: Driver Management. This corresponds to the Driver aggregate in DOMAIN_MODEL.md Section 4. No location, tracking, or positioning data is described or implied; DOMAIN_MODEL.md defines Availability only as a driver's own declaration of readiness, not a location signal.

| Event Name | Owner Domain | Meaning | Why It Exists | Related Aggregate |
| --- | --- | --- | --- | --- |
| DriverAvailabilityChanged | Driver Management | A driver's declared readiness to receive an assignment has changed. | Makes the driver's current availability state known to the domains that depend on it, without those domains owning the driver's information themselves. | Driver |

## 8. Passenger Domain Events

No event owned by Passenger Experience is currently justified. DOMAIN_MODEL.md Sections 4 and 8 define no lifecycle, invariant, or event for the Passenger or Corporate Customer entities beyond their existence as entities (DOMAIN_MODEL.md Section 5). Inventing a passenger-owned event here would exceed what the Domain Model establishes; this section records that absence rather than filling it.

## 9. Cross-Domain Events

DOMAIN_MODEL.md Section 8 identifies which of the events above become business events, per ADR-003, because another domain relies on them:

- **DriverAvailabilityChanged** is relied upon by Dispatch, which needs to know which drivers are currently available in order to make an assignment.
- **AssignmentAccepted** is relied upon by Order Management, which needs to know an assignment has been confirmed in order to track the order's status.
- **AssignmentCompleted** is relied upon by Order Management, which needs to know the ride has finished in order to transition its own Order to `COMPLETED`. Added by [ADR-041](ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md), whose Decision item 1 makes it *"the single trigger that completes an Order."* Consistent with Section 10's ownership rules, Order Management acts only on the fact the event represents: `AssignmentStatus.COMPLETED` remains Dispatch's authority on the *ride*, `OrderStatus.COMPLETED` remains Order Management's authority on the *order* (ADR-041 Decision item 2) — two facts, causally linked, not one fact stored twice.

**AssignmentArrived and AssignmentStarted are deliberately not cross-domain events.** Both are published by Dispatch, and no domain consumes either. ADR-041 Decision item 4 is explicit: *"Order Management binds **only** `assignment.completed` in addition to the existing `assignment.accepted`; it does **not** bind `assignment.arrived` or `assignment.started`."* Ride-progress states remain exclusively on Assignment; this section records that absence rather than manufacturing a consumer for it, the same way Section 8 records the absence of any Passenger-owned event.

In addition, the Notification Delivery and Analytics Aggregation domain services (DOMAIN_MODEL.md Section 7) are each described as producing their output from information obtained from other domains, without owning that information themselves. This means any event in this catalog may, in principle, be relied upon by Notifications or Analytics; the Domain Model does not name which specific events each of them consumes, and this document does not invent that specificity. [ADR-033: Notifications Module and Event Consumption Boundary](ADR/ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) has since examined this specific gap for Notifications and confirmed it remains open, pending a product/business decision — it does not resolve the silence, only records that it was deliberately examined and left unresolved rather than overlooked.

## 10. Event Ownership Rules

- **Who creates an event.** The domain that owns the fact the event describes, per the ownership mapping in DOMAIN_MODEL.md Section 4 and ADR-019, creates the corresponding event. No domain creates an event describing a fact it does not own.
- **Who owns meaning.** The creating domain is the sole authority on what its event means and when it occurs. No consuming domain may redefine, reinterpret, or override that meaning, consistent with the exclusive-ownership rule in ADR-005.
- **Who consumes information.** Any domain may consume an event published by another domain, through the declared event mechanism in ADR-003. Consuming an event never grants ownership over the event's meaning or the information that produced it; a consuming domain acts only on the fact that the event represents, consistent with ADR-009.

## 11. Event Traceability

| Event | Constitution | ADR | Product Foundation | System Architecture | Domain Model |
| --- | --- | --- | --- | --- | --- |
| OrderSubmitted | Section 3 (Transparency) | ADR-005, ADR-009 | Section 10 (Order, Status) | Section 9 | Sections 4, 8 |
| OrderCancelled | Section 3 (Transparency) | ADR-005, ADR-009 | Section 10 (Cancellation) | Section 9 | Sections 4, 11 |
| OrderCompleted | Section 3 (Transparency) | ADR-005, ADR-009 | Section 10 (Status) | Section 9 | Sections 4, 11 |
| OrderProposed | Section 3 (Fair Dispatch) | ADR-002, ADR-003, ADR-035 | Section 10 (Assignment) | Sections 6, 9 | Section 4 |
| ProposalAccepted | Section 3 (Fair Dispatch) | ADR-002, ADR-003, ADR-035 | Section 10 (Acceptance) | Sections 6, 9 | Section 4 |
| ProposalDeclined | Section 3 (Fair Dispatch) | ADR-002, ADR-003, ADR-035 | Section 10 (Acceptance) | Sections 6, 9 | Section 4 |
| ProposalLapsed | Section 3 (Fair Dispatch) | ADR-002, ADR-003, ADR-035 | Section 10 (Acceptance) | Sections 6, 9 | Section 4 |
| OrderAssigned | Section 3 (Fair Dispatch) | ADR-002, ADR-003 | Section 10 (Assignment) | Sections 6, 9 | Sections 4, 7 |
| AssignmentAccepted | Section 3 (Fair Dispatch) | ADR-002, ADR-003 | Section 10 (Acceptance) | Sections 6, 9 | Sections 4, 8, 11 |
| AssignmentArrived | Section 3 (Transparency) | ADR-002, ADR-003, ADR-040 | Section 10 (Assignment) | Sections 6, 9 | Sections 4, 12, 13 |
| AssignmentStarted | Section 3 (Transparency) | ADR-002, ADR-003, ADR-040 | Section 10 (Assignment) | Sections 6, 9 | Sections 4, 12, 13 |
| AssignmentCompleted | Section 3 (Transparency) | ADR-002, ADR-003, ADR-040, ADR-041 | Section 10 (Status) | Sections 6, 9 | Sections 4, 12, 13 |
| DriverAvailabilityChanged | Section 3 (Driver Ownership) | ADR-005, ADR-009 | Section 10 (Availability) | Sections 3, 9 | Sections 4, 8, 11 |

Where this document is silent — including on Passenger Domain Events in Section 8, and on which specific events Notifications or Analytics consume in Section 9 (examined, and left open pending a product decision, by ADR-033) — no lower-priority document may fill that silence by invention; resolution requires DOMAIN_MODEL.md, or USE_CASE_CATALOG.md/PRODUCT_FOUNDATION.md for the Section 9 gap specifically, to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
