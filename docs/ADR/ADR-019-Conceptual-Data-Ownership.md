# ADR-019: Conceptual Data Ownership

## Status

Proposed

## Context

ADR-005 establishes that each architectural component owns the data relevant to its own responsibility exclusively, and that a component needing information it does not own obtains it only through another component's declared interaction style or published events. SYSTEM_ARCHITECTURE.md Section 10 (Data Ownership) marks the definitive mapping of information to owning component as "Architecture Decision Required," pending the bounded context decision. ADR-018 has now ratified eight architectural components, each with a stated responsibility. This ADR applies the ownership rule in ADR-005 to those eight components.

## Decision

Each component ratified in ADR-018 conceptually owns the information within the scope of its stated responsibility, and no other component owns that same information:

- **Order Management** conceptually owns information about orders and their status, from submission through completion or cancellation.
- **Dispatch** conceptually owns information about assignments — the outcome of allocating an order to a driver — consistent with ADR-002.
- **Driver Management** conceptually owns information about drivers, including their standing and their own declared availability.
- **Passenger Experience** conceptually owns information about passengers and corporate customers and their interaction with the platform.
- **Payments** conceptually owns the platform's own record of payment-related information associated with orders.
- **Administration** conceptually owns information about platform governance and participant standing within it.
- **Analytics** conceptually owns the derived, aggregate insight it produces about platform and ecosystem behavior; it does not own the underlying information from which that insight is derived, which remains owned by the component that originated it and is obtained only through that component's declared interaction style or published events.
- **Notifications** conceptually owns information about what has been communicated to participants; it does not own the underlying information that triggers a notification, which remains owned by the component responsible for it.

No database, schema, table, entity, or attribute is defined by this decision.

## Consequences

Resolves the "Architecture Decision Required" marker in SYSTEM_ARCHITECTURE.md Section 10 by giving the exclusive-ownership rule in ADR-005 a concrete assignment across the eight ratified components. Makes explicit that Analytics and Notifications own only what they themselves produce, not the information they depend on from other components, reinforcing that such dependency must occur through declared interaction or events rather than direct access. Gives future domain-level documentation a starting boundary for each component's information scope.

## Alternatives Considered

Allowing Analytics or Notifications to be treated as owning the underlying information they depend on, rather than only their own derived output — rejected, since it would contradict the exclusive-ownership rule in ADR-005 and blur responsibility for the same information across more than one component. Leaving the mapping unresolved until a full domain model exists — rejected, since ADR-018 already provides a sufficient, well-grounded basis for a conceptual mapping without requiring entities, schemas, or a domain model.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-018: Core Architectural Components](ADR-018-Core-Architectural-Components.md)
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 10
