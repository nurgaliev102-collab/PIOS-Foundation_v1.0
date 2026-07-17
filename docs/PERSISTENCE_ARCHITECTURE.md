# PIOS Persistence Architecture

Status: Draft — Derived from Approved Foundation. This document defines the architectural principles governing persistence across PIOS: persistence responsibilities, boundaries, ownership, consistency, and evolution. It is the architectural bridge between LOGICAL_DATA_MODEL.md and any future Physical Database Design. It is not a database schema, SQL, DDL, ORM mapping, migration script, storage implementation, or technology selection.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-021), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [LOGICAL_DATA_MODEL.md](LOGICAL_DATA_MODEL.md), and [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), in that order of authority. It introduces no entity, domain, or ownership decision beyond what these documents already establish.

---

## 1. Purpose

Persistence Architecture exists to define, before any database technology or schema is chosen, the architectural principles that govern how domain-owned information is actually persisted: who is responsible for persisting it, where the boundary of that responsibility lies, and what consistency and evolution guarantees hold. It formalizes ADR-005, ADR-019, and ADR-021 into a single, persistence-specific architecture that every future Database Design must satisfy, regardless of which technology a given domain eventually selects.

## 2. Persistence Principles

- **Domain-Owned Persistence.** Persistence of a piece of information is a property of the domain that owns it (ADR-005, ADR-019); no persistence decision is made independently of domain ownership.
- **Single Source of Truth.** For any given fact, exactly one domain's persisted record is authoritative (ADR-001); a copy held by another domain — Analytics' insight, Notifications' record — is never treated as the source.
- **Persistence Follows Domain Ownership.** The boundary of what a domain persists is exactly the boundary of what that domain owns (CONCEPTUAL_DATA_MODEL.md Section 3; LOGICAL_DATA_MODEL.md Section 3); persistence never extends beyond it.
- **Separation of Operational and Derived Information.** Information a domain originates is persisted with a distinct responsibility from information derived from it elsewhere, consistent with the source, derived, and analytical distinction already established in ADR-019.
- **Evolution Through ADR.** Persistence responsibility, boundaries, or principles change only through a new ADR that explicitly supersedes the decision that established them (ADR-015).
- **Technology Independence.** These principles hold regardless of which storage technology a domain's future Database Design selects, consistent with the per-domain evaluation framework already established in ADR-021.

## 3. Persistence Boundaries

Each domain below persists exactly the information stated, consistent with ADR-005, ADR-018, and ADR-019. No storage engine is described.

### Order Management

- **Persistence responsibility.** Persists the Order logical entity across its lifecycle (LOGICAL_DATA_MODEL.md Section 4).
- **Persistence boundary.** Crossed only through Order Management's declared interaction or published events (ADR-003, ADR-004, ADR-009); no other domain persists Order information.
- **Information authority.** Order Management is the final authority on an order's persisted state.

### Dispatch

- **Persistence responsibility.** Persists the Assignment logical entity.
- **Persistence boundary.** Only Dispatch persists or changes Assignment information (ADR-002, ADR-005).
- **Information authority.** Dispatch is the final authority on whether, and to whom, an order has been assigned.

### Driver Management

- **Persistence responsibility.** Persists the Driver logical entity, including its availability information.
- **Persistence boundary.** Only Driver Management persists a driver's availability state (ADR-005, ADR-009).
- **Information authority.** Driver Management is the final authority on a driver's standing and availability.

### Passenger Experience

- **Persistence responsibility.** Persists the Passenger, Corporate Customer, and, jointly with Driver Management, Personal Client Relationship logical entities.
- **Persistence boundary.** Only Passenger Experience persists this representation (ADR-005, ADR-009).
- **Information authority.** Passenger Experience is the final authority on a passenger's or corporate customer's own representation.

### Administration

- **Persistence responsibility.** Persists the Administrator logical entity and governance and standing records.
- **Persistence boundary.** Only Administration persists governance and standing information (ADR-005, ADR-009).
- **Information authority.** Administration is the final authority on participant standing and governance decisions.

### Analytics

- **Persistence responsibility.** Persists only the Insight logical entity — its own derived output.
- **Persistence boundary.** Never persists the underlying information insight is derived from (ADR-005, ADR-019).
- **Information authority.** Analytics is the final authority only for the insight itself, never for the facts it was derived from.

### Notifications

- **Persistence responsibility.** Persists only the Notification logical entity — the record of what was communicated.
- **Persistence boundary.** Never persists the underlying information that triggers a notification (ADR-005, ADR-019).
- **Information authority.** Notifications is the final authority only for the record of what was communicated.

### Payments

- **Persistence responsibility.** Persists the Payment Record logical entity — the platform's own record of payment-related information.
- **Persistence boundary.** Never persists external settlement information, which is outside PIOS (ADR-020; PRODUCT_FOUNDATION.md Section 8).
- **Information authority.** Payments is the final authority for the platform's own payment record, never for external settlement.

## 4. Operational vs Derived Persistence

Consistent with ADR-019:

