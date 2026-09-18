# D-08 Final Decision Lock

**Purpose of this document**: to close as many of D-08's remaining open questions as the existing architecture genuinely supports, and to isolate what remains — honestly, not by convenience — as requiring the Product Owner's own judgment. This is not an implementation spec and does not build, fix, or ratify anything by itself. It builds directly on `docs/PIOS_D08_DECISION_ANALYSIS.md` and does not reopen PO-01–PO-04 as already answered there.

---

## 1. Decisions Already Inherited from D-01…D-07

Restated, not reopened:

- **D-08 = Observation Before Cap** (`docs/PIOS_D08_DECISION_ANALYSIS.md` §2). D-07's own Invariant #26/#31 already locks this as a conditional gate, not a permanent ban on ever having a cap. No cap ships as part of D-08's own observation phase.
- **The cap's future subject is the committing driver's own behavior**, not a driver's general quality, reliability, or rating. Nothing about *how well* a driver drives, is reviewed, or is trusted enters this analysis anywhere.
- **Observation is owner/platform-operator-facing.** Never passenger-facing, never peer/cross-driver-facing, never a public or semi-public figure of any kind.
- **Observation is built from real Handoff facts already recorded in `handoffs` (V22)** — proposal, substitute acceptance, passenger consent, refusal, withdrawal, their timestamps and actors — never from a subjective judgment, a review, or an inferred "quality" signal. No such signal exists anywhere in this codebase and none is introduced here.
- **`completedRidesCount`/`totalStatedEarnings` crediting the executing driver is correct, already locked, and not touched by anything in this document.**
- **D-08 must not become a score, tier, rank, quality index, Fair Score, or trust score**, and must not feed Dispatch's own Assignment Policy (`ADR-034` Part 2's forbidden-inputs list) or any passenger-facing surface (`ADR-034`, `ADR-081`, `DriverTrustIndicator.tsx`'s own design boundary).

---

## 2. D-08 Metric Definition

**Semantic definition (no numbers):**

> The observed behavior is the rate and pattern by which a **committing driver** (`Assignment.driver` / `Trip.driver` — the driver who originally took the commitment) transfers **execution** of their own committed rides to other drivers via Handoff, over an observation period — specifically:
>
> (a) what share of that driver's own committed rides were transferred via a Handoff the named substitute actually accepted, rather than personally executed by the committing driver; and
>
> (b) how concentrated the recipients of those transfers are — a small, recurring set of substitute drivers versus a broad, non-repeating set.

This restates the task's own illustrative phrasing precisely: *"доля/частота committed rides, для которых committing driver инициировал Handoff, достигший как минимум SUBSTITUTE_ACCEPTED, включая меру повторяемости конкретных substitute-получателей."*

**What counts toward this metric — derived, not chosen for convenience:**

The single governing rule is: **a Handoff counts if and only if `substitute_accepted_at IS NOT NULL`** — i.e., the Handoff reached `SUBSTITUTE_ACCEPTED` or `COMMITTED` at any point in its life, regardless of how it was later resolved. This is derivable directly from the existing `handoffs` (V22) schema with no new column:

| Event | Counts? | Why |
|---|---|---|
| **Proposed**, never accepted by the substitute (`PROPOSED → WITHDRAWN`, or `PROPOSED → REFUSED` via substitute decline) | **No** | No engagement from a second party ever occurred; the named risk pattern (§5) is defined by a driver *actually routing work to other people*, not merely attempting to. A bare, unaccepted proposal proves nothing about a pattern. |
| **Substitute accepted** (`SUBSTITUTE_ACCEPTED`), regardless of what happens next | **Yes** | This is the first point of real, mutual engagement — the substitute has affirmatively agreed to take the work. This is the fact the "informal dispatcher... routing rides to several other people" language (§5) actually describes, independent of whether the passenger later agrees. |
| **Passenger consented / committed** (`COMMITTED`) | **Yes** | The realized transfer — a strict superset of the row above (every `COMMITTED` Handoff necessarily passed through `SUBSTITUTE_ACCEPTED` first, per `Handoff.commit()`'s own precondition). |
| **Refused by the passenger after substitute acceptance** (`SUBSTITUTE_ACCEPTED → REFUSED` via passenger refusal) | **Yes** | The driver→substitute routing and the substitute's own buy-in already happened; the passenger's later "no" does not undo that fact. |
| **Withdrawn by the original driver after substitute acceptance** | **Yes**, by the same rule | Substitute engagement already occurred before the withdrawal. |
| **Refused by the substitute before ever accepting** (`PROPOSED → REFUSED` via decline-substitute) | **No** | Same as row 1 — no engagement occurred. |
| **A commitment (Trip/Assignment) is separately terminated (D-01)** | **Not applicable to this metric** | Termination is a different aggregate's lifecycle event. It neither erases nor adds to whether a given Handoff reached `substitute_accepted_at` — the Handoff's own record is unaffected either way. |

**Recipient concentration** (dimension (b) above) is computed over the same counted set, grouped by `substitute_driver_reference` per committing driver — its exact formula (e.g., share going to the single most-frequent substitute, or count of distinct substitutes) is a computation detail, not a semantic question, and is not fixed here.

---

## 3. Observation Window

**Bucket/granularity — derivable, proposed with evidence, not asserted as already ratified:**

**Proposed value (as originally written here): 1 ISO calendar week**, mirroring `driver_milestones.current_streak_weeks`'s own existing `RideStreakCalculator` boundary convention (Monday 00:00 UTC).

**Implementation correction (2026-09-18).** The calendar-alignment half of this proposal — `windowEnd` fixed to the start of the current ISO week — was implemented and then tested against real PostgreSQL data (`HandoffObservationPostgreSQLTest`, part of the actual D-08 observation implementation). Testing found it hid exactly the activity an owner-facing observation tool exists to surface: any Assignment created since the most recent Monday would be invisible until the current week fully elapsed, for no benefit any document on record required. **What was actually implemented, and what this section's own recommendation is now corrected to, is a plain trailing window**: `windowEnd` = the query's own current instant (not calendar-aligned), `windowStart` = `windowEnd` minus `windowWeeks × 7` days — i.e. `[now − N weeks, now)`. "Week" survives only as the *unit of measure* the configuration value is expressed in (§ below), not as a Monday-alignment rule. This correction does not reopen anything else in this section — the evidence for "week" as the unit, below, is unaffected by which half of the original proposal (alignment vs. unit) turned out to be correct.

**Evidence for "week" as the unit of measure:**
- `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:75` — OQ-7 Option B's own illustrative text, in the very document that originated D-08: *"A numeric cap (e.g. **N per driver per week**)."* This is the only place in the entire evidentiary record where any time unit is named at all for this concept, and it names "week."
- `RideStreakCalculator` (driver-management) already computes and persists week-boundary *logic* in production, for a conceptually adjacent metric (`current_streak_weeks`) — the original inspiration for calendar alignment specifically; the alignment idea itself did not transfer (above), but "week" as a duration unit still carries this real precedent.

**Consequences of "week" as the unit**: matches the only textual precedent that exists; is coarse enough to smooth day-to-day noise while fine enough to reflect a recent pattern change reasonably quickly, now applied as a trailing duration rather than a calendar bucket.

**Window length (how many weeks the trailing window spans) — cannot be justified as a specific business number, and is not proposed as one.**

This codebase has an existing, directly analogous precedent for exactly this situation: `ProposalLapseApplicationService.timeoutMinutes` is explicitly documented in its own code as *"a configuration parameter (not a ratified business rule — `ADR-051`/`ADR-052`)"* — a real, currently-running production value that was never elevated to a Product-Owner-ratified business decision, precisely because no evidence justified a specific number. The same treatment is proposed here: **window length is a configuration value, not a business rule**, with a non-binding operational starting point (a trailing 4-week view, i.e. 28 days) that carries no claim of correctness and can be changed operationally without a new ADR or PO decision. No PO ratification is required for this value under this framing — see §9 for why it is not listed as a remaining decision.

---

## 4. Sufficient Observation

**What architecture already determines (a process, not a number):**

This project has an existing, already-used mechanism for exactly this class of question — "how do we know we have enough real signal to act" — and it does not depend on a pre-agreed number of events or days. `ADR-068`'s own Status section, Q5, records the precedent directly: *"No `E-NNN` evidence exists. Per `PIOS_PRODUCT_EVIDENCE.md`'s second admissible Sprint basis... a hypothesis has been registered **before** this Sprint... This Sprint **produces** evidence; it does not yet rest on any."* The same structure applies here: the observation mechanism ships; a hypothesis about what a concerning pattern looks like is registered in `docs/PIOS_PRODUCT_HYPOTHESES.md` (the same document ADR-068's own H8 already used); real production Handoff data accumulates; and a future cap-adoption decision becomes *eligible for consideration* only once that evidence log contains at least one real, citable `E-NNN` entry describing an actually-observed pattern — not a guessed volume or duration.

**What genuinely requires a PO decision, and what does not:**

This closes the *process* question (no PO input is needed to define *how* sufficiency will eventually be judged — this project already has a working, precedented mechanism for it, and shipping observation is itself the thing that produces the evidence). What remains genuinely optional, not required, is **whether the Product Owner additionally wants a hard numeric floor** (e.g., "no cap consideration before N weeks of platform-wide data regardless of what the evidence log says") layered on top of the qualitative evidence-gate, as an extra safeguard against premature action. Nothing in the architecture answers this either way, and nothing about shipping observation-only D-08 requires answering it now — a numeric floor, if ever wanted, only becomes relevant once an actual cap proposal is made, which is explicitly not part of this phase.

**What data would let a future numeric threshold be justified empirically:** exactly the data the observation mechanism itself will produce — real `handoffs` rows, keyed by committing driver, over real calendar weeks, once the owner-facing view exists. Observation is the generative process for that future evidence, not a delay imposed on top of an already-known answer.

---

## 5. Substitute Concentration

**Re-examined from first principles, per instruction, rather than left open as in the prior analysis.**

**The precise system problem D-08 exists to prevent**, restated from its own most specific source text (`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:74`, OQ-7 Option A's risk note): *"one 'driver' account is functionally operating as an informal dispatcher routing rides to several other people... exactly the crew/fleet shape `ADR-054` was written to prevent."* This describes **one nominal driver identity acting as a non-executing intermediary** — the ADR-054 "Team/Fleet/crew/delegation" ban given operational shape. It is not framed, anywhere in the record, as a description of a *popular or frequently-chosen substitute* — that is a structurally different fact (a substitute being widely used could just as easily mean the substitute is simply well-regarded, reliable, and often available, which is not a problem PIOS's architecture treats as needing prevention anywhere).

**Two structurally different things can be called "substitute concentration," and they must not be conflated:**

1. **Recipient concentration *within* one committing driver's own outgoing pattern** — does this specific driver's Handoffs cluster onto a small, recurring set of substitutes, or spread broadly? This is not a new actor to observe — it is a second dimension of the **same, already-in-scope committing-driver metric** (§2(b) above), and is retained.
2. **Aggregate concentration onto one substitute, across many different, otherwise-unrelated committing drivers** — does substitute S receive an unusually large share of *all* platform Handoffs, from many different original drivers? This is a genuinely separate measurement, requiring the substitute (not the committing driver) as the primary observed actor.

**Conclusion: (2) does not follow from existing decisions and is excluded from D-08's scope, not merely deferred.** Two independent reasons:
- The specific, concrete example given for the concern (§ above) describes the pattern unidirectionally, from the committing driver's own side — nothing in `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`, the Decision Brief's D-08 text, or `ADR-054` frames the concern from the receiving substitute's side.
- Pursuing (2) risks manufacturing exactly the thing D-08 is explicitly forbidden from becoming: a substitute who is disproportionately chosen by many unrelated drivers is, on the evidence available, at least as likely to indicate a well-regarded, available, in-demand driver as it is to indicate abuse — monitoring and flagging that pattern would functionally create a comparative signal *between* drivers (who gets chosen more), which is precisely the "ranking, implicitly" risk `docs/PIOS_TAXI_PRODUCT_DECISIONS.md`'s own MATCHING RULES vs. RANKING ALGORITHM distinction warns against, and precisely what `ADR-034`/`ADR-081`/`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` already keep out of this system by design.

**Do not conflate** (per the task's own explicit warning): abuse-of-Handoff (the actual D-08 concern, §2/§5 above, measured on the committing driver) is not the same problem as *general marketplace concentration* (which substitute drivers are busiest — a Fair-Opportunity-adjacent, NETWORK-scope, assignment-time question, governed separately and explicitly excluded from Handoff's own domain, §15 of the prior reconciliation), which is not the same as *trust/quality* (no such concept exists anywhere in this codebase and none is introduced here), which is not the same as *dispatch fairness* (`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`'s own, separate, assignment-time-only domain).

---

## 6. `repeatClientsCount` / `driver_client_rides` Drift — Forensic Analysis

For each of the five ADR-081-governed metrics: (1) the fact, (2) current attribution, (3) case for committing driver, (4) case for executing driver, (5) how D-07 changed it, (6) OQ-3's literal text, (7) D-07 spec's literal text, (8) ADR-081's position, (9) what the code actually does.

### `completedRidesCount`
1. Fact: an `AssignmentCompleted` event fired for a Trip.
2. Attribution: `AssignmentCompleted.driverId`.
3. Case for committer: "this is my business's fulfilled-commitment count," a commitment-fulfillment reading.
4. Case for executor: "оплата следует за трудом" — a labor fact, paired 1:1 with earnings in the same pipeline.
5. D-07 change: `driverId` now = `Trip.executingDriver`, not `Trip.driver`, once a Handoff has committed.
6. OQ-3 (`:37`): "ride-count/earnings credit the executing driver" (Option C).
7. D-07 spec (§10, Invariant #23 / PO decision item 34): "the executing/substitute driver receives execution attribution and stated-earnings attribution... fully matching D-08(b)'s own recommended split."
8. ADR-081: private/self-reported, silent on *which* driver — predates the Handoff attribution question entirely.
9. Code: `AssignmentCompletedApplicationService.handle()` credits `command.driverId` directly. **Matches (6)/(7) exactly. No drift.**

### `totalStatedEarnings`
Same structure and conclusion as `completedRidesCount` — explicitly a financial/labor fact per OQ-3 and D-07 spec §10, code matches exactly. **No drift.**

### `invitedDriversCount`
1. Fact: driver X was driver Y's referring driver at Y's registration (ADR-073).
2. Attribution: `drivers.invited_by_driver_id`, set once, permanently, at creation.
3/4. The committing/executing-driver distinction does not apply — this fact has no relationship to any ride, Trip, or Handoff at all.
5. D-07 change: **none.** `invitedDriversCount` is computed via `PostgreSQLDriverRepository.countInvitedBy`, which reads only `drivers.invited_by_driver_id` — it is never touched by `AssignmentCompletedApplicationService` or any Handoff-adjacent code path.
6/7. Neither OQ-3 nor the D-07 spec's attribution sections mention this metric — it was never part of the split's subject matter.
8. ADR-081: names it as one of the five governed metrics, on the same private/self-reported footing, with its own separate open question (`isTest` exclusion) — unrelated to Handoff.
9. Code: no causal path from Handoff exists. **Not applicable. No drift.**

### `driver_client_rides` (`ride_count`, `last_ride_at`) and `repeatClientsCount`
1. Fact: a specific (driver, passenger) pair has shared N completed rides, most recently at time T.
2. Attribution: currently credited via the same `AssignmentCompleted.driverId` used for ride-count, correlated against the order's passenger.
3. **Case for committer — the strongest textual case of any metric here.** `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:39`, OQ-3 Option C, verbatim: *"relationship-facing facts (**repeat-client flag**, 'Мой бизнес' stats) credit the **committing** driver."* This names the exact metric by name.
4. Case for executor: "repeat client" could instead mean "have I personally, physically served this passenger before" — an execution-tied, first-hand-experience reading, under which a committing driver who never personally executes never actually builds this familiarity themselves.
5. **D-07 change, and the actual mechanism of the drift**: before D-07, `driverId` always equaled the sole committing driver, so no attribution question existed. After D-07, `driverId` = `executingDriver`. Critically — `AssignmentCompleted`'s own payload was deliberately kept to **one** `driverId` field, unchanged in shape (D-07's own §9 decision, "additive-field-only, no `AssignmentCompleted` shape change... resolved the earlier spec draft's own 'additive new field' recommendation in favor of the simpler, explicitly instructed approach: reuse the existing `driverId` field"). This means `driver-management` has **no way to learn the committing driver from this event at all** — it only ever sees the executor.
6. OQ-3's own text (`:39`) had already foreseen exactly this problem, before D-07 was built: *"[Option C] requires the most implementation work: `AssignmentCompleted` needs an additive `executingDriverId` field... and two different attribution rules replace today's single one."* **D-07 did not add that field** — it chose the simpler, single-field-reuse path instead, which correctly serves ride-count/earnings but structurally forecloses the committer-side half of Option C for any metric that would need it.
7. `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` (§10) states: *"The original driver retains the historical/origination fact regardless (Invariant #22...) — matching D-08(b)'s own 'relationship-facing facts credit the committing driver' half... fully matching D-08(b)'s own recommended split, on both halves."* This claim conflates two different things: Invariant #22 is true and satisfied — but it describes the **Handoff aggregate's own permanent `original_driver_reference` field** (queryable via `GET /v1/handoffs`), not `driver_management`'s `repeatClientsCount`/`driver_client_rides`. The spec's own summary sentence treats "the origination fact is preserved somewhere" as equivalent to "the repeat-client *metric* credits the committer," which — per (9) below — it does not.
8. ADR-081: governs this metric under the same private/self-reported rule as the other four; ratified one day before D-07 and silent on the Handoff attribution question (it could not have addressed a question that did not yet exist).
9. Code: `AssignmentCompletedApplicationService.handle()` computes `driver_client_rides`/`repeatClientsCount` from the identical `command.driverId` used for `completedRidesCount` — the same single field, the same pipeline. It never reads `Assignment.driver`/`Trip.driver`. **This is a real, confirmed drift**: the shipped behavior contradicts OQ-3 Option C's own literal, metric-named text, and the D-07 spec's own claim that both halves of D-08(b) were "fully matched" is not accurate for this specific metric.

**Conclusion — is this a D-08 decision, or a separate matter to relocate?**

**This is not a D-08 decision. It is a post-D-07 remediation question, and it is moved out of D-08's scope here, per instruction.** D-08 is a forward-looking initiative defining a *new* observation mechanism for Handoff frequency. This drift concerns whether an *already-shipped* D-07 feature (the D-08(b) attribution split, which D-08(a) — this initiative — is not the same half of) was correctly built against an *already-locked* decision. Resolving it requires choosing between two paths that have nothing to do with observation windows, caps, or frequency: either (a) add the additive `executingDriverId`-style field OQ-3 itself already anticipated, so `driver-management` can finally distinguish committer from executor and route the repeat-client credit to the committer as OQ-3 literally specifies, or (b) treat the shipped executor-credit behavior as the accepted outcome and formally amend OQ-3/the D-07 spec's own summary to match reality rather than contradict it. Both are legitimate resolutions; neither is decided here, and neither belongs inside a D-08 ADR. **This should be logged and carried as its own, separate, small decision item — a D-07 attribution remediation — not bundled into D-08's own decision lock.**

---

## 7. Cap Enforcement Point

Re-examined against all four candidates the task named, not merely reconfirmed by assertion:

1. **Forbid Handoff creation (`propose`).** The fact being evaluated (§2 — the committing driver's own historical rate and recipient-concentration, all fully known *before* any given Handoff is proposed) requires no information from any later step. Rejecting here costs nothing informationally and mirrors the existing precedent already in `HandoffApplicationService.propose()` (the substitute-eligibility check already happens at this exact point). No party but the original driver is ever engaged if rejection happens here.
2. **Allow creation, but forbid substitute acceptance.** Strictly worse than (1): the substitute has already read and considered the request before being told it cannot proceed, for a reason (the committing driver's own history) that has nothing to do with the substitute's own participation and was already fully knowable at (1).
3. **Allow acceptance, but stop passenger consent.** Worse still: the passenger has already been shown "your ride will be driven by X" (`RideRequest.tsx`, "a blocking choice, not a toast") and built a real expectation, then told no for a reason entirely outside their own choice or the substitute's.
4. **Warning only, no block.** A materially different *mode*, not a *point* — a warning could in principle be shown at (1) without preventing the Handoff. This is orthogonal to the point-in-lifecycle question: whichever enforcement point is eventually used (if any), a warning-only variant of it is still evaluated, and still best evaluated, at `propose`, for the identical reasons above.

**Conclusion, now more firmly derivable than in the prior analysis**: because §2 has now fixed the metric as depending *only* on the committing driver's own already-existing history (never on anything the substitute or passenger do), there is no scenario under which delaying the check past `propose` gains any information. **`propose` is not merely the least-harmful option — it is the only point at which the fact being evaluated is even meaningfully different from "already decided."** This is derivable from existing architecture and does not require a PO decision — with the standing caveat that this question is moot for the observation-only phase itself, since no enforcement of any kind ships as part of D-08 today (§1).

---

## 8. What D-08 Does NOT Do

- **Does not create any score, tier, rank, quality index, Fair Score, or trust score** — confirmed nowhere in scope by any of §§1–7 above.
- **Does not enforce anything today.** Observation-before-cap (§1) means this initiative ships observation only; no `propose`/`accept-substitute`/`consent` call is ever rejected by D-08 in this phase.
- **Does not monitor aggregate concentration onto a single substitute across unrelated committing drivers** (§5, excluded, not deferred).
- **Does not resolve the `repeatClientsCount`/`driver_client_rides` attribution drift** (§6) — relocated to its own, separate remediation item, outside D-08.
- **Does not address metric fabrication or synthetic-traffic fraud** — `ADR-081`'s own explicitly separate, still-open question, untouched here.
- **Does not touch assignment-time dispatch selection or Fair Opportunity Policy's own domain** — a structurally different, NETWORK-scope, pre-commitment decision point.
- **Does not become passenger-facing or peer/cross-driver-facing in any form** (§1/§ PO-04, inherited).
- **Does not change `Trip.agreedAmount`, `Connection`, `Assignment.driver`, or any D-07 invariant** — nothing here reopens D-07.

---

## 9. Final PO Decisions Required

Only what genuinely cannot be determined from existing architecture. Every other question this task and the prior analysis raised has been closed above by direct evidence or precedent-consistent derivation, and is not repeated here.

**D8-PO-1 — Private, driver-facing self-view.**
*Exact question*: alongside the owner-facing observation view (§1, already settled), should the observed driver also see a private, self-only view of their own Handoff activity (mirroring how `completedRidesCount` is already shown to the driver who owns it)?
*Proposed answer*: none proposed — genuinely undetermined either way.
*Evidence*: the source text says "owner-facing," not "driver-facing" (`PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766`); nothing rules a self-view in or out, since a driver seeing only their own number creates no cross-driver comparison and no ranking risk.
*Consequence*: purely additive UX scope; does not affect the metric definition, window, or any enforcement question. Can be added later without revisiting anything above.
*What remains open*: whether to build it now, later, or not at all.

**D8-PO-2 — Hard numeric floor on top of the evidence-gate process (optional).**
*Exact question*: beyond the qualitative evidence-log/hypothesis-registration process already established as sufficient (§4), does the Product Owner want an additional hard numeric or time floor (e.g., a minimum number of weeks or events) before any future cap proposal may even be considered?
*Proposed answer*: none proposed — this project's own existing evidence-gate precedent (`ADR-068` Q5) is sufficient on its own to begin shipping observation; a numeric floor is an optional reinforcement, not a requirement.
*Evidence*: §4 above.
*Consequence*: does not block or change anything about shipping observation-only D-08 either way; only becomes relevant once an actual future cap proposal is made.
*What remains open*: whether the PO wants this extra safeguard, and if so, its number — genuinely not derivable from any existing document.

**No other blocking decision remains to scope a first, observation-only D-08 ADR.** The `repeatClientsCount` drift (§6) requires its own, separate resolution but is not a D-08 blocker, since D-08's own metric (§2) does not use `repeatClientsCount` at all — it is built entirely from the `handoffs` table directly.

---

## Verification

- No production code, schema, migration, ADR, or test was modified. Only `docs/PIOS_D08_FINAL_DECISION_LOCK.md` was created.
- No commit was made. No push was made.
- HEAD and git status are reported in the accompanying message.
