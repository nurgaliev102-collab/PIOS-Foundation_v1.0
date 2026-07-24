# Sprint 1 Report

Sprint: Foundation Stabilization. Executed in two stages per the Product Owner's decision on the Implementation Review: Stage A (Tasks 2–5), Stage B (draft-only ADR for Task 1). No exception to governance was taken; Task 1's actual implementation remains blocked pending ADR-036's approval, exactly as decided.

## Выполненные задачи

- **Task 1 (blocked, not implemented)**: confirmed still blocked, per the Implementation Review and the Product Owner's own confirming decision. No code changed for this task. Resolved instead by drafting `ADR-036` (Stage B, see below).
- **Task 2**: verified the full Proposal → AcceptProposal → Assignment → Domain Events path against current code. Findings below.
- **Task 3**: synchronized `DOMAIN_MODEL.md` and `EVENT_CATALOG.md` with the already-implemented, already-ratified-by-Product-Decision Proposal aggregate. No architecture rewritten; every added statement is traced to an already-ratified source (ADR-035, Product Decision: Opportunity Before Assignment, Product Decision: Opportunity/Acceptance/Commitment Semantics, Product Decision: Electronic Dispatcher MVP Blockers).
- **Task 4**: analyzed the `.claude/CLAUDE.md` vs. root `CLAUDE.md` conflict; prepared a merge proposal (not applied). No file deleted.
- **Task 5**: ran the complete test suite across all four backend modules (337 tests total). Categorized every failure. Fixed none, because none fell into the "test bug" or "implementation bug" categories the sprint authorized fixing — full reasoning below.
- **Stage B**: drafted `ADR-036: Proposal↔Assignment Shared Transaction`, `Status: Proposed`, containing every section requested (Problem, Context, Constraints, Considered Options, Decision, Consequences, aggregate-independence/ADR-035/DDD justifications, why-not-Saga, why-not-Domain-Event-orchestration, Migration Impact, Risks). No code changed, no architecture changed, `PERSISTENCE_ARCHITECTURE.md` not touched.

## Изменённые файлы

| File | Change |
|---|---|
| `docs/DOMAIN_MODEL.md` | §Aggregates: "Pre-Commitment Business Fact — Name Not Yet Ratified" → "Proposal", Invariant/Lifecycle filled in from ratified sources; §Domain Events: 4 new events; §Commands: 4 new commands; §Business Invariants: 1 new invariant; §State Lifecycles: Proposal entry updated; §Relationships: 1 new relationship line; header derivation line extended |
| `docs/EVENT_CATALOG.md` | §6 retitled "Dispatch Domain Events (Proposal and Assignment)", 4 new event rows added, explanatory note on non-cross-domain status, 4 new traceability rows |
| `backend/dispatch/.../application/ProposalApplicationService.kt` | KDoc only — updated the outbox-non-wiring rationale to reflect the now-catalogued (but still not cross-domain) status of the four Proposal events |
| `docs/ADR/ADR-036-Proposal-Assignment-Shared-Transaction.md` | New — `Status: Proposed` |
| `docs/README.md` | Added ADR-036 to the ADR table; added a note flagging the pre-existing ADR-027–034 indexing gap (found, not fixed — out of this sprint's scope) |

No production/business logic changed. No test file changed (none required fixing — see Results below).

## Архитектурные решения

None taken unilaterally. One architectural question was formally answered as a **proposal awaiting approval**: `ADR-036` recommends Option A (single shared transaction, owned by `ProposalAssignmentOrchestrationService`) over Option B (Saga/Process Manager) and Option C (Domain Event orchestration), with full justification against ADR-035, DDD literature, and the project's own prior rejections of B and C for this exact problem in Sprint IMPLEMENTATION-004. This is a recommendation on record, not a decision — it becomes binding only if and when it is reviewed and marked `Accepted`, per the Product Owner's own instruction.

## Обновлённая документация

- `DOMAIN_MODEL.md` and `EVENT_CATALOG.md` now name the Proposal aggregate and its four domain events, closing the documentation-lag identified in `PIOS_MASTER_CONTEXT.md` §5.3 and the earlier full-repository documentation audit.
- `docs/README.md` gained one new ADR row and one explicit note about a pre-existing, unrelated indexing gap (ADR-027–034) — found while working in this file, disclosed rather than silently left implicit, but not corrected (out of this sprint's stated scope).
- `ADR-036` is new, `Status: Proposed`, awaiting the review the Product Owner named as the next step.

## Результаты тестирования

Full suite, all four modules, real PostgreSQL/RabbitMQ, JDK 21.0.4/Gradle 8.10.2:

| Module | Tests | Failures |
|---|---|---|
| `order-management` | 75 | 0 |
| `passenger-experience` | 13 | 0 |
| `driver-management` | 61 | 2 |
| `dispatch` | 188 | 5 |
| **Total** | **337** | **7** |

**All 7 failures fall outside the two categories this sprint authorized fixing ("test bug" / "implementation bug")** — every one is environment/test-isolation flakiness, verified by evidence, not assumed:

- **Dispatch's 5 failures**: the same pre-existing PostgreSQL data-pollution failures identified and root-caused earlier — a real, non-reset dev/test database (`pios_dispatch`) accumulating fixture rows across repeated runs and sessions. Re-confirmed present, unchanged in count and identity, in this run.
- **Driver-management's 2 failures** (`DriverAvailabilityChangedEnvelopeTest` — new finding this sprint, first time this module's tests were run in this session): both pass when the class is run in isolation (`BUILD SUCCESSFUL`, verified by direct re-run), and both fail identically when run as part of the full suite — the exact same signature as the dispatch module's own known cross-test interference (some other test class in the same JVM run, using the same shared `pios_driver_management` outbox table, races with this class's own freshly-created records). Confirmed the interfering record is not simply leftover unpublished rows (`driver_management_outbox` shows 0 unpublished rows outside any test run), so the cause is live cross-test-class interference during the shared run, not stale data — recorded precisely, not guessed further.

