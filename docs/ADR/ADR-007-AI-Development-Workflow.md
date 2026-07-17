# ADR-007: AI Development Workflow

## Status

Proposed

## Context

Constitution Section 9 defines, at a principle level, the responsibilities of AI assistants, human developers, and the Product Owner. ADR-006 establishes that no implementation proceeds without prior, reviewed documentation. An architectural decision is needed on how AI assistants participate in producing that documentation and the work that follows it, without granting them authority the Constitution reserves for humans.

## Problem

What is each participant's role — Claude Code, ChatGPT, Human Architect, and Product Owner — in producing and approving documentation, architecture, and implementation, consistent with Constitution Section 9?

## Decision

Responsibilities are assigned as follows:

- **Claude Code** operates within this repository to draft documentation, ADRs, and implementation strictly within an explicitly stated scope. It marks every architectural proposal it authors with a review status and does not treat its own output as authoritative until accepted by the Human Architect. It follows the Documentation Driven Development rule in ADR-006 and reports completed work per `.ai/EXECUTION_PROTOCOL.md`.
- **ChatGPT**, when used on this project, serves in an advisory and drafting capacity outside the repository's execution flow. Any output it produces is subject to the same review and acceptance requirement as any other proposal before it can influence the repository.
- **Human Architect** holds authority to accept, reject, or amend any architectural proposal, regardless of whether it originated from an AI assistant or a human contributor. Architectural acceptance is a human act.
- **Product Owner** defines product intent and priority and approves product-level decisions, without prescribing architecture or implementation, consistent with Constitution Section 9.

## Alternatives Considered

- **Allowing an AI assistant to accept its own architectural proposals.** Rejected: it removes human accountability for decisions that bind the whole project, directly contradicting Constitution Section 9.
- **Excluding AI assistants from producing documentation or ADR drafts entirely.** Rejected: it would forgo the drafting capability the project has chosen to use, without addressing the actual risk, which is unreviewed authority rather than authorship itself.

## Consequences

### Positive Consequences

- Preserves human accountability over every architectural decision while allowing AI assistants to accelerate drafting.
- Gives every participant a clearly bounded role, reducing ambiguity about who may approve what.
- Keeps the review requirement consistent regardless of whether a proposal's author is human or AI.

### Negative Consequences

- Adds a mandatory review step before any AI-authored proposal can govern further work, which is slower than accepting AI output directly.
- Requires the Human Architect role to remain actively engaged in reviewing proposals as the volume of AI-drafted work grows.

## Future Impact

Any future process document describing how work is proposed, reviewed, or merged must be consistent with the role boundaries established here. This ADR does not define specific tools, platforms, or technical workflow mechanics.

## Related ADRs

Builds on ADR-001 (System Philosophy) and ADR-006 (Documentation Driven Development), applying their review and traceability requirements specifically to AI-authored work.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 9 (AI Development Rules)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-006: Documentation Driven Development](ADR-006-Documentation-Driven-Development.md)
