# ADR-077: The Dispatch Routing Obligation — a Durable `dispatch_requests` Record, a Bounded Retry, and `UNFULFILLED` as an Order Terminal State

## Status

**Accepted — documenting already-built, uncommitted code (2026-09-16).**

Retroactive, on the same terms as `ADR-075`/`ADR-076`: the code exists in the working tree, was written without the Stage 1 Architect review `.claude/CLAUDE.md` requires (*"domain model changes"*, *"bounded context boundaries may change"*, *"a new ADR may be required"* — all three apply), and is **not committed and not deployed**. This ADR records what was built. It does not re-open the design.

**Author:** Architect role. **Ratification:** a human act (`ADR-007`).

**Evidence basis: absent.** `docs/PIOS_PRODUCT_HYPOTHESES.md` ends at H12; no hypothesis was registered before this Sprint, unlike every Sprint from `ADR-042` through `ADR-074`. Registration is a Product Owner act and is not performed here.

**This ADR explicitly amends `ADR-068` Part 4 in three places.** Each is stated below rather than left as a silent disagreement (`ADR-015`). One thing `ADR-068` Part 4 says is **not** amended — and that is the most important sentence in this document, see Decision 4.

---

## Context

### The real defect this answers, recorded honestly by its own predecessor

`docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` line 35 originally recorded a live E2E anomaly as a test-script artifact:

> *«первая попытка заказа не дала proposal — гонка между API-подтверждением доступности водителя и распространением события через RabbitMQ. Retry намеренно не предусмотрен (ADR-068)… Не баг продукта — особенность тестового скрипта.»*

Line 51 of the same file retracts that:

> *"This section supersedes the earlier statement that an order without a proposal after the availability-event race is merely a test-script issue. That was a real commercial-readiness gap."*

The retraction is correct. The structure of the defect: `OrderSubmittedFirstRefusalListener` handled `OrderSubmitted` exactly once, in-line. If Dispatch's `driver_availability` projection had not yet caught up — a genuine, unavoidable window in an event-driven system (`ADR-031`, `ADR-032`) — the single attempt found no eligible driver, returned `NoAvailableDriver`, and **the order was never routed again.** The passenger saw a saved order and nothing else, forever.

`ADR-068` Part 4 explicitly ratified that outcome as acceptable (*"the order returns to unmatched exactly as today, Coordinator-visible"*). At the time, the only way to reach "unmatched" was for no driver to be available at all. It was not anticipated that the *same* terminal outcome would be produced by a projection lag of a few hundred milliseconds.

---

## Decision

### 1. Dispatch persists a **routing obligation** before attempting an offer

`dispatch_requests` (`V19__dispatch_requests.sql`) is a new Dispatch-owned table, keyed by `order_id`, holding the routing payload (`passenger_reference`, `is_test`, `explicit_driver_intent`, `requested_driver_id`, `requested_pickup_at`), a `state` in `{PENDING, OFFERED, UNFULFILLED, CANCELLED}`, a `submitted_at`, an `expires_at`, and a `next_attempt_at`. Two CHECK constraints keep the row honest: an active row must carry its full payload, and `next_attempt_at` is non-null **iff** the state is `PENDING`. A partial index `dispatch_requests_due_idx ... WHERE state = 'PENDING'` serves the sweep.

The obligation is written **before** the first attempt, inside the same transaction (`DispatchRequestApplicationService.routeSubmitted`). This is the whole point: the fact "this order must be routed" now outlives any single failed attempt.

This is a Dispatch-owned aggregate in Dispatch's own database. It holds `passenger_reference` and `requested_driver_id` as opaque strings, exactly as `PrimaryDriverRecord` and `DriverAvailabilityRecord` already do — reference, not ownership (`ADR-005`, `ADR-019`). No new cross-module read, no new synchronous call, no shared schema.

### 2. A bounded, five-second retry over a two-minute window

`DispatchRequestRetryScheduler` (`@Scheduled(fixedDelayString = "${pios.dispatch.retry.fixed-delay-ms:5000}")`) calls `retryDue()`, which claims up to 100 due rows and re-runs `attemptLocked` for each in its own transaction. `attemptLocked` takes a row lock (`findForUpdate`), and:

- if **any** proposal already exists for the order → `markOffered`, stop. *(Comment in the code, load-bearing: "never declare an order unfulfilled after any proposal exists.")*
- if `now >= expires_at` (`submitted_at + 2 minutes`) → `markUnfulfilled`, write `DispatchExhausted` to the outbox, stop.
- otherwise → attempt one offer through the **existing, unmodified** decision path, then either `markOffered` or schedule the next attempt at `now + 5s`.

