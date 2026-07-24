# PIOS Architecture Convergence Decision v1.0

Status: Decided — following the Git History Divergence Analysis and the PIOS Architecture Convergence Review conducted after the Sprint 2.5 repository stabilization audit. This document records the resulting decision; it does not itself perform any merge, rebase, branch change, or code change, and none was performed in the course of reaching it.

Derived from the factual findings of that audit and review (git history inspection of `origin/main` after `git fetch`, direct reading of the `mvr/` Python sources and `docs/MILESTONE_*.md` files on that line), and from `PROJECT_CONSTITUTION.md`'s own governance and ADR-authority rules, which this document does not modify.

---

## 1. Context

A repository state audit (Sprint 2.5) and a subsequent `git fetch` revealed that `origin/main` had moved independently of this local clone's cached knowledge of it. Comparing the two after the fetch showed not a simple "behind/ahead" gap but a genuine divergence: both lines share only the repository's very first commit (`8b314dd`) as a common ancestor, and have developed **two independent, non-overlapping histories** since:

- **Local `main`** (89 commits since the common ancestor, spanning 2026-07-17 through 2026-07-24): a Kotlin/Spring Boot, PostgreSQL/RabbitMQ, multi-module platform (`order-management`, `dispatch`, `driver-management`, `passenger-experience`), a React/TypeScript frontend, and a full documentation-first governance apparatus (`PROJECT_CONSTITUTION.md`, Product Decisions, an ADR series through ADR-036, `PIOS_MASTER_CONTEXT.md`, `IMPLEMENTATION_STRATEGY.md`, two sprint reports).
- **`origin/main` / Milestones M2–M14** (13 commits, all dated 2026-07-23, the last merged via a reviewed pull request `#13`): a Python, SQLite-backed research track (`mvr/`) that builds, milestone by milestone, a hash-chained event ledger, fencing tokens against stale commands, crash-recoverable transactional writes, a multi-instance concurrency boundary, and a bidirectional fail-closed recovery admission gate — with no REST layer, no other bounded context, and no product-facing documentation beyond one short `README.md` line and a handful of `MILESTONE_*.md` files.

Both lines independently implement the same core vocabulary (`Proposal`, `Assignment`, the "at most one OPEN Proposal per order" invariant) using entirely different mechanisms and technology stacks. Neither line was built with awareness of the other reaching `main` at the time it did; a mechanical `git merge` or `git rebase` between them would not resolve this — it would only avoid file-level conflicts while leaving two unreconciled architectural answers to the same problem sitting in one branch.

## 2. Decision

- **The Kotlin/Spring Boot platform on local `main` is the primary line of PIOS development.** It is the only line that covers the full product (all four bounded contexts, REST APIs, a frontend) and the only line governed by this project's own Constitution/ADR/Product Decision apparatus.
- **`origin/main`'s Milestone 2–14 line is retained as an Architecture Research Reference**, not as a competing or parallel product line, and not as a branch to be merged, rebased onto, or replaced by. It remains exactly where it is on GitHub, untouched by this decision.
- **No mechanical integration is performed.** No merge, no rebase, no branch change, no cherry-pick. Any adoption of specific ideas from the research line into the primary line happens only through the mechanism already established for architectural change in this project: a dedicated ADR, evaluated and accepted on its own terms.

## 3. Rationale

**Why local is the primary line:**
- **Full product coverage.** It is the only line implementing Order Management, Driver Management, Passenger Experience, and Dispatch together — the research line covers dispatch concurrency alone.
- **Governance.** It is the only line with a Constitution, an ADR lifecycle (Proposed → Accepted, as ADR-036 itself demonstrated), and a Product Decision chain that makes every ratified business rule traceable to its authority.
- **Domain model.** `DOMAIN_MODEL.md`, `EVENT_CATALOG.md`, and the Proposal/Assignment aggregates (ADR-035/ADR-036) give this line a documented, cross-referenced domain model; the research line's domain vocabulary exists only as inline dataclasses and a comment block per milestone.
- **User-facing scenarios.** Only local has a frontend (Passenger Landing, Ride Request, Driver Home, Coordinator) and REST APIs a real client can call; the research line has no client-facing surface at all.

**Why remote is not discarded:**
- It contains genuinely valuable, rigorously proven research into exactly the class of problem local's own ADR-036 explicitly disclaims solving: **fencing** against stale/delayed commands, **deterministic dispatch** behavior under adversarial and concurrent conditions, a **recovery model** with fail-closed, bidirectionally-validated admission, and **concurrency control** across multiple service instances. Each milestone states its own required properties and required proofs, and several are backed by deterministic failure-injection tests and a 10,000-order simulation — a level of rigor local's own concurrency story does not yet have.

## 4. Future Evaluation

The following ideas from the M2–M14 research line are candidates for a **future, dedicated ADR** each — none is adopted by this document, and none is authorized for implementation by it:

- **Proposal fencing** — an order-level generation token, checked and advanced inside Dispatch's existing shared transaction (ADR-036), to close the concurrent-invocation gap ADR-036's own Risks section already names as unresolved.
- **Stale command protection** — rejecting a command that targets a dispatch generation already superseded by a newer one.
- **Recovery model** — a fail-closed admission/readiness gate for the Dispatch service, validating Proposal/Assignment/outbox consistency before accepting traffic, with an explicit "no automatic repair of contradictory persisted state" operational policy.
- **Deterministic dispatch testing** — formalizing named failure-injection points (in the spirit of the research line's `after_fence_before_event`/`after_event_before_commit`) as a standing pattern for proving atomicity/rollback claims, and considering a large-N deterministic simulation test for Dispatch specifically.

**What is not carried over automatically, by this document or any future one, without its own separate justification:**
- The SQLite/Python stack itself, or any technology substitution for PostgreSQL/Kotlin.
- The hash-chained, tamper-evident event ledger — this defends against a threat model (malicious tampering with persisted state) that no PIOS document has named; adopting it would require first naming and ratifying that threat model.
- The research line's milestone documents themselves, treated as if they were already-Accepted ADRs — they are reference material, not ratified architecture for this platform.
- The absence of a REST layer, of other bounded contexts, or of product/business policy (the research line's `propose()` takes a raw driver-id list and a `reason: str = "fairness"` stub — it is not a Fair Opportunity Policy and does not become one by reference here).

## 5. Non-goals

This document explicitly does **not**:

- Change the technology stack of any PIOS module.
- Initiate, plan, or schedule any migration between the two lines.
- Merge, unify, or otherwise combine the two GitHub repositories' histories.
- Replace, supersede, or modify ADR-036 in any way — ADR-036 remains Accepted, as is, and any fencing extension to it is future work requiring its own ADR.
- Authorize any code change. None was made in producing this document.

## References

- Git History Divergence Analysis (this session) — `git fetch` findings, `origin/main` at `9e8a92b`, common ancestor `8b314dd`.
- PIOS Architecture Convergence Review (this session) — detailed comparison of `mvr/*.py`, `docs/MILESTONE_*.md`, and local Dispatch architecture.
- [ADR-036: Proposal↔Assignment Shared Transaction](ADR/ADR-036-Proposal-Assignment-Shared-Transaction.md) — the local-line decision this document leaves untouched and explicitly does not replace.
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md)
- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md) Section 6 (Architecture Governance), Section 7 (ADR Policy)
- [IMPLEMENTATION_STRATEGY.md](IMPLEMENTATION_STRATEGY.md)
