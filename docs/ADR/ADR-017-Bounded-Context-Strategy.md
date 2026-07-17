# ADR-017: Bounded Context Strategy

## Status

Proposed

## Context

ADR-009 establishes the rule that governs a bounded context once one is identified — an explicit responsibility, a boundary crossed only through declared interaction or published events, and no shared internal logic or data with another context — but explicitly declines to enumerate the contexts themselves. SYSTEM_ARCHITECTURE.md Section 7 (Bounded Context Overview) marks this as "Architecture Decision Required." PRODUCT_FOUNDATION.md Section 9 identifies eight product-level capability groups — Order Management, Dispatch, Driver Management, Passenger Experience, Payments, Administration, Analytics, and Notifications — each already described with a distinct, non-overlapping responsibility, and ADR-002 has already ratified Dispatch specifically as an independent architectural capability with exclusive ownership of the assignment decision.

## Decision

PIOS is divided into bounded contexts. Each of the eight capability groups identified in PRODUCT_FOUNDATION.md Section 9 is recognized as one conceptual bounded context, on the basis that each already carries a distinct, non-overlapping responsibility consistent with the boundary rule in ADR-009: Order Management, Dispatch, Driver Management, Passenger Experience, Payments, Administration, Analytics, and Notifications. This ADR names the contexts only; it does not define any entity, aggregate, class, or relationship within or between them.

## Consequences

Resolves the "Architecture Decision Required" marker in SYSTEM_ARCHITECTURE.md Section 7 by giving the domain isolation rule in ADR-009 a concrete set of contexts to govern. Gives future architectural and domain-level documentation a stable, named set of contexts to build on. Commits the platform to treating these eight contexts as the unit of architectural boundary until a future ADR supersedes this one, per ADR-015.

## Alternatives Considered

Leaving bounded contexts unresolved indefinitely — rejected, since it would leave Core Architectural Components, Data Ownership, and Integration Boundaries permanently undecidable, blocking further architectural progress without justification once a reasonable, well-grounded basis for naming them already exists. A finer-grained or coarser-grained partition than the eight product-level capability groups — rejected for this stage, since no source document offers a more granular or more consolidated partition with equivalent justification; the eight groups are the only ones with an already-documented, distinct responsibility.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md)
- [ADR-016: Architectural Layers](ADR-016-Architectural-Layers.md)
- [PRODUCT_FOUNDATION.md](../PRODUCT_FOUNDATION.md), Section 9
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 7