The decision path per attempt is byte-for-byte the pre-existing one: named driver → `ProposalApplicationService.handle`; else if `explicitDriverIntent` → **do nothing at all** (`ADR-062`'s no-op preserved; the comment explains that a v1/v2 event that declared intent without carrying an ID must never have an unrelated driver substituted); else `FirstRefusalApplicationService.attempt` and, only on `NoPrimaryDriver`/`PrimaryDriverIneligible`, `FallbackDispatchApplicationService.attempt`. **`ADR-068`'s tier algorithm, its `updated_at ASC` tie-break, its empty Tier 2, and `ADR-069`'s test/real gate are all untouched.**

The retry re-runs *the same tier evaluation at a later moment in time*. It is **not** a descent through tiers on a negative result, and it does not try a second driver after a first one was offered.

### 3. `DispatchExhausted` → `OrderUnfulfilled`: a new Order terminal state

At expiry Dispatch emits `DispatchExhausted` v1 (`routing key dispatch.exhausted`, `reason = NO_OFFER_WITHIN_WINDOW`) through its existing outbox (`ADR-032`). `order-management`'s `DispatchExhaustedListener` validates the envelope at the transport boundary and calls `DispatchExhaustedApplicationService`, which locks the order and transitions it **only if it is still `SUBMITTED`** — a redelivery, a cancellation, or a completed ride cannot undo a terminal outcome. `Order.markUnfulfilled()` guards the same invariant in the domain, and `OrderLifecycleApplicationService.markUnfulfilled` writes the order and `OrderUnfulfilled` (`order.unfulfilled`) in one transaction.

`OrderStatus` therefore gains **`UNFULFILLED`**. This is a change to the ratified domain model:

- `docs/DOMAIN_MODEL.md` §12 says *"**Order.** Submitted, then either assigned by Dispatch and carried through acceptance, the ride itself, and completion, or cancelled at any point before completion."* There was no third terminal outcome.
- `ADR-068` Part 4 says "no driver in any tier" is `NoAvailableDriver`, *"not an error, **not a new state, not a new event**."*

**Both are amended by this ADR**, narrowly: `NoAvailableDriver` remains exactly what `ADR-068` says it is at the level of a single `attempt` call. What is new is a fact at a different level — *the routing window for this order closed with no offer ever made* — which is genuinely not expressible as an outcome of one attempt. `DOMAIN_MODEL.md` §11's invariants ("an order has exactly one current status"; "a completed order cannot return to an active state") are unchanged and still hold. `UNFULFILLED` is terminal, reachable only from `SUBMITTED`, and cannot be left.

Both `DispatchExhausted` and `OrderUnfulfilled` are recorded in `docs/EVENT_CATALOG.md` §5 and `docs/INTERFACE_CONTRACTS.md` §5, marked *"CURRENT in local code, not deployed"*.

### 4. The retry covers the **no-proposal** path only — and this is consistent with `ADR-068` Part 4, not in tension with it

`ADR-068` Part 4 ratified **"no retry on decline/lapse"**, confirmed at ratification as Q3. That position is **preserved exactly**, and the mechanism that preserves it is structural rather than a matter of intent:

> `attemptLocked` short-circuits on `proposals.findByOrder(order).isNotEmpty()` → `markOffered`.

Once *any* proposal exists, the `dispatch_requests` row leaves `PENDING` permanently. `next_attempt_at` becomes `NULL` (enforced by `dispatch_requests_due_only_when_pending`), so the row can never appear in a due sweep again. A proposal that is later declined, lapses, or is withdrawn therefore triggers **nothing** from this mechanism — the order returns to unmatched exactly as `ADR-068` Part 4 requires. The pre-existing decline/lapse fallback call sites (`ProposalController.attemptFallbackAfterDecline`, `ProposalLapseApplicationService.attemptFallbackAfterLapse`) are untouched and keep their own, separate, already-ratified `excludeDrivers` semantics.

The tracker's own honest note — *"the retry covers the no-proposal path only; a declined or lapsed proposal does not yet trigger the same recovery loop"* — is therefore **not a gap against `ADR-068`; it is `ADR-068` Part 4 being obeyed.** Extending recovery to decline/lapse would *reverse* a ratified decision and requires its own ADR and its own Product Owner answer. It is not authorized here.

### 5. Three things `ADR-068` Part 4 says, which this ADR **does** amend

Stated individually so none is glossed:

| `ADR-068` Part 4 text | Status after this ADR |
|---|---|
| *"No new idempotency ledger, no new lock, no new advisory lock."* | **Amended.** `dispatch_requests` is a durable per-order record that also dedupes `OrderSubmitted` redelivery (`insertIfAbsent`), and `findForUpdate` takes a row lock. `Proposal.propose`'s in-memory invariant and `proposals_one_open_per_order` (V14) remain the deduplication mechanisms for *proposals*; this row serializes *routing attempts*, which did not previously exist as a persisted thing. |
| *"…not a new state, not a new event."* | **Amended** — see Decision 3. |
| *"one `attempt`, one tier evaluation, no… internal retry loop"* (Part 2) and *"any cascading retry across tiers"* (Part 5, not authorized) | **Amended in the narrow sense only.** No cascading *across tiers* is introduced: each retry is a fresh, complete, identical single evaluation, and it stops the moment a proposal exists. What is new is that the evaluation may be repeated *over time* while no proposal exists at all. |

### 6. A cancellation tombstone, and a routing-state gate on proposal creation

`OrderCancelled` and `OrderSubmitted` arrive on **separate RabbitMQ queues** and can therefore be delivered out of order. `dispatch_requests` allows a `CANCELLED` row to be written before the submitted event arrives (its payload CHECK exempts `CANCELLED` from requiring a payload), and `insertIfAbsent` will not overwrite it. A cancelled order can never receive a late offer.

Correspondingly, `ProposalApplicationService.handle` now reads the routing state first:

```kotlin
check(routingState != DispatchRequestState.CANCELLED && routingState != DispatchRequestState.UNFULFILLED) {
    "Order ${command.order.orderId} is no longer eligible for a proposal"
}
```

A `null` row (no routing obligation — e.g. every order submitted before this deploys) does not block anything, so existing data behaves exactly as today.

**Disclosed consequence, not a side note:** this gate applies to the **owner/coordinator** path too. Once an order is `UNFULFILLED`, `POST /v1/proposals` returns 409 even for the coordinator. `FallbackDispatchApplicationService`'s own KDoc (lines 86–95) still says *"A coordinator can still manually propose a different driver via the existing `ProposalController.createProposal`, unchanged"* — that sentence is now false for `UNFULFILLED`/`CANCELLED` orders and must be corrected in code by the developer role.

### 7. Driver Management `V13` replays current availability

`V13__replay_current_driver_availability_for_advance_requests.sql` inserts one `DriverAvailabilityChanged` v1 outbox row per existing driver, so Dispatch's projection has a record for drivers who registered after `V11` and never toggled status. Driver registration now also emits an initial unavailable-state event, so the gap does not reopen. No driver's actual availability changes.

**Two consequences that must not be discovered in production instead of read here:**

- `SELECT ... FROM drivers` carries **no `ORDER BY`**, so the replay order is arbitrary. Every row's `updated_at` in Dispatch's projection is refreshed, which **resets `ADR-068`'s `updated_at ASC` "longest idle" fairness tie-break platform-wide, once, into an arbitrary order.** `V11` did the same and the migration comment acknowledges it. It is a one-time fairness reset, not a ranking change, but a driver who had been waiting longest loses that position.
- The replay publishes one event per driver through the live outbox relay on deploy. Dispatch's existing per-event idempotency ledger will treat each fresh `gen_random_uuid()` as new and process all of them.

## What this ADR does not authorize

Recovery after proposal **decline or lapse** (Decision 4 — that reverses `ADR-068` Part 4 and needs its own ADR and Product Owner answer); any change to the tier algorithm, its tie-break, or Tier 2's emptiness; any cascading descent across tiers; broadcast or parallel offers; any change to the lapse timeout; any re-opening of an `UNFULFILLED` order; any automatic re-submission of a new order on the passenger's behalf; any read from or dependency on `network-management`; any pricing, commission, or matching rule (`ADR-002`).

**The 2-minute window and the 5-second interval are configuration, not ratified business rules** — the same status `ADR-051`/`ADR-052` give the proposal lapse timeout. Neither number is a product decision, and neither may be cited as one.

---

## Consequences

### Positive

- The defect is closed at its cause: an availability-projection lag can no longer consume an order's only chance to be routed.
- The passenger is told the truth. A terminal `UNFULFILLED` status replaces an order that appeared saved and would never move; `RideStatus.tsx`/`RideRequest.tsx` observe it and offer a new order. This is the same honesty principle H5 already states ("не приняв ложный экран за успешный исход").
- Out-of-order cancellation is handled by construction rather than by timing luck.
- `ADR-068`'s selection algorithm, `ADR-069`'s segregation, `ADR-070`'s Option B relationship formation, `ADR-071`'s poll-derived surface, `ADR-073` and `ADR-074` are each untouched by this mechanism.

### Negative

- Dispatch gains a table, a scheduler, a row lock, and a new outbound event contract to operate and monitor. `ADR-068` Part 4 explicitly declined all of these; that decision is now reversed and the operational cost is real.
- **Failure mode that must be monitored:** if `DispatchExhausted` is lost or dead-lettered, Dispatch's row is `UNFULFILLED` while the order stays `SUBMITTED` forever — and Decision 6's gate means **no proposal can ever be created for it again, by anyone, including the coordinator.** The order is permanently stuck and looks normal. A DLQ alarm on the `DispatchExhausted` queue is not optional.
- The human coordinator loses the ability to rescue an order after two minutes (Decision 6). `ADR-051` assigns proposal-lifecycle authority to Dispatch, so this is within Dispatch's rights, but it removes a human escape hatch that existed.
- Two minutes is now, de facto, the longest a passenger will wait — a number nobody has validated against real passenger behavior and which this ADR explicitly refuses to dress up as a product decision.
- `V13` resets the fairness queue once (Decision 7).

---

## Blocking Prerequisites — resolved

1. **Flyway numbering — resolved 2026-09-16.** Renumbered to `V19__dispatch_requests.sql`. The phantom `V19` this worktree's `pios_dispatch_test` had briefly recorded (from a different, uncommitted local session sharing the same Postgres instance) was searched for across every local branch and every remote ref (`git rev-list --all` + `git ls-tree`) and found nowhere — it was never committed anywhere and cannot be recovered or collided with. Production's own `pios_dispatch` (verified read-only, item 2 below) has only ever reached `V18`, so it has never seen any `V19` either, real or phantom. `V19` is therefore genuinely free and correctly used here.
2. **Production Flyway state — verified 2026-09-16, read-only, all three databases:**
   - `pios_dispatch`: latest applied is `V18` ("driver availability is test"), `success = t`.
   - `pios_identity`: latest applied is `V3` ("create identity credentials"), `success = t` — `V4` (this work's own unique-association migration) has not touched production.
   - `pios_driver_management`: latest applied is `V12` ("add driver invited by"), `success = t`.
   No surprises in any of the three; safe to proceed to deploy planning.
3. **Production duplicate driver claims — checked 2026-09-16, before any `identity/V4` deploy.** `SELECT driver_id, count(*) FROM identities WHERE driver_id IS NOT NULL GROUP BY driver_id HAVING count(*) > 1` returned exactly two groups, both `postgres-identity-lifecycle-driver-*` — confirmed, by cross-reference against `pios_driver_management.drivers`, to correspond to **zero** real Driver rows (the same `PostgreSQLIdentityLifecycleTest`-fixture-run-against-production pattern this session already found and cleaned once for `driver_availability`). No real driver's profile access is at risk from `V4`'s NULL-out behavior on this data.
   for `pios_dispatch`, `pios_identity`, and `pios_drivermanagement`. If production's `pios_dispatch` is already past `V18` with anything other than these files, **stop.**
3. **DLQ monitoring** on the new `DispatchExhausted` queue, per the failure mode above.
4. **Hypothesis registration (Product Owner)** — no H13/H14 exists. Not performed by this ADR.
5. **One code KDoc correction** (developer role): `FallbackDispatchApplicationService` lines 86–95, whose coordinator-rescue sentence is now false.

---

## Related ADRs

- [ADR-068](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) — Part 4 amended in three places (Decision 5); its "no retry on decline/lapse" is **preserved** (Decision 4); its tier algorithm untouched.
- [ADR-069](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — unchanged; still enforced on every attempt.
- [ADR-070](ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — discovery matching and Option B relationship formation untouched.
- [ADR-062](ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) — `ExplicitDriverIntentDeclared` no-op preserved by the `explicitDriverIntent` early return.
- [ADR-051](ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) / [ADR-052](ADR-052-Proposal-Lapse-Resolution-Mechanism.md) / [ADR-053](ADR-053-Proposal-Resolution-on-Order-Cancellation.md) — lapse and cancellation handling, unchanged.
- [ADR-041](ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) — precedent for a Dispatch fact driving an Order Management status transition.
- [ADR-031](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) / [ADR-030](ADR-030-Event-Schema-Versioning-Strategy.md) — outbox, consumer and versioning machinery reused with no new technology.
- [ADR-076](ADR-076-Server-Authorized-Named-Driver-Offer.md) — the `requestedDriverId` path this mechanism routes.
- [ADR-002](ADR-002-Dispatch-Engine.md) — the window and interval are configuration, not business rules.

## References

Read for this ADR (working tree, 2026-09-16, uncommitted):

- `backend/dispatch/src/main/resources/db/migration/dispatch/V19__dispatch_requests.sql` (and the `V1…V18` sequence; no `V19`)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestRetryScheduler.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLDispatchRequestRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalListener.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/DispatchExhaustedListener.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/application/DispatchExhaustedApplicationService.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/application/OrderLifecycleApplicationService.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt`, `OrderStatus.kt`, `OrderUnfulfilled.kt`
- `backend/driver-management/src/main/resources/db/migration/drivermanagement/V13__replay_current_driver_availability_for_advance_requests.sql`
- `docs/DOMAIN_MODEL.md` §11, §12
- `docs/EVENT_CATALOG.md` §5, `docs/INTERFACE_CONTRACTS.md` §5
- `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` lines 35, 51, 61, 62
