# D-08 — Handoff Observation, Implementation-Ready Specification

**Status: OBSERVATION-ONLY FOUNDATION.** This specification describes how to build the *observation* infrastructure D-08 requires — data collection, aggregation, and an owner-facing view — so that a future cap decision can be evidence-based. **It does not activate any enforcement.** No `HandoffApplicationService` method rejects a Handoff because of anything this specification describes. Nothing here changes D-01–D-07.

This document builds on, and does not reopen: `docs/PIOS_D08_HANDOFF_CAP_RECONCILIATION.md`, `docs/PIOS_D08_DECISION_ANALYSIS.md`, `docs/PIOS_D08_FINAL_DECISION_LOCK.md`. Where this specification needed to verify a claim from those documents against the current codebase before building on it, that verification was redone here directly (not assumed) — see inline citations.

---

## 0. Scope

**In scope**: read-only observation of Handoff usage by committing driver, an owner-facing API/view, a documented (but disabled) future cap interface, and the configuration/data-model groundwork a future cap would need.

**Out of scope, explicitly**: any enforcement; any score, tier, rank, or quality index; any passenger- or peer-driver-facing surface; any change to `Assignment`, `Trip`, `Handoff`, or any D-07 invariant; resolution of the `repeatClientsCount`/`driver_client_rides` drift (`docs/POST_D07_DRIVER_ATTRIBUTION_DRIFT.md`, a separate document — this feature does not read those metrics at all, see §11).

---

## 1. Domain Semantics

**Handoff observation = the recorded, factual history of a committing driver's own use of the Handoff mechanism — never an assessment of that driver's quality, reliability, or trustworthiness.**

Re-verified directly against the current implementation (`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Handoff.kt`, `HandoffStatus.kt`, `HandoffApplicationService.kt`, `V22__handoff.sql`) rather than assumed from the prior three documents:

**`Handoff`'s own five states** (`HandoffStatus.kt`, confirmed unchanged): `PROPOSED → SUBSTITUTE_ACCEPTED → COMMITTED`, with `REFUSED` and `WITHDRAWN` as the two non-terminal-positive exits, reachable as follows (`Handoff.kt:60-122`):
- `PROPOSED → SUBSTITUTE_ACCEPTED` (`acceptSubstitute()`)
- `SUBSTITUTE_ACCEPTED → COMMITTED` (`commit()`, passenger's constitutive consent — the *only* transition that changes `Trip.executingDriver`)
- `PROPOSED → REFUSED` or `SUBSTITUTE_ACCEPTED → REFUSED` (`refuse()` — collapses two distinct real-world actors, passenger refusal and substitute decline, into one status; see the table below for why this matters to observation)
- `PROPOSED → WITHDRAWN` or `SUBSTITUTE_ACCEPTED → WITHDRAWN` (`withdraw()`, original driver only; unconditionally forbidden from `COMMITTED`)

**`TERMINATED` and `COMPLETED` are not Handoff states** — they belong to `TripStatus` (`TripStatus.kt`, confirmed: `CREATED, ARRIVED, IN_PROGRESS, COMPLETED, TERMINATED`), a different aggregate's own lifecycle. A Handoff and its underlying Trip resolve independently: `HandoffApplicationService.propose/acceptSubstitute/consent` each re-validate `trip.status ∈ {CREATED, ARRIVED}` under lock (`HandoffApplicationService.kt:90-91,131-132,162-163`), but nothing about a later Trip termination or completion retroactively changes what a Handoff's own row already recorded.

**Fact vs. intent — the precise line, restated with the underlying schema in view:**

| Lifecycle point | `handoffs` row state | Is this "the Handoff was used"? |
|---|---|---|
| **PROPOSED**, never accepted by substitute | `status = PROPOSED`, `substitute_accepted_at = NULL` | **No — intent only.** The committing driver named a substitute; nothing about a second party's own participation exists yet. |
| **SUBSTITUTE_ACCEPTED** | `status = SUBSTITUTE_ACCEPTED`, `substitute_accepted_at` set | **Yes — the fact begins here.** The substitute has affirmatively agreed to take over. This is the first point at which "committing driver routed work to another person" is actually true. |
| **PASSENGER_CONSENTED / COMMITTED** | `status = COMMITTED`, `resolved_at` set (superset of the row above — `commit()` requires `SUBSTITUTE_ACCEPTED` first, `Handoff.kt:82`) | **Yes — the realized transfer.** `Trip.executingDriver` has changed. |
| **REFUSED reached from SUBSTITUTE_ACCEPTED** (passenger refusal) | `status = REFUSED`, `substitute_accepted_at` set | **Yes.** The substitute already engaged; the passenger's later "no" does not erase that fact. |
| **REFUSED reached from PROPOSED** (substitute decline) | `status = REFUSED`, `substitute_accepted_at = NULL` | **No.** No second-party engagement ever occurred. |
| **WITHDRAWN reached from SUBSTITUTE_ACCEPTED** | `status = WITHDRAWN`, `substitute_accepted_at` set | **Yes**, by the same rule. |
| **WITHDRAWN reached from PROPOSED** | `status = WITHDRAWN`, `substitute_accepted_at = NULL` | **No.** |
| Underlying **Trip TERMINATED** (D-01), independent of the Handoff's own state | No change to the Handoff row | **Does not change the answer above either way.** Termination is orthogonal to whether a Handoff reached substitute acceptance. |
| Underlying **Trip COMPLETED** | No change to the Handoff row | **Does not change the answer above either way**, for the same reason. |

**Governing rule, restated as one predicate**: `substitute_accepted_at IS NOT NULL` is the single fact that distinguishes "this Handoff was used" from "this Handoff was merely proposed." This requires no new column — it is already present on every row in `handoffs` (V22).

---

## 2. Attribution

**The observation subject is the committing driver — `Assignment.driver` / `Trip.driver` / `Handoff.originalDriver` (the same immutable field under three names across three aggregates, all confirmed never rewritten by any Handoff operation, `HandoffApplicationService.kt` read directly: no method ever assigns to `Assignment.driver` or `Trip.driver`).**

Not the executing driver (`Trip.executingDriver` / `Handoff.substituteDriver`) — that actor's own execution/earnings attribution is D-08(b)'s already-correctly-implemented, unrelated concern (`docs/PIOS_D08_FINAL_DECISION_LOCK.md` §6). Not the passenger. Not any `Connection`/relationship owner — `Connection` is never read or written by any Handoff operation (`HandoffApplicationService.kt:149-151`, re-confirmed: no `Connection` reference anywhere in the file).

**Where the committing driver's identity is sourced from**: `handoffs.original_driver_reference` (permanent, set once at `propose()` time, `Handoff.kt:139-166`) — never `AssignmentCompleted.driverId` (which is `executingDriver`, per D-07, and is the wrong field for this purpose entirely; observation does not touch the `AssignmentCompleted` event or driver-management's own metrics at all, see §11).

