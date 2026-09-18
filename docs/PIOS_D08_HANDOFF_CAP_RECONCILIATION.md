# D-08 — Handoff Cap / Observation Reconciliation

**READ-ONLY RECONCILIATION — NOT AN IMPLEMENTATION AUTHORIZATION**

This document implements nothing, decides nothing, and supersedes nothing. It records what already exists in the repository, what D-08's own source documents already say, and which questions remain genuinely open. No code, schema, migration, ADR, or test is created or modified by this document or by the work that produced it.

---

## 1. Purpose

D-07 (Handoff Protocol) is implemented and merged (HEAD `16ae80be0b59c3dd43138b66715124ee0632a062`). Its own specification carries forward, as a locked principle, an "observation-before-cap" rule: no Handoff frequency/concentration cap may block a driver before sufficient observable behavior exists to justify one. The *mechanism* — what is observed, of whom, over what window, and what (if anything) happens when a threshold is crossed — was explicitly left unbuilt and unspecified, classified as "D-08, not D-07," in `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` §16 and §18.

This document performs the reconciliation the Product Owner requested before any D-08 design or implementation work begins: what already exists that D-08 could use, what the existing product-decision record already says about the shape D-08 may and may not take, and — for every question the task listed — the current evidence, why it matters, and the concrete options, without picking one.

## 2. Current D-07 State

D-07 is closed and pushed at `16ae80b`. Its locked architecture (restated from the task, cross-checked against the code — confirmed accurate):

- One `Order` → one `Assignment` → one `Trip`. Handoff never creates a second `Assignment` or `Trip` (`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Handoff.kt:14-16`: "no second Assignment, no second Trip is ever created by anything in this class or its own application service").
- `Assignment.driver` is fixed at commitment time and never changes. `Trip.executingDriver` (new in D-07, default `= driver`) is the only field a Handoff ever changes (`Trip.kt:108-113`, `:201`).
- Handoff states: `PROPOSED → SUBSTITUTE_ACCEPTED → COMMITTED`, with `REFUSED`/`WITHDRAWN` as the two non-terminal-positive exits (`Handoff.kt:33-168`, `HandoffStatus.kt`).
- Single-hop, structurally: a substitute is never `Assignment.driver`, so a substitute's own `propose` call is rejected by the same authorization anchor that would let the original driver propose (`HandoffControllerTest.kt`, `HandoffConcurrencyPostgreSQLTest.kt` "chained handoff attempt" test).
- Handoff permitted only while `Trip.status ∈ {CREATED, ARRIVED}`; forbidden from `IN_PROGRESS` onward (`HandoffApplicationService.kt:90-91`, `:131-132`, `:162-163`).
- No fee/split/credit/commission; `Trip.agreedAmount` and `Connection` are never read or written by any Handoff operation (`HandoffApplicationService.kt:149-151`, verified directly in the consent method body — no `Connection` or `agreedAmount` reference anywhere in `HandoffApplicationService.kt`).
- Attribution: `Assignment.driver`/`Trip.driver` (original committing driver) is permanent and retains the historical/origination fact; `Trip.executingDriver` (substitute, once committed) becomes the `driverId` reported by `Trip.arrive/start/complete`'s own events, which is what `AssignmentCompleted.driverId` carries forward unchanged in shape (`Trip.kt:143-172`).

## 3. Existing Implementation Evidence

**Domain**: `Handoff.kt`, `HandoffId.kt`, `HandoffStatus.kt`, `HandoffCreated`/`HandoffSubstituteAccepted`/`HandoffCommitted`/`HandoffRefused`/`HandoffWithdrawn` (one file each, internal-only, not wired to the outbox). `Trip.kt`'s `executingDriver` field and `assignExecutingDriver()`.

**Application**: `HandoffApplicationService.kt` — five operations (`propose`, `acceptSubstitute`, `consent`, `refuse`, `withdraw`), each: load → `orderGuard.lock(order)` → re-read under lock → validate → mutate → save. `HandoffRepository.kt` (interface) — `save`, `findById`, `findByAssignmentId`, `findBySubstituteDriver`, `findActiveByAssignmentId` (default, derived). **No `findByOriginalDriver` method exists** — the repository can answer "which Handoffs name driver X as substitute" directly, but not "which Handoffs did driver X originate," without a full scan/filter.

**API**: `HandoffController.kt`, `/v1/handoffs` — propose, accept-substitute, decline-substitute, consent, refuse, withdraw, and `GET ?assignmentId=|substituteDriverId=`.

