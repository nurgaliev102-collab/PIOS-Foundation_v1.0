# ADR-005: Data Ownership

## Status

Proposed

## Context

Independent architectural capabilities, such as Dispatch (ADR-002), will each need to retain information relevant to their own responsibility. Without an explicit ownership rule, it is possible for capabilities to come to depend on data they do not own, which would undermine their independence.

## Problem

Who is responsible for a given piece of information within PIOS, and how may a capability that needs information it does not own obtain it?

## Decision

Each capability owns the data relevant to its own responsibility exclusively. No capability's data is jointly owned. A capability that needs information owned by another capability obtains it only through that capability's own interaction style (ADR-004) or through an event that capability has published (ADR-003); it never assumes direct access to another capability's data. This ADR does not define any schema, table, or storage technology.

## Alternatives Considered

- **Shared ownership of common data by multiple capabilities.** Rejected: joint ownership removes accountability for the data's correctness and creates the kind of hidden coupling ADR-001 requires the architecture to avoid.
- **Allowing any capability to read another capability's data directly, bypassing its interaction style.** Rejected: it would make every capability's internal representation a de facto public interface, contradicting the boundary discipline established in ADR-002 for Dispatch and intended to apply to every capability.

## Consequences

### Positive Consequences

- Gives every capability full accountability for the correctness of the data it owns.
- Prevents one capability's internal data representation from becoming an unstated dependency of another.
- Keeps data-related change contained to the capability that owns it, supporting the Evolution principle in ADR-001.

### Negative Consequences

- A capability that frequently needs another's data must rely on that capability's interaction style or published events rather than accessing it directly, which can be less immediately convenient.
- Requires careful design, left to later documents, of how needed information is obtained without violating ownership.

## Future Impact

Any future document describing storage, schema, or data models must respect exclusive ownership per capability as established here. Domain-level documentation must identify, for each piece of information, exactly one owning capability.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-002 (Dispatch Engine, as an example of an owning capability), ADR-003 (Event-Driven Domain), and ADR-004 (API Style) as the two sanctioned means of obtaining data one does not own.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — No Hidden Assumptions)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-004: API Style](ADR-004-API-Style.md)
