# D-08 Decision Analysis

**This is not an implementation spec, not an ADR, and not a new Product Owner decision.** It is a Senior-Product-Architect-level derivation: for each of the four questions the Product Owner declined to answer directly, it determines what already follows, by necessity or by strong textual evidence, from D-01–D-07, the ratified ADRs, and the Product Owner Decision Brief — and separately, honestly, names what genuinely cannot be derived without new input. Nothing here is invented business logic; every conclusion is traced to a specific existing source. Where the evidence is genuinely silent or split, this document says so instead of picking a side.

---

## 1. Executive Conclusion

**What already follows from existing decisions, with no new PO input required:**

- **PO-01 is already answered by D-07 itself.** `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md`'s own Invariant #26/#31 — already ratified, already implemented, already locked — is phrased as a conditional gate ("no cap... before sufficient observable behavior exists to base one on"), not as a permanent prohibition on ever having a cap. That phrasing structurally presupposes a future cap as the thing being gated. Re-opening this into "observation only, no cap ever" would mean rewriting an already-locked D-07 invariant, which this analysis is instructed not to do. **Answer: B — Observation Before Cap**, not newly decided here but recovered from what D-07 already locked.
- **PO-02's primary subject is derivable from the architecture's own words, not merely convenient.** The specific risk named everywhere D-08 is discussed (`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option A: *"one 'driver' account is functionally operating as an informal dispatcher routing rides to several other people"*) describes one committing driver's own outgoing pattern, not incoming concentration onto a substitute. The cap subject, if one is ever built, is the **committing driver's own rate and breadth of Handoff use**, not a raw count and not (primarily) the substitute side.
- **PO-03 surfaces one confirmed non-drift and one confirmed drift.** `completedRidesCount`/`totalStatedEarnings` crediting the executing driver is *exactly* what D-08(b) already specified and D-07 already implemented — not drift. `repeatClientsCount`/`driver_client_rides` crediting the executing driver **contradicts** the explicit, named text of OQ-3 Option C ("repeat-client flag... credit the committing driver"), which D-07's own spec cites as the locked basis for D-08(b). This is a real, unresolved conflict between a specific written decision and shipped code — recorded as DRIFT, not resolved here.
- **PO-04's primary answer is textually explicit.** The source text says "owner-facing view," and an owner-facing surface (`OwnerControlCenter`) already exists in this codebase. Passenger-facing and cross-driver/peer-facing visibility are both excluded by multiple, independent, already-ratified rules (ADR-034, ADR-081, `DriverTrustIndicator.tsx`'s own explicit design boundary). Whether a *private, self-only* driver view is added alongside the owner view is not textually required and is left open, not forced.

**What cannot be closed without new PO input, honestly:**
- The exact size and shape (rolling vs. calendar) of any observation window.
- The exact volume/duration that constitutes "sufficient observable behavior."
- Whether concentration *onto* a single substitute (as distinct from one driver's own outgoing pattern) is also in scope, as a secondary signal.
- The repeatClientsCount drift itself — which side is correct (the written OQ-3 text, or the shipped code) is a real product decision, not something this analysis may pick on the Product Owner's behalf.
- Whether a private driver-facing self-view is added alongside the owner-facing one.

---

## 2. PO-01 — Is a Cap Needed at All?

**Decision (derived, not newly made): B — Observation Before Cap.**

**Evidence:**

- `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` — Invariant #26 (already ratified as part of D-07, already implemented as the enforced absence of any cap today): *"No Handoff frequency/concentration cap blocks a Handoff before sufficient observable behavior exists to base one on."* The clause "before... exists to base one on" is a conditional, not a prohibition — it describes what must happen *before* a cap is legitimate, which only makes sense if a cap is the anticipated eventual state once that condition is met. If the intent were "no cap, ever, full stop," this invariant would have been written as an unconditional ban, the way Invariant-style text elsewhere in the same spec bans other things outright (e.g., "no fee, split, financial credit, or commission").
- Same document, §16: *"D-08(a)'s own remaining mechanism — the exact observability tooling, and **any eventual cap threshold/window, once real usage data exists**."* This phrase — "any eventual cap" — is itself part of the already-ratified specification, not part of the still-open question.
- The current task's own framing (this conversation and the prior one) restates this as a "LOCK": *"A Handoff frequency/concentration cap MUST NOT block a driver before there is sufficient observable behavior."* This is consistent with B, not A.

**Alternative reading (A — Observation Only), and why it is not adopted here:**

The *original* recommendation text, `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766`, reads more skeptically of a cap ever existing at all: *"a cap would be an invented number, and monitoring is the honest alternative."* Read in isolation, this sentence leans toward A — pure, permanent observability, with no anticipated cap. This is genuine tension in the record, not manufactured for this analysis.

**Why B controls anyway:** the brief is a pre-decision recommendation document (the Product Owner had not yet answered it when it was written); the D-07 implementation spec is the *later*, ratified, already-implemented artifact that actually resolved how the principle would be encoded — and it encoded it as a conditional gate, not a ban. This mirrors how this same codebase has repeatedly treated brief-stage language as superseded once a specific ADR/spec ratifies a more precise wording (e.g., ADR-068's Q1 was "offered for confirmation" at brief stage and then became a specific, binding algorithm once ratified). Reopening Invariant #26's own conditional structure now, in favor of the earlier and vaguer brief language, would be rewriting an already-locked D-07 decision — which this analysis was explicitly told not to do.

**What B does *not* yet decide:** B only establishes that *if* a cap is ever adopted, observation must precede and justify it. It does not itself commit PIOS to eventually adopting a cap — "observe indefinitely, never actually cap" remains a legitimate terminus *within* B, if the observed data never justifies one. B is a sequencing rule, not a promise of eventual enforcement.

**Related ADRs checked:** ADR-054 (no ranking/scoring — consistent with B, since B does not require any score); ADR-068/069 (established precedent that a *binary filter* is acceptable while a *score* is not — directly informs what a future cap, if any, may look like, see §3); ADR-081 (governs the metrics a future cap might read, see §12); Discovery/Trust Graph/Relationship Graph — none of these model any cap or window concept today (confirmed in the prior reconciliation, `docs/PIOS_D08_HANDOFF_CAP_RECONCILIATION.md` §5/§14), so none of them independently push toward A or B.

---

## 3. PO-02 — What Should a Cap (If Any) Limit?

**Decision (derived): the primary subject is the *committing (original) driver's* own rate and breadth of routing work to others — not a raw count, and not primarily the substitute side.**

**Evidence:**

- `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7, Option A's own risk note — the single most specific, concrete description of the failure mode anywhere in the source material: *"an uncapped pattern where one 'driver' account is functionally operating as an informal dispatcher routing rides to several other people is exactly the crew/fleet shape `ADR-054` was written to prevent."* Read precisely: this describes **one** driver account, **routing** (present continuous, repeated action), **to several other people** (plural, distinct recipients). That is a description of one committing driver's own outgoing behavior — not "many drivers converging their rides onto one popular substitute."
- `PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766` reinforces this with "per driver" (singular possessive), and the surrounding sentence: *"the informal-fleet pattern is statistical and invisible per transaction"* — a fleet-front pattern is defined by one nominal driver identity that does not itself perform the labor, which is a property of the *committing* side, not the executing side.
- This also aligns with the platform-mission text quoted in this task: *"Оплата следует за трудом"* (payment follows labor) already resolves *who gets credited* (§4/PO-03) in favor of the executor — the remaining, distinct concern D-08 exists to catch is a *committing* driver who is not actually laboring, i.e. functioning as a dispatcher rather than a driver. That is a fact about the committing driver's pattern, not the executing driver's.

**Rate vs. raw count vs. breadth:** the OQ-7 quote's "several other people" implies *breadth* (distinct recipients) matters, not just volume. A driver who hands off occasional rides to the *same one* trusted colleague (e.g., mutual, reciprocal covering between two independent driver businesses — itself a legitimate, product-aligned form of mutual aid the platform's own philosophy does not forbid) looks structurally different from a driver who routes rides to many different people. A **raw absolute count** also does not distinguish a naturally high-volume driver from a low-volume one; a **rate** (share of the driver's own commitments executed via Handoff) and **breadth** (number of distinct substitutes used) are both more consistent with the "informal dispatcher" description than a bare count.

**Secondary, unconfirmed reading — concentration onto one substitute:** "crew/fleet shape" could, in principle, also describe the mirror pattern: many different committing drivers all routing to the same one executing driver (one person quietly working many other people's nominal shifts). Nothing in the source text explicitly names this shape by example the way it names the outgoing-dispatcher shape. This analysis does **not** rule it out — it is a plausible extension of the same underlying concern (ADR-054's "no crew/fleet" ban is symmetric, in principle, to both a dispatching front and an aggregating pool) — but it cannot be asserted as the *primary* subject on the evidence available, and is recorded in §13 as a remaining question rather than resolved here.

**What D-08 should prevent:** a committing driver's account behaving, in sustained pattern, like an informal dispatch/fleet front — routing a disproportionate share of their own committed work to other people, especially to many different people — which is exactly the "crew/fleet" shape `ADR-054` Part 5 already bans in principle and Handoff (D-07) did not itself reintroduce a check for, because D-07's own scope was the *mechanics* of one transfer, not the *pattern* across many transfers over time.

**What D-08 should not try to solve:**
- **Occasional, low-frequency Handoff use for legitimate personal reasons** (illness, a family emergency, brief unavailability) — this is precisely the case Handoff exists to serve (D-07's own stated purpose: preserving an existing commitment, not creating a new business model). A mechanism that penalizes rare, isolated use would contradict D-07's own purpose.
- **Fabricated/synthetic metrics or fraud** — `ADR-081` already explicitly scopes this out as its own separate, still-open question ("The anti-fraud question... is explicitly not part of D-02 and is not resolved here"). D-08 inherits the same non-scope; a Handoff cap is not an anti-fraud mechanism and should not be asked to double as one.
- **Assignment-time fairness/dispatch selection** — `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` governs a structurally different decision point (NETWORK-scope, assignment-time, shared-pool access) and explicitly excludes ranking/scoring from its own scope. D-08 must not become a backdoor way of ranking drivers for future Assignment Policy input — doing so would directly cross `ADR-034` Part 2's forbidden-inputs list ("driver profile, standing, participation, or ranking beyond availability").
- **Execution quality** — no rating/quality concept exists anywhere in this codebase (`DriverTrustIndicator.tsx`'s own explicit design), and D-08 must not become the first one by another name.

---

## 4. PO-03 — Attribution of `repeatClientsCount` and the Other Four Metrics

**Method:** each of the five ADR-081-governed metrics is classified by semantic type (relationship/origination, execution, financial, network), its current data source is stated exactly, the attribution the existing written decisions actually specify is stated separately from the attribution the code actually implements, and any conflict is named as DRIFT rather than resolved.

| Fact | Semantic type | Current source | Attribution per written decision | Attribution as implemented | Drift? |
|---|---|---|---|---|---|
| `completedRidesCount` | Execution fact | `AssignmentCompleted.driverId` (= `Trip.executingDriver` post-D-07) | Executing driver — D-08(b) text: *"ride-count... credit the driver who drove"* | Executing driver | **No.** Matches exactly. |
| `totalStatedEarnings` | Financial/stated-earnings fact | `AssignmentCompleted.driverId` + `statedPrice` | Executing driver — D-08(b) text: *"earnings credit the driver who drove"* | Executing driver | **No.** Matches exactly. |
| `driver_client_rides` (ride_count, last_ride_at) | Relationship-adjacent fact (see analysis below) | `AssignmentCompleted.driverId` + `OrderSubmitted.passengerReference` | **Committing driver** — OQ-3 Option C, verbatim: *"relationship-facing facts (repeat-client flag, 'Мой бизнес' stats) credit the committing driver"* | **Executing driver** (same pipeline as `completedRidesCount`, same `driverId`) | **YES — confirmed drift.** |
| `repeatClientsCount` | Relationship-adjacent fact (derived from `driver_client_rides`) | Same as above | Committing driver, per the same OQ-3 Option C sentence | Executing driver | **YES — same drift, same root cause.** |
| `invitedDriversCount` | Network fact | `drivers.invited_by_driver_id` (ADR-073), set once at driver creation | Independent of Handoff — this fact predates and is structurally unrelated to any ride, Assignment, or Trip | Independent of Handoff (never touched by `AssignmentCompletedApplicationService`) | **No.** Not applicable — this metric was never wired through `executingDriver` and never could be. |

**Why `repeatClientsCount`/`driver_client_rides` is the one genuine conflict, not merely a code bug:**

D-07's own implementation spec (`docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md:356`) states: *"D-08(b)'s split is now fully locked... relationship-facing facts to the committing driver, execution/earnings facts to the executing driver, exactly as recommended."* This sentence asserts the split is fully and correctly implemented. But the actual code path (`AssignmentCompletedApplicationService.handle()`, `driver-management`) computes `driver_client_rides`/`repeatClientsCount` from the *same* `command.driverId` as `completedRidesCount` — there is, today, only one `driverId` per `AssignmentCompleted` event, and it is always the executor's. **No code path exists that routes anything to the *committing* driver specifically** (`Assignment.driver`/`Trip.driver` is not consulted anywhere in `AssignmentCompletedApplicationService`). This means the "fully locked, exactly as recommended" claim in the D-07 spec is itself inaccurate for this one metric — the spec's own summary and the actual behavior disagree, and neither this analysis nor the prior reconciliation invented that disagreement; it was found by reading `AssignmentCompletedApplicationService.kt` directly against OQ-3's own literal text.

**This analysis's own architectural observation (not a resolution):** `driver_client_rides` sits in genuine conceptual tension between the two categories the task asked to distinguish. On one hand, OQ-3 explicitly calls it "relationship-facing." On the other hand, the canonical relationship-ownership record in this codebase is `Connection`/`PrimaryConnection` (passenger-experience, ADR-054) — which Handoff explicitly and permanently never touches (Invariant #4/#7). `driver_client_rides` is a *separate*, informal, self-reported echo of "how many times have I personally met this passenger," and its own honest meaning to a driver reading their own "Мой бизнес" screen is arguably "have I personally, physically served this person before" — which is inherently an execution-shaped question, not a relationship-ownership one (relationship ownership already has its own, untouched, canonical answer in `Connection`). This observation is offered as context for the Product Owner's own resolution, not as a substitute for it — the written decision (OQ-3 Option C) and the shipped code currently disagree, and this document does not pick which one is right.

---

## 5. PO-04 — Who Should See Observation?

**Decision (derived): owner/platform-operator-facing, for the initiative this D-08 analysis concerns. Passenger-facing and cross-driver/peer-facing visibility are excluded, not left open.**

**Evidence:**

- The source text is explicit and singular on this point: `PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1766` — *"No Handoff without an **owner-facing** view of handoff frequency and concentration per driver."* "Owner" in the context of a document literally addressed *"Твой вопрос"* to the Product Owner, about a platform-level monitoring requirement, reads as the platform operator (Ildar), not "the driver as owner of their own small business" — the sentence is about the platform having visibility into a systemic pattern, not about a driver monitoring themselves.
- This codebase already has a concrete, existing home for exactly this kind of surface: `frontend/src/pages/OwnerControlCenter/` — an existing owner/operator-facing page, distinct from any driver- or passenger-facing page. A future D-08 observation view has an obvious, already-established architectural slot, consistent with this reading.
- **Passenger-facing is excluded by multiple independent rules, not one**: `ADR-034` Part 2 forbids "driver profile, standing, participation, or ranking beyond availability" from crossing into Passenger Experience; `frontend/src/components/DriverTrustIndicator/DriverTrustIndicator.tsx` is explicitly designed to render "never a rating, count, rank, or any fabricated trust score" to a passenger (confirmed by direct reading in the prior reconciliation, not assumed from the component's name); `ADR-081` restricts all five existing driver metrics from ever becoming "a passenger-facing verified fact" without a new superseding decision. A Handoff-frequency observation is the same *kind* of fact (a private, derived, driver-specific figure) and would need the same kind of new, explicit, superseding decision before it could ever be shown to a passenger — nothing in D-08's own source material asks for that, so it is excluded, not merely undecided.
- **Cross-driver/peer-facing visibility is excluded on the same grounds** — nothing in this codebase's Trust Graph, Relationship Graph, or Discovery model ever surfaces one driver's activity to another driver. Doing so for Handoff-frequency specifically would create exactly the "hidden rating" risk this task's own instructions warn against: a driver seeing another driver's handoff count is a comparison, and a comparison between drivers is functionally a ranking signal regardless of whether it is called one. `docs/PIOS_TAXI_PRODUCT_DECISIONS.md`'s own explicit MATCHING RULES vs. RANKING ALGORITHM distinction ("the latter is not to be introduced implicitly") applies directly: a network-visible per-driver number, even unscored, would function as an implicit ranking input the moment it becomes comparable across drivers by other drivers.

**What is left open, not forced:** whether the *observed driver themselves* also gets a private, self-only view of their own Handoff activity (mirroring how `completedRidesCount` etc. are already shown to the driver who owns them, self-only, `Bearer`-gated). This does not conflict with anything above — a driver seeing only their own number creates no comparison and no ranking risk — but it is not explicitly requested by the source text either ("owner-facing," not "driver-facing," is the literal wording), so this analysis records it as optional, not required, and defers it to §13.

**Internal/system-only use** (the data being read by automated logic without any human ever seeing it directly) is a baseline that exists regardless of the human-visibility question above — any future cap-enforcement code (§2/§11) would need to read this data mechanically whether or not a human dashboard is ever built. This is a mechanical necessity, not a separate visibility decision.

---

## 6. D-08 Business Boundary

**What D-08 solves:** it gives the platform operator visibility into whether a specific driver's own Handoff usage pattern — rate and breadth, not raw volume — resembles an informal dispatch/fleet-front operation, the specific "crew/fleet backdoor" concern `ADR-054` Part 5's ban was written to prevent and that Handoff (D-07), by design, does not itself check for at the level of a single transfer. If, after sufficient real observation, a cap is later justified, D-08 is the initiative that would gate that cap on real evidence rather than an invented number.

**What D-08 does not solve:**
- Occasional, low-frequency, legitimate Handoff use (illness, emergency, brief unavailability) — the exact case Handoff exists to serve.
- Metric fabrication/synthetic-traffic fraud — explicitly out of scope per `ADR-081`, unchanged here.
- Assignment-time / dispatch-selection fairness — governed separately by `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, a different decision point (NETWORK-scope, pre-commitment).
- Execution quality, service rating, or any notion of a "better" or "worse" driver — no such concept exists anywhere in this codebase, and D-08 must not introduce one under another name.
- Payment, pricing, commission, or subscription tier — `Trip.agreedAmount` and `billing` remain completely outside D-08's reach, exactly as they are outside Handoff's own reach today (D-07 Invariant: no fee/split/credit/commission).

---

## 7. Concurrency

`OrderGuard`/`PostgreSQLOrderGuard` locks a single row keyed strictly by `order_reference` (`SELECT ... FOR UPDATE` on `dispatch_order_guards`). `HandoffApplicationService` uses this same order-scoped lock in every one of its five methods. **It provides no serialization at all between two Handoff operations that share a driver but not an order** — a driver named in two different Handoffs on two different orders at the same moment is not protected by anything today.

**Whether this matters depends directly on the PO-01 answer already derived (§2):** for the **observation-only** phase (the only phase this analysis is actually scoping, per B), an approximate, eventually-consistent count is sufficient — a dashboard reading being off by one during a brief, genuine race is not a correctness defect the way a bypassed hard cap would be. **No new driver-level lock is required to ship observation alone.**

A driver-scoped serialization mechanism (a `DriverGuard`, structurally identical to the existing `PostgreSQLOrderGuard` pattern — `INSERT ... ON CONFLICT DO NOTHING` + `SELECT ... FOR UPDATE`, keyed by `driver_reference` instead of `order_reference`) would become necessary **only if and when an actual enforced cap is adopted** (§11's failure-mode analysis), since an atomic "reject if this driver's count would exceed N" check cannot be correct under concurrency without it. This is named here as a future requirement, not built or specified further — consistent with the instruction not to implement.

---

## 8. Data Model

**Already exists and is sufficient for observation alone:**
- `handoffs` (V22) — a complete, append-only, permanent audit trail of every Handoff attempt (`proposed_at`, `substitute_accepted_at`, `resolved_at`, `status`, `original_driver_reference`, `substitute_driver_reference`). This already *is* the Handoff-history aggregate the task asked whether a separate one is needed for — **no new history aggregate is needed**, since this table already serves that role at the row level.

**Missing for efficient/frequent reads:**
- No index on `original_driver_reference` or `substitute_driver_reference` — any per-driver query today is a full scan. At minimum, an index on `original_driver_reference` would be needed given PO-02's derived primary subject (the committing driver's own pattern).
- No repository method exists for "find Handoffs by original driver" at all (only `findBySubstituteDriver` exists today).
- No aggregate/rollup read-model exists for Handoff activity, unlike `driver_milestones`, which is incrementally maintained on each event. If the owner-facing view (§5) needs to be read often/quickly, a similar incrementally-updated table would be the pattern this codebase already uses elsewhere — new work, not something the existing schema provides.
- No table anywhere computes a "rate" (Handoffs relative to a driver's own total commitments) — this would require a new join against Dispatch's own Assignment/Trip population, which nothing currently computes.

None of the above is implemented by this analysis, per instruction.

---

## 9. Observation Window

**Lifetime is not appropriate for the pattern-detection use case derived in §3.** A lifetime rate dilutes a recent, sustained behavior change into an average that includes a driver's entire history — a driver with 500 legitimate solo rides who then spends one month behaving like a dispatch front would show an unremarkable lifetime rate, defeating the purpose. Lifetime *raw counts* remain useful as supplementary, low-stakes context (exactly as `completedRidesCount` already is), but a lifetime-only figure cannot answer "is this driver's *current* pattern concerning."

**Between rolling and calendar, this codebase has an existing, directly reusable precedent for calendar-based windowing** (`RideStreakCalculator`'s ISO-week boundaries, already driving `driver_milestones.current_streak_weeks`) and **no precedent at all for a durable, multi-instance-safe rolling window** — the only rolling-window code in the entire codebase (`LoginRateLimiter`, `PhoneOtpRequestRateLimiter` in Identity) is in-memory, resets on restart, and exists purely for authentication anti-abuse; it is not a safe precedent for a persistent business metric. A calendar-based window is therefore the structurally consistent choice *if* the Product Owner wants to avoid introducing genuinely new infrastructure; a rolling window would be more resistant to boundary-gaming (concentrating activity right after a reset) but is new engineering, not a reuse of anything proven here.

**What cannot be derived:** the *size* of the window (how many weeks/months). No existing ratified document states or implies a number, and per this project's own explicit discipline (`CLAUDE.md` "Never Invent Business Rules"; the evidence-gating convention used for every prior Sprint in this engagement), inventing one here would be exactly the mistake `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option B's own risk note already names ("the number itself would be an invented business rule with no evidence behind it yet"). **This is recorded as a remaining question (§13), not answered.**

---

## 10. Sufficient Observation

"Sufficient observable behavior" is not defined anywhere in the existing record. This codebase does, however, already have a standing, repeatedly-applied convention for exactly this kind of judgment: `docs/PIOS_PRODUCT_EVIDENCE.md`'s own Sprint-gate rule — no Sprint proceeds without a citable evidentiary basis, though a Sprint *may* begin on a registered hypothesis (the same path `ADR-068`'s own Q5 used: "no E-NNN evidence exists... a hypothesis has been registered before this Sprint. This Sprint produces evidence; it does not yet rest on any."). By direct structural analogy, "sufficient observable behavior" for any eventual D-08 cap would mean: enough real, production Handoff activity — across enough distinct drivers, over enough real time — to distinguish an actual pattern from noise, evaluated the same evidence-gated way every other Sprint-level decision in this engagement already has been.

**What cannot be derived:** the specific volume or duration that would count as "enough." No existing document states a number, and none can be honestly inferred — this genuinely requires either a Product-Owner-set number or the accumulation of real evidence through the project's own existing Sprint-evidence discipline. **Recorded as a remaining question (§13), not answered.**

---

## 11. Failure Mode

Four candidate rejection points, evaluated against consequences (not chosen by convenience):

- **`propose` (earliest).** If a cap is ever enforced, rejecting here means the original driver learns immediately, before the substitute or passenger are ever engaged. This is the only point at which the fact being evaluated (§3 — the *committing* driver's own rate/breadth) is fully knowable, since it depends only on the original driver's own history, not on anything the substitute or passenger do. It also mirrors the existing precedent in `HandoffApplicationService.propose()`, which already performs its own cheapest, earliest-possible validation (the substitute-eligibility check) at exactly this point.
- **`accept-substitute`.** Rejecting here would mean the original driver has already proposed and the substitute has already engaged (read the request, decided to accept) before being told the transfer cannot proceed — strictly worse than `propose`, with no offsetting benefit, since nothing about the substitute's own acceptance adds information relevant to a cap defined on the *original* driver's own pattern (§3).
- **`consent` (passenger).** This is the most damaging point to fail at: the passenger has already been shown "your ride will be driven by X" (`RideRequest.tsx`'s own consent screen, "a blocking choice, not a toast") and has built an expectation, only to have it withdrawn by an internal policy they have no part in and no visibility into. This directly conflicts with the product's own principle that passenger consent is constitutive and meaningful — consent should resolve a valid offer, not one that was already invalid before the passenger ever saw it.
- **execution (Trip start).** Worst of all — an already-consented Handoff that cannot execute would strand the ride entirely, defeating Handoff's own stated purpose (preserving execution of a committed ride), the opposite of what D-07 exists to do.

**Conclusion, derived rather than assumed:** if a cap is ever adopted, `propose` is the only rejection point consistent with (a) the subject being evaluated (§3, known fully at propose time), (b) minimizing harm to the substitute and passenger, and (c) the existing precedent already in the code for where this class of check belongs. This conclusion depends on §3's own actor derivation (committing driver); if a future, separately-confirmed cap subject also included concentration onto a substitute (§3's unconfirmed secondary reading), the earliest point at which *that* fact is knowable is still `propose` (the substitute is named at proposal time), so this conclusion is robust to that open question as well.

---

## 12. Existing Architecture Drift

Two confirmed drifts, both already surfaced in `docs/PIOS_D08_HANDOFF_CAP_RECONCILIATION.md` and restated here with the precision this task asked for:

**Drift 1 — ADR-081 ↔ D-07 ↔ `AssignmentCompleted.driverId`.** `ADR-081` was ratified 2026-09-17, one day before D-07 shipped, on the basis of a reconciliation that explicitly found "zero references to any of the five [metrics]... anywhere in dispatch." That finding was accurate *at the time*. D-07 (2026-09-18) then changed what `AssignmentCompleted.driverId` means — executor, not committer — without any accompanying re-examination of ADR-081 or its own evidentiary basis. No rule of ADR-081 is violated (the metrics remain private, self-reported, unused for money/tier/priority/ranking/trust), but ADR-081's own reconciliation no longer describes current reality, and no document has updated it to reflect that D-07 now silently determines *which* driver these four metrics accrue to.

**Drift 2 — OQ-3 Option C ↔ `driver_client_rides`/`repeatClientsCount` (§4 above, the more serious of the two).** The written, named decision (OQ-3 Option C, cited by D-07's own spec as the locked basis for D-08(b)) explicitly assigns the repeat-client flag to the *committing* driver. The shipped code assigns it to the *executing* driver, via the same single-`driverId` pipeline as ride-count and earnings. D-07's own spec asserts this split is "fully locked... exactly as recommended," which is not accurate for this one metric specifically. This is the more consequential of the two drifts, because it directly contradicts a specific, named, written instruction rather than merely outliving an earlier reconciliation's assumptions.

Neither drift is corrected by this document, per instruction.

---

## 13. Remaining PO Decisions

Only the items this analysis could not close from existing evidence:

1. **Observation window size** — how many weeks (or other unit) the calendar-based window (§9) should span. No number is derivable.
2. **Sufficient-observation threshold** — what volume/duration of real data must accumulate before any cap becomes even eligible for consideration. No number is derivable.
3. **Whether concentration onto a single substitute (incoming) is also in scope**, alongside the primary, textually-supported subject (a committing driver's own outgoing pattern, §3) — a plausible extension, not confirmed by the available text.
4. **Resolution of Drift 2** (`repeatClientsCount` attribution, §4/§12) — whether the code should be changed to match OQ-3 Option C's literal text (credit the committer), or OQ-3 Option C should be explicitly amended to match the shipped behavior (credit the executor). This is a genuine fork between a specific written decision and shipped code; this analysis names it but does not resolve it.
5. **Whether a private, self-only driver-facing view of a driver's own Handoff activity is added** alongside the owner-facing view (§5) — not textually required, not excluded by anything, genuinely optional.
6. **Which events count toward observation** — this analysis's own proposed default (§14, item 3: only Handoffs reaching `SUBSTITUTE_ACCEPTED`/`COMMITTED`, excluding pure `PROPOSED`-then-withdrawn/refused rows) is a reasoned inference from the "routing rides to several other people" language (§3), not a certainty — the Product Owner may confirm or override it.

Everything else this task's original four questions asked about was closed above from existing evidence, without needing new PO input.

---

## 14. Proposed D-08 Decision Lock

A draft of concrete resolutions the Product Owner could ratify in a single next step, assembled strictly from what §§2–12 above actually derived — not a new proposal beyond that derivation:

1. **Scope of this initiative**: observation only. No cap, threshold, or enforcement mechanism is built now. (Confirms the already-locked D-07 Invariant #26/#31 — not a new decision.)
2. **Primary observed subject**: the committing (original) driver's own Handoff usage — rate of own commitments executed via Handoff (relative to the driver's own total commitments) and breadth (count of distinct substitutes used) over the observation window. Concentration onto a single substitute is *not* included as a primary signal pending item 3 of §13.
3. **Countable events**: Handoffs that reach `SUBSTITUTE_ACCEPTED` or `COMMITTED`. Pure `PROPOSED`-then-`WITHDRAWN`/`REFUSED` rows are excluded from the count by default (pending confirmation — §13 item 6).
4. **Attribution — no change to what is already correct**: `completedRidesCount`/`totalStatedEarnings` continue to credit the executing driver (already correct, already locked, no drift).
5. **Attribution — drift requiring explicit resolution**: `driver_client_rides`/`repeatClientsCount` currently credits the executing driver, contradicting OQ-3 Option C's own text. This Decision Lock does **not** resolve it — it is carried forward as Remaining Question §13 item 4 and must be explicitly settled (one way or the other) before or alongside any D-08 ADR that touches attribution.
6. **`invitedDriversCount`**: unaffected by Handoff; no change; not part of D-08's scope.
7. **Visibility**: owner/platform-operator-facing (a natural extension of the existing `OwnerControlCenter` surface). Never passenger-facing. Never cross-driver/peer-facing. A private, self-only driver view is optional and separately decidable (§13 item 5).
8. **Concurrency**: no new driver-level lock is required for this (observation-only) phase. A `DriverGuard`-equivalent is deferred to whatever future work, if any, actually enforces a cap.
9. **Data model**: no new Handoff-history aggregate — `handoffs` (V22) already serves that role. New work limited to indexing (`original_driver_reference`, at minimum) and an incrementally-maintained read-model for fast owner-facing reads, built only if/when the observation view is actually implemented.
10. **Window shape**: calendar-based, reusing the existing week-boundary convention (`RideStreakCalculator`), not lifetime, not rolling — unless the Product Owner prefers rolling despite the new-infrastructure cost. Window **size** is not set here (§13 item 1).
11. **Sufficient-observation threshold**: not set here (§13 item 2) — to be determined either by explicit Product Owner number or by accumulation of real evidence per this project's own existing Sprint-evidence discipline.
12. **Failure mode, if a cap is ever later adopted**: rejection occurs at `propose`, the earliest point, before the substitute or passenger are engaged.

This Decision Lock, if ratified, would be sufficient to scope a first, narrow D-08 ADR covering observation only (items 1–10, 12), while explicitly carrying items 2 (partially — the secondary substitute-concentration question), 5, and 11 forward as still-open, exactly as `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`'s own closing convention already established for D-07: a partial answer is a valid, usable answer.

---

## Verification

- **No production code, schema, migration, ADR, or test was modified.** Only `docs/PIOS_D08_DECISION_ANALYSIS.md` was created.
- **No commit was made.**
- **HEAD and git status**: reported in the accompanying message to this document.
