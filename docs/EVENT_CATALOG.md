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

## 6. Assignment Domain Events

Owner Domain: Dispatch. These correspond to the Assignment aggregate in DOMAIN_MODEL.md Section 4. No matching or assignment algorithm is described or implied; ADR-002 explicitly excludes that from every level of this documentation.

| Event Name | Owner Domain | Meaning | Why It Exists | Related Aggregate |
| --- | --- | --- | --- | --- |
| OrderAssigned | Dispatch | Dispatch has connected an order to a driver. | Records the outcome of the assignment decision that only Dispatch may make, per ADR-002. | Assignment |
| AssignmentAccepted | Dispatch | A proposed assignment has been confirmed. | Marks the point at which an assignment is confirmed to proceed, which Order Management depends on to know the order will proceed toward a ride. | Assignment |

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
| OrderAssigned | Section 3 (Fair Dispatch) | ADR-002, ADR-003 | Section 10 (Assignment) | Sections 6, 9 | Sections 4, 7 |
| AssignmentAccepted | Section 3 (Fair Dispatch) | ADR-002, ADR-003 | Section 10 (Acceptance) | Sections 6, 9 | Sections 4, 8, 11 |
| DriverAvailabilityChanged | Section 3 (Driver Ownership) | ADR-005, ADR-009 | Section 10 (Availability) | Sections 3, 9 | Sections 4, 8, 11 |

Where this document is silent — including on Passenger Domain Events in Section 8, and on which specific events Notifications or Analytics consume in Section 9 (examined, and left open pending a product decision, by ADR-033) — no lower-priority document may fill that silence by invention; resolution requires DOMAIN_MODEL.md, or USE_CASE_CATALOG.md/PRODUCT_FOUNDATION.md for the Section 9 gap specifically, to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
