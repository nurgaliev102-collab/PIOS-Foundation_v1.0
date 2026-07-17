# PIOS Engineering Guidelines

Status: Draft — Derived from Approved Foundation. This document defines development principles, quality standards, change discipline, review practices, testing philosophy, and operational expectations for PIOS engineering work. It selects no programming language, framework, tool, CI/CD pipeline, repository structure, or infrastructure.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-021), and the architecture documents that follow from them — [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [MODULE_STRUCTURE.md](MODULE_STRUCTURE.md), [INTERFACE_CONTRACTS.md](INTERFACE_CONTRACTS.md), and [DATABASE_DESIGN.md](DATABASE_DESIGN.md) — in that order of authority. It introduces no engineering practice that contradicts them.

---

## 1. Purpose

Engineering standards exist so that the discipline already established for documentation and architecture — Documentation First, Architecture First, No Shortcuts (PROJECT_CONSTITUTION.md Section 4) — extends consistently into how code is written once implementation begins, rather than stopping at the boundary between architecture and code. This document gives every future contributor, human or AI, one place to find how quality, change, testing, and review are expected to work.

## 2. Engineering Principles

- **Architecture First.** Implementation follows the architecture already approved (PROJECT_CONSTITUTION.md Section 4; ADR-001); no engineering decision contradicts SYSTEM_ARCHITECTURE.md, MODULE_STRUCTURE.md, or INTERFACE_CONTRACTS.md.
- **Documentation Driven Development.** No implementation begins without the documentation that governs it already existing and reviewed, consistent with ADR-006.
- **Small, Controlled Changes.** A change is scoped to what it claims to do, reviewed, and reversible, consistent with the reversibility requirement in ADR-014 and the incremental evolution principle in ADR-015.
- **Explicit Ownership.** A change to a module is made only within that module's boundary, consistent with the exclusive-ownership rule in ADR-005 and ADR-009.
- **Maintainability Over Shortcuts.** The No Shortcuts principle in PROJECT_CONSTITUTION.md Section 4 governs engineering the same way it governs documentation; expedience never justifies bypassing architecture, review, or documentation.

## 3. Code Quality Principles

- **Readability.** Code is written to be understood by someone encountering it for the first time, consistent with the Readability principle in PROJECT_CONSTITUTION.md Section 14.
- **Simplicity.** The simplest implementation that satisfies an already-documented requirement is preferred, extending the Simplicity product principle in PRODUCT_FOUNDATION.md Section 5 into engineering.
- **Consistency.** The same concept is implemented the same way everywhere it appears, consistent with PROJECT_CONSTITUTION.md Section 14.
- **Low Complexity.** Complexity is introduced only when the already-approved architecture requires it, never speculatively.
- **Clear Responsibility.** Code belonging to a module implements only that module's owned responsibility (MODULE_STRUCTURE.md Section 2, High Cohesion); it never absorbs another module's concern.
- **No Premature Optimization.** A performance concern is addressed once it is observed, not assumed in advance, consistent with the Performance Considerations already deferred to implementation in DATABASE_DESIGN.md Section 8.

## 4. Development Workflow

No specific tool is named for any step below.

- **Planning.** Work begins only once the relevant documentation exists and has been reviewed (ADR-006); planning identifies which module and interface contract (MODULE_STRUCTURE.md, INTERFACE_CONTRACTS.md) a change touches before implementation starts.
- **Implementation.** Implementation stays within the module boundary and interface contracts already established; it introduces no capability, command, query, or event beyond what DOMAIN_MODEL.md, EVENT_CATALOG.md, and INTERFACE_CONTRACTS.md already establish.
- **Review.** Every change is reviewed before it is accepted, extending the review requirement already established for AI-authored proposals in ADR-007 to apply to all changes regardless of author.
- **Validation.** A change is verified against the behavior the documentation already describes — USE_CASE_CATALOG.md outcomes, DOMAIN_MODEL.md invariants — before it is considered complete, consistent with the Definition of Done in PROJECT_CONSTITUTION.md Section 11.
- **Documentation Update.** Where implementation reveals a documentation gap or ambiguity, the documentation is corrected as part of the same change, consistent with ADR-006's requirement that documentation gaps are defects, not license to proceed undocumented.

## 5. Change Management

- **How architectural changes happen.** Only through a new ADR that explicitly supersedes the decision being changed, never silently, consistent with PROJECT_CONSTITUTION.md Section 7 and ADR-015.
- **When an ADR is required.** Whenever a change affects system structure, component boundaries, domain ownership, or a previously recorded architectural decision (PROJECT_CONSTITUTION.md Section 6; ADR-009); a change that stays within an already-approved boundary does not require a new ADR.
- **When documentation must change.** Whenever a change alters what a document already states, that document is updated as part of the same change, consistent with ADR-006 and the requirement that documentation is never left silently out of date.
- **The existing ADR process.** The ADR lifecycle already established in PROJECT_CONSTITUTION.md Section 7 and ADR-015 — superseding, never editing in place; a stated migration window for a deprecated version — governs every architectural change engineering work might require.

## 6. Testing Philosophy

No testing framework or test code is described.

- **Testing purpose.** To verify that implementation matches the behavior already documented — DOMAIN_MODEL.md invariants, USE_CASE_CATALOG.md outcomes — not to discover what that behavior should be.
- **Levels of confidence.** Consistent with the testing philosophy already established in ADR-013: confidence weighted toward a module's own internal behavior and its declared boundary (its interface contracts, INTERFACE_CONTRACTS.md), with verification spanning more than one module reserved for critical, cross-module flows only.
- **Quality expectations.** A change is not complete, per the Definition of Done in PROJECT_CONSTITUTION.md Section 11, without verification at the levels applicable to it.

## 7. Error Handling Principles

No language-specific exception mechanism is described.

- **Predictable failures.** An outcome that would violate a domain's own invariant is rejected by the module that owns that invariant, never silently allowed, consistent with the Error Handling Philosophy already established in APPLICATION_ARCHITECTURE.md Section 11.
- **Clear responsibility.** Only the module that owns an invariant has authority over why it was violated (ADR-005); no other module reinterprets or overrides that reason.
- **User impact awareness.** A failure is communicated back to the actor whose use case (USE_CASE_CATALOG.md) could not be fulfilled, without exposing internal reasoning that belongs to another module.
- **Observability.** Every failure is recorded consistent with the Observability Principles already established in ADR-012, so it can be diagnosed after the fact.

## 8. Logging and Observability Principles

No tool or infrastructure is named.

- **Important events.** Every domain event already catalogued (EVENT_CATALOG.md) and every rejected outcome (Section 7) is recorded as it occurs, consistent with ADR-012.
- **Operational visibility.** Every module provides its own logs, metrics, and tracing as an intrinsic part of its own design, not added afterward, consistent with ADR-012 and SYSTEM_ARCHITECTURE.md Section 15.
- **Traceability.** A single actor's interaction that crosses more than one module (INTERFACE_CONTRACTS.md Section 5) can be followed across them, consistent with ADR-012.

## 9. Security Engineering Principles

Consistent with ADR-011; no implementation mechanism is described.

- **Secure by Design.** Security is considered when a change is designed, not added afterward.
- **Data Protection.** Sensitive information — Payment Record and Personal Client Relationship in particular (DATABASE_DESIGN.md Section 9) — is handled with particular care, consistent with the Trust principle in PROJECT_CONSTITUTION.md Section 3.
- **Least Privilege.** A change grants only the access it requires; no change grants broader access for convenience.

## 10. Documentation Standards

- **Required documentation.** Every change is accompanied by the documentation that governs it, already existing per ADR-006; no change ships without it.
- **Traceability.** Every piece of documentation traces to the Constitution, an ADR, or a higher-authority architecture document, consistent with the Traceability principle in ADR-001 and the pattern already established throughout this documentation set.
- **Keeping documents synchronized.** When implementation and documentation diverge, the documentation is corrected, never left to silently drift, consistent with the No Hidden Assumptions principle in PROJECT_CONSTITUTION.md Section 4.

## 11. Code Review Principles

- **Review goals.** Confirm that a change is consistent with the architecture already approved and does not silently introduce a new decision.
- **Architecture compliance.** A reviewer checks a change against MODULE_STRUCTURE.md and INTERFACE_CONTRACTS.md before checking anything else; a change that violates a module boundary or an interaction contract is not accepted regardless of its other qualities.
- **Quality expectations.** A reviewer applies the Code Quality Principles in Section 3 consistently, regardless of who authored the change.
- **Knowledge sharing.** Review is also how a reviewer builds their own understanding of the module being changed, consistent with the Consistency and Traceability principles in PROJECT_CONSTITUTION.md Section 14.

## 12. AI-Assisted Development Rules

- **AI may assist development.** An AI assistant, including Claude Code, may draft implementation and documentation within an explicitly stated scope, consistent with ADR-007.
- **AI must follow approved architecture.** Every AI-authored change is bound by the same architecture, module boundaries, and interface contracts as any human-authored change; AI receives no exception.
- **AI cannot introduce unapproved decisions.** An AI assistant never invents a business rule, architecture, domain concept, or capability not already established in the approved documentation set, consistent with PROJECT_CONSTITUTION.md Section 9.
- **AI-generated code requires validation.** AI-authored work is marked with a review status and does not govern the system until a human with appropriate authority accepts it, consistent with ADR-007; the same review, testing, and documentation standards in this document apply to AI-authored work as to any other.
- **No autonomous architectural changes.** An AI assistant does not accept its own architectural proposals and does not merge its own changes without human review, consistent with ADR-007's prohibition on AI self-approval.

## 13. Technical Debt Management

- **Identification.** A deviation from the architecture or the Code Quality Principles in Section 3, accepted deliberately rather than by oversight, is recorded as technical debt, not silently left undocumented.
- **Prioritization.** Technical debt is evaluated against the Long-Term Maintainability element of ADR-001, not deferred indefinitely by default.
- **Controlled repayment.** Repaying technical debt follows the same Change Management discipline in Section 5 as any other change; it is not exempted from review or documentation because it is "just cleanup."

## 14. Traceability

| Section | Constitution | ADR | System Architecture | Module Structure | Interface Contracts | Database Design |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 4 | ADR-001, ADR-006 | — | — | — | — |
| 2. Engineering Principles | Section 4 | ADR-001, ADR-005, ADR-006, ADR-009, ADR-014, ADR-015 | — | — | — | — |
| 3. Code Quality Principles | Section 14 | ADR-001 | — | Section 2 | — | Section 8 |
| 4. Development Workflow | Section 11 | ADR-006, ADR-007 | — | — | Section 5 | — |
| 5. Change Management | Sections 6–7 | ADR-006, ADR-009, ADR-015 | — | — | — | — |
| 6. Testing Philosophy | Section 11 | ADR-013 | — | — | Section 5 | — |
| 7. Error Handling Principles | — | ADR-005, ADR-012 | — | — | — | — |
| 8. Logging and Observability Principles | — | ADR-012 | Section 15 | — | Section 5 | — |
| 9. Security Engineering Principles | Section 3 (Trust) | ADR-011 | — | — | — | Section 9 |
| 10. Documentation Standards | Section 4 | ADR-001, ADR-006 | — | — | — | — |
| 11. Code Review Principles | Section 14 | — | — | Section 5 | — | — |
| 12. AI-Assisted Development Rules | Section 9 | ADR-007 | — | — | — | — |
| 13. Technical Debt Management | — | ADR-001 | — | — | — | — |

Where this document is silent, no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.
