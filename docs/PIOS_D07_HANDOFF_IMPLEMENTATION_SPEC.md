# D-07 Handoff — Implementation-Ready Specification

**Type: SPECIFICATION, not implementation. FINAL LOCK.** Consolidates every Product Owner decision locked to date: `docs/PIOS_D07_HANDOFF_RECONCILIATION.md`'s own "Product Owner Decision — D-07 (2026-09-18)" section (architecture = DESIGN 1), this document's own first pass (OQ-1/OQ-2/OQ-3/OQ-4/OQ-7, at `HEAD = 12b8bbe`), and this final pass's own further-locked decisions (substitute acceptance, withdrawal, no TTL, eligibility, guest consent, atomic revalidation). No code, schema, migration, API, frontend, test, or event contract was changed to produce this document. Written against `HEAD = 378c878fa874678f0dda04cf2314ce69d66df25e`.

**Nothing below reopens** D-07's architecture (DESIGN 1), OQ-1, OQ-2, OQ-3, OQ-4, OQ-7, substitute acceptance, expiry, withdrawal, eligibility, or guest consent — all are now locked. Where the *first* pass of this specification marked something **OPEN**, this revision states plainly whether that item is now **RESOLVED** (with the resolving decision cited) or remains genuinely open — the first pass's own reasoning for why it was open is preserved inline, not deleted, so the resolution history stays legible.

---

## 1. Purpose

**Handoff, exactly as locked:** a mechanism by which the driver already committed to an order (the *original committing driver*, recorded as `Assignment.driver` and never rewritten) may, with the named substitute driver's own explicit acceptance and the passenger's own explicit, informed, constitutive consent, transfer *who actually executes* the ride to a different, named driver — without creating a new commitment, without ending the existing one, and without altering any fact the existing commitment already carries (`Trip.agreedAmount`, the `Connection`/relationship record, or the order itself).

**The single sentence that governs every design choice below**, as given in the PO decision lock: *"Это всё та же поездка. Меняется только фактический исполнитель."* — it is still the same ride; only the actual executor changes.