No implementation code and no test code was modified in response to either category, because neither is a test bug (the tests' own logic and defensive design — filtering by a unique random marker — are correct) nor an implementation bug (the domain/application code they exercise is correct, confirmed by the same tests passing in isolation). This is a fourth category the sprint's own three-way classification didn't name; flagged explicitly below as a Sprint 2 recommendation rather than forced into an ill-fitting bucket.

**Proposal → Assignment → Domain Events path (Task 2), in detail:**
- **Publish-once**: unaffected, unchanged. `OutboxRelay` marks a record published only after broker confirmation; Proposal's own four domain events are not written to the outbox at all (unchanged, deliberate), so there is no double-publish surface for them. `OrderAssigned`/`AssignmentAccepted` retain their existing, previously-verified at-least-once/idempotent-consumer guarantee.
- **Aggregate invariants**: both Proposal's Root Invariant and Assignment's one-active-per-order invariant are enforced correctly, within their own transactions, in current code.
- **TOCTOU**: the read-before-write windows closed in prior work (self-fetching overloads inside `TransactionRunner`, and the three REST controller methods that used to read externally) remain closed, confirmed by direct code re-inspection. **One TOCTOU window remains, found, not fixed**: `ProposalAssignmentOrchestrationService.acceptProposal`'s own initial `proposalRepository.findById` read is still outside any transaction, and the subsequent `proposalApplicationService.acceptProposal(proposal, command)` call operates on that externally-read, in-memory object rather than re-reading fresh inside its own transaction. Deliberately not patched this sprint: it is a strict subset of the exact problem `ADR-036` addresses, and Option A's own implementation will naturally restructure this same method to read once, inside its new shared transaction — patching it separately now would be throwaway work superseded within the same feature, which the sprint's own "не улучшать архитектуру без необходимости" constraint argues against.
- **Double writes**: none found. `Assignment.create`'s own invariant, checked fresh inside its own transaction (Milestone 14A/14B work, re-verified this sprint), prevents two Assignments for one order through every currently-live path, including the deprecated manual endpoint.

## Найденные проблемы

1. **Task 1 remains blocked** — `ADR-036` is `Proposed`, not yet `Accepted`; Task 1's actual implementation cannot begin until it is.
2. **The orchestrator's own read-before-write TOCTOU** (above) — found, deliberately deferred to the same future task that implements `ADR-036`.
3. **`.claude/CLAUDE.md` vs. root `CLAUDE.md` — real, substantive conflict**, narrower than "the whole file": only the "Roles" section (`.claude/CLAUDE.md` lines 7–13, 67–71) actually contradicts anything — it names "ChatGPT" as "the system architect... responsible for architecture, technical decisions," which appears in no ratified document (`PROJECT_CONSTITUTION.md` §6 names a Chief-Software-Architect-equivalent role, never an external LLM; §9's AI Development Rules treat all AI tooling, including "Claude Code and other AI tooling," symmetrically). Everything else in `.claude/CLAUDE.md` (engineering principles, working process, reporting format, code-quality preferences, "stop and explain conflicts") is *compatible* with root `CLAUDE.md`, not contradictory — largely a more granular restatement of what `.ai/EXECUTION_PROTOCOL.md` already covers.
   **Merge proposal (not applied)**: (a) remove or rewrite the "Roles" section to remove the ChatGPT-as-architect claim, replacing it with a reference to `PROJECT_CONSTITUTION.md` §6/§9's actual governance; (b) fold the remaining, non-conflicting content (engineering principles, working process, reporting format) into `.ai/EXECUTION_PROTOCOL.md`, which already serves exactly that role, rather than maintaining a second, competing "entry point" file; (c) as a minor, separate technical defect: the file currently contains two concatenated `# Project Memory` sections with no line break between them (line 60/61) — worth a clean rewrite regardless of the merge decision. This requires a decision from whoever owns Architecture Governance; not applied here.
4. **`docs/README.md`'s ADR table was already missing ADR-027 through ADR-034** before this sprint — found while adding ADR-036's own row, flagged in the file itself, not corrected (outside this sprint's scope).
5. **Cross-test interference under full-suite execution is not confined to the `dispatch` module** — this sprint is the first time it was confirmed in `driver-management` too. The shared, non-reset PostgreSQL databases used by both modules' Postgres-backed integration tests appear to be a systemic pattern, not a one-off. Recommend a dedicated investigation, not another one-off diagnosis, in Sprint 2.

## Что рекомендуется сделать в Sprint 2

1. Review and, if approved, mark `ADR-036` `Accepted` — this is the actual unblock for Task 1.
2. Once `ADR-036` is `Accepted`: implement Option A, closing both the cross-aggregate atomicity gap and the orchestrator's own residual TOCTOU (Found Problem #2) as one piece of work, plus extend `PERSISTENCE_ARCHITECTURE.md` Section 6/8 with the small, explicit note `ADR-036` itself recommends.
3. Decide the `.claude/CLAUDE.md` merge proposal (Found Problem #3) — needs an owner with Architecture Governance authority, not an engineering task.
4. Fix `docs/README.md`'s ADR-027–034 indexing gap (Found Problem #4) — small, mechanical, no architecture involved.
5. Investigate cross-test interference under full-suite execution as its own item (Found Problem #5) — likely candidates: per-module/per-class test database isolation, or serializing PostgreSQL-touching test classes; needs its own scoped investigation before a fix is chosen, not a guess.
