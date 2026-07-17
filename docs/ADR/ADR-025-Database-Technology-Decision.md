# ADR-025: Database Technology Decision

## Status

Proposed

## Context

ADR-021 already established the database technology strategy: technology is evaluated and selected independently for each domain, no two domains ever share a database instance or schema, and a defined set of criteria governs that per-domain evaluation. PERSISTENCE_ARCHITECTURE.md and DATABASE_DESIGN.md have since translated that strategy into concrete persistence responsibilities and technology-neutral physical structures for each of the eight ratified domains. ADR-022 established the cross-cutting technology-selection principles every implementation decision must satisfy, and ADR-023 has already applied them to select Kotlin with Spring Boot for backend implementation. What remains is the database technology decision itself — the next concrete step physical persistence requires before implementation can begin.

## Decision

**PostgreSQL**, a relational database technology, is selected as the primary database technology for PIOS, applying to six of the eight ratified domains: Order Management, Dispatch, Driver Management, Passenger Experience, Administration, and Payments.

**Why it satisfies architecture.** DOMAIN_MODEL.md Section 11 establishes strict invariants for exactly these six domains — an order has exactly one current status, an order cannot be connected to more than one active assignment, a driver has exactly one current availability state — each requiring the kind of immediate, strongly consistent guarantee a mature relational database with full transaction support provides within a single domain's own boundary (PERSISTENCE_ARCHITECTURE.md Section 6, Ownership Consistency). DATABASE_DESIGN.md Section 5 already describes each of these domains' physical structures as one record per entity, referencing other records within the same domain — a shape a relational schema represents and enforces directly, without this document designing that schema itself. PostgreSQL specifically is selected, among relational technologies, for its maturity, active community, extensive documentation, and demonstrated fit with the JVM and Kotlin backend already selected in ADR-023, satisfying Team Capability and Community Maturity without introducing an additional, less-proven relational technology.

**Analytics and Notifications are explicitly not assigned PostgreSQL by this decision.** Both domains' physical structures are described in DATABASE_DESIGN.md Section 5 as append-only or periodically refreshed records, with simpler internal structure and read-heavy, aggregate-oriented access patterns quite different from the other six domains' transactional needs. Consistent with ADR-021's explicit allowance for per-domain divergence, this document anticipates that a document-oriented or log-oriented technology category is the more likely fit for these two domains, but does not commit to a specific product for either; that remains a dedicated, future evaluation for those two domains specifically.

## Evaluation Against ADR-021

- **Domain Ownership.** PostgreSQL is deployed as a separate database instance per domain, never shared across a domain boundary, directly upholding ADR-005's exclusive-ownership rule and ADR-021's prohibition on shared database instances or schemas.
- **Consistency Requirements.** PostgreSQL's full transaction support satisfies the Ownership Consistency and Lifecycle Consistency principles already established in PERSISTENCE_ARCHITECTURE.md Section 6 for the six domains it is selected for.
- **Transaction Support.** PostgreSQL's transaction boundary can be scoped to match a single domain's own aggregate boundary (APPLICATION_ARCHITECTURE.md Section 10), satisfying ADR-021's Transaction Requirements criterion without requiring a transaction to span more than one domain.
- **Query Capability.** PostgreSQL's relational query capability directly supports the queries already established for these domains (APPLICATION_ARCHITECTURE.md Section 7) — Retrieve Order Status, Retrieve Assignment, Retrieve Driver Availability, Retrieve Payment Record.
- **Evolution.** PostgreSQL's schema evolution tooling is mature and well understood, supporting the versioned, incremental evolution already required in ADR-010 and ADR-015 without requiring downtime for typical structural changes.
- **Operational Maturity.** PostgreSQL has decades of production use, extensive operational documentation, and a large body of established practice, satisfying ADR-021's Operational Maturity criterion directly.
- **Maintainability.** PostgreSQL's stability and standard foundation keep a domain's physical structures comprehensible over years, consistent with ADR-021's Maintainability criterion and the Long-Term Maintainability element of ADR-001.
- **Security.** PostgreSQL's mature, widely audited security features support Security by Design and Least Privilege (ADR-011) at the boundary of each domain's own database instance, without this document selecting a specific configuration.

