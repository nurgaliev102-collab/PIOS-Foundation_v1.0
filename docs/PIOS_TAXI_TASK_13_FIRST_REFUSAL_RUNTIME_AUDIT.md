# Task 13 — First Refusal Runtime Audit

**Date:** 2026-09-03. **Type:** Read-only audit. **No code, test, migration, ADR status, or infrastructure was changed.** See Section 24.

---

## 1. Executive Conclusion

First Refusal can be implemented **without any new architectural decision** — ADR-062 (Accepted) already authorizes the one missing contract (Passenger Experience → Dispatch, event-fed), and `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 3–4 already ratify the product mechanism. The relationship primitive already exists and is fully built end-to-end (`Connection`/`primary_connections`, Passenger Experience, ADR-054) — **no second Primary Driver model should be created.** The response-window infrastructure already exists and is reusable as-is (`ProposalLapseScheduler`/`ProposalLapseApplicationService`, ADR-051/ADR-052). The existing `Proposal` aggregate's state vocabulary (`OPEN/ACCEPTED/DECLINED/LAPSED/WITHDRAWN`) already covers every outcome First Refusal needs — no new domain concept is required to represent it.

**What is genuinely missing and must be built:** (1) the `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` events themselves — `SetPrimaryConnectionApplicationService` currently persists with **no event publication at all** (confirmed by direct reading, Section 5); (2) Dispatch's own `PrimaryDriverRecord` projection and its consumer wiring; (3) the actual decision logic that consults the projection before creating the first Proposal for a new order; (4) a **real, structural race-condition guard** preventing normal dispatch from starting while First Refusal is pending — none exists today because nothing about First Refusal exists today; (5) a caller-identity check on Proposal accept/decline — **today, anyone who knows a `proposalId` can accept or decline it; nothing verifies the caller is the proposed driver** (Section 15, a genuine safety gap for First Refusal specifically, confirmed directly in `ProposalController.kt`).

**No STOP condition (Phase 16) was triggered.** The one real historical ambiguity — Dispatch having "no ratified role" in Personal Client Relationship information while First Refusal requires exactly that — was already identified and already resolved by ADR-062 and the accompanying Task 10B documentation reconciliation, both already Accepted/complete. This is confirmed by direct reading of `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` line 35 (superseded note) and `ADR-062` itself. One **non-blocking documentation inconsistency** was found: `ADR-037` (Network Management's own founding ADR) is still `Proposed`, not `Accepted`, despite the module being built and running — noted in Section 12, and immaterial to First Refusal since ADR-062 explicitly keeps Network Management out of scope.

## 2. Authoritative Product Decisions

- `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 3 (Primary Driver): **APPROVED** — Primary Driver = `Connection.isPrimary`, Passenger Experience-owned, "at most one primary per passenger," no new domain concept needed to represent it.
- Section 4 (First Refusal): **APPROVED**, exact mechanism transcribed verbatim in the task prompt (accept → assignment proceeds; decline/timeout/unavailable → normal matching). **No exclusive reservation** — Option B, not Option A. Response-window duration: **explicitly OPEN**, not decided by any document, not to be invented in this task either.
- Section 18 item 1: names the required new contract and points to Section 19's conflict.
- Section 19: the conflict between First Refusal's requirement and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`'s "[RATIFIED absence]" of any Dispatch role — **explicitly not resolved by that document itself**, deferred to a future ADR.
- **That future ADR is `ADR-062` (Accepted, Task 10B)** — resolves the Section 19 conflict exactly as required: a new Passenger Experience → Dispatch contract, event-fed (`PrimaryConnectionDesignated`/`PrimaryConnectionCleared` → Dispatch's own `PrimaryDriverRecord` projection), narrow, binary, non-authoritative. `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` line 35 and `ADR-034`'s own Part 2 both carry the resulting "superseded, narrowly" notes (confirmed by direct reading).
- `ADR-054` (Accepted for implementation, 2026-08-08): the actual placement/persistence decision for Circle of Trust — Passenger Experience, `connections` + `primary_connections` tables, structural single-primary invariant via `PRIMARY KEY (passenger_reference)`. **Already fully implemented** (Section 5).
- `ADR-051`/`ADR-052` (both Accepted): ownership and mechanism for Proposal timeout detection — directly reusable infrastructure (Section 8).
- `ADR-063` (Accepted, twice-corrected; Task 12 implemented it): Trip is Dispatch-internal, ride-progress owner. Confirms the `Assignment → Trip` tail of the target flow is real and live.

No contradiction was found between these documents as they stand today — every historical contradiction (Section 19's own conflict) has already been resolved by an ADR that predates this task.

## 3. Current Passenger → Order Call Graph (Direct-Propose Path — Verified Against Source)

1. **Frontend entry**: `frontend/src/pages/RideRequest/RideRequest.tsx`, route `/i/:driverCode/request` (`driverCode` from the invitation link the passenger arrived through).
2. **Order creation** (`RideRequest.tsx` ~line 633): `request('/v1/orders', { method: 'POST', baseUrl: ORDER_MANAGEMENT_BASE_URL, ... })`.
3. **Controller**: `OrderSubmissionController.submitOrder` (`backend/order-management/.../api/OrderSubmissionController.kt:52`) — no auth check present in this controller.
4. **Application service**: `OrderSubmissionRequestHandler.handle` → `OrderLifecycleApplicationService.submitOrder` (`backend/order-management/.../application/OrderLifecycleApplicationService.kt:62`).
5. **Domain aggregate**: `Order.submit(...)` (`backend/order-management/.../domain/Order.kt`).
6. **Persistence**: `PostgreSQLOrderRepository`, `pios_order_management` database.
7. **Event**: `OrderSubmitted` (`backend/order-management/.../domain/OrderSubmitted.kt`), written to Order Management's own outbox in the same transaction (ADR-032).
8. **Publisher**: Order Management's own `OutboxRelay`/`RabbitMQEventPublisher`.
9. **Current consumers**: **none.** `EVENT_CATALOG.md` line 36 lists `OrderSubmitted`; Section 9 (cross-domain consumer list) does not name it — confirmed by direct grep of `EVENT_CATALOG.md`. No module in this repository consumes `OrderSubmitted` today.
10. **Coordinator involvement in this path**: **none.** The frontend proceeds directly to step 11 without any human action.
11. **Dispatch involvement**: immediately after order creation succeeds, the same `RideRequest.tsx` function (~line 656–660) calls `POST /v1/proposals` on Dispatch directly, with `{orderId, driverId: driverCode}` — `driverCode` is the invitation-link driver, **not** Circle of Trust's `isPrimary` fact. This is a second, sequential, client-orchestrated call — not a backend reaction to `OrderSubmitted`.

**Verified again, per the task's own instruction**: this bypasses the Coordinator exactly as prior audits found. `RideRequest.tsx`'s own code comments (lines ~229–237) state this explicitly: the flow "calls Order Management's `POST /v1/orders` directly" then Dispatch's `POST /v1/proposals` "exactly as [Sprint 7B] already established." Passenger Experience's own `OrderSubmissionCoordinator`/`PassengerOrderSubmissionApplicationService` exist in source (confirmed present in `backend/passenger-experience`) but — per `ADR-062`'s own Context section, itself citing direct source inspection — have **no controller wiring them to any HTTP entry point**. They are dead code, unreachable today.

**Coordinator's own, separate path** (`frontend/src/pages/Coordinator/Coordinator.tsx`): a human reads `GET /v1/orders` and `GET /v1/drivers`, then calls the same `POST /v1/proposals` endpoint manually (confirmed by direct grep of `Coordinator.tsx` line 246: `request<ProposalResponse>('/v1/proposals', ...)`). This is "Stage A" / normal dispatch — the fallback path First Refusal must degrade to.

## 4. Current Order → Dispatch Call Graph

There is **no automated matching algorithm** in this codebase (`ADR-002`: driver-selection criteria are "never Dispatch's own architectural or domain concern," restated in `Assignment.create`'s and `Proposal.propose`'s own KDoc). Every `Proposal` in this system today is created by one of exactly two human/client decisions naming a specific `driverId`:

- The passenger's own invitation-link driver (`RideRequest.tsx`'s direct-propose flow, Section 3).
- The Coordinator's own manual pairing (`Coordinator.tsx`).

Both funnel through the **one** creation path: `ProposalController.createProposal` (`backend/dispatch/.../api/ProposalController.kt:93`) → `ProposalApplicationService.handle` (`.../application/ProposalApplicationService.kt:125`) → `Proposal.propose(...)` (`.../domain/Proposal.kt:230`) → `OrderProposed` (in-process only; **no outbox write** — `ProposalApplicationService`'s own KDoc states this deliberately: no ratified contract names any Proposal-stage event as cross-domain, so wiring one would create an unratified contract silently).

`ProposalApplicationService.handle` (confirmed by direct reading) **already gates on driver availability**: `driverAvailabilityRepository.findByDriverReference(command.driver)` must show `available == true`, else `IllegalStateException` (409 at the controller). This is the one automated eligibility check that exists today, and it is exactly the shape a "Primary Driver unavailable → fall through" check would reuse.

**Acceptance → Assignment → Trip** (already fully audited in Tasks 11/12, re-confirmed present, unchanged by this audit): `ProposalController.acceptProposal` → `ProposalAssignmentOrchestrationService.acceptProposal` → `Assignment.create()` (publishes `OrderAssigned`) → `DispatchAssignmentApplicationService`'s `createTripFor`/`Trip.create()` (Task 11) → Trip's own ride-progress lifecycle now converged (Task 12).

**Proposal expiration**: `ProposalLapseScheduler` (`@Scheduled(fixedDelayString = "\${pios.proposal.lapse.fixed-delay-ms:30000}")`, `backend/dispatch/.../application/ProposalLapseScheduler.kt:51`) calls `ProposalLapseApplicationService.lapseStaleProposals()` every 30s (default, configurable), which lapses every `OPEN` Proposal whose `createdAt` is older than `pios.proposal.lapse.timeout-minutes` (default **5 minutes**, `ProposalLapseApplicationService.kt:67`).

**Insertion point for First Refusal, based on the actual call graph (not intuition):** `ProposalApplicationService.handle` (or a new orchestrating layer immediately above it, in front of both `ProposalController.createProposal` and whatever new First-Refusal-aware endpoint is added) is the one place both real Proposal-creation paths already funnel through, and it already performs one gating check (driver availability) of exactly the shape a Primary-Driver-eligibility check needs. This is the smallest-disruption point — it requires no change to `Proposal.propose`'s own invariant, no change to `ProposalController`'s two existing entry points' contracts, and reuses the one proven gating pattern already in this exact class.

## 5. Current Relationship Model — Verified Against Source

| Concept | Owning module | Database | Table(s) | Identifiers |
|---|---|---|---|---|
| `Person`, `PersonProfile` | `network-management` | `pios_network_management` | `V1__initial_schema.sql` (`persons`, `person_profiles`) | `Person.id` |
| `Connection` (Person↔Person referral) | `network-management` | `pios_network_management` | same | abstract Person ids — **a different concept from Passenger Experience's `Connection`, deliberately kept separate and excluded from the pilot flow** (`frontend/vite.config.ts` lines 19–22, confirmed by ADR-054's own direct citation) |
| `Connection` (Passenger↔Driver pairing) | **Passenger Experience** | `pios_passenger_experience` | `connections` (`V1__create_connections.sql`) — `id, driver_id, passenger_reference, created_at`, `UNIQUE (driver_id, passenger_reference)` | `driver_id`/`passenger_reference` — plain strings, reference-not-ownership |
| **Primary designation** | **Passenger Experience** | `pios_passenger_experience` | `primary_connections` (`V2__create_primary_connections.sql`) — `passenger_reference PRIMARY KEY, connection_id, designated_at`, composite FK to `connections(passenger_reference, id)` | keyed by `passenger_reference` — structurally at most one row per passenger |
| Invitation mechanism | `network-management` | `pios_network_management` | part of `V1__initial_schema.sql` | — |

**A. Owner**: Passenger Experience, exclusively, for the Primary Driver fact itself (ADR-054 Part 1). **B/C.** confirmed above. **D.** plain string `driver_id`/`passenger_reference` (reference-not-ownership, `DriverReference.kt` in `passenger-experience`). **E.** `ConnectionController.kt` (`backend/passenger-experience/.../api/`): `POST /v1/connections`, `GET /v1/connections?driverId=`, `GET /v1/connections?passengerReference=`, `POST /v1/connections/{id}/primary`, `DELETE /v1/connections/{id}` — **all five require a valid `Bearer` session token** (`sessionTokenVerifier`, ADR-055); the three passenger-facing ones additionally require the token's own `sub` claim to match. **F. Synchronously queryable today**: only by the passenger's own authenticated browser session (`sub` match) — **no service-to-service path exists.** **G. Consumable by Dispatch today**: **no.** No event is published on set-primary or remove — confirmed directly: `SetPrimaryConnectionApplicationService.handle` (`backend/passenger-experience/.../application/SetPrimaryConnectionApplicationService.kt:25`) calls only `connectionRepository.findById` and `primaryConnectionRepository.setPrimary` — no `OutboxRepository`, no event construction, nothing. **H. An existing primitive already provides everything needed — do not build a second one.**

**CRITICAL instruction satisfied**: no second Primary Driver model should be created, and this audit found none needed — `Connection`/`primary_connections` already expresses exactly the required semantics (structural single-primary invariant, passenger-owned, at-most-one). The only missing piece is the **event that reports a change**, not a new model of the relationship itself.

## 6. Primary Driver Feasibility

Feasible without a new relationship model. What is required, precisely:

1. `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` domain events, published from `SetPrimaryConnectionApplicationService`/`RemoveConnectionApplicationService` (or wherever "primary" is cleared — currently `removeConnection` deletes the underlying `Connection` row, cascading the `primary_connections` row via `ON DELETE CASCADE`; whether that cascade path needs its own explicit clear-event trigger, or whether only an explicit future "unset primary without removing the connection" action would, is an open implementation question ADR-062 itself leaves open).
2. Passenger Experience's own outbox wiring for these two events (this module currently publishes **no** events at all — Circle of Trust was deliberately built with "no new event" as a stated non-goal at the time, ADR-054 Part 1: "No new service and no new queue... no event, no outbox record, no consumer"). This is now superseded, narrowly, by ADR-062 — building the outbox path for these two specific events is new work, not a reversal of anything.
3. Dispatch's own `PrimaryDriverRecord` projection + consumer queue/DLQ, mirroring `DriverAvailabilityRecord`/`DriverAvailabilityChangedListener` exactly (ADR-062's own named precedent, and directly present in source at `backend/dispatch/.../application/DriverAvailabilityRecord.kt`, `.../persistence/DriverAvailabilityChangedListener.kt`).