---

## 3. Data Source

Re-verified directly, not assumed:

**Already sufficient, no new business entity required:**
- **`handoffs` (V22)** — confirmed to already be a complete, append-only, permanent audit trail: `id, assignment_id, order_reference, original_driver_reference, substitute_driver_reference, passenger_reference, status, proposed_at, substitute_accepted_at, resolved_at, is_test`. No row is ever deleted or overwritten (`V22__handoff.sql:11-13`: "historical (REFUSED/WITHDRAWN) rows are never deleted or overwritten — each Handoff attempt is its own permanent audit record"). **This table already is the Handoff-history aggregate.** No new domain aggregate, event, or table is needed to *record* observable facts — they are already recorded.
- **`assignments`** — needed for the denominator (§6): `assignments.driver_reference = committing driver` identifies which Assignments belong to the committing driver at all.

**Implementation update (2026-09-18, recorded after the fact against real code and real Postgres testing — nothing above this line was wrong, only incomplete):** `assignments` was confirmed to carry **no creation-timestamp column of its own** — `V1__initial_schema.sql` through `V11__add_proposal_and_assignment_is_test.sql` add only `status_changed_at`/`arrived_at`/`started_at`/`completed_at`, each `null` until its own transition, none of them "when was this Assignment created." Adding one would have meant changing `Assignment`'s own primary constructor — a pre-D-07, pervasively-constructed aggregate, a materially larger change than this feature's own scope. The implementation instead sources an Assignment's own creation instant from **`dispatch_outbox`** (V3): every Assignment's creation publishes exactly one `OrderAssigned` record, keyed by `aggregate_id = assignment.id`, and that table's rows are never deleted (only `published_at` is ever set). `PostgreSQLHandoffObservationRepository` joins `assignments` to `dispatch_outbox` (`event_type = 'OrderAssigned'`) to resolve each Assignment's own window-relevant timestamp — no schema change to `assignments` was made. The in-memory adapter (`InMemoryHandoffObservationRepository`), having no outbox equivalent, keeps its own small creation-time record instead, per its own KDoc. **The metric's data source is therefore `handoffs` + `assignments` + `dispatch_outbox`, not `handoffs` + `assignments` alone** — this corrects the sentence above and Acceptance Criterion §13 item 1/8 accordingly.

