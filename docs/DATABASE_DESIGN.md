# PIOS Database Design

Status: Draft — Derived from Approved Foundation. This document translates LOGICAL_DATA_MODEL.md and PERSISTENCE_ARCHITECTURE.md into physical storage structures: technology-neutral descriptions of how each domain's information is organized as persisted structures. It selects no specific database product or vendor for any domain — that remains a distinct, later decision for each domain, per the per-domain evaluation ADR-021 already establishes. It is not SQL, DDL, an ORM model, a migration script, or an implementation document.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-021), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [LOGICAL_DATA_MODEL.md](LOGICAL_DATA_MODEL.md), and [PERSISTENCE_ARCHITECTURE.md](PERSISTENCE_ARCHITECTURE.md), in that order of authority. Every physical structure here already exists as a logical entity in LOGICAL_DATA_MODEL.md; none is introduced.

---

## 1. Purpose

Database Design exists to give each domain's already-established persistence responsibility (PERSISTENCE_ARCHITECTURE.md) a physical shape — how its information is organized as storable structures — without yet selecting the technology that will realize it. It is the last architectural document before a domain's specific technology selection (ADR-021) and schema implementation, and it exists so that step can begin from a concrete, technology-neutral physical design rather than directly from logical concepts.

## 2. Database Design Principles

- **Domain Ownership.** Physical storage structures follow exactly the domain boundaries already established (ADR-005, ADR-019; PERSISTENCE_ARCHITECTURE.md Section 3); no physical structure spans more than one domain's ownership.
- **Data Integrity.** A physical structure is designed to enforce, to whatever extent its eventual technology allows, the invariants already established (DOMAIN_MODEL.md Section 11; LOGICAL_DATA_MODEL.md Section 7).
- **Consistency.** Physical design preserves the consistency philosophy already established (PERSISTENCE_ARCHITECTURE.md Section 6): strict consistency within a domain's own boundary, eventual consistency across domains.
- **Evolution.** A physical structure evolves only through a decision that explicitly supersedes the one that established it (ADR-015), never silently.
- **Performance Awareness.** Physical design anticipates the access patterns already implied by each domain's own queries (APPLICATION_ARCHITECTURE.md Section 7), without committing to a specific optimization technique or product.
- **Maintainability.** A physical structure remains comprehensible and modifiable as the domain it serves grows, consistent with the Quality Principles in PROJECT_CONSTITUTION.md Section 14.
- **Security Awareness.** A physical structure accounts for the boundary and least-privilege principles already established (ADR-011), without specifying an implementation mechanism.

## 3. Database Strategy Alignment

ADR-021 establishes that database technology is evaluated and selected independently for each domain, never shared across a domain boundary; PERSISTENCE_ARCHITECTURE.md Section 3 establishes each domain's persistence responsibility and boundary. This document's physical structures are organized identically to that boundary — one set of physical structures per domain, matching PERSISTENCE_ARCHITECTURE.md Section 3 exactly — so that whichever technology a domain's own evaluation eventually selects against the criteria in ADR-021, the physical structures described here remain a valid, unmodified input to it. No structure in this document presumes a relational, document, or distributed technology; each is described in terms that hold regardless of which category a domain later chooses.

## 4. Database Boundaries

Each domain below owns exactly the physical storage responsibility stated, consistent with PERSISTENCE_ARCHITECTURE.md Section 3. No physical structure crosses into another domain's boundary.

| Domain | Owned Storage Responsibility | Data Authority | Boundary |
| --- | --- | --- | --- |
| Order Management | Physical structures for Order. | Final authority on an order's persisted state. | Crossed only through Order Management's declared interaction or published events. |
| Dispatch | Physical structures for Assignment. | Final authority on whether, and to whom, an order has been assigned. | Only Dispatch creates or changes Assignment structures. |
| Driver Management | Physical structures for Driver, including availability. | Final authority on a driver's standing and availability. | Only Driver Management changes Driver structures. |
| Passenger Experience | Physical structures for Passenger, Corporate Customer, and its own side of Personal Client Relationship. | Final authority on passenger and corporate customer representation. | Only Passenger Experience changes its own structures. |
| Administration | Physical structures for Administrator and governance records. | Final authority on participant standing and governance decisions. | Only Administration changes governance structures. |
| Analytics | Physical structures for Insight only. | Final authority only for the insight itself. | Never holds the underlying information insight is derived from. |
| Notifications | Physical structures for Notification only. | Final authority only for the record of what was communicated. | Never holds the underlying information that triggers a notification. |
| Payments | Physical structures for Payment Record. | Final authority for the platform's own payment record. | Never holds external settlement information. |

