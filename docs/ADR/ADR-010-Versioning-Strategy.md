# ADR-010: Versioning Strategy

## Status

Proposed

## Context

ADR-004 requires every interface to follow a versioning philosophy without defining one. ADR-003 requires published events to be treated as stable facts once relied upon. Constitution Section 7 requires that architectural decisions themselves be recorded and never silently changed. Each of these depends on a concrete versioning approach that has not yet been defined.

## Problem

What approach governs how interfaces, events, and architectural decisions themselves are versioned, so that a consumer of any of them can know what to expect from a given version?

## Decision

Every interface (ADR-004) and every event contract (ADR-003) is versioned, so that a change which alters what a consumer can rely on is distinguishable from a change that does not. A version that alters existing consumer expectations is a distinct, identifiable version from one that only adds to them. Architecture Decision Records follow the lifecycle already defined in Constitution Section 7: an existing ADR is never edited to change its decision; it is superseded by a new ADR, and the superseded ADR is retained, not deleted. Documents outside ADRs are versioned through the repository's version control history rather than a separate numbering scheme.

## Alternatives Considered

- **Unversioned, "latest only" interfaces and events.** Rejected: it gives consumers no way to know whether a change they are about to depend on is safe, contradicting the No Hidden Assumptions principle in Constitution Section 4.
- **Editing ADRs in place when a decision changes.** Rejected: it would destroy the historical record that Constitution Section 7 requires and contradicts the Traceability element of ADR-001.

## Consequences

### Positive Consequences

- Gives every consumer of an interface or event a reliable signal for whether a given change is safe to adopt.
- Makes the ADR lifecycle already required by the Constitution consistent with how every other artifact in the system is versioned.
- Preserves the full history of both interface evolution and architectural reasoning.

### Negative Consequences

- Requires interface and event owners to commit to maintaining meaning across versions rather than changing behavior freely.
- Adds the discipline of explicitly marking and tracking versions to every interface and event, rather than allowing silent change.

## Future Impact

Any future document defining a concrete interface, event schema, or architectural change must state its version and, where it changes existing behavior, must be recorded as a new version rather than a silent edit. This ADR does not define a specific numbering scheme, format, or tooling for tracking versions.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-003 (Event-Driven Domain), and ADR-004 (API Style), providing the versioning approach both require.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — No Hidden Assumptions), Section 7 (ADR Policy)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-004: API Style](ADR-004-API-Style.md)
