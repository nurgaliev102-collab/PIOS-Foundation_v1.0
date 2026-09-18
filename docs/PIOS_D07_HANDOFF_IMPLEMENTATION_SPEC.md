# D-07 Handoff — Implementation-Ready Specification

**Type: SPECIFICATION, not implementation.** Consolidates the Product Owner decisions already locked in `docs/PIOS_D07_HANDOFF_RECONCILIATION.md` (the "Product Owner Decision — D-07 (2026-09-18)" section and this task's own further-locked OQ-1/OQ-2/OQ-3/OQ-4/OQ-7 answers) into a single, implementation-ready technical contract. No code, schema, migration, API, frontend, test, or event contract was changed to produce this document. Written against `HEAD = 12b8bbe99eca8121cf02d62bc9e3e39de38a4ac4`.

This document does not reopen, reinterpret, or re-derive D-07's architecture (DESIGN 1) or the locked answers to OQ-1, OQ-2, OQ-3, OQ-4, or OQ-7. Where an implementation detail is not yet decided by any ratified document, it is marked **OPEN** rather than guessed.

---

## 1. Purpose

**Handoff, exactly as locked:** a mechanism by which the driver already committed to an order (the *original committing driver*, recorded as `Assignment.driver` and never rewritten) may, with the named substitute's own agreement and the passenger's own explicit, informed consent, transfer *who actually executes* the ride to a different, named driver — without creating a new commitment, without ending the existing one, and without altering any fact the existing commitment already carries (`Trip.agreedAmount`, the `Connection`/relationship record, or the order itself).

**The single sentence that governs every design choice below**, as given in the PO decision lock: *"Это всё та же поездка. Меняется только фактический исполнитель."* — it is still the same ride; only the actual executor changes.

**What Handoff is not**, restated once, because every subsequent section depends on it: it is not a cancellation, not a re-dispatch, not a new proposal, not a PIOS-driven substitution, not a financial transaction, and not a relationship transfer. Each of these is separately, already forbidden by ratified decisions cited throughout (ADR-054 Part 5; `docs/PIOS_D07_HANDOFF_RECONCILIATION.md`'s own "MUST NOT IMPLEMENT" list).

---

## 2. Invariants

Every invariant below is either (a) already directly stated in the locked PO decision, (b) mechanically implied by DESIGN 1 combined with facts already true of the existing codebase (cited), or (c) named in this task's own required minimum list. None is invented.

| # | Invariant | Source |
|---|---|---|
| 1 | **One `Assignment` per `Order`, for the entire lifetime of that Order.** | Already true today, unconditionally — `Assignment.create`'s own guard, `check(existingAssignments.none { it.order == order })` (`Assignment.kt:189-191`), and DESIGN 1 relies on this remaining true rather than changing it. |
| 2 | **One `Trip` per `Assignment`.** | Already true today — `trips.assignment_id UNIQUE` plus `Trip.create`'s own in-memory check (`Trip.kt:49-54`, per D-07 reconciliation §11 item 4's citation). |
| 3 | **Handoff does not create a second `Assignment`.** | PO decision items 1, 3 ("no new Assignment," "the existing Assignment/Trip remains the same commitment"). |
| 4 | **Handoff does not create a second `Trip`.** | PO decision item 2. |
| 5 | **A commitment already `TERMINATED` (or `COMPLETED`) cannot be handed off.** | PO decision item 16 ("Handoff must not turn a terminated commitment back into executable state"); mechanically enforced today by `Trip.terminate`'s own guard already excluding these two states from every further transition (`Trip.kt:130-133`) — Handoff must respect the same boundary, not create a new one. |
| 6 | **Handoff cannot revive `TERMINATED`.** | Same as #5, restated as its own explicit item (PO decision item 16). |
| 7 | **Handoff is only reachable while the Trip is `CREATED` or `ARRIVED`.** | This task's own locked OQ-1 answer. |
| 8 | **Handoff is forbidden once the Trip has reached `IN_PROGRESS`, `COMPLETED`, or `TERMINATED`.** | Same locked OQ-1 answer, restated as the negative form for direct implementability against `TripStatus`'s own five values (`TripStatus.kt:25-30`). |
| 9 | **The substitute must be explicitly named by the original committing driver.** | PO decision item 10 combined with the reconciliation's own Part 4.2 principle 3 ("PIOS does not substitute a random driver for the one a client came to," `Proposal.kt:239-243`, already live code for a different, but principle-identical, case). |
| 10 | **PIOS never autonomously selects the substitute.** | PO decision item 10, verbatim. |
| 11 | **Passenger consent is mandatory.** | PO decision item 11. |
| 12 | **Consent is constitutive only once the passenger has positively confirmed — not a notification, not a default, not a timeout-as-acceptance.** | PO decision item 11 combined with this task's own locked OQ-2 answer ("Consent becomes constitutive only after positive confirmation"). |
| 13 | **Consent must be tied to the exact named substitute, not to "a handoff" in the abstract.** | Direct consequence of #9 + #11/#12 — consenting to an unnamed or different substitute is not consent to *this* handoff. |
| 14 | **Handoff is single-hop only.** | PO decision item 12. |
| 15 | **Chained Handoff (a substitute naming a further substitute) is prohibited.** | PO decision item 13. |
| 16 | **The original committing driver remains a durable, queryable historical fact, never overwritten.** | PO decision items 5, 6. `Assignment.driver` already fills this role today and must continue to, unmodified by Handoff. |
| 17 | **The current executing driver is separately identifiable from the original committing driver.** | PO decision items 4, 5 — necessarily a *new* fact, since nothing in the existing model carries it (`docs/PIOS_D07_HANDOFF_RECONCILIATION.md` §"Direct technical implication"). |
| 18 | **`Trip.agreedAmount` is unchanged by Handoff, in every case, unconditionally.** | PO decision item 7; D-06's own capture-once-immutable design (`Trip.kt`, D-06 KDoc) already structurally supports this — Handoff introduces no new write path to this field. |
| 19 | **`Connection`/`PrimaryConnection` (the passenger's relationship record) is unchanged by Handoff.** | PO decision item 8; ADR-054 Parts 1-4 remain unamended by this work. |
| 20 | **No fee, split, credit (financial sense), or commission of any kind attaches to a Handoff.** | PO decision item 9, and this task's own locked OQ-3 answer ("PIOS never splits money or participates in payment"). |
| 21 | **No financial credit accrues to the original driver for having handed off.** | This task's own locked OQ-3 answer, first clause. |
| 22 | **The original driver retains the historical/origination fact regardless of outcome.** | Locked OQ-3, second clause — restates invariant #16 in attribution terms; see §10. |
| 23 | **The executing/substitute driver receives execution attribution and stated-earnings attribution for the ride they actually execute.** | Locked OQ-3, third clause — see §10 for the precise boundary of what this does and does not authorize. |
| 24 | **A passenger's refusal cancels only the proposed Handoff — the existing Assignment/Trip, and the original driver's own commitment to it, are unaffected.** | This task's own locked OQ-4 answer, first two clauses. |
| 25 | **Passenger refusal alone never terminates the commitment.** | Locked OQ-4, fourth clause — termination, if it happens at all after a refusal, only ever happens through the ordinary, already-built D-01 flow, triggered independently. |
| 26 | **No Handoff frequency/concentration cap blocks a Handoff before sufficient observable behavior exists to base one on.** | This task's own locked OQ-7 answer — see §10/§15 for what remains D-08/future work versus what this implies structurally now (namely: observability must exist as a capability before any cap logic could ever be layered on top, but no cap logic itself is authorized here). |

---

## 3. Lifecycle

All four diagrams operate on the **same, single** `Assignment`/`Trip` pair — no diagram below ever produces a second one, per Invariants #3/#4.

**Normal (no Handoff):**

```
Order → Proposal ACCEPTED → Assignment.create + Trip.create
  → Trip: CREATED → ARRIVED → IN_PROGRESS → COMPLETED
```

**Handoff, successful (consented before `IN_PROGRESS`):**

```
Assignment/Trip exists, Trip.status ∈ {CREATED, ARRIVED}   (Invariant #7)
  → original driver proposes Handoff, naming a substitute   (Invariant #9)
  → substitute [see §7 — whether a separate substitute-acceptance
     step exists before passenger consent is OPEN, §15]
  → passenger consents to the exact named substitute          (Invariants #11-13)
  → existing Assignment/Trip CONTINUES UNCHANGED — same id, same row
  → the executing driver (a new, separate fact — Invariant #17) becomes the substitute
  → Trip.status proceeds: → IN_PROGRESS → COMPLETED, executed by the substitute
```

**Handoff, rejected by passenger:**

```
Assignment/Trip exists, Trip.status ∈ {CREATED, ARRIVED}
  → original driver proposes Handoff, naming a substitute
  → passenger refuses
  → the Handoff proposal is discarded — no Assignment/Trip mutation of any kind (Invariant #24)
  → original driver remains the executing driver, exactly as before the proposal
  → Trip continues its ordinary lifecycle, unaffected
```

**Original driver cannot fulfil, after a refused (or never-proposed) Handoff:**

```
Assignment/Trip exists
  → (optionally) Handoff proposed and refused, per the diagram above
  → original driver independently cannot fulfil the commitment
  → the ORDINARY, ALREADY-BUILT D-01 termination flow is used
    (CommitmentTerminationApplicationService.terminate, initiator = DRIVER)
  → Trip/Assignment: → TERMINATED, via the existing, unmodified mechanism
```

This fourth diagram is the locked OQ-4 answer's third clause made explicit: **Handoff never invents a new termination path.** A failed original driver, whether or not a Handoff was ever proposed, ends the commitment exactly the way D-01 already ends it today — `CommitmentTerminationApplicationService.terminate` (`CommitmentTerminationApplicationService.kt:41-117`), unchanged.

---

## 4. Actors

| Actor | Definition | Can initiate |
|---|---|---|
| **Original / committing driver** | The driver named on `Assignment.driver` at `Assignment.create` time — unchanged for the life of the commitment (Invariant #16). | Proposes a Handoff, naming the substitute (Invariant #9). This reuses the existing driver-authorization pattern already live for `AssignmentTerminationController.terminate` (`verified.drv == assignment.driver.driverId`, `AssignmentTerminationController.kt:50`) — the same check, for a different action. |
| **Current executing driver** | Whoever is, at any given moment, the driver actually expected to execute the ride — `Assignment.driver` until and unless a Handoff has been consented, the substitute thereafter (Invariant #17). Before any Handoff, the original and executing driver are the same person; this is not a separate identity, only a separate *fact*. | N/A — this is a derived role, not an actor who takes actions in their own right; see "current executing driver" as a *fact*, not a party, in §6. |
| **Substitute driver** | The specific, named driver the original driver proposes. Never chosen by PIOS (Invariant #10). Not verified against `driver-management` beyond the plain reference (reference-not-ownership, per `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4's own note that `toDriver` is "a plain string, never verified against driver-management"). | Whether the substitute must take an affirmative acceptance action before the passenger is even asked is **OPEN — see §7, §15.** If such a step exists, the substitute initiates it; this specification does not assume it exists. |
| **Passenger** | The order's own passenger, reached the same way every existing passenger-facing check already reaches them — via the order's `ACCEPTED` Proposal's `passengerReference` (`Proposal.passengerReference`, already the pattern `AssignmentController.listAssignmentsHttp`'s passenger branch uses, `AssignmentController.kt:206-207`). | Consents to, or refuses, the exact named substitute (Invariants #11-13, #24-25). |
| **PIOS** | The system itself. | **Never** initiates or selects a substitute (Invariant #10). Its only role is to enforce the invariants above and record the resulting facts — not to act as a party to the Handoff. |

**Authorization rules not yet groundable in existing architecture** are named, not invented, in §11.

---

## 5. Handoff State Model

A minimal, purpose-built state set for the Handoff proposal/request itself — **distinct from, and layered on top of, `TripStatus`/`AssignmentStatus`, which this model never re-enters or duplicates** (Invariants #3, #4).

```
PROPOSED
  → CONSENTED   (passenger accepts the exact named substitute)
  → REFUSED     (passenger declines)
  → WITHDRAWN   (original driver withdraws before either resolution — OPEN, see below)
```

- **`PROPOSED`**: the original driver has named a substitute. Nothing about the ride has changed yet (mirrors `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4's own "nothing about the ride changes" language for its `PROPOSED` state, reused here because it is exactly accurate under DESIGN 1 too).
- **`CONSENTED`**: the passenger has positively confirmed the exact named substitute (Invariant #12). This is the one and only moment the executing-driver fact (Invariant #17) changes.
- **`REFUSED`**: the passenger has declined. Per Invariant #24, this has no effect on the underlying Assignment/Trip.
- **`WITHDRAWN`**: named for completeness (an original driver may reasonably want to retract a proposal before the passenger answers) but **not decided by any locked PO input** — see §15. If the PO does not want this state, `PROPOSED` simply persists until `CONSENTED`/`REFUSED` or is superseded by the underlying Trip leaving the `CREATED`/`ARRIVED` window (Invariant #7/#8), at which point it becomes moot by construction rather than by an explicit transition.

**Explicitly not modeled, per instruction**: any `EXPIRED`/timeout-based auto-resolution state. Whether a Handoff proposal that receives no passenger answer within some window should lapse is **OPEN — see §15.** No duration, and no expiry mechanism, is assumed here.

**A `SUBSTITUTE_ACCEPTED` (or equivalently named) intermediate state — proposed in `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4, between `PROPOSED` and passenger consent — is deliberately not included above**, because whether the current business flow actually requires a separate substitute-acceptance step before the passenger is asked is itself **OPEN — see §7, §15.** Including it here would invent a decision this specification is not authorized to make.

---

## 6. Data Model

**Not implemented. Described only — what information must be represented, and why.** No table, column, or migration is proposed as final.

| Information | Why it must exist | Authoritative or derived? | Needs persistence? |
|---|---|---|---|
| **Original committing driver** | Answers "who originally accepted the commitment?" (this task's own Critical Attribution Rule). | **Authoritative, already exists**: `Assignment.driver` (`Assignment.kt`). Not a new fact — Handoff must simply never write to it. | Already persisted; no change. |
| **Current executing driver** | Answers "who actually executed (or will execute) the ride?" — the other half of the Critical Attribution Rule, and the direct implication of Invariant #17. | **Authoritative, does not yet exist.** `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.5 names the shape (`Trip.executingDriver`, defaulting to `assignment.driver`, changed only at `CONSENTED`) — this specification records that shape as the one already evaluated and consistent with DESIGN 1, without re-authorizing it as final schema. | Yes — this is the one genuinely new durable fact DESIGN 1 requires. |
| **Substitute driver (named on the proposal itself)** | The specific driver the original driver proposed, before/regardless of consent. | **Authoritative** for the Handoff proposal's own record; becomes the same value as "current executing driver" only upon `CONSENTED`. | Yes, on the Handoff record itself (§5's state model needs a subject). |
| **Handoff initiator** | Always the original committing driver under the locked design (Invariant #9) — recorded for audit completeness, not because any other initiator is possible. | Authoritative, but degenerate (always equals `Assignment.driver` at proposal time, per Invariant #16). | Yes, for audit (§12). |
| **Passenger consent (boolean/fact of `CONSENTED`)** | The constitutive act itself (Invariants #11, #12). | Authoritative. | Yes. |
| **Consent timestamp** | Required for audit (§12) and for any future observability work (Invariant #26/D-08(a)). | Derived fact of the consent act, but must be stored, not recomputed. | Yes. |
| **Handoff (proposal) timestamp** | Same reasoning as consent timestamp, for the `PROPOSED` moment. | Derived fact of the proposal act. | Yes. |
| **Linkage to the existing Assignment/Trip** | Handoff has no independent existence — it always refers to exactly one Assignment (Invariant #1/#2) and, transitively, its one Trip. | Authoritative (a foreign reference), not derived. | Yes — the same `assignmentId`-shaped reference `Trip` itself already uses (`Trip.assignmentId`, `Trip.kt`). |
| **Single-hop protection** | Invariants #14/#15 must be checkable, not merely honored by convention. | Derived — computable from "does a `CONSENTED` Handoff already exist for this Assignment, and if so, is the *current* proposer the original driver or a previous substitute." | Only if not already derivable from the linkage + initiator fields above; likely no separate field needed, but this is an implementation judgment, not decided here. |
| **Audit/history of every Handoff attempt** (proposed, consented, refused, and — if authorized — withdrawn) | Item 6 of the PO decision ("Handoff must preserve historical attribution of the original commitment") and §12's own requirement. | Each individual Handoff record is itself the audit trail; per DESIGN 1, **multiple historical Handoff records may exist for one Assignment over its lifetime** (a refused one, followed by a later consented one, for instance) — none of them is ever deleted or overwritten, mirroring this codebase's own "never delete documentation"/"historical evidence" discipline already applied to `Proposal` and `Termination` records elsewhere. | Yes, and specifically append-only. |

**Not decided here, and explicitly out of this section's own scope per instruction**: table name, column names, migration numbering, which module owns the Handoff record (the reconciliation's own §"Direct technical implication" already names Dispatch as the natural owner, consistent with `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.3's own reasoning — restated here as inherited context, not re-decided).

---

## 7. API Contract

**Described, not implemented.** No endpoint path, HTTP verb, or request/response shape below is authorized as final — each entry specifies only the *operation's own contract*, per instruction.

### Operation: Propose Handoff

- **Actor**: original committing driver.
- **Intent**: name a substitute for the existing, live commitment.
- **Input**: the target Assignment (or its Trip) identity; the substitute driver's identifier.
- **Server-derived fields**: the initiator (from the verified session, never client-supplied — mirrors `AssignmentTerminationController.terminate`'s own pattern of deriving the driver from `verified.drv`, never trusting a request body field for it); the proposal timestamp.
- **Expected result**: a new Handoff record in `PROPOSED` state (Invariant #9's precondition — the caller must be `Assignment.driver`).
- **Authorization**: caller's `verified.drv` must equal `Assignment.driver.driverId` — the existing driver-check pattern, reused (§4).
- **Idempotency**: **OPEN** — whether a repeated identical proposal (same substitute, same assignment) is idempotent-safe or rejected as a duplicate is not decided by any locked input; the existing codebase's own precedent (`TerminateCommitmentCommand.requestId`-based idempotency, `CommitmentTerminationApplicationService.kt:53-61`) is a plausible shape but is not itself authorized here.
- **Forbidden conditions**: Trip not in `{CREATED, ARRIVED}` (Invariant #7/#8); an existing non-terminal Handoff already pending for this Assignment (Invariant #14's single-hop enforcement extends to "no second live proposal," per §8 race #4); caller is not `Assignment.driver`; the named substitute equals the original driver (a degenerate, meaningless handoff — not explicitly named in any locked decision, flagged here as an obvious edge case implementation must reject, not as a new PO decision).

### Operation: Passenger Consent

- **Actor**: passenger.
- **Intent**: positively confirm the exact named substitute (Invariant #12/#13).
- **Input**: the Handoff proposal's own identity; confirmation that the passenger has seen and accepts the *named* substitute (not a bare "yes" disconnected from which substitute — the input must be bound to the specific proposal, closing the risk of a stale consent being replayed against a *later*, different proposal for the same Assignment).
- **Server-derived fields**: consent timestamp; the consenting identity, from the verified session, never client-supplied.
- **Expected result**: the Handoff transitions to `CONSENTED`; the executing-driver fact (§6) updates to the substitute; the original Assignment/Trip is otherwise untouched (Invariants #3, #4, #18, #19).
- **Authorization**: caller's `verified.sub` must equal the order's own Proposal's `passengerReference.passengerId` — the existing passenger-check pattern, reused (§4). Self-only: a passenger may only consent on their own order, never another passenger's.
- **Idempotency**: a repeated consent to an already-`CONSENTED` Handoff should be a safe no-op, not an error — consistent with this codebase's general precedent of idempotent resolution endpoints (e.g., `POST /v1/connections`'s own 201/200 idempotency, ADR-054 Part 4). Not itself re-authorized here as a specific status code.
- **Forbidden conditions**: the Handoff is not `PROPOSED` (already `CONSENTED`/`REFUSED`, or does not exist); the underlying Trip has left `{CREATED, ARRIVED}` since the proposal was made (a race — see §8 race #5); caller is not the order's own passenger.

### Operation: Passenger Refusal

- **Actor**: passenger.
- **Intent**: decline the exact named substitute (Invariant #24).
- **Input**: the Handoff proposal's own identity.
- **Server-derived fields**: refusal timestamp; the refusing identity, from the verified session.
- **Expected result**: the Handoff transitions to `REFUSED`; the Assignment/Trip is completely unaffected (Invariant #24/#25) — no cancellation, no termination, no state change to anything but the Handoff record itself.
- **Authorization**: same passenger-check pattern as consent.
- **Forbidden conditions**: same as consent's "not `PROPOSED`" case.

### Operation: Substitute Acceptance — **OPEN, not specified as a required operation**

`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4 proposes a `SUBSTITUTE_ACCEPTED` step *before* the passenger is asked, reasoning that "asking the passenger to consent to a driver who has not themselves agreed would produce a consent to something that may evaporate." **This specification does not assume that step exists**, because no locked PO decision confirms or declines it — see §15. If the Product Owner confirms it is required, it would be a fourth operation, actor = substitute driver, gating the passenger-consent operation above on its own prior `CONSENTED`-equivalent state; until then, it is named here only as an explicit open item, not designed.

---

## 8. Concurrency

All races below assume the existing D-01 locking discipline is reused unmodified: `PostgreSQLOrderGuard.lock(order)` (`PostgreSQLOrderGuard.kt:9-17`), taken first, inside the same transaction, before any Assignment/Trip/Handoff row lock — exactly the same guard `CommitmentTerminationApplicationService.terminate` (line 52) and `DispatchAssignmentApplicationService.completeAssignment` (line 320) already share today. Handoff introduces no new lock primitive; it must participate in this same one.

| # | Race | Lock boundary | Expected winner | Final valid state | Rejected operation |
|---|---|---|---|---|---|
| 1 | **Two concurrent Handoff proposals for the same Assignment** | Both acquire `orderGuard.lock(order)` before checking for an existing non-terminal Handoff. | Whichever acquires the order lock first. | Exactly one `PROPOSED` Handoff exists for the Assignment. | The second proposal — rejected once the first's existence is visible under lock (mirrors `Trip.create`'s own "in-memory check alone loses the race" lesson, `Trip.kt:49-54` — a persistent uniqueness constraint on "at most one non-terminal Handoff per Assignment" is the backstop, same two-layer pattern). |
| 2 | **Handoff proposal vs. passenger refusal of a *different*, already-superseded proposal** | Both under the same order lock; refusal targets a specific Handoff id, not "the current one" implicitly. | N/A — not actually a race once refusal is bound to a specific proposal id (§7's own consent/refusal input design already closes this). | The targeted proposal resolves to `REFUSED`; any newer, different `PROPOSED` Handoff for the same Assignment is unaffected. | A refusal request naming a stale Handoff id that is no longer the live one — rejected as a not-found/conflict, not silently applied to the wrong proposal. |
| 3 | **Handoff (proposal or consent) vs. D-01 termination** | Both acquire `orderGuard.lock(order)` first — same shared lock D-01 already uses. | Whichever acquires the lock first. | If termination wins: Trip → `TERMINATED`; any pending Handoff for that Assignment becomes moot (Invariant #6 forbids ever consenting it afterward — must be checked under the same lock at consent time, not only at proposal time, since the proposal may have been made before termination started). If Handoff-consent wins: executing driver updates; a subsequent termination attempt proceeds exactly as D-01 already handles a live commitment. | Whichever operation observes the order already resolved the other way loses, cleanly (mirrors `CommitmentTerminationPostgreSQLTest.kt`'s own "single first committer" proof for completion-vs-termination, unmodified, now extended by analogy to Handoff-vs-termination). |
| 4 | **Handoff consent vs. termination, specifically at the consent moment** | Same order lock, re-checked at consent time (not only at proposal time) — this is the critical implementation requirement Invariant #6 imposes: a `PROPOSED` Handoff whose underlying Trip is terminated *after* the proposal but *before* consent must not be consentable. | Termination, if it reaches the lock first. | Trip → `TERMINATED`; the Handoff, if not yet consented, must be foreclosed (transitioned to some terminal-negative state, or simply rejected at consent time — exact mechanism is an implementation detail, not decided here, but the *outcome* — consent must fail — is Invariant #6 itself, not optional). | The consent operation, if it arrives after termination has already committed. |
| 5 | **Handoff consent vs. Trip start (`arrive`→`start`, i.e., entering `IN_PROGRESS`)** | Same order lock. Invariant #7/#8 means consent must re-verify `Trip.status ∈ {CREATED, ARRIVED}` under the very same lock it uses to write the executing-driver change — not merely at proposal time. | Whichever operation (start, or consent) acquires the lock first. | If `start` wins: Trip → `IN_PROGRESS`; the pending Handoff must then be rejected at consent time (Invariant #8), not silently honored. If consent wins: executing driver updates while still `CREATED`/`ARRIVED`; `start` then proceeds normally, now understood as the substitute's own ride-progress action (§11's authorization-check implication). | Consent, if `start` already committed first. |
| 6 | **Stale consent** (a consent request arriving for a Handoff that has already resolved — `CONSENTED` by an earlier, duplicate request, or `REFUSED`) | Read-then-check under the order lock, same as every other resolution here. | The first resolution to commit. | The Handoff's own terminal state, set exactly once. | A second, stale consent/refusal request — idempotent no-op if it matches the already-committed outcome (§7's own idempotency note), rejected as a conflict if it contradicts it (e.g., consent arriving after refusal already committed). |
| 7 | **Chained Handoff attempt** (a substitute, once `CONSENTED`, tries to propose their own Handoff) | Same order lock; the propose-operation's own authorization check (§7) must compare the caller against the *original* `Assignment.driver`, not the current executing driver, to enforce Invariant #15. | N/A — not a race, a straightforward rejection: the caller (the substitute) is never `Assignment.driver`, so the existing driver-authorization check (§4, §7) already rejects them by construction, with no new lock logic required beyond re-using the unchanged `Assignment.driver` comparison. | Assignment/Trip execution stays with the already-consented substitute; no chained Handoff record is ever created. | The substitute's own propose-Handoff attempt — rejected at the authorization layer, before any lock contention is even relevant. |
| 8 | **The same substitute proposed twice** (either by the same original driver reissuing the proposal, or — degenerate — as a retry) | Same as race #1 (two concurrent/duplicate proposals), plus §7's own OPEN idempotency question for the propose operation specifically. | Depends on whether propose is specified as idempotent (OPEN, §7) or rejected as a duplicate. | Exactly one live `PROPOSED` Handoff naming that substitute exists at any time, regardless of how many times the original driver issued the request. | The second, duplicate call — either absorbed idempotently or rejected as a conflict; which of the two is not decided here (§7). |

No test is written or implemented for any of the above, per instruction.

---

## 9. Events

**No event contract is changed by this document.** This section distinguishes what already exists from what a future implementation would need to add — a gap list, not a schema change.

**Existing events that can be reused, unmodified:**
- `CommitmentTerminated` (`CommitmentTerminationApplicationService.kt:146-163`) — reused exactly as-is for the "original driver cannot fulfil" lifecycle path (§3's fourth diagram); Handoff introduces no variant of this event.
- `AssignmentCompleted`/`TripCompleted` — reused exactly as-is for a Handoff-executed ride's own completion; per Invariant #18, `payload.statedPrice` (D-06's own `agreedAmount`-sourced field, `DispatchAssignmentApplicationService.kt:325-334`) is completely unaffected by which driver executed the ride, so no change to this payload is required *by Handoff itself* — see below for the one open question this raises for Driver Management's own consumption, which is an attribution question (§10), not an event-contract one.

**New Handoff-specific events that would be required** (naming, not authorizing, per `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.6's own already-evaluated minimum, restated here as inherited, not re-decided, context):
- A `HandoffProposed`-shaped fact, emitted at `PROPOSED`. Per that same evaluation, no server-side consumer is obviously required initially — the substitute driver's own app could learn of it by the existing poll pattern (`ADR-071`), not a new push mechanism.
- A `HandoffConsented`-shaped fact, emitted at `CONSENTED` — this is the one event that *must* exist as a real, durable domain fact, because it is the moment the executing driver changes (Invariant #17), and it is what any future attribution-consuming code (§10) would need to key off of.
- A `HandoffRefused`-shaped fact (or a shared "resolved negatively" shape covering refusal and, if authorized, withdrawal) — for audit (§12), not because any consumer is yet known to need it.

**Payload information required**, at minimum, for `HandoffConsented` specifically: the Assignment/Trip identity; the original driver; the substitute (now executing) driver; the consent timestamp. No currency, fee, or financial field of any kind (Invariant #20).

**`eventVersion` implication, flagged not implemented**: if attribution work (§10) ever requires `AssignmentCompleted`/`TripCompleted` to carry an *executing*-driver-distinct-from-`driverId` field (the same additive-field shape D-06 already used for `statedPrice`, and `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.6 itself names as the open "consumer question that must not be guessed"), that would be an **additive field, `eventVersion` staying at its current value** (1) — following the exact precedent ADR-065/D-06 already established (`DispatchAssignmentApplicationService.kt`'s own `envelopeFor` KDoc). This is flagged as a likely future need, not implemented, and not itself authorized here — it depends entirely on §10's own still-open attribution question.

---

## 10. Attribution

Integrates the D-08 boundary (`PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1763-1774`) with this task's own locked OQ-3 answer, without implementing either.

**What is locked (this task's OQ-3 answer, verbatim in substance):**
- No financial credit/reward for the original driver for having handed off (Invariant #21).
- The original driver retains the historical/origination fact regardless (Invariant #22) — i.e., "who committed to this order" is never erased or reassigned, matching D-08(b)'s own "relationship-facing facts credit the committing driver" half.
- The executing/substitute driver receives **execution attribution and stated-earnings attribution** for the ride they actually execute (Invariant #23) — this newly, explicitly resolves the *metric*-attribution half of OQ-3/D-08(b) that the prior reconciliation pass (`docs/PIOS_D07_HANDOFF_RECONCILIATION.md`'s own updated OQ matrix) had found still open: it is now locked as "executing driver," matching D-08(b)'s own recommended split exactly, on both halves.
- PIOS never splits money or participates in payment of any kind (Invariant #20) — no commission, no fee, on either driver, for either half of the attribution.

**What this does NOT authorize, per instruction ("Do not implement D-08 caps or scoring"):**
- No implementation of `totalStatedEarnings`/`completedRidesCount` re-routing logic (Driver Management's own existing derived-metric machinery, `AssignmentCompletedApplicationService.kt`, D-02/D-06-bound) is designed or built here. This section records *which* driver the future metric should credit once built, not how.
- No cap, threshold, or scoring mechanism of any kind (D-08(a)'s own remaining open half — see §14, §15).
- No new business-metric concept beyond the two that already exist (`totalStatedEarnings`, `completedRidesCount`) is proposed — attribution here means "which existing driver's existing counters move," not a new counter.

**Genuine open question this section surfaces, not decided by OQ-3's own locked wording:** whether the *original* driver's own existing counters (e.g. a distinct "facilitated" or "handed off" count, separate from `completedRidesCount`) should reflect anything at all about having facilitated a handoff. OQ-3's locked answer says the original driver "retains the historical/origination fact" but does not say whether that fact is ever surfaced as a *counted* metric anywhere (as opposed to simply being queryable per-Assignment, per §12's own audit requirement). This is named in §15 as a residual, narrower attribution question, distinct from — and not reopening — the already-locked executing/committing split.

---

## 11. Security

Authorization invariants, grounded only in what already exists (`SessionTokenVerifier.VerifiedToken(sub: String, drv: String?)`, `SessionTokenVerifier.kt`).

- **A requester cannot choose an arbitrary driver identity to act as.** Every operation in §7 derives the acting identity from the verified session (`verified.sub`/`verified.drv`), never from a client-supplied field — the same discipline already governing `AssignmentTerminationController.terminate`'s own `TerminateAssignmentRequest` (it accepts no driver-identity field at all; `verified.drv` is the only source, `AssignmentTerminationController.kt:44,50`). Handoff's propose/consent/refuse operations must follow the identical pattern.
- **The substitute must correspond to a real, eligible identity — but "eligible" is not further defined by any locked decision.** `DriverReference` is, and per `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4's own already-evaluated note, remains "a plain string, never verified against driver-management" (reference-not-ownership, `ADR-005`/`ADR-019`). Whether Handoff should be the first place this reference *is* cross-checked (e.g., against `DriverAvailabilityRepository`, the way `ProposalApplicationService.handle` already gates proposing to an unavailable driver, `ProposalApplicationService.kt:137-149`) is **OPEN — see §15.**
- **Passenger consent must be self-only, authenticated.** `verified.sub == proposal.passengerReference.passengerId` — the exact, already-live pattern (`AssignmentController.kt:206-207`), reused unmodified.
- **One actor cannot impersonate another party.** Covered by the two bullets above — every check compares the verified session's own identity, never a request-body-asserted one, against the specific role being exercised.
- **Guest handling.** No guest-specific restriction is known to exist anywhere in the current identity model beyond the standard identity-matching check above (guest vs. phone-verified identities are distinguished elsewhere, per this session's own prior D-03 closure work, but nothing in that work or in any Handoff-adjacent document restricts a guest passenger's ability to consent to something their own session is otherwise authorized to act on). **If the Product Owner wants guest passengers specifically excluded from Handoff consent, that is a new rule, not inferable from existing architecture — flagged as OPEN, §15, rather than assumed either way.**
- **Server derives every actor identity.** Restated once more because it is the single rule every operation in §7 depends on: no operation accepts a client-asserted driver id, passenger id, or substitute id as its own authorization basis — only as *content* (e.g., "which substitute is being named"), never as *proof of who is asking*.

---

## 12. Observability / Audit

Every Handoff attempt (proposed, consented, refused, and — if authorized, §15 — withdrawn) must be durably recorded, per §6's own append-only requirement, sufficient to answer, for any Assignment, at any later time:

- Who initiated the Handoff (always the original committing driver, per Invariant #9 — recorded anyway, for audit completeness rather than ambiguity).
- Who was the original executor/committer before this Handoff (`Assignment.driver`, unchanged, always available).
- Who became the substitute, if the Handoff was consented.
- Who consented (the passenger identity, from the verified session at consent time) — or who refused.
- The proposal timestamp and the resolution (consent/refusal) timestamp.
- The final result (consented-and-executing, refused-and-discarded, or — if authorized — withdrawn).

**Sensitive data discipline**: no field beyond the above is logged. In particular, no free-text note, no location data, and no content beyond the plain identifiers and timestamps already named — mirroring this codebase's own existing restraint on `Termination.note` (capped, optional, never used for ranking or metrics per `ADR-080`'s own Decision text) as the nearest precedent for "record enough to audit, nothing more."

---

## 13. Frontend Behavior

**UX states only — no UI is designed or implemented here.**

- **Handoff proposed**: the original driver's own screen reflects that a Handoff is pending, naming the substitute they chose.
- **Passenger sees the exact substitute**: per OQ-2 (locked by the prior reconciliation pass, §10 of that document — not reopened here), the passenger must be shown the *specific* named substitute before being asked to consent, not a generic "your driver wants to hand off" notice. What facts about the substitute are shown (name, availability — per `DRIVER_IDENTITY_DESIGN_DECISION.md`'s own already-ratified limits, no rating, no ride count) is inherited from that same existing constraint, not re-decided here.
- **Passenger accepts**: a blocking confirmation step, not a toast or passive notice (Invariant #12) — the passenger must take an affirmative action.
- **Passenger refuses**: an equally explicit, available action; per Invariant #24, refusing must visibly leave the original driver as the ride's own driver, with no ambiguity that anything changed.
- **Original driver remains visible if refusal occurs**: the passenger-facing screen must not, even transiently, suggest the substitute is now their driver if the Handoff was refused — this is a direct UX consequence of Invariant #24's own "existing Assignment/Trip remains unchanged."
- **Execution driver updates after successful Handoff**: once `CONSENTED`, every passenger-facing and driver-facing surface that currently shows "your driver" (which today reads `Assignment.driver` directly, per `docs/PIOS_D07_HANDOFF_RECONCILIATION.md` §9/§10's own inventory of existing authorization/data-surfacing patterns) must instead reflect the current executing driver (§6) — a read-model change, not designed here beyond naming that it is required.

---

## 14. Compatibility

- **D-01 (Commitment Termination)**: fully compatible by construction. `TERMINATED` remains terminal (Invariant #5/#6); Handoff never calls `Trip.terminate`/`Assignment.terminate`; the "original driver cannot fulfil" path (§3's fourth diagram) reuses D-01's own existing, unmodified termination flow rather than inventing a parallel one.
- **D-02 (Driver Business Metrics Carry No Value)**: unaffected in kind — §10's own attribution work moves *which* driver an existing metric credits, never introduces a new metric, a ranking, or a scoring mechanism (`ADR-081`'s own boundary, restated, not reopened).
- **D-03 (Phone-Verified Identity Recovery)**: unaffected — Handoff's own authorization model (§11) reuses the existing session-token verification `identity` already issues and every other module already verifies locally (ADR-055 Decision 1); nothing about Handoff touches identity recovery itself.
- **D-06 (Settlement as Evidence)**: fully compatible. `Trip.agreedAmount` is untouched (Invariant #18); there is exactly one Trip, never two, which is precisely the property D-06's own capture-once, no-later-Proposal-reread design depends on (per the prior reconciliation's own explicit finding, `docs/PIOS_D07_HANDOFF_RECONCILIATION.md` §"D-06 (Settlement as Evidence)" subsection).
- **D-08 (Handoff's binding conditions)**: **partially resolved, partially still open.** D-08(b) (split attribution) is now fully locked by this task's own OQ-3 answer (§10). D-08(a) (observation-before-cap) is only partially addressed — Invariant #26 states the *principle* (no cap before observation), but the observability mechanism itself, and any cap value or threshold, remain explicitly unbuilt and unauthorized here (§15).
- **Explicitly outside D-07's own scope, named so nothing is silently assumed**: Settlement's own "what constitutes settled" question (OQ-8); Fallback Tier-2/Network's own reserved question (OQ-10, ADR-068's own); any ride-outcome taxonomy; any payment/billing mechanism of any kind.

---

## 15. Open Questions

**Not reopened**: D-07's architecture (DESIGN 1, locked), OQ-1 (timing window, locked), OQ-2 (consent framing, locked), OQ-3 (attribution split, locked), OQ-4 (refusal consequence, locked), OQ-7 (observation-before-cap principle, locked). Every item below is a genuinely distinct, narrower implementation question these locked answers do not themselves resolve.

1. **Substitute driver acceptance — does it exist as a separate, required step before passenger consent?** `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4 proposes one (`SUBSTITUTE_ACCEPTED`); no locked PO decision confirms or declines it. §5/§7 both flag this explicitly rather than assume either answer.
2. **Handoff proposal expiry/timeout.** Whether an un-answered `PROPOSED` Handoff should ever auto-resolve (and if so, to what state, after what duration) is not decided by any locked input. §5 explicitly omits an `EXPIRED` state rather than invent one.
3. **Handoff withdrawal by the original driver, before passenger resolution.** Named in §5 as a plausible `WITHDRAWN` state but not itself authorized — whether the original driver may retract a pending proposal is unaddressed by every locked decision so far.
4. **Substitute eligibility verification.** Whether Handoff should be the first place `DriverReference` is cross-checked against `driver-management`'s own availability/eligibility data (as `ProposalApplicationService.handle` already does for ordinary proposals) or should remain a plain, unverified reference (§11).
5. **Guest passenger consent.** Whether a guest (non-phone-verified) passenger identity may consent to a Handoff on the same terms as any other passenger session, or should be excluded — no existing document addresses this (§11).
6. **Original driver's own facilitation metric, if any.** Whether "retains the historical/origination fact" (locked, OQ-3) should ever surface as a counted business metric for the original driver, distinct from simply being queryable per-Assignment (§10) — narrower than, and not a reopening of, the already-locked executing/committing split itself.
7. **D-08(a)'s own remaining half — the exact observability mechanism, and any eventual cap threshold/window.** Explicitly classified as **D-08, not D-07**, per instruction: this specification records only that no cap may block Handoff before observability exists (Invariant #26); the mechanism and any numeric threshold are future, separately-scoped work.
8. **Idempotency semantics for the propose and consent operations** (§7) — whether repeated identical requests are absorbed safely or rejected, and by what key (a client-supplied request id, mirroring `TerminateCommitmentCommand.requestId`, is a plausible but unauthorized shape).

---

## 16. Implementation Sequence

A staged order only — no stage is implemented by this document.

1. **Domain invariants** — encode §2's invariants directly into a `Handoff` domain type (or equivalent) and the `Trip.executingDriver`-shaped fact (§6), the same way `Termination`/`Trip.terminate` already encode D-01's own invariants as `check()` preconditions rather than external validation.
2. **Persistence** — the minimal, append-only schema §6 describes, once its still-open shape questions (§15 items 2-3, table ownership) are resolved by the implementing team within the boundaries this spec sets.
3. **Application services** — the propose/consent/refuse operations (§7), including the concurrency discipline (§8) reusing `OrderGuard` unmodified.
4. **API** — the transport layer for §7's three (or four, pending §15 item 1) operations, following this codebase's own existing controller/authorization conventions (§4, §11).
5. **Events** — `HandoffProposed`/`HandoffConsented`/`HandoffRefused` (§9), plus, only once §10's attribution question is fully implementable, the additive `AssignmentCompleted`/`TripCompleted` field §9 flags as a likely future need.
6. **Frontend** — the UX states §13 names, on both the original driver's and the passenger's own existing screens, plus whatever the substitute driver's own screen needs once §15 item 1 is resolved.
7. **Concurrency tests** — real-PostgreSQL races matching §8's own eight named scenarios, in the same style already proven for D-01 (`CommitmentTerminationPostgreSQLTest.kt`).
8. **Integration tests** — the four lifecycle diagrams in §3, end to end.
9. **Full regression** — dispatch, order-management, driver-management, and frontend suites, the same discipline already applied for D-01/D-06 (per this session's own prior closure reports), to confirm Handoff's additive changes disturb nothing already shipped.

---

## Evidence Index

Documents and files read or directly cited to produce this specification (none modified):

- `docs/PIOS_D07_HANDOFF_RECONCILIATION.md` (both its original reconciliation body and its "Product Owner Decision — D-07 (2026-09-18)" addendum).
- `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4 (technical design precedent for `Handoff`/`executingDriver`), Part 6 (OQ-1…OQ-10 origin).
- `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` (OQ option tables).
- `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` (D-07/D-08 sections, `:1750-1774`).
- `docs/ADR/ADR-054-Circle-of-Trust-Passenger-Experience-Owned-Primary-Driver-Relationship.md`.
- `docs/ADR/ADR-080-Commitment-Termination-and-Order-Cancellation-Handshake.md`.
- `docs/ADR/ADR-065-Driver-Earnings-From-Self-Stated-Prices.md` (additive-field/`eventVersion` precedent, §9).
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt`, `Trip.kt`, `Proposal.kt`, `Termination.kt`.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/CommitmentTerminationApplicationService.kt`, `DispatchAssignmentApplicationService.kt`, `ProposalApplicationService.kt`.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt`, `AssignmentTerminationController.kt`, `SessionTokenVerifier.kt`.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLOrderGuard.kt`.
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/CommitmentTerminationPostgreSQLTest.kt`.

**This is a SPECIFICATION, produced from already-locked decisions. No code, schema, migration, API, frontend, test, or event contract was changed to produce it. Implementation has not started. Stopping here, per instruction, to wait for the next Product Owner or engineering command.**
