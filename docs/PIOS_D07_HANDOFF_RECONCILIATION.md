# D-07 Handoff Reconciliation Report

**Type: READ-ONLY reconciliation. Not a decision, not an ADR, not an implementation authorization, not a design.** Prepared per explicit Product Owner instruction, "D-07 — HANDOFF PROTOCOL / FINAL READ-ONLY RECONCILIATION BEFORE IMPLEMENTATION." No code, schema, migration, test, or ADR was changed to produce this document. Repository state at time of writing: branch `pios-product-main`, `HEAD = ce71c3cbacb59b04dfa7205f73f54382f427fdac` (D-01/D-02/D-03/D-06 closed; D-06's corrective-pass commit `ce71c3c` local-only at the time of this reconciliation).

---

## 1. Executive Summary

**Handoff does not exist in this codebase in any form.** No `Handoff` class, table, endpoint, or event exists anywhere in `backend/`. This is independently confirmed by three separate sources already in the repository (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md:20,95`, `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:399`) and by this reconciliation's own direct code search (§4, §7, §8 below).

What *does* exist:

1. A complete, working D-01 commitment-termination state machine (`Trip.terminate`, `Assignment.terminate`, `CommitmentTerminationApplicationService`) that ends a commitment but never starts a new one for the same order.
2. A complete, working D-06 agreed-amount model (`Trip.agreedAmount`) that Handoff's own event design (§13) would need to extend, not replace.
3. A full, ratified, ADR-054-based prohibition of "any delegation mechanism of any kind" (`docs/ADR/ADR-054-...md:126`), narrowly and explicitly amended exactly once, for an unrelated purpose (Fallback Dispatch candidate filtering, `:128`).
4. A complete, unimplemented, developer-ready technical design for Handoff (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4) and ten open business questions (OQ-1…OQ-10) that design cannot proceed without (§6).
5. A structural fact this reconciliation confirms directly, not by inference: **`Assignment.create`'s own invariant makes it impossible, today, for a second `Assignment` to exist for an order that already has one — regardless of the first one's status, including `TERMINATED`.** This is the single fact that most constrains any D-07 implementation, and §15 answers the Product Owner's own "Critical Check" question about it directly.

The sixteen decisions in the current instruction read, almost point for point, as answers to OQ-1 (implicitly, via "kind of termination"), OQ-5, OQ-6, and design principles already stated in Part 4.2/4.8 of the evaluation — **with one exception (§6, OQ-3, "item 9 — Нет credit") that does not match any of the three previously-drafted options verbatim**, and one apparent tension (§15) between decision item 13 and the evaluation's own recommended technical shape. Both are flagged, not resolved, below.

---

## 2. Current Lifecycle

```
Order.submit()                                                    [order-management, Order.kt]
  → OrderSubmitted
Dispatch: Proposal.propose(order, driver)                          [Proposal.kt:335-355] → OPEN
  ↓
Proposal.accept(statedPrice?) OR Proposal.proposePrice()→PRICE_PROPOSED→Proposal.confirmPrice()
  ↓ → ProposalAccepted
ProposalAssignmentOrchestrationService.acceptProposal/confirmPrice  [ProposalAssignmentOrchestrationService.kt:122-172]
  → Assignment.create(order, driver, existingAssignments)           [Assignment.kt:184-201]
      -- REJECTS if existingAssignments contains ANY assignment for this order, any status (see §4, §15)
  → Trip.create(assignment, agreedAmount)                           [Trip.kt:147-190]
  ↓
Trip.arrive() / Trip.start()                                        [Trip.kt:98-117]
  ↓
Trip.complete()  → AssignmentCompleted (terminal-positive)          [Trip.kt:120-128]
      OR
Trip.terminate(fact)  → CommitmentTerminated (terminal-negative)    [Trip.kt:130-137]
      -- reachable from CREATED, ARRIVED, IN_PROGRESS; never from COMPLETED or TERMINATED
```

Both `Trip.complete()` and `Trip.terminate()` are genuinely terminal: `TripStatus` (`TripStatus.kt:25-30`) and `AssignmentStatus` (`AssignmentStatus.kt:18-24`) each have exactly one positive terminal state (`COMPLETED`) and one negative terminal state (`TERMINATED`), and no state machine transition in either class re-enters a non-terminal state from either.

---

## 3. D-01 Termination Integration

**States.** `TripStatus`: `CREATED, ARRIVED, IN_PROGRESS, COMPLETED, TERMINATED` (`TripStatus.kt:25-30`). `AssignmentStatus`: identical five plus no additional state (`AssignmentStatus.kt:18-24`). Neither enum has a "cancelled," "reassigned," or "handed off" value — confirmed directly by reading both files in full.

**Application services that change them:**
- `DispatchAssignmentApplicationService.arriveAssignment/startAssignment/completeAssignment` — positive path (`DispatchAssignmentApplicationService.kt:283-331`).
- `CommitmentTerminationApplicationService.terminate` — negative path (`CommitmentTerminationApplicationService.kt:41-117`).

**Locking.** Every one of the above calls `orderGuard.lock(order)` (`PostgreSQLOrderGuard.kt:9-17`) — a real `SELECT ... FOR UPDATE` on a `dispatch_order_guards` row, taken first, before any other row lock, inside the same transaction. `CommitmentTerminationApplicationService.terminate` (line 52) and `DispatchAssignmentApplicationService.completeAssignment` (line 320) both lock the *same* order-scoped row, which is what gives completion-vs-termination a single first committer (confirmed empirically by the real-PostgreSQL race test `CommitmentTerminationPostgreSQLTest.kt:83-112`, unmodified by this reconciliation).

**Events published:**
- `CommitmentTerminated` (`commitment.terminated`) — `CommitmentTerminationApplicationService.kt:146-163`, payload `{requestId, orderId, assignmentId, tripId, driverId, initiator, reasonCode, note, terminatedAt}`.
- `OrderCancellationResolved` (`order.cancellation-resolved`) — same file, lines 133-144, for the no-commitment-yet and already-terminal outcomes.
- Consumed inbound: `OrderCancellationRequested`, via `OrderCancellationRequestedListener.kt:11-40`, which calls `CommitmentTerminationApplicationService.terminate` directly with `initiator = PASSENGER`.

**Invariants that forbid further execution of a terminated commitment:**
- `Trip.terminate` itself: `check(status != COMPLETED && status != TERMINATED)` (`Trip.kt:130-133`) — a terminated Trip cannot be terminated again, and (per `arrive`/`start`/`complete`'s own `check(status == ...)` preconditions) cannot progress, because none of those three methods accepts `TERMINATED` as a starting state.
- `Assignment.terminate`: symmetric guard, `check(status != AssignmentStatus.COMPLETED && status != AssignmentStatus.TERMINATED)` (`Assignment.kt:165-167`).
- **`CommitmentTerminationApplicationService.terminate` itself refuses to act on more than one Assignment per order**: `check(matchingAssignments.size <= 1) { "Multiple assignments for order ${order.orderId} require reconciliation" }` (`CommitmentTerminationApplicationService.kt:63`) — this line is a second, independent piece of evidence (beyond `Assignment.create`'s own guard, §4) that the *entire* current termination model assumes exactly zero or one Assignment ever exists per order, for the whole lifetime of that order.

**Can a new Assignment exist for the same Order after termination? Can a new driver get the same Order? Can Trip #2 exist, application-level?**

**No, to all three, today.** See §4 for the exact mechanism and §15 for the full "Critical Check" answer.

**Redispatch path?** None exists that operates *after* an Assignment has ever been created for an order (see §4's FallbackDispatch analysis — it is a pre-Assignment mechanism only, and is itself structurally prevented from firing once a Proposal is live).

---

## 4. Assignment/Reassignment Reality

**The exact invariant, verified by direct read:**

```kotlin
// backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt:184-201
fun create(
    order: OrderReference,
    driver: DriverReference,
    existingAssignments: Collection<Assignment> = emptyList(),
    isTest: Boolean = false
): AssignmentCreated {
    check(existingAssignments.none { it.order == order }) {
        "Order ${order.orderId} already has an active assignment"
    }
    ...
}
```

The check is **unconditional on status** — it inspects only `it.order == order`, never `it.status`. A `TERMINATED` Assignment for an order still satisfies `it.order == order` and therefore still blocks a second one, exactly as a `CREATED` or `COMPLETED` one would.

**Both real call paths pass every existing Assignment for the order into this check, not just active ones:**
- `DispatchAssignmentApplicationService.handleWithinCallerTransaction` (line 191): `val existingAssignments = assignmentRepository.findByOrder(command.order)`.
- `PostgreSQLAssignmentRepository.findByOrder` (`PostgreSQLAssignmentRepository.kt:132-141`): `SELECT ... FROM assignments WHERE order_reference = ?` — no `WHERE status <> 'TERMINATED'` filter of any kind.
- The manual-assignment controller path (`AssignmentController.assignOrder`, line 179) calls the self-fetching `handle(command)` overload, which internally calls `handleWithinCallerTransaction` — same guard, same result.

**Conclusion: there is no code path, manual or Proposal-driven, by which a second `Assignment` can be created for an order that already has one, of any status.** This reconciliation independently confirms the finding first surfaced during D-06 (`CommitmentTerminationPostgreSQLTest.kt:305-313`, itself a comment recorded during that work, not modified here).

**Substitute/handoff/delegation/reassignment/redispatch/transfer/replacement — searched exhaustively across `backend/dispatch`, `backend/order-management`, `backend/driver-management`:** zero implemented domain mechanisms found. Every hit is one of: (a) a comment/KDoc explicitly *rejecting* the concept, (b) an unrelated English usage (e.g., "not a replacement for X" about architecture, "substitute" in an IDOR-analysis sentence), or (c) a test name proving the *absence* of the mechanism. No `Handoff`, `Delegation`, `Substitute`, `Reassign`, `Redispatch`, or `Transfer` class exists anywhere under `backend/dispatch/src/main/kotlin` (confirmed by glob, zero matches).

**FallbackDispatch (ADR-078) is not a handoff mechanism and does not overlap with one:**
- File: `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt`.
- Triggers: no Proposal from First Refusal; a Proposal's own `decline`/`lapse`; or the `dispatch_requests` sweep reopening `OFFERED → PENDING` on decline/lapse (`ADR-078` Decision A/B).
- Mechanically, it only ever calls `proposalApplicationService.handle(ProposeDriverCommand(...))` — it creates a new **Proposal**, never touches `Assignment` or `Trip`.
- It is structurally unreachable once any Proposal for the order is live (`OPEN`/`PRICE_PROPOSED`/`ACCEPTED`) — `DispatchRequestApplicationService`'s own short-circuit predicate stops it the moment routing state leaves `OFFERED`.
- It is therefore **strictly pre-Assignment**. It never replaces a driver on an order that already has a committed Assignment — that is precisely the gap Handoff (unbuilt) is designed to fill, and the evaluation's own Part 4 design explicitly scopes Handoff as the complementary, post-Assignment case (`RELATIONSHIP_MODEL_EVALUATION.md:219`: "no handoff of an order that has no `Assignment` yet — that is a decline").
- A specifically relevant, already-ratified, already-implemented principle: named-driver orders are **never substituted** by FallbackDispatch either — `DispatchRequestApplicationService.kt:133-141` and `Proposal.kt:239-243` both implement "PIOS does not substitute a random driver for the one a client came to" as live code, not just documentation.

---

## 5. ADR-054 Analysis

File: `docs/ADR/ADR-054-Circle-of-Trust-Passenger-Experience-Owned-Primary-Driver-Relationship.md` (read in full).

**What it actually decides (Parts 1-4):** places the passenger's "circle of trust" (several recognized drivers, at most one `primary`) in Passenger Experience, as an extension of the existing `Connection` concept — a new `primary_connections` table (`PrimaryConnection`), keyed by `passenger_reference`, with a composite FK back into `connections`. Zero Dispatch involvement (Part 3): no new cross-module contract, no Assignment Policy input, no ranking.

**What it explicitly forbids (Part 5, line 126):**

> **No Team, Fleet, crew, or delegation mechanism of any kind.** ... No fallback-to-another-driver behavior, no group, no shared client list.

**The one, narrow, already-exercised amendment (line 128, dated 2026-09-15):**

> Partially superseded ... [ADR-068] narrowly supersedes "no fallback-to-another-driver behavior" — **for candidate-set filtering only**, on Fallback Dispatch's existing selection query, when the passenger's primary driver has declined or lapsed. Nothing else in this sentence or this Part is superseded: still no Team/Fleet/crew/delegation mechanism of any kind...

**What this means for D-07, precisely:**

- **What conflicts with D-07:** the ban's plain text — Handoff *is* a delegation mechanism by any reading of line 126, and the ADR's own "What This ADR Does Not Authorize" section repeats the prohibition of "any Team/Fleet/crew concept or delegation or fallback mechanism" without qualification.
- **What must change:** a **second**, equally narrow, dated, in-place supersession pointer into line 126 — the same pattern ADR-068's own amendment already established at line 128 — authorizing *only* single-hop, driver-initiated, passenger-consented Handoff, and nothing broader. This is exactly what the evaluation's own §4.1 already recommends and what decision item 16 of the current instruction restates ("ADR-054 требует узкого amendment, а не полного удаления запрета").
- **What does NOT need to change:** everything else in ADR-054 — Parts 1-4 (Circle of Trust's own placement, schema, Dispatch-neutrality) are untouched by Handoff under every existing design (decision items 6, 11, 15 of the current instruction: "Handoff не изменяет Connection"). The already-exercised ADR-068 amendment at line 128 is also untouched — it covers a different, pre-Assignment case (Fallback candidate filtering) that D-07's Handoff never overlaps with (§4).
- **Related, reinforcing prohibitions checked and found consistent, not conflicting:** `ADR-068:166` (rejects driver-declared-peer "vouching" as a separate delegation shape — directly relevant to OQ-2); `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md:95` ("[PRINCIPLE] The relationship should not be silently reassigned" — directly relevant to decision item 11); `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md:107,148` (Part 8's still-open "can it be delegated, e.g. to a Fleet?" question, which the current instruction's decision item 1-16 set answers *specifically for single-hop driver-to-driver Handoff*, not for Fleet/Team delegation in general — that broader question remains untouched and still open).

---

## 6. OQ-1…OQ-10 Matrix

Source of all ten: `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 6 (`:247-260`), elaborated with options in `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`.

| OQ | Question | Current status / Evidence | Resolved / Open | What D-07's current instruction already closes | What still requires explicit PO decision |
|---|---|---|---|---|---|
| **OQ-1** | May a driver hand off at all, and at which points (before arrival only, vs. also mid-ride)? | Three options A/B/C in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:15-19`. No prior PO answer found in any document. | **Open** (timing not addressed) | Decision item 1 ("Handoff = разновидность termination") establishes Handoff is possible and frames it structurally, but does **not** state at which lifecycle point(s) it may be proposed. | Whether Handoff may be proposed only pre-arrival (Option B) or also mid-ride (Option C) — this is not decidable from the current instruction's 16 items. |
| **OQ-2** | Is "my driver named this substitute" itself a trust claim PIOS renders to the passenger? | Three options A/B/C in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:25-29`; directly collides with `ADR-068:166`'s existing rejection of vouching if answered "A". | **Open** | None of items 1-16 addresses consent-screen wording or framing. | Whether/how the substitute is presented to the passenger (neutral fact vs. endorsement). |
| **OQ-3** | After a consented handoff, whose ride-count/earnings increment? | Three options A (executing)/B (committing)/C (split by kind) in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:35-39`; D-08 (decision brief, `:1763-1774`) recommends Option C as a *binding condition*, not yet confirmed accepted anywhere. | **Open, and possibly newly ambiguous** | Decision item 9 ("Нет credit") does **not** match any of A/B/C verbatim — none of the three drafted options says "neither driver receives credit." This is flagged, not resolved, in §15/§16 below: it may mean (a) a fourth option — no attribution to either driver for this ride — or (b) shorthand for "no *financial* credit" (consistent with items 7/8, "no fee"/"no split"), leaving the *ride-count* attribution question (OQ-3's actual subject) still genuinely open. | **Requires clarification before implementation**: does "нет credit" answer OQ-3 itself (ride-count/earnings attribution), or does it only restate "no money" (items 7-8)? If the latter, OQ-3/D-08(b)'s split-attribution question remains open. |
| **OQ-4** | If the passenger refuses, what happens to the commitment? | Three options A (stays committed, Part 4's own default)/B (no-penalty cancel offered)/C (both) in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:45-49`. | **Open** | None of items 1-16 addresses refusal consequences. | Whether refusal also unlocks a no-penalty cancel (would require an `ADR-053` carve-out per the evaluation, `:48`). |
| **OQ-5** | Does any fee/split/commission attach? | Three options A (never)/B (commission on substitute)/C (off-platform, no PIOS role) in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:57-59`. | **Closed by the current instruction** | Decision items 7 ("Нет fee") and 8 ("Нет payment split") together read as Option A/C combined — no PIOS financial role of any kind attaches to Handoff. | None — this reads as fully answered. |
| **OQ-6** | May a substitute hand off again (chained handoff)? | Two options A (no chaining, v1 scope)/B (chaining allowed) in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:67-68`; Part 4 itself already defaulted to A as a *scoping* choice, explicitly not a product answer. | **Closed by the current instruction** | Decision items 2 ("только single-hop") and 3 ("Chained handoff запрещён") directly and unambiguously answer OQ-6 = A, and elevate it from Part 4's scoping default to an actual ratified product decision. | None. |
| **OQ-7** | Does a handoff cap exist (per driver/day/client)? | Three options A (no cap)/B (numeric cap)/C (no formal cap, monitored/reported) in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:73-76`; D-08(a) (decision brief `:1763-1774`) recommends C as a *binding shipping condition* ("observation is a shipping condition. No Handoff without an owner-facing view of handoff frequency and concentration per driver"). | **Not closed by the current instruction's 16 items** | Decision item 10 ("не должен превращаться в механизм автоматического перераспределения клиентов") gestures at the same *concern* D-08(a)/OQ-7-Option-A's risk note names (the "informal fleet backdoor"), but **does not itself state a cap, or confirm the observation-before-cap binding condition** that D-08 proposed to resolve it. | **Requires explicit confirmation**: is D-08(a)'s "observation is a shipping condition" accepted as binding? Item 10 alone is a stated concern, not a mechanism decision (no cap value; no confirmed observability requirement). |
| **OQ-8** | Settlement: what constitutes "settled"? | Named as adjacent, not Handoff-specific, in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:84`. D-06 ("Settlement as Evidence," already closed) built `Trip.agreedAmount` but explicitly does not define "settled" or implement payment status. | **Open, out of D-07's own scope** | None — the current instruction's 16 items are entirely about Handoff, not Settlement. | Untouched by D-07; remains its own, separately-scoped question. |
| **OQ-9** | Was ADR-065's digits-only price-parsing rule ever formally confirmed? | Named as adjacent in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:85`. `ADR-065`'s own Status line (read during D-06 work) already records Product Owner confirmation, 2026-09-07 — the decision brief itself calls this a `[DRIFT]` correction candidate (§ found in agent research above, brief lines ~1440/465), i.e. arguably no longer actually open. | **Effectively resolved** (per ADR-065's own Status section), independent of D-07 | Not addressed by D-07's 16 items — correctly so, since it isn't a Handoff question. | None, from D-07's perspective. |
| **OQ-10** | Does ADR-073's `invitedByDriverId` change ADR-068's Tier-2/Network candidate answer? | Named as adjacent, "entirely unrelated to Handoff," in `PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:86`. | **Open, ADR-068's own reserved question** | Not addressed, correctly. | Untouched by D-07; belongs to ADR-068's own Q2. |

---

## 7. Existing API Surface

**Proposal acceptance:** `POST /v1/proposals/{proposalId}/accept`, `.../propose-price`, `.../confirm-price`, `.../decline-price`, `.../decline`, `.../lapse` — all `ProposalController.kt` (lines 254, 296, 357, 391, 441, 492).

**Assignment creation:** `POST /v1/assignments` (deprecated manual path) — `AssignmentController.kt:163`.

**Assignment termination:** `POST /v1/assignments/{assignmentId}/terminate` — `AssignmentTerminationController.kt:36`, driver-only (session token's `drv` must equal `assignment.driver.driverId`, line 50).

**Trip lifecycle:** `POST /v1/assignments/{assignmentId}/arrive|start|complete` — `AssignmentController.kt:251,279,307` (still HTTP-surfaced on the `/v1/assignments` path per ADR-040's own placement decision, even though the underlying state now lives on `Trip`).

**Order cancellation:** `POST /v1/orders/{orderId}/cancel` — `OrderCancellationController.kt:99` (order-management; publishes `OrderCancellationRequested`, resolved asynchronously by Dispatch via `OrderCancellationRequestedListener.kt`).

**Driver actions:** none beyond the above and the pre-Assignment `arrive/start/complete`/`terminate` set; no separate "driver-initiated handoff request" endpoint exists.

**Passenger actions:** order submission (`OrderSubmissionController.kt`), order cancellation (above), and Circle-of-Trust management (`POST /v1/connections/{connectionId}/primary`, `DELETE /v1/connections/{connectionId}` — per ADR-054 Part 4, confirmed implemented: `SetPrimaryConnectionApplicationService.kt` exists). No "handoff consent" endpoint exists.

**Handoff-specific endpoints — searched, none exist:**
- Handoff request: **absent.**
- Substitute proposal: **absent.**
- Substitute acceptance: **absent.**
- Passenger consent (for a handoff specifically — Circle-of-Trust's own primary-designation consent is a different, unrelated concept): **absent.**
- Driver replacement / reassignment: **absent.**

This section records absence only, per instruction; no endpoint shape is proposed here (the evaluation's Part 4.6/4.7 already sketches one, but that remains an unauthorized proposal, not this document's business).

---

## 8. Existing Events

Checked against `docs/EVENT_CATALOG.md` Section 6/8/9 and direct source reads:

- `OrderProposed`, `ProposalAccepted`, `ProposalDeclined`, `ProposalLapsed`, `ProposalWithdrawn` — Proposal events, internal-only (no outbox write; `EVENT_CATALOG.md:70`).
- `OrderAssigned`, `AssignmentAccepted`, `AssignmentArrived`, `AssignmentStarted`, `AssignmentCompleted` — Assignment events (`EVENT_CATALOG.md:55-59`).
- `TripArrived`, `TripStarted`, `TripCompleted` — Trip events, dual-published alongside the `Assignment*` ones, not yet consumed by anything (`EVENT_CATALOG.md:60-62,106`).
- `PrimaryConnectionDesignated`, `PrimaryConnectionCleared` — Passenger Experience → Dispatch, First Refusal projection (`EVENT_CATALOG.md:92-93,108`).
- `CommitmentTerminated`, `OrderCancellationResolved`, `OrderCancellationRequested` — confirmed live in code (`CommitmentTerminationApplicationService.kt:133-163`, `OrderCancellationRequestedListener.kt`), **but not yet present in `EVENT_CATALOG.md`** — this reconciliation notes the catalog has not been updated since D-01 shipped; a pre-existing documentation-freshness gap, unrelated to and not created by D-07, flagged here for completeness only, not to be fixed in this pass.

**No handoff event exists** — no `HandoffProposed`, `HandoffConsented`, `HandoffResolvedNegatively`, or any equivalently-named event appears anywhere in `backend/`. The evaluation's Part 4.6 (`:198-208`) proposes this exact three-event minimum, but it is an unimplemented proposal.

---

## 9. Authorization Reality

**Current primitives, confirmed by direct read:**

- `SessionTokenVerifier` — verifies a `Bearer` token and exposes `.sub` (identity id) and `.drv` (driver id, nullable) (`AssignmentController.kt:198`, `AssignmentTerminationController.kt:42`, etc.).
- **Driver-side checks** compare `verified.drv` against `assignment.driver.driverId` — e.g. `AssignmentController.arrive/start/complete` (`:262,290,318`), `AssignmentTerminationController.terminate` (`:50`). This is the *only* existing pattern for "is this caller the committed driver."
- **Passenger-side checks** compare `verified.sub` against a Proposal's own `passengerReference.passengerId`, via `proposalRepository.findByOrder(...)` — e.g. `AssignmentController.listAssignmentsHttp` (`:206-207`, confirmed and reused unmodified in D-06's own frontend work this session).
- **No third-party (substitute-driver-before-acceptance) authorization pattern exists** — every current check assumes the caller is either the order's single committed driver or its single passenger; there is no notion of "a driver who has been named but has not yet accepted anything."

**Who could plausibly initiate what, under the *existing* primitives (not proposing new ones):**

- **Original driver's handoff proposal**: could reuse the existing driver-check pattern (`verified.drv == assignment.driver.driverId`), the same way `terminate` already does — a handoff proposal is, after all, decision item 1's own framing ("a kind of termination").
- **Passenger consent**: could reuse the existing passenger-check pattern (`verified.sub == proposal.passengerReference.passengerId`), the same way `listAssignmentsHttp`'s passenger branch already does.
- **Substitute acceptance**: **no existing primitive covers this.** The substitute is, by definition, not yet the assignment's own `driver`, and is not a `passengerReference` on any Proposal for this order. Every existing check would reject them. This is a genuine gap, not an oversight to paper over — see §12 and §15.

No new authorization model is introduced or proposed by this document.

---

## 10. Data Model Reality

**What exists today, for each named role:**

- **Original driver**: `Assignment.driver: DriverReference` (`Assignment.kt`), set once at `Assignment.create`, never rewritten by any existing code path.
- **Passenger**: `Proposal.passengerReference: PassengerReference?` (`Proposal.kt`), nullable, set at `Proposal.propose`; nothing on `Assignment` or `Trip` carries it directly — it must be reached via the order's own `ACCEPTED` Proposal.
- **Relationship**: `Connection(id, driverId, passengerReference, createdAt)` — passenger-experience, immutable, no status/type field of any kind (`Connection.kt:9-11,18-22`, quoted directly). `PrimaryConnection` (a separate table, `primary_connections`, `passenger_reference` primary key, composite FK into `connections`) — the mutable, passenger-changeable "primary" designation (ADR-054 Part 2, confirmed implemented via `PrimaryConnectionRepository.kt`, `SetPrimaryConnectionApplicationService.kt`).
- **Proposal**: `Proposal(id, order, driver, status, statedPrice, statedEtaMinutes, passengerReference, ...)` — `Proposal.kt`.
- **Assignment**: `Assignment(id, order, driver, status, isTest, ...timestamps)` — `Assignment.kt`. No second driver field of any kind.
- **Trip**: `Trip(id, assignmentId, order, driver, agreedAmount, isTest, status, ...timestamps, termination)` — `Trip.kt`, as of D-06. No second driver field, no `executingDriver` field.

**Where a substitute executor, original executor, handoff request, or passenger consent *could theoretically* be represented — inspected, not designed:**

- **Original executor**: already represented, unambiguously, by `Assignment.driver`.
- **Substitute executor**: no existing field anywhere. The evaluation's own Part 4.4/4.5 proposes a new `Handoff.toDriver` plus a new `Trip.executingDriver` — neither exists.
- **Handoff request**: no existing entity anywhere. The evaluation's Part 4.3 proposes a genuinely new Dispatch-owned aggregate for exactly this reason (a second `Proposal` would hit `Assignment.create`'s own guard, per §4/§15; a field on `Assignment` conflicts with that aggregate's now-frozen-remnant status per D-06's own KDoc, `DispatchAssignmentApplicationService.kt:93-94`; a field on `Trip` conflates "who is committed" with "the execution record").
- **Passenger consent (for a handoff specifically)**: no existing entity. `PrimaryConnection`'s own designation is a different, unrelated kind of passenger act (choosing a favorite driver in general, not consenting to a specific ride's substitute).

No field or migration is proposed, added, or recommended by this document.

---

## 11. Concurrency Risks

**D-01's own locking discipline** (§3): every write path that touches `Assignment`/`Trip`/`dispatch_requests` for a given order takes `PostgreSQLOrderGuard.lock(order)` first, inside the same transaction, before any aggregate-specific row lock — a real `SELECT ... FOR UPDATE` on `dispatch_order_guards`. This is what currently gives completion-vs-termination "a single first committer" (`ADR-080` Decision text, verified against `CommitmentTerminationPostgreSQLTest.kt`'s own passing real-PostgreSQL race tests, unmodified here).

**Races a D-07 implementation would need to protect against** (described, not implemented, per instruction):

1. **Driver termination vs. passenger handoff-consent** — if `CommitmentTerminationApplicationService.terminate` and a hypothetical `HandoffConsented`-producing service both act on the same order without sharing the same `orderGuard.lock(order)` call inside the same transaction, both could observe a live commitment and each try to resolve it terminally in a different way (one to `TERMINATED`, one to a swapped `executingDriver`).
2. **Passenger consent vs. termination** — a passenger consenting to a handoff at the exact moment the original driver (or the passenger themselves, via a different channel) independently terminates the same commitment.
3. **Substitute acceptance vs. termination** — the named substitute accepting the handoff at the same moment the original driver withdraws it or the commitment is otherwise terminated.
4. **Two concurrent handoff attempts** — the evaluation's own Part 4.4 already names the needed invariant: "at most one non-terminal `Handoff` per `assignmentId`," and explicitly prescribes the same two-layer defense (in-memory factory check *and* a partial unique index) this codebase already uses for `Trip.create`'s own `trips.assignment_id UNIQUE` constraint (`Trip.kt:49-54`) — because, per that same KDoc's own stated lesson, an in-memory check alone loses the check-then-create race.
5. **Stale handoff after commitment already terminated** — a `HandoffProposed`/`SUBSTITUTE_ACCEPTED` record surviving past the moment its own `Assignment`/`Trip` reaches `TERMINATED` or `COMPLETED` through an unrelated path, and later being consented into a commitment that no longer exists to be handed off.
6. **Chained handoff attempt** — decision items 2/3 forbid this at the product level; a concurrency-relevant enforcement question exists too: must the invariant be checked against the *original* `fromDriver` (per the evaluation's own Part 4.4 note that `fromDriver` "is always the original committing driver") under lock, or could a race let two handoffs originating from the same substitute both begin before either resolves?
7. **Handoff against an already-assigned substitute** — a second handoff proposal naming a *different* substitute while an earlier one is still pending `SUBSTITUTE_ACCEPTED`/awaiting passenger consent for the same `assignmentId` — this is the same shape as race #4, restated for the specific case of overlapping, not merely concurrent, requests.

None of these is tested or fixed by this reconciliation; they are named per the instruction's own explicit ask (§I).

---

## 12. D-07 Gap Analysis

**1. What already exists:**
- A working, lockable, terminal-state commitment lifecycle (D-01) that Handoff's own "kind of termination" framing (decision item 1) could build on.
- A working agreed-amount model (D-06, `Trip.agreedAmount`) that any Handoff-driven `executingDriverId` attribution work would sit beside.
- A ratified, narrowly-amendable prohibition (ADR-054 Part 5) with an existing precedent for how to amend it narrowly (ADR-068's own line-128 amendment).
- A complete, developer-ready (but unauthorized) technical design (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4).
- Existing driver-side and passenger-side authorization primitives that cover two of Handoff's three parties (original driver, passenger) but not the third (substitute).

**2. What is missing:**
- Any `Handoff` (or equivalently-named) domain aggregate, table, or persistence code.
- Any handoff-specific endpoint (propose, substitute-accept, passenger-consent, decline paths).
- Any handoff-specific event.
- Any authorization primitive for "a driver who has been named as a substitute but has not yet accepted."
- A resolved answer to OQ-1 (timing), OQ-2 (consent-screen framing), OQ-4 (refusal consequence), and a *fully unambiguous* answer to OQ-3/OQ-7 (see §6's flags).
- A dated, narrow ADR-054 supersession — decision item 16 confirms this is required but does not itself constitute one.

**3. What D-07's current instruction actually requires (as stated, read literally):**
- Build Handoff as a species of termination (item 1), compatible with D-01's own state machine (item 12).
- Enforce single-hop only, no chaining, structurally (items 2, 3, 14).
- Passenger consent blocking and constitutive (item 5).
- No Connection/relationship-ownership change of any kind (items 6, 11, 15).
- No fee/split/credit (items 7, 8, 9 — with the OQ-3 ambiguity flagged in §6).
- The original commitment must not remain executable after a handoff (item 13) — see §15 for why this is the single most load-bearing, and most under-specified, item in the list.
- A narrow ADR-054 amendment, not a rewrite (item 16).

**4. What must NOT be implemented** (per both the current instruction and every source document it cites):
- Any PIOS-selected or automatic substitute (item 4; `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md:219`).
- Chained handoff (items 2-3; OQ-6 now closed = A).
- Any fee, commission, split, or credit of any financial kind (items 7-9, OQ-5 closed = A/C).
- Any Connection/PrimaryConnection mutation as a side effect of Handoff (items 6, 11, 15; ADR-054 Parts 1-4 unamended).
- Any general Team/Fleet/crew/delegation mechanism beyond the single, named, narrow case (ADR-054 Part 5's un-superseded remainder).
- A ride-outcome taxonomy, a settlement mechanism, or any payment/billing change (out of D-07's own scope, per OQ-8; also `CLAUDE.md`'s "Never Invent Business Rules").

**5. What requires additional PO decision before implementation can begin:**
- OQ-1 (timing window).
- OQ-2 (consent-screen framing/vouching question).
- OQ-3's exact meaning, given item 9's ambiguity (§6).
- OQ-4 (refusal consequence).
- OQ-7 / D-08(a)'s observation-before-cap condition — not yet explicitly confirmed as binding by the current instruction.
- The tension in §15 between item 13 and the evaluation's own "retain Assignment, swap `Trip.executingDriver`" recommended shape.
- The actual ADR-054 supersession text itself (item 16 requires it, does not provide it).

**6. Recommended implementation sequence** (sequencing observation only, not an implementation plan, and not authorized by this document):
1. Resolve the §15 tension and the OQ-3/item-9 ambiguity with the Product Owner — both are logically prior to any schema or aggregate design, since they determine whether Handoff terminates-and-recreates or retains-and-redirects the commitment.
2. Resolve OQ-1, OQ-2, OQ-4, and confirm/decline D-08(a)'s binding observation condition.
3. Write the narrow, dated ADR-054 supersession (item 16), citing the specific decisions above.
4. Only then: aggregate/schema/endpoint/event design — a distinct, later phase, out of this reconciliation's own scope.

---

## 13. Implementation Boundary

Restated concisely from §12 for direct traceability to the instruction's own "J" section: the boundary today is **exactly the line drawn by `Assignment.create`'s invariant** (§4, §15). Everything on the "termination" side of that line (ending a commitment) is built and proven. Everything on the "a different driver now executes this same order" side of that line is entirely unbuilt, and the two existing candidate shapes for crossing it (a new Assignment vs. a same-Assignment `executingDriver` swap) have different, currently-undecided consequences for item 13's own requirement.

---

## 14. Open PO Decisions

Consolidated from §6 and §12.5:

1. **OQ-1** — handoff timing window (pre-arrival only, or also mid-ride).
2. **OQ-2** — consent-screen framing (neutral fact vs. endorsement).
3. **OQ-3 / item 9** — does "нет credit" answer the ride-count/earnings attribution question itself, or only the financial-credit question (items 7-8)? If only the latter, OQ-3's actual attribution split (executing / committing / split-by-kind) remains open.
4. **OQ-4** — consequence of passenger refusal (status quo only, vs. an additional no-penalty cancel option).
5. **OQ-7 / D-08(a)** — is "observation before cap" accepted as a binding shipping condition, or does item 10 alone (a stated concern, not a mechanism) suffice?
6. **The §15 tension** — is the original `Assignment`/`Trip` actually terminated (new Assignment created for the substitute, which today requires resolving the `Assignment.create` invariant question) or retained with a swapped `executingDriver` (the evaluation's own Part 4.5 recommendation, which does not terminate anything)? Item 13's wording ("исходное обязательство не должно оставаться executable") is consistent with either reading in isolation but the two readings imply materially different, mutually exclusive technical designs.

---

## 15. Explicit Answer: Can D-07 Be Implemented Without Changing `Assignment.create`'s Fundamental Model?

**Short answer: it depends entirely on which of two designs is chosen for what "handoff" means at the data-model level — and the current instruction's own wording (item 13) does not yet unambiguously pick one.**

**Design 1 — retain `Assignment`/`Trip`, add `Trip.executingDriver` (the evaluation's own Part 4.5 recommendation).**

- **Yes**, this is implementable **without any change to `Assignment.create`'s invariant.** No second `Assignment` is ever created; `Assignment.driver` continues to record "who committed" and is never rewritten; only `Trip` gains a new field, changed exactly once, at `CONSENTED`.
- Existing lifecycle path this uses: the *same* `Assignment`/`Trip` row that already exists for the order — no new row, no new `Assignment.create` call at all.
- What *would* need to change, precisely, and it is not `Assignment.create`: the three ride-progress authorization checks (`AssignmentController.arrive/start/complete`, currently `verified.drv != assignment.driver.driverId`) would need to compare against the Trip's own executing driver instead — a change to `AssignmentController.kt`, not to `Assignment.kt`'s own invariant.
- **The open question this design leaves unresolved is item 13's own wording**: under this design, the *original* `Assignment` is never terminated and never becomes non-executable in the D-01 sense — `Assignment.driver` still names the original driver, forever, as a historical fact of who committed (exactly as D-06 leaves `Proposal.statedPrice` as a historical fact after Trip.agreedAmount supersedes it for authority). Whether that satisfies "the original obligation must not remain executable" depends on reading "obligation" narrowly (the *right to execute the ride*, which the authorization-check change above does remove from the original driver) or broadly (the *Assignment/Trip record itself*, which under this design is never marked `TERMINATED`).

**Design 2 — terminate the original `Assignment`/`Trip` (via the existing D-01 machinery, `initiator = DRIVER`), then create a new `Assignment`/`Trip` for the substitute.**

- **No**, this is **not** implementable without first resolving `Assignment.create`'s invariant, because §4 already proves, directly from the code, that `Assignment.create` unconditionally rejects a second Assignment for an order that already has one — including one that has just been marked `TERMINATED` by this same flow, one line earlier. This is not a hypothetical; it is the literal, unconditional behavior of the `check(existingAssignments.none { it.order == order })` guard, independent of status.
- This design reads more naturally against decision item 13's own plain wording ("the original obligation must not remain executable" — genuinely true under this design, since the original `Assignment.status` becomes `TERMINATED`, exactly like any other D-01 termination) and against item 1's framing ("Handoff = a kind of termination," which literally means *terminating* something, not merely redirecting execution rights on something that stays alive).
- Making this design possible would require a genuine, scoped change to the fundamental model — not necessarily removing the invariant outright (this same invariant was already found load-bearing for a *different* reason during the D-06 implementation work: the D-06 reconciliation analysis, delivered as this session's own chat output rather than as a committed document, found that `Assignment.create`'s status-blind guard is also what makes the multi-`ACCEPTED`-Proposal-per-order ambiguity structurally unreachable today, i.e. the same invariant is doing double duty as a safety property elsewhere), but at minimum teaching `Assignment.create`'s check to exclude a `TERMINATED` Assignment that is itself the direct predecessor of the new one being created, under the same order-guard lock. This is exactly the kind of change the current instruction says **not** to make in this pass ("НЕ исправляй это. Только зафиксируй."), and is out of scope for any future pass too unless the Product Owner explicitly authorizes changing this specific invariant.

**Conclusion, stated once more for clarity:** Design 1 requires no `Assignment.create` change and is buildable today, on the existing lifecycle path, once ADR-054 is amended and OQ-1/OQ-2/OQ-4/OQ-3-ambiguity/OQ-7 are resolved. Design 2 is blocked by `Assignment.create`'s current, unconditional, status-blind invariant, and cannot proceed without a Product Owner decision to change that specific invariant — which this reconciliation does not recommend either way, per its own explicit read-only, no-workaround instruction. **Which design D-07 actually intends is not yet decided by the current instruction's sixteen items**, and this is the single most consequential open question this reconciliation surfaces.

---

## 16. Proposed Next Step

Not an implementation plan — a sequencing observation only, offered because the instruction's own §J asked for one:

1. The Product Owner resolves the §15 design choice (retain-and-redirect vs. terminate-and-recreate) — everything else in this reconciliation is downstream of that single choice.
2. The Product Owner resolves the item-9/OQ-3 ambiguity and OQ-1, OQ-2, OQ-4, OQ-7/D-08(a) (§14).
3. Only then does the narrow, dated ADR-054 supersession get written (item 16) — citing the resolved design and the resolved OQs by number, the same pattern ADR-068's own line-128 amendment already set as precedent.
4. Schema/aggregate/endpoint/event design follows the ratified ADR, as a distinct, later, explicitly-authorized implementation phase.

This document takes no position on which design the Product Owner should choose.

---

## Evidence Index (files read in full or in the cited sections, none modified)

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Trip.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/AssignmentStatus.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/TripStatus.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Termination.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/CommitmentTerminationApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/OrderGuard.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLOrderGuard.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLAssignmentRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/OrderCancellationRequestedListener.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentTerminationController.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/OrderCancellationController.kt`
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/domain/Connection.kt`
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/application/PrimaryConnectionRepository.kt`
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/CommitmentTerminationPostgreSQLTest.kt`
- `docs/ADR/ADR-054-Circle-of-Trust-Passenger-Experience-Owned-Primary-Driver-Relationship.md`
- `docs/ADR/ADR-068-Relationship-Ordered-Fallback-Dispatch.md`
- `docs/ADR/ADR-078-Dispatch-Recovery-After-Proposal-Decline-Or-Lapse.md`
- `docs/ADR/ADR-080-Commitment-Termination-and-Order-Cancellation-Handshake.md`
- `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`
- `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md`
- `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md`
- `docs/PIOS_AI_HANDOFF.md`
- `docs/ATLAS_IMPLEMENTATION_RECONCILIATION.md`
- `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`
- `docs/PRODUCT_DECISION_CIRCLE_OF_TRUST.md`
- `docs/EVENT_CATALOG.md`

**This is RECONCILIATION ONLY.** No code, schema, migration, test, API, or ADR was edited. No implementation begins from this document. Stopping here, per instruction, to wait for the next Product Owner command.
