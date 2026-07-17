# ADR-026: Deployment Strategy

## Status

Proposed

## Context

PIOS's architecture is now fully specified through implementation-adjacent decisions: a Capability-Based architecture (ADR-016) decomposed into eight ratified domain modules (ADR-018; MODULE_STRUCTURE.md), each with independent ownership boundaries (ADR-005, ADR-009) and explicit interface contracts (INTERFACE_CONTRACTS.md) governing how they may interact. ADR-014 already requires that each domain be deployed independently of the others. ADR-022 established the technology-selection framework; ADR-023 and ADR-025 have already selected Kotlin with Spring Boot for backend implementation and PostgreSQL for six of the eight domains' persistence, respectively — with ADR-023 explicitly committing to one independently deployable service per module as the direct consequence of ADR-014. What remains before implementation can begin is the deployment strategy itself: how that independent deployability is actually realized, how the system evolves operationally, and how operational complexity is controlled as the number of independently deployable modules grows.

## Decision

PIOS deploys each of its eight ratified modules as its own independently releasable unit from the point implementation begins. This confirms and operationalizes what ADR-014 already requires and what ADR-023 already committed to for backend implementation; it is not a new, independently reopened choice between deployment models.

Independent deployability, as decided here, is a property of the release process — each module can be built, versioned, and released without requiring any other module to be rebuilt or redeployed alongside it — and is explicitly distinct from a claim about physical infrastructure topology. Whether independently deployable modules run on separate physical or virtual resources, or share underlying infrastructure economically, is not decided by this ADR; that is Infrastructure, a distinct technology-selection boundary ADR-022 already identified as a separate, later decision. Separating these two concerns lets PIOS satisfy ADR-014's requirement in full from day one, without this decision alone determining how operationally expensive that requirement turns out to be in practice — that depends on the future Infrastructure decision, not this one.

## Module Deployment Model

**Selected: independent deployment from day one**, for all eight modules, as a release-process property.

A modular platform with evolutionary separation — starting with two or more modules co-deployed as a single artifact and splitting them apart only once a demonstrated need arises — was seriously considered, since it is common, well-regarded industry practice and would lower initial development and operational overhead. It is not selected here, because it would require, for as long as any grouping persisted, deploying more than one domain as a single unit. That directly contradicts ADR-014's existing requirement that "a change to one domain does not require redeploying unrelated domains," and it would be inconsistent with ADR-023's already-stated backend decision, which committed to one independently deployable service per module specifically because ADR-014 requires it. Reopening that question is outside the scope of this ADR: ADR-014 and ADR-023 are existing, binding decisions, not aspirations deferred to "whenever the team is ready," and changing them would require a decision that explicitly supersedes them, which this ADR does not attempt.

Choosing independent deployment from day one, rather than reopening it, is itself the more architecturally disciplined outcome: MODULE_STRUCTURE.md and INTERFACE_CONTRACTS.md already require every module to interact with every other only through declared interactions and published events, never through shared internals. A codebase already built to that discipline has already done the hard design work a "modular monolith" strategy exists to defer; packaging each module as its own release artifact from the outset is comparatively mechanical, not a leap in complexity.

## Evolution Path

The deployment model itself — one independently releasable unit per module — does not need to evolve, since it already satisfies ADR-014 in full. What evolves instead:

- **Operational sophistication.** How each module's independent release is operated — the tooling, automation, and process around it — can start simple and mature over time without changing the underlying model; this ADR does not mandate a specific level of operational maturity on day one.
- **Module boundaries.** If a module's own responsibility later proves too coarse or too fine, it is re-scoped only through a decision that explicitly supersedes ADR-017 or ADR-018, consistent with ADR-015; this ADR does not itself split or merge any module.
- **Infrastructure topology.** Whether modules eventually run on more specialized, separated infrastructure, or continue to share underlying resources economically, is a future Infrastructure decision (ADR-022) that can evolve independently of the deployment model this ADR establishes, without requiring this ADR to be revisited.
- **Architecture boundaries are preserved throughout.** Every evolution path above operates strictly within the domain isolation (ADR-009), exclusive ownership (ADR-005), and interface contract (INTERFACE_CONTRACTS.md) rules already established; none of them is a route around those boundaries.

## Operational Complexity

