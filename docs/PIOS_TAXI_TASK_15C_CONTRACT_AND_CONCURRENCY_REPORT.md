# Task 15C — First Refusal Contract Completion and Concurrency Safety: Implementation Report

**Date:** 2026-09-03. **Scope:** resolve the two concrete blockers Task 15B identified — the `OrderSubmitted` payload gap and the missing database-level Proposal invariant — plus the atomic no-override guarantee this task's own Product Rule requires. **Full automatic First Refusal runtime triggering is explicitly not implemented here**, per this task's own scope.

**Status:** Complete. 409/409 Dispatch tests passing, 165/165 Order Management tests passing, 75/75 Passenger Experience tests passing (unaffected, re-verified since it depends on Order Management test-scoped). No STOP condition was triggered.

---

## 1. Executive Summary

Task 15B found two concrete blockers and one deeper concurrency question. This task closes all three: `OrderSubmitted` now carries `passengerReference` (Part 1); a genuinely atomic, non-racing "explicit driver choice always wins" mechanism was designed and built by recording intent at order-submission time — the one point in the current runtime that is causally guaranteed to precede any automatic processing of that same order (Parts 2–3, 6); the Proposal "one OPEN per order" invariant is now enforced by PostgreSQL itself, not only application code (Part 4), closing a real, pre-existing, independently-discovered gap. `FirstRefusalApplicationService` (Task 14) gained one additive parameter respecting the new intent flag; no new aggregate, no new Proposal status, no ranking, and no frontend change were introduced. `Proposal.propose`'s own root invariant, `ProposalController`'s existing endpoints (URLs, request/response shapes), and the Trip/Assignment lifecycle are all untouched.

**What remains deliberately not done, per this task's own scope**: nothing yet calls the new intent flag from a live caller (the frontend still never sets `explicitDriverIntent: true`), and no Dispatch-side consumer of `OrderSubmitted` exists to invoke `FirstRefusalApplicationService` automatically. Both are for the next task.

## 2. Task 15B Findings Re-Verified

Re-checked fresh, not trusted blindly, before writing any code: `OrderSubmitted`'s payload was confirmed still exactly `{"orderId": "..."}` (domain event, outbox builder, and envelope serializer all agreed). `Order.origin` was confirmed to already carry the passenger reference. The `proposals` table was confirmed to still carry no unique constraint on `order_reference` (`V4__proposals.sql`, unchanged since creation). The isolated `pios_dispatch_test` database was queried directly and confirmed to hold **zero** existing duplicate-OPEN-proposal rows before the new migration was written.

## 3. Actual OrderSubmitted Producer

Unchanged call path: `Order.submit(...)` (`backend/order-management/.../domain/Order.kt`) → `OrderLifecycleApplicationService.submitOrder` (`.../application/OrderLifecycleApplicationService.kt`), persisting `Order` and its outbox record in one transaction. Only the *payload this producer builds* changed (Section 4) — no new producer, no renamed event, no changed routing key (`order.submitted`, unchanged).

## 4. Final OrderSubmitted Payload

```json
{
  "eventId": "...",
  "eventType": "OrderSubmitted",
  "eventVersion": 1,
  "occurredAt": "...",
  "payload": {
    "orderId": "...",
    "passengerReference": "...",
    "explicitDriverIntent": false
  }
}
```

