# PIOS Conceptual Data Model

Status: Draft — Derived from Approved Foundation. This document defines what information exists in PIOS, who owns it, and how information concepts relate, from the business domain perspective. It is a business information model. It is not a database schema, SQL design, table design, ORM model, Entity Framework, data migration plan, or storage architecture.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [EVENT_CATALOG.md](EVENT_CATALOG.md), [USE_CASE_CATALOG.md](USE_CASE_CATALOG.md), [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), and [API_SPECIFICATION.md](API_SPECIFICATION.md), in that order of authority. Every information concept here already exists in DOMAIN_MODEL.md; none is introduced.

---

## 1. Purpose

Conceptual data modeling establishes what information exists in PIOS and who owns it, before any logical or physical data model is designed against it. It formalizes, specifically from an information-ownership perspective, the aggregates, entities, and value objects already established in DOMAIN_MODEL.md and the exclusive ownership mapping already ratified in ADR-019, so that a future Logical Data Model or Database Design has a single, business-grounded source to derive from.

## 2. Data Modeling Principles

- **Ownership First.** No information concept is described in this document without first establishing the single domain that owns it, consistent with ADR-005 and ADR-019.
- **Single Source of Truth.** Each fact has exactly one authoritative source domain; this document never introduces a second source for a fact already owned elsewhere (ADR-001).
- **Domain Ownership.** Only the domain that owns an information concept may originate or change it (ADR-005, ADR-009); every other domain depends on it only through that domain's declared interaction or published events.
- **Derived Information.** Information computed or aggregated from other domains' facts — such as Analytics' insight — is authoritative only for the derivation itself, never for the underlying facts it was derived from (DOMAIN_MODEL.md Sections 5, 11).
- **Traceability.** Every information concept in this document traces to DOMAIN_MODEL.md and to the ADR that establishes its ownership, consistent with the Traceability principle in ADR-001.

## 3. Data Ownership Model

Each domain below owns the information stated and no other, consistent with ADR-019.

### Order Management

- **Information owned.** Orders and their status, from submission through completion or cancellation.
- **Information not owned.** The assignment connecting an order to a driver (Dispatch); the passenger or corporate customer who originated the order (Passenger Experience); payment information associated with the order (Payments).
- **Source of truth.** Order Management is the sole source of truth for an order's current status; DOMAIN_MODEL.md Section 11 establishes that an order has exactly one current status at any time.

### Dispatch

- **Information owned.** Assignments — the outcome of allocating an order to a driver.
- **Information not owned.** The order itself (Order Management); the driver's availability that made the assignment possible (Driver Management).
- **Source of truth.** Dispatch is the sole source of truth for whether, and to whom, an order has been assigned; DOMAIN_MODEL.md Section 11 establishes that only Dispatch may create or change an assignment.

### Driver Management

- **Information owned.** Drivers, including their standing and their own declared availability.
- **Information not owned.** Any assignment a driver is connected to (Dispatch); any order a driver is fulfilling (Order Management).
- **Source of truth.** Driver Management is the sole source of truth for a driver's current availability state; DOMAIN_MODEL.md Section 11 establishes that a driver has exactly one current availability state at any time.

### Passenger Experience

- **Information owned.** Passengers and corporate customers and their interaction with the platform, including any Personal Client relationship.
- **Information not owned.** Any order a passenger or corporate customer has originated (Order Management); any payment information (Payments).
- **Source of truth.** Passenger Experience is the sole source of truth for a passenger's or corporate customer's own representation and Personal Client relationships.

### Administration

- **Information owned.** Platform governance and participant standing within it.
- **Information not owned.** The operational information — orders, assignments, availability — that governance oversees but does not itself originate.
- **Source of truth.** Administration is the sole source of truth for governance decisions and participant standing records.

### Analytics

- **Information owned.** Only the derived, aggregate insight it produces about platform and ecosystem behavior.
- **Information not owned.** The underlying information the insight is derived from, which remains owned by the domain that originated it.
- **Source of truth.** Analytics is the source of truth only for the insight itself, never for the facts it is derived from.

### Notifications

- **Information owned.** Information about what has been communicated to participants.
- **Information not owned.** The underlying information that triggers a notification, which remains owned by the domain responsible for it.
- **Source of truth.** Notifications is the source of truth only for the record of what was communicated, never for the triggering fact.

### Payments (Justification Noted)

