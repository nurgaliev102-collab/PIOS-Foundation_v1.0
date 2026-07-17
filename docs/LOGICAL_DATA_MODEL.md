# PIOS Logical Data Model

Status: Draft — Derived from Approved Foundation. This document defines logical entities, information structures, relationships, ownership boundaries, and logical constraints, independent of storage technology. It is one level closer to implementation than CONCEPTUAL_DATA_MODEL.md, but it is still not a database schema, SQL model, ORM model, physical storage design, migration plan, or performance design. It is the bridge between the Conceptual Data Model and any future Database Design.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [CONCEPTUAL_DATA_MODEL.md](CONCEPTUAL_DATA_MODEL.md), [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), and [API_SPECIFICATION.md](API_SPECIFICATION.md), in that order of authority. Every logical entity here already exists in CONCEPTUAL_DATA_MODEL.md; none is introduced.

---

## 1. Purpose

Logical modeling gives the information concepts already established in CONCEPTUAL_DATA_MODEL.md enough structure — grouped categories of information, relationships described by meaning, and logical constraints — to be recognizable as the input to a future Database Design, without yet committing to any field, table, or storage technology. It exists so that the step from business concept to physical schema is traceable through an intermediate, technology-independent form.

## 2. Logical Modeling Principles

- **Domain Ownership.** Every logical entity belongs to exactly one domain, consistent with ADR-005, ADR-009, and the ownership already ratified in ADR-019.
- **Consistency.** A logical entity's structure here is consistent with, and never contradicts, its treatment in DOMAIN_MODEL.md and CONCEPTUAL_DATA_MODEL.md.
- **Traceability.** Every logical entity, relationship, and constraint traces to an existing concept in the Domain Model or Conceptual Data Model; none is introduced at this level for the first time.
- **Minimal Duplication.** Where the Conceptual Data Model already states a fact — an entity's purpose, its owner, its lifecycle — this document references it rather than restating it, consistent with Single Source of Truth (ADR-001).
- **Evolution.** A logical entity's structure changes only through a decision that explicitly supersedes the one that established it, consistent with ADR-015, never through silent drift.

## 3. Logical Domains

Each logical domain below owns exactly the logical information stated, consistent with ADR-018 and ADR-019.

- **Order Management.** Owns the Order logical entity. Responsible for receiving and tracking transportation demand from any source through its lifecycle (ADR-018). Its boundary is crossed only through its declared interaction or published events (ADR-003, ADR-004, ADR-009); no other domain may hold or change Order information.
- **Dispatch.** Owns the Assignment logical entity. Responsible for the assignment decision connecting a transportation request to a driver (ADR-002). Only Dispatch may create or change Assignment information (ADR-002, ADR-005).
- **Driver Management.** Owns the Driver logical entity, including its availability information. Responsible for representing drivers, their standing, availability, and participation (ADR-018). Only Driver Management may record a driver's availability state (ADR-005, ADR-009).
- **Passenger Experience.** Owns the Passenger, Corporate Customer, and (jointly with Driver Management) Personal Client Relationship logical entities. Responsible for representing passengers and corporate customers and their interaction with the platform (ADR-018). Only Passenger Experience may hold or change this representation (ADR-005, ADR-009).
- **Administration.** Owns the Administrator logical entity. Responsible for supporting platform administrators in overseeing and governing the ecosystem (ADR-018). Only Administration may hold or change governance and standing information (ADR-005, ADR-009).
- **Analytics.** Owns the Insight logical entity only. Responsible for producing insight into platform and ecosystem behavior (ADR-018). Never owns the underlying information it derives insight from (ADR-005, ADR-019).
- **Notifications.** Owns the Notification logical entity only. Responsible for informing participants of information relevant to them (ADR-018). Never owns the underlying information that triggers a notification (ADR-005, ADR-019).
- **Payments.** Owns the Payment Record logical entity. Responsible for the platform's own record of payment-related information associated with transportation demand (ADR-018). Never owns external settlement information, which is outside PIOS (ADR-020; PRODUCT_FOUNDATION.md Section 8).

