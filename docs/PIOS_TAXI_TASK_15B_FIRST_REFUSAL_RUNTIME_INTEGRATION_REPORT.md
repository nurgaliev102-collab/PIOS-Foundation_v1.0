# Task 15B — First Refusal Runtime Integration: STOP Report

**Date:** 2026-09-03. **Outcome: STOP triggered during Phase 0 (pre-implementation verification), condition #1.** No implementation code was written. This report pinpoints the exact missing piece and proposes the minimal fix for the Architect/Product Owner to authorize — deliberately narrower and more actionable than Task 15's own prior STOP report, since Task 15B's product decision already resolved the ambiguity that report raised.

---

## 1. Executive Summary

Task 15B resolves Task 15's own open question: First Refusal is automatic, event-driven from `OrderSubmitted`, and must never override an explicit passenger driver choice. This is a clear, implementable decision. Phase 0 verification, however, found — confirmed three independent ways in the actual source, not inferred — that **`OrderSubmitted`'s real, currently-published payload carries only `{orderId}`, no `passengerReference`.** Dispatch cannot determine "does this order's passenger have a primary driver" from the event it is being asked to consume. This is exactly STOP condition #1, which the task's own text pre-authorizes reporting rather than improvising around.

The fix is small and well-precedented — not a redesign: `Order.origin: OrderOrigin` already carries the passenger reference, persisted on `Order` itself; it is simply never included in `OrderSubmitted`'s outbox envelope today. Adding one field (`payload.passengerReference`) mirrors the exact shape `DriverAvailabilityChanged` already uses (multiple named fields under `payload`) and requires no new event, no rename, no new cross-module contract beyond what Order Management → Dispatch (ADR-053) already establishes as a pattern. Two further questions Task 15B required to be proven before implementation — DB-level duplicate-Proposal protection, and safe non-override detection — were investigated and are resolved below (Sections 9–10), ready to implement the moment the payload question is authorized.

## 2. Actual Pre-Implementation Call Graph

Re-confirmed, unchanged since Task 15's own report (re-verified fresh, not trusted blindly): both real Proposal-creation paths (`RideRequest.tsx`'s direct-propose/Circle-of-Trust flow, `Coordinator.tsx`'s manual assignment) converge on `ProposalApplicationService.handle`, both always supplying an explicit `driverId`. `ProposeDriverCommand` still carries no passenger identity. `FirstRefusalApplicationService` (Task 14) is still called from no production code (`grep -rn "FirstRefusalApplicationService" backend/ --include=*.kt`, excluding tests: only its own file and `PrimaryDriverRepository`'s KDoc reference it).

## 3. Existing OrderSubmitted Producer

`Order.submit(...)` (`backend/order-management/.../domain/Order.kt`) → `OrderLifecycleApplicationService.submitOrder` (`.../application/OrderLifecycleApplicationService.kt:62-74`) — persists `Order` and an outbox record in one transaction (ADR-032), unchanged, real, and currently the only producer of this event.

## 4. Existing OrderSubmitted Payload — the Blocker, With Full Evidence

Confirmed by reading all three layers directly, in order:

1. **Domain event** (`backend/order-management/.../domain/OrderSubmitted.kt`): `data class OrderSubmitted(val orderId: OrderId, val occurredAt: Instant = Instant.now())` — two fields, no passenger reference.
2. **Outbox record builder** (`OrderLifecycleApplicationService.kt:130-135`): `outboxRecordFor(event: OrderSubmitted)` calls `envelopeFor("OrderSubmitted", event.orderId.value, event.occurredAt.toString())` — passes only `orderId`, nothing from `Order` itself (no access to `command.origin`/`submitted.order.origin` at this call site at all).
3. **Envelope serializer** (`OrderLifecycleApplicationService.kt:164-173`): `envelopeFor(eventType, orderId, occurredAt)` builds `payload = mapOf("orderId" to orderId)` — **exactly one payload field, confirmed at the point of actual JSON serialization**, not merely inferred from the domain type.

**The information does exist, just not on this event**: `Order.origin: OrderOrigin` (`backend/order-management/.../domain/OrderOrigin.kt`) is a plain string reference to "the participant whose intention originates a given order" — this *is* the passenger reference, already persisted on `Order` itself (`Order.kt`), already accepted by `Order.submit(...)` as its own first parameter. It is simply never carried onto `OrderSubmitted`'s own payload.