**Persistence**: `V22__handoff.sql` — `trips.executing_driver_reference` (nullable, additive), `handoffs` table, index on `assignment_id` only (**no index on `original_driver_reference` or `substitute_driver_reference` alone**), unique partial index enforcing at most one non-terminal Handoff per Assignment. Historical (`REFUSED`/`WITHDRAWN`) rows are permanent, never deleted (migration comment, `V22__handoff.sql:11-13`) — the `handoffs` table is already a complete, append-only audit log of every Handoff attempt, not just successful ones.

**Frontend**: `DriverHome.tsx` (original-driver propose/withdraw; substitute accept/decline; committed-handoff ride card), `RideRequest.tsx` (passenger consent/refuse, shown only once the substitute has already accepted). No handoff count, history, or limit is displayed anywhere in the frontend today (confirmed by a repo-wide case-insensitive grep for "handoff" across `frontend/src` — matches exist only in these two pages and their test files).

**Tests**: `HandoffTest.kt`, `HandoffApplicationServiceTest.kt`, `HandoffConcurrencyPostgreSQLTest.kt` (9 concurrency tests), `HandoffControllerTest.kt`. Every "duplicate"/"repeated"/"chained" test proven in this suite is a **per-assignment single-active-Handoff** or **per-driver-authorization** (`Assignment.driver` never changes) invariant. **No test anywhere asserts a per-driver count, a numeric limit, a cooldown, or a time window.** After a withdrawn Handoff, a new one may be proposed immediately for the same Assignment — no rate limit exists even at that narrow scope (`HandoffApplicationServiceTest.kt`, "re-proposal after withdrawal").

## 4. Existing Data Available for D-08

The `handoffs` table (V22) is, today, the only durable, per-attempt record of Handoff activity, and it already carries everything a frequency/concentration observation would need at the row level: `original_driver_reference`, `substitute_driver_reference`, `status`, `proposed_at`, `substitute_accepted_at`, `resolved_at`. It is a genuine audit trail — no row is ever overwritten or deleted, so a REFUSED or WITHDRAWN attempt remains visible forever alongside a COMMITTED one.

What is **not** available without new work:
- **No index** on either driver-reference column — any "how many times has driver X done Y" query today requires a full-table scan of `handoffs` (small today; not necessarily small under real usage).
- **No repository method** to look up Handoffs by `original_driver_reference` at all (`HandoffRepository` only exposes `findBySubstituteDriver`).
- **No aggregate/count view** anywhere over the `handoffs` table.
- **No time-windowed query** exists anywhere in this codebase for any driver metric (see §5) — every existing per-driver counter is a lifetime running total or a point-in-time timestamp, never a rolling window.

## 5. Existing Metrics and Their Current Meaning

Five metrics exist, all owned exclusively by `driver-management`, all governed by **ADR-081 (Driver Business Metrics Carry No Value, ratified by the Product Owner 2026-09-17, "D-02")**:

| Metric | Table | Computed from | Current status per ADR-081 |
|---|---|---|---|
| `completedRidesCount` | `driver_milestones.completed_rides_count` | `AssignmentCompleted.driverId` | private, self-reported; may never become money/tier/priority/eligibility/ranking/trust/penalty/passenger-facing fact without a decision that explicitly supersedes ADR-081 |
| `repeatClientsCount` | `driver_milestones.repeat_clients_count`, derived from `driver_client_rides.ride_count` reaching 2 | `AssignmentCompleted.driverId` + `OrderSubmitted.passengerReference` | same |
| `totalStatedEarnings` | `driver_milestones.total_stated_earnings` | `AssignmentCompleted.driverId` + `statedPrice` | same |
| `driver_client_rides` (table) | itself | same | same |
| `invitedDriversCount` | not persisted; query-time `COUNT(*) WHERE invited_by_driver_id = ?` | `drivers.invited_by_driver_id` (ADR-073) | same |

**All five are cumulative, lifetime, non-windowed counters or query-time aggregates.** None has ever been time-windowed, capped, or used as an input to any decision — ADR-081's own reconciliation (performed 2026-09-17, one day before D-07 shipped) found "zero references to any of the five... anywhere in dispatch, order-management, passenger-experience, or billing" and "no conditional branch, eligibility check, dispatch candidate filter... reads any of the five."