## 4. Logical Entities

Every entity below already exists in CONCEPTUAL_DATA_MODEL.md Section 4; none is introduced here. No field or column is described.

| Name | Purpose | Owner Domain | Lifecycle Reference | Description of Information Held |
| --- | --- | --- | --- | --- |
| Order | A request for transportation from submission through completion or cancellation. | Order Management | DOMAIN_MODEL.md Section 12 | The identity of the request, its current status, and its origin (platform, corporate, or personal-client), across its lifecycle. |
| Assignment | The outcome of allocating an order to a driver. | Dispatch | DOMAIN_MODEL.md Section 12 | The connection between a specific order and the driver it has been allocated to, and whether that connection has been accepted. |
| Driver | A driver's standing, availability, and participation. | Driver Management | DOMAIN_MODEL.md Section 12 | A driver's standing and participation within the platform, and their own currently declared availability. |
| Passenger | A person originating or receiving transportation demand. | Passenger Experience | Not catalogued | A person's representation within the platform as the originator or recipient of transportation demand. |
| Corporate Customer | An organization originating aggregated transportation demand. | Passenger Experience | Not catalogued | An organization's representation within the platform as the originator of aggregated transportation demand. |
| Personal Client Relationship | A recognized, recurring relationship between a driver and a passenger or corporate customer. | Passenger Experience and Driver Management, jointly | Not catalogued | The recognized, recurring relationship between a specific driver and a specific passenger or corporate customer. |
| Administrator | A participant responsible for platform oversight and governance. | Administration | Not catalogued | A participant's representation within the platform as responsible for oversight and governance. |
| Payment Record | The platform's own record of payment-related information associated with an order. | Payments | Not catalogued | The platform's own record of payment-related information associated with a specific order. |
| Notification | A record of what has been communicated to a participant. | Notifications | Not catalogued | A record of what was communicated to a specific participant, and the occurrence it responded to. |
| Insight | A unit of derived, aggregate understanding about platform or ecosystem behavior. | Analytics | Not catalogued | A unit of derived, aggregate understanding about the behavior of one or more other domains. |

**Domain Decision Required:** Fleet remains, as in CONCEPTUAL_DATA_MODEL.md, an ecosystem participant owned by no ratified domain. No logical entity is created for it here.

## 5. Entity Relationships

Described by meaning only; no cardinality, foreign key, or join table is specified. Each relationship already exists in CONCEPTUAL_DATA_MODEL.md Section 5.

- **Order relates to Assignment.** An Order becomes connected to an Assignment once Dispatch allocates it to a driver.
- **Assignment relates to Driver.** An Assignment connects an Order to the Driver it was allocated to.
- **Order relates to Passenger or Corporate Customer.** Depending on the Order's origin.
- **Driver relates to Personal Client Relationship.** A Driver may hold one with a Passenger or Corporate Customer, independent of any single Order.
- **Payment Record relates to Order.** A Payment Record is associated with the Order it was generated for.
- **Notification relates to a participant and an occurrence.** A Notification relates to the participant it informs and the occurrence that triggered it, without owning that occurrence.
- **Insight relates to other domains.** An Insight relates to the domains whose behavior it describes, without owning their underlying information.

## 6. Logical Attribute Groups

Categories of information within a logical entity, not a list of fields. Each group corresponds to a value object already established in DOMAIN_MODEL.md Section 6.

- **Order information.** Identity and origin information (Order Origin) and current-state information (Status), together spanning the Order's lifecycle.
- **Assignment information.** Connection information (which order, which driver) and confirmation information (Acceptance), and termination information where a Cancellation applies.
- **Driver information.** Standing and participation information, and separately, availability information (Availability) — a driver's own declared readiness, which changes independently of standing.
- **Passenger / Corporate Customer information.** Representation information, and, where applicable, Personal Client relationship information.
- **Administrator information.** Governance and standing information.
- **Payment information.** The platform's own record of payment-related information associated with a specific Order.
- **Notification information.** What was communicated, and a reference to the occurrence that triggered it — never the occurrence's own underlying information.
- **Insight information.** Derived, aggregate understanding, and a reference to the domains it describes — never their own underlying information.