- **Operational information.** Information a domain originates as part of fulfilling its own responsibility: Order, Assignment, Driver, Passenger, Corporate Customer, Personal Client Relationship, Administrator, and Payment Record — each an original source, owned by the domain named in Section 3.
- **Derived information.** Information computed from another domain's operational information without itself being a source: Notification records that a communication occurred but is never the source of the fact that triggered it (LOGICAL_DATA_MODEL.md Section 9).
- **Analytical information.** Insight, produced by the Analytics Aggregation domain service (DOMAIN_MODEL.md Section 7) from operational information obtained through other domains' declared interactions or published events, never through direct persistence access.
- **Historical information.** A domain's own past states — a completed Order, an accepted Assignment that has since been fulfilled — remain persisted by the same domain that owned them while active. Completion or cancellation (DOMAIN_MODEL.md Section 11) never transfers persistence responsibility to another domain; historical information is a temporal aspect of already-owned operational information, not a distinct ownership category.

## 5. Cross-Domain Persistence

- **Allowed references.** A domain's persisted information may reference another domain's identity conceptually — an Assignment references the Order and Driver it connects (LOGICAL_DATA_MODEL.md Section 5) — without persisting that other domain's own information as its own.
- **Forbidden ownership.** No domain may persist information already owned by another domain, including as a convenience copy. A domain that needs another domain's information obtains it through that domain's declared interaction or published events (ADR-005, ADR-009); any representation it retains for its own purposes is never authoritative and is superseded by the owning domain's own record.
- **Information sharing principles.** Information crosses a persistence boundary only through the mechanisms already established — synchronous interaction (ADR-004) or events (ADR-003); persistence never establishes a shared access path that bypasses these mechanisms.

## 6. Consistency Philosophy

- **Ownership consistency.** Within a domain's own boundary, its persisted information is consistent with the invariants that domain owns (DOMAIN_MODEL.md Section 11; LOGICAL_DATA_MODEL.md Section 7); this consistency is a property of the owning domain alone.
- **Lifecycle consistency.** A domain's persisted information reflects only the lifecycle states already established for it (DOMAIN_MODEL.md Section 12); a persisted state outside that lifecycle is not valid.
- **Cross-domain consistency.** Consistency across more than one domain's persisted information is never assumed to be simultaneous; it is achieved only through the eventual consistency already established for the event mechanism (ADR-003; APPLICATION_ARCHITECTURE.md Section 10).
- **Evolution consistency.** When a domain's persisted structure evolves, it remains consistent with that domain's own invariants throughout the transition, and any consumer depending on it is given the migration window already established in ADR-010 and ADR-015.

No distributed transaction mechanism or other implementation detail is defined here.

## 7. Persistence Evolution

Persistence responsibility, boundaries, and consistency principles change only through a new ADR that explicitly supersedes the decision that established them, consistent with ADR-015. A domain's persisted structure may evolve independently of every other domain's, consistent with the per-domain technology evaluation scope already established in ADR-021. Ownership itself changes only through a new ADR superseding ADR-019 or the relevant domain-level decision (CONCEPTUAL_DATA_MODEL.md Section 10), never silently. A future architectural decision that changes domain boundaries (superseding ADR-017 or ADR-018) requires the persistence responsibilities in Section 3 to be revisited accordingly.

## 8. Database Design Readiness

**Sufficiently defined for the next stage.** Domain ownership of persisted information (Section 3); the operational, derived, analytical, and historical distinction (Section 4); cross-domain reference and sharing principles (Section 5); the consistency philosophy (Section 6); and the per-domain technology evaluation framework (ADR-021). Together, these give a future Database Design everything needed to begin selecting technology and designing a schema for one domain at a time.

**Intentionally undecided until Database Design.** The specific storage technology for each domain, deferred by ADR-021; any schema, table, column, key, or index; any caching, replication, sharding, or backup strategy; any specific consistency mechanism beyond the conceptual philosophy in Section 6; and the specific mechanism by which a cross-domain reference is technically resolved.

## 9. Traceability

| Section | Constitution | ADR | System Architecture | Domain Model | Logical Data Model | Application Architecture |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 5 | ADR-001, ADR-005, ADR-019, ADR-021 | — | — | Section 1 | Section 1 |
| 2. Persistence Principles | — | ADR-001, ADR-005, ADR-009, ADR-015, ADR-019, ADR-021 | — | — | Sections 2–3 | — |
| 3. Persistence Boundaries | — | ADR-002, ADR-005, ADR-009, ADR-018, ADR-019, ADR-020 | — | — | Sections 3–4 | — |
| 4. Operational vs Derived Persistence | — | ADR-019 | — | Section 7 | Sections 4, 9 | — |
| 5. Cross-Domain Persistence | — | ADR-003, ADR-004, ADR-005, ADR-009 | — | — | Section 5 | — |
| 6. Consistency Philosophy | — | ADR-003, ADR-010, ADR-015 | — | Sections 11–12 | Section 7 | Section 10 |
| 7. Persistence Evolution | Section 7 (ADR Policy) | ADR-015, ADR-017, ADR-018, ADR-019, ADR-021 | — | — | Section 10 | — |
| 8. Database Design Readiness | — | ADR-021 | — | — | — | — |

Where this document is silent — including on any decision reserved for a future Database Design — no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
