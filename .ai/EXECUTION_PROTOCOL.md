# Execution Protocol

This document defines how work is executed in the PIOS repository. It applies to every Claude Code session and every milestone, from Foundation v1.0 onward. It is subordinate to the rules in [CLAUDE.md](../CLAUDE.md) and must be read together with it.

## Execution Rules

1. Work only within the scope explicitly requested for the current task or milestone.
2. Do not create, modify, or delete files outside the stated scope.
3. Do not implement business logic, backend code, or frontend code unless the corresponding documentation and architecture already exist and the task explicitly requests implementation.
4. Do not introduce dependencies, frameworks, or tooling that has not been specified.
5. Do not assume technology choices that have not been documented. Use placeholders where a decision is pending.
6. Every change must be traceable to an explicit instruction or an existing, approved document.

## Milestone Workflow

1. A milestone is defined with an explicit goal and an explicit list of deliverables.
2. Work begins only after the milestone scope is understood. If the scope is ambiguous, stop and ask before proceeding.
3. Deliverables are produced in the order they support each other: documentation and architecture before implementation.
4. Each milestone is completed in full before the next milestone begins. Partial or speculative work on future milestones is not carried out.
5. On completion, the milestone is reported using the Reporting Format below.

## Definition of Done

A milestone is done only when all of the following are true:

- Every deliverable listed for the milestone exists and matches its description.
- All Markdown files use valid, consistent formatting.
- All internal links resolve to existing files or sections.
- No placeholder content, lorem ipsum, or fabricated detail remains, except for explicitly labeled placeholders awaiting a future decision.
- No business rules, architecture, or product functionality have been invented.
- Changes have been committed with a clear, accurate commit message describing the milestone.

## Reporting Format

At the end of a milestone, report:

1. Files created, with full relative paths.
2. Files modified, with full relative paths.
3. Repository tree reflecting the current state.
4. Validation result: confirmation that links and formatting were checked, and the outcome.
5. Commit hash of the resulting commit.

Do not report a milestone as complete if any of the above cannot be provided.

## Forbidden Actions

- Writing backend, frontend, or database code before the corresponding documentation exists.
- Inventing product functionality, business rules, pricing logic, or workflows not explicitly specified.
- Changing or removing existing architecture decisions without a corresponding ADR.
- Deleting documentation.
- Expanding the scope of a milestone without explicit instruction.
- Committing incomplete or unverified work as a milestone deliverable.
- Bypassing documentation-first or architecture-first ordering for the sake of speed.

## When Claude Must Stop

Claude must stop and ask for clarification instead of proceeding when:

- A requested task requires a business rule, product decision, or technology choice that has not been specified.
- A task would require changing existing architecture without an accompanying ADR.
- The scope of a request is ambiguous or could be interpreted as extending beyond the current milestone.
- A task would require deleting or overwriting existing documentation.
- Required upstream documentation or architecture for a task does not yet exist.
