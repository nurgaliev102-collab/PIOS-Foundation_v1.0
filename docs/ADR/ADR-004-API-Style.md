# ADR-004: API Style

## Status

Proposed

## Context

ADR-003 establishes events as the architectural mechanism for communicating that something has already happened. Some interactions, however, require an immediate answer rather than a reaction to a past occurrence. An architectural decision is needed on the style such interactions follow, without specifying any concrete interface.

## Problem

What principles govern the style of direct, request-driven interaction between a capability and its callers, whether external clients or other capabilities?

## Decision

Direct, request-driven interaction in PIOS follows these principles:

- **Resource-Oriented.** An interaction is framed around a well-defined thing being acted upon, not around an arbitrary procedure name.
- **Versioning Philosophy.** An interface is expected to change over time, and callers must be able to identify which version of an interface's behavior they are relying on. The specific versioning scheme is left to a later architectural decision.
- **Consistency.** The same kind of interaction is shaped the same way across every capability that exposes one, so that a caller who understands one interface can reasonably predict the shape of another.

This ADR does not define any endpoint, payload, protocol, or technology.

## Alternatives Considered

- **Allowing each capability to define its own ad hoc interaction style.** Rejected: it would violate the Consistency requirement and make every integration a bespoke effort, contradicting the Single Source of Truth and Long-Term Maintainability principles in ADR-001.
- **Treating all interaction as procedure-oriented (arbitrary named actions) rather than resource-oriented.** Rejected: procedure-oriented interaction tends to accumulate inconsistent, overlapping actions over time, which works against the Consistency principle this ADR establishes.

## Consequences

### Positive Consequences

- Gives every future interface a shared style to be evaluated against, before any specific interface is designed.
- Reduces the cognitive cost of integrating with a new capability, since the shape of interaction is already familiar.
- Establishes that versioning is a first-class concern for every interface from the outset.

### Negative Consequences

- Constrains capabilities from choosing whatever interaction style is most convenient for their internal implementation.
- Requires a later architectural decision on the specific versioning scheme before interfaces can be considered fully specified.

## Future Impact

Any future document that defines a concrete interface, including its endpoints, payloads, or protocol, must conform to the Resource-Oriented, Versioning Philosophy, and Consistency principles established here. The specific versioning scheme referenced but not defined here is addressed in a later ADR.

## Related ADRs

Builds on ADR-001 (System Philosophy) and distinguishes itself from ADR-003 (Event-Driven Domain) by addressing direct, request-driven interaction rather than event-based communication.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — Single Source of Truth)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