**Per this task's own explicit instruction** ("If it cannot [be established through existing persisted domain state], report the exact missing information and stop rather than silently changing the API/event contract"): the distinction *can* be established through existing persisted domain state (`Order.origin`), but **only by changing `OrderSubmitted`'s own payload to actually carry it** — the state exists, but is not currently transmitted across the module boundary Dispatch would need to consume it through. This is reported here rather than changed unilaterally, exactly as instructed.

## 5. Existing FirstRefusalApplicationService

Unchanged since Task 14, re-confirmed by reading the file fresh: `attempt(order: OrderReference, passengerReference: PassengerReference, isTest: Boolean = false): FirstRefusalOutcome`. Already reuses `ProposalApplicationService.handle`'s own existing availability gate and Proposal invariant (via a caught `IllegalStateException` → `AlreadyAttempted`), already fully tested (Task 14: 401/401 Dispatch, 75/75 Passenger Experience at time of that task's own completion). Requires exactly the two inputs `OrderSubmitted` today cannot supply: `OrderReference` (available) and `PassengerReference` (not available — Section 4).

## 6. PrimaryDriverRecord Flow

Unchanged since Task 14, re-confirmed present and functional: Passenger Experience publishes `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` (both now live, per Task 14's own real-broker integration tests) → Dispatch's `PrimaryConnectionEventListener` → `PrimaryDriverProjectionApplicationService` → `PostgreSQLPrimaryDriverRepository` (`primary_driver_records`). No synchronous Passenger Experience lookup exists or is needed for this half of the flow — this part of the architecture is complete and requires no further work.

## 7. Runtime Integration Point

Confirmed unchanged: `ProposalApplicationService.handle`, reached (for the automatic path) via a new Dispatch-side consumer of `OrderSubmitted`, calling straight into `FirstRefusalApplicationService.attempt` — not a new creation path, not a duplicate of `Proposal` persistence logic. This remains correct and does not change based on this report's own finding; only the *information available at that point* is blocked (Section 4).

## 8. Explicit-Driver Detection — Resolved, No Payload Change Needed

This question (distinct from Section 4's) **is** resolvable through existing persisted domain state, with no new signal required: an explicit-driver Proposal (from either real path) and an automatic First-Refusal attempt both ultimately call the same `Proposal.propose(...)`, gated by the existing Root Invariant ("an order may have at most one open proposal at any time," `Proposal.kt`). Whichever request reaches the transaction first wins; the other's own `FirstRefusalApplicationService.attempt` call (or, symmetrically, the explicit caller's own `POST /v1/proposals`) receives the aggregate's own `IllegalStateException`. `FirstRefusalApplicationService` already turns this into `AlreadyAttempted`, a clean no-op — this was proven, tested, and shipped in Task 14 without any changes needed here.

**What this does *not* fully resolve, disclosed rather than glossed over:** this is a *race*, not a proof. In the common case (`RideRequest.tsx`'s direct-propose flow), the explicit `POST /v1/proposals` call fires synchronously, milliseconds after `POST /v1/orders` returns; the automatic path only runs after Order Management's own outbox relay polls (`fixed-delay-ms`, ~0–2s default) *and* RabbitMQ delivers *and* Dispatch's consumer processes it — the explicit path will, in practice, almost always win this race and the automatic path will almost always correctly no-op. This is a timing *tendency*, not a guarantee, and is disclosed as such rather than relied upon as a proof — in the rare case the automatic path wins, the passenger's own explicit choice would receive a 409 from an already-existing Proposal for their primary driver instead. Whether this residual, low-probability case is acceptable, or whether it needs an explicit design response (e.g., a short, separately-authorized grace delay before the automatic path acts — itself a new timeout this task's own Flow D explicitly warns against inventing without justification), is a decision for the Architect once Section 4 is resolved, not decided unilaterally here.

## 9. Idempotency Mechanism — Investigated, Resolved

**RabbitMQ redelivery / consumer restart**: does **not** require a new idempotency ledger table, contrary to what the Task 14 precedent (`PrimaryDriverProjectionApplicationService`'s own `processed_events` ledger) might suggest is always needed. Unlike a projection *upsert* (which needs an explicit ledger to distinguish "reapply" from "reject"), Proposal *creation* is already naturally idempotent per-order through the Root Invariant itself: calling `FirstRefusalApplicationService.attempt` twice for the same order — whether from genuine redelivery or a restarted consumer reprocessing an unacknowledged message — simply produces `Proposed` once and `AlreadyAttempted` on every subsequent call, with no duplicate row and no corruption, *provided* Section 10's own database-level gap is closed first.

## 10. Concurrency Protection — Investigated, a Real Gap Found, Fix Identified

**Investigated the actual schema** (`backend/dispatch/.../db/migration/dispatch/V4__proposals.sql`): `CREATE TABLE proposals (id TEXT PRIMARY KEY, order_reference TEXT NOT NULL, driver_reference TEXT NOT NULL, status TEXT NOT NULL)` — **no unique constraint, partial or otherwise, on `order_reference`.** The Root Invariant is enforced **only** by `Proposal.propose`'s own in-memory check against `existingProposals` (a `SELECT` computed before the `INSERT`, with no locking between the two). **Two genuinely concurrent calls to `ProposalApplicationService.handle` for the same order — e.g., two RabbitMQ consumer threads processing a redelivered `OrderSubmitted`, or the automatic path racing the explicit path at the database level rather than merely at the network level — can both pass the check and both insert, producing two `OPEN` proposals for the same order.** This is a real, pre-existing gap (not introduced by Task 14 or this report), silent until now because no two real callers have ever raced each other before.

**Fix identified, not yet applied**: a partial unique index, `CREATE UNIQUE INDEX proposals_one_open_per_order ON proposals (order_reference) WHERE status = 'OPEN'` — mirrors the exact reasoning `ADR-054` Part 2 already used for `primary_connections`' own `PRIMARY KEY (passenger_reference)` ("the single-primary invariant becomes a [key]... expresses the invariant more directly... carries a concrete PostgreSQL hazard [without it]"). This is additive (no existing row affected — no two `OPEN` rows can already exist for the same order in any populated database, since the invariant has held by application-level luck so far), requires no data migration, and directly satisfies this task's own instruction: *"If a new constraint is genuinely necessary, document why before adding it."* **Not applied in this report** — held pending Section 4's own resolution, since implementing this constraint alone, without the payload fix, would not make the runtime integration functional.

## 11. Fallback Behavior

Unchanged since Task 13/14/15's own findings, re-confirmed: no automated fallback exists anywhere in this codebase — a human (Coordinator) manually creates the next Proposal via the unmodified `POST /v1/proposals`. This remains the correct, unmodified fallback target; nothing about this report's finding requires touching it.

## 12. Proposal Lifecycle

Unchanged. `ProposalStatus` (`OPEN`/`ACCEPTED`/`DECLINED`/`LAPSED`/`WITHDRAWN`), `ProposalLapseScheduler`/`ProposalLapseApplicationService` (default 5-minute timeout, `pios.proposal.lapse.timeout-minutes`), and every existing transition method — none require any change for this integration, confirmed by inspection, consistent with Task 14's own already-proven design.

## 13. RabbitMQ Topology Used

None created — this report proposes no infrastructure change of its own. For the record, once Section 4 is resolved: the target topology would extend the *already-existing* Order Management → Dispatch relationship (ADR-053) with a dedicated new queue bound to the `order.submitted` routing key on the *already-declared* `order-management.events` exchange — mirroring `RabbitMQOrderManagementTopologyConfiguration`'s own established, ADR-053-Part-4-mandated pattern of one dedicated queue per consumed event type (never a second binding sharing an existing queue whose listener hard-requires one `eventType`). Not built in this report.

## 14. PostgreSQL Test Database Used

None connected to — no code was written, so no test was run. For the record: any future implementation would use `pios_dispatch_test` and `pios_order_management_test` exclusively, per `docs/TEST_DATABASE_ISOLATION.md`.

## 15. Tests Added

None.

## 16. Exact Test Commands

None run.

## 17. Exact Test Results

Not applicable — no test was executed. Task 14's own last-verified results (401/401 Dispatch, 75/75 Passenger Experience) remain the last confirmed state of the test suites; this report did not re-run them, since nothing in the codebase changed.

## 18. Production Safety Verification

Trivially satisfied: no command in this task connected to any database or RabbitMQ vhost, production or test — verification and source-reading only.

## 19. Files Modified

None.

## 20. Files Created

Only this report: `docs/PIOS_TAXI_TASK_15B_FIRST_REFUSAL_RUNTIME_INTEGRATION_REPORT.md`.

## 21. Files Explicitly NOT Modified

`OrderSubmitted.kt`, `OrderLifecycleApplicationService.kt` (Order Management) — the payload change identified in Section 4 was deliberately **not** made; this report recommends it but does not perform it, per the task's own explicit instruction to stop and report rather than silently change an event contract. `proposals` table / `V4__proposals.sql` — the constraint identified in Section 10 was deliberately **not** added, held pending Section 4. `FirstRefusalApplicationService.kt`, `PrimaryDriverRepository.kt`, `ProposalApplicationService.kt`, `ProposalController.kt` — unchanged, none required changing to reach this finding. Any frontend file. Network Management, Identity. `ProposalController.acceptProposal`/`declineProposal`/`lapseProposal` — the pre-existing caller-identity gap (Task 13) — untouched, per this task's own explicit instruction not to expand into a general authentication project.

## 22. Known Remaining Risks

- **The exact residual race described in Section 8** (automatic path occasionally winning against a slow explicit call) is disclosed, not resolved — worth an explicit decision once Section 4 unblocks implementation, not an afterthought.
- **The DB-level constraint gap (Section 10) is a real, currently-live risk independent of this task** — today, nothing prevents two *sufficiently fast, concurrent, ordinary* callers (e.g., two Coordinator browser tabs, or a retried request racing an original) from creating two `OPEN` proposals for the same order right now, with zero connection to First Refusal. Worth flagging to the Architect as a standalone finding, not only as a First-Refusal prerequisite.
- **The pre-existing accept/decline/lapse authorization gap** (Task 13) remains exactly as consequential as Task 14's own report already described — unaffected, unresolved, by design, in this report too.

## 23. Security Limitations

Unchanged from Task 13/14's own findings — restated per this task's own instruction to confirm rather than silently carry forward: `ProposalController.acceptProposal`/`declineProposal`/`lapseProposal` still have no caller-identity check. This report's own finding (a payload gap on an *inbound*, consumer-side event) introduces no new security-sensitive information exposure — `OrderSubmitted`'s proposed additional field (`passengerReference`) is the same opaque identifier already carried, unencrypted, on numerous other already-ratified contracts (`PrimaryDriverRecord` itself, `Order`'s own already-public `GET /v1/orders?passengerReference=` query parameter) — not a new class of exposure.

## 24. Final Verification Checklist

- [x] Read all six authoritative documents listed in this task's own header, in full, before any other action.
- [x] Re-traced the actual call graph from source, not from any prior report's own claims.
- [x] Confirmed `OrderSubmitted`'s actual, currently-serialized payload three independent ways.
- [x] Confirmed `FirstRefusalApplicationService`/`PrimaryDriverRecord` remain exactly as Task 14 left them.
- [x] Investigated (not deferred) both mandatory concurrency questions (Sections 9–10), reaching a concrete, actionable answer for each.
- [x] Made no code, test, migration, or documentation change beyond this report.
- [x] Verified no database or RabbitMQ connection, production or test, was made.

## 25. STOP/GO Conclusion

**STOP.** Condition #1 triggered, with complete, three-times-cross-checked evidence (Section 4). **This is not a request for a new architecture decision** — the architecture (ADR-062/063, this task's own Product Decision) is settled and requires no reinterpretation. It is a request to authorize one small, additive, well-precedented payload change (`OrderSubmitted.payload.passengerReference`, sourced from `Order.origin`, mirroring `DriverAvailabilityChanged`'s own existing multi-field payload shape) — since this task's own text explicitly reserves exactly this kind of change for explicit authorization rather than silent implementation. Once authorized, the remaining two open questions (Sections 8, 10) have concrete, already-investigated answers ready to implement immediately, and the DB-level constraint (Section 10) can be added in the same pass. No further architecture work, no new ADR, and no frontend change are anticipated to be necessary beyond that one authorization.

---

**Per this task's own STOP protocol: no further implementation changes were made.** Waiting for the Architect/Product Owner's decision on the `OrderSubmitted` payload addition before proceeding.