## Domain Alignment

- **Persistence Architecture.** PostgreSQL, deployed one instance per domain, upholds the Persistence Boundaries in PERSISTENCE_ARCHITECTURE.md Section 3 exactly; no domain's instance is reachable by another domain directly.
- **Database Design.** PostgreSQL directly realizes the physical structures already described, technology-neutrally, in DATABASE_DESIGN.md Section 5 for the six domains this decision covers, without altering their shape.
- **Data Ownership.** PostgreSQL's per-domain deployment enforces, at the technology level, the exclusive-ownership mapping already ratified in ADR-019; no instance holds information belonging to more than one domain.

## Alternatives Considered

- **A document-oriented database as the primary technology for all eight domains.** Rejected as the primary choice, since six of the eight domains' invariants (DOMAIN_MODEL.md Section 11) require strong, immediately consistent transactional guarantees that a document-oriented technology typically provides more weakly, or with more application-level effort, than a mature relational technology. It remains the anticipated direction for Analytics and Notifications specifically, where it was not rejected.
- **A single distributed database applied uniformly.** Offers stronger horizontal scalability than a traditional relational deployment, but was not selected as the default because SYSTEM_ARCHITECTURE.md Section 12 already achieves independent scalability through per-domain isolation rather than requiring a single database technology to scale as one unit; the added operational complexity is not currently justified against a demonstrated need, consistent with No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3).
- **A different relational product.** Rejected primarily on Community Maturity and Team Capability grounds relative to PostgreSQL's open, widely adopted ecosystem, and to avoid introducing a licensing dependency not required by anything already approved.
- **Uniform technology across all eight domains, whichever category chosen.** Rejected in favor of the two-track outcome in Decision, since forcing Analytics and Notifications into a consistency model and query shape their own access patterns (DATABASE_DESIGN.md Section 5) do not require would contradict No Premature Optimization and Operational Simplicity.

## Consequences

**Positive.** Maintainability — PostgreSQL's maturity and standard foundation keep six of PIOS's most business-critical domains comprehensible and well supported over years. Development speed — the Kotlin and Spring Boot backend selected in ADR-023 has mature, well-documented PostgreSQL integration, letting engineers move quickly within the Documentation Driven, small-controlled-change discipline already established. Operational impact — one well-understood relational technology for the majority of the system, rather than eight different ones, minimizes operational surface.

**Negative.** Tradeoffs — Analytics and Notifications are left with an anticipated but not finalized direction, meaning implementation of those two domains cannot fully begin until their own dedicated evaluation is complete. Long-term commitments — selecting PostgreSQL as the default for six domains creates a long-term operational dependency on relational-database expertise remaining available to the team; while Team Capability was evaluated favorably above, this is still a commitment that would be costly to reverse across six domains at once.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 14
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-010: Versioning Strategy](ADR-010-Versioning-Strategy.md)
- [ADR-011: Security Principles](ADR-011-Security-Principles.md)
- [ADR-014: Deployment Philosophy](ADR-014-Deployment-Philosophy.md)
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md)
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md)
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md)
- [ADR-022: Technology Stack Strategy](ADR-022-Technology-Stack-Strategy.md)
- [ADR-023: Backend Technology Decision](ADR-023-Backend-Technology-Decision.md)
- DOMAIN_MODEL.md, Section 11
- PERSISTENCE_ARCHITECTURE.md, Sections 3, 6
- DATABASE_DESIGN.md, Section 5
- APPLICATION_ARCHITECTURE.md, Sections 7, 10
- SYSTEM_ARCHITECTURE.md, Section 12
- ENGINEERING_GUIDELINES.md, Section 3