- **Information owned.** The platform's own record of payment-related information associated with orders, per ADR-019.
- **Information not owned.** The order itself (Order Management); settlement details held by external banking or financial infrastructure, which is outside PIOS (PRODUCT_FOUNDATION.md Section 8).
- **Source of truth.** Payments is the source of truth for the platform's own payment record, but never for external settlement, consistent with the Synchronization Philosophy in ADR-020. No use case currently drives this record; APPLICATION_ARCHITECTURE.md and API_SPECIFICATION.md both note this absence rather than inventing one. Ownership itself, however, is already established by ADR-019, so it is described here.

## 4. Core Information Concepts

Each concept below already exists in DOMAIN_MODEL.md; none is introduced here. No attribute or identifier is described.

| Concept | Purpose | Owner Domain | Lifecycle Reference |
| --- | --- | --- | --- |
| Order | A request for transportation from submission through completion or cancellation. | Order Management | DOMAIN_MODEL.md Section 12 |
| Assignment | The outcome of allocating an order to a driver. | Dispatch | DOMAIN_MODEL.md Section 12 |
| Driver | A driver's standing, availability, and participation. | Driver Management | DOMAIN_MODEL.md Section 12 |
| Passenger | A person originating or receiving transportation demand. | Passenger Experience | Not catalogued — DOMAIN_MODEL.md Section 5 defines no discrete lifecycle. |
| Corporate Customer | An organization originating aggregated transportation demand. | Passenger Experience | Not catalogued. |
| Personal Client Relationship | A recognized, recurring relationship between a driver and a passenger or corporate customer. | Spans Driver Management and Passenger Experience | Not catalogued. |
| Administrator | A participant responsible for platform oversight and governance. | Administration | Not catalogued. |
| Payment Record | The platform's own record of payment-related information associated with an order. | Payments | Not catalogued. |
| Notification | A record of what has been communicated to a participant. | Notifications | Not catalogued. |
| Insight | A unit of derived, aggregate understanding about platform or ecosystem behavior. | Analytics | Not catalogued. |
| Availability | A driver's own declaration of readiness to receive an assignment. | Part of Driver Management's owned information | DOMAIN_MODEL.md Section 12 |
| Status | The current state of an order or assignment within its lifecycle. | Not independently owned — part of whichever aggregate it describes (Order or Assignment). | DOMAIN_MODEL.md Section 12 |
| Acceptance | A confirmation that a proposed assignment or order will proceed. | Part of Dispatch's or Order Management's owned information, depending on what is being accepted. | DOMAIN_MODEL.md Section 12 |
| Cancellation | An indication that an order or assignment has been terminated before completion. | Part of Order Management's or Dispatch's owned information, depending on what is cancelled. | DOMAIN_MODEL.md Section 12 |
| Order Origin | The distinction of whether an order arose from platform demand, a corporate customer, or a personal client relationship. | Part of Order Management's owned information. | Not catalogued — set once, at submission. |

**Domain Decision Required:** Fleet is recognized as an ecosystem participant (PRODUCT_FOUNDATION.md Sections 6–7) but DOMAIN_MODEL.md Section 5 already marks it as owned by no ratified domain. This document does not assign it an owner or describe a lifecycle for it by invention; that decision awaits a future ADR, consistent with DOMAIN_MODEL.md.

## 5. Conceptual Relationships

No cardinality, foreign key, or schema is implied by any relationship below; each already exists in DOMAIN_MODEL.md Section 13.

- An Order relates to an Assignment once Dispatch allocates the order to a driver.
- An Assignment relates to a Driver, connecting an order to that driver.
- An Order relates to a Passenger or a Corporate Customer, depending on its origin.
- A Driver may relate to a Personal Client Relationship with a Passenger or Corporate Customer, independent of any single Order.
- A Payment Record relates to the Order it is associated with.
- A Notification relates to the participant it informs and the occurrence that triggered it, without owning that occurrence's underlying information.
- An Insight relates to the domains whose behavior it describes, without owning their underlying information.

## 6. Master Data vs Derived Data

- **Authoritative (master) information.** Information a domain owns as its original source, per Section 3: Order (Order Management), Assignment (Dispatch), Driver and Availability (Driver Management), Passenger and Corporate Customer (Passenger Experience), Payment Record (Payments), and governance records (Administration).
- **Calculated information.** Information computed from another domain's authoritative facts without itself being an original source. A Notification is calculated in this sense: it records that a communication occurred, but it is not the source of the fact that triggered it (DOMAIN_MODEL.md Section 5).
- **Analytical information.** Insight, produced by the Analytics Aggregation domain service (DOMAIN_MODEL.md Section 7) from information obtained from other domains through their declared interactions or published events (ADR-005, ADR-009), never through direct access to what those domains own.

