# PIOS Taxi Architecture Contract

Status: **Architecture ratification document.** Converts `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` into a precise, implementable architectural contract, resolving the Task 9 conflict via `ADR-062` and `ADR-063`. This document is architecture documentation only; nothing here is implemented.

**Update (Task 10B, 2026-09-03 — Architecture Ratification & Document Reconciliation).** `ADR-062` and `ADR-063` were reviewed against the actual repository (`docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md`), found internally consistent (with one wording correction applied to `ADR-063`'s own trigger definition), and are now both **Accepted**. `INTERFACE_CONTRACTS.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, `ADR-034`, `EVENT_CATALOG.md`, and `docs/README.md` have been reconciled accordingly. Section 21's gate below is updated to reflect this; see `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` for the consolidated ratification record and the current, authoritative gate status.

**Further update (Task 11A, 2026-09-03 — Correct Trip Creation Trigger).** Task 11's own mandatory pre-implementation call-graph inspection found the Task 10B correction above incomplete: `AssignmentAccepted` is never actually published by any live production path. `ADR-063`'s Trip-creation trigger is corrected a second time, to `OrderAssigned` — see Sections 9 and 13 below, `ADR-063`'s own Status section, and `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md` for the full record. `ADR-063` remains **Accepted**; only its technical trigger changed.

**Tagging convention** (per this task's own required structure): **FACT** (directly observed in code/config) / **APPROVED** (ratified in `docs/PIOS_TAXI_PRODUCT_DECISIONS.md`, restated not redecided) / **RECOMMENDATION** (this document's own architectural proposal, tied to `ADR-062`/`ADR-063`, both still Proposed) / **OPEN** / **NON-GOAL** / **TARGET** (the state being designed toward, not yet built).

---

## 1. Executive Summary

Implementing First Refusal requires exactly one new architectural fact this repository did not already have a ratified answer for: **a Passenger Experience → Dispatch contract carrying Primary Driver information.** `ADR-062` (created alongside this document) resolves it, choosing an event-fed local projection in Dispatch — the same shape already proven by `DriverAvailabilityRecord` — over three weaker alternatives (a synchronous cross-module call, overloading `OrderSubmitted`, or relying on client-side orchestration alone). `ADR-063` (also created alongside this document) ratifies Trip as a new aggregate inside Dispatch's existing module, splitting ride-progress facts cleanly away from the match decision `Assignment` retains.

Tracing the actual, current implementation (Section 2) surfaced two facts prior architecture documents in this decision chain had not stated precisely: **passenger-driven order submission and proposal creation already bypass the Coordinator entirely for the personal-invitation flow** (the frontend calls Order Management then Dispatch directly, Sprint 7B, "Personal Network Flow MVP"), and **Dispatch's own `POST /v1/proposals` endpoint has no authentication at all today.** Both facts materially shaped the contract recommendation in Section 6 and are flagged as pre-existing conditions, not something this document fixes.

## 2. Current Foundation Flow

**FACT**, traced directly from source code for this task (not inferred from documentation):

```
Passenger (RideRequest.tsx)
  │
  ├─► POST /v1/orders  (Order Management, DIRECT — no Passenger Experience hop)
  │     body: { passengerReference: <passenger's own Identity id>, pickupAddress, destination, passengerName, requestedPickupAt? }
  │     → Order created, status SUBMITTED
  │
  └─► POST /v1/proposals  (Dispatch, DIRECT — no Coordinator, no auth check)
        body: { orderId, driverId: <the URL's own inviting driverCode> }
        → Proposal created, status OPEN, notifies that specific driver

Driver (DriverHome.tsx)
  │
  └─► accepts/declines via Dispatch's own Proposal/Assignment endpoints

Coordinator (/coordinator, owner-credential-gated, human)
  │
  └─► the ONLY path by which an order not already auto-proposed to a specific
      driver (or whose auto-proposal was declined/lapsed) ever reaches a
      different driver — a human manually looks up available drivers/orders
      via REST and calls POST /v1/proposals directly, same endpoint the
      frontend already uses above.
```

**Where the passenger relationship currently exists**: Passenger Experience's `Connection` table (`ADR-054`, Circle of Trust) — `driverId`, `passengerReference`, `createdAt`, plus a separate `PrimaryConnection` marker (migration `V2__create_primary_connections.sql`) recording which one connection is primary. **This is displayed today (the RideRequest "Circle of Trust" step) but never consulted for routing** — confirmed by direct search: zero references to `isPrimary` anywhere in Dispatch or Coordinator source.

**What Primary Driver information Dispatch currently receives: none.** Zero. `INTERFACE_CONTRACTS.md` Section 5 lists five justified contracts (Passenger Experience → Order Management; Driver Management → Dispatch; Dispatch → Order Management; Order Management → Dispatch, added by `ADR-053`; Any Module → Notifications/Analytics, generic) — none touches Circle of Trust.

**What Dispatch does receive**: `DriverAvailabilityChanged` (from Driver Management, projected locally as `DriverAvailabilityRecord` — `driverReference`/`available` only, nothing else) and `OrderCancelled` (from Order Management, used to withdraw an `OPEN` Proposal). Nothing about who submitted an order beyond the plain `orderId`/`driverId` a caller supplies directly to `POST /v1/proposals`.

**A dead code path, worth naming precisely so it is not mistaken for a live mechanism**: Passenger Experience's own `OrderSubmissionCoordinator`/`PassengerOrderSubmissionApplicationService`/`RestClientOrderSubmissionClient` ("Tranche 2: Passenger Experience REST Transport") implement a passenger-experience-mediated order submission — but **no controller in Passenger Experience's own `api/` package invokes any of it.** It is unreachable from any live HTTP request today.

**Security fact, noted here because it directly bears on Section 6/17, not evaluated further in this document**: `ProposalController.createProposal` (`POST /v1/proposals`) has no authentication check of any kind — not `sessionTokenVerifier`, not `ownerCredentialGate`. Anyone who can reach the port can create a Proposal for any `orderId`/`driverId` pair.

## 3. Target Taxi V1 Flow

**TARGET**, per `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4, `ADR-062`:

```
Passenger creates order (mechanism unchanged from Section 2 — still an explicit
client act, never triggered by Dispatch consuming OrderSubmitted, Section 10)
  │
  ▼
Trigger reaches Dispatch (mechanism/endpoint shape not decided by this document —
ADR-062's own explicit non-scope)
  │
  ▼
Dispatch consults its own local PrimaryDriverRecord projection
(fed by Passenger Experience's new PrimaryConnectionDesignated/Cleared events,
ADR-062) — in-process, no synchronous cross-module call
  │
  ├─ Primary Driver exists → Proposal created targeting that driver first
  │     ├─ accepts  → Assignment proceeds with that driver (unchanged mechanism)
  │     ├─ declines → falls through to normal matching (Coordinator-visible,
  │     │              exactly as an un-auto-proposed order is today)
  │     ├─ no response within [OPEN window] → same fallthrough
  │     └─ ineligible/unavailable → same fallthrough
  │
  └─ No Primary Driver → proceeds directly to normal matching, unchanged from today
```

**What changes vs. Section 2**: Dispatch gains the ability to consult a Primary Driver signal before creating its first Proposal. **What does not change**: the trigger mechanism (still an explicit client/Coordinator act, Section 10), the fallback mechanism itself (still Coordinator-mediated, still human, Section 11), and every existing Proposal/Assignment state transition (`ADR-062`, Constraints).

## 4. Domain Ownership

**FACT/APPROVED**, consolidated, unchanged by this document except where `ADR-062`/`ADR-063` explicitly note a narrow supersession (Section 8):

| Concept | Owner | Notes |
|---|---|---|
| Passenger | Passenger Experience | unchanged |
| Driver | Driver Management | unchanged |
| Order | Order Management | unchanged (FACT: `SUBMITTED`/`COMPLETED`/`CANCELLED` only) |
| Proposal, Assignment (match only, post-`ADR-063`) | Dispatch | unchanged/narrowed per `ADR-063` |
| Trip | Dispatch (new, `ADR-063`) | new aggregate, same module |
| Circle of Trust / `isPrimary` (source of truth) | Passenger Experience | unchanged, `ADR-054` |
| `PrimaryDriverRecord` (derived projection) | Dispatch | new, `ADR-062` — explicitly **not** a second source of truth |
| Network Management's `Person`/`Connection`/`Invitation` | Network Management | unchanged, untouched by this task, `ADR-037` |

## 5. Primary Driver Ownership

**APPROVED (source of truth) / RECOMMENDATION tied to `ADR-062` (consumed signal), NOT YET APPROVED as an ADR (Status: Proposed):**

- **Source of truth**: Passenger Experience's `Connection.isPrimary` — unchanged, unambiguous, `ADR-054`.
- **Consumed routing signal**: Dispatch's own `PrimaryDriverRecord` projection — derived, non-authoritative, eventually consistent, fed only by two new events (`PrimaryConnectionDesignated`/`PrimaryConnectionCleared`).
- **No duplicate ownership is created** — this is the same relationship `DriverAvailabilityRecord` already has to Driver Management's own `Availability`, reused, not invented.
- **Who may modify**: Passenger Experience only, via existing `setPrimaryConnection`/`removeConnection` endpoints — no new write path anywhere.
- **Who may read**: within Dispatch, only the First Refusal decision logic; the projection is never exposed to any external caller.
- **Staleness**: accepted, bounded by ordinary event-relay latency, never a blocking condition (`ADR-062` Decision, "eventually consistent" — reused from the existing `DriverAvailabilityRecord` precedent, not a new tolerance invented here).
- **Unavailable information**: treated identically to "no Primary Driver exists" — falls through to normal matching, never an error state (`ADR-062` Decision).

## 6. First Refusal Contract

**RECOMMENDATION tied to `ADR-062`, NOT YET APPROVED as an ADR:** Option C — Passenger Experience publishes `PrimaryConnectionDesignated`/`PrimaryConnectionCleared`; Dispatch consumes and projects, following `DriverAvailabilityRecord`'s own proven shape. Full four-option comparison (A: synchronous REST call; B: overload `OrderSubmitted`; C: event-fed projection, chosen; D: client-orchestrated) is in `ADR-062`'s own Decision section — not reproduced in full here to avoid duplication, per this task's own instruction against creating redundant documentation.

**Why not the already-existing client-orchestrated precedent (Sprint 7B's own shape, which this task's Option D corresponds to)**: it is real, already shipped, and technically the lowest-effort path — but it places enforcement of what the product decision calls a first-class Matching Rule (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 10) entirely in client behavior, with no server-side record of whether First Refusal was even attempted, compounded by `POST /v1/proposals`'s own complete lack of authentication (Section 2). A rule the product explicitly wants to be *explainable* (Section 10, "explicit, explainable signals") should be enforced and auditable server-side, not merely performed by a well-behaved client.

## 7. Minimum Data Contract

**RECOMMENDATION tied to `ADR-062`, NOT YET APPROVED:**

| Field | Required/Optional/Forbidden | Reasoning |
|---|---|---|
| `passengerReference` | Required | The projection's own key — mirrors `PassengerReference`'s existing use elsewhere in this codebase |
| `primaryDriverId` (i.e. `DriverReference`) | Required | The one fact First Refusal needs |
| `connectionId` | **Forbidden** | Dispatch has no use for it and no ratified relationship to the underlying `Connection` aggregate beyond this one derived fact |
| Passenger name, phone, or any other passenger profile field | **Forbidden** | Not required for the routing decision; sending it would violate `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 13's "not a data broker" boundary, restated here as a hard constraint on this specific contract |
| `createdAt` (of the Connection) | **Forbidden** | Not needed for a binary "does a primary exist" check; a future feature that *did* need it would require its own new decision, not a speculative field added now |
| Relationship status/type | Not applicable | Circle of Trust models only a binary `isPrimary`, no richer status vocabulary exists to send |
| Eligibility/relevance indicator | **Forbidden for this contract** | Eligibility (`docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 8) is a *separate*, Driver-Management-owned fact, evaluated independently by Dispatch at Proposal-creation time — conflating it into this contract would blur two distinct ownership domains |

**Privacy**: the projection carries only two opaque identifiers, never any human-readable passenger data — satisfying `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 13 by construction, not by later review.

**Source of truth**: restated from Section 5 — Passenger Experience, always; the projection is never treated as authoritative by anything, including Dispatch itself.

**Versioning**: Section 16 below.

## 8. Failure Modes

**RECOMMENDATION tied to `ADR-062`'s own framing, NOT YET APPROVED. No timeout values are invented — every numeric parameter below is explicitly OPEN, per this task's own instruction.**

| # | Failure | Expected product behavior | Architectural handling |
|---|---|---|---|
| 1 | Primary Driver *service* unavailable (i.e., Dispatch's own projection lookup fails — a purely local, in-process read, so this is really "projection missing/empty") | Order proceeds to normal matching, no passenger-visible error | Treated identically to "no Primary Driver" (Section 5) — no remote call exists to fail, by construction of the chosen contract (`ADR-062`'s own point against Option A) |
| 2 | Relationship lookup timeout | Not applicable under the chosen contract — no synchronous lookup exists | N/A, a direct benefit of rejecting Option A |
| 3 | Stale relationship (projection hasn't caught up to a recent `isPrimary` change) | First Refusal may offer to the *previous* primary driver briefly | Accepted (Section 5) — bounded by ordinary event-relay latency, same tolerance already accepted for `DriverAvailabilityRecord` |
| 4 | Primary Driver offline (app closed, not polling) | Falls through on non-response, same as any non-responsive driver today | Reuses existing `ProposalStatus.LAPSED` mechanism (timeout — OPEN duration) |
| 5 | Primary Driver unavailable (`Availability.UNAVAILABLE`) | Falls through immediately, no wait | Dispatch's existing `DriverAvailabilityRecord` already answers this — First Refusal should check availability before proposing, not after, avoiding a needless wait |
| 6 | Primary Driver declines | Falls through to normal matching immediately | Reuses existing `ProposalStatus.DECLINED` — unchanged mechanism |
| 7 | Primary Driver does not respond | Falls through after the [OPEN] response window | Reuses existing `ProposalStatus.LAPSED` — the window value itself is OPEN, not invented here |
| 8 | Duplicate First Refusal (e.g., a retried client call creates two Proposals for the same order) | Passenger sees one coherent state, not two competing proposals | Not newly introduced by this design — `Order`'s own existing invariant plus Dispatch's own idempotency conventions (already proven in a genuinely live path — `AssignmentCompleted`'s own consumer-side idempotency, Order Management, reachable via `/complete`; **corrected, Task 11A**: this row previously cited `AssignmentAccepted` as the proven precedent, but that event is not currently published by any live path, so it could not have proven anything in production — `ADR-062` Constraints reuses the same general pattern) should extend to cover it; exact mechanism is an implementation detail, not decided here |
| 9 | Order cancelled during First Refusal | The in-flight Proposal is withdrawn, exactly as today | Reuses the existing `ADR-053` mechanism (`OrderCancelled` → `ProposalWithdrawn`) unchanged — First Refusal's own Proposal is an ordinary Proposal from this mechanism's point of view |
| 10 | Driver accepts *after* fallback has already begun (a race between the Primary Driver's late acceptance and the Coordinator's own manual re-proposal to someone else) | Product behavior **OPEN** — no existing document decides which assignment should win, or whether both should be prevented | Architecturally, `Assignment`'s own existing invariant ("an order cannot be connected to more than one active assignment at a time," `Assignment.kt`) already prevents a *second* Assignment from being created once one exists — but does not by itself decide which of two *simultaneous* accept attempts wins a race; this remains an implementation-level concurrency question, not a product one, and is named here rather than answered |
| 11 | Network Management unavailable | **Not applicable** — First Refusal has no dependency on Network Management (Section 12) | N/A by design |
| 12 | Inconsistent relationship state (e.g., Passenger Experience shows a different primary than Dispatch's own stale projection) | Passenger sees Passenger Experience's own view (always current, since it's read directly, not through the projection); Dispatch may briefly act on the older one | Accepted staleness window (Section 5) — the passenger-facing Circle of Trust UI is never sourced from Dispatch's projection, only from Passenger Experience directly, so this inconsistency is invisible to the passenger except through *behavior* (which driver got first refusal), not through any displayed state disagreeing with itself |

## 9. Event Model

**FACT** (current) and **TARGET** (per `ADR-063`), per this task's own instruction not to perform any rename:

| Event | Current status | Taxi-specific or general? | Must eventually rename? |
|---|---|---|---|
| `OrderSubmitted` | Published (Order Management), no consumer (Section 10) | General (Order concept, not Taxi-specific in shape) | No |
| `OrderCancelled` | Published, consumed by Dispatch (`ADR-053`) | General | No |
| `OrderCompleted` | Published, no consumer beyond generic Notifications/Analytics | General | No |
| `OrderAssigned` | **Corrected, Task 11A**: reliably published by Dispatch on both real Assignment-creation paths, confirmed by direct call-graph inspection — but has **no actual consumer in Order Management** today, despite this table's own prior text and `INTERFACE_CONTRACTS.md` Section 7 both describing it as consumed there; only the external Driver API consumption is confirmed live. **Now also Trip's own authoritative creation trigger** (`ADR-063`, corrected) — a new, Dispatch-internal, in-process consumption, not a cross-module one | Taxi-specific in practice (Assignment is Dispatch's own concept) | No — stays on Assignment, unaffected by `ADR-063` |
| `AssignmentAccepted` | **Corrected, Task 11A**: modeled and outbox-capable, but **not currently published by any live production path** — `Assignment.accept()`, its only source, is reachable from no controller (Section 2's own finding, re-confirmed by Task 11). Order Management's own `AssignmentAcceptedListener` is real and correctly wired, but has never received a message. No longer Trip's trigger (was, briefly, per Task 10B's own since-superseded correction — see `ADR-063` Status section for the full history) | Taxi-specific | No — this is the *match*, stays on Assignment; its own non-production-status is a separate, unresolved integration gap (Section 20) |
| `AssignmentArrived` | **Updated, Task 12**: still published, unconsumed — now the *legacy* half of a live dual-publish pair (see `TripArrived` row below) | Taxi-specific | Dual-publish window now live (Task 12); full retirement (removing this event) not yet performed |
| `AssignmentStarted` | **Updated, Task 12**: same as `AssignmentArrived` above | Taxi-specific | Same as above |
| `AssignmentCompleted` | **Updated, Task 12**: still published, still the one consumed by Order Management (`ADR-041` Decision item 1) — deliberately unswitched, per Task 12's own Order Management Rule | Taxi-specific | Same as above — Order Management's own binding is the one thing this table's prior text said would eventually switch; it has not yet |
| `TripArrived` | **New, Task 12**: published by `Trip`, unconsumed | Taxi-specific | N/A — this is the target name itself |
| `TripStarted` | **New, Task 12**: published by `Trip`, unconsumed | Taxi-specific | N/A |
| `TripCompleted` | **New, Task 12**: published by `Trip`, unconsumed — Order Management still binds `AssignmentCompleted`, not this | Taxi-specific | N/A — becoming the sole surviving name (retiring `AssignmentCompleted`) is the remaining, not-yet-authorized step |
| `DriverAvailabilityChanged` | Published, consumed by Dispatch | General (Driver concept) | No |
| **`PrimaryConnectionDesignated`** (new, `ADR-062`) | Not yet built | Taxi-specific (Circle of Trust) | N/A — new |
| **`PrimaryConnectionCleared`** (new, `ADR-062`) | Not yet built | Taxi-specific | N/A — new |

## 10. OrderSubmitted

**APPROVED, restated, not reopened:** `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 9's own decision — `OrderSubmitted` remains published, Dispatch does not consume it as a matching trigger, the Coordinator (and, per Section 2 above, the passenger-driven direct-proposal path) remain the explicit triggers.

**Interaction with First Refusal, stated precisely (TARGET):** First Refusal does **not** change this. The trigger for attempting a Proposal — whether First-Refusal-aware or not — remains an explicit act by a client or the Coordinator, never `OrderSubmitted` consumption. What First Refusal adds is only a **check Dispatch performs once already triggered** (Section 6), not a new reason for Dispatch to act. **Current Foundation and Target Taxi V1 are identical on this point** — this document changes nothing about who or what decides an order needs a Proposal; it only adds a decision Dispatch makes about *which driver* to propose to first, once something has already decided a Proposal should be attempted.

## 11. Coordinator

**APPROVED (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4/8's own restatement of Task 7A Stage A→B→C) / TARGET role, precisely located:**

```
STAGE A (current, FACT) — Coordinator is the primary, manual mechanism for
  any order not auto-proposed to a specific driver via the invitation-link
  path (Section 2).

STAGE B (target, this document) — Coordinator becomes the FALLBACK path for:
  (a) orders with no Primary Driver (unchanged from today — same as Stage A),
  (b) orders whose Primary Driver declined/lapsed/was ineligible (Section 8,
      failure modes 4-7) — the Coordinator's own existing REST-polling
      console (todayData.ts) already surfaces these orders once they reach
      that state; no new Coordinator-side capability is required, only the
      upstream fact (First Refusal resolved negatively) becoming visible
      through the same query paths it already uses.

STAGE C (future, unaffected by this document) — unchanged from
  docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md Section 8; not designed
  here.
```

**Precise operational note (FACT, not previously stated this precisely in any prior document in this chain):** the Coordinator's own fallback role in Stage B is **not automatically triggered** — it depends on a human watching the Coordinator console and noticing a fallen-through order, exactly as today. This document does not add any push notification or alerting mechanism (that would be Notifications capability, `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 15, out of this task's scope). This is named explicitly so Stage B is not mistaken for a fully-automated fallback.

## 12. Network Management Boundary

**APPROVED, unchanged, no new ADR required (`ADR-037` already covers this):** First Refusal operates entirely on Circle of Trust (Passenger Experience's own `Connection`/`isPrimary`) and creates **zero** dependency, direct or indirect, on Network Management. `ADR-037`'s own Consequences section — "future work connecting `network-management`'s `Connection` concept to actual order routing... will need its own ADR" — remains exactly as deferred as before this task. **Taxi-specific**: Circle of Trust, `PrimaryDriverRecord` (Dispatch's projection). **Platform-level, untouched**: `Person`, general `Connection`/`Invitation` (Network Management). This document reaffirms `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` Section 5's own boundary without modification.

## 13. Order / Assignment / Trip

**APPROVED (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 9) / architecturally ratified by `ADR-063`:**

- **Created (corrected, Task 11A — this section's own earlier text, "Trip is created the moment an Assignment reaches `ACCEPTED`," is superseded, not deleted, per `CLAUDE.md`'s "Never Delete Documentation"; historical record kept in `ADR-063`'s own Status section).** The authoritative trigger, verified against the actual call graph rather than assumed:

  ```
  Assignment
      ↓
  OrderAssigned
      ↓
  Trip
  ```

  Task 11's own mandatory pre-implementation inspection found `AssignmentAccepted` (the trigger this section previously named, following `ADR-063`'s then-current text) is never actually published by any live production path — `Assignment.accept()`, the only place that event is constructed, is reachable from no controller. Both real Assignment-creation paths (Proposal acceptance, Coordinator manual assignment) call `Assignment.create()` and reliably publish `OrderAssigned` instead. Trip creation is therefore triggered by `OrderAssigned`, in-process, within Dispatch — still not a new cross-module event, unchanged from every prior version of this decision on that specific point. Trip creation does not mean the ride has started; it begins in its own initial, pre-`ARRIVED` lifecycle state. Full evidence: `docs/PIOS_TAXI_TASK_11_TRIP_FOUNDATION_REPORT.md`, `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md`.
- **Owns trip progress**: Dispatch, via the new `Trip` aggregate — never Order Management, never a new module. **Implemented, Task 12 (Trip Ride-Progress Convergence)**: `Trip` now actually validates and persists `ARRIVED`/`IN_PROGRESS`/`COMPLETED` (`Assignment`'s own identically-named methods/fields remain in place, unused by any production path, per Task 12's own compatibility rule — not the literal "removed from Assignment" this section and `ADR-063`'s Decision section once described; see `ADR-063`'s own Implementation note and `docs/PIOS_TAXI_TASK_12_TRIP_CONVERGENCE_REPORT.md`).
- **Assignment cancelled**: mid-Trip cancellation remains an explicitly **OPEN** question (`ADR-063` Decision, Section 8 failure mode analogy) — not resolved by this document, by `ADR-063` itself, or by Task 12.
- **Relation to Rating/Payment/repeat business**: all key off `TripCompleted` (or, for repeat business, are entirely independent of Trip, living in Passenger Experience) — unchanged from `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Sections 11–12, 21, reaffirmed not redesigned here. `TripCompleted` is now actually published (Task 12), still consumed by nothing.
- **Future verticals**: the Order/Match/Execution *pattern* generalizes; the `Trip` *name* and *table* do not — each future vertical would define its own equivalent, in its own module (`ADR-063` Decision, "Relationship to other concepts").

## 14. Payment Boundary

**APPROVED, unchanged, not implemented:** cash-first pilot; Taxi V1 architecture must remain compatible with future card/payment (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 14). No payment gateway, no payment economics, no `PaymentIntent`/`Payment` code is created by this task. `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 11's decomposable-`PaymentIntent` architecture remains the target, unaffected by `ADR-062`/`ADR-063` beyond confirming Payment should key off `TripCompleted` (Section 13, unchanged).

## 15. Trust / Rating / Reputation Boundary

**APPROVED, unchanged, not implemented:** `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 11's five-way distinction (Profile / Trust / Rating / Reputation / Relationship) stands, untouched by this task. No universal score. Nothing in `ADR-062`/`ADR-063` creates, reads, or depends on any Rating/Reputation data — First Refusal's own `PrimaryDriverRecord` (Section 7) carries no trust/reputation field of any kind, by explicit design (Section 7's own "forbidden fields").

## 16. Contract Versioning

**FACT, existing convention, reused, not reinvented (per this task's own explicit instruction):** this repository's own versioning discipline (`ADR-010`) governs event/interaction evolution — a new command, query, or event version is introduced *alongside* the one it replaces, never as a silent change (`MODULE_STRUCTURE.md` Section 8). `ADR-062`'s two new events (`PrimaryConnectionDesignated`/`PrimaryConnectionCleared`) are additive — no existing event is versioned or replaced. `ADR-063`'s event rename (`Assignment*` → `Trip*`) is the one place genuine versioning discipline applies: the dual-publish migration window named in `ADR-063`'s own Consequences section is this repository's already-established pattern for exactly this situation (the same pattern `ADR-010` itself describes) — no new versioning convention is introduced by this document.

## 17. Security / Privacy — Architectural Risk Register

**⚠️ ARCHITECTURAL RISK SECTION — production-hardening blockers, explicitly separate from `ADR-062`/`ADR-063`. FACT, flagged, not fixed by this task or by Task 10B.** These four items are pre-existing conditions of the current Foundation, confirmed by direct source inspection (`docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md` Section 3, re-confirmed here). None was introduced by `ADR-062`/`ADR-063`, and neither ADR's own acceptance (Task 10B) constitutes a decision that these risks are acceptable for production — only that they are out of scope for the architectural contracts those two ADRs ratify.

1. **Dispatch's Proposal-mutation endpoints have no authentication.** `POST /v1/proposals` (create), `/accept`, `/decline`, `/lapse` all require neither a session token nor an owner credential — confirmed by reading `ProposalController.kt` in full (Readiness Review Section 3.3). Only `GET /v1/proposals?driverId=` is authenticated (`ADR-060`).
2. **No service-to-service (backend-to-backend) authentication mechanism exists anywhere in this codebase.** An exhaustive search found exactly one inter-module REST client (`passenger-experience`'s `OrderManagementRestClientConfiguration.kt`), itself unreachable dead code (Section 2), and it sends no credential of any kind. This is not a gap in one place — it is the total absence of a mechanism `ADR-062`'s own chosen contract (event-fed projection) was specifically designed to avoid needing.
3. **Production RabbitMQ uses a shared `guest`/`guest` credential on the default vhost `/` for every module** — no per-module broker-level access control, confirmed directly against `dispatch/src/main/resources/application.yml` (and, by the same configured pattern, every other RabbitMQ-using module). Trust that a given exchange's declared publisher is the only real publisher rests entirely on which code is actually deployed, not on any broker-enforced boundary. `ADR-062`'s new events inherit this exact, already-accepted risk model — the same one `DriverAvailabilityChanged` already operates under today — not a new or weaker one.
4. **These are implementation/production-hardening blockers, not architecture blockers**, and must be tracked separately from First Refusal's own implementation (this task's own instruction: "do not mix security remediation into First Refusal implementation"). `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` Section 6 carries this same register forward as the canonical pointer for any future security-hardening task.

**Unaffected, confirmed clean:**
- `PrimaryDriverRecord`'s minimum-field design (Section 7) is itself the primary privacy control this document specifies — no passenger-identifying data beyond an opaque reference ever leaves Passenger Experience, and `ADR-034`'s own Forbidden-inputs list (profile/rating/reputation data) is unaffected and unweakened by `ADR-062` (reconciled directly in `ADR-034` itself, Task 10B).
- No location data, payment data, or reputation data is touched by anything in this document (Sections 14–15) — each remains exactly as absent from the domain model as `docs/PIOS_POST_REMEDIATION_BASELINE.md` already found.
- Session authentication (`ADR-055`) is unaffected — `ADR-062`'s chosen contract specifically avoids needing any new service-to-service authentication path, unlike the rejected Option A.

## 18. Observability

**TARGET, not implemented:** the Owner Control Center's existing `EventFeed` (`docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 16) should extend to surface `PrimaryConnectionDesignated`/`Cleared` and (once `ADR-063` is implemented) `TripArrived`/`TripStarted`/`TripCompleted` — no new observability *mechanism* is proposed, only new event types flowing through the one that already exists. Coordinator visibility into fallen-through First Refusal orders (Section 11) similarly reuses `todayData.ts`'s own existing fan-out, not a new dashboard.

## 19. Migration Implications

**Items 1–3 performed by Task 10B; items 4–5 remain TARGET, gated on actual implementation:**

1. ~~`INTERFACE_CONTRACTS.md` Section 5/7 — new "Passenger Experience → Dispatch" contract entry, two new event rows.~~ **Performed (Task 10B)** — see `INTERFACE_CONTRACTS.md` Sections 5, 7, and the new Section 17 addendum.
2. ~~`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 — needs a note recording the narrow supersession `ADR-062` creates.~~ **Performed (Task 10B)** — original text preserved, superseding note appended.
3. ~~`ADR-034` Part 2 — needs a note distinguishing PCR-as-first-refusal-trigger (now ratified, narrowly) from PCR-as-ranking-input (still unratified).~~ **Performed (Task 10B)** — both the Future-candidate and Forbidden-inputs entries received reconciling notes.
4. `EVENT_CATALOG.md` Section 6, `INTERFACE_CONTRACTS.md` Sections 5/7 — `Assignment*` → `Trip*` rename, once implemented, with the dual-publish window. **TARGET notes added (Task 10B) without performing the rename** — `EVENT_CATALOG.md` Sections 6/9 and `INTERFACE_CONTRACTS.md` Section 18 now record the ratified target explicitly, distinguished from current runtime behavior; the actual rename remains gated on implementation, per `ADR-063`'s own Constraints.
5. `MODULE_STRUCTURE.md` Section 3 — no change required; Dispatch's own entry already covers the pre-commitment aggregate broadly enough to encompass Trip as an internal addition (confirmed by re-reading that section during this task — it does not enumerate Dispatch's own aggregates exhaustively).

## 20. Open Architectural Questions

**OPEN**, consolidated, none answered by this document:

- The exact First Refusal response-window duration (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4, `ADR-062`).
- The exact command/endpoint shape for "propose, honoring First Refusal" (`ADR-062`'s own explicit non-scope).
- Duplicate First Refusal / idempotency mechanism specifics (Section 8, failure mode 8).
- The race between a late Primary Driver acceptance and a Coordinator-initiated fallback proposal (Section 8, failure mode 10).
- Mid-Trip cancellation (Section 13, `ADR-063`).
- Whether/how `POST /v1/proposals`'s own missing authentication should be resolved, and on what timeline relative to First Refusal's own implementation (Section 17).
- Whether Passenger Experience's own dead order-submission code path (Section 2) should be removed, reactivated, or left as-is — not addressed by this task, named here only because it was newly discovered while tracing the current flow.
- **New, Task 11A**: Order Management's own event-driven "an order has been assigned" signal is currently non-functional end to end — `AssignmentAccepted` is unpublished (Section 9), and `OrderAssigned`, which is actually published, has no consumer in Order Management. Whether and how to close this is a separate, unscoped question — not addressed by this document, `ADR-062`, or `ADR-063`, and explicitly not to be fixed as a side effect of implementing Trip (`docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md` Section 7).

## 21. Implementation Preconditions

**CRITICAL IMPLEMENTATION GATE — updated by Task 10B, 2026-09-03. This is now the authoritative gate for this document; `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` Section 8 carries the consolidated, canonical status forward.**

1. ✅ **`ADR-062` is Accepted.** Reviewed against `ADR-034`, `MODULE_STRUCTURE.md`, `ADR-005`/`009`/`019`, `ADR-054`, `ADR-055` — no material defect found (`docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md` Section 1). Status flipped 2026-09-03.
2. ✅ **`ADR-063` is Accepted, after the event-trigger correction — corrected a second time, Task 11A.** First corrected (Task 10B) from "`Assignment.status == ACCEPTED`" to the `AssignmentAccepted` event/outcome. Task 11's own pre-implementation call-graph inspection then found that correction incomplete — `AssignmentAccepted` is itself never published by any live production path — and Task 11A corrected the trigger a second time, to `OrderAssigned`, the event both real Assignment-creation paths actually, reliably publish. Both corrections preserved in `ADR-063`'s own Status section; see `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md` for the full record.
3. ✅ **Contradictory interface/product documentation is reconciled.** `INTERFACE_CONTRACTS.md` (Sections 5, 7, 17, 18), `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2, `ADR-034` Part 2 (both Future-candidate and Forbidden-inputs entries), `EVENT_CATALOG.md` (Sections 6, 8, 9), and `docs/README.md`'s ADR table were all updated — original text preserved throughout, superseding notes appended, per `CLAUDE.md`'s "Never Delete Documentation."
4. ✅ **Primary Driver ownership is unambiguous.** Section 5 — unchanged, already satisfied.
5. ✅ **First Refusal contract is defined.** Section 6, `ADR-062` — unchanged, already satisfied.
6. ✅ **Failure behavior is defined.** Section 8 — unchanged; explicit OPEN markers remain on specific *parameters* (response-window value, endpoint shape), which is a distinct, narrower kind of openness than an undefined *mechanism*, and does not block this gate.
7. ✅ **Order / Assignment / Trip boundaries are defined.** Section 13, `ADR-063` — now with the corrected trigger.
8. ✅ **Network Management boundary is defined.** Section 12 — unchanged, confirmed still no new ADR needed (`ADR-037` remains authoritative).
9. ✅ **No existing ratified decision is silently contradicted.** Every supersession is now explicit, citable, and traceable to `ADR-062`/`ADR-063` — no document disagrees with another without saying so.

**Overall gate status: OPEN.** All nine architectural preconditions are satisfied as of this update. **This is a narrower claim than "implementation may proceed unconditionally"** — Section 17's security risk register (unauthenticated Proposal mutations, no service-to-service auth, shared RabbitMQ credential) remains a **separate, still-open production-hardening concern**, deliberately not one of the nine gate items above (per this task's own instruction not to mix security remediation into First Refusal implementation), and the response-window value/endpoint shape (Section 20) remain genuinely undecided parameters a future implementation task must still resolve before writing the specific code paths that need them. See `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` for the full ratification record and the exact next implementation task this gate now permits.

---

## Final Report

**Files created:**
- `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md`
- `docs/ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md`
- `docs/ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md`

**Files modified:** none. `INTERFACE_CONTRACTS.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, `ADR-034`, `EVENT_CATALOG.md`, `MODULE_STRUCTURE.md`, `docs/README.md` are all named as needing a future update (Section 19) but none was touched by this task, per its own explicit instruction.

**Files deleted:** none.

**ADRs created and their numbers:** `ADR-062` (Primary Driver / First Refusal — Passenger Experience → Dispatch Contract), `ADR-063` (Trip as a New Dispatch-Owned Aggregate). Both Status: Proposed. A third candidate ADR ("Network Management boundary") was evaluated and **not created** — `ADR-037` already covers it in full; this task's own analysis (Section 12) found no new decision to ratify there.

**Tests/checks executed:** none — documentation-only task, no code run.

**Production databases touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO

**Unresolved architectural questions:** enumerated in full in Section 20 above.
