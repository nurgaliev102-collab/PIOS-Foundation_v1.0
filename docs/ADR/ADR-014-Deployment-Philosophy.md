# ADR-014: Deployment Philosophy

## Status

Proposed

## Context

Domains in PIOS are architecturally independent of one another (ADR-009) and are expected to evolve at their own pace (ADR-010). Once domains exist, an architectural decision is needed on the philosophy governing how a domain reaches a running state, so that releasing one domain does not undermine the independence already established for it.

## Problem

What philosophy governs how a domain is released and how environments relate to one another, so that a domain's independence is preserved through deployment and not only through design?

## Decision

A domain is deployed independently of other domains, so that releasing a change to one domain does not require releasing unrelated domains at the same time. Environments in which a domain runs are kept separate according to their purpose, so that a change under evaluation does not affect a domain relied upon for stable use. A release is not considered acceptable unless it can be reversed; if a released change cannot be rolled back to the domain's prior accepted version, that change is not treated as ready. This ADR does not define specific deployment platforms, environments by name, or infrastructure.

## Alternatives Considered

- **Releasing all domains together in a single coordinated release.** Rejected: it couples the release risk of every domain together, contradicting the independent evolution established in ADR-001 and ADR-009.
- **Treating rollback as optional, to be decided at release time.** Rejected: a release that cannot be reversed turns every deployment into an irreversible risk, which is inconsistent with the Reliability principle in Constitution Section 3.

## Consequences

### Positive Consequences

- Keeps the release risk of one domain from spilling over into unrelated domains.
- Gives every release a defined way back to a known-good state.
- Extends the independence already established for domain design (ADR-009) into how those domains actually reach production use.

### Negative Consequences

- Requires each domain to maintain compatibility with its consumers during a rollout, consistent with the versioning approach in ADR-010.
- Requires environment separation and rollback capability to be designed for explicitly, rather than assumed to exist by default.

## Future Impact

Any future deployment architecture or infrastructure document must satisfy independent domain release, environment separation, and mandatory rollback capability as established here. The specific platform, tooling, and environment names are left to that later documentation.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-009 (Domain Isolation), and ADR-010 (Versioning Strategy), extending domain independence and versioning discipline into how domains are released.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 3 (Product Principles — Reliability)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-010: Versioning Strategy](ADR-010-Versioning-Strategy.md)