- **Development speed.** Slower initial per-module setup than a single deployable artifact would require, consistent with the tradeoff already accepted in ADR-023; mitigated by MODULE_STRUCTURE.md and INTERFACE_CONTRACTS.md already having done the boundary-design work, leaving comparatively mechanical packaging work per module.
- **Operational simplicity.** This is the genuine cost of the decision: eight independently releasable units are more operational surface than one. It is partially mitigated by deferring physical infrastructure topology to a separate, later decision (Section: Decision, above), so operational complexity does not have to scale as sharply, at least initially, as eight fully separate infrastructure footprints would imply.
- **Maintenance.** Benefits from the Explicit Ownership principle already established in MODULE_STRUCTURE.md Section 2: a module's maintenance is scoped to that module and does not ripple into others, consistent with ADR-009.
- **Scaling.** Each module can scale independently once its actual load characteristics are known, consistent with SYSTEM_ARCHITECTURE.md Section 12 and the No Premature Optimization principle in ENGINEERING_GUIDELINES.md Section 3; this decision enables independent scaling as a capability, it does not require exercising it for every module on day one.

## Alignment With Existing ADRs

- **ADR-005 (Data Ownership).** No module's deployment requires shared data access; independent release reinforces exclusive ownership, since a module's own deployable artifact and its own database instance (ADR-025) both change without touching another module's.
- **ADR-014 (Deployment Philosophy).** This ADR is the direct operationalization of ADR-014's independent-deployment requirement; it elaborates how that requirement is realized and does not reopen it.
- **ADR-015 (Evolution Strategy).** Any future change to the deployment model — a further module split, a change to infrastructure topology — proceeds through a decision that explicitly supersedes the relevant ADR, never silently.
- **ADR-021 (Database Technology Strategy).** Module-level deployment independence and domain-level persistence independence are separate, mutually reinforcing decisions; neither presupposes the other's specific technology.
- **ADR-022 (Technology Stack Strategy).** No infrastructure, cloud provider, or specific deployment tool is selected here, consistent with ADR-022 treating Infrastructure as a still-separate, later Technology Selection Boundary.
- **ADR-023 (Backend Technology Decision).** This ADR confirms and elaborates the one-independently-deployable-service-per-module commitment ADR-023 already made; it does not introduce a different topology.
- **ADR-025 (Database Technology Decision).** The six PostgreSQL-backed domains already have their own database instance each; this ADR's module-level deployment independence is consistent with, and does not alter, that persistence-level independence.

## Alternatives Considered

- **Fully independent services immediately, for all eight modules.** Selected. Advantage: satisfies ADR-014 in full from day one, with no transitional period during which the requirement is not yet met. Disadvantage: the highest initial operational surface of the three alternatives, though this is partly mitigated by deferring infrastructure topology to a separate decision.
- **Single unified application.** Rejected outright. Advantage: the lowest possible initial development and operational overhead. Disadvantage: directly contradicts ADR-014's existing requirement that a change to one domain not require redeploying unrelated domains, and cannot be reconciled with it without superseding ADR-014 itself, which is outside this ADR's scope.
- **Hybrid evolutionary approach — co-deploying some modules initially, splitting later.** Seriously considered and rejected for PIOS specifically, though acknowledged as common, well-regarded industry practice in general. Advantage: lower initial overhead than full independence, while preserving a path to full separation. Disadvantage: for as long as any modules remained co-deployed, it would violate ADR-014's binding requirement, and would be inconsistent with the one-service-per-module commitment ADR-023 already made for backend implementation; adopting it now would require a decision superseding both, which this ADR does not attempt.

## Consequences

**Positive.** PIOS satisfies ADR-014's independent-deployment requirement completely and immediately, with no transitional gap. Module boundaries already established in MODULE_STRUCTURE.md and INTERFACE_CONTRACTS.md are reinforced by the release process, not merely documented. Deferring infrastructure topology to a separate decision keeps this ADR from prematurely committing to an operational cost that has not yet been evaluated.

**Negative.** Eight independently releasable units carry more initial operational surface than a single deployable artifact would; the team must be prepared to manage that surface, or accept whatever the future Infrastructure decision determines it costs to manage.

**Long-term implications.** Every future module is expected to follow the same one-independently-deployable-unit model unless a future ADR supersedes this one; the eventual Infrastructure decision (ADR-022) inherits an obligation to support at least eight independently releasable units, and should be evaluated with that already-established fact in mind rather than as an open question.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 7
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-014: Deployment Philosophy](ADR-014-Deployment-Philosophy.md)
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md)
- [ADR-016: Architectural Layers](ADR-016-Architectural-Layers.md)
- [ADR-017: Bounded Context Strategy](ADR-017-Bounded-Context-Strategy.md)
- [ADR-018: Core Architectural Components](ADR-018-Core-Architectural-Components.md)
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md)
- [ADR-022: Technology Stack Strategy](ADR-022-Technology-Stack-Strategy.md)
- [ADR-023: Backend Technology Decision](ADR-023-Backend-Technology-Decision.md)
- [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md)
- SYSTEM_ARCHITECTURE.md, Section 12
- MODULE_STRUCTURE.md, Sections 2–3
- INTERFACE_CONTRACTS.md
- ENGINEERING_GUIDELINES.md, Section 3