**What Handoff is not**, restated once, because every subsequent section depends on it: it is not a cancellation, not a re-dispatch, not a new proposal, not a PIOS-driven substitution, not a financial transaction, and not a relationship transfer. Each of these is separately, already forbidden by ratified decisions cited throughout (ADR-054 Part 5; `docs/PIOS_D07_HANDOFF_RECONCILIATION.md`'s own "MUST NOT IMPLEMENT" list).

---

## 2. Invariants

Every invariant below is either (a) already directly stated in a locked PO decision, (b) mechanically implied by DESIGN 1 combined with facts already true of the existing codebase (cited), or (c) named in a required minimum list this or a prior pass was given. None is invented. Invariants #1-26 are unchanged from the first pass (renumbered only where a new one is inserted for grouping clarity); #27 onward are newly locked in this pass.

| # | Invariant | Source |
|---|---|---|
| 1 | **One `Assignment` per `Order`, for the entire lifetime of that Order.** | Already true today, unconditionally — `Assignment.create`'s own guard, `check(existingAssignments.none { it.order == order })` (`Assignment.kt:189-191`), and DESIGN 1 relies on this remaining true rather than changing it. |
| 2 | **One `Trip` per `Assignment`.** | Already true today — `trips.assignment_id UNIQUE` plus `Trip.create`'s own in-memory check (`Trip.kt:49-54`). |
| 3 | **Handoff does not create a second `Assignment`.** | PO decision items 1, 3. |
| 4 | **Handoff does not create a second `Trip`.** | PO decision item 2. |
| 5 | **A commitment already `TERMINATED` (or `COMPLETED`) cannot be handed off.** | PO decision items 17-18; mechanically enforced today by `Trip.terminate`'s own guard already excluding these two states from every further transition (`Trip.kt:130-133`). |
| 6 | **Handoff cannot revive `TERMINATED`.** | PO decision item 18, restated as its own explicit item. |
| 7 | **Handoff is only reachable while the Trip is `CREATED` or `ARRIVED`.** | PO decision item 19; locked OQ-1. |
| 8 | **Handoff is forbidden once the Trip has reached `IN_PROGRESS`, `COMPLETED`, or `TERMINATED`.** | Same as #7, restated as the negative form against `TripStatus`'s own five values (`TripStatus.kt:25-30`). |
| 9 | **The substitute must be explicitly named by the original committing driver.** | PO decision items 11-12 combined with the reconciliation's own principle 3 ("PIOS does not substitute a random driver for the one a client came to," `Proposal.kt:239-243`). |
| 10 | **PIOS never autonomously selects the substitute.** | PO decision item 11, verbatim. |
| 11 | **Passenger consent is mandatory.** | PO decision item 13. |
| 12 | **Consent is constitutive only once the passenger has positively confirmed — not a notification, not a default, not a timeout-as-acceptance.** | PO decision item 13; locked OQ-2. |
| 13 | **Consent must be tied to the exact named substitute, not to "a handoff" in the abstract.** | Direct consequence of #9 + #11/#12. |
| 14 | **Handoff is single-hop only.** | PO decision item 15. |
| 15 | **Chained Handoff (a substitute naming a further substitute) is prohibited.** | PO decision item 16. |
| 16 | **The original committing driver remains a durable, queryable historical fact, never overwritten.** | PO decision items 4, 33. `Assignment.driver` already fills this role and must continue to, unmodified by Handoff. |
| 17 | **The current executing driver is separately identifiable from the original committing driver.** | PO decision item 5 — necessarily a *new* fact, since nothing in the existing model carries it. |
| 18 | **`Trip.agreedAmount` is unchanged by Handoff, in every case, unconditionally.** | PO decision item 3. |
| 19 | **`Connection`/`PrimaryConnection` is unchanged by Handoff.** | PO decision item 6; ADR-054 Parts 1-4 remain unamended. |
| 20 | **No fee, split, credit (financial sense), or commission of any kind attaches to a Handoff.** | PO decision items 7-10, 35; locked OQ-3. |
| 21 | **No financial credit accrues to the original driver for having handed off.** | PO decision item 9; locked OQ-3, first clause. |
| 22 | **The original driver retains the historical/origination fact regardless of outcome.** | PO decision item 33; locked OQ-3, second clause. |
| 23 | **The executing/substitute driver receives execution attribution and stated-earnings attribution for the ride they actually execute.** | PO decision item 34; locked OQ-3, third clause. |
| 24 | **A passenger's refusal cancels only the proposed Handoff — the existing Assignment/Trip, and the original driver's own commitment to it, are unaffected.** | Locked OQ-4, first two clauses. |
| 25 | **Passenger refusal alone never terminates the commitment.** | PO decision item 20; locked OQ-4, fourth clause. |
| 26 | **No Handoff frequency/concentration cap blocks a Handoff before sufficient observable behavior exists to base one on.** | PO decision items 30-31; locked OQ-7. |
| 27 | **The substitute driver must explicitly accept the specific Handoff before the passenger is ever asked to consent.** | PO decision item 14 — **this resolves what the first pass of this document left OPEN** (§5/§7/§15's own prior "whether a separate substitute-acceptance step exists" question). |
| 28 | **A consent request cannot be constitutive of anything if the named substitute has not themselves accepted first.** | Direct consequence of #27 — the state model (§5) must make this structurally true, not merely policy-true. |
| 29 | **No artificial Handoff TTL exists.** | PO decision item 27 — **resolves the first pass's own "expiry — OPEN" item.** A `PROPOSED`/`SUBSTITUTE_ACCEPTED` Handoff does not lapse from the mere passage of time. |
| 30 | **A Handoff remains pending until exactly one of: passenger accepts; passenger refuses; original driver withdraws; or the Commitment enters a state where Handoff is forbidden (Invariant #8).** | PO decision item 28 — the exhaustive, closed list of ways a pending Handoff resolves. |
| 31 | **The original driver may withdraw a Handoff before passenger consent.** | PO decision item 21 — **resolves the first pass's own "withdrawal — OPEN" item**, for the *before-consent* case. |
| 32 | **Once the passenger has given constitutive consent, the original driver cannot simply withdraw the Handoff.** | PO decision item 22 — the *after-consent* case, decided the opposite way from #31. Consistent with #12: consent, once constitutive, is not unilaterally reversible by the party who is no longer the executing driver. |
| 33 | **Before passenger consent is recorded, the system must atomically revalidate the current state** (the Handoff is still `SUBSTITUTE_ACCEPTED`/live, the Trip is still `CREATED`/`ARRIVED`, no termination has intervened). | PO decision item 29 — a concurrency invariant, not merely a data-model one; see §8. |
| 34 | **The substitute must be an existing, registered driver Identity with a valid driver association.** | PO decision item 23 — **resolves the first pass's own "substitute eligibility — OPEN" item**, in the direction of requiring real, registered eligibility rather than leaving the reference unverified. |
| 35 | **No PIOS automatic discovery or selection of the substitute is introduced by the eligibility check in #34** — verifying that a named substitute is real and registered is not the same as PIOS choosing one (Invariant #10 stays absolute). | PO decision item 23's own qualifier, read together with items 11-12. |
| 36 | **A requester cannot arbitrarily supply or bind another driver's identity to any Handoff operation.** | PO decision item 24 — restates, for Handoff specifically, the discipline already enforced elsewhere (§11). |
| 37 | **A guest passenger may consent to a Handoff without prior registration**, through the existing protected guest context, bound to the exact Commitment/Handoff. | PO decision items 25-26 — **resolves the first pass's own "guest consent — OPEN" item**, in the direction of permitting it (not excluding it), with an explicit binding requirement. |

---

## 3. Lifecycle

All diagrams operate on the **same, single** `Assignment`/`Trip` pair — none ever produces a second one (Invariants #3/#4).

**Normal (no Handoff):**

```
Order → Proposal ACCEPTED → Assignment.create + Trip.create
  → Trip: CREATED → ARRIVED → IN_PROGRESS → COMPLETED
```

**Handoff, successful (consented before `IN_PROGRESS`) — updated to include the now-locked substitute-acceptance step:**

```
Assignment/Trip exists, Trip.status ∈ {CREATED, ARRIVED}       (Invariant #7)
  → original driver proposes Handoff, naming a substitute       (Invariant #9)
  → substitute driver explicitly accepts this specific Handoff  (Invariant #27 — now required, not optional)
  → passenger is shown the exact, now-accepted substitute and consents   (Invariants #11-13, #28)
  → system atomically revalidates state immediately before recording consent   (Invariant #33)
  → existing Assignment/Trip CONTINUES UNCHANGED — same id, same row
  → the executing driver (Invariant #17) becomes the substitute
  → Trip.status proceeds: → IN_PROGRESS → COMPLETED, executed by the substitute
```

**Handoff, rejected by passenger:**

```
... substitute accepts (as above) ...
  → passenger refuses
  → the Handoff resolves to REFUSED — no Assignment/Trip mutation of any kind   (Invariant #24)
  → original driver remains the executing driver, exactly as before the proposal
  → Trip continues its ordinary lifecycle, unaffected
```

**Handoff, withdrawn by the original driver before consent — newly locked, not in the first pass:**

```
Assignment/Trip exists, Handoff PROPOSED or SUBSTITUTE_ACCEPTED, not yet CONSENTED
  → original driver withdraws                                     (Invariant #31)
  → the Handoff resolves to WITHDRAWN — no Assignment/Trip mutation of any kind
  → original driver remains the executing driver
```

Withdrawal **after** passenger consent is not a valid transition (Invariant #32) — there is no diagram for it because it cannot occur; see §7/§8.

**Original driver cannot fulfil, after a refused, withdrawn, or never-proposed Handoff:**

```
Assignment/Trip exists
  → (optionally) Handoff proposed and refused/withdrawn, per the diagrams above
  → original driver independently cannot fulfil the commitment
  → the ORDINARY, ALREADY-BUILT D-01 termination flow is used
    (CommitmentTerminationApplicationService.terminate, initiator = DRIVER)
  → Trip/Assignment: → TERMINATED, via the existing, unmodified mechanism
```

This last diagram is locked OQ-4's third clause made explicit: **Handoff never invents a new termination path.** A failed original driver, whether or not a Handoff was ever proposed, refused, or withdrawn, ends the commitment exactly the way D-01 already ends it today — `CommitmentTerminationApplicationService.terminate` (`CommitmentTerminationApplicationService.kt:41-117`), unchanged.

---

## 4. Actors

| Actor | Definition | Can initiate |
|---|---|---|
| **Original / committing driver** | The driver named on `Assignment.driver` at `Assignment.create` time — unchanged for the life of the commitment (Invariant #16). | Proposes a Handoff, naming the substitute (Invariant #9); may withdraw it **only before passenger consent** (Invariant #31/#32). Reuses the existing driver-authorization pattern already live for `AssignmentTerminationController.terminate` (`verified.drv == assignment.driver.driverId`, `AssignmentTerminationController.kt:50`). |
| **Current executing driver** | Whoever is, at any given moment, the driver actually expected to execute the ride — `Assignment.driver` until and unless a Handoff has been consented, the substitute thereafter (Invariant #17). Before any Handoff, the original and executing driver are the same person; this is not a separate identity, only a separate *fact*. | N/A — a derived role, not an actor who takes actions in their own right (§6). |
| **Substitute driver** | The specific, named, registered driver (Invariant #34) the original driver proposes. Never chosen by PIOS (Invariant #10, #35). Named on the proposal from the moment of `PROPOSED`, but not yet a party to the commitment until they accept. | **Explicitly accepts (or, symmetrically, could decline) the specific Handoff naming them, before the passenger is ever asked** (Invariant #27) — this is now a required, locked step, not an open question. |
| **Passenger** | The order's own passenger, reached the same way every existing passenger-facing check already reaches them — via the order's `ACCEPTED` Proposal's `passengerReference` (`AssignmentController.listAssignmentsHttp`'s passenger branch, `AssignmentController.kt:206-207`). May be a guest identity (Invariant #37). | Consents to, or refuses, the exact named, already-substitute-accepted Handoff (Invariants #11-13, #24-25, #28). |
| **PIOS** | The system itself. | **Never** initiates or selects a substitute (Invariant #10). Verifies the substitute is a real, registered driver with a valid association (Invariant #34) — a check, not a selection. Enforces every invariant above and records the resulting facts; not a party to the Handoff. |

---

## 5. Handoff State Model

A minimal, purpose-built state set for the Handoff proposal/request itself — **distinct from, and layered on top of, `TripStatus`/`AssignmentStatus`, which this model never re-enters or duplicates** (Invariants #3, #4).

```
PROPOSED
  → SUBSTITUTE_ACCEPTED   (the named substitute explicitly accepts — now required, Invariant #27)
      → CONSENTED   (passenger positively confirms — the one and only moment execution changes)
      → REFUSED     (passenger declines)
      → WITHDRAWN   (original driver withdraws — only reachable from PROPOSED or SUBSTITUTE_ACCEPTED, never from CONSENTED, Invariant #32)
  → WITHDRAWN         (original driver withdraws before the substitute has even accepted)
```

Revision from the first pass of this document, recorded rather than silently overwritten:

- **`SUBSTITUTE_ACCEPTED` is now included** — the first pass deliberately omitted it as an open question ("whether the current business flow actually requires a separate substitute-acceptance step... is itself OPEN"). PO decision item 14 resolves this: the step is required. `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4's own original reasoning for it — "asking the passenger to consent to a driver who has not themselves agreed would produce a consent to something that may evaporate" — is exactly the reasoning this locked decision now ratifies.
- **`CONSENTED` is only reachable from `SUBSTITUTE_ACCEPTED`, never directly from `PROPOSED`.** This is what makes Invariant #28 ("a consent request cannot be constitutive if the substitute hasn't accepted first") a structural property of the state machine, not merely a policy statement an implementation could accidentally bypass.
- **`WITHDRAWN` is now included, with an explicit, asymmetric reachability rule**: reachable from `PROPOSED` or `SUBSTITUTE_ACCEPTED` (Invariant #31), **never** from `CONSENTED` (Invariant #32). The first pass left this entirely open ("not decided by any locked PO input"); it is now fully resolved in both directions.
- **No `EXPIRED` state exists, and none is added.** The first pass already omitted one as open; PO decision item 27 now closes the question definitively in the same direction — **there is no TTL, by design, not by omission.** Invariant #30's own closed list of resolution triggers (accept, refuse, withdraw, or the Trip leaving the eligible window) is exhaustive; a bare timeout is not a fifth trigger.
- **A Handoff whose underlying Trip leaves `{CREATED, ARRIVED}`** (starts, completes, or is independently terminated) while the Handoff is still `PROPOSED` or `SUBSTITUTE_ACCEPTED` becomes moot by construction, per Invariant #30's own fourth trigger and Invariant #8 — this is not a new explicit state, it is the existing Trip-state boundary doing the same work it already does for every other Handoff-forbidding condition.

---

## 6. Data Model

**Not implemented. Described only — what information must be represented, and why.** No table, column, or migration is proposed as final.

| Information | Why it must exist | Authoritative or derived? | Needs persistence? |
|---|---|---|---|
| **Original committing driver** | Answers "who originally accepted the commitment?" | **Authoritative, already exists**: `Assignment.driver`. Handoff must simply never write to it. | Already persisted; no change. |
| **Current executing driver** | Answers "who actually executed (or will execute) the ride?" (Invariant #17). | **Authoritative, does not yet exist.** `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.5 names the shape (`Trip.executingDriver`, defaulting to `assignment.driver`, changed only at `CONSENTED`) — recorded as the evaluated, DESIGN-1-consistent shape, not re-authorized as final schema. | Yes — the one genuinely new durable fact DESIGN 1 requires. |
| **Substitute driver (named on the proposal)** | The specific driver proposed, before/regardless of acceptance or consent. | **Authoritative** for the Handoff record; becomes the same value as "current executing driver" only upon `CONSENTED`. | Yes. |
| **Handoff initiator** | Always the original committing driver (Invariant #9) — recorded for audit completeness. | Authoritative, but degenerate. | Yes, for audit (§12). |
| **Substitute-acceptance fact and timestamp** | The now-required, locked gate before consent can occur (Invariant #27/#28). | Authoritative — a distinct act from proposal or consent. | Yes — newly required by this pass (absent from the first pass's own table). |
| **Passenger consent (fact of `CONSENTED`)** | The constitutive act itself (Invariants #11, #12). | Authoritative. | Yes. |
| **Consent timestamp** | Required for audit (§12) and for any future observability work (Invariant #26/D-08(a)). | Derived fact of the consent act, but must be stored. | Yes. |
| **Handoff (proposal) timestamp** | Same reasoning as consent timestamp, for `PROPOSED`. | Derived fact of the proposal act. | Yes. |
| **Withdrawal fact and timestamp, if withdrawn** | Newly required by this pass (Invariant #31/#32) — must also record *that it could only have happened pre-consent*, which is structurally guaranteed by §5's own state machine rather than needing a separate stored flag. | Derived fact of the withdrawal act. | Yes. |
| **Linkage to the existing Assignment/Trip** | Handoff has no independent existence — always refers to exactly one Assignment (Invariant #1/#2) and, transitively, its one Trip. | Authoritative (a foreign reference). | Yes — the same `assignmentId`-shaped reference `Trip` itself already uses. |
| **Single-hop protection** | Invariants #14/#15 must be checkable. | Derived — computable from "does a `CONSENTED` Handoff already exist for this Assignment, and is the *current* proposer the original driver or a previous substitute." | Likely derivable from the linkage + initiator fields; an implementation judgment, not decided here. |
| **Eligibility check result (substitute is a real, registered driver with a valid association)** | Invariant #34 must be enforceable at propose time, not merely assumed. | Not itself a stored fact on the Handoff record — a **read-time check** against `driver-management`'s own existing driver registry at proposal time (see §11), the same way `ProposalApplicationService.handle` already checks availability for an ordinary Proposal. | No new field on the Handoff record itself required for this — the check is a precondition, not a stored attribute. |
| **Audit/history of every Handoff attempt** (proposed, substitute-accepted, consented, refused, or withdrawn) | PO decision item 6 ("preserve historical attribution") and §12's own requirement. | Each individual Handoff record is itself the audit trail; per DESIGN 1, **multiple historical Handoff records may exist for one Assignment over its lifetime** — none ever deleted or overwritten, mirroring `Proposal`/`Termination`'s own existing historical-evidence discipline. | Yes, and specifically append-only. |

**Not decided here, and explicitly out of this section's own scope**: table name, column names, migration numbering, exact module ownership (Dispatch is the natural owner per the reconciliation's own reasoning, restated as inherited context, not re-decided).

---

## 7. API Contract

**Described, not implemented.** No endpoint path, HTTP verb, or request/response shape below is authorized as final.

### Operation: Propose Handoff

- **Actor**: original committing driver.
- **Intent**: name a substitute for the existing, live commitment.
- **Input**: the target Assignment (or its Trip) identity; the substitute driver's identifier.
- **Server-derived fields**: the initiator (from the verified session, never client-supplied); the proposal timestamp.
- **Expected result**: a new Handoff record in `PROPOSED` state.
- **Authorization**: caller's `verified.drv` must equal `Assignment.driver.driverId` (§4).
- **Eligibility check** *(newly required by this pass — resolves the first pass's own "substitute eligibility — OPEN" item)*: the named substitute must resolve to a real, registered driver Identity with a valid driver association (Invariant #34) — rejected outright (not merely accepted as an unverified string) if it does not. This is a verification, never a PIOS-side selection (Invariant #35).
- **Idempotency**: reclassified in this pass from an open PO question to an ordinary implementation detail — no product decision is required to choose between "absorb a repeated identical request" and "reject as a duplicate"; the existing codebase's own precedent (`TerminateCommitmentCommand.requestId`-based idempotency, `CommitmentTerminationApplicationService.kt:53-61`) is available to the implementing team without further PO input.
- **Forbidden conditions**: Trip not in `{CREATED, ARRIVED}` (Invariant #7/#8); an existing non-terminal Handoff already pending for this Assignment (Invariant #14); caller is not `Assignment.driver`; the named substitute equals the original driver; the named substitute fails the eligibility check above.

### Operation: Substitute Acceptance — **now a required operation, resolved from the first pass's OPEN item**

- **Actor**: the named substitute driver.
- **Intent**: explicitly accept this specific Handoff (Invariant #27).
- **Input**: the Handoff proposal's own identity.
- **Server-derived fields**: acceptance timestamp; the accepting identity, from the verified session, never client-supplied.
- **Expected result**: the Handoff transitions `PROPOSED → SUBSTITUTE_ACCEPTED`. Nothing about the ride itself changes yet (mirrors the same "nothing changes yet" property `PROPOSED` already has).
- **Authorization**: caller's `verified.drv` must equal the Handoff's own named substitute driver id — a **new** authorization pattern this specification names (§11), since no existing check compares a caller against "the substitute named on someone else's proposal."
- **Forbidden conditions**: the Handoff is not `PROPOSED`; caller is not the named substitute; the underlying Trip has left `{CREATED, ARRIVED}` since the proposal (a race — §8).

### Operation: Passenger Consent

- **Actor**: passenger (including a guest passenger, Invariant #37).
- **Intent**: positively confirm the exact, already-substitute-accepted Handoff (Invariant #12/#13/#28).
- **Input**: the Handoff proposal's own identity; confirmation bound to that specific proposal (not a bare "yes" disconnected from which substitute — closing the risk of a stale consent being replayed against a later, different proposal for the same Assignment).
- **Server-derived fields**: consent timestamp; the consenting identity, from the verified session, never client-supplied.
- **Precondition, newly explicit in this pass**: the Handoff must already be `SUBSTITUTE_ACCEPTED` (Invariant #28) — consenting to a merely-`PROPOSED` Handoff is rejected outright, not merely discouraged.
- **Atomic revalidation, newly required (Invariant #33)**: immediately before recording consent, under the same order lock, the system must re-confirm the Handoff is still `SUBSTITUTE_ACCEPTED` and the Trip is still `{CREATED, ARRIVED}` — not merely rely on whatever was true when the passenger's own screen last polled.
- **Expected result**: the Handoff transitions to `CONSENTED`; the executing-driver fact (§6) updates to the substitute; the original Assignment/Trip is otherwise untouched (Invariants #3, #4, #18, #19).
- **Authorization**: caller's `verified.sub` must equal the order's own Proposal's `passengerReference.passengerId` (§4). Self-only. Guest identities are not excluded (Invariant #37) — the same check applies regardless of whether the identity is phone-verified.
- **Idempotency**: a repeated consent to an already-`CONSENTED` Handoff is a safe no-op, consistent with this codebase's general precedent for idempotent resolution endpoints.
- **Forbidden conditions**: the Handoff is not `SUBSTITUTE_ACCEPTED`; the underlying Trip has left `{CREATED, ARRIVED}` since substitute acceptance (§8); caller is not the order's own passenger.

### Operation: Passenger Refusal

- **Actor**: passenger.
- **Intent**: decline the exact named, substitute-accepted Handoff (Invariant #24).
- **Input**: the Handoff proposal's own identity.
- **Server-derived fields**: refusal timestamp; the refusing identity.
- **Expected result**: the Handoff transitions to `REFUSED`; the Assignment/Trip is completely unaffected (Invariant #24/#25).
- **Authorization**: same passenger-check pattern as consent.
- **Forbidden conditions**: the Handoff is not `PROPOSED` or `SUBSTITUTE_ACCEPTED`.

### Operation: Withdraw Handoff — **newly specified, resolved from the first pass's OPEN item**

- **Actor**: original committing driver.
- **Intent**: retract a Handoff before it becomes irreversible.
- **Input**: the Handoff proposal's own identity.
- **Server-derived fields**: withdrawal timestamp; the withdrawing identity (must match the proposal's own initiator).
- **Expected result**: the Handoff transitions to `WITHDRAWN`; the Assignment/Trip is completely unaffected.
- **Authorization**: caller's `verified.drv` must equal `Assignment.driver.driverId` (the same check as Propose).
- **Forbidden conditions, exact and locked (Invariant #31/#32)**: the Handoff is already `CONSENTED` — withdrawal after constitutive consent is never permitted, unconditionally. Withdrawal from `PROPOSED` or `SUBSTITUTE_ACCEPTED` is permitted.

---

## 8. Concurrency

All races assume the existing D-01 locking discipline is reused unmodified: `PostgreSQLOrderGuard.lock(order)` (`PostgreSQLOrderGuard.kt:9-17`), taken first, inside the same transaction, before any Assignment/Trip/Handoff row lock. Handoff introduces no new lock primitive.

| # | Race | Lock boundary | Expected winner | Final valid state | Rejected operation |
|---|---|---|---|---|---|
| 1 | **Two concurrent Handoff proposals for the same Assignment** | Both acquire `orderGuard.lock(order)` before checking for an existing non-terminal Handoff. | Whichever acquires the lock first. | Exactly one non-terminal Handoff exists for the Assignment. | The second proposal — rejected once the first's existence is visible under lock; a persistent uniqueness constraint on "at most one non-terminal Handoff per Assignment" is the backstop, the same two-layer pattern already used for `trips.assignment_id UNIQUE`. |
| 2 | **Consent arriving before substitute acceptance has committed** | Both under the same order lock; consent's own precondition check (§7) requires `SUBSTITUTE_ACCEPTED`. | Whichever commits first. | If acceptance wins: consent then proceeds normally. If consent somehow arrives first (client-side race, not a true server race since the passenger cannot even be shown a substitute who hasn't accepted per §13): rejected outright by the precondition check (Invariant #28) — this is a **hard, structural rejection, not a timing race** the server needs to arbitrate, since `SUBSTITUTE_ACCEPTED` is a strict precondition, not a best-effort ordering. | A consent request for a Handoff still in `PROPOSED`. |
| 3 | **Handoff (any stage) vs. D-01 termination** | Both acquire `orderGuard.lock(order)` first — the same shared lock D-01 already uses. | Whichever acquires the lock first. | If termination wins: Trip → `TERMINATED`; any pending Handoff for that Assignment becomes moot (Invariant #6 forbids ever consenting it afterward — re-checked at consent time under the same lock, per Invariant #33). If Handoff-consent wins: executing driver updates; a subsequent termination attempt proceeds exactly as D-01 already handles a live commitment. | Whichever operation observes the order already resolved the other way, cleanly (mirrors `CommitmentTerminationPostgreSQLTest.kt`'s own "single first committer" proof for completion-vs-termination, extended by analogy). |
| 4 | **Handoff consent vs. termination, specifically at the atomic-revalidation moment** | Same order lock, re-checked at consent time per Invariant #33 — not merely at proposal or substitute-acceptance time. | Termination, if it reaches the lock first. | Trip → `TERMINATED`; the Handoff, if not yet consented, is foreclosed at the revalidation step — consent must fail (Invariant #6/#33, not optional). | The consent operation, if it arrives after termination has already committed. |
| 5 | **Handoff consent vs. Trip start (entering `IN_PROGRESS`)** | Same order lock. Invariant #33's own atomic revalidation re-verifies `Trip.status ∈ {CREATED, ARRIVED}` at the exact moment consent is recorded, not merely at proposal or acceptance time. | Whichever operation (start, or consent) acquires the lock first. | If `start` wins: Trip → `IN_PROGRESS`; the pending Handoff is rejected at the revalidation step (Invariant #8). If consent wins: executing driver updates while still `CREATED`/`ARRIVED`; `start` then proceeds normally, understood as the substitute's own ride-progress action. | Consent, if `start` already committed first. |
| 6 | **Stale consent/refusal/withdrawal** (a request arriving for a Handoff that has already resolved) | Read-then-check under the order lock, same as every other resolution here. | The first resolution to commit. | The Handoff's own terminal state, set exactly once. | A second, stale request — idempotent no-op if it matches the already-committed outcome, rejected as a conflict if it contradicts it. |
| 7 | **Chained Handoff attempt** (a substitute, once `CONSENTED`, tries to propose their own Handoff) | Same order lock; the propose-operation's own authorization check must compare the caller against the *original* `Assignment.driver`, not the current executing driver (Invariant #15). | N/A — not a race, a straightforward rejection: the caller (the substitute) is never `Assignment.driver`. | Execution stays with the already-consented substitute; no chained Handoff record is ever created. | The substitute's own propose-Handoff attempt — rejected at the authorization layer. |
| 8 | **The same substitute proposed twice** | Same as race #1, plus §7's own reclassified (non-PO-blocking) idempotency question for the propose operation. | Depends on the implementation's own idempotency choice (§7 — no longer a PO decision). | Exactly one live Handoff naming that substitute exists at any time. | The second, duplicate call — absorbed or rejected per the implementation's own choice. |
| 9 | **Withdrawal racing consent — newly named in this pass (Invariant #32)** | Both under the same order lock. | Whichever commits first. | If consent wins first: the Handoff is `CONSENTED`; a subsequent withdrawal attempt is rejected outright (Invariant #32 is unconditional, not merely first-committer-wins — even a withdrawal that would have "won" a naive race must still fail once consent has actually committed). If withdrawal wins first: the Handoff is `WITHDRAWN`; a subsequent consent attempt fails the `SUBSTITUTE_ACCEPTED`-or-live precondition. | Withdrawal, specifically, whenever it targets an already-`CONSENTED` Handoff — this is the one race in this table where the *locked business rule itself* (not merely lock ordering) forbids one outcome regardless of timing. |
| 10 | **Substitute acceptance racing withdrawal or Trip-state change** | Same order lock. | Whichever commits first. | If withdrawal/termination/start wins: the acceptance attempt is rejected (the Handoff is no longer `PROPOSED`). If acceptance wins: the Handoff reaches `SUBSTITUTE_ACCEPTED` normally. | The acceptance operation, if it arrives after the Handoff has already left `PROPOSED` for any reason. |

No test is written or implemented for any of the above, per instruction.

---

## 9. Events

**No event contract is changed by this document.**

**Existing events reused, unmodified:**
- `CommitmentTerminated` — reused exactly as-is for the "original driver cannot fulfil" lifecycle path (§3).
- `AssignmentCompleted`/`TripCompleted` — reused exactly as-is; per Invariant #18, `payload.statedPrice` is completely unaffected by which driver executed the ride.

**New Handoff-specific events that would be required** (naming, not authorizing):
- `HandoffProposed`, emitted at `PROPOSED`.
- `HandoffSubstituteAccepted`, emitted at `SUBSTITUTE_ACCEPTED` — **new in this pass**, since the state itself is newly locked (§5); this is the fact that unlocks the passenger-consent step and so is a real, necessary domain event, not merely audit.
- `HandoffConsented`, emitted at `CONSENTED` — the moment the executing driver changes; the one event any future attribution-consuming code (§10) would key off of.
- `HandoffRefused` and `HandoffWithdrawn` (or one shared "resolved negatively" shape covering both) — for audit (§12).

**Payload information required**, at minimum, for `HandoffConsented`: the Assignment/Trip identity; the original driver; the substitute (now executing) driver; the consent timestamp. No currency, fee, or financial field of any kind (Invariant #20).

**`eventVersion` implication, flagged not implemented**: if attribution work (§10) ever requires `AssignmentCompleted`/`TripCompleted` to carry an executing-driver-distinct-from-`driverId` field, that would be an **additive field, `eventVersion` staying at its current value (1)** — following the exact precedent ADR-065/D-06 already established. Flagged as a likely future need, not implemented, and not itself authorized here.

---

## 10. Attribution

Integrates the D-08 boundary (`PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1763-1774`) with the now-locked OQ-3/attribution decisions, without implementing either.

**What is locked:**
- No financial credit/reward for the original driver (Invariant #21).
- The original driver retains the historical/origination fact regardless (Invariant #22, PO decision item 33) — matching D-08(b)'s own "relationship-facing facts credit the committing driver" half.
- The executing/substitute driver receives **execution attribution and stated-earnings attribution** for the ride they actually execute (Invariant #23, PO decision item 34) — fully matching D-08(b)'s own recommended split, on both halves.
- PIOS never splits or transfers money in connection with a Handoff, in any form (Invariant #20, #35, PO decision item 35).

**What this does NOT authorize, per instruction ("Do not implement D-08 caps or scoring"):**
- No implementation of `totalStatedEarnings`/`completedRidesCount` re-routing logic is designed or built here — this section records *which* driver the future metric should credit once built, not how.
- No cap, threshold, or scoring mechanism of any kind (D-08(a)'s own remaining open half — see §14/§15).
- No new business-metric concept beyond the two that already exist is proposed.

**Genuinely still open, narrower than the now-locked split itself**: whether the original driver's own facilitation fact should ever surface as a *counted* metric (distinct from simply being queryable per-Assignment) — named as future, non-blocking work in §15, per this task's own explicit "Known non-blocking future work" list.

---

## 11. Security

Authorization invariants, grounded in what already exists (`SessionTokenVerifier.VerifiedToken(sub: String, drv: String?)`, `SessionTokenVerifier.kt`) plus the eligibility/guest decisions newly locked in this pass.

- **A requester cannot choose an arbitrary driver identity to act as.** Every operation in §7 derives the acting identity from the verified session (`verified.sub`/`verified.drv`), never from a client-supplied field — the same discipline already governing `AssignmentTerminationController.terminate` (`AssignmentTerminationController.kt:44,50`). PO decision item 24 restates this explicitly for Handoff.
- **The substitute must correspond to a real, eligible, registered driver Identity with a valid driver association — resolved, no longer OPEN.** PO decision item 23 requires this outright: unlike the ordinary `DriverReference`'s existing "plain string, never verified" convention (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4.4), Handoff's own propose operation must actually check the named substitute against `driver-management`'s own registered-driver data before accepting the proposal (§7). This is a *verification*, not a selection — it never causes PIOS to choose or suggest a substitute (Invariant #35).
- **Substitute self-authorization for the acceptance step — a genuinely new pattern (§7).** `verified.drv` must equal the Handoff's own named substitute driver id. No existing endpoint compares a caller against "the substitute named on someone else's proposal"; this is the one authorization shape Handoff introduces that has no direct precedent elsewhere in the codebase, named explicitly so it is not silently assumed to already exist.
- **Passenger consent must be self-only, authenticated.** `verified.sub == proposal.passengerReference.passengerId` — the exact, already-live pattern (`AssignmentController.kt:206-207`), reused unmodified.
- **Guest passengers may consent — resolved, no longer OPEN.** PO decision items 25-26: a guest (non-phone-verified) passenger identity may consent through the existing protected guest context, on the same authorization terms as any other passenger session (`verified.sub` matching), **provided the consent is bound to the exact Commitment/Handoff** — i.e., the same anti-replay/anti-cross-binding discipline §7's own consent operation already requires (the input must name the specific Handoff, never "consent to whatever is pending for this session"), applied without exception to a guest session too.
- **One actor cannot impersonate another party.** Covered by every bullet above — every check compares the verified session's own identity, never a request-body-asserted one, against the specific role being exercised.
- **Server derives every actor identity.** No operation accepts a client-asserted driver id, passenger id, or substitute id as its own authorization basis — only as *content*, never as *proof of who is asking*.

---

## 12. Observability / Audit

Every Handoff attempt (proposed, substitute-accepted, consented, refused, or withdrawn) must be durably recorded, per §6's own append-only requirement, sufficient to answer, for any Assignment, at any later time:

- Who initiated the Handoff (always the original committing driver).
- Who was the original executor/committer before this Handoff (`Assignment.driver`, unchanged, always available).
- Who was named as the substitute, and whether/when they accepted.
- Who became the substitute, if the Handoff was consented.
- Who consented — or who refused, or who withdrew (and, per Invariant #32, withdrawal is only ever recordable *before* consent, never after — the audit trail itself is a witness to this invariant holding).
- The proposal timestamp, the substitute-acceptance timestamp, and the resolution (consent/refusal/withdrawal) timestamp.
- The final result.

**Sensitive data discipline**: no field beyond the above is logged — no free-text note, no location data — mirroring `Termination.note`'s own existing restraint (capped, optional, never used for ranking or metrics per `ADR-080`).

---

## 13. Frontend Behavior

**UX states only — no UI is designed or implemented here.**

- **Handoff proposed**: the original driver's own screen reflects that a Handoff is pending, naming the substitute they chose, and that it is awaiting the substitute's own acceptance first.
- **Substitute sees the proposal and can accept or decline it — new in this pass**, since the acceptance step is now locked (§5/§7): the substitute's own screen must surface this Handoff distinctly from an ordinary Proposal, since it is not one.
- **Passenger sees the exact substitute, only after they have accepted**: per Invariant #28, the passenger is never shown a consent prompt for a substitute who has not yet accepted — what facts about the substitute are shown (name, availability — no rating, no ride count, per `DRIVER_IDENTITY_DESIGN_DECISION.md`'s own already-ratified limits) is inherited from that existing constraint.
- **Passenger accepts**: a blocking confirmation step, not a toast or passive notice (Invariant #12).
- **Passenger refuses**: an equally explicit, available action; refusing must visibly leave the original driver as the ride's own driver, with no ambiguity that anything changed (Invariant #24).
- **Original driver may withdraw, only while the option is genuinely available** — the original driver's own screen must stop offering a withdraw action the moment the Handoff reaches `CONSENTED` (Invariant #32), not merely disable it after a failed attempt.
- **Original driver remains visible if refusal or withdrawal occurs**: the passenger-facing screen must not, even transiently, suggest the substitute is now their driver unless `CONSENTED` has actually been recorded.
- **Execution driver updates after successful Handoff**: once `CONSENTED`, every passenger-facing and driver-facing surface that currently shows "your driver" (which today reads `Assignment.driver` directly) must instead reflect the current executing driver (§6) — a read-model change, named as required, not designed here.

---

## 14. Compatibility

- **D-01 (Commitment Termination)**: fully compatible by construction. `TERMINATED` remains terminal (Invariant #5/#6); Handoff never calls `Trip.terminate`/`Assignment.terminate`; the "original driver cannot fulfil" path reuses D-01's own existing, unmodified termination flow.
- **D-02 (Driver Business Metrics Carry No Value)**: unaffected in kind — §10's own attribution work moves *which* driver an existing metric credits, never introduces a new metric, ranking, or scoring mechanism (`ADR-081`'s own boundary).
- **D-03 (Phone-Verified Identity Recovery)**: unaffected — Handoff's own authorization model reuses the existing session-token verification unmodified; the newly-locked guest-consent allowance (§11) does not touch identity recovery itself, only which sessions may consent.
- **D-06 (Settlement as Evidence)**: fully compatible. `Trip.agreedAmount` is untouched (Invariant #18); there is exactly one Trip, never two.
- **D-08 (Handoff's binding conditions)**: **fully resolved on attribution (D-08(b)), partially resolved on cap/observability (D-08(a)).** D-08(b)'s split is now fully locked (§10) — relationship-facing facts to the committing driver, execution/earnings facts to the executing driver, exactly as recommended. D-08(a)'s own principle (no cap before observation) is locked (Invariant #26/#31 as recorded in the prior pass), but the observability mechanism itself and any cap value/threshold remain explicitly unbuilt — classified as D-08's own separate, future work (§15).
- **Explicitly outside D-07's own scope**: Settlement's own "what constitutes settled" question (OQ-8); Fallback Tier-2/Network's own reserved question (OQ-10); any ride-outcome taxonomy; any payment/billing mechanism of any kind.

---

## 15. Final Consistency Check

Performed against `docs/PIOS_D07_HANDOFF_RECONCILIATION.md`, this document's own body above, `ADR-054`, and D-01/D-06/D-08's own already-cited sources. Each item is classified as **REAL contradiction**, **documentation gap**, or **no contradiction**. Where a documentation gap was found, it was closed in the sections above (this is that fix); no code was touched for any of these.

**A. D-01 contradiction — could any Handoff path revive `TERMINATED`?**
**No contradiction.** Invariants #5/#6, PO decision items 17-18, and §3's own lifecycle diagrams all converge on the same rule: Handoff is reachable only from `{CREATED, ARRIVED}` (Invariant #7/#8), and `Trip.terminate`'s own existing guard already excludes `TERMINATED`/`COMPLETED` from every further transition. No diagram in §3, and no operation in §7, contains a path from `TERMINATED` back into any Handoff state.

**B. D-06 contradiction — could Handoff create a second Trip or change `agreedAmount`?**
**No contradiction.** Invariants #4/#18 are unconditional; every lifecycle diagram in §3 operates on "the same, single `Assignment`/`Trip` pair," stated explicitly at the top of that section; §9's own event analysis confirms `payload.statedPrice` needs no change because it is sourced from the one, unchanged `Trip.agreedAmount` regardless of which driver executes.

**C. D-08 contradiction — could Handoff attribution create financial credit or alter the existing D-08 boundary?**
**No contradiction.** Invariants #20-23 and §10's own "What this does NOT authorize" list are explicit that attribution moves *existing, non-financial* metrics (`completedRidesCount`, `totalStatedEarnings` — both already ratified as carrying no value per D-02/`ADR-081`) between which driver's counter they credit; no new financial mechanism, commission, or credit (in the monetary sense) is introduced. D-08(b)'s own recommended split is matched exactly, not altered.

**D. ADR-054 contradiction — does the D-07 model require a narrow amendment rather than silently violating ADR-054?**
**Documentation gap, now closed.** ADR-054 Part 5's own text ("No Team, Fleet, crew, or delegation mechanism of any kind") is not amended, narrowed, or reinterpreted anywhere in this document — it remains exactly as ratified. This specification does not implement anything, so it cannot itself violate the ban; but it must, and does, record plainly that a real implementation would require the narrow, dated, in-place supersession pointer the reconciliation already identified. Recorded explicitly, per this task's own §"Prefer recording in D-07 specification" instruction, immediately below in §17 — no amendment to `ADR-054` itself is made by this document.

**E. Assignment invariant — does the specification accidentally require a second Assignment anywhere?**
**No contradiction.** Every operation in §7, every state in §5, and every diagram in §3 operates on the Assignment/Trip pair that already exists at proposal time; nothing in this specification's own text calls, references, or implies a second `Assignment.create`. Invariant #1/#3 restate this as a standing constraint every later section is written against.

**F. Identity/security — could a caller choose an arbitrary `driverId`?**
**No contradiction.** §11's own "server derives every actor identity" rule, applied to all five operations in §7 (propose, accept, consent, refuse, withdraw), means every actor identity is read from the verified session (`verified.sub`/`verified.drv`), never from request content. The one genuinely new authorization shape (§7's Substitute Acceptance operation, §11's own "genuinely new pattern" note) is explicitly named as new precisely so it does not get silently assumed to inherit protection it does not yet have anywhere else in the codebase.

**G. Guest — could a public/unbound guest request become consent for another Commitment?**
**No contradiction, given the binding requirement is honored.** PO decision item 26 requires guest consent to be "bound to the exact Commitment/Handoff" — §7's own Consent operation already requires the input to name the specific Handoff proposal, not merely "whatever is pending," and §11 explicitly extends this same anti-replay/anti-cross-binding requirement to guest sessions without exception. The risk this question names (an unbound guest action silently applying to the wrong Commitment) is exactly what the existing "consent must be tied to the exact named substitute/Handoff" design (Invariant #13, §7's own input specification) already forecloses — it was not a guest-specific gap.

**H. Chaining — could the substitute become a new original driver and perform another Handoff?**
**No contradiction.** Invariant #15 (chained Handoff prohibited) and §8's race #7 both confirm this is rejected at the authorization layer: the propose operation's own check compares the caller against `Assignment.driver` — the *original* committing driver, which never changes (Invariant #16) — so a `CONSENTED` substitute, now the executing driver, still fails that check if they attempt to propose their own Handoff. The executing-driver fact (§6) and the authorization fact (`Assignment.driver`) are deliberately different fields for exactly this reason.

**I. Consent — could passenger consent occur before substitute acceptance?**
**No contradiction, closed structurally in this pass.** §5's own revised state model makes `CONSENTED` reachable only from `SUBSTITUTE_ACCEPTED`, never directly from `PROPOSED` — this was the specific gap the first pass of this specification left open (no substitute-acceptance state existed at all), and this pass's own Invariant #27/#28 and §7's own "Precondition, newly explicit in this pass" note close it. §8's own race #2 additionally names the concurrency form of this same question and confirms the precondition check, not merely lock ordering, is what prevents it.

**J. Withdrawal — could the original driver withdraw after constitutive passenger consent?**
**No contradiction, closed explicitly in this pass.** Invariant #32 and §7's own Withdraw Handoff operation state the forbidden condition unconditionally: "the Handoff is already `CONSENTED`" is a hard rejection, not a race-dependent outcome. §8's own race #9 names this specifically as "the one race in this table where the locked business rule itself, not merely lock ordering, forbids one outcome regardless of timing" — i.e., even if a withdrawal request technically arrives at the lock before a slower consent request finishes committing, withdrawal must still fail once consent has actually committed; this is stated explicitly so an implementer does not mistake it for an ordinary first-committer-wins race.

---

## 16. Open Questions

**Not reopened**: D-07's architecture (DESIGN 1), OQ-1, OQ-2, OQ-3, OQ-4, OQ-7, substitute acceptance, expiry, withdrawal, eligibility, guest consent — all locked. Everything the first pass of this document listed as open under those headings is now resolved above; nothing below restates them.

**Known non-blocking future work** (named, not treated as implementation blockers):

1. **D-08(a)'s own remaining mechanism** — the exact observability tooling, and any eventual cap threshold/window, once real usage data exists. Classified as **D-08, not D-07**; Invariant #26/#31 already state the binding *principle* (no cap before observation) this specification enforces today.
2. **Whether the original driver's own facilitation/origination fact should ever become a counted, driver-facing metric** — distinct from simply being queryable per-Assignment (§10/§12, which already satisfy the audit requirement regardless of how this is answered).
3. **Other future Settlement/Network decisions** (OQ-8, OQ-10) — confirmed, once again, as entirely outside D-07's own scope; Handoff neither depends on nor advances either.

**No item in this list blocks implementation of D-07 as specified above.** No new PO question is invented here beyond what genuinely remains open per this task's own explicit instruction not to do so.

---

## 17. ADR-054 Amendment — Recorded, Not Made

**`ADR-054` is not modified by this document, and no amendment is written here.** Per this task's own instruction ("prefer recording in D-07 specification"), the requirement is recorded plainly instead:

> **`ADR-054` requires a narrow amendment to permit this specific single-hop, consent-based Handoff mechanism while preserving its other delegation prohibitions.**

Specifically, once implementation is authorized, the amendment would need to be a second, narrow, dated, in-place supersession pointer into `ADR-054:126` ("No Team, Fleet, crew, or delegation mechanism of any kind"), of the exact same shape as the already-existing one at `ADR-054:128` (the ADR-068 Fallback-candidate-filtering amendment) — naming DESIGN 1 specifically, citing the now-fully-locked OQ-1/OQ-2/OQ-3/OQ-4/OQ-7 plus substitute-acceptance/withdrawal/eligibility/guest-consent decisions by number, and explicitly reaffirming that every other part of ADR-054 Part 5's own prohibition (no general Team/Fleet/crew concept, no group, no shared client list) remains exactly as ratified. **No historical ADR-054 text is erased, rewritten, or reinterpreted by this document.**

---

## Implementation Readiness

**D-07 is READY for implementation**, in the sense that every product-level decision this specification's own §2-§14 depend on is now locked — no further Product Owner input is required to begin domain modeling, persistence design, or application-service work against the invariants, lifecycle, state model, data model, API contract, concurrency rules, attribution boundary, and security rules stated above. The items in §16 are named future work, not blockers, per that section's own explicit conclusion.

**What implementation work is required** (unchanged in substance from the first pass, restated here as the current, final sequence):

1. **Domain invariants** — encode §2's invariants directly into a `Handoff` domain type and the `Trip.executingDriver`-shaped fact (§6), the same way `Termination`/`Trip.terminate` already encode D-01's own invariants as `check()` preconditions.
2. **Persistence** — the minimal, append-only schema §6 describes (table/column naming, migration numbering, and module ownership left to the implementing team within these boundaries).
3. **Application services** — the propose/substitute-accept/consent/refuse/withdraw operations (§7), including the concurrency discipline (§8) reusing `OrderGuard` unmodified, and the eligibility check (§11) against `driver-management`'s own registered-driver data.
4. **Authorization** — the four authorization patterns §11 names, including the one genuinely new one (substitute self-authorization for the acceptance step).
5. **API** — the transport layer for §7's five operations, following this codebase's own existing controller/authorization conventions.
6. **Events** — `HandoffProposed`/`HandoffSubstituteAccepted`/`HandoffConsented`/`HandoffRefused`/`HandoffWithdrawn` (§9), plus, only once needed, the additive `AssignmentCompleted`/`TripCompleted` executing-driver field §9 flags.
7. **Frontend** — the UX states §13 names, on the original driver's, the substitute's, and the passenger's own screens.
8. **Concurrency tests** — real-PostgreSQL races matching §8's own ten named scenarios, in the same style already proven for D-01 (`CommitmentTerminationPostgreSQLTest.kt`).
9. **Integration tests** — the four lifecycle diagrams in §3, end to end, including the withdrawal path.
10. **Full regression** — dispatch, order-management, driver-management, and frontend suites, the same discipline already applied for D-01/D-06, to confirm Handoff's additive changes disturb nothing already shipped.

**What decisions remain outside D-07**, restated once more for this section's own completeness: D-08(a)'s exact observability mechanism and cap threshold; whether facilitation ever becomes a counted metric; Settlement's own "what constitutes settled" question; Fallback Tier-2/Network's own reserved question. None of these gate the sequence above.

**No implementation code is written by this document.**

---

## Evidence Index

Documents and files read or directly cited to produce this specification (none modified):

- `docs/PIOS_D07_HANDOFF_RECONCILIATION.md` (original reconciliation body and its "Product Owner Decision — D-07 (2026-09-18)" addendum).
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

*(Status as of the specification's own writing, 2026-09-18. Superseded by §18 below, same date: implementation has since been carried out against this specification. Nothing in §§1-17 above is edited or reinterpreted by that section.)*

---

## 18. Implementation Record (2026-09-18)

Recorded after the fact, against the already-locked specification above. Nothing in §§1-17 is changed by this section; where this record names a concrete identifier, it is reporting what was built, not a new decision.

**Domain** (`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/`):
- `Handoff.kt` — aggregate, states `PROPOSED → SUBSTITUTE_ACCEPTED → COMMITTED`, with `REFUSED`/`WITHDRAWN` as the two non-terminal-positive exits. (Named `COMMITTED` rather than this document's own working name `CONSENTED` — a naming choice only, not a state-model change; §5's transition table is otherwise implemented exactly as specified.)
- `HandoffId.kt`, `HandoffStatus.kt` — supporting value types.
- `HandoffCreated`, `HandoffSubstituteAccepted`, `HandoffCommitted`, `HandoffRefused`, `HandoffWithdrawn` — the five domain events named in §9, one file each, internal-only (not wired to the outbox), mirroring `Proposal`'s own precedent.
- `Trip.kt` — added `executingDriver: DriverReference` (default `= driver`) and `assignExecutingDriver(driver)`, guarded to `CREATED`/`ARRIVED` exactly per §6/§8. `arrive()`/`start()`/`complete()` now report `executingDriver` (not `driver`) as the returned event's `driverId`, which is how `AssignmentCompleted` attribution (§10) reaches the executing driver with zero shape or `eventVersion` change.

**Persistence:**
- `V22__handoff.sql` — additive migration: `trips.executing_driver_reference` (nullable, no backfill, falls back to `driver_reference` at read time — the same no-backfill precedent §6 and D-06's `agreedAmount` both already use); `handoffs` table; partial unique index enforcing "at most one active Handoff per Assignment" (`WHERE status IN ('PROPOSED','SUBSTITUTE_ACCEPTED')`), the persistent half of the two-layer invariant enforcement §8 requires.
- `PostgreSQLHandoffRepository.kt`, `InMemoryHandoffRepository.kt`.

**Application layer** (`backend/dispatch/src/main/kotlin/com/pios/dispatch/application/`):
- `HandoffApplicationService.kt` — the five operations from §7 (`propose`, `acceptSubstitute`, `consent`, `refuse`, `withdraw`), each: load → `OrderGuard.lock` → re-read under lock → validate → mutate → save, reusing `OrderGuard` unmodified per §8. Actor identity is never accepted as a command field (`HandoffCommands.kt`); it is established solely by the controller layer below, per §11.

**API** (`/v1/handoffs`, `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/HandoffController.kt`):
- `POST /v1/handoffs` (propose), `POST /v1/handoffs/{id}/accept-substitute`, `POST /v1/handoffs/{id}/decline-substitute` (an addition beyond §7's five named operations — a substitute needs a way to decline *before* accepting, reusing the existing `refuse()` domain transition rather than a new state), `POST /v1/handoffs/{id}/consent`, `POST /v1/handoffs/{id}/refuse`, `POST /v1/handoffs/{id}/withdraw`, `GET /v1/handoffs?assignmentId=|substituteDriverId=`. Authorization follows the existing controller-authorizes/service-trusts-the-caller split and the existing 401→404→403 error-mapping convention (§11); no new API style introduced.
- `AssignmentController.kt`/`AssignmentTerminationController.kt`/`AssignmentResponse.kt` — ride-progress and termination authorization now compares against `trip.executingDriver` (falling back to `assignment.driver` pre-Handoff), so a committed substitute can operate and the original driver's own authorization is unaffected when no Handoff has occurred.

**Tests:**
- `domain/TripTest.kt` (appended), `domain/HandoffTest.kt` (new) — pure unit coverage of every transition in §5.
- `application/HandoffApplicationServiceTest.kt` (new) — in-memory coverage of §7/§11's eligibility, denormalization, and state-guard rules.
- `api/HandoffControllerTest.kt` (new) — the authorization matrix §11 requires: wrong original driver, wrong substitute, wrong passenger, cross-order access, guest-session consent, forged/chained substitute attempt.
- `persistence/HandoffConcurrencyPostgreSQLTest.kt` (new) — real PostgreSQL, real threads, 9 tests covering the concurrent races named in §8 (repeated runs where practical), following `CommitmentTerminationPostgreSQLTest.kt`'s own established pattern.
- Frontend: `DriverHome.test.tsx`, `RideRequest.test.tsx` (both extended) — original driver, substitute, and passenger-side UI states from §13.

**ADR-054 (§17):** the narrow amendment §17 said would be required has been made — a second, dated (2026-09-18), in-place supersession pointer in Part 5, immediately below the existing 2026-09-15/`ADR-068` one, plus a matching sentence in "What This ADR Does Not Authorize" and a parenthetical note on Evolution Path item 1. No historical ADR-054 text was edited or removed.

**Regression:** `:dispatch:test` full suite green apart from two pre-existing, environment-dependent RabbitMQ outbox-relay test failures unrelated to this change (confirmed via `git diff` showing no modification to those files); `driver-management`/`order-management` suites unaffected (no files in those modules changed); frontend `vitest`/`lint`/`build` all green.

**Outside this implementation's scope**, unchanged from §16: D-08's own cap mechanism/threshold, whether facilitation becomes a counted metric, Settlement's OQ-8, Fallback Tier-2's OQ-10.