**Missing, and genuinely new work (not built by this specification, described for the implementer):**
- **No index on `handoffs.original_driver_reference`** (only `assignment_id` is indexed, `V22__handoff.sql:28`) — any per-committing-driver query today is a full table scan.
- **No `HandoffRepository` method exists for "find by original driver"** — only `findBySubstituteDriver`, `findByAssignmentId`, `findById`, `save` (`HandoffRepository.kt`, re-read directly: confirmed no `findByOriginalDriver`).
- **No incrementally-maintained read-model/rollup** exists for Handoff activity, unlike `driver_milestones` in driver-management, which is updated on each event. Whether the owner view (§9) computes on demand from `handoffs`+`assignments`+`dispatch_outbox` directly, or is served by a new incremental rollup table, is an implementation-cost tradeoff, not a semantic one — not decided here (§12's test plan covers both possibilities without requiring either).
- **`orders`/`trips`/`driver-management`/other event streams are not needed** for this metric at all — the metric (§6) is defined entirely from `handoffs` + `assignments` + `dispatch_outbox`, all already Dispatch-owned, requiring no cross-module read.

---

## 4. Window (Configuration, Not Product Policy)

Restated with implementation-level precision, per `docs/PIOS_D08_FINAL_DECISION_LOCK.md` §3's own derivation.

**Implementation correction (2026-09-18) — read this before the paragraph that follows.** The bucket description originally proposed here — "1 ISO calendar week (Monday 00:00 UTC boundary)," i.e. `windowEnd` aligned to the start of the current ISO week — was implemented, tested against real PostgreSQL data (`HandoffObservationPostgreSQLTest`), and found to hide exactly the activity this tool exists to surface: any Assignment created since the most recent Monday would be invisible to observation until the current week fully elapsed. This is not a property any upstream document (`docs/PIOS_D08_FINAL_DECISION_LOCK.md`, the Decision Brief, OQ-7) required — it was this specification's own added interpretation, mirroring `RideStreakCalculator`'s "complete weeks only" discipline for a different purpose (consecutive-week streak counting) that does not transfer to a trailing-rate metric. **What is actually implemented, and what this section now describes, is a plain trailing window: `windowEnd = now` (the query's own current instant, no calendar alignment), `windowStart = windowEnd - (windowWeeks × 7 days)`, i.e. `[now − N weeks, now)`.** "Week" survives only as the *unit of measure* for the configuration value (§ below), not as a calendar-alignment rule. See `HandoffObservationQueryService`'s own KDoc for the full reasoning and the test that surfaced it.

**Bucket/granularity: the week (7 days) is the unit of measure for the trailing window length** — not a calendar-alignment boundary. Evidence for "week" specifically as the unit: `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:75`, OQ-7 Option B's own illustrative text — "N per driver **per week**" — the only place in the entire evidentiary record naming a time unit for this concept at all. `RideStreakCalculator`'s own week-boundary *logic* (driver-management, `current_streak_weeks`) was the original inspiration for calendar alignment specifically, and is why "week" as a duration unit still carries a real, running precedent in this codebase even though the alignment idea itself did not transfer.

**Window length is a configuration value, not a ratified business threshold**, mirroring the precedent this codebase already uses for exactly this situation — `ProposalLapseApplicationService.timeoutMinutes`, whose own code comment states plainly: *"not a ratified business rule — `ADR-051`/`ADR-052`."*

**Configuration contract:**

| Property | Value |
|---|---|
| Configuration key | `pios.handoff.observation.window-weeks` (matching the existing `pios.<feature>.<property>` convention actually used in `dispatch/src/main/resources/application.yml` — e.g. `pios.proposal.lapse.fixed-delay-ms`, `pios.outbox.relay.fixed-delay-ms` — neither of which repeats the owning module's own name; an earlier draft of this table proposed `pios.dispatch.handoff.observation.window-weeks`, which does not match that convention and was not what was implemented) |
| Where stored | `application.yml` / environment override, `dispatch` module only — the same mechanism every other `pios.*` value in this codebase already uses; **not** a database row, **not** editable through any API |
| Default | a non-binding operational starting point — **4** (weeks, i.e. a trailing 28 days) — offered as a safe, reasonable default, explicitly not asserted as a business-correct number; no evidence in the existing record justifies any specific figure (per `docs/PIOS_D08_FINAL_DECISION_LOCK.md` §3, restated: inventing one here would repeat the exact mistake `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option B's own risk note already names) |
| Units | whole weeks (7-day multiples), applied as a trailing duration ending at query time — not calendar-aligned |
| Validation | must be a positive integer; a sane upper bound (e.g. ≤ 52) should reject configuration nonsense (an unbounded window defeats the purpose of a window at all) without asserting any *business* meaning for that bound — it is an input-sanity guard, not a policy |
| Operational override | standard Spring `@Value`-style externalized configuration (environment variable or `application.yml`), read once at process start, exactly like `pios.proposal.lapse.fixed-delay-ms` and `pios.owner.auth.window-ms` already are |
| Passenger/driver API access | **none, by design.** No `Handoff`, `Trip`, `Assignment`, or any passenger/driver-facing endpoint reads or exposes this value. It is read only by the owner-observation query path (§9) at computation time. |

---

## 5. Evidence Gate

**Observation-before-cap is expressed architecturally as the *absence of any code path capable of denying a Handoff*, not as a runtime flag.**

This is a deliberate structural choice, not merely a phased rollout: a boolean "cap enabled/disabled" configuration flag would itself risk becoming exactly the kind of quietly-tunable, undocumented threshold this whole initiative exists to avoid inventing without evidence. Instead:

- **Today, and until a separate, future decision changes it**: `HandoffApplicationService.propose()` (and every other method) contains no reference to any cap, threshold, or policy object of any kind. Observation (§6/§9) runs entirely as a *read* path over `handoffs`/`assignments` — it has no write access to, and no call-site inside, `HandoffApplicationService`.
- **The future cap interface (§7) is specified, not created.** No Kotlin file is added by this specification. When a future initiative does wire it in, the *default* implementation of that interface is specified to be a permanent no-op ("always allow") — mirroring this codebase's own existing pattern for exactly this shape of optional port (`NoOpOrderGuard`, `NoOpTransactionRunner`, `NoOpHandoffRepository` all follow this same "always-safe default, explicit override required" shape today).
- **Activating any non-no-op policy is a distinct, later, structural change** — swapping which implementation is wired into `HandoffApplicationService`'s constructor — which, per `CLAUDE.md`'s own "Never Change Architecture Without an ADR" rule, requires its own ADR and its own explicit Product Owner authorization, not a configuration toggle. This is what "sufficient evidence" gates in practice: not a number crossing a threshold automatically, but a human, architecturally-visible decision to wire in a real policy at all.

**This does not become a rating.** The gate produces no per-driver number that is compared, ranked, or exposed anywhere except the owner-only view (§9), and even there, every figure is a raw, explainable count (§10) — nothing is normalized into a score, percentile, or tier.

---

## 6. Metric

**Definition** (restated precisely from `docs/PIOS_D08_FINAL_DECISION_LOCK.md` §2, with numerator/denominator now made exact):

> For a given committing driver and a given window: what share of that driver's own commitments (Assignments) had at least one Handoff reach `SUBSTITUTE_ACCEPTED` or beyond — plus, separately, how concentrated the recipients of those transfers are.

**Numerator**: count of **distinct `Assignment` rows** where `assignments.driver_reference = <committing driver>`, the Assignment's own creation instant falls in `[windowStart, windowEnd)` (§4's own 2026-09-18 correction — resolved via `dispatch_outbox`'s `OrderAssigned` record, `assignments` itself carrying no such column), and **at least one** `handoffs` row exists for that `assignment_id` with `substitute_accepted_at IS NOT NULL` (regardless of that Handoff's final `status`). Counting by distinct Assignment, not by raw `handoffs` row, is deliberate: a driver who proposes, is refused, and legitimately re-proposes another Handoff for the *same* ride (an explicitly supported, tested flow — `HandoffApplicationServiceTest.kt`, "re-proposal after withdrawal") must not have that single ride counted twice toward the rate.

**Denominator**: count of **all** `Assignment` rows where `assignments.driver_reference = <committing driver>` and the same window-membership test above holds — regardless of that Assignment's own outcome (completed normally, terminated, still open). A commitment is a commitment the moment it exists; how it later resolves does not remove it from "how much of this driver's own work did this window cover."

**Secondary, non-ratio figure — recipient concentration**: over the same numerator set, grouped by `handoffs.substitute_driver_reference`, report the count of **distinct substitutes used** and the **share of transfers going to the single most-frequent substitute**. This is reported as a plain count/share, never combined into the primary rate, and never compared across drivers in any ranked list (§9).

**Edge cases, explicitly resolved:**

| Case | Numerator | Denominator | Rationale |
|---|---|---|---|
| **Cancelled/terminated before any Handoff was ever proposed** | Not counted (no Handoff row exists) | **Counted** | Still a real commitment this driver took in the window. |
| **Passenger refusal** (Handoff reached `SUBSTITUTE_ACCEPTED` then `REFUSED`) | **Counted** | Counted (once, via the underlying Assignment) | Substitute engagement already happened; the passenger's later refusal doesn't erase it (§1). |
| **Substitute refusal/decline** (`PROPOSED → REFUSED`, never accepted) | Not counted | Counted | No second-party engagement occurred (§1). |
| **Withdrawal before substitute acceptance** | Not counted | Counted | Same as substitute refusal. |
| **Withdrawal after substitute acceptance** | **Counted** | Counted | Substitute already engaged (§1). |
| **Underlying Trip terminated (D-01)**, independent of the Handoff's own status | Unaffected — follows the Handoff's own `substitute_accepted_at`, per the row above | Counted | Termination is orthogonal (§1). |
| **Handoff completed (`COMMITTED`)** | **Counted** | Counted | The realized case; a strict superset of "reached `SUBSTITUTE_ACCEPTED`." |
| **Empty history** (driver has zero Assignments in the window) | N/A | 0 | Reported as **"insufficient data," not `0%`** — a 0-over-0 ratio must never be silently rendered as "this driver never hands off," which would be a false, ranking-adjacent claim about a driver who may simply have had no rides in the window at all. |

---

## 7. Future Cap Interface (Described, Not Implemented)

No Kotlin file is created by this specification. The shape below is documentation only, mirroring `ADR-034` Part 3's own precedent for describing a not-yet-built port ("illustrative only, not a code decision — no interface is created by this ADR").

```kotlin
// ILLUSTRATIVE ONLY — not created by this specification.

interface HandoffCapPolicy {
    /**
     * Evaluated once, inside HandoffApplicationService.propose(), after the
     * committing driver and eligibility are known, before Handoff.propose()
     * is called. Reads only; never mutates anything.
     */
    fun evaluate(committingDriver: DriverReference, at: Instant): HandoffCapDecision
}

sealed class HandoffCapDecision {
    object Allow : HandoffCapDecision()
    data class Deny(val reasonCode: String, val explanation: String) : HandoffCapDecision()
}

// The only implementation wired anywhere today, and until a separate,
// future, ADR-authorized decision replaces it:
object NoOpHandoffCapPolicy : HandoffCapPolicy {
    override fun evaluate(committingDriver: DriverReference, at: Instant) = HandoffCapDecision.Allow
}
```

**Input**: the committing driver's own reference and the current instant — nothing about the substitute or passenger, consistent with §2's attribution and §11 of `docs/PIOS_D08_FINAL_DECISION_LOCK.md`'s own confirmation that `propose` is the only defensible enforcement point (evaluated *before* any other party is engaged).

**Output**: a closed, two-case decision — `Allow`, or `Deny` carrying a **reason code** (a stable, machine-readable string, e.g. `HANDOFF_CAP_EXCEEDED` — not a numeric score) and a human-readable explanation. No third case, no numeric confidence, no partial/graduated result — this keeps the interface incapable of expressing anything score-shaped even if a future implementation wanted to.

**Auditability**: a future `Deny` decision would need to be traceable back to the exact observation figures (§6/§10) that produced it — the interface's own `evaluate()` call is specified to read from the same, already-auditable observation data this specification builds (§3/§6), not from any separate, opaque state.

**Wiring point, described only**: `HandoffApplicationService.propose()` would call `handoffCapPolicy.evaluate(assignment.driver, now)` immediately after loading the Assignment and before `Handoff.propose()` is invoked; a `Deny` would need its own distinct exception type (not `IllegalStateException`, which `propose()` already uses for "an active Handoff already exists" and must remain distinguishable), so `HandoffController` can map it to its own distinct HTTP response, separately from existing 400/404/409 cases. **None of this is implemented here.**

---

## 8. Concurrency

Re-verified directly (`backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLOrderGuard.kt`, full file read): `OrderGuard.lock(order)` performs `INSERT ... ON CONFLICT DO NOTHING` then `SELECT ... FOR UPDATE` on `dispatch_order_guards`, keyed strictly by `order_reference`. It is **order-scoped**, not driver-scoped, and `HandoffApplicationService` uses it, unmodified, in all five of its methods.

**For this observation-only phase, no driver-level lock is needed.** Observation is a read path; it mutates nothing. A dashboard reading a per-driver aggregate that is occasionally one event behind, because of a genuine race between two concurrent Handoff operations for the same driver on two different orders, is not a correctness defect — it is the same eventual-consistency tolerance every other read-model in this codebase (`driver_milestones`, `driver_client_rides`) already has.

**A future active cap would need a new, driver-scoped serialization primitive.** `OrderGuard`'s own order-scoping is structurally insufficient for a driver-level check: two Handoffs proposed for the same committing driver on two different orders, at the same moment, each lock only their own order and proceed fully independently — an atomic "would this exceed the driver's own cap" read-then-write would race under exactly this condition. If a future cap is authorized (§7), it would need a `DriverGuard`, structurally identical to `PostgreSQLOrderGuard` but keyed by `driver_reference` instead of `order_reference` (a new `dispatch_driver_guards` table, same `INSERT ... ON CONFLICT DO NOTHING` + `SELECT ... FOR UPDATE` shape). **Not created here** — named as a documented prerequisite for whichever future work actually wires in a non-no-op `HandoffCapPolicy`.

---

## 9. Owner View

**Authorization — reuses an already-ratified, already-implemented, same-module precedent, not a new mechanism.**

Re-verified directly: `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/OwnerCredentialGate.kt` already exists in the `dispatch` module (one of six per-module replicas, `ADR-044`), and `dispatch` already gates a real business query endpoint with it today — `GET /v1/proposals?driverId=...` and `GET /v1/proposals?orderId=...`, per `ADR-060` (Order Query Authorization) and `ADR-066` (Proposal Participant Authorization), confirmed directly from `frontend/src/pages/OwnerControlCenter/todayData.ts`'s own KDoc: *"ADR-060 (Order Query Authorization): `GET /v1/orders` and `GET /v1/proposals?driverId=...` now require a credential... `Authorization: Basic` header"*. **A new `GET /v1/handoffs/observation?driverId=` endpoint, gated identically, is a direct, same-module, same-pattern extension — not a new authorization mechanism.**

**Prerequisite — satisfied (2026-09-18).** Per `CLAUDE.md`'s "Never Change Architecture Without an ADR," formally authorizing owner-Basic-auth for this *specific new* endpoint required the same narrow, dated, in-place ADR amendment pattern already used repeatedly in this engagement (e.g. `ADR-054`'s two dated supersession pointers). `docs/ADR/ADR-060-Order-Query-Authorization.md` now carries a dated "Amendment pointer (2026-09-18, per `ADR-015`)" immediately after Decision 4, extending that Decision's own "Dispatch-owned business query endpoint, gated by Dispatch's own `OwnerCredentialGate`" pattern to `GET /v1/handoffs/observation?driverId=` specifically — narrower than Decision 4's own dual-scheme (`Bearer`-or-`Basic`) rule, since this endpoint accepts `Basic` only. No other part of ADR-060 was touched.

**Proposed contract:**

```
GET /v1/handoffs/observation?driverId=<committingDriverId>
Authorization: Basic <owner credential>

200 OK
{
  "driverId": "d-123",
  "windowStart": "2026-08-25T00:00:00Z",
  "windowEnd": "2026-09-22T00:00:00Z",
  "windowWeeks": 4,
  "totalCommitments": 42,
  "handoffsWithSubstituteAcceptance": 6,
  "rate": 0.142857,
  "distinctSubstitutesUsed": 2,
  "topSubstituteShare": 0.833333,
  "eventBreakdown": {
    "proposedOnly": 1,
    "substituteAccepted": 1,
    "committed": 3,
    "refusedAfterAcceptance": 1,
    "withdrawnAfterAcceptance": 1,
    "declinedBySubstitute": 0,
    "withdrawnBeforeAcceptance": 0
  }
}
```

`rate`/`totalCommitments = 0` → `rate: null`, with the response marked (e.g. `"insufficientData": true`) rather than a misleading `0`.

The `windowStart`/`windowEnd` pair above is illustrative only, not evidence of calendar alignment — per §4's own correction, `windowEnd` is the query's own current instant (any time of day), and `windowStart` is exactly `windowWeeks × 7` days earlier; the two happening to land on midnight in this example is coincidental, not a rule.

**Visibility, restated as a hard boundary, not a preference**: this endpoint is owner-only. No passenger-facing endpoint, no `GET /v1/drivers/{id}` field, no `DriverTrustIndicator`-adjacent component, and no other driver's own session may ever read another driver's observation data. It is never aggregated into any list that ranks or orders drivers against each other — even the owner's own view is scoped to one `driverId` at a time, by design, so nothing about this contract enables a side-by-side comparison UI without a further, separate decision.

**If `OwnerControlCenter`'s existing architecture is reused** (as instructed): the frontend pattern is already established in `frontend/src/pages/OwnerControlCenter/todayData.ts` — a typed fetch function taking the stored `OwnerCredential`, sending `Authorization: toBasicAuthorizationHeader(credential)`, following the exact shape `loadOrders`/`loadProposalsForDriver` already use. A new `loadHandoffObservation(credential, driverId)` function of the same shape, and a new card component (mirroring `StatusCard`/`TodayCard`) would be the natural extension — **not built here**, since no frontend file may be touched by this task.

---

## 10. Auditability

**No opaque numbers.** Every figure in §9's response is one of: a direct count over `handoffs`/`assignments`/`dispatch_outbox` rows (auditable by re-running the defining query, §6), or a ratio of two such counts. Nothing is smoothed, weighted, decayed, or otherwise transformed in a way that would make the number not reconstructible from source rows.

For any given aggregate response, the following must be independently reconstructible by querying `handoffs`/`assignments`/`dispatch_outbox` directly:
- The exact window (`windowStart`/`windowEnd`, derived from the configured `window-weeks` value at query time — §4).
- The exact count of Handoffs proposed (`status` regardless), the count that reached `SUBSTITUTE_ACCEPTED` or beyond (the numerator, §6), and the count that reached `COMMITTED` specifically — the `eventBreakdown` object in §9's response exists specifically so these are visible individually, not only as one collapsed rate.
- The originating committing driver for every contributing row (`handoffs.original_driver_reference`, permanent and unambiguous, §2).
- Which specific events (by `HandoffId`) contributed — not returned inline in the aggregate response by default (to keep the payload small), but derivable at any time via the already-existing `GET /v1/handoffs?assignmentId=` per contributing Assignment, since nothing in `handoffs` is ever deleted (§3).

This satisfies the task's own auditability requirement without inventing any new audit-log mechanism — the existing, already-permanent `handoffs` table already *is* the audit log; this specification only adds a read/aggregation layer over it.

---

## 11. D-07 Drift — Explicit Non-Scope

`repeatClientsCount` and `driver_client_rides` are **not read, written, or referenced anywhere in this specification.** The metric defined in §6 is built entirely from `handoffs` (Dispatch-owned) and `assignments` (Dispatch-owned) — it has no dependency on `driver-management`'s own metrics at all, and therefore no dependency on the drift those metrics carry.

The forensic analysis of that drift — exact current behavior, the semantics `OQ-3` specifies, why D-07's implementation diverged from it, which code path is responsible, and why it is a D-07 remediation question rather than a D-08 question — is recorded in full in the separate document `docs/POST_D07_DRIVER_ATTRIBUTION_DRIFT.md`, per instruction, and is not repeated here.

---

## 12. Test Plan

Implementation-level, organized by layer. No test file is created by this specification.

**Domain layer**: none required — no domain object (`Handoff`, `Trip`, `Assignment`) changes. Observation is a pure query/aggregation layer over existing, already-tested domain state.

**Query/aggregation layer** (new, e.g. `HandoffObservationQueryTest` or equivalent, in-memory or against `HandoffRepository`/`AssignmentRepository`):
1. **Correct committing-driver attribution** — a Handoff proposed by driver A naming driver B as substitute contributes to A's observation, never B's.
2. **Completed Handoff** (`COMMITTED`) — counts toward the numerator.
3. **Refused Handoff after substitute acceptance** — counts toward the numerator (§1/§6).
4. **Withdrawn Handoff after substitute acceptance** — counts toward the numerator.
5. **Substitute decline** (`PROPOSED → REFUSED`, never accepted) — does **not** count toward the numerator.
6. **Passenger refusal** — same as (3), explicit separate test since it reaches `REFUSED` via a different actor/endpoint (`refuse` vs. `decline-substitute`) even though the resulting `HandoffStatus` is identical.
7. **Withdrawal before substitute acceptance** — does **not** count.
8. **Underlying Trip termination (D-01)**, independent of Handoff state — does not change the numerator/denominator outcome either way (§1/§6).
9. **Window boundary** — an Assignment created exactly at `windowStart` is included, exactly at `windowEnd` is excluded (the half-open `[windowStart, windowEnd)` trailing interval, §4's own 2026-09-18 correction — no calendar/Monday alignment is involved).
10. **Empty history** — a driver with zero Assignments in the window returns `insufficientData`, never a misleading `0`.
11. **Denominator correctness** — an Assignment with multiple re-proposed Handoffs (after a withdrawal) counts once in the numerator (by distinct Assignment, §6), not once per `handoffs` row.
12. **Recipient concentration** — distinct-substitute count and top-substitute share compute correctly against a known, hand-constructed distribution.

**Repository/PostgreSQL layer** (new, e.g. `HandoffObservationPostgreSQLTest`, real database):
13. The same scenarios as (1)–(12) above, against real Postgres, confirming the SQL aggregation (whichever query shape is chosen, §3) produces the same results as the in-memory reference.
14. **Sufficient-evidence boundary** — this is a *process*, not a numeric threshold (`docs/PIOS_D08_FINAL_DECISION_LOCK.md` §4); the corresponding test is not "does the metric cross a number" but **"does the observation API remain purely informational regardless of volume"** — i.e., a driver with an extremely high computed rate still receives `200 OK` with the figures, never a `403`/`409`/any form of denial. This is the concrete, testable expression of "observation never enforces."

**API/authorization layer** (new, e.g. `HandoffObservationControllerTest`, constructed directly, mirroring `HandoffControllerTest`'s own pattern):
15. **Owner authorization** — a correctly-configured, correct owner Basic credential succeeds.
16. **Passenger cannot access** — a passenger session token (or no credential at all) is rejected (401/403), never returns observation data.
17. **Other driver cannot access** — a driver's own session token, including the committing driver's own token, is rejected — this endpoint is owner-only, not self-service (§9; a future self-view, if ever authorized, is a *separate*, differently-scoped endpoint per `docs/PIOS_D08_FINAL_DECISION_LOCK.md` §9 D8-PO-1, not this one).
18. **No ranking** — the endpoint accepts exactly one `driverId` per request and returns no list, no ordering, no comparison field of any kind; there is no code path by which two drivers' figures could be returned side by side from this endpoint.
19. **No financial impact** — no test scenario in this suite ever asserts on `Trip.agreedAmount`, `totalStatedEarnings`, or any billing-adjacent value; observation is confirmed, by the shape of the test suite itself, to never touch money.

**Future cap concurrency tests — described, not implemented, since no active cap exists to test:**
20. Two concurrent `propose()` calls for the same committing driver, on two different orders, both racing a (future, not-yet-built) `HandoffCapPolicy.evaluate()` call — would need to prove that a genuinely enforced cap cannot be bypassed by concurrency, which requires the `DriverGuard` named in §8 to exist first. **Not implementable today**, since neither the policy nor the guard exists; recorded here so the future work that does implement them inherits this test obligation explicitly.

---

## 13. Acceptance Criteria

**"D-08 Observation Foundation implemented"** is true only when all of the following hold:

1. The metric defined in §6 is computable from `handoffs` + `assignments` + `dispatch_outbox` (§3's own 2026-09-18 correction — `dispatch_outbox` supplies the Assignment creation instant `assignments` itself does not carry), with no new domain aggregate or event and no schema change to `assignments`.
2. `GET /v1/handoffs/observation?driverId=` (or an equivalent contract) returns the shape in §9, gated by the same `OwnerCredentialGate` mechanism already used elsewhere in `dispatch`, with its own ADR prerequisite satisfied (§9 — `ADR-060`'s 2026-09-18 amendment pointer).
3. The observation window's length is a `pios.handoff.observation.window-weeks`-style configuration value, not a hardcoded or ratified business number; the window itself is a trailing `[now − windowWeeks × 7 days, now)`, with "week" surviving only as the configuration value's own unit of measure, not as a calendar-alignment rule (§4's own 2026-09-18 correction).
4. No code path in `HandoffApplicationService` (`propose`, `acceptSubstitute`, `consent`, `refuse`, `withdraw`) rejects, delays, or alters its behavior based on any observation figure — enforcement remains entirely absent, not merely disabled by a flag.
5. The `HandoffCapPolicy` interface (§7) exists only in this specification's documentation, or — if implemented in code as a genuinely inert, always-`Allow` port (mirroring `NoOpOrderGuard`) — is wired nowhere into `HandoffApplicationService`'s actual construction path.
6. No passenger-facing surface, no other-driver-facing surface, and no public driver profile ever exposes any figure this specification defines.
7. No response from the observation endpoint contains more than one driver's own figures, and no endpoint returns a list, ranking, or comparison of drivers.
8. Every figure returned by the observation endpoint is reconstructible from `handoffs`/`assignments`/`dispatch_outbox` by direct query, per §10.
9. `repeatClientsCount`/`driver_client_rides` are referenced nowhere in the observation feature's own code path (§11); the drift concerning them is tracked exclusively in `docs/POST_D07_DRIVER_ATTRIBUTION_DRIFT.md`.
10. The test plan in §12, items 1–19, passes; item 20 is explicitly and honestly recorded as blocked on future work, not silently skipped.
11. No D-01–D-07 invariant, test, migration, or API contract is altered.

---

## Verification

- No production code, schema, migration, ADR, test, or frontend file was modified to produce this specification. Only this document and `docs/POST_D07_DRIVER_ATTRIBUTION_DRIFT.md` were created.
- No commit, no push.
