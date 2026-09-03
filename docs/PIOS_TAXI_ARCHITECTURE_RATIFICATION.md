# PIOS Taxi Architecture Ratification

Status: **Ratification record.** Closes the architecture gate `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` Section 21 opened. Produced by Task 10B (Architecture Ratification & Document Reconciliation), 2026-09-03. No code, schema, migration, RabbitMQ, or configuration was touched to produce this document or the reconciliation it records — this is the terminal documentation-governance step before an implementation task, not the implementation task itself.

**Update (Task 11A, 2026-09-03 — Correct Trip Creation Trigger).** Task 11's own mandatory pre-implementation call-graph inspection (not performed by Task 10B, which reviewed the ADRs' internal consistency but did not trace every call site) found that `ADR-063`'s trigger, as corrected by Task 10B, was still wrong: `AssignmentAccepted` is never published by any live production path. `ADR-063`'s trigger is corrected a second time, to `OrderAssigned`. `ADR-063` remains **Accepted** — only its technical trigger changed. Sections 1, 2, 4, 6, 8, and 9 below are updated accordingly; see `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md` for the full record. **This ratification's own "OPEN" gate status (Section 8) is unaffected** — the correction it made about `AssignmentAccepted`'s non-production status (Section 6, then item 4) already anticipated exactly this kind of finding; Task 11A resolves it precisely by not implementing around it.

---

## 1. Ratified Decisions

