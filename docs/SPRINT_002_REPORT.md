# Sprint 2 Report

Sprint: ADR-036 Implementation. Goal: implement Option A (single shared transaction, owned by `ProposalAssignmentOrchestrationService`) exactly as `ADR-036` (Accepted, 2026-07-24) authorizes it — no deviation, no scope beyond it.

## Выполненные изменения

- **Задача 1 (общая транзакция).** `ProposalAssignmentOrchestrationService.acceptProposal` now opens the one shared transaction ADR-036 authorizes and is the sole `TransactionRunner` owner for this flow. The Proposal read, the Proposal write, the Assignment's own existing-assignments read, the Assignment write, and the Assignment's outbox write all happen inside it — either all of it commits or none of it does.
- **Задача 2 (TOCTOU).** Closed as a direct, structural consequence of Задача 1, not a separate patch: the orchestrator's `proposalRepository.findById` read now happens *inside* `transactionRunner.run { }`, instead of before a transaction that opens afterward. No standalone workaround was added.
- **Задача 3 (Domain Events).** Verified, not changed: Assignment's own outbox write (`OrderAssigned`) still happens exactly once, inside the same physical transaction as the Assignment's own state write (ADR-032, unaffected by this sprint). Proposal still writes no outbox record at all (deliberate, pre-existing, unrelated to ADR-036 — see `ProposalApplicationService`'s own KDoc). Because the whole flow is now one transaction, a rollback on the Assignment side also rolls back the Proposal's write — there is no scenario in which an event fires for a change that was later undone.
- **Задача 4 (тесты).** Added a new PostgreSQL-backed test class proving atomicity for real (see below); updated one existing in-memory test's comment to state precisely what it does and does not prove now that a real shared transaction exists. No test was changed to make an unrelated point pass.

## Изменённые файлы

| File | Change |
|---|---|
| `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` | Added `handleWithinCallerTransaction(command)` — same body as the existing self-fetching `handle(command)`, minus its own `transactionRunner.run { }` wrapper, for the orchestrator to call from inside its own transaction. `handle(command)` now delegates to it, unchanged in behavior. The two-argument `handle(command, existingAssignments)` overload is untouched. |
| `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt` | Added `acceptProposalWithinCallerTransaction(proposal, command)` — same body as `acceptProposal(proposal, command)`, minus its own transaction wrapper. `acceptProposal(proposal, command)` now delegates to it, unchanged in behavior. `handle`, `declineProposal`, `lapseProposal` are untouched. |
| `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt` | Added a `transactionRunner: TransactionRunner = NoOpTransactionRunner` constructor parameter (Spring supplies the real `SpringTransactionRunner` bean automatically, same pattern as every other service in this module). `acceptProposal` is now wrapped in `transactionRunner.run { }`; its body calls the two `*WithinCallerTransaction` methods instead of the public, self-transacting ones. KDoc rewritten: removed the "known, disclosed limitation" and "known, disclosed consequence" sections (both described behavior this sprint replaces) and replaced them with a "Shared transaction, single Unit of Work owner (ADR-036)" section. |
| `backend/dispatch/src/test/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationServiceTest.kt` | One test's assertion is unchanged (it still reads `ProposalStatus.ACCEPTED` after a rejected Assignment creation) but its comment now explains why: this class's harness uses `NoOpTransactionRunner`, which has no rollback mechanism at all, so it cannot demonstrate ADR-036's actual guarantee — only the new PostgreSQL test can. No other test in this file changed. |
| `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/ProposalAssignmentOrchestrationTransactionTest.kt` | **New.** Three tests against real PostgreSQL and a real `SpringTransactionRunner`, mirroring the established rollback-proof pattern already used by `DriverAvailabilityProjectionTransactionTest`/`AssignmentOutboxTransactionTest`: (1) a successful accept commits Proposal ACCEPTED and the Assignment together; (2) a real `Assignment.create` invariant rejection (pre-existing Assignment for the order) rolls the Proposal back to `OPEN`, not left `ACCEPTED`; (3) a simulated transient infrastructure failure on the Assignment write (a fake `AssignmentRepository.save` that throws) also rolls the Proposal back to `OPEN` — proving the guarantee holds for infrastructure failures, not only business-rule rejections. |
| `docs/PERSISTENCE_ARCHITECTURE.md` | Extended, not superseded, per ADR-036's own Migration Impact instruction: one new bullet in Section 6 ("Intra-domain cross-aggregate transactions (ratified exception)") naming ADR-036 as the first, narrow authorized case and stating explicitly that it is not a general license; Section 8's "Intentionally undecided" bullet now carves out this one ratified exception; Section 9's traceability table gained `ADR-036` in the Section 6 and Section 8 rows. |

No production/business logic changed beyond the transaction restructuring above. No architecture changed beyond what `ADR-036` itself already authorized. No files deleted.

## Как реализован ADR-036

`ProposalAssignmentOrchestrationService.acceptProposal` is now:

```kotlin
fun acceptProposal(command: AcceptProposalCommand): ProposalAcceptanceOutcome = transactionRunner.run {
    val proposal = proposalRepository.findById(command.proposalId)
        ?: throw ProposalNotFoundException(command.proposalId)

    proposalApplicationService.acceptProposalWithinCallerTransaction(proposal, command)

    val assignmentCreated = dispatchAssignmentApplicationService.handleWithinCallerTransaction(
        AssignOrderCommand(proposal.order, proposal.driver)
    )

    ProposalAcceptanceOutcome(proposal, assignmentCreated)
}
```

This is Option A exactly as `ADR-036`'s own Decision describes it: the orchestrator opens one transaction; `ProposalApplicationService` and `DispatchAssignmentApplicationService`'s relevant methods no longer independently open a transaction for this specific flow. Both services keep their own public, self-transacting methods (`acceptProposal`, `handle`) completely unchanged for every other caller (`ProposalController.declineProposal`/`lapseProposal`, `AssignmentController.assignOrder`, direct test usage) — only the orchestrator calls the new `*WithinCallerTransaction` variants. This was the deliberate design choice: rather than relying on Spring's implicit `PROPAGATION_REQUIRED` join behavior (confirmed possible in a prior technical discussion this sprint), the orchestrator explicitly owns the transaction and the participating methods explicitly do not open a second one — nothing here depends on propagation semantics holding correctly.

In production, Spring's constructor injection supplies the same single `SpringTransactionRunner` bean to all three services (there is exactly one `TransactionRunner` bean in the application context); the orchestrator's own `transactionRunner.run { }` is therefore the only physical transaction opened for this flow. No `@Configuration` change was needed.

## Результаты тестов

Full `:dispatch:test` run (real PostgreSQL, JDK 21.0.4, Gradle 8.10.2): **191 tests, 6 failed** (188 baseline + 3 new tests from `ProposalAssignmentOrchestrationTransactionTest` = 191). All three new tests pass. All 8 tests in the existing `ProposalAssignmentOrchestrationServiceTest` pass unchanged.

**All 6 failures are pre-existing/environmental, none caused by this sprint's change** — each verified by direct evidence, not assumed:

1. **4× `ProposalControllerPostgreSQLIntegrationTest`** (`creating a proposal...`, `accepting a proposal...persists ACCEPTED`, `a proposal resolved...frees its order`, `an order with a pre-existing Assignment...rejects acceptance`) — this test class uses fixed, non-randomized order ids (`postgres-vertical-order-1`, `-3`, `-3b`, `-3c`, `-3d`, `-4`, `-5`, `-6`). Direct SQL against `pios_dispatch` confirms accumulated fixture rows from many prior runs across this long session — e.g. `postgres-vertical-order-3d` has 6 accumulated `assignments` rows, matching the exact `expected: <1> but was: <6>` failure. This is the same category Sprint 1 already found and disclosed (Sprint 1 Report, Found Problem #5), now further accumulated by this session's own repeated test runs — not a regression.
2. **1× `PostgreSQLAssignmentLifecycleTest`** — same category: fixed order id `postgres-lifecycle-order-1` already has a stale Assignment from a prior run, so `Assignment.create`'s own invariant correctly throws `IllegalStateException`. The stack trace runs through this sprint's new `handleWithinCallerTransaction` method, but the check and its outcome are identical to what the pre-Sprint-2 code would have done against the same stale row — confirmed by code comparison, not merely assumed.
3. **1× `AssignmentOutboxTransactionTest`** — a *new, more precise* diagnosis of the same general category, found by direct process inspection rather than guessed: `Get-CimInstance Win32_Process` shows a live, already-running `dispatch-0.1.0-SNAPSHOT.jar` process (PID 30732, started 2026-07-22, unrelated to this session's work — most likely left over from earlier manual verification work) still running against the same `pios_dispatch` database. Its own `OutboxRelayScheduler` polls and marks outbox rows published in real time — direct SQL confirms all 113 rows currently in `dispatch_outbox` already have `published_at` set, many within ~100–200ms of `created_at`. The test's own `findUnpublished()` read races this live scheduler and intermittently loses — reproduced with a different test method failing on a second, isolated run of the same class, consistent with a timing race rather than a fixed defect. This is an environmental hazard (a stray long-running process sharing the test database), not a code defect; not something this sprint's scope authorized touching, and the process was left running rather than stopped unilaterally.

No test was modified to make these pass, per this sprint's own instruction to fix only tests directly related to ADR-036.

## Проверка соответствия ADR-036

Checked point by point against the Accepted ADR text:

- **Decision** ("single shared database transaction, owned by `ProposalAssignmentOrchestrationService`, spanning the Proposal write and the Assignment write"): implemented exactly as described, see code above.
- **Constraints**: `Proposal` and `Assignment` remain two separate aggregate roots, unmodified — confirmed, no domain file touched. No new domain concept, entity, or persisted state — confirmed. Both aggregates remain owned exclusively by Dispatch, in `pios_dispatch` — unchanged.
- **Operational Consequences**: no network/external I/O inside the shared transaction — confirmed, no such call exists inside `acceptProposal`. No long-held locks — the transaction now covers exactly the same database work as the two previous transactions combined, nothing new added. External calls happen after commit via outbox — unchanged, `OutboxRelay` is still the only publisher. Assignment failure rolls back Proposal too — proven directly by `ProposalAssignmentOrchestrationTransactionTest`'s second and third tests.
- **Migration Impact**: "impact confined to" the three named application-layer files, plus a small extension to `PERSISTENCE_ARCHITECTURE.md` — both done, nothing else touched.

No discrepancy found between the implementation and the ADR text.

## Ограничения и известные проблемы

1. **Concurrent-invocation races remain out of scope**, exactly as ADR-036's own Risks section discloses — this sprint closed the cross-aggregate atomicity gap, not every remaining concurrency question. Not touched.
2. **Pre-existing PostgreSQL test-database pollution** (Found Problem #5 from Sprint 1) is worse than when Sprint 1 disclosed it, purely from repeated runs across this long working session — still not fixed, still out of this sprint's scope.
3. **A stray, already-running `dispatch` application process** (PID 30732) was discovered racing the test database's outbox table — newly diagnosed this sprint, not stopped, disclosed here for a decision rather than acted on unilaterally.
4. **`ADR-036`'s own "Silent broadening risk"** is now mitigated: `PERSISTENCE_ARCHITECTURE.md` Section 6/8 explicitly names this exception and its narrow scope, so a future reader of that document alone will find it.

## Рекомендации для Sprint 3

1. Before running the full `:dispatch:test` suite again, stop any stray long-running local service processes (`order-management`, `driver-management`, `passenger-experience`, `dispatch` jars) — several were found still running from a much earlier session and are actively polluting shared test state.
2. Randomize the remaining fixed-string order ids in `ProposalControllerPostgreSQLIntegrationTest` and `PostgreSQLAssignmentLifecycleTest` (the same fix already implicitly proven to work by every test in this sprint's own new file and in `AssignmentOutboxTransactionTest`'s randomized tests), or adopt Sprint 1's own recommendation of a dedicated cross-test-interference investigation — this is the same category, not a new one.
3. Decide the `.claude/CLAUDE.md` merge proposal (Sprint 1, Found Problem #3) — still unresolved, still needs a governance owner.
4. Fix `docs/README.md`'s ADR-027–034 indexing gap (Sprint 1, Found Problem #4) — still unresolved, still small and mechanical.
5. If a future product decision ever needs true protection against *concurrent* Accept Proposal invocations for the same order (not addressed by this ADR or this sprint), that is a new, separate architectural question requiring its own ADR — not an extension of ADR-036.