## 7. Proposal Analysis

- **Who creates it**: `ProposalApplicationService.handle`, from either real caller (Section 4).
- **Who receives it**: the named `driverId` — visible via `GET /v1/proposals?driverId=` (session-token-gated for a driver's own token, or owner-credential for the Coordinator/Owner view — ADR-060 Decision 4).
- **Expiration**: yes, already exists (Section 4) — `ProposalLapseScheduler`/`ProposalLapseApplicationService`, default 5-minute timeout, fully configurable, already production-wired.
- **Decline**: `Proposal.decline()` — `OPEN → DECLINED`, driver-initiated, via `POST /v1/proposals/{id}/decline`.
- **Lapse**: `Proposal.lapse()` — `OPEN → LAPSED`, system-initiated via the scheduler.
- **Multiple proposals coexisting**: **no**, by the aggregate's own root invariant — "an order may have at most one open proposal at any time" (`Proposal.propose`'s own `check()`), enforced both in-memory (existing-proposals read) and, per this module's established convention, expected to be enforced by a persisted uniqueness constraint as well (mirrors `Assignment`'s own precedent — not independently re-verified against the live migration in this audit, flagged for the implementation task to confirm).
- **Cancellation**: yes — `Proposal.withdraw()` (`WITHDRAWN`), but **only** reachable via `ProposalAssignmentOrchestrationService`... actually via `OrderCancelledApplicationService` (Dispatch's own consumer of Order Management's `OrderCancelled`, ADR-053) — **no manual withdraw endpoint exists** (`ProposalApplicationService.kt`'s own KDoc states this explicitly, citing ADR-053 Part 4).
- **Does Proposal already have a concept representing First Refusal**: **the state vocabulary does** (`OPEN`→`ACCEPTED`/`DECLINED`/`LAPSED` maps 1:1 onto accept/decline/timeout) but **the aggregate carries no concept of "this proposal is a First-Refusal offer, distinct from a normal one."** There is no field distinguishing why a Proposal exists or what happens next if it resolves negatively.
- **Would reusing Proposal corrupt existing semantics?** Not necessarily, but two real risks exist if First Refusal is implemented as "just another Proposal": (a) the Coordinator's own `todayData.ts`/`Coordinator.tsx` views, and the driver's own `GET /v1/proposals?driverId=` list, would show a First-Refusal offer identically to a normal one, with no way for a caller to know "if this lapses, does it auto-retry with a *different* driver via normal matching, or does the order sit unassigned until a human notices?" — today, a lapsed/declined Proposal does **nothing automatically**; a human (Coordinator) or another client call must create the next one. (b) A second `OPEN` Proposal cannot be created for the same order while the first is still open (Root Invariant) — this is actually **desirable** for First Refusal (prevents a race between the primary driver's own Proposal and a normal-dispatch one), but it also means whatever creates the *fallback* Proposal after decline/lapse must itself be triggered reliably, exactly once, by something — today nothing does this automatically for a normal (non-First-Refusal) lapsed/declined Proposal either; it currently relies on a human (Coordinator) noticing.

**Determination (A/B/C):** **(A) — reuse `Proposal`, do not introduce a separate First Refusal aggregate.** Every state First Refusal needs (`OPEN`/`ACCEPTED`/`DECLINED`/`LAPSED`) already exists, exactly matching `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 18 item 4's own conclusion ("`ProposalStatus`/`AssignmentStatus` do not need new states for First Refusal itself... what's new is the *trigger*, not the *outcome vocabulary*"). What is missing is not a new domain concept but a **decision-making layer above Proposal creation** (Section 4's insertion point) that (i) checks the Primary Driver projection before the first Proposal is created, (ii) on that Proposal's negative resolution (decline/lapse/ineligibility), automatically and reliably creates the *next* Proposal through normal matching rather than waiting on a human — this second half is itself a **new, currently-nonexistent automation**, since today nothing automatically retries a lapsed/declined Proposal at all, First-Refusal or not.

## 8. Response-Window Infrastructure

Fully reusable as-is:
- **Where such configuration should live**: `pios.proposal.lapse.timeout-minutes` already exists as exactly this kind of parameter — a Spring `@Value`-injected, externally configurable duration, no code change needed to alter it, mirroring `OutboxRelayScheduler.FIXED_DELAY_PROPERTY`'s own established precedent (`ProposalLapseApplicationService.kt`'s own KDoc). Whether First Refusal needs its **own**, separate, shorter window (e.g., "primary driver has 60s, but a normal Coordinator proposal still gets 5 minutes") or can share the existing one is an **open implementation question**, not decided here — the existing mechanism supports either (a second `@Value`-configured parameter is additive) but the *value itself* remains explicitly OPEN per Section 2.
- **Existing timeout infrastructure**: yes, directly reusable — `ProposalLapseScheduler` + `ProposalLapseApplicationService`, already production-wired, already tested (`ProposalLapseApplicationServiceTest.kt`), already handles the exact concurrent-resolution race (Section 9).
- **How timeout is detected**: a periodic sweep (`@Scheduled(fixedDelay=...)`), not per-Proposal timers — `findOpen()` then a stale-age filter.
- **Duplicate timeout processing prevention**: `Proposal.lapse()`'s own precondition (`check(status == OPEN)`) plus `ProposalLapseApplicationService.lapseIfStillOpen`'s own documented, deliberate handling of the resulting `IllegalStateException` as "already resolved, not a failure" — this is the exact mechanism ADR-052's own Negative Consequences section anticipated and accepted.

## 9. Race-Condition Analysis

- **Accept vs. expire**: already solved by the existing mechanism (Section 8) — `Proposal.lapse()`'s `check()` precondition makes the two mutually exclusive at the aggregate level; whichever write commits first wins, the other throws and is treated as a no-op (not an error). **This pattern is directly reusable for First Refusal's own accept-vs-expire race with no new mechanism required.**
- **Accept vs. passenger cancellation**: already solved by the existing `OrderCancelled` → `Proposal.withdraw()` path (ADR-053) for a still-`OPEN` Proposal — same `check()`-based mutual exclusion. Not yet analyzed for the case where cancellation arrives *after* acceptance but *before* Assignment creation completes — this is a **pre-existing gap unrelated to First Refusal** (`ProposalAssignmentOrchestrationService`'s own KDoc, cited in Section 4, already discloses "no shared transaction between the Proposal and Assignment writes" as a known limitation), not introduced or worsened by First Refusal.
- **Decline vs. timeout handler**: same `check()` mechanism, same "already resolved" handling — solved.
- **First Refusal vs. normal dispatch starting prematurely**: **this is the one race with no existing guard**, because nothing about First Refusal exists yet to race against. The Root Invariant ("at most one `OPEN` proposal per order") is the structural primitive that *would* prevent it, **provided** the decision logic that creates the fallback (normal-dispatch) Proposal only runs *after* the First-Refusal Proposal has actually reached a terminal state (`DECLINED`/`LAPSED`), never speculatively in parallel. Concretely: as long as the fallback-Proposal-creation code path is triggered *by* the First-Refusal Proposal's own resolution (a decline/lapse handler, not an independent timer racing the same clock), the existing invariant alone prevents the premature-start race — no new locking or state machine is required beyond correctly sequencing "resolve, then react," which is exactly the shape `ProposalLapseApplicationService.lapseIfStillOpen` already demonstrates for a different transition.

**Required transactional/idempotency mechanism, precisely:** (1) the existing `Proposal` root invariant, unmodified; (2) the existing `check()`-based mutual exclusion on every terminal transition, unmodified; (3) the existing "treat a lost race as already-resolved, not a failure" handling pattern, reused; (4) a **new** requirement specific to First Refusal: the code that creates the fallback Proposal must run from *inside* (or immediately, reliably after, in the same logical unit as) the handler that resolves the First-Refusal Proposal negatively — not from an independent process that could fire before or concurrently with that resolution. No new database constraint appears necessary beyond what already exists (the Root Invariant's own persisted uniqueness, Section 7's own flagged-for-confirmation point).

## 10. Required State Machine

**Recommendation: do not introduce new state names — reuse `ProposalStatus` (`OPEN`/`ACCEPTED`/`DECLINED`/`LAPSED`/`WITHDRAWN`) for the individual First-Refusal Proposal itself**, per Section 7's determination. What First Refusal actually needs, on top of that, is a small **outer state** — not on `Proposal`, but on however the *order-level* First Refusal attempt is tracked (a new, small piece of state; see Section 13 for whether this needs its own table):

| Outer state | Meaning | Legal transitions | Who triggers | Event produced | Effect on normal dispatch |
|---|---|---|---|---|---|
| `PENDING` (or equivalent) | A Primary-Driver Proposal is `OPEN` | → `RESOLVED_ACCEPTED`, → `RESOLVED_DECLINED`, → `RESOLVED_TIMEOUT` | driver (accept/decline), scheduler (lapse) | mirrors the underlying `ProposalAccepted`/`ProposalDeclined`/`ProposalLapsed` | **blocks** any fallback Proposal from being created for this order |
| `RESOLVED_ACCEPTED` | Terminal — Assignment proceeds | none (terminal) | — | `OrderAssigned` (existing, unchanged) | N/A — normal dispatch never needed |
| `RESOLVED_DECLINED` | Terminal for the First-Refusal attempt | → triggers fallback Proposal creation | system, immediately on decline | none new required — the existing `OrderProposed` for the *next* Proposal already represents "normal dispatch has now started" | **releases** the block; normal dispatch begins |
| `RESOLVED_TIMEOUT` | Terminal for the First-Refusal attempt | → triggers fallback Proposal creation | scheduler | same as above | same as above |
| (no Primary Driver / ineligible) | Not entered at all | — | — | — | normal dispatch begins immediately, unchanged from today |

**Illegal transitions**: `PENDING` directly to normal dispatch (the exact case the task explicitly requires be prevented) — prevented structurally by the Root Invariant (Section 9) as long as fallback-creation is sequenced correctly, not by inventing a new lock.

Whether this outer state is a real, persisted row (Section 13) or purely derivable from "does an `OPEN` Proposal for this order exist, and was it the *first* Proposal for this order" is an open implementation question this audit surfaces but does not resolve — surfaced in Section 22.

## 11. Event Architecture

| Step | Existing event reusable? | New event needed? |
|---|---|---|
| Process starts | `OrderSubmitted` — **exists, unconsumed today**, could become the trigger, but the current direct-propose flow does not go through it (Section 3) — using it would require Order Management to be a dependency of the trigger path, or the frontend's own existing two-sequential-calls shape to remain the trigger (matching ADR-062's own "Trigger, kept unchanged" decision, Section 2) | No — ADR-062 already decided the trigger stays an explicit client/Coordinator act, not `OrderSubmitted`-driven |
| First Refusal created | `OrderProposed` — **exists**, already fires on every `Proposal.propose()`, including a First-Refusal one | No new event required to *represent* creation |
| Acceptance | `ProposalAccepted` → `OrderAssigned` — **exist**, unchanged | No |
| Decline | `ProposalDeclined` — **exists**, unchanged | No |
| Timeout | `ProposalLapsed` — **exists**, unchanged | No |
| Normal dispatch may begin | No existing event represents this **transition** specifically (it's currently just "a human notices and calls `POST /v1/proposals` again") | Possibly none needed if the fallback Proposal's own creation (`OrderProposed`, again) is treated as sufficient signal — an explicit new event would only be needed if some *other* consumer needs to react to "First Refusal ended, without yet knowing what replaces it," which no current requirement names |
| Primary Driver relationship signal | **New, already-authorized-not-yet-built**: `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` (ADR-062) | Yes — the one new event pair this whole capability actually requires |

**`OrderSubmitted`, `OrderAssigned`, `Assignment*`, `Trip*`**: none require any change. The Order Management event-consumption gap (neither `OrderAssigned` nor `AssignmentAccepted` consumed there) is **explicitly not touched** by this audit's recommendations, per this task's own instruction.

## 12. Network Management Boundary

`network-management`'s own `Connection` (Person↔Person) is a **different concept**, already deliberately fenced off from the pilot flow (`frontend/vite.config.ts` lines 19–22, confirmed) and explicitly outside ADR-062's own scope ("This does not authorize any Network Management integration," ADR-062 Constraints). **No relationship exists in code today between the two `Connection` concepts**, and none is required for First Refusal.

**Determination (A/B/C)**: **(B) — Passenger Experience already provides the narrow contract (ADR-062), and no Network Management involvement is needed or authorized.** The smallest contract needed for First Refusal is exactly what ADR-062 already specifies: two opaque identifiers (`passengerReference`, `primaryDriverId`), nothing more — already documented, already illustrated in code-shape (ADR-062's own `PrimaryDriverRecord` sketch), not yet built.

**Broader PIOS Network vision, addressed honestly**: the task's own framing ("a relationship established in Taxi should be capable of becoming part of the broader PIOS participant network") is a real, named aspiration in `network-management`'s own founding documents, but **`ADR-037` — the ADR that would need to authorize any such convergence — is itself still `Proposed`, not `Accepted`** (confirmed: `docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md`'s own Status section says "Proposed," no Decision Date; `docs/README.md` line 71 records the same status). This is a **pre-existing documentation-state inconsistency** (a module built and referenced elsewhere as settled fact, while its own founding decision was never formally accepted) — flagged here, not fixed, and **immaterial to First Refusal**, since First Refusal's own contract (ADR-062) explicitly does not depend on or touch Network Management at all.

## 13. Passenger Experience Boundary

- **Owns passenger identity?** No — `identity` module owns authentication; Passenger Experience owns `PassengerReference` (a plain string) and the Circle-of-Trust relationship, not identity itself.
- **Knows the passenger's primary driver?** Yes, exclusively (`primary_connections` table).
- **Owns ride-request initiation?** In principle (`OrderSubmissionCoordinator` exists in source) but **unreachable in production today** — the frontend bypasses it entirely (Section 3).
- **Communicates with Order Management today?** Only in the sense that both are called sequentially, directly, by the same frontend — no server-to-server call exists between them.
- **Communicates with Dispatch today?** **No** — this is precisely the gap ADR-062 authorizes closing.
- **Is a new Passenger Experience → Dispatch contract actually required?** **Yes** — already authorized (ADR-062), not yet built.
- **Should the relationship cross as an opaque identifier, not profile data?** **Yes, and ADR-062 already mandates exactly this** — `PrimaryDriverRecord`'s own illustrative shape carries only `passengerReference`/`primaryDriverId`, explicitly forbidding rating/reputation/profile data (ADR-062 Constraints: "No ranking, scoring, or weighting is introduced"). This audit found nothing in the current codebase that would tempt violating this — `Connection`'s own fields are already minimal (`driver_id`, `passenger_reference`, `created_at`).

## 14. Frontend Impact (Documentation Only — Nothing Modified)

**Existing, reusable pieces, confirmed in source:**
- `RideRequest.tsx` already polls `GET /v1/proposals?orderId=` and `GET /v1/assignments?orderId=` on an interval and already renders a status label (`rideStatusLabel`, Task 7's own recent cleanup target) covering "waiting for driver," "accepted," ride-progress states. A First-Refusal-specific waiting state ("your usual driver has first chance") would extend this same status-label function, not a new mechanism.
- `RideRequest.tsx` already has a full Circle-of-Trust management UI in place (`handleRequestMakePrimary`/`handleConfirmMakePrimary`/`handleRequestRemove`/`handleConfirmRemove`, lines ~757–818) — the passenger already sees and manages their primary driver on this exact screen.
- `DriverHome.tsx` already polls `GET /v1/proposals?driverId=` and renders Accept/Decline actions (`respondToAssignment`-sibling pattern, `.../{action}` POST) — a First-Refusal offer, if represented as an ordinary `Proposal` (Section 7's recommendation), requires **no new driver-facing component at all**, only (at most) a visual distinguishing cue if product wants the driver to know "this is your Circle-of-Trust customer" — not decided here.
- Cancellation: `RideRequest.tsx` already has a working `POST /v1/orders/{id}/cancel` flow (line ~698) — reusable, unmodified.

**What a real implementation would need to design (not designed here):** whether/how the passenger sees "your primary driver has first refusal" as a distinct waiting state from ordinary "waiting for a driver"; whether the driver sees any visual distinction for a Circle-of-Trust offer vs. a Coordinator-assigned one. Both are additive UI-state questions on top of existing polling/rendering infrastructure, not new infrastructure.

## 15. Security Impact

- **Existing gaps, unchanged, cited from source directly (not assumed)**: `POST /v1/proposals` (`ProposalController.createProposal`) has **no authentication check of any kind** — confirmed by reading the method body; only `listProposalsForDriver` (the `?driverId=` branch of `GET /v1/proposals`) checks anything. `POST /v1/proposals/{id}/accept`, `/decline`, `/lapse` likewise have **no caller-identity check at all** — confirmed directly: none of `acceptProposal`, `declineProposal`, `lapseProposal` in `ProposalController.kt` references `sessionTokenVerifier` or `ownerCredentialGate`.
- **New requirement First Refusal specifically introduces**: the task's own example — *"a driver must not be able to accept another driver's First Refusal"* — is **not met by the current codebase at all**, for *any* Proposal, First-Refusal or ordinary. Today, anyone who obtains a `proposalId` (enumerable via `GET /v1/proposals?orderId=`, itself deliberately left unauthenticated per ADR-060 Decision 4, Section 194–197 of `ProposalController.kt`'s own KDoc) can accept or decline it, regardless of which driver it names. **This is not a new gap introduced by First Refusal — it is a pre-existing one that First Refusal makes more consequential**, since a First-Refusal Proposal specifically encodes a passenger's trust relationship; an unauthenticated third party wrongly accepting it would misroute a ride to the wrong driver in a context the product explicitly frames as trust-sensitive.
- **Does this block implementation?** Per Phase 16 condition 6 — "existing auth makes the flow unsafe and cannot be addressed additively" — **it does not block**, because it can be addressed additively: adding a `sessionTokenVerifier`-based check to `acceptProposal`/`declineProposal` (comparing the token's own `drv` claim to the Proposal's `driver.driverId`, exactly the pattern `ConnectionController.setPrimaryConnection` already establishes for `sub`) is a strictly additive change to an already-existing endpoint, requiring no new infrastructure. It is, however, a **prerequisite this audit recommends be done before or alongside First Refusal**, not deferred indefinitely — named as a recommendation (Section 19), not performed here, and not part of the broader security register this task explicitly says not to solve.
- **Passenger Experience's own endpoints** (Section 5) are already fully session-authenticated (ADR-055) — no additional work needed there for First Refusal's own data path.
- **Service-to-service authentication**: still does not exist anywhere in this codebase (confirmed, matches `docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md`'s own prior finding) — irrelevant to ADR-062's chosen mechanism specifically, since event-fed projection (RabbitMQ) requires no new service-to-service credential, which is exactly why ADR-062 chose it (its own Decision table, "Operational complexity" row).

## 16. Database Impact

- **New tables likely required**: (1) Dispatch's own `PrimaryDriverRecord` projection table (ADR-062's own explicit Consequence: "Dispatch's own database gains one new table"); (2) possibly a small table tracking the outer "PENDING/RESOLVED_*" state per order (Section 10) — **or** this may be derivable without a new table if "the first Proposal for an order was First-Refusal-triggered" can be inferred from existing data (e.g., a boolean/reason column on `proposals` rather than a whole new table) — an open implementation question, not resolved here.
- **New columns**: possibly one on `proposals` (e.g., a `first_refusal` marker) if the outer-state table above is avoided in favor of a column — smaller, more additive, consistent with this codebase's own repeated preference (`isTest`, `statedPrice` precedent) for additive columns over new tables when the concept is a flag/optional fact rather than a new invariant-bearing entity. Given Section 7's own finding that "is this Proposal a First-Refusal offer" carries no new invariant of its own, a column is the better-precedented shape than a new table, **but this determination belongs to the implementation task, informed by ADR-054's own Part 2 reasoning for when a new table beat a column** (an invariant, not a flag, forces the new-table choice — First Refusal's own marker does not obviously carry one).
- **New indexes/FKs/unique constraints**: none beyond what a `PrimaryDriverRecord` table needs on its own key (mirroring `DriverAvailabilityRecord`'s own precedent). No cross-module FK — forbidden by ADR-005/009 regardless.
- **What must survive a restart between First Refusal CREATED and RESOLVED**: exactly what already must survive today between any Proposal's `OPEN` and its resolution — the `Proposal` row itself (`proposals` table, already durable), since `Proposal.createdAt`/`status` are what `ProposalLapseApplicationService` reads on every sweep regardless of process restarts (confirmed: `findOpen()` reads from the database, not from any in-memory scheduler state). **No new durability requirement is introduced by First Refusal beyond what already exists for Proposal** — the outer-state question above is the only piece that would need its own persisted row if it is not derivable from `proposals` alone.

## 17. Implementation Dependency Graph

```
TRACK A — Relationship / Primary Driver resolution
  A1. PrimaryConnectionDesignated/Cleared events + outbox wiring (Passenger Experience)
  A2. PrimaryDriverRecord projection + consumer/DLQ (Dispatch) — depends on A1

TRACK B — First Refusal lifecycle
  B1. Decision logic: "does this order's passenger have a Primary Driver?" — depends on A2
  B2. First-Refusal-aware Proposal creation (reuses Proposal.propose, Section 7) — depends on B1
  B3. Negative-resolution → fallback-Proposal-creation handler (Section 9's sequencing requirement) — depends on B2
  B4. Response-window value decision (product/config, Section 8) — independent, blocking B2's real-world usefulness but not its code

TRACK C — Dispatch handoff
  C1. Normal-dispatch fallback path — already exists (Coordinator, Section 4) — no new code, only B3's trigger into it
  C2. Race-condition guard (Section 9) — depends on B2, B3

TRACK D — Frontend
  D1. Passenger waiting-state UI (Section 14) — depends on B2 existing to poll against
  D2. Driver offer UI (Section 14) — likely no new component, depends on B2

TRACK E — Security
  E1. Proposal accept/decline caller-identity check (Section 15) — independent, recommended before/alongside B2, not technically blocking

TRACK F — Tests
  F1. Depends on every track above producing something to test
```

**Cross-track dependencies**: B depends entirely on A; C1 needs nothing new; C2 depends on B; D depends on B; E is independent but strongly recommended in the same implementation window as B, given B is what makes the existing accept/decline authorization gap consequential in a new way (Section 15).

## 18. Stop Conditions Encountered

**None.** Each of the 8 listed conditions was checked explicitly:

1. Contradiction with an approved product decision — none found (Section 2).
2. Network Management ownership ambiguous — not ambiguous; explicitly out of scope, confirmed by ADR-062 itself (Section 12).
3. Passenger Experience vs. Network Management ownership ambiguous — not ambiguous, same reason.
4. Proposal reuse would corrupt semantics — assessed in depth (Section 7); determined safe, with one caveat (fallback-Proposal automation is new work, not corruption).
5. No safe way to prevent normal dispatch from racing First Refusal — a safe way exists using only already-proven mechanisms (Section 9).
6. Existing auth makes the flow unsafe and cannot be addressed additively — unsafe today (Section 15), but addressable additively; does not block.
7. Would require a new product decision — no; ADR-062 and `PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 3–4 already cover it. The response-window *value* remains open but was never in scope to decide (Phase 6's own instruction).
8. Current production flow differs materially from ratified architecture in a way that changes the design — the direct-propose bypass of the Coordinator is a **known, already-documented** divergence (ADR-062's own Context section already discloses it), not a new discovery that changes anything.

## 19. Recommended Implementation Sequence

1. Track A (events + projection) — the only genuinely new infrastructure, fully spec'd by ADR-062, zero open design questions.
2. Track E (accept/decline caller-identity check) — small, additive, closes a real gap First Refusal makes more consequential; recommended alongside, not necessarily strictly before, Track B.
3. Track B (decision logic + fallback sequencing) — the actual behavioral capability.
4. Track C2 (race guard) — verify, not build; the guard falls out of B3's own correct sequencing plus the pre-existing Root Invariant.
5. Track D (frontend) — additive UI states on existing polling.
6. Track F (tests) throughout, not deferred to the end.

The response-window **value** (Phase 6) and the exact command/endpoint shape for "propose, honoring First Refusal" (ADR-062's own "Explicitly not decided" list) are both **prerequisites to finalize before Track B's own code is written**, not during it.

## 20. Exact Files Likely to Change in Implementation

**Passenger Experience** (Track A): `SetPrimaryConnectionApplicationService.kt`, `RemoveConnectionApplicationService.kt` (or wherever "clear primary" lands), a new `PrimaryConnectionDesignated.kt`/`PrimaryConnectionCleared.kt`, outbox wiring additions mirroring `DispatchAssignmentApplicationService`'s own outbox pattern, a new producer topology configuration (mirrors `RabbitMQProducerTopologyConfiguration.kt` precedent already in `dispatch`).

**Dispatch** (Tracks A2, B, C2, E): a new `PrimaryDriverRecord.kt` + repository (mirrors `DriverAvailabilityRecord.kt`/`DriverAvailabilityRepository.kt`), a new consumer listener (mirrors `DriverAvailabilityChangedListener.kt`), a new consumer topology configuration, `ProposalApplicationService.kt` (or a new orchestrating service above it, Section 4), `ProposalController.kt` (Track E's auth check; possibly a new endpoint shape per ADR-062's own open question), a new migration (`V10__...sql` or similar — confirmed next number would need re-verification at implementation time, not assumed here).

**Not expected to change**: `Order.kt`, `OrderLifecycleApplicationService.kt`, anything in `order-management` (per this task's own Order Management Rule and this audit's own finding that no new Order Management involvement is needed); `Assignment.kt`, `Trip.kt` (Track C1 reuses the existing hand-off unmodified); `network-management`, `identity`.

## 21. Files Explicitly Excluded

Everything in `backend/order-management`, `backend/network-management`, `backend/identity`, `backend/driver-management`; `Assignment.kt`/`Trip.kt` and their own application services (Task 12's own convergence is unaffected and untouched); any frontend file (this task modified none, and implementation should not either without separate authorization — Frontend Rule); RabbitMQ production credentials/topology beyond additive new queues; any ADR's own Status field.

## 22. Unresolved Product/Architecture Decisions

- The exact response-window duration (explicitly and repeatedly out of scope, Section 2/8).
- Whether First Refusal's window should be independently configurable from the existing Proposal lapse window, or share it (Section 8).
- Whether the outer "PENDING/RESOLVED" state (Section 10) needs its own persisted row/table, or is derivable from `proposals` alone with a marker column (Section 16) — an implementation-level question, not a product one, but one the next task should resolve deliberately rather than default into.
- The exact command/endpoint shape for "propose, honoring First Refusal if applicable" — ADR-062's own named open item.
- Whether/how the driver-facing UI distinguishes a Circle-of-Trust offer from an ordinary Coordinator one (Section 14) — a product/UX decision, not resolved here.
- Whether `removeConnection`'s cascade-delete of a primary designation should itself fire `PrimaryConnectionCleared`, or whether that event is reserved for a future explicit "unset primary without removing the connection" action (Section 6) — ADR-062 names both possibilities but does not choose.

## 23. Final Recommendation

Proceed to implementation planning for Track A first (the event/projection infrastructure ADR-062 already fully specifies, with zero open design questions), in parallel or immediately followed by Track E (the accept/decline authorization gap). Track B (the actual First Refusal decision/sequencing logic) should not begin until the response-window value and the outer-state persistence question (Section 22) are each explicitly decided — both are small, bounded decisions, not new architecture. No new ADR is required for any of this; ADR-062 already carries the full authorization. This audit found the codebase closer to First-Refusal-ready than a from-scratch reading of the product decision alone would suggest: the hard architectural question (module boundary, contract shape, event mechanism) was already resolved in Task 10; what remains is implementation, sequencing, and one un-glamorous but real security fix.

## 24. Explicit Statement — No Code, Infrastructure, or Data Was Changed

This task was read-only throughout. No Kotlin file, TypeScript file, test file, migration, or configuration was modified. No ADR's Status field was changed. No database (test or production) was connected to, queried, or modified. No RabbitMQ connection was made or topology altered. No service was restarted. The only file created by this task is this report, `docs/PIOS_TAXI_TASK_13_FIRST_REFUSAL_RUNTIME_AUDIT.md`. Every conclusion above cites the specific file, class, or method it was verified against, per this task's own requirement; where a claim could not be independently re-verified within this audit's own scope (the persisted uniqueness of the Proposal Root Invariant, Section 7), that limitation is stated explicitly rather than asserted as fact.

---

**Per this task's own final instruction: STOP here.** Not implementing Primary Driver, First Refusal, or any code. Not modifying the frontend, Network Management, Dispatch, or Order Management. Returning this report only.