`passengerReference` (Part 1) and `explicitDriverIntent` (Parts 2/3/6, an addition beyond Part 1's own stated target — see Section 6 for why) were added. Verified by a real test (`OrderSubmittedEnvelopeTest`, new assertion) that the payload's own field set is *exactly* `{orderId, passengerReference, explicitDriverIntent}` — no unnecessary passenger data (no name, no phone, no profile). `eventVersion` was deliberately **not** bumped: this is an additive change to an event no consumer yet exists for in production (Task 13's own confirmed finding, re-verified: nothing consumes `OrderSubmitted` today), so there is no existing consumer whose contract this could break — consistent with ADR-030's own versioning discipline governing *breaking* changes, not additive ones.

## 5. Passenger Reference Source

`event.origin.reference` — `OrderSubmitted.origin: OrderOrigin`, the exact same value already persisted on `Order.origin` (`Order.kt`), now also carried on the domain event itself rather than only on the aggregate. No new type, no new module dependency — `OrderOrigin` is already Order Management's own, already-existing, already-validated type.

## 6. Explicit-Driver Intent Source

**Investigated per Part 2's own instruction, not chosen by intuition.** Traced the real call graph (unchanged since Task 15/15B, re-confirmed): both real Proposal-creation paths (`RideRequest.tsx`'s direct-propose flow, `Coordinator.tsx`'s manual assignment) supply an explicit `driverId` only at *Proposal*-creation time — there is no existing field, anywhere, recording "this order's driver is already spoken for" at *order*-submission time. This is a genuine gap, not an oversight to route around.

**The smallest mechanism providing a genuine atomic guarantee** (Part 2's own requirement) is a new field on `Order` itself — `explicitDriverIntent: Boolean`, defaulting to `false` — because order submission is the **only** point in the current runtime causally guaranteed to precede any processing that could ever be triggered by that same order's own `OrderSubmitted` event. A flag set at *any later* point (e.g., at the moment the frontend calls `POST /v1/proposals`) would itself race the very consumer it exists to gate — the same problem restated, not solved. This is why Part 1's own illustrated payload (`{orderId, passengerReference}`) does not show this third field: Part 1 scopes the passenger-reference addition specifically; the explicit-intent field is a **separate, additional** payload addition this task makes under Parts 2/3/6's own distinct instructions, evaluated against Part 6's own explicit criteria (Section 8) rather than assumed to be forbidden by Part 1's own illustration.

**Considered and rejected**: a Dispatch-local "reservation" table checked before Proposal creation — does not solve the actual problem (a reservation race still has no way to prefer the explicit side deterministically; it only achieves "exactly one wins," the same guarantee the Part 4 unique index already provides as a backstop, Section 9). A synchronous Dispatch → Order Management query at processing time — rejected for the same coupling reasons ADR-062 itself already rejected an equivalent design (Option A in that ADR's own Decision table) for a structurally identical problem.

## 7. Persistence Mechanism

`Order.explicitDriverIntent: Boolean`, a new, non-nullable, defaulted (`false`) constructor parameter (`Order.kt`), set once by `Order.submit(...)` and never changed afterward (no method exists to change it — verified by a dedicated test: completing an order leaves the flag unchanged). Persisted as `orders.explicit_driver_intent BOOLEAN NOT NULL DEFAULT FALSE` (`V11__add_order_explicit_driver_intent.sql`), read/written by `PostgreSQLOrderRepository` following that class's own established reflective-private-constructor pattern (now a tenth constructor parameter). `SubmitOrderCommand`, `OrderSubmissionRequestHandler.handle`, `SubmitOrderRequest`, and `OrderSubmissionController` were each extended with the identical optional, defaulted, appended-last field, following this codebase's own already-established convention for every prior additive field on this exact contract (`destination`, `passengerName`, `pickupAddress`, `requestedPickupAt`, `isTest`).

## 8. Atomicity Mechanism

The guarantee is structural, not procedural: because `explicitDriverIntent` is a constructor-time fact of `Order` itself, and `OrderSubmitted` is only ever published *after* `Order.submit` has already returned a fully-formed `Order` (the same transaction that will publish the event first has to create the aggregate carrying the flag), **there is no possible interleaving in which a future automatic consumer of `OrderSubmitted` could observe a stale or "not yet set" value** — the flag is either correctly `true` on the one event this order will ever produce, or correctly `false`. `FirstRefusalApplicationService.attempt`'s own new `explicitDriverIntentDeclared` parameter, when `true`, makes **no read** of `PrimaryDriverRepository` and **no call** to `ProposalApplicationService` at all (verified by a dedicated test using a `PrimaryDriverRepository` double that throws `AssertionError` if ever invoked) — the strongest form of "does not compete," not merely "loses gracefully."

**Per Part 6's own required evaluation**: this changes an existing public API (`POST /v1/orders`'s own request body). The change is additive (`explicitDriverIntent: Boolean = false`, appended last, matching this exact endpoint's own five prior additive-field precedents) — every existing caller (verified: full Order Management, Passenger Experience, and Dispatch suites, Section 16) continues to compile and behave identically. No new ADR is required: this follows the same "optional, defaulting, backward-compatible field" precedent this specific endpoint has already used five times without one.

## 9. Proposal Invariant

**Investigated per Part 4's own required checklist before writing the migration:**
- **Migration history**: `V4__proposals.sql` (the table's own creation) never declared a uniqueness constraint on `order_reference`. No later migration (`V6`–`V12`) added one either.
- **Existing duplicate OPEN proposals**: queried directly against `pios_dispatch_test` — none found (Section 2).
- **All Proposal status transitions**: `propose`/`accept`/`decline`/`lapse`/`withdraw` (`Proposal.kt`) each independently require `status == OPEN` as their own precondition — confirming the invariant ("at most one `OPEN` proposal per order") is a real, intended, universally-applicable rule, not conditional on some Proposal "type" this aggregate does not have.
- **Transaction boundaries**: `ProposalApplicationService.handle` reads `existingProposals` via a plain `SELECT` (no locking) inside a transaction, then `INSERT`s — confirming the gap is real: two genuinely concurrent transactions can both read "no existing proposal" before either commits.

**Conclusion**: the invariant is valid for every Proposal, unconditionally — a database-level constraint is the correct fix, not a narrower or conditional one.

## 10. Database Constraint/Index

```sql
CREATE UNIQUE INDEX proposals_one_open_per_order
    ON proposals (order_reference)
    WHERE status = 'OPEN';
```

A **partial** index, deliberately — restricting only `OPEN` rows preserves the ordinary, already-established fallback/re-proposal flow (a new proposal for an order whose previous one already resolved), verified by a dedicated test (`Test 8`) proving a proposal can still be created for an order whose earlier proposal was `DECLINED`. Does not interact with `PostgreSQLProposalRepository.save`'s own `ON CONFLICT (id) DO UPDATE` clause (a different conflict target entirely) — confirmed by every existing Proposal test passing unmodified (Section 16).

`ProposalController.createProposal` and `FirstRefusalApplicationService.attempt` were each additionally updated to catch `org.springframework.dao.DataIntegrityViolationException` alongside the pre-existing `IllegalStateException`, mapping it to the identical outcome (`FirstRefusalOutcome.AlreadyAttempted`, HTTP 409) — without this, a genuinely concurrent request that reaches the new database constraint (rather than the pre-existing in-memory check) would have surfaced as an unhandled 500/uncaught exception instead of the endpoint's own already-documented conflict behavior. This is not a new observable behavior for either caller — it keeps each one's already-stated contract accurate now that the constraint it always implied is real.

## 11. Migration Number

`backend/order-management/.../db/migration/ordermanagement/V11__add_order_explicit_driver_intent.sql` (highest existing was `V10`). `backend/dispatch/.../db/migration/dispatch/V14__proposals_one_open_per_order.sql` (highest existing was `V13`, from Task 14). Both confirmed by directly listing each module's migration directory before creating the new file.

## 12. Concurrent Transaction Behavior

Proven with real concurrent threads against real PostgreSQL (`ExecutorService` + `CountDownLatch`, not sequential calls, not mocks), in `ProposalConcurrencyTest.kt`:
- **Two concurrent automatic attempts, same order** (`Test 6`): exactly one produces `Proposed`, the other `AlreadyAttempted` — never both, never neither, never an uncaught exception.
- **Concurrent explicit vs. automatic, no flag declared** (`Test 5`, representing today's actual unprotected scenario): exactly one proposal survives, for whichever side won — disclosed honestly as non-deterministic in *which* side wins, but deterministic in that exactly one does and the loser fails cleanly.
- **Concurrent explicit vs. automatic, flag declared** (`Test 5`, deterministic counterpart): the automatic side never competes at all — the explicit driver's proposal is the only one, unconditionally, proven under genuine concurrency, not merely asserted.
- **Post-resolution re-proposal** (`Test 8`): a `DECLINED` proposal does not block a fresh one for the same order.

No test relies on `sleep`, an arbitrary delay, a retry loop, or an in-memory-only lock — every guarantee is either the new PostgreSQL constraint or the pre-existing in-memory check, both already transaction-scoped.

## 13. RabbitMQ Test Topology

None created or modified by this task — no Dispatch-side consumer of `OrderSubmitted` was built (out of scope, Section 1). Every test in this task is either a pure in-memory unit test or a real-PostgreSQL integration test; none connects to RabbitMQ.

## 14. PostgreSQL Test Database

`pios_dispatch_test` and `pios_order_management_test` exclusively — verified present and distinctly named from their production counterparts (`psql -h 127.0.0.1 -U postgres -lqt`) before running any test. No command in this task connected to `pios_dispatch` or `pios_order_management`.

## 15. Tests Added

**Order Management** (11 new/extended tests): `OrderTest.kt` (+4: default-false, true-carries-through, immutability-after-complete, event-origin-matches-order-origin), `OrderSubmittedEnvelopeTest.kt` (+2: payload field-set exactness, explicit-intent-true carried to envelope), `PostgreSQLOrderRepositoryTest.kt` (+3: round-trip true, round-trip false, fresh-instance persistence proof).

**Dispatch** (8 new tests): `FirstRefusalApplicationServiceTest.kt` (+3: `Test 3` ×2 — outcome and no-read-at-all proof, `Test 4`), `ProposalConcurrencyTest.kt` (+5, new file: `Test 6`, `Test 5` ×2, `Test 8` ×2).

## 16. Exact Test Commands

```
./gradlew.bat :order-management:test --tests "com.pios.ordermanagement.domain.OrderTest" --tests "com.pios.ordermanagement.persistence.OrderSubmittedEnvelopeTest" --tests "com.pios.ordermanagement.persistence.PostgreSQLOrderRepositoryTest"
./gradlew.bat :dispatch:test --tests "com.pios.dispatch.application.FirstRefusalApplicationServiceTest" --tests "com.pios.dispatch.persistence.ProposalConcurrencyTest"
./gradlew.bat :dispatch:test
./gradlew.bat :order-management:test
./gradlew.bat :passenger-experience:test
```

## 17. Exact Test Results

Focused runs: all green on the described commands. **Complete Dispatch suite**: `BUILD SUCCESSFUL`, aggregated from XML reports `tests=409 skipped=0 failures=0 errors=0` (401 from Task 14's own baseline + 8 new). **Complete Order Management suite**: `BUILD SUCCESSFUL`, `tests=165 skipped=0 failures=0 errors=0`. **Complete Passenger Experience suite** (re-run since it depends on Order Management test-scoped): `BUILD SUCCESSFUL`, unchanged at 75/75 from Task 14.

**Pre-existing residual-data failures encountered and resolved, root-caused as unrelated to this task**: the first full-Dispatch-suite run failed 5 tests (`ProposalControllerPostgreSQLIntegrationTest` ×3, `PostgreSQLAssignmentLifecycleTest` ×2) — the exact, already-diagnosed (Tasks 11/12/14) non-randomized-literal-identifier residual-data pattern in those two pre-existing files, re-confirmed by querying `pios_dispatch_test` directly for the specific `postgres-vertical-*`/`postgres-lifecycle-*` rows before touching anything. Cleaned via `DELETE` against `pios_dispatch_test` only (trips → assignments → proposals, respecting the FK order), then reran — clean. No code or test file was modified to achieve this.

## 18. Production Safety Verification

`pios_dispatch_test`/`pios_order_management_test` confirmed distinct from `pios_dispatch`/`pios_order_management` by direct `psql -lqt` listing before any test ran. Every `DELETE` statement in this task's own residual-data cleanup (Section 17) targeted `pios_dispatch_test` explicitly, by full database name, never a variable or default connection. No RabbitMQ connection was made by any test or command in this task. No `ALTER`/`CREATE INDEX`/`CREATE TABLE` was run directly against any database by hand — both new schema changes were applied exclusively through Flyway migration files, executed by the test suite's own already-established `PostgreSQLTestDatabase` bootstrap, never by a manual command against production.

## 19. Files Modified

**Order Management**: `domain/Order.kt`, `domain/OrderSubmitted.kt`, `application/SubmitOrderCommand.kt`, `application/OrderSubmissionRequestHandler.kt`, `application/OrderLifecycleApplicationService.kt`, `api/SubmitOrderRequest.kt`, `api/OrderSubmissionController.kt`, `persistence/PostgreSQLOrderRepository.kt`; tests: `domain/OrderTest.kt`, `persistence/OrderSubmittedEnvelopeTest.kt`, `persistence/PostgreSQLOrderRepositoryTest.kt`.

**Dispatch**: `application/FirstRefusalApplicationService.kt` (Task 14's own file, extended), `api/ProposalController.kt`.

## 20. Files Created

**Order Management**: `db/migration/ordermanagement/V11__add_order_explicit_driver_intent.sql`.

**Dispatch**: `db/migration/dispatch/V14__proposals_one_open_per_order.sql`, `src/test/kotlin/.../persistence/ProposalConcurrencyTest.kt`.

This report: `docs/PIOS_TAXI_TASK_15C_CONTRACT_AND_CONCURRENCY_REPORT.md`.

## 21. Files Explicitly NOT Modified

Any frontend file — `explicitDriverIntent` is not sent by any current caller; the capability is additive and unwired, exactly like `FirstRefusalApplicationService` itself was left in Task 14. No Dispatch-side `OrderSubmitted` consumer/listener/topology was created — "full automatic First Refusal runtime triggering" remains explicitly out of scope. `Proposal.kt`, `ProposalStatus.kt`, `Proposal.propose`'s own invariant logic — unchanged; the new constraint enforces the existing rule, it does not add a new one. `ProposeDriverCommand.kt`, `ProposalApplicationService.handle` — unchanged. `Assignment.kt`, `Trip.kt`, and every file Tasks 11/12 established. Network Management, Identity, Driver Management. `ProposalController.acceptProposal`/`declineProposal`/`lapseProposal` — the pre-existing caller-identity gap (Task 13) remains completely untouched.

## 22. Remaining Risks

- **The mechanism this task built is not yet active for any real order**, since no caller sets `explicitDriverIntent: true` and no consumer reads `OrderSubmitted` at all yet. This is by design (Section 1), not an oversight, but worth restating plainly: today's actual runtime is exactly as it was before this task, observably.
- **Test 5's own honest disclosure** (Section 12): without the flag declared, which side wins a genuine race remains non-deterministic — this is the expected, accepted state until a future task actually wires the frontend (or another caller) to set the flag for the direct-propose case.
- **The DB constraint (Section 10) is a real, independently valuable fix** for a risk that existed before and separately from First Refusal (any two sufficiently fast concurrent callers, e.g. two Coordinator tabs) — worth flagging to the Architect as a standalone finding now resolved, not merely a First-Refusal prerequisite.
- **The pre-existing accept/decline/lapse authorization gap** (Task 13) remains exactly as consequential and exactly as unresolved as every prior report in this chain has stated.

## 23. Security Limitations

Unchanged from Task 13/14/15B: `ProposalController.acceptProposal`/`declineProposal`/`lapseProposal` still have no caller-identity check — explicitly out of this task's own scope, not touched. The new `explicitDriverIntent` field and `OrderSubmitted.payload.passengerReference` introduce no new sensitive-data exposure: both are the same class of opaque identifier already carried by numerous already-ratified contracts (`PrimaryDriverRecord`, `Order`'s own already-public query parameters).

## 24. Architecture Impact

No new ADR was required or created (Section 8's own explicit evaluation). `ADR-062`/`ADR-063` are unaffected in substance — this task implements infrastructure those ADRs already authorize, using patterns (additive REST fields, partial unique indexes, dual-purpose payload envelopes) already established elsewhere in this codebase, not new architectural patterns. No `INTERFACE_CONTRACTS.md`/`EVENT_CATALOG.md` update was made in this task — `OrderSubmitted`'s own entry there predates any consumer and remains accurate in describing what is published; a future task wiring an actual Dispatch-side consumer is the natural point to update those documents to reflect a real, live cross-module relationship, not this one, which builds only the producer-side contract and a Dispatch-side capability neither document yet needs to describe as "consumed."

## 25. Final GO/STOP Conclusion

**GO — complete, no STOP condition triggered.** Both of Task 15B's own concrete blockers are resolved: `OrderSubmitted` now carries `passengerReference`; the Proposal invariant is now database-enforced. The additional atomic no-override guarantee this task's own Product Rule required was designed, built, and proven under genuine concurrency, not merely asserted. `FirstRefusalApplicationService`, `PrimaryDriverRecord`, and the entire Task 14 foundation remain unchanged in their own core logic, only extended additively. The contract and concurrency model are now safe for a future task to build the actual automatic-triggering consumer and frontend wiring on top of, without violating explicit passenger choice, the Proposal lifecycle, PostgreSQL consistency, or any existing architectural boundary.

---

**Per this task's own scope: full automatic First Refusal runtime triggering was not implemented.** The next task's own job is to build the Dispatch-side `OrderSubmitted` consumer (reusing `RabbitMQOrderManagementTopologyConfiguration`'s own established extension pattern) and to decide where the `explicitDriverIntent` flag is actually set by a real caller.
