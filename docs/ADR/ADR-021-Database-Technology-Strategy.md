# ADR-021: Database Technology Strategy

## Status

Proposed

## Context

PIOS now has DOMAIN_MODEL.md, CONCEPTUAL_DATA_MODEL.md, and LOGICAL_DATA_MODEL.md, establishing what information exists, who owns it, and how it is logically structured, all independent of storage technology. Physical persistence — Database Design — is the next layer in the documentation hierarchy (PROJECT_CONSTITUTION.md Section 5), but no decision yet governs how storage technology itself will be evaluated or chosen. ADR-005 already establishes that each domain owns its data exclusively and that no domain shares a database with another; that rule constrains, but does not by itself decide, how a storage technology is selected for any given domain. This ADR establishes the decision framework a future Database Design must use, without selecting a technology.

## Decision

Database strategy is governed by the following principles:

1. **Domain Ownership.** A storage decision is made within the boundary of exactly one domain and never requires two domains to share a database instance or schema, consistent with the exclusive-ownership rule already established in ADR-005 and the boundary rule in ADR-009.
2. **Data Consistency.** A domain's storage must be capable of upholding that domain's own invariants (DOMAIN_MODEL.md Section 11; LOGICAL_DATA_MODEL.md Section 7) as a guarantee within its own boundary. Consistency across domains is never assumed of storage itself; it is achieved only through the event mechanism already established in ADR-003, consistent with the Transaction Boundaries already described in APPLICATION_ARCHITECTURE.md Section 10.
3. **Transaction Requirements.** A domain's storage must support a consistency boundary no larger than that domain's own aggregate boundary (APPLICATION_ARCHITECTURE.md Section 10); no storage decision may require a transaction spanning more than one domain.
4. **Evolution Capability.** A domain's storage must be able to change independently of every other domain's storage, consistent with ADR-009 and ADR-015, and must support the same versioned, incremental evolution already required of interfaces and events (ADR-010).
5. **Scalability.** A domain's storage must be capable of scaling independently of every other domain's storage, consistent with the Scalability Principles in SYSTEM_ARCHITECTURE.md Section 12.
6. **Operational Simplicity.** A storage decision that the team can operate reliably is preferred over one that is more capable but harder to operate, consistent with the Long-Term Maintainability element of ADR-001.
7. **Reliability.** A domain's storage must support the mandatory reversibility of every release already established in ADR-014; a storage change that cannot be rolled back is not acceptable.
8. **Maintainability.** A storage decision must remain comprehensible and modifiable as the domain it serves grows, consistent with the Quality Principles in PROJECT_CONSTITUTION.md Section 14.

Because domains are already isolated from one another (ADR-005, ADR-009) and already show different information characteristics — source, derived, and analytical information are distinguished in CONCEPTUAL_DATA_MODEL.md Section 6 and LOGICAL_DATA_MODEL.md Section 9 — storage technology is evaluated and selected independently for each domain, against the criteria in this ADR, rather than decided once for the whole platform. This ADR neither mandates a single technology across every domain nor mandates that every domain use a different one; it mandates only that the decision be made per domain, evaluated on that domain's own merits, and that no two domains ever share a database instance or schema regardless of which technology each has chosen.

## Technology Evaluation Criteria

A domain's Database Design evaluates candidate storage technology against the following criteria; this ADR does not apply these criteria to any specific technology itself.

- **Consistency Model.** Whether the technology can provide the strength of consistency the domain's own invariants require.
- **Query Capability.** Whether the technology supports the kinds of information retrieval the domain's queries require (APPLICATION_ARCHITECTURE.md Section 7).
- **Transaction Support.** Whether the technology supports a consistency boundary matching the domain's own aggregate boundary, without requiring a boundary larger than that.
- **Operational Maturity.** How well understood, documented, and supportable the technology is for ongoing operation.
- **Performance Characteristics.** Whether the technology's performance profile matches the domain's expected volume and access pattern.
- **Evolution Flexibility.** How readily the technology's structure can change as the domain's logical model evolves (ADR-015).
- **Team Capability.** Whether the team has, or can reasonably acquire, the capability to operate the technology reliably.

## Storage Responsibility

- **Belongs to database technology.** Physically persisting and retrieving the information a domain owns, and enforcing whatever strength of consistency the chosen technology itself is capable of, within that domain's own boundary.
- **Belongs to application and domain.** Deciding what the persisted information means, enforcing the business invariants that are not purely a storage-level consistency guarantee (DOMAIN_MODEL.md Section 11), and deciding when information changes — consistent with the Domain Decides Business Meaning principle in APPLICATION_ARCHITECTURE.md Section 2. Storage technology never decides business meaning; it persists only what the domain has already decided.

## Alternatives Considered

- **Mandating a single database technology platform-wide, decided once and applied uniformly regardless of domain fit.** Rejected: domains already show different information and consistency characteristics (CONCEPTUAL_DATA_MODEL.md Section 6; LOGICAL_DATA_MODEL.md Section 9) that a single technology may not serve equally well, and no higher-authority document requires uniformity for its own sake.
- **Mandating polyglot persistence as a requirement that every domain use a technology distinct from its neighbors.** Rejected: forcing diversity for its own sake adds operational surface without benefit where two domains' needs are genuinely similar.
- **Relational databases, document databases, and distributed databases, as technology categories.** Each is a legitimate candidate for a given domain's Database Design to evaluate against the criteria above; none is selected or excluded by this ADR.
- **Single storage strategy versus multiple storage strategy, as an outcome.** Either may legitimately result from independent, per-domain evaluation; this ADR does not choose between them in advance.

## Consequences

**Positive.** Every future domain's Database Design has a consistent evaluation framework to work from, rather than starting without one. Domains remain free to choose the technology best suited to their own needs, without being forced into a platform-wide compromise. No new coupling is introduced between domains, since this ADR only elaborates the exclusive-ownership and isolation rules already established.

**Negative.** Without a mandated single technology, the platform may end up operating more than one storage technology, which adds operational surface compared to a single, uniform choice made in advance. Each domain's Database Design must independently perform the evaluation in this ADR rather than inheriting a ready-made decision.

**Future Implications.** Every future Database Design document must reference this ADR and demonstrate how its chosen technology satisfies the criteria above for that specific domain. A change to the evaluation criteria or to the per-domain evaluation scope decided here requires a superseding ADR, consistent with ADR-015.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Sections 5, 14
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-010: Versioning Strategy](ADR-010-Versioning-Strategy.md)
- [ADR-014: Deployment Philosophy](ADR-014-Deployment-Philosophy.md)
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md)
- [ADR-018: Core Architectural Components](ADR-018-Core-Architectural-Components.md)
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md)
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 12
- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md), Section 11
- [CONCEPTUAL_DATA_MODEL.md](../CONCEPTUAL_DATA_MODEL.md), Section 6
- [LOGICAL_DATA_MODEL.md](../LOGICAL_DATA_MODEL.md), Sections 7, 9
- [APPLICATION_ARCHITECTURE.md](../APPLICATION_ARCHITECTURE.md), Sections 2, 10
