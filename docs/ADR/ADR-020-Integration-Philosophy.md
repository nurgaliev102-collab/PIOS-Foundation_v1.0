# ADR-020: Integration Philosophy

## Status

Proposed

## Context

ADR-003 and ADR-004 establish how PIOS's own architectural components communicate with one another. SYSTEM_ARCHITECTURE.md Section 11 (Integration Boundaries) identifies four categories of external dependency — Payment, Location and Routing, Notifications, and Identity — each explicitly outside the platform's boundary per PRODUCT_FOUNDATION.md Sections 8 and 14, and marks the specific integration mechanism for each as "Architecture Decision Required." Neither ADR-003 nor ADR-004 states whether the principles they establish for communication between PIOS's own components also govern the platform's relationship with a dependency outside it. This ADR resolves that gap at the level of philosophy, without selecting a protocol or technology for any specific external dependency.

## Decision

- **Internal communication principles.** Communication between PIOS's own architectural components follows ADR-003 and ADR-004 as already established: synchronous interaction for requests requiring an immediate answer, and events for facts already occurred.
- **External communication principles.** PIOS treats every dependency outside its boundary the same way it treats another component's boundary under ADR-009: the platform never assumes direct access to an external system's internals, and every interaction with it occurs through a declared interaction at the platform's own edge, governed by the same security discipline required of any boundary under ADR-011.
- **Event philosophy.** An external occurrence that PIOS needs to know about, and that the external system can make available as a fact rather than an immediate answer, is treated as an event under the same principle as ADR-003: PIOS reacts to it after it has occurred rather than assuming simultaneous knowledge of it.
- **Command philosophy.** Where PIOS needs an external system to carry out an action within that system's own responsibility, this is treated as a request for that system to act, not as an assumption that PIOS can act on the external system's behalf; PIOS does not directly manipulate information or state owned by an external system.
- **Query philosophy.** Where PIOS needs information currently held by an external system in order to proceed, this is treated as a synchronous request for that information, following the same immediate-answer principle used for internal synchronous interaction under ADR-004.
- **Synchronization philosophy.** PIOS does not assume its own record and an external system's record are simultaneously consistent. Each remains authoritative for what it owns — PIOS for the information within its own components' responsibilities (ADR-019), the external system for the concern it is depended on for — and PIOS reconciles its own record with the external system's state only through the declared interaction or event received from it, never by assuming a shared, simultaneously consistent state.

No protocol, message format, or technology is selected by this decision.

## Consequences

Resolves the "Architecture Decision Required" markers in SYSTEM_ARCHITECTURE.md Section 11 for Payment, Location and Routing, Notifications, and Identity at the level of philosophy, by establishing that each is governed by the same declared-boundary discipline as an internal component, applied at the platform's outer edge. Extends ADR-003, ADR-004, ADR-005, ADR-009, and ADR-011 to explicitly cover the platform's external relationships, rather than leaving them implicitly internal-only. Leaves the specific choice of synchronous versus event-based integration for each individual external dependency, and any protocol or technology, to a future architectural or domain-level decision, since that choice depends on characteristics of each dependency not yet documented.

## Alternatives Considered

Treating external systems as trusted extensions of PIOS's own internal components, sharing state or access directly — rejected, since it would contradict the exclusive-ownership principle in ADR-005 and the boundary discipline in ADR-009 and ADR-011, and PIOS does not control an external system's internals. Assuming simultaneous, shared consistency between PIOS's own record and an external system's state — rejected, since PIOS cannot guarantee or enforce consistency in a system it does not own, and assuming it would contradict the No Hidden Assumptions principle in Constitution Section 4.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-004: API Style](ADR-004-API-Style.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-011: Security Principles](ADR-011-Security-Principles.md)
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md)
- [PRODUCT_FOUNDATION.md](../PRODUCT_FOUNDATION.md), Sections 8 and 14
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 11