## 7. Event Data Relationship

Each domain event already catalogued in EVENT_CATALOG.md corresponds to a change in the information its owning domain holds, and represents the fact that the change has already occurred, not an instruction for one to occur (ADR-003):

- **OrderSubmitted** — Order Management's record of the order now exists.
- **OrderAssigned** — Dispatch's record of the assignment now exists, connecting the order to a driver.
- **AssignmentAccepted** — Dispatch's record of the assignment changes to accepted.
- **OrderCancelled** — Order Management's record of the order changes to cancelled.
- **OrderCompleted** — Order Management's record of the order changes to completed.
- **DriverAvailabilityChanged** — Driver Management's record of the driver's availability changes.

Consistent with the Event Ownership Rules in EVENT_CATALOG.md Section 10, only the domain that owns the affected information may emit the event describing its change; no payload or schema is defined here.

## 8. Data Boundaries

Consistent with ADR-019:

- **What each domain owns.** Exactly the information stated for it in Section 3, and no other domain's information.
- **What domains may reference.** A domain may depend on information it does not own only through the owning domain's declared interaction (ADR-004) or the events it publishes (ADR-003) — never through direct access, consistent with ADR-005 and ADR-009.
- **What domains should not own.** A domain should never claim ownership of information already owned by another — Order Management should never hold assignment information (Dispatch's alone), and Analytics and Notifications should never claim ownership of the underlying facts they consume (DOMAIN_MODEL.md Sections 5, 11).

## 9. External Data Boundaries

Consistent with ADR-020, PIOS treats information held by an external system the same way it treats another domain's boundary: never assumed to be directly accessible, and never assumed to be simultaneously consistent with PIOS's own record.

- **Payment.** Settlement information held by external banking or financial infrastructure is outside PIOS; Payments' own record (Section 3) is the only information PIOS itself holds.
- **Location / Routing.** Physical road, traffic, and routing information is outside PIOS (PRODUCT_FOUNDATION.md Sections 8, 14); no domain owns it.
- **Notifications.** Any external delivery channel's own records of what it delivered are outside PIOS; Notifications' own record (Section 3) covers only what PIOS itself communicated.
- **Identity.** Licensing and certification records held by external regulatory authorities are outside PIOS.

No technical integration mechanism is described for any of these boundaries.

## 10. Future Evolution

An existing information concept's ownership changes only through a new ADR that explicitly supersedes ADR-019 or the relevant domain-level decision, never silently, consistent with ADR-015. A change that alters what a consumer of an information concept can rely on requires a versioned transition with a stated migration window, consistent with ADR-010 and ADR-015, before the prior version is removed. This document itself is superseded the same way, per ADR-007, when a future decision changes what it establishes.

## 11. Traceability

| Section | Constitution | ADR | System Architecture | Domain Model | Event Catalog | Application Architecture | API Specification |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 4 | ADR-001, ADR-019 | — | Section 1 | — | Section 1 | Section 1 |
| 2. Data Modeling Principles | Section 4 | ADR-001, ADR-005, ADR-009, ADR-019 | — | Sections 5, 11 | — | — | Section 2 |
| 3. Data Ownership Model | — | ADR-002, ADR-005, ADR-018, ADR-019, ADR-020 | Section 10 | Sections 3–5, 11 | — | Section 5 | Section 5 |
| 4. Core Information Concepts | — | ADR-019 | — | Sections 4–6 | — | — | — |
| 5. Conceptual Relationships | — | ADR-005, ADR-019 | — | Section 13 | — | — | — |
| 6. Master Data vs Derived Data | — | ADR-005, ADR-009, ADR-019 | — | Sections 5, 7, 11 | — | — | — |
| 7. Event Data Relationship | — | ADR-003 | Section 9 | Sections 8, 11 | Sections 5–7, 10 | — | Section 8 |
| 8. Data Boundaries | — | ADR-003, ADR-004, ADR-005, ADR-009, ADR-019 | Section 10 | Section 11 | — | — | Section 5 |
| 9. External Data Boundaries | — | ADR-011, ADR-020 | Section 11 | — | — | — | Section 12 |
| 10. Future Evolution | Section 7 (ADR Policy) | ADR-007, ADR-010, ADR-015, ADR-019 | Section 16 | — | — | — | Section 9 |

Where this document is silent — including on Fleet having no owning domain — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