## 7. Logical Constraints

Formalized from the invariants in DOMAIN_MODEL.md Section 11; no database constraint is defined.

- **Uniqueness concepts.** An Order has exactly one current status at any time. A Driver has exactly one current availability state at any time. An Order cannot be connected to more than one active Assignment at a time.
- **Ownership constraints.** Only the domain that owns a logical entity may create or change it — only Dispatch may create or change an Assignment; only Driver Management may change a Driver's availability; no domain changes information owned by another (ADR-005, ADR-009).
- **Lifecycle consistency.** A completed Order cannot return to an active state. A Cancellation may occur only before an Order or Assignment is completed. A ride — an Order's executing phase — cannot exist without a prior accepted Assignment.

## 8. Event Relationship

Each logical entity below is affected by exactly the domain events already catalogued in EVENT_CATALOG.md; no new event is introduced.

| Event | Logical Entity Affected | Nature of Change |
| --- | --- | --- |
| OrderSubmitted | Order | An Order is recorded as existing. |
| OrderAssigned | Assignment (created); Order (referenced) | An Assignment is recorded, connecting an Order to a Driver. |
| AssignmentAccepted | Assignment | The Assignment's confirmation information changes to accepted. |
| OrderCancelled | Order | The Order's status changes to cancelled. |
| OrderCompleted | Order | The Order's status changes to completed. |
| DriverAvailabilityChanged | Driver | The Driver's availability information changes. |

## 9. Derived Information

Consistent with ADR-019:

- **Source information.** Order, Assignment, Driver, Passenger, Corporate Customer, Personal Client Relationship, Administrator, and Payment Record are each an original source, owned exclusively by the domain named in Section 3.
- **Derived information.** Notification is derived: it records that a communication occurred but is never the source of the fact that triggered it.
- **Analytical information.** Insight is analytical: produced by the Analytics Aggregation domain service (DOMAIN_MODEL.md Section 7) from information obtained from other domains through their declared interactions or published events, never a source for the underlying facts it draws on.

## 10. Future Evolution

A logical entity's structure changes only through a decision that explicitly supersedes the one that established it, consistent with ADR-015; this document is itself superseded, not edited in place, when such a decision is made, consistent with the ADR lifecycle in ADR-007. A change that alters what depends on a logical entity's information is introduced alongside the structure it replaces, with a stated transition period, consistent with ADR-010, before the prior structure is retired. No migration mechanism is described here.

## 11. Traceability

| Section | Constitution | ADR | System Architecture | Domain Model | Conceptual Data Model | Event Catalog | Application Architecture |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 4 | ADR-001 | — | Section 1 | Section 1 | — | Section 1 |
| 2. Logical Modeling Principles | Section 4 | ADR-001, ADR-005, ADR-009, ADR-015, ADR-019 | — | — | Section 2 | — | — |
| 3. Logical Domains | — | ADR-002, ADR-005, ADR-018, ADR-019, ADR-020 | Section 10 | Section 3 | Section 3 | — | Section 5 |
| 4. Logical Entities | — | ADR-019 | — | Sections 4–5 | Section 4 | — | — |
| 5. Entity Relationships | — | ADR-005, ADR-019 | — | Section 13 | Section 5 | — | — |
| 6. Logical Attribute Groups | — | ADR-019 | — | Section 6 | Section 4 | — | — |
| 7. Logical Constraints | — | ADR-002, ADR-005, ADR-009 | — | Section 11 | — | — | — |
| 8. Event Relationship | — | ADR-003 | Section 9 | Sections 8, 11 | Section 7 | Sections 5–7, 10 | — |
| 9. Derived Information | — | ADR-005, ADR-009, ADR-019 | — | Section 7 | Section 6 | — | — |
| 10. Future Evolution | Section 7 (ADR Policy) | ADR-007, ADR-010, ADR-015 | Section 16 | — | Section 10 | — | — |

Where this document is silent — including on Fleet having no logical entity — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