**A material consequence of D-07, not evaluated by ADR-081 (which predates D-07 by one day) and not separately re-examined since:** `AssignmentCompleted.driverId` now equals `Trip.executingDriver`, not `Trip.driver`, once a Handoff has committed (`Trip.kt:143-172`). Since `AssignmentCompletedApplicationService.handle()` in `driver-management` derives `completedRidesCount`, `totalStatedEarnings`, **and** `repeatClientsCount`/`driver_client_rides` purely from `command.driverId` (`AssignmentCompletedApplicationService.kt:65-85`), **all four of these ADR-081-governed metrics now silently shift to the substitute driver whenever a Handoff has been consented for that ride** — not just ride-count/earnings (which D-08(b)'s own recommendation explicitly intended, see §9), but also the repeat-client/CRM relationship metric, which D-08(b)'s own language ("relationship-facing facts credit the driver who committed") arguably intended to stay with the *original* driver. See §15 for the precise tension.

## 6. Observable Behavior Candidates

Directly available today, without new instrumentation, from the `handoffs` table alone:
- A Handoff being **proposed** (`proposed_at`, `original_driver_reference`, `substitute_driver_reference`).
- A Handoff being **accepted by the substitute** (`substitute_accepted_at`).
- A Handoff being **committed by the passenger** (`resolved_at` where `status = COMMITTED`).
- A Handoff being **refused** (by passenger or substitute — both currently collapse to `status = REFUSED`, see §7) or **withdrawn** (by the original driver) (`resolved_at` where `status ∈ {REFUSED, WITHDRAWN}`).

Not directly available without new work: any derived notion of "this driver's rides, what fraction were handed off" (would require joining `handoffs` against Dispatch's own completed-Trip population, which `handoffs` does not itself track) or "this driver executed rides for how many distinct original drivers" (computable from `substitute_driver_reference` grouped by `original_driver_reference`, but requires a new query, not an existing one).

## 7. Frequency Definition Analysis

"Handoff frequency" is not defined anywhere in the codebase or in any ratified document. The task's own framing ("D-08 — Handoff's two binding conditions," `PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766`) says only "an owner-facing view of handoff frequency and concentration per driver" — it does not itself define frequency numerically. Candidate readings, each consistent with the existing `handoffs` schema, none chosen by any document:

- **Raw count of Handoff rows** naming a driver as substitute (or as original) within some period — simplest, directly queryable, but counts REFUSED/WITHDRAWN attempts identically to COMMITTED ones unless explicitly filtered (see §8).
- **Count of COMMITTED Handoffs only** — a narrower, "actually happened" reading.
- **Count relative to the driver's own total ride volume** (a rate, not a raw count) — would require joining against Dispatch's completed-Trip population per driver, which no existing query computes.

## 8. Concentration Definition Analysis

"Concentration" is similarly undefined. Two structurally distinct readings are both plausible from the task's own wording ("PIOS is infrastructure for independent driver businesses... prevent Handoff from becoming a mechanism for systematic uncontrolled concentration of rides through particular drivers"):

- **Concentration of executions onto one substitute** — many different original drivers routing rides to the same substitute (the "informal dispatcher"/"crew" pattern named in `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option A's own risk note, and in `ADR-054` Part 5's "no Team, Fleet, crew, or delegation mechanism"). This reading is measured on the **substitute**.
- **Concentration of one original driver's own rides going to Handoff** — one driver systematically not executing their own commitments, which is a different concern (something closer to "is this actually a driver, or a dispatcher-in-disguise") and is measured on the **original driver**.

Both are legitimate readings of "concentration," and nothing in the source material distinguishes which (or both) D-08 is meant to catch. See §9/Q4 below.

## 9. Attribution Analysis

D-08's own two binding conditions were proposed together in `PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1763-1774`:

> (a) Observation is a shipping condition... (b) Attribution splits by kind (OQ-3 option C): **relationship-facing facts credit the driver who committed; ride-count and earnings credit the driver who drove.**

Per `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md:356`, **D-08(b) is reported as "fully resolved... implemented"** — `completedRidesCount`/`totalStatedEarnings` credit the executing driver (via `AssignmentCompleted.driverId` = `Trip.executingDriver`), and the original driver's own historical/origination fact is preserved regardless (queryable via `GET /v1/handoffs?assignmentId=`, which always returns the Handoff's own permanent `original_driver_reference`).

**What is not resolved, and not addressed by any document:** whether `repeatClientsCount`/`driver_client_rides` (the repeat-client/CRM read model) is a "relationship-facing fact" (→ should credit the *committing* driver, per D-08(b)'s own wording) or a "ride-count"-shaped fact (→ correctly credits the *executing* driver, as it currently does, since it is computed inside the same `AssignmentCompletedApplicationService.handle()` call as `completedRidesCount`). OQ-3's own three-option table (`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:31-39`) frames this exact ambiguity as "Option C — Split by purpose: relationship-facing facts (repeat-client flag, 'Мой бизнес' stats) credit the committing driver; ride-count/earnings credit the executing driver" — **explicitly naming the repeat-client flag as a relationship-facing fact that should credit the committer** — but the shipped implementation routes it through the same `driverId` as ride-count/earnings, crediting the *executor*. This is a genuine, evidence-based gap between OQ-3 Option C's own stated intent and what D-07 actually built, not previously flagged in any document reviewed for this reconciliation. This document does not resolve it — see §15/§16.

## 10. Observation-Before-Cap Analysis

The *principle* — no cap without observation — is locked, per `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md`'s own Invariant #26/#31 and the current task's own framing ("CRITICAL D-08 LOCK ALREADY EXISTS"). It is enforced today only in the negative sense: **no cap of any kind exists in code**, so the principle cannot currently be violated.

A precise reading of the *original* source text is important and easy to over-read: `PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766` frames D-08(a) as **"a cap would be an invented number, and monitoring is the honest alternative"** — i.e., the original recommendation leans toward observability *instead of* an eventual cap, not observability *as a staged prerequisite to* an eventual cap. The current task's own framing ("MUST NOT block a driver before there is sufficient observable behavior from which such a cap can legitimately be derived") reads more like the latter — implying a cap is still the eventual destination, just evidence-gated. Both readings are consistent with everything shipped so far (since nothing has been built either way), but they are **not the same product outcome**, and no document in the repository disambiguates which one D-08 is actually meant to produce. This is presented as evidence, not resolved.

## 11. Candidate Policy Models

Recorded per the task's own A–E distinction, none selected:

- **Model 1 — Pure observability (Option C in OQ-7 as literally worded).** No enforcement of any kind. An owner-facing view/report of Handoff frequency and concentration per driver. This is squarely a **[D] Metric** used for no **[C] Policy** at all — no block, no warning, nothing shown to the affected driver. Matches the "monitoring is the honest alternative" reading of §10.
- **Model 2 — Numeric cap, evidence-gated.** A concrete threshold (count per driver per period) that rejects a Handoff operation once exceeded, but the threshold itself is only set/activated after a defined observation period yields real data. This is **[C] Policy** built on **[D] Metric**, deferred in time.
- **Model 3 — Soft warning, no hard block.** The system surfaces a warning (to the driver, to the owner, or both) once a threshold is crossed, without preventing the Handoff. A hybrid of [D] and a weaker [C].
- **Model 4 — Reporting only to the Product Owner, not to drivers.** Same data as Model 1, but explicitly scoped as an internal/owner tool, never driver-visible, to avoid any appearance of a driver-facing score (risk of drifting toward **[E] Ranking/Score**, discussed in §12).

## 12. Risks of Each Model

- **Model 1 (pure observability)**: Lowest risk of becoming [E] Ranking, since no ordering or scoring of drivers is produced — only a per-driver figure held privately. Risk: does not actually address the "informal fleet backdoor" concern named in OQ-7 Option A, since nothing stops the pattern from continuing while it is merely watched.
- **Model 2 (evidence-gated numeric cap)**: Directly answers the task's own "observation-before-cap" framing. Risk: **the threshold itself, whenever chosen, is an invented business rule with no evidence behind it yet** — the exact concern `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option B already raised and `CLAUDE.md`'s "Never Invent Business Rules" rule forbids this seat from resolving. Also risks drifting toward a driver-tier/eligibility concept if the cap's consequence is anything beyond "this specific Handoff operation is rejected" (e.g., if it ever fed back into `driverAvailabilityRepository` or Assignment Policy, it would cross into ADR-034 Part 2's forbidden-inputs list — "driver profile, standing, participation, or ranking beyond availability").
- **Model 3 (soft warning)**: Ambiguous who the warning is for. If shown to the driver, risks being read as an implicit reputational signal (adjacent to what `DRIVER_IDENTITY_DESIGN_DECISION.md` §4 and `DriverTrustIndicator.tsx`'s own design explicitly avoid — "never a rating, count, rank, or any fabricated trust score").
- **Model 4 (owner-only reporting)**: Avoids driver-facing score risk entirely, but is operationally identical to Model 1 unless paired with a manual, out-of-band, human decision process — which itself is undocumented and outside any existing ADR's scope (ADR-034 Part 2's forbidden inputs concern *automated* decisions; a human owner manually contacting a driver is not currently addressed by any ratified document either way).

**Common risk across every model that persists or displays a count**: ADR-081's own boundary (§5) governs `completedRidesCount` et al. specifically by name; a *new* Handoff-frequency metric is not one of ADR-081's five named metrics and would not automatically inherit its protection — a fresh, explicit ruling would be needed to keep a new D-08 metric from drifting into money/tier/priority/ranking/trust use, the same way ADR-081 did for the five it names.

## 13. Concurrency Implications

`OrderGuard`/`PostgreSQLOrderGuard` (`backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLOrderGuard.kt`) locks a single row in `dispatch_order_guards`, keyed strictly by `order_reference` (`SELECT ... FOR UPDATE`). `HandoffApplicationService` uses this same lock in all five of its methods, scoped to the Handoff's own order.

**This is an order-level lock, not a driver-level one.** A confirmed, repo-wide search (by the research agent, corroborated by direct reading of `PostgreSQLOrderGuard.kt`) found no driver-ID-keyed lock, mutex, or `SELECT ... FOR UPDATE` anywhere in the codebase. The only rolling-window/rate-limit mechanism that exists at all is `LoginRateLimiter`/`PhoneOtpRequestRateLimiter` in Identity — an in-memory (non-persisted, resets on restart), per-phone-number window used purely for authentication anti-abuse, architecturally analogous but never applied to a driver or a business action.

**Concrete consequence for D-08**: if the same driver is named as substitute (or original) on two different orders at the same moment, each Handoff's own `propose`/`consent` call locks only its own order — the two operations proceed fully independently, with no serialization between them. A driver-level cap check (e.g., "has this driver already had 3 Handoffs committed today") reading and incrementing a per-driver counter across two concurrent order-scoped transactions would race: both could read "2" before either writes "3," and both commit, exceeding the intended cap by one. **The current `OrderGuard` does not prevent this** — a driver-scoped serialization primitive does not exist today and would need to be introduced if any policy model requires an atomic per-driver limit enforced under concurrency (Model 2/3 above); Model 1/4 (pure observation, no blocking) does not need one, since a report that is occasionally one event behind under a genuine race is not a correctness violation the way a bypassed cap would be.

## 14. Persistence/Schema Implications

- The `handoffs` table (V22) already holds every fact a naive count-based observation would need, but has **no index on either driver-reference column** — any per-driver query today is a full scan.
- **No aggregate/rollup table** exists for Handoff activity (unlike `driver_milestones`, which is a maintained read-model updated incrementally on each event). If D-08 needs fast, frequent per-driver reads (e.g., an owner dashboard queried often), a new read-model table, updated incrementally the same way `driver_milestones`/`driver_client_rides` already are, is the pattern this codebase already uses — but building one is new work, not something the existing schema provides for free.
- **No time-windowed structure** exists anywhere in this codebase for any driver metric (confirmed by both research agents independently: every existing counter is a lifetime total or a single timestamp). A rolling-window design (§10's "observation window," Q9/Q10 in the checklist below) would be new architecture for this codebase, not a reuse of an existing pattern — except for the Identity rate-limiters, which are in-memory and not persisted, and therefore not a safe precedent for anything requiring durability across restarts or multiple server instances.
- Any new table or column is, per this task's own instruction, **not to be created by this reconciliation** — this section identifies only what a future implementation would need to design.

## 15. Existing ADR/Product-Decision Conflicts or Drift

No outright contradiction was found between D-07's shipped implementation and any ratified ADR. Two genuine **drifts** (gaps between what was reasoned about and what was actually built, not violations of any explicit rule) were found:

1. **ADR-081 predates D-07 by one day (2026-09-17 vs 2026-09-18) and its own reconciliation explicitly found zero Dispatch-side references to the five metrics it governs.** That finding was accurate *at the time it was ratified*. D-07, shipped the next day, changed what `AssignmentCompleted.driverId` means (executor, not committer) without any accompanying update to ADR-081 or its reconciliation. ADR-081's own rules are not violated (the metrics are still private, self-reported, not used for money/tier/priority/ranking/trust — nothing in D-07 touches that boundary), but the **content** of `completedRidesCount`, `totalStatedEarnings`, `repeatClientsCount`, and `driver_client_rides` is now Handoff-dependent in a way ADR-081's evidentiary basis did not evaluate, because it could not have — D-07 did not exist yet. No document currently records this connection explicitly; this reconciliation is the first to state it.
2. **OQ-3 Option C's own wording ("repeat-client flag... credit the committing driver") vs. the shipped implementation (repeat-client credit follows `AssignmentCompleted.driverId`, i.e. the executor).** See §9. This is a specific, narrow gap between one sentence of one open-questions document and what was actually built — not a violation of any locked invariant (the D-07 implementation spec's own Invariants #22/#23 speak only to "the historical/origination fact" and "execution attribution and stated-earnings attribution," not to the repeat-client metric by name), but worth the Product Owner's explicit attention given D-08(b) is reported elsewhere as "fully resolved."

Neither drift blocks or requires reopening D-07. Both are recorded here because D-08's own observation surface would likely read from the same event/metric pipeline these drifts concern.

**No conflict found** between D-08's likely shape and: `ADR-034` Part 2 (forbidden Assignment-decision inputs) — *provided* D-08's cap, if any, never feeds back into Dispatch's own driver-selection decision, only into whether a specific Handoff operation is permitted (a materially different decision than "which driver does Dispatch pick for a new Order"); `ADR-054` (no ranking/scoring/priority) — *provided* D-08's mechanism stays a binary filter (matching-rule-shaped, per `docs/PIOS_TAXI_PRODUCT_DECISIONS.md`'s own explicit MATCHING RULES vs RANKING ALGORITHM distinction) rather than any ordering or scoring of drivers against each other; `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` — that document scopes itself explicitly to NETWORK-scoped *assignment-time* opportunity access, a different decision point than Handoff's own driver-initiated, post-assignment execution transfer, so it does not directly govern D-08, though its own reasoning (procedural fairness, no ranking, no pay-to-win) is the closest existing precedent for how a similar question was previously resolved in this codebase.

## 16. Questions Requiring Explicit Product Owner Decision

Each recorded with: question, current evidence, why it matters, concrete options, consequences. No ranking, no recommendation.

**Q-A. What does "handoff frequency" mean numerically?**
Evidence: undefined anywhere (§7). Matters because it determines what gets counted and what the observation surface even measures.
Options: (i) raw Handoff-row count per driver per period, any status; (ii) COMMITTED-only count; (iii) a rate relative to the driver's own total ride volume.
Consequences: (i) is simplest but conflates attempted-and-refused activity with actual substitutions; (ii) undercounts the "informal dispatcher" pattern if many proposals are withdrawn/refused before commit but the underlying behavior pattern is still real; (iii) is the most informative but requires new cross-module query work (§14) and ties the metric to ride volume in a way that could itself need justification.

**Q-B. What does "concentration" mean, and on which actor?**
Evidence: §8 — two structurally distinct readings (concentration onto a substitute vs. concentration of an original driver's own outflow), both textually supported by the task's own framing.
Options: measure the substitute only; measure the original driver only; measure both, separately.
Consequences: measuring only the substitute catches the "crew formation" pattern but misses a single driver funneling most of their own commitments out; measuring only the original driver catches that but misses several original drivers funneling into one substitute; measuring both doubles the design/implementation surface.

**Q-C. Which events count toward observation, and which must not?**
Evidence: §6/§7 — the `handoffs` table records every status, including REFUSED and WITHDRAWN, permanently.
Options: count every row regardless of outcome; count only COMMITTED rows; count PROPOSED+SUBSTITUTE_ACCEPTED+COMMITTED but exclude REFUSED/WITHDRAWN; some other split.
Consequences: including REFUSED/WITHDRAWN rows means a driver who proposes Handoffs that never complete (for entirely legitimate reasons — a substitute declines, a passenger says no) accumulates observation "weight" without ever actually transferring a ride, which may or may not be desired signal; excluding them undercounts a genuine pattern where withdrawal/refusal itself is part of the concerning behavior (e.g., repeatedly probing the same substitute).

**Q-D. What is the correct denominator, if any?**
Evidence: §7 — no existing query computes "this driver's total commitments" in a form directly joinable to Handoff counts without new work.
Options: no denominator (raw counts only); all commitments (Assignments); completed commitments (completed Trips); consented Handoffs as their own unit with no denominator at all.
Consequences: a raw count is simplest but says nothing about a driver's overall activity level (10 handoffs out of 10 rides reads very differently from 10 out of 500); a denominator requires new cross-module aggregation (Dispatch's own Assignment/Trip data joined against Handoff data) that does not exist today.

**Q-E. What minimum observation volume is required before any cap (if one is ever adopted) may activate?**
Evidence: none — no document states a number, window, or volume; the "observation-before-cap" principle states only that the moment must be evidence-based, not what evidence suffices.
Options: a fixed calendar period (e.g., "N weeks of platform-wide data"); a fixed volume of Handoff events (e.g., "M total Handoffs observed"); left entirely to a future, separate decision once Model 1/4-style monitoring has run for some time.
Consequences: any concrete number chosen now would be exactly the "invented business rule with no evidence behind it yet" `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option B and `CLAUDE.md` both warn against.

**Q-F. Calendar-based or rolling observation window?**
Evidence: §14 — no rolling-window persistence pattern exists anywhere in this codebase for a durable, multi-instance-safe metric; the only rolling-window code (Identity's rate limiters) is in-memory and not a safe precedent.
Options: calendar-based (e.g., per ISO week, mirroring `driver_milestones.current_streak_weeks`'s own existing week-boundary convention); rolling (e.g., trailing 30 days), requiring new infrastructure.
Consequences: calendar-based reuses an existing, proven pattern in this codebase (`RideStreakCalculator`) but can produce edge effects at period boundaries; rolling is smoother but is genuinely new architecture here.

**Q-G. What, precisely, does a cap limit — if D-08 ever adopts one?**
Evidence: §11 — the task itself lists four candidate targets (incoming Handoffs, outgoing Handoffs, concentration of executions, percentage of a driver's own rides executed via Handoff) without choosing one.
Options: as listed in the task; not mutually exclusive.
Consequences: each targets a different failure mode (§8); choosing more than one compounds both the design and the "what happens when reached" question (Q-H) for each.

**Q-H. What happens when a cap (if any) is reached — reject proposal, block substitute acceptance, block passenger consent, warn only, or something else?**
Evidence: none — no document specifies enforcement behavior; §11's four models bracket the space.
Options: as the task lists; also "reject at whichever step first reveals the breach" vs. "reject uniformly at `propose` regardless of where the count would be exceeded."
Consequences: blocking earlier (at `propose`) is simpler and gives faster feedback to the original driver, but means the substitute and passenger never even see a Handoff that might otherwise have been fine from their own perspective; blocking later (at `consent`) preserves more optionality but means a substitute may accept a Handoff that is then rejected out from under them at the last step, which is a materially worse experience than being told "no" up front.

**Q-I. Is D-08 fundamentally anti-concentration, anti-abuse, fairness, or some combination — and does that classification change what mechanism is appropriate?**
Evidence: the original recommendation (`PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766`) frames it as guarding against "the informal-fleet pattern," which is closest to an **anti-concentration/anti-circumvention-of-ADR-054** concern (preventing Handoff from becoming a backdoor around the no-Team/Fleet/crew ban), not a fairness-to-passengers or fairness-to-other-drivers concern in the `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` sense (that document governs *assignment-time* shared-opportunity access, a different decision point, per §15 above). It is also not framed anywhere as anti-fraud (ADR-081 explicitly scopes the fabricable-metrics/fraud question out of its own decision, as a separate, still-open question). This document does not label D-08 on the Product Owner's behalf — the evidence points most directly at "anti-circumvention of the existing no-delegation/no-Team/Fleet boundary," but the task's own instruction is explicit that this must not be asserted without the Product Owner's confirmation.
Options: anti-concentration/anti-circumvention (closest fit to existing evidence); anti-abuse/anti-fraud (would then need to also address ADR-081's already-open fabricability question, which D-08 was never scoped to touch); a Fair-Opportunity-style fairness mechanism (would then need to be reconciled against that document's own NETWORK-scope limitation, since Handoff is not an assignment-time, shared-pool decision).
Consequences: the classification chosen affects which existing ADR/product-decision boundary D-08 must be checked against, and therefore which document (if any) needs its own amendment alongside a future D-08 ADR.

**Q-J. Should the "repeat client" metric (`repeatClientsCount`/`driver_client_rides`) follow the committing driver or the executing driver post-Handoff?**
Evidence: §9/§15 — OQ-3 Option C's own text names the repeat-client flag as a "relationship-facing fact" (→ committer), but the shipped D-07 implementation routes it through `AssignmentCompleted.driverId` (→ executor) alongside ride-count/earnings.
Options: leave as shipped (executor credit, consistent with ride-count/earnings); change to credit the committer (consistent with OQ-3 Option C's literal wording, would require new engineering work Dispatch/Driver Management do not currently do — separating "who gets the repeat-client credit" from "who gets the ride-count credit" inside `AssignmentCompletedApplicationService`, which today has only one `driverId` to work with per event).
Consequences: this question is logically upstream of any D-08 design that intends to use `repeatClientsCount`/`driver_client_rides` as an observation signal, since its current meaning is ambiguous relative to what was originally intended.

## 17. Questions That Are Implementation Details and Do Not Require PO Decision

These follow mechanically from whichever answers are given to §16, and do not themselves need separate Product Owner sign-off:

- Whether a driver-level index is added to `handoffs` for query performance (§14) — a performance detail once the query shape is known.
- Whether a new read-model table (mirroring `driver_milestones`) is introduced to serve fast per-driver reads, versus computing on demand from `handoffs` directly — an implementation-cost tradeoff, not a product question, once the observation definition (Q-A through Q-D) is settled.
- The exact PostgreSQL locking strategy for any driver-level serialization the chosen policy model requires (advisory lock vs. a new driver-guard table mirroring `dispatch_order_guards`'s own pattern) — a concurrency-engineering decision downstream of Q-G/Q-H, not a business rule.
- Which specific repository methods/API endpoints expose the observation data, and their exact shapes — standard engineering work once what is being observed (Q-A–Q-D) is fixed.

## 18. Recommended Decision Matrix, Without Selecting a Winner

| Question | Depends on | Candidate answers on record |
|---|---|---|
| Q-A frequency definition | — | raw count / COMMITTED-only / rate |
| Q-B concentration actor | — | substitute / original driver / both |
| Q-C countable events | — | all statuses / COMMITTED-only / all-but-terminal-negative |
| Q-D denominator | Q-A | none / all commitments / completed commitments / no denominator |
| Q-E minimum observation volume | Q-A, Q-C | calendar period / event volume / deferred |
| Q-F window shape | Q-E | calendar-based / rolling |
| Q-G cap target | Q-B | incoming / outgoing / execution concentration / percentage of own rides |
| Q-H enforcement point | Q-G, Model choice (§11) | propose / accept-substitute / consent / warn-only / none |
| Q-I classification | — | anti-concentration / anti-abuse / fairness / combination |
| Q-J repeat-client attribution | — | executor (as shipped) / committer (per OQ-3 Option C wording) |

No cell in this matrix is populated with a chosen value. It exists to show how the open questions relate to one another, not to narrow them.

## 19. Exact Next PO Decisions Required

Before any D-08 design document (let alone an ADR or implementation) can proceed, the Product Owner must answer, at minimum, Q-A through Q-J above (§16). Partial answers are usable — as `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`'s own closing note observes for OQ-1 through OQ-7, "a partial answer is enough to scope a first, narrow version," including explicit deferral of any individual question (e.g., "ship Model 1 pure observability now; Q-G/Q-H deferred until real data exists").

## 20. Proposed Next Step

Not an ADR, not a migration, not an implementation. The lowest-commitment next step consistent with everything found in this reconciliation is: the Product Owner works through the **D-08 Decision Lock Checklist** below (mirroring the precedent already set by `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` for D-07 itself), and only once enough of it is answered does an architect-role ADR define D-08's concrete mechanism — at which point, per this repository's own convention (`CLAUDE.md`, "Never Change Architecture Without an ADR"), that ADR would be the first document authorized to make any of these choices binding.

---

## D-08 DECISION LOCK CHECKLIST

Questions only the Product Owner can answer before any D-08 implementation may begin. Each corresponds to a numbered question in §16 above.

1. **Frequency definition** — does "handoff frequency" count every Handoff attempt, only committed ones, or a rate relative to total rides? (§16 Q-A)
2. **Concentration actor** — is concentration measured on the substitute (rides flowing in), the original driver (rides flowing out), or both, separately? (§16 Q-B)
3. **Countable events** — do REFUSED and WITHDRAWN Handoffs count toward observation, or only ones that reach SUBSTITUTE_ACCEPTED/COMMITTED? (§16 Q-C)
4. **Denominator** — is any per-driver total (all commitments, completed commitments) required as a denominator, or are raw counts sufficient? (§16 Q-D)
5. **Minimum observation volume** — what amount of real usage data (time, event count, or both) must exist before any cap may even be considered active? (§16 Q-E)
6. **Window shape** — calendar-based (e.g., per week, reusing the existing streak-week convention) or rolling? (§16 Q-F)
7. **Cap target** — if a cap is ever adopted, what exactly does it limit: incoming Handoffs, outgoing Handoffs, execution concentration, or percentage of a driver's own rides executed via Handoff? (§16 Q-G)
8. **Enforcement point and behavior** — reject at proposal, block substitute acceptance, block passenger consent, warn only, or observe with no enforcement at all? (§16 Q-H)
9. **Classification** — is D-08 an anti-concentration safeguard, an anti-abuse mechanism, a fairness mechanism, or a stated combination — and is "monitoring only, no eventual cap" (the original brief's own framing) or "monitoring as a staged prerequisite to an eventual cap" (this task's own framing) the intended destination? (§16 Q-I, §10)
10. **Repeat-client attribution** — should `repeatClientsCount`/`driver_client_rides` credit the committing driver (per OQ-3 Option C's literal wording) or the executing driver (as currently shipped)? (§16 Q-J, §9, §15)
11. **Visibility** — is the observation surface owner-facing only, driver-facing (to the driver being observed), or both — and if driver-facing, how is it kept distinguishable from a prohibited "trust score" (`DriverTrustIndicator.tsx`'s own existing design boundary)?

---

## Verification

- **Exact git status** (after this document was written, before any commit): see the session's own report accompanying this document — only `docs/PIOS_D08_HANDOFF_CAP_RECONCILIATION.md` was added; every path listed in the task's "known unrelated working-tree paths" remains exactly as it was found.
- **HEAD**: unchanged at `16ae80be0b59c3dd43138b66715124ee0632a062` throughout this work — no commit was made.
- **No test, migration, ADR, or production source file was read for any purpose other than citation in this document; none was modified.**
