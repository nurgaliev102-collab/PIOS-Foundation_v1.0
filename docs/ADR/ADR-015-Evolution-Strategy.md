# ADR-015: Evolution Strategy

## Status

Proposed

## Context

Every ADR in this foundational set will eventually need to change as the system and its context evolve. Constitution Section 7 requires that architecture never change without an ADR and that superseded ADRs are never deleted. ADR-010 establishes versioning for interfaces, events, and ADRs themselves. ADR-014 requires every release to be reversible. What remains undecided is how change, deprecation, and migration proceed in practice once a decision needs to move forward.

## Problem

How does an existing architectural decision, interface, or event evolve over time without breaking what already depends on it, and how is that evolution retired once it is no longer needed?

## Decision

An existing architectural decision is changed only by a new ADR that explicitly supersedes it, per Constitution Section 7 and the versioning approach in ADR-010; the superseded ADR is retained and marked accordingly, never deleted. Evolution proceeds incrementally: a new version of an interface or event, per ADR-010, is introduced alongside the version it will replace, rather than replacing it outright. A version that is being retired is explicitly marked as deprecated, and the ADR or document that deprecates it states the period during which the deprecated version remains available before removal. Backward compatibility is maintained for the duration of that stated period; removal before the period ends is not acceptable.

## Alternatives Considered

- **Replacing an interface, event, or ADR outright without an explicit deprecation period.** Rejected: it would break existing consumers without notice, contradicting the reversibility requirement in ADR-014 and the No Hidden Assumptions principle in Constitution Section 4.
- **Leaving deprecated versions available indefinitely, with no defined removal.** Rejected: it would allow obsolete decisions to accumulate without ever being retired, working against the Long-Term Maintainability element of ADR-001.

## Consequences

### Positive Consequences

- Gives consumers of any interface, event, or architectural decision a predictable, bounded window to adapt to change.
- Keeps the full history of architectural reasoning available and auditable, consistent with Constitution Section 7.
- Prevents obsolete decisions from persisting indefinitely by requiring an explicit, time-bound retirement.

### Negative Consequences

- Requires maintaining a deprecated version alongside its replacement for the duration of the migration window, at additional cost.
- Requires every superseding ADR or deprecation notice to explicitly state a migration window, or the change is not considered complete.

## Future Impact

Any future document that changes an existing architectural decision, interface, or event must follow this evolution and deprecation process. This ADR does not define a specific length for any migration window; that is decided case by case in the document that performs the deprecation.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-010 (Versioning Strategy), and ADR-014 (Deployment Philosophy), defining how the versioned, reversible releases those ADRs establish are retired over time.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — No Hidden Assumptions), Section 7 (ADR Policy)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-010: Versioning Strategy](ADR-010-Versioning-Strategy.md)
- [ADR-014: Deployment Philosophy](ADR-014-Deployment-Philosophy.md)
