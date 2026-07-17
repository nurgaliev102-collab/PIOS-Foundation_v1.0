# ADR-003: Event-Driven Domain

## Status

Proposed

## Context

PIOS is composed of independently owned architectural capabilities, such as Dispatch (ADR-002). These capabilities need a way to make significant occurrences known to the rest of the system without becoming directly coupled to one another. An architectural decision is needed on how such occurrences are represented and communicated.

## Problem

How does a capability communicate that something significant has occurred, in a way that other capabilities can rely on without being tightly coupled to it?

## Decision

PIOS treats significant occurrences as architectural communication in their own right, expressed as events. Two categories of event are recognized architecturally:

- **Domain events**, which record something significant that occurred within a single capability's own boundary, of interest primarily to that capability's own future behavior and history.
- **Business events**, which record something significant that has meaning beyond the capability where it originated, such as the outcome of a Dispatch assignment decision (ADR-002), and which other capabilities may rely on.

A capability publishes an event to represent a fact that has already happened; it does not use events to issue commands to other capabilities. No implementation mechanism, message format, or delivery technology is defined by this ADR.

## Alternatives Considered

- **Direct, synchronous calls between capabilities for every interaction.** Rejected: it creates temporal coupling, where every capability must be available and responsive at the moment another capability acts, undermining the independent evolution intended by ADR-001.
- **A single global event with no distinction between domain and business significance.** Rejected: it would force every capability to filter irrelevant occurrences, and it obscures which events are safe to depend on across a boundary.

## Consequences

### Positive Consequences

- Allows capabilities such as Dispatch to make their outcomes known without depending on who consumes them.
- Distinguishes internal history from cross-capability facts, clarifying what other capabilities may depend on.
- Supports the Evolution principle from ADR-001 by allowing new consumers of an event to be added without changing the capability that publishes it.

### Negative Consequences

- Introduces eventual consistency: a capability reacting to an event does so after the fact, not at the instant it occurred.
- Requires every capability that publishes business events to treat that event as a stable, relied-upon fact once published.

## Future Impact

Later architectural decisions on interface style, versioning, and observability must account for both synchronous interaction and event-based communication as two distinct, legitimate patterns. Any future decision defining how an assignment outcome or similar business fact is delivered to other capabilities must be consistent with the domain/business event distinction made here.

## Related ADRs

Builds on ADR-001 (System Philosophy) and ADR-002 (Dispatch Engine), using the Dispatch assignment decision as an example of an occurrence with cross-capability significance.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — No Hidden Assumptions)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md)