## 5. Physical Data Structures

Every entity below already exists in LOGICAL_DATA_MODEL.md Section 4. No SQL, table, or column is defined; "storage representation" describes shape only, in terms independent of any specific technology.

| Entity | Purpose | Owner Domain | Storage Representation | Main Data Groups | Lifecycle Relationship |
| --- | --- | --- | --- | --- | --- |
| Order | A request for transportation from submission through completion or cancellation. | Order Management | One physical record per order, uniquely identifiable, updated as its status changes. | Order information (LOGICAL_DATA_MODEL.md Section 6). | DOMAIN_MODEL.md Section 12. |
| Assignment | The outcome of allocating an order to a driver. | Dispatch | One physical record per assignment, uniquely identifiable, referencing the order and driver it connects. | Assignment information. | DOMAIN_MODEL.md Section 12. |
| Driver | A driver's standing, availability, and participation. | Driver Management | One physical record per driver, with availability represented as an independently updatable part of that record. | Driver information; availability information. | DOMAIN_MODEL.md Section 12. |
| Passenger | A person originating or receiving transportation demand. | Passenger Experience | One physical record per passenger. | Passenger / Corporate Customer information. | Not catalogued. |
| Corporate Customer | An organization originating aggregated transportation demand. | Passenger Experience | One physical record per corporate customer. | Passenger / Corporate Customer information. | Not catalogued. |
| Personal Client Relationship | A recognized, recurring relationship between a driver and a passenger or corporate customer. | Passenger Experience and Driver Management, each maintaining its own side | Each owning domain holds its own physical record of the relationship from its own perspective; no single, shared physical record spans both domains. | Passenger / Corporate Customer information; Driver information. | Not catalogued. |
| Administrator | A participant responsible for platform oversight and governance. | Administration | One physical record per administrator. | Administrator information. | Not catalogued. |
| Payment Record | The platform's own record of payment-related information associated with an order. | Payments | One physical record per payment, referencing the order it is associated with. | Payment information. | Not catalogued. |
| Notification | A record of what has been communicated to a participant. | Notifications | One physical record per communication sent, typically retained as an append-only historical record rather than updated in place. | Notification information. | Not catalogued. |
| Insight | A unit of derived, aggregate understanding about platform or ecosystem behavior. | Analytics | One physical record per unit of derived understanding; whether it is retained as an append-only history or periodically refreshed is left to Analytics' own future evaluation, not decided here. | Insight information. | Not catalogued. |

**Domain Decision Required:** Fleet remains, as in every prior data document, owned by no ratified domain. No physical structure is created for it here.

## 6. Relationships

Described by meaning only; no SQL foreign key is specified. Each relationship already exists in LOGICAL_DATA_MODEL.md Section 5.

- **Entity relationship meaning.** An Order relates to an Assignment once Dispatch allocates it to a driver; an Assignment relates to the Driver it was allocated to; an Order relates to a Passenger or Corporate Customer depending on its origin; a Driver may relate to a Personal Client Relationship; a Payment Record relates to the Order it was generated for; a Notification relates to the participant it informs and the occurrence that triggered it; an Insight relates to the domains whose behavior it describes.
- **Reference relationship.** Where one domain's physical structure relates to another's, it holds only a reference to that other structure's identity, never a copy of the other domain's owned information, consistent with the Forbidden Ownership principle in PERSISTENCE_ARCHITECTURE.md Section 5.
- **Integrity relationship.** Because domains persist independently and never share a database (ADR-005, ADR-009), a reference cannot be enforced by a single, cross-domain database constraint. A reference is expected to point to information that exists in the referenced domain at the time it is created; maintaining that expectation over time is the responsibility of the referencing domain reacting to the referenced domain's published events (ADR-003), not a database-level guarantee spanning domain boundaries.