| Decision | Ratified by | Scope |
|---|---|---|
| Primary Driver is a passenger-controlled preference signal — never ownership, never exclusivity | `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 3 (Product Owner), architecturally contracted by `ADR-062` | Product + architecture |
| First Refusal: Primary Driver gets first opportunity; decline/timeout/ineligibility falls through to normal matching; no exclusive reservation | `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4, `ADR-062` | Product + architecture |
| Passenger Experience → Dispatch contract: event-fed local projection (`PrimaryDriverRecord`), never a synchronous call, never a shared table | `ADR-062` | Architecture |
| Order = request / Assignment = match / Trip = execution; Trip lives inside Dispatch, not a new service; trigger is `OrderAssigned` (corrected a second time, Task 11A — never the mutable `Assignment.status` field, and, per Task 11A's own finding, never `AssignmentAccepted` either, since that event is not produced by any live production path) | `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 9, `ADR-063` (twice-corrected) | Architecture |
| Circle of Trust stays Taxi-specific; Network Management stays platform-level, undeployed, uncoupled from Taxi V1 | `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 5–6, `ADR-037` (unchanged, reaffirmed) | Product + architecture |
| No opaque ranking; First Refusal is a matching rule (binary), never a ranking-algorithm input | `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 10, `ADR-034` Part 2 (reconciled) | Product + architecture |
| Minimum data contract: two opaque identifiers only, no profile/rating/reputation data crosses the new contract | `ADR-062` Decision, `ADR-034` Part 2 (Forbidden-inputs entry, reconciled) | Architecture + privacy |

## 2. Accepted ADRs

| ADR | Title | Status before Task 10B | Status after Task 10B | Correction applied |
|---|---|---|---|---|
| `ADR-062` | Primary Driver / First Refusal — Passenger Experience → Dispatch Contract | Proposed | **Accepted** (2026-09-03) | None — verified materially correct as written |
| `ADR-063` | Trip as a New Dispatch-Owned Aggregate, Distinct from Assignment | Proposed | **Accepted, after correction — corrected a second time in Task 11A** (2026-09-03) | Trigger corrected twice: first (Task 10B) from "`Assignment.status == ACCEPTED`" to the `AssignmentAccepted` event/outcome; then (Task 11A), after Task 11's own call-graph inspection found `AssignmentAccepted` itself is never published by any live production path, to **`OrderAssigned`** — the event both real Assignment-creation paths actually publish. Both corrections preserved in `ADR-063`'s own Status section |

No third ADR was created for Network Management — `ADR-037` remains fully authoritative and unmodified, consistent with Task 10A's own finding and this task's explicit instruction not to create `ADR-064` for this purpose.

## 3. Superseded Decisions

Every supersession below preserves the original text in place, per `CLAUDE.md`'s "Never Delete Documentation" — none was deleted or silently rewritten:

| Original statement | Document | Superseded by | Scope of supersession |
|---|---|---|---|
| *"Dispatch. No ratified role at all"* in Personal Client Relationship | `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 | `ADR-062` | **Narrow**: Dispatch may now consume a binary Primary Driver signal for First Refusal only — no general role, no profile visibility, no assignment-decision role |
| Personal Client Relationship listed only as an unratified "Future candidate input" | `ADR-034` Part 2 | `ADR-062` | **Narrow, same scope as above** — explicitly distinguished from any future ranking/scoring use, which remains exactly as unratified as before |
| Ride-progress trigger implicitly tied to `Assignment.status` | `ADR-063` (its own earlier text, pre-acceptance) | `ADR-063`'s own corrected text | **Self-correction**, applied before acceptance — not a supersession of a different document, recorded here for completeness |
| Implicit prior state where `INTERFACE_CONTRACTS.md` Section 5 listed no Passenger Experience → Dispatch contract | `INTERFACE_CONTRACTS.md` | `ADR-062` | The absence itself is superseded (a new contract now exists); nothing previously stated is contradicted, since the document never claimed such a contract could not exist |

**Not superseded, explicitly reaffirmed:** `ADR-040`'s own module-level holding (ride-progress stays in Dispatch, never Order Management) — `ADR-063` operates entirely within that holding, narrowing only which aggregate inside Dispatch owns it. `ADR-041`'s Order/Assignment-completion synchronization — unaffected in substance, only the event name it will eventually bind changes, on its own future update. `ADR-034` Part 2's Forbidden-inputs list — every other entry (payment, driver standing/ranking, notification history, analytics, external systems) remains exactly as forbidden as before; only the one Personal Client Relationship line, in the *Future-candidate* tier, moved, and only narrowly.

## 4. Current vs. Target Distinctions

**CURRENT (running in production today, unaffected by this ratification):**
- `OrderAssigned`, `AssignmentArrived`, `AssignmentStarted`, `AssignmentCompleted` — published exactly as before, no rename performed. **Corrected, Task 11A**: `AssignmentAccepted` is modeled and outbox-capable but **not** actually published by any live path (Section 6 below carries this as a separate, tracked risk) — it does not belong in this "running today" list, and this document's own earlier version incorrectly implied it did.
- `Assignment.status`: `CREATED, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED` — unchanged, no field removed or added.
- Circle of Trust (`Connection`/`isPrimary`) — unchanged, still consumed by no routing logic.
- `POST /v1/proposals` and its sibling endpoints — unchanged, still unauthenticated (Section 6).
- The Coordinator, `RideRequest.tsx`'s own Sprint-7B direct-propose flow — unchanged, untouched by this task.

**TARGET (architecturally ratified, not yet built — explicitly marked as such in every reconciled document):**
- `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` events (Passenger Experience) and `PrimaryDriverRecord` projection (Dispatch) — `ADR-062`, zero code exists.
- ~~`Trip`/`TripStatus`/`TripArrived`/`TripStarted`/`TripCompleted` (Dispatch-internal) — `ADR-063`, zero code exists.~~ **Moved to CURRENT, Task 12 (Trip Ride-Progress Convergence) — see the dated addition below.** Struck through, not deleted, per `CLAUDE.md`'s "Never Delete Documentation."
- The eventual **full retirement** of `Assignment*` — the dual-publish window itself is now live (Task 12); only *removing* the legacy half, and switching Order Management's own binding to `trip.completed`, remains not begun.
- Any wiring of Dispatch's own Proposal-creation logic to actually consult `PrimaryDriverRecord` — explicitly out of both ADRs' own scope (the trigger/endpoint shape remains OPEN).

**Added 2026-09-03 (Task 12, Trip Ride-Progress Convergence) — moved from TARGET to CURRENT:**
- `Trip` is now the sole validator and persister of `ARRIVED`/`IN_PROGRESS`/`COMPLETED` — `DispatchAssignmentApplicationService.arriveAssignment`/`startAssignment`/`completeAssignment` transition `Trip`, not `Assignment`. `TripArrived`/`TripStarted`/`TripCompleted` are published to the outbox alongside the unchanged `AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted` (dual-publish, per `ADR-063`'s own Consequences section).
- `AssignmentController`'s REST surface (`GET /v1/assignments`, `.../arrive`, `.../start`, `.../complete`) is unchanged in shape; its response is now internally projected from both `Assignment` and `Trip`.
- **Deliberate, documented deviation from `ADR-063`'s own Decision-section wording**: `Assignment.arrive()`/`start()`/`complete()` and its ride-progress fields were **not** deleted (that text said "removed from Assignment"). Task 12's own Phase 3 instruction — "do NOT delete existing Assignment fields ... only remove old behavior after proving no active consumer depends on it" — was treated as controlling, since `DriverHome.tsx` is a live consumer and the pilot's own PostgreSQL data already has `Assignment.status` rows at `ARRIVED`/`IN_PROGRESS`/`COMPLETED`. See `ADR-063`'s own "Implementation note (Task 12)" and `docs/PIOS_TAXI_TASK_12_TRIP_CONVERGENCE_REPORT.md` for the full reasoning.
- A legacy `Assignment` with no connected `Trip` (saved before Task 11) self-heals on its first ride-progress action rather than failing.

## 5. Interface Contracts Now Authoritative

`INTERFACE_CONTRACTS.md` Section 5 now lists **six** justified module-to-module contracts (five pre-existing, per `ADR-053`'s own count, plus the new sixth):

1. Passenger Experience → Order Management (unchanged)
2. Driver Management → Dispatch (unchanged)
3. Dispatch → Order Management (unchanged)
4. Order Management → Dispatch (`ADR-053`, unchanged)
5. Any Module → Notifications/Analytics, generic (unchanged)
6. **Passenger Experience → Dispatch** (`ADR-062`, new — Section 5's own new entry, Section 17's own addendum)

`INTERFACE_CONTRACTS.md` Section 7's event table now carries explicit **CURRENT**/**TARGET** markers per row (Task 10B's own addition) — the first place in this document set where that distinction is machine-checkable at the row level, not only asserted in prose.

## 6. Security Blockers — Architectural Risk Register (Separate from `ADR-062`/`ADR-063`)

**Carried forward from `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` Section 17, restated here as the canonical pointer. None of the four items below is fixed by this task, by `ADR-062`, or by `ADR-063` — all are pre-existing conditions of the current Foundation, confirmed by direct source inspection.**

1. **Dispatch's Proposal-mutation endpoints (`POST /v1/proposals`, `/accept`, `/decline`, `/lapse`) have no authentication of any kind.**
2. **No service-to-service authentication mechanism exists anywhere in this codebase** — the one inter-module REST client that exists is dead code and sends no credential regardless.
3. **Production RabbitMQ uses a shared `guest`/`guest` credential on the default vhost for every module** — no per-module broker-level access boundary.
4. **These are production-hardening blockers, not architecture blockers**, and are explicitly kept separate from First Refusal's own implementation, per this task's own instruction. `ADR-062`'s own chosen contract (event-fed projection) neither worsens nor depends on fixing any of these — it was specifically designed to avoid needing a new service-to-service credential (item 2) at all.

5. **Separate category, added Task 11A — an integration gap, not a security risk.** `AssignmentAccepted` (modeled, outbox-capable) is not published by any live production path — `Assignment.accept()`, its only source, is unreachable from any controller. Order Management's own `AssignmentAcceptedListener` is fully built and correctly wired, waiting for a message that never arrives. Independently, `OrderAssigned` — actually published on every real Assignment creation — has **no consumer at all** in Order Management. **Net effect: Order Management currently has no working event-driven signal that an order has been assigned to a driver.** This is unrelated to Trip's own correctness (Trip's `OrderAssigned` consumption, per the corrected `ADR-063`, is Dispatch-internal and does not depend on Order Management consuming anything) but is a real, separate gap. Full evidence: `docs/PIOS_TAXI_TASK_11_TRIP_FOUNDATION_REPORT.md`, `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md` Section 7. **Not fixed by Task 11A** — Order Management was not modified.

**Items 1–4 (security) and item 5 (integration gap) do not block the architecture gate (Section 8, below)** — each is a distinct, still-open concern for a future task to address, ideally before or alongside whichever implementation task first makes First Refusal's own routing logic live, or reconciles Order Management's own event consumption (neither is the contract-infrastructure-only task this gate now permits, Section 9).

## 7. Explicit Non-Goals

Restated, none reopened by this ratification:

- No exclusive Primary Driver / passenger reservation (First Refusal Option A) — never approved, never built.
- No driver ownership of a passenger, in any form.
- No expansion of Circle of Trust into social-network functionality.
- No implementation of the complete PIOS Network, no merge of Network Management into Taxi, no new ADR for the Network Management boundary (`ADR-037` remains sufficient and unmodified).
- No opaque "best driver" ranking system, implicit or explicit — First Refusal remains a binary matching rule, not a ranking-algorithm input (`ADR-034` Part 2, reconciled, not weakened).
- No universal reputation score; Profile/Trust/Rating/Reputation/Relationship remain distinct concepts.
- No payment gateway, no payment economics, no monetization mechanism.
- No security remediation performed as part of, or mixed into, this architectural ratification (Section 6).
- **No code, schema, migration, RabbitMQ change, frontend change, or authentication implementation was performed by this task** — documentation and ADR status changes only.

## 8. Implementation Gate Status

Restated from `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` Section 21, all nine conditions this task's own prompt specified:

| # | Condition | Status |
|---|---|---|
| 1 | `ADR-062` is Accepted | ✅ |
| 2 | `ADR-063` is Accepted after the event-trigger correction | ✅ — corrected twice: `Assignment.status` → `AssignmentAccepted` (Task 10B) → `OrderAssigned` (Task 11A, after `AssignmentAccepted` was found never actually published) |
| 3 | Contradictory interface/product documentation is reconciled | ✅ — `INTERFACE_CONTRACTS.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, `ADR-034`, `EVENT_CATALOG.md`, `docs/README.md` |
| 4 | Primary Driver ownership is unambiguous | ✅ |
| 5 | First Refusal contract is defined | ✅ |
| 6 | Failure behavior is defined | ✅ (with named OPEN parameters, not undefined mechanisms) |
| 7 | Order / Assignment / Trip boundaries are defined | ✅ (with the corrected trigger) |
| 8 | Network Management boundary is defined | ✅ |
| 9 | No ratified decision is silently contradicted | ✅ — every supersession above is explicit and citable |

## IMPLEMENTATION GATE: OPEN

**All nine conditions are satisfied.** This is a statement about architectural readiness only — it does not authorize skipping the security register (Section 6), and it does not resolve the parameters this ratification deliberately leaves open (response-window duration, exact trigger/endpoint shape — both remain OPEN, per `ADR-062`'s own explicit non-scope, unchanged by this task).

## 9. Exact Next Implementation Task

Per `docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md` Section 5's own scoping (unaffected by this ratification, now unblocked by it): the next task should build the **contract infrastructure only** — new events, aggregates, projections, and migrations for `ADR-062` and/or `ADR-063` (the two tracks are independent and may proceed in either order or in parallel) — **without wiring either into any existing endpoint's live behavior**. Concretely:

- **ADR-062 track**: `PrimaryConnectionDesignated`/`Cleared` event classes and outbox wiring in Passenger Experience; `PrimaryDriverRecord`, its repository, RabbitMQ topology/consumer, and migration in Dispatch. Stop once the projection exists and is fed — do not modify `ProposalController.kt`.
- **ADR-063 track (corrected trigger, Task 11A)**: `Trip`/`TripStatus`/`TripArrived`/`Started`/`Completed` domain classes, repository, and migration in Dispatch; wire Trip creation to `OrderAssigned` — consumed from both real Assignment-creation paths (`ProposalAssignmentOrchestrationService.acceptProposal` and `AssignmentController.assignOrder`'s own application-service path), never `AssignmentAccepted` and never `Assignment.status`; relocate `Assignment.arrive()/start()/complete()`; dual-publish the new `Trip*` events alongside the existing `Assignment*` ones. Do not wire `AssignmentAccepted` into any live path to satisfy the original trigger — Section 6 item 5's own gap is explicitly not this task's to fix. Stop before switching Order Management's own consumer.

Both tracks must respect the file-scope boundary `docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md` Section 5.4 already named: no frontend file, no `Coordinator.tsx`, no existing endpoint signature change, no RabbitMQ vhost/credential change (Section 6 above remains a separate, later concern).

---

## Final Report

**Files created:**
- `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md`

**Files modified:**
- `docs/ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md` — Status: Proposed → **Accepted**
- `docs/ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md` — Status: Proposed → **Accepted, after correction**; trigger definition corrected (event, not status field)
- `docs/INTERFACE_CONTRACTS.md` — new Section 5 contract entry, Section 7 table updated (2 new rows + CURRENT/TARGET column), 2 new addendum sections (17, 18)
- `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` — Part 2 superseding note appended (original text preserved)
- `docs/ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md` — Part 2 reconciling notes appended to both the Future-candidate and Forbidden-inputs entries (original text preserved)
- `docs/EVENT_CATALOG.md` — Sections 6, 8, 9 received TARGET notes for the new Passenger Experience events and the future Trip rename (no rename performed)
- `docs/README.md` — ADR table gained ADR-062/ADR-063 rows, plus a third continuation note matching the existing precedent
- `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` — status header, Section 17 (strengthened into a dedicated risk register), Section 19, and Section 21 (gate) updated to reflect this ratification

**Files deleted:** none.

**ADR status changes:** `ADR-062` Proposed → Accepted; `ADR-063` Proposed → Accepted (after correction). No other ADR's Status header was changed — `ADR-034`, `ADR-040`, `ADR-041`, `ADR-037` all received only body-text reconciliation notes, never a status change, since none of those decisions was itself reopened.

**Superseded decisions:** enumerated in full in Section 3 above.

**Tests/checks executed:** none — documentation/governance-only task, no code run, no code written.

**Production databases touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO

**Implementation gate status:** **OPEN** (Section 8 above).

**Remaining blockers:** none for the architecture gate itself. Separately, and explicitly not blocking that gate: the security risk register (Section 6) remains open as a production-hardening concern, and two implementation parameters (First Refusal's response-window duration; the exact trigger/endpoint shape) remain OPEN, both to be resolved by the implementation task itself, not by this ratification.
