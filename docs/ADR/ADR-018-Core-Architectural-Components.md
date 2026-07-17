# ADR-018: Core Architectural Components

## Status

Proposed

## Context

SYSTEM_ARCHITECTURE.md Section 6 (Core Architectural Components) ratifies Dispatch, per ADR-002, as the only formally confirmed architectural component, and lists the remaining seven product-level capability groups as candidates pending formal ratification, marked "Architecture Decision Required." ADR-017 has now named all eight capability groups as the platform's bounded contexts. This ADR ratifies each of those eight as a formal architectural component with a stated responsibility, extending the precedent already set for Dispatch by ADR-002.

## Decision

The following eight architectural components are ratified, each owning the responsibility stated and no other, consistent with the boundary rule in ADR-009 and the contexts named in ADR-017:

- **Dispatch** — owns the assignment decision connecting a transportation request to a driver, as already established in ADR-002.
- **Order Management** — owns the receiving and tracking of transportation demand from any source through its lifecycle.
- **Driver Management** — owns the representation of drivers within the platform, including their standing, availability, and participation.
- **Passenger Experience** — owns the representation of passengers and corporate customers and their interaction with the platform.
- **Payments** — owns the platform's own record of payment-related information associated with transportation demand.
- **Administration** — owns support for platform administrators in overseeing and governing the ecosystem.
- **Analytics** — owns the production of insight into how the platform and its ecosystem are behaving and performing.
- **Notifications** — owns informing participants of information relevant to them.

No interaction diagram, sequence, or internal structure is defined for any component here.

## Consequences

Resolves the remaining "Architecture Decision Required" marker in SYSTEM_ARCHITECTURE.md Section 6 for all seven previously candidate components. Gives future documentation a confirmed set of components to assign conceptual data ownership to. Commits each of these eight components to the exclusive-ownership and boundary discipline already established in ADR-005 and ADR-009.

## Alternatives Considered

Ratifying only Dispatch and leaving the remaining seven as unratified candidates indefinitely — rejected, since ADR-017 already established a well-grounded basis for treating all eight as bounded contexts, and leaving seven of them unratified as components would be an unjustified inconsistency. Assigning more than one responsibility to a single component, or splitting a single product-level capability group across more than one component — rejected, since no source document provides a basis for a different partition than the one already named in ADR-017.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-017: Bounded Context Strategy](ADR-017-Bounded-Context-Strategy.md)
- [PRODUCT_FOUNDATION.md](../PRODUCT_FOUNDATION.md), Section 9
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 6