## 7. Constraints

Conceptual only; no database constraint is defined.

- **Data integrity constraints.** Within a domain's own physical structures, integrity means that structure always reflects that domain's own invariants — for example, an Order's physical record always reflects exactly one current status (DOMAIN_MODEL.md Section 11).
- **Lifecycle constraints.** A physical structure's state always corresponds to a valid position in the entity's lifecycle (DOMAIN_MODEL.md Section 12); a completed Order's physical record cannot be returned to an active state, consistent with the Lifecycle Consistency principle in PERSISTENCE_ARCHITECTURE.md Section 6.
- **Ownership constraints.** No physical structure is created, changed, or removed by anything other than the domain that owns it (ADR-005, ADR-009); this holds physically exactly as it holds logically.

## 8. Indexing Strategy

Principles only; no specific index is created.

- **Query Optimization.** A domain's physical structures anticipate the information retrieval already established for it — for example, Order Management anticipates retrieval by an order's identity and by its status (APPLICATION_ARCHITECTURE.md Section 7); Driver Management anticipates retrieval by availability state.
- **Access Patterns.** Domains differ in read and write characteristics already implied by their own nature — Order and Assignment are expected to be retrieved and updated frequently during an active lifecycle; Notification and Insight are expected to be written once and read as history more often than updated.
- **Performance Considerations.** How a domain's eventual technology satisfies these access patterns — through indexing or any other mechanism — is left to that domain's own technology evaluation (ADR-021); this document establishes only that indexing decisions should follow the access patterns already implied by the domain's own queries, not the reverse.

## 9. Security Considerations

- **Data protection principles.** Sensitive information — Payment Record and Personal Client Relationship in particular — is protected consistent with Security by Design and Least Privilege (ADR-011); a physical structure never becomes accessible to a domain other than its owner by virtue of physical proximity or shared infrastructure.
- **Access boundaries.** Physical access to a domain's storage is limited to that domain itself (ADR-005, ADR-009, ADR-011); no shared credential or access path spans a domain boundary.
- **Sensitive information handling.** Payment Record and Personal Client Relationship warrant particular care, consistent with the Trust principle in PROJECT_CONSTITUTION.md Section 3 and least privilege; no specific protection mechanism — encryption, masking, or otherwise — is defined here, as that is an implementation decision left to each domain's own technology selection.

## 10. Evolution Strategy

Physical structures evolve the same way interfaces and events do: a new version is introduced alongside the one it replaces, with a stated migration window, before the old structure is retired, consistent with ADR-010 and ADR-015. Backward compatibility is maintained for that window. No physical structure changes without a decision that traces back through this document to LOGICAL_DATA_MODEL.md and, where ownership itself changes, to ADR-019; a change to which technology category is appropriate for a domain follows the same per-domain evaluation already established in ADR-021, not a platform-wide migration.

## 11. Traceability

| Section | ADR | System Architecture | Logical Data Model | Persistence Architecture |
| --- | --- | --- | --- | --- |
| 1. Purpose | ADR-021 | — | Section 1 | Section 1 |
| 2. Database Design Principles | ADR-001, ADR-005, ADR-009, ADR-011, ADR-015, ADR-019, ADR-021 | — | Section 7 | Sections 2, 6 |
| 3. Database Strategy Alignment | ADR-005, ADR-021 | — | — | Section 3 |
| 4. Database Boundaries | ADR-005, ADR-018, ADR-019 | — | Section 3 | Section 3 |
| 5. Physical Data Structures | ADR-002, ADR-019 | — | Sections 4, 6 | Section 3 |
| 6. Relationships | ADR-003, ADR-005, ADR-009 | — | Section 5 | Section 5 |
| 7. Constraints | ADR-002, ADR-005, ADR-009 | — | Section 7 | Section 6 |
| 8. Indexing Strategy | ADR-021 | — | — | Section 8 |
| 9. Security Considerations | ADR-011 | Section 14 | — | — |
| 10. Evolution Strategy | ADR-010, ADR-015, ADR-019, ADR-021 | Section 16 | Section 10 | Section 7 |

Where this document is silent — including on Fleet having no physical structure, and on which specific technology any domain will use — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
