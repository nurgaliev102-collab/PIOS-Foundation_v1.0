# ADR-022: Technology Stack Strategy

## Status

Proposed

## Context

The architecture foundation for PIOS is now complete: the Constitution, the ADR Foundation through ADR-021, SYSTEM_ARCHITECTURE.md, DOMAIN_MODEL.md, APPLICATION_ARCHITECTURE.md, API_SPECIFICATION.md, DATABASE_DESIGN.md, MODULE_STRUCTURE.md, INTERFACE_CONTRACTS.md, and ENGINEERING_GUIDELINES.md are all in place. Every prior ADR that touched technology — ADR-004 (API Style) and ADR-021 (Database Technology Strategy) in particular — deliberately deferred specific technology selection, establishing only principles and, for storage, a per-domain evaluation framework. Implementation is the next phase, and it requires actual technology decisions: a language, frameworks, infrastructure, and tooling. This ADR establishes the decision framework those choices must follow; it does not make them.

## Decision

Every technology decision — for the application language, backend framework, frontend approach, database technologies, infrastructure, testing ecosystem, and developer tooling alike — is evaluated against the following principles:

1. **Architecture Compatibility.** A technology must be capable of implementing the module boundaries (MODULE_STRUCTURE.md) and interface contracts (INTERFACE_CONTRACTS.md) already established, without requiring either to be compromised.
2. **Long-Term Maintainability.** Consistent with the Long-Term Maintainability element of ADR-001 and the Quality Principles in PROJECT_CONSTITUTION.md Section 14, a technology is evaluated for its effect on the system's comprehensibility over years, not immediate convenience.
3. **Developer Productivity.** A technology should let engineers work within the discipline already established in ENGINEERING_GUIDELINES.md — documentation-first, small controlled changes — without unnecessary friction.
4. **Operational Simplicity.** Consistent with the Operational Simplicity criterion in ADR-021, a technology the team can operate reliably is preferred over one that is more capable but harder to run.
5. **Community Maturity.** A technology with established, well-documented practice reduces the risk of unsupported or abandoned dependencies over PIOS's long-term life.
6. **Security.** Consistent with ADR-011, a technology must be capable of upholding Security by Design, Least Privilege, and Defense in Depth at the boundaries already established.
7. **Performance Suitability.** A technology's performance characteristics must match the access patterns already anticipated for the boundary it serves (DATABASE_DESIGN.md Section 8), evaluated against actual need, consistent with the No Premature Optimization principle in ENGINEERING_GUIDELINES.md Section 3.
8. **Team Capability.** Consistent with ADR-021, whether the team has, or can reasonably acquire, the capability to use a technology reliably is a first-class evaluation criterion.
9. **AI-Assisted Development Compatibility.** A technology must be well-documented and conventionally structured enough for AI-assisted development tools to work with it reliably, consistent with ADR-007 and the AI-Assisted Development Rules in ENGINEERING_GUIDELINES.md Section 12.

This ADR selects no specific language, framework, product, or provider. Each technology decision within its own boundary is made by a future, separate decision that evaluates candidates against these principles.

## Technology Selection Boundaries

The following are separate decisions, each requiring its own future evaluation against the principles above; none is resolved by this ADR:

- Application language.
- Backend framework.
- Frontend approach.
- Database technologies — already governed by the per-domain evaluation framework in ADR-021.
- Infrastructure.
- Testing ecosystem.
- Developer tooling.

Each boundary may be decided independently and at a different time from the others; a decision in one boundary does not automatically determine another unless a specific technical dependency requires it, and any such dependency must itself be stated in the decision that relies on it.

## Architecture Constraints

Whatever technology is eventually selected for any boundary above, it must preserve:

- **Domain isolation** (ADR-005, ADR-009). No technology choice may require two domains to share data access or bypass their declared boundary.
- **Module boundaries** (MODULE_STRUCTURE.md). No technology choice may require a module to depend on another module's internals.
- **Interface contracts** (INTERFACE_CONTRACTS.md). No technology choice may alter the meaning of an already-established command, query, or event.
- **Data ownership** (ADR-019). No technology choice may relocate ownership of information to a domain that does not already own it.
- **Evolution capability** (ADR-015, ADR-021). No technology choice may foreclose a domain's future independent evolution or the per-domain technology evaluation ADR-021 already establishes.

A candidate technology that cannot satisfy these constraints is not eligible for selection, regardless of how well it satisfies the principles in Decision.

## AI Development Considerations

Consistent with ADR-007:

- **Technology choices must be understandable by AI-assisted development tools.** A technology's documentation and conventions must be legible enough for an AI assistant operating under ADR-007 to work with it reliably, consistent with the AI-Assisted Development Compatibility principle in Decision.
- **AI cannot choose technologies without an approved decision.** An AI assistant may evaluate and recommend candidates against the principles and constraints in this ADR, but a technology selection becomes binding only once accepted by the Human Architect, consistent with ADR-007's prohibition on AI self-approval.
- **Generated implementation must follow the selected stack.** Once a technology decision is made for a given boundary, AI-generated implementation conforms to it; an AI assistant does not introduce an alternative technology for its own convenience.

## Alternatives Considered

- **Single unified technology stack, applied uniformly across every boundary.** Would simplify operational surface, but is not mandated here, since domains and boundaries already show different characteristics — per-domain persistence in ADR-021 — that a single choice may not equally serve.
- **Multiple specialized technologies, chosen independently per boundary.** Consistent with the per-domain evaluation already established in ADR-021 and the boundary-by-boundary framework in this ADR; also not mandated.
- **Managed services**, where a technology's operation is delegated to an external provider. Reduces operational burden, consistent with Operational Simplicity, at the cost of a dependency that would itself need evaluation as an external integration boundary under ADR-020; appropriate for some boundaries, not decided here.
- **Self-managed infrastructure.** Gives more direct control at higher operational cost; appropriate for some boundaries, not decided here.

Neither single-stack versus multiple-stack, nor managed versus self-managed, is selected or excluded by this ADR; each remains available to whichever specific technology decision it applies to, weighed against the principles and constraints above.

## Consequences

**Positive.** Every future technology decision has a consistent, already-approved framework to evaluate against, rather than ad hoc reasoning. Consistency and predictability follow from applying the same principles to every boundary. Maintainability is protected because no technology choice can proceed without demonstrating it preserves the architecture already approved.

**Negative.** Technology choices, once made, create long-term commitments that are costly to reverse. Deferring selection until this framework exists means implementation cannot begin until at least the first, most foundational technology decisions — the application language in particular — are made using it.

**Future Implications.** Every future technology-selection decision must reference this ADR and demonstrate how its chosen candidate satisfies the principles in Decision and the constraints in Architecture Constraints. A change to the principles or constraints themselves requires a superseding ADR, consistent with ADR-015.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Sections 3, 4, 14
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-007: AI Development Workflow](ADR-007-AI-Development-Workflow.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-011: Security Principles](ADR-011-Security-Principles.md)
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md)
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md)
- [ADR-020: Integration Philosophy](ADR-020-Integration-Philosophy.md)
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md)
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md)
- [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md)
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md)
- [DATABASE_DESIGN.md](../DATABASE_DESIGN.md), Section 8
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Sections 3, 9, 12
