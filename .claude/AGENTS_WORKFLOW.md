# PIOS AI Agent Workflow

This file describes the full cycle a piece of product work moves through in this repository, and which agent (`.claude/agents/*.md`) is responsible for each stage. It complements, and does not replace, `.claude/CLAUDE.md`'s "Development Workflow" section — that section defines the Architect → Developer → QA Reviewer stage gate; this file extends the cycle on both ends (Evidence before Architect, Release Manager after QA Reviewer).

## The cycle

```
Evidence
   ↓
Product Decision
   ↓
Architect
   ↓
ADR (if required)
   ↓
Developer
   ↓
QA Reviewer
   ↓
Release Manager
   ↓
Knowledge Update
   ↓
Tag
```

## Stage responsibilities

### 1. Evidence — `evidence-analyst`

Maintains the Evidence Log (`E-001`, `E-002`, ...). Before any new Sprint is planned, checks whether the Evidence Log actually contains an entry that justifies it. Distinguishes a real, cited observation of user/pilot behavior from design reasoning or a self-audit finding dressed up as one. Never invents an observation, a participant, a date, or a count. Does not write code, does not author ADRs.

### 2. Product Decision — Product Owner (human)

Where evidence surfaces a genuine open question (a business rule, a pricing model, a scope boundary not yet ratified), the Product Owner decides. No agent invents a business rule on its own authority (ADR-002). A Product Decision, once made, is recorded in the relevant document and becomes binding on every later stage — Architect checks new work against it, not around it.

### 3. Architect — `architect`

Before planning a Sprint, checks: is there Evidence for it; does it conflict with an existing Product Decision; is a new ADR required. Reviews whether a proposed change is consistent with PIOS's ratified architecture (bounded contexts, the "reference, not ownership" rule, existing ADRs) before any code is written. Writes or updates ADRs. Does not write production code.

### 4. ADR — `architect` (conditional stage)

Not every Sprint needs one. An ADR is required before scaffolding a new module, or before any change that alters a ratified architectural decision. An additive, non-breaking change to an existing aggregate does not automatically need one (see `architect.md`'s own precedent-based test). The Architect names or writes the ADR; it is never written after the fact to justify what already happened.

### 5. Developer — `developer`

Implements the approved solution. Starts only once Evidence (if the task is new product functionality), a Product Decision (if one was needed), and Architect approval (a reviewed direction, ADR named/written, a specific scope) all exist for the task. Does not begin new functionality on its own initiative. Makes the smallest correct change, preserves architecture, updates tests when behavior changes, never fabricates results.

### 6. QA Reviewer — `qa-reviewer`

Verifies the completed work independently — does not trust the Developer's own report. Checks functional correctness (real scenarios, real requests against real running services where possible) and architectural conformance: does the implementation match what the cited ADR actually decided, are bounded-context boundaries intact, did any unconfirmed/unratified functionality slip in, does the user scenario actually work end to end. Reports pass, fail, or "not verified and why." Never fixes code — no write access, by design.

### 7. Release Manager — `release-manager`

Runs after QA Reviewer's report is accepted. Checks release readiness: real `git status`, exact composition of the proposed commit against what was actually reviewed (flags anything dirty-but-unreviewed or reviewed-but-missing), prepares a commit message matching this repository's convention, and a changelog entry if one applies. Creates a tag only after explicit human confirmation of the commit. Does not modify production code, does not fix bugs it notices, does not make architectural calls. Never pushes without separate, explicit confirmation.

### 8. Knowledge Update

Before tagging, whatever this Sprint changed about the project's own working knowledge is reconciled — the Evidence Log entries it consumed or added (`evidence-analyst`'s files), documentation index entries the Architect knows are stale (e.g. `docs/README.md`'s ADR table), and anything a future Sprint would otherwise have to rediscover from scratch. This stage is not yet bound to a specific agent — it draws on whichever role owns the document in question (evidence-analyst for the Evidence Log/hypotheses/UX backlog, architect for ADR-adjacent index pages) rather than being a new independent role. Skipping it is how a project accumulates the kind of undiscovered drift this repository has already hit more than once (e.g. an ADR index silently falling behind the real `docs/ADR/` directory).

**Release Checklist (Release Manager — not a new stage, a gate checked after Knowledge Update, before Tag):**

```
□ Evidence актуален
□ Product documentation актуальна
□ ADR индекс актуален
□ Sprint scope закрыт
□ Working tree clean
□ QA approved
□ Human approved
```

This belongs to Release Manager's own responsibility, not a new role — it is the concrete, checkable form of "release readiness" that section already names. Its purpose is specifically to prevent a tag being created while documentation (Evidence, ADR index, product docs) is still behind the code — the same class of drift the Knowledge Update step exists to close, checked here as a final gate rather than trusted as already done.

### 9. Tag

The Sprint is closed once Release Manager has committed, the human has confirmed, the Release Checklist above passes, and the tag is created. Push remains a separate, explicit, never-implied action — consistent with this project's standing rule that push is never performed unless newly and explicitly requested.

## How this relates to `.claude/CLAUDE.md`

`.claude/CLAUDE.md`'s "Development Workflow" section states the core rule that governs the middle of this cycle: *"A task is considered complete only after QA Reviewer reports it as passed or explicitly lists the remaining limitations. The Architect never modifies production code. The QA Reviewer never modifies production code."* This file adds the same discipline to the two ends of the cycle QA Reviewer's own sign-off doesn't cover: whether the Sprint should have started at all (Evidence, Product Decision), and whether what gets committed and tagged is exactly what was reviewed (Release Manager). Neither new stage overrides the rule that the Architect and QA Reviewer never modify production code, and neither introduces a new party who can invent a business rule or an architectural decision on their own authority.
