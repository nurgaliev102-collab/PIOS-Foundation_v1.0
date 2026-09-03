# PIOS Product Decision Gate

Status: **Product decision analysis only. No decision made here is final.** This document identifies exactly which product decisions must be made by the Product Owner before implementation of the Taxi V1 target architecture (`docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md`, as corrected by `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md`) can safely begin. Every recommendation below is explicitly marked **RECOMMENDATION — NOT YET APPROVED** and must never be read as authorization to build.

**Sources used**: the ten documents this task names as current sources of truth, plus the ADRs and product decision documents they cite — read directly for this task: `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`, `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md`, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`, `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`, `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md`, `PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` (uncommitted draft, itself not a ratified decision), `ADR-037`, `ADR-034`, `ADR-054`, plus direct re-inspection of `Connection.kt` and `ProfileType.kt`. `docs/PIOS_REALITY_AUDIT.md` is treated as historical context only, as Task 6 already established — superseded by `PIOS_POST_REMEDIATION_BASELINE.md` for current-state claims.

**Tagging convention**: **FACT** / **DECISION** (already ratified elsewhere, reused, never made by this document) / **ASSUMPTION** / **RECOMMENDATION** (this document's own proposal, not approved) / **FUTURE**.

---

## 1. Executive Summary

Sixteen product decisions stand between the current, working Taxi Foundation and a safely-implementable Taxi V1. Of these, **exactly one is genuinely load-bearing and blocks everything else that depends on relationship-aware routing**: Decision 1 (relationship-first routing mechanism). Two more decisions — Decision 3 (Circle of Trust vs. Network boundary) and Decision 2 (what "Primary Driver" means) — are prerequisites *to* Decision 1, not independent of it: you cannot decide the routing mechanism before deciding what the relationship it routes on actually means and where it lives. Everything else (Decisions 4–16) can be decided in parallel or sequentially without blocking the others, and several (Payment, Monetization, Network activation) are correctly sequenced late by the existing architecture documents regardless of when they are *decided*.

The repository's own existing product-decision chain has already done a remarkable amount of this work — eight separate, already-ratified or in-progress documents converge on the same few open questions from different angles (Sections 3–4 below), each one explicitly naming what it does *not* resolve rather than silently filling the gap. This document's main contribution is not new analysis so much as **assembling that already-scattered, already-rigorous work into one decision gate**, plus the four genuinely new questions Tasks 7/7A surfaced (Trip domain naming, Payment/Rating boundaries, monetization value-attachment, small-town structural advantage) that the existing product-decision chain had not yet reached.

## 2. Product Principles Treated as Fixed

Restated, not re-decided (**FACT**, ratified in `PROJECT_CONSTITUTION.md` and reused consistently across every document this review read):

1. **Fairness is procedural, not distributional** — how a decision is made and whether it is explainable, never a guaranteed outcome (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 5, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Central Finding — reused three times independently across the decision chain, never contradicted).
2. **No hidden dispatcher preference, no opaque algorithmic decisions** — Constitution Sections 3/18.
3. **No pay-to-win priority** — payment or subscription may never purchase priority in shared-opportunity allocation, independent of whatever commercial model is eventually chosen (`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8, extended three times across the chain).
4. **Relationship Protection, symmetric** — a client is not the platform's property *and* not the driver's property (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.1, extending Constitution Section 3/18 explicitly onto the driver's side).
5. **Driver autonomy is a boundary condition, not a value to optimize** — availability is always self-declared, never overridden (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 1).
6. **Value follows contribution; payment follows work** — recognition/origin and earned payment are two different things, never conflated (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Sections 2.3/2.5).
7. **"В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу."** — this task's own restated core principle, consistent with every document above.

## 3. Decisions Already Established

Not open questions — restated here only so the sixteen decisions below aren't confused with settled ground (**FACT/DECISION**):

- Order/Assignment/Trip domain split — re-validated twice (Task 7 Section 10, Task 7A Section 11); holds.
- `OrderSubmitted` has no Dispatch consumer, by design — re-validated (Task 7 Section 9, Task 7A Section 2).
- Personal Client Relationship is **not ownership** and **not a commercial relationship** (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1) — ratified, not reopened by this document.
- A minimal software Personal Network MVP was already authorized in principle, including "order routing that checks for an existing personal relationship before offering the order to the general queue — a single, explicit, transparent rule, not a ranking" (`PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`) — this is the *shape* Decision 1 below narrows, not a blank slate.
- Network Management's Dispatch integration is **deferred, not rejected** (`ADR-037` Consequences) — re-confirmed, not reopened.
- No reciprocity engine, no reputation-based ranking, no AI selection, no payment-purchased priority — excluded across the entire chain, unanimously, by every document that touches dispatch.

## 4. Decision 1 — Relationship-First Routing

**1. The question:** When a passenger with a Primary Driver submits a new order, what exactly happens?

**2. Why it matters:** This is the one decision Task 7A identified as the most consequential gap in Task 7's own analysis (Task 7A Section 3/8) — it is the mechanism that would make PIOS's core differentiator ("connection gives advantage") operative in software rather than merely aspirational, and it is a prerequisite for the Coordinator-evolution Stage B design (Decision 8 below).

**3. What the repository already establishes:** `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` pre-authorizes, **in shape only**, "a single, explicit, transparent rule, not a ranking, score, or learned/automated policy" — this rules out Option C-as-ranking and any AI/scoring variant outright, but does not choose among what remains. `ADR-034` Part 2 lists Personal Client Relationship as a **future candidate input**, never ratified. `PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` (uncommitted draft) independently evaluates four options against "Связь даёт преимущество. Качество подтверждает право. Оплата следует за трудом" and leans toward its own "Вариант 2" (limited first-look priority) — **but this draft is explicitly not itself a decision**, per its own header.

**4. What is genuinely undecided:** the actual mechanism — exclusivity vs. first-refusal vs. simultaneous-offer vs. informational-only — and, independently, the *timeout/window* for whichever mechanism is chosen (`PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md`'s own "REQUIRES VALIDATION" flag on Option 2's window size).

**5–7. Options, consequences, risks:**

| | **A — Exclusive Primary Driver** | **B — First refusal, then fallback** | **C — Simultaneous offer to Primary + eligible pool** | **D — Informational only, normal matching primary** |
|---|---|---|---|---|
| **Passenger freedom** | Low — no path to another driver while relationship exists | Medium — falls back automatically on decline/timeout | High — passenger effectively gets fastest response, any eligible driver may serve | Highest — relationship never constrains routing at all |
| **Driver autonomy** | High for the primary (guaranteed work) but structurally resembles ownership | Preserved — primary can still decline freely, no penalty implied | Preserved, but "recognition" (Decision 6) becomes moot for actual assignment | Fully preserved, but recognition is /purely/ symbolic |
| **Trust** | Strong signal, but risks reading as a guarantee, not a relationship | Strong, transparent, matches "recognized... not a guarantee" (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1) | Weaker — the passenger may not even notice the primary driver was "first," diluting the signal | Weakest — a passenger who set a primary and sees no behavioral difference may lose trust in the feature |
| **Small-town usefulness** | High in isolation (single-driver villages), but see abuse risk below | High — exactly matches the low-liquidity case (Task 7A Section 9) | Neutral-to-negative — simultaneous offer to "the eligible pool" is meaningless where the pool is 1-2 drivers anyway | Low — wastes the one structural advantage a thin market has |
| **Supply scarcity behavior** | Works but brittle — one unavailable primary driver blocks the passenger entirely unless a manual override exists | Degrades gracefully — falls back automatically | Degrades gracefully, but adds coordination complexity for no gain when pool is already tiny | Degrades gracefully but never uses the relationship signal at all |
| **Fairness (procedural)** | Weakest — functionally indistinguishable from ownership (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.1's own explicit rejection of this shape for the *ownership* question, directly applicable here) | Strong — explainable, bounded, matches every ratified fairness principle (Section 2 above) | Strong on access fairness, weak on honoring recognition (Section 2, Principle 6) | Strong on fairness, weak on honoring recognition at all |
| **Response latency** | Fastest when primary responds; unbounded if they don't (no ratified fallback trigger without a decision here) | One bounded wait window, then fallback — predictable | Fastest expected response (multiple candidates simultaneously) | Same as current Coordinator flow (Stage A) — unaffected |
| **Operational complexity** | Low to build, high to operate (Coordinator needs a manual override path for a non-responsive "exclusive" primary) | Medium — needs a timeout/lapse mechanism, already proven (`ProposalStatus.LAPSED`, FACT) | Medium-high — needs multi-offer race resolution, a genuinely new mechanism not present anywhere in the current domain model | Lowest — no new mechanism at all |
| **Abuse/manipulation risk** | Highest — a bad-faith or overwhelmed primary driver traps the passenger with no clean exit; closest to the "customer ownership" model this task explicitly instructs must be rejected | Low — bounded exposure, passenger always reaches a driver eventually | Low, but the "simultaneous offer" shape could let a driver game acceptance speed rather than genuine availability — a new incentive not present today | None — no new surface at all |
| **Future cross-vertical applicability** | Poor — an exclusive-service model doesn't generalize to a vertical where "first refusal" makes more sense (e.g. a repair visit) | Good — first-refusal is a generic pattern any relationship-based vertical could reuse | Fair — "offer to a relevant subset simultaneously" generalizes, but loses the recognition signal every vertical needs (Decision 6) | Good by default — it does nothing vertical-specific, but also delivers nothing vertical-specific |

**8. Implications by actor:**
- **Passenger**: A/B/C/D ordered worst-to-best on freedom, except D loses the actual convenience of "my driver already knows me."
- **Driver**: A resembles obligation more than opportunity; B/C/D all preserve full decline freedom.
- **Operations**: A needs a manual-override capability that doesn't exist yet, adding real Coordinator complexity; B reuses the existing `LAPSED` pattern almost unchanged; C is the only option requiring genuinely new domain modeling (a race/first-accept-wins mechanism does not exist anywhere in Dispatch today).
- **Trust**: B best matches the already-ratified language ("recognized... not a guarantee," Part 1 of `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`).
- **Network**: B and C both generalize cleanly to a future Network-level relationship (Decision 3); A does not, because exclusivity is structurally incompatible with the "any participant may have relationships with several others" model `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.6 already anticipates.
- **Monetization**: none of A–D may ever be purchasable (Principle 3, Section 2 above) — this constrains all four equally, not a differentiator among them.
- **Future verticals**: B is the most reusable pattern (Task 7A Section 11's own "Order/Match/Execution" template reasoning applies identically here).

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED: Option B (first refusal, then fallback to Coordinator), with the exact timeout window itself left as a separate, still-open sub-decision.** This is the option `PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md`'s own draft analysis leans toward (its Option 2), reuses an already-proven domain mechanism (`ProposalStatus.LAPSED`), and is the only option that scores well on every one of the eight evaluated dimensions simultaneously rather than trading one off hard against another.

## 5. Decision 2 — Definition of Primary Driver

**1. The question:** What does "Primary Driver" mean, precisely?

**2. Why it matters:** Decision 1 cannot be implemented without this being unambiguous — "first refusal" only means something once it's clear what relationship triggers it.

**3. Established:** `Connection`'s own `isPrimary` field (Passenger Experience, FACT) is currently defined purely structurally — "at most one primary per passenger" (`ADR-054`) — with **zero behavioral meaning attached** (Task 7A Section 3's own correction: `isPrimary` is consumed nowhere today). `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1 is explicit that the underlying concept is "not ownership," "not a commercial relationship," and "not a guarantee of future assignment."

**4. Undecided:** which of the interpretations below is the *intended* one, since the current implementation is compatible with several of them simultaneously by virtue of doing nothing behaviorally.

**5–7. Interpretations, evaluated:**

| Interpretation | Compatible with PIOS? | Reasoning |
|---|---|---|
| Passenger's trusted driver (a preference the *passenger* controls) | **Yes — recommended reading** | Matches existing implementation exactly: the passenger, not the driver, sets `isPrimary` (Task 7 Section 21, re-confirmed) |
| Driver's preferred status (something the *driver* claims or is granted) | **No** | Nothing in the current implementation or any product decision gives a driver control over this; would silently invert who holds the relationship |
| Exclusive service provider | **No — must be explicitly rejected** | Directly contradicts Relationship Protection (Principle 4, Section 2) and the "not ownership" finding (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1); this is Decision 1's Option A, already scored worst |
| First-refusal relationship signal | **Yes, if Decision 1 = Option B** | This is exactly what "Primary Driver" would functionally become under the recommended routing option |
| Historical relationship signal (a fact about the past, not a rule about the future) | **Partially — insufficient alone** | Useful as an input to Recognition (Decision 6) but does not, by itself, answer what happens at routing time |
| Routing priority (a technical, backend-only concept) | **No, not as the primary framing** | Conflates the *product* meaning (what the passenger sees and controls) with the *mechanism* (what Dispatch does with it) — these should stay conceptually separate even if causally linked, the same distinction `EVENT_CATALOG.md` already draws between `Order.status`/`Assignment.status` for a different pair of concepts |

**8. Implications:** Passenger — must remain the party who sets and can unset this; Driver — receives the *benefit* of being chosen, never the *right* to claim it; Operations — needs a clear, single definition to build any override tooling around; Trust — the "not a guarantee" framing must be preserved in whatever UI copy describes this (directly continuous with this session's own `DriverTrustIndicator` design constraint against implying anything numeric or absolute); Network — "passenger's trusted driver, passenger-controlled" is the interpretation that generalizes to Network Management's general Connection model (Decision 3) without modification; Monetization — none of the interpretations may be purchasable (Principle 3).

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** Define "Primary Driver" as **"the passenger's own, passenger-controlled declaration of a trusted, first-refusal relationship — never a claim the driver holds, never exclusivity, never a guarantee."** Explicitly reject "exclusive service provider" and "driver's preferred status" as incompatible interpretations, for the reasons in the table above.

## 6. Decision 3 — Circle of Trust vs. PIOS Network

**1. The question:** What is the correct architectural/conceptual boundary between Passenger Experience's Circle of Trust and Network Management's Person/Connection model?

**2. Why it matters:** Building Decision 1's routing logic on the wrong side of this boundary either locks Taxi into a model that can't generalize (Task 7A Section 5's own finding) or prematurely couples Taxi to an undeployed module.

**3. Established** (re-confirmed this task, FACT): Circle of Trust's `Connection` (Passenger Experience) is explicitly, by its own KDoc, "deliberately minimal... not a general relationship model," two-role only (`driverId`/`passengerReference`). Network Management's `Person`/`Connection`/`Invitation` (dormant, undeployed) is explicitly designed for a general, any-participant graph (`ProfileType.DRIVER`/`PASSENGER`/`NETWORK_MEMBER`, FACT) — `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.6 states directly that a scenario where "any participant... may create their own connections" (e.g. a passenger's own contact, once connected, inviting someone else) "is exactly what network-management's already-implemented, currently-unused... model was built for."

**4. Undecided:** the exact mechanism of eventual convergence (Task 7A Section 5 proposed one: Passenger Experience's `Connection` referencing a Network Management `Person` id, additive, no shared storage — this is Task 7A's own recommendation, not yet approved, and is re-evaluated rather than re-decided here).

**5–7. What should remain Taxi-specific vs. become platform-level:**

| Concept | Should remain Taxi-specific | Should become platform-level | Reasoning |
|---|---|---|---|
| Circle of Trust's `isPrimary` marker | **Yes, indefinitely** | | It is a Taxi UX preference (Decision 2), not a portable fact about a person |
| The two-role driver↔passenger `Connection` record itself | **Yes, for V1; convergence point is Person-reference, not deletion** | | Working, tested, pilot-proven — Task 7A's "preserve working code" migration discipline applies |
| `Person` (identity independent of role) | | **Already platform-level (`ADR-037`), correctly so** | No change needed; Taxi should not invent its own version |
| General, any-participant `Connection`/`Invitation` graph | | **Platform-level — Network Management's own domain** | Directly per Section 2.6 evidence above; Taxi does not need this for V1 |
| Reputation | | **Taxi-specific for V1, schema-ready for cross-vertical later** (unchanged from Task 7 Section 12, Task 7A Section 6) | `personId`-shaped `Rating`, not `driverId`-shaped, precisely so this doesn't need rework later |

**How Taxi V1 operates independently while staying compatible (DECISION restated from Task 7A, re-affirmed, not re-decided here):** zero runtime dependency on Network Management for V1; the only forward-compatibility requirement is that Taxi's own `Profile`-shaped data doesn't diverge gratuitously from `ProfileType`'s existing vocabulary (Task 7A Section 5) — a naming discipline, not an integration.

**8. Implications:** Passenger — no visible change, Circle of Trust keeps working exactly as today; Driver — same; Operations — no new tooling required for V1; Trust — unaffected; Network — this is the one place where a *conceptual* commitment now (Person-reference as the eventual convergence shape) meaningfully reduces future rework; Monetization — Network-level relationships are a precondition for any future cross-vertical monetization (Decision 12), so getting this boundary right now has real, if deferred, commercial consequence.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** Adopt Task 7A's own Section 5 model as the target convergence shape (Passenger Experience `Connection` optionally referencing a Network Management `Person` id, additive, no shared storage, no merge) — but treat this as a **naming and future-migration-path commitment only for V1**, not something to build now. Do not build any Network Management integration before Decision 1/2 are resolved and Network Management is actually deployed (Task 6 Section 5's own finding that it is not deployed today).

## 7. Decision 4 — Participant Profile

**1. The question:** What minimum participant information must exist before a transaction (order) can occur?

**2. Why it matters:** Directly serves this task's own "BlaBlaCar-derived, not BlaBlaCar-copied" instruction — too little information undermines trust, too much becomes a ranking surface in disguise.

**3. Established:** Name only exists today (FACT, Task 6 Section 4/17). No photo, vehicle, phone verification, rating, trip count, or cancellation history exists anywhere in the domain model (FACT, repeatedly re-confirmed across Tasks 6/7/7A).

**4. Undecided:** exactly which additional fields are required *before Taxi V1 can be considered commercially ready*, versus which can wait.

**5–7. Field-by-field phasing (RECOMMENDATION, not yet approved, synthesizing Task 7 Section 13 and Task 7A Section 6):**

| Field | Pilot | Taxi V1 | Future Network | Reasoning |
|---|---|---|---|---|
| Name | ✅ existing | ✅ | ✅ | Already real |
| Photo | ❌ | ✅ | ✅ | Cheap, directly serves "clear participant profile" (BlaBlaCar principle, adapted not copied), no ranking risk |
| Phone verification | ❌ | ✅ | ✅ | Prerequisite for Rating to mean anything (Decision 5) |
| Identity verification (document-level) | ❌ | ❌ | ⚠️ possible | No ratified checklist exists (Task 7 Section 13); do not invent one here |
| Vehicle | ❌ | ⚠️ optional | ✅ (varies per vertical) | Not blocking; a driver can operate without declaring one |
| Experience/history (trip count) | ❌ | ✅ | ✅ | A count, never an average — explicitly not a ranking (Decision 9) |
| Ratings | ❌ | ✅ | ✅ | See Decision 5 |
| Reputation (aggregate) | ❌ | ✅ | ✅ | Derived only, never independently written |
| Completed trips | ❌ | ✅ (same as trip count) | ✅ | — |
| Cancellation history | ❌ | ❌ | ⚠️ possible | Genuinely new domain concept, no evidence anywhere it's required for V1 commercial readiness — flagged as a candidate, not recommended |

**8. Implications:** Passenger — more visible information before requesting increases trust without requiring choice-among-drivers (Decision 9 keeps this distinct from BlaBlaCar's browsing model); Driver — photo/verification is real, if modest, additional onboarding friction; Operations — verification needs a moderation/support path (Task 7 Section 16); Trust — every added field must stay non-comparative (no averages, no ranks); Network — trip-count/rating fields should stay `personId`-shaped from day one (Decision 3); Monetization — unaffected directly.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** the table above. Do **not** add cancellation history or document-level identity verification to V1 merely because competitors have them — no ratified requirement identifies either as necessary for Taxi V1's own commercial readiness.

## 8. Decision 5 — Trust / Rating / Reputation

**1. The question:** What precisely distinguishes Rating, Reputation, Trust, and Relationship — who creates each, who sees it, when, and whether any of it crosses verticals?

**2. Why it matters:** These four words get used loosely; without a clean separation, a future implementation risks quietly building a ranking system under the label "trust."

**3. Established:** Task 7 Section 12 already drew the Rating-vs-Reputation split (an atomic per-trip data point vs. a derived aggregate view); this task adds Trust and Relationship to the same table for completeness.

**4. Undecided:** the specific qualitative-tag vocabulary for Rating (Task 7 Section 12 left this open — "on time," "friendly," etc., named as examples, not finalized).

**5–7. The four concepts, precisely separated (RECOMMENDATION):**

| Concept | What it means | Who creates it | Who sees it | When available | Taxi-specific? | Cross-vertical? |
|---|---|---|---|---|---|---|
| **Relationship** | A recognized, recurring connection between two specific participants (Circle of Trust today) | Formed implicitly by the invitation/interaction itself; passenger controls `isPrimary` | Both participants | Immediately, on first interaction | Taxi-specific mechanism (Decision 3), general concept platform-level | Conceptually yes, mechanically no (Decision 3) |
| **Trust** | A verification-backed floor — "this claim about who someone is has been checked" (phone verified, etc.) | The verification process itself (Identity module) | Everyone who interacts with that participant | After verification completes | Platform-level (Identity already is) | Yes, directly |
| **Rating** | One atomic, immutable, per-trip data point | The other participant, post-trip | Aggregated only (Section below), never shown per-rating to third parties | After `TripCompleted` | Taxi-specific instance, `personId`-shaped schema | Yes, schema-ready |
| **Reputation** | A derived, aggregate *view* over accumulated Ratings + relationship history — never independently written | Nobody directly — always computed | The rated participant, and (in aggregate, non-comparative form) anyone considering a transaction with them | Once enough Ratings exist to aggregate meaningfully (a threshold, undecided — flagged, not resolved here) | Taxi-specific for V1 | Future, once Network is active |

**8. Implications:** Passenger — sees Reputation as reassurance, never as a ranking to browse by (Decision 9); Driver — same, plus Trust's verified badge protects against impersonation; Operations — needs a moderation path for Rating's free-text component (Task 7 Section 12); Trust — this decision *is* the trust architecture; Network — Reputation's cross-vertical future depends entirely on Decision 3's convergence shape; Monetization — Reputation must never be purchasable or boostable (Principle 3), a hard constraint on any future "premium visibility" product (Decision 12).

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** adopt the four-way table above as the canonical vocabulary going forward, specifically to prevent "trust" from becoming an informal catch-all that quietly absorbs ranking behavior. The exact Rating tag vocabulary and Reputation's aggregation threshold remain open sub-decisions.

## 9. Decision 6 — Repeat Business

**1. The question:** What precisely is a "repeat business relationship," and how does it differ from historical interaction, trusted relationship, Primary Driver, repeat customer, and (explicitly rejected) customer ownership?

**2. Why it matters:** This task explicitly requires rejecting any model where PIOS treats passengers as driver-owned assets — the terms above are easy to collapse into each other by accident.

**3. Established:** `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.1 already rejects ownership, symmetrically, for both platform and driver — the strongest available citation for this exact question (Task 7A Section 7 already used this; reused here, not re-derived).

**4. Undecided:** the *mechanism* that gives origin/recognition any operational consequence beyond a passive label (this is Decision 1, not re-litigated here).

**5–7. The five terms, precisely distinguished (RECOMMENDATION):**

| Term | Definition | Is it ownership? |
|---|---|---|
| Historical interaction | The plain fact that a specific passenger and driver have transacted before (a query over past Orders, not new storage) | No — a fact, not a relationship |
| Trusted relationship | Historical interaction plus the passenger's own affirmative choice to recognize it (Circle of Trust, general) | No |
| Primary Driver | The specific, singular, passenger-controlled top of that trust (Decision 2) | No — explicitly rejected as exclusivity/ownership (Decision 2 table) |
| Repeat customer | A passenger who has multiple historical interactions with the same driver, regardless of whether `isPrimary` was ever set | No — a descriptive fact, weaker than "trusted relationship" |
| **Customer ownership** (rejected) | A model where a driver holds an exclusive, platform-enforced claim over a passenger's future business | **Yes — this is the explicitly rejected model.** No document anywhere ratifies it; `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.1 rejects it directly, in both directions |

**Privacy/freedom protections that must hold regardless of Decision 1's outcome (DECISION, already ratified, restated):** the passenger, not the driver, controls whether a relationship is recognized (`isPrimary` is passenger-set, FACT); the relationship never grants the driver visibility into anything beyond what the ride itself already requires (no contact-sharing, ADR-059, unchanged); the passenger can always reach a different driver (Decision 1's every option except A preserves this; A is recommended against specifically because it doesn't).

**8. Implications:** Passenger — retains full freedom to choose differently at any time under every recommended option; Driver — accumulates recognition and reputation, never a transferable/purchasable customer list; Operations — no new privacy-sensitive data surface beyond what Circle of Trust already stores; Trust — this is the direct product-level expression of Trust (Decision 5); Network — "repeat business" as a concept generalizes cleanly (a different vertical's own driver-equivalent accumulates the same four-part value, Task 7A Section 7); Monetization — repeat business itself is not monetized directly; the *platform capacity* that helps form it is (Decision 12).

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** adopt the five-term table above verbatim as product vocabulary, and treat "customer ownership" as a permanently rejected model, not merely a currently-unbuilt one — i.e., a future feature request that would functionally recreate it (e.g., Decision 1 Option A) should be flagged against this decision explicitly, not evaluated fresh each time.

## 10. Decision 7 — Driver Business Model

**1. The question:** What must PIOS eventually provide so a driver can build a real business using the platform?

**2. Why it matters:** This is the direct architectural expression of the core principle this task restates in its own header.

**3. Established:** Task 7A Section 7's four-part "accumulated business value" breakdown (Recognition + Reputation + Repeat volume + Earnings) is reused directly here, now attached to explicit pilot/V1/future phasing this task additionally requires.

**4. Undecided:** which specific business tools (analytics, a "business account" concept) belong in V1 vs. later — Task 7 Section 22 named "premium driver tools" as a future monetization category without phasing it against pilot/V1.

**5–7. Phasing (RECOMMENDATION):**

| Capability | Pilot | Taxi V1 | Future Network |
|---|---|---|---|
| Professional profile (name, photo) | ⚠️ name only | ✅ | ✅ |
| Customer relationships (Circle of Trust, driver-visible side) | ✅ existing | ✅ | ✅ |
| Repeat rides | ✅ existing (a query) | ✅ (surfaced explicitly, Decision 6) | ✅ |
| Scheduled rides | ✅ existing (ADR-058) | ✅ | ✅ |
| History (own trip log) | ❌ (not surfaced to driver today) | ✅ | ✅ |
| Reputation | ❌ | ✅ | ✅ cross-vertical |
| Earnings view | ❌ | ✅ | ✅ |
| Business analytics (beyond raw earnings) | ❌ | ❌ | ⚠️ candidate |
| Business account (distinct legal/commercial entity concept) | ❌ | ❌ | ⚠️ candidate — depends on Decision 12 |
| Customer management tools (beyond what Circle of Trust already gives) | ❌ | ❌ | ⚠️ candidate — must be evaluated against Decision 6's ownership rejection before ever being built |

**8. Implications:** Passenger — unaffected directly; Driver — this is the direct realization of "build your own business"; Operations — earnings/history views reuse Owner Control Center-class tooling patterns (Task 7 Section 16), not new infrastructure; Trust — Reputation feeds directly into this; Network — "business account" and cross-vertical tools are explicitly Future, not V1; Monetization — "premium driver tools" (Decision 12) is the natural home for anything beyond this table's V1 column.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** the table above. Explicitly do **not** build business analytics or a formal business-account concept for V1 — no evidence anywhere in the pilot-scale product decisions supports needing either before basic earnings/history/reputation exist and are validated with real drivers.

## 11. Decision 8 — Coordinator Evolution

**1. The question:** Is the Stage A → B → C evolution (this task's own structure, matching Task 7A Section 8) the correct target, or does it need adjustment?

**2. Why it matters:** Determines how much of the current, working Coordinator flow must change, and when.

**3. Established:** Stage A is FACT (current implementation, Task 6 Section 5). Stage B's binary, non-ranking shape is directly pre-authorized in principle by `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` (Section 4 above). Stage C deliberately leaves the algorithm question to `ADR-034`, unresolved.

**4. Undecided:** the exact Stage B mechanism (this is Decision 1, feeding directly into Stage B's design) and the trigger/threshold for ever moving toward any part of Stage C.

**5–7.** This document does not re-evaluate the three-stage structure itself — direct review (re-checked against `ADR-034` Part 4's own candidate-mechanism list, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 11) finds **no contradiction and no missing stage**. The one refinement worth naming: Stage B should be understood as **additive to Stage A, not a replacement of it** — the Coordinator's fallback role in Stage B is structurally identical to its Stage A primary role, just triggered conditionally (Decision 1 Option B's own fallback path) rather than always. This is consistent with, not a correction to, Task 7A's own framing.

**8. Implications:** Passenger — Stage B reduces wait time for relationship-holders without removing the Coordinator safety net for everyone else; Driver — autonomy fully preserved at every stage (no stage ever overrides availability); Operations — the Coordinator's own workload actually *decreases* in Stage B only for orders with an existing Primary Driver relationship, which in a small-town deployment (Decision 10) may be the majority of orders — a real, measurable operational benefit worth tracking once piloted; Trust — each stage remains fully explainable; Network — unaffected structurally; Monetization — unaffected.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** confirm the Stage A → B → C structure as correct and adopt it as the target evolution, with Stage B blocked specifically on Decision 1's own approval (not decided here) before any implementation begins.

## 12. Decision 9 — Matching Philosophy

**1. The question:** What are PIOS's matching *rules*, and how are they kept structurally distinct from a ranking *algorithm*?

**2. Why it matters:** This task explicitly warns against introducing an opaque "best driver" system implicitly — the current documents already draw this line carefully; this decision formalizes it as its own gate.

**3. Established:** `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 3 already draws the exact distinction this decision needs: **Equal Opportunity / Balanced Participation** (process-neutrality, no new driver measurement, consistent with existing authority) vs. **Contribution-Based / Service-Quality Priority** (require a new measurement, actively favor some drivers over others, explicitly `[HYPOTHESIS]`, not supported by any ratified document). Section 11 of that same document evaluates Round Robin, Ranking, Scoring, AI selection, Reciprocity, and Reputation systems — **none chosen, all explicitly excluded from V1 scope** (`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 9).

**4. Undecided:** which of Equal Opportunity / Balanced Participation (both merely "directionally consistent," neither operationalized) actually governs the *general-queue* fallback path once Stage B (Decision 8) exists — this remains `ADR-034`'s own open question, correctly left open by every document that touches it.

**5–7. Matching rules (recommended, in scope for V1) vs. ranking algorithm (explicitly out of scope):**

| Signal | Matching rule (V1-eligible) | Ranking algorithm component (excluded) |
|---|---|---|
| Primary Driver relationship | ✅ — a binary first-look gate (Decision 1) | — |
| Availability | ✅ — existing, binary | — |
| Eligibility (Task 7 Section 8) | ✅ — a binary floor, explicitly "not ranking" (`PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md` Section 1) | — |
| Scheduled commitment | ✅ — existing, a filter/sort by time, not a score | — |
| Geographic suitability | ⚠️ not currently applicable — no location data exists anywhere (FACT); if ever added, must stay a filter (eligible/not), never a proximity score, to avoid smuggling in a ranking dimension | Would become one if implemented as a "closest wins" score |
| Driver acceptance | ✅ — existing (Proposal accept/decline) | — |
| Fairness (equal opportunity / balanced participation) | ⚠️ directionally consistent, not yet operationalized — safe as a *principle* guiding Coordinator behavior today, not yet a coded rule | Becomes one only if implemented as a numeric rotation score rather than a simple queue discipline |
| Passenger preference beyond Primary Driver | ❌ not evaluated by any document — no evidence for or against | Would need its own decision before being added at all |
| Contribution / service quality | ❌ excluded — `[HYPOTHESIS]`, unsupported | This is, by definition, a ranking algorithm component |
| Reputation | ❌ excluded from routing entirely (Decision 5's own scope — Reputation informs the passenger's *view*, never Dispatch's *decision*) | Same |

**8. Implications:** Passenger — sees relationship-first routing and transparent fallback, never a "best match" score; Driver — is never ranked against peers; Operations — the Coordinator's own manual judgment remains the only "ranking-like" behavior in the system, and it is human, accountable, and already explainable by construction; Trust — every rule above is independently explainable to the participants it affects (Principle 2, Section 2); Network — this same rules/algorithm distinction should govern any future vertical's own matching design; Monetization — reinforces Principle 3 (no pay-to-win) directly, since none of the V1-eligible rules above has any monetary dimension.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** adopt the table above as the binding matching-rules/ranking-algorithm boundary for V1. Nothing in the "ranking algorithm component" column may be implemented without its own separate, explicit product decision — this boundary itself should arguably become a required ADR (see Section 20).

## 13. Decision 10 — Low-Density Economics

**1. The question:** Does the relationship-first model give PIOS a structural advantage in villages/monotowns/small settlements, or does it merely tolerate them?

**2. Why it matters:** This is the one place this task explicitly warns against forcing a conventional marketplace model where it doesn't fit.

**3. Established:** Task 7A Section 9 already argues the *advantage* case directly: "PIOS's relationship-first model degrades gracefully to exactly the opposite [of a liquidity-dependent model]: it works best precisely where liquidity is thinnest, because the matching problem a conventional aggregator solves algorithmically is instead solved by an already-existing human relationship." This decision re-examines that claim rather than merely repeating it.

**4. Undecided:** whether this is empirically true — it is currently an **ASSUMPTION**, argued from architecture, not from pilot evidence. No document in the repository cites real low-density usage data (consistent with Task 6 Section 14's finding of zero small-town product documentation).

**5–7. Re-examination:** the argument holds *architecturally* — a 1-driver village literally cannot support Decision 1's Option C (simultaneous offer to a "pool") or any ranking-based Stage C mechanism, because there is no pool. Decision 1's recommended Option B degrades to "offer to the one relationship, then to the one human Coordinator" — which is not a degraded experience relative to a conventional aggregator's own likely failure mode in the same village (no aggregator coverage at all, since density-dependent aggregators structurally avoid low-liquidity markets). The advantage is real *by construction*, but remains **unvalidated by real usage** — this document does not overstate it into a proven fact.

**8. Implications:** Passenger (village) — 1-5 regular passengers per driver is not a degraded case for PIOS, it is close to the *modal* case Decision 1/8 are designed around; Driver (village) — full autonomy preserved, no pressure to compete in a pool that doesn't exist; Operations — Coordinator intervention remains available at every density, unlike an automated system that might simply have no candidates to show; Trust — a village's own social trust (everyone already knows everyone) makes phone verification (Decision 4) arguably *more* valuable, not less, since it's the one signal that travels with someone who moves between towns; Network — cross-vertical relationships (Decision 3's future) could plausibly matter *more* in a small settlement, where the same trusted person might provide multiple kinds of services; Monetization — a low-volume market makes per-ride commission (Decision 12) structurally weak, reinforcing the case for value-attached, not extraction-based, monetization.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** treat the low-density advantage as a testable hypothesis to validate during the actual pilot (Agidel — Artur, Regina, per `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`'s own real scenario), not as an already-proven differentiator to market on. The architecture should — and per Task 7/7A, does — support this environment without penalty; whether it is actually *better* than the alternative there remains to be measured.

## 14. Decision 11 — Payment

**1. The question:** What is the product boundary between `statedPrice` and real Payment, across cash/card/authorization/capture/refund/settlement/earnings/custody?

**2. Why it matters:** Payment is the one V1-required capability with zero existing implementation (Task 6 Section 5/10) and real regulatory/trust weight.

**3. Established:** Task 7 Section 11's architecture (PaymentIntent/Payment/Payout, cash-first V1, async from Trip) is sound and re-validated (Task 7A Section 2). This decision addresses the **product** boundary the architecture serves, not the architecture itself.

**4. Undecided:** whether PIOS ever takes payment custody (holds funds before paying out) versus only *records* a transaction the driver collects directly — a materially different regulatory and trust posture, not addressed by either prior task.

**5–7. Phasing (RECOMMENDATION):**

| Capability | Pilot | Taxi V1 | Future Network |
|---|---|---|---|
| `statedPrice` (informational only) | ✅ existing | ✅ existing, unchanged | ✅ |
| Cash, driver-confirmed | ❌ | ✅ | ✅ |
| PaymentIntent/Payment records (even for cash) | ❌ | ✅ (Task 7 Section 11) | ✅ |
| Card payment | ❌ | ❌ | ⚠️ candidate |
| PIOS payment custody (holding funds) | ❌ | ❌ — explicitly excluded (`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 9: "no payment-custody infrastructure") | ⚠️ requires its own dedicated decision, not assumed here |
| Refunds | ❌ | ⚠️ minimal (cash disputes go to Operations, Task 7 Section 16, not an automated refund flow) | ✅ full flow, once card exists |
| Driver payout/earnings (as distinct from Payment) | ❌ | ✅ view only — no actual transfer mechanism needed while cash-only (driver already holds the cash) | ✅ real payout mechanism, once custody exists |

**8. Implications:** Passenger — sees a clear, recorded transaction even for cash, improving trust/dispute-ability over today's informal `statedPrice`-only state; Driver — earnings become visible and historical (Decision 7) without requiring PIOS to touch their money; Operations — payment-problem handling (Task 7 Section 16) starts real for V1 even without custody; Trust — recorded-but-not-custodied payment is the lower-risk posture for a pilot-stage platform; Network — payment services (Decision 12) as a future monetization category depend on eventually deciding the custody question; Monetization — custody is a prerequisite for taking a transaction-based platform fee directly (vs. an invoiced/subscription fee that doesn't require custody) — this is a real fork in Decision 12's own options.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** cash-only, non-custodial, recorded-transaction Payment for V1 (matching Task 7's own architecture exactly), with the custody question explicitly deferred to a dedicated future decision rather than assumed either way by silence.

## 15. Decision 12 — Monetization

**1. The question:** What economic value does PIOS itself create, and which monetization model(s) should the architecture stay compatible with?

**2. Why it matters:** This task states plainly that PIOS must make money — this is a real product requirement, not deferred by virtue of being decided late.

**3. Established:** Task 7A Section 10 already names four real value points PIOS creates (trust infrastructure, matching/fallback capacity, payment/settlement infrastructure, operational tooling) and argues monetization should attach to those, not to raw transaction volume. Principle 3 (Section 2 above) forbids any model where paying PIOS buys dispatch priority — a hard constraint on every option below.

**4. Undecided:** which model(s), in what combination, and at what price — explicitly not decided here or by any prior task.

**5–7. Options, evaluated against who pays / value received / effects:**

| Model | Who pays | Value received | Effect on drivers | Effect on passengers | Effect on small settlements | Compatible with "build your own business"? |
|---|---|---|---|---|---|---|
| Per-ride commission | Driver, per transaction | Matching/payment infrastructure use | Directly reduces per-ride earnings, scales with volume — closest to conventional aggregator economics this task explicitly warns against | Indirect (price effects) | Weak — low volume means low platform revenue too, may not cover fixed costs | Weakest fit — this is literally the model the core principle contrasts PIOS against |
| Flat subscription (driver pays for platform access) | Driver, periodic | Ongoing access to matching, tooling, trust infrastructure regardless of volume | Predictable cost, doesn't penalize a slow week — but a real burden in a genuinely low-volume village | None directly | Weak unless priced very low or waived — a subscription assumes a driver can predict earnings well enough to justify it | Better fit — decouples cost from per-ride extraction |
| Transaction fee on Payment processing only (not matching) | Driver or passenger, per processed payment | Payment/settlement infrastructure specifically (Section 14) | Small, tied directly to a real service rendered (processing, not matching) | Small, transparent | Neutral — cash-only drivers (Decision 11's own V1 default) pay nothing | Good fit — fee tracks a specific, real service |
| Premium driver tools (analytics, enhanced visibility *of information, never ranking*) | Driver, optional | Business tooling (Decision 7's own Future column) | Opt-in, no penalty for not paying | None directly | Low relevance until a driver has enough volume to benefit from analytics | Good fit — directly serves "build your own business" |
| Business accounts / corporate customer fees | Business/corporate customer | Access to the platform's driver network for recurring business demand | Positive — more `OrderOrigin: Corporate` demand (already-modeled, unused seam, Task 7 Section 22) | N/A | Low relevance | Good fit — this is genuinely new demand, not extraction from existing drivers |
| Network services (future, cross-vertical) | Any participant, once Network is active | Access to trust/reputation portability across verticals | Positive, long-term | Positive, long-term | Potentially high relevance (Decision 10) — one trusted person serving several needs | Excellent fit, but entirely Future |
| Payment/custody services (fee on holding/transferring funds) | Whoever uses card/custody payment | Settlement convenience | Only relevant once card payment exists (Decision 11) | Only relevant once card exists | Low relevance while cash-dominant | Good fit, gated on Decision 11 |

**What economic value PIOS itself creates, stated plainly (this task's own required question, answered directly):** trust infrastructure that lets a relationship form and be believed faster than word-of-mouth alone; fallback matching capacity a driver could not generate independently; payment/settlement convenience; and operational tooling. **None of these require extracting a cut of every ride to exist** — several (subscription, tooling, business accounts, network services) can generate real revenue while a driver pays nothing per ride at all.

**8. Implications:** Passenger — models that charge the driver per-ride quietly become passenger price effects; models that charge for tooling/business accounts do not; Driver — subscription and per-ride commission are the two models most at odds with "build your own business," per-ride commission more so; Operations — no new tooling required to evaluate this decision itself; Trust — pay-to-win is forbidden regardless of which model(s) are chosen (Principle 3); Network — Network services is the only option requiring Decision 3's future activation; Monetization — this is the decision itself.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** do not adopt per-ride commission as the primary model. Favor a combination weighted toward transaction-fee-on-processing (Decision 11-gated), premium driver tools, and business accounts — each traceable to a specific value point (Section above), none extracting value regardless of platform contribution. This is a direction, not a price, and remains subject to real pilot evidence before any specific number is chosen.

## 16. Decision 13 — Data Ownership

**1. The question:** Who conceptually owns Identity, Profile, Relationship, Order, Trip, Payment, Rating, Reputation, business history, and participant preferences?

**2. Why it matters:** This task explicitly warns against PIOS becoming a "customer-data broker."

**3. Established:** Task 7 Section 19's data-ownership table already answers this at the *module* level (technical ownership); this decision restates it at the *conceptual* level this task asks for (what belongs to the participant vs. the platform).

**4. Undecided:** nothing new — this decision consolidates, rather than opens, a question.

**5–7. Conceptual ownership (RECOMMENDATION, consolidating Task 7 Section 19 + Task 7A Section 7):**

| Data | Platform-operational (PIOS custodies, never sells/brokers) | Participant-owned (participant controls visibility/existence) |
|---|---|---|
| Identity (phone, credentials) | Custody (must exist for the system to function) | **Owned by the participant** — they registered it, control it |
| Profile (name, photo) | Custody | **Owned by the participant** |
| Relationship (Circle of Trust) | Custody | **Owned jointly, but passenger controls `isPrimary`** — the specific asymmetry Decision 2/6 already establish |
| Order | Platform-operational — a transactional record both parties need but neither "owns" exclusively | — |
| Trip | Platform-operational | — |
| Payment | Platform-operational (a financial record PIOS must retain for accountability) | — |
| Rating | Created by one participant, about another — **the rated participant does not own or control it** (can't delete an honest rating), but PIOS does not own it as a sellable asset either — it exists to serve Reputation, nothing else | |
| Reputation | Derived, platform-operational (a computed view) | — |
| Business history (a driver's own accumulated Recognition/Reputation/Volume/Earnings, Decision 7) | | **Belongs conceptually to the driver, in the "not transferable, not purchasable, must keep earning it" sense (Decision 7)** — PIOS custodies the records but never resells or repurposes them commercially without the driver's own benefit in view |
| Participant preferences | | **Owned by the participant** |

**What PIOS must never do (this task's own explicit instruction, restated as a hard boundary):** never sell, broker, or monetize participant relationship/history/rating data to a third party; never use it for anything beyond serving the relationship it describes (matching, trust display, the participant's own business tooling). This is a direct extension of Relationship Protection (Principle 4) to *data*, not only to the relationship concept itself.

**8. Implications:** Passenger — full control over their own identity/profile/preferences, joint but asymmetric control over relationship visibility; Driver — accumulates business history that is genuinely theirs in effect, even though PIOS technically custodies the records; Operations — audit trails (Task 7 Section 16) are platform-operational, need no participant-ownership carve-out; Trust — this whole decision *is* a trust commitment; Network — cross-vertical data (Decision 3's future) must inherit these same ownership rules, not weaker ones, when it eventually exists; Monetization — directly constrains Decision 12: no monetization model may involve selling or brokering this data, only providing services *around* it.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED:** adopt the table above, and explicitly ratify "PIOS is not a data broker" as a standing constraint on every future monetization decision (Decision 12), not merely an aspiration.

## 17. Decision 14 — Order / Assignment / Trip

**1. The question:** Does the Order = request / Assignment = match / Trip = execution model hold?

**2. Why it matters:** This is the one Task 7 decision this task asks to be re-validated a third time (after Task 7A's own Section 11), specifically against repeat business, scheduled rides, cancellation, payment, rating, and future verticals.

**3. Established:** Validated twice already (Task 7 Section 10, Task 7A Section 11); this task's own re-check finds the same result.

**4. Undecided:** nothing new.

**5–7.** Re-checked against each named dimension, once more, briefly (no new evidence, so no new finding — stated for completeness per this task's own instruction to check, not skip):
- **Repeat business** (Decision 6): lives entirely outside the triple, in Passenger Experience's Circle of Trust — correctly separated, unaffected by how many Order/Assignment/Trip triples accumulate.
- **Scheduled rides**: `requestedPickupAt` stays on Order, correctly — no Trip concept needed until execution actually begins.
- **Cancellation**: `Order.cancel()` remains Order Management's own authority; a mid-Trip cancellation is a distinct, not-yet-fully-modeled case, same finding as Task 7A Section 11 (not a defect in the *split*, a genuine future modeling task).
- **Payment**: keyed on `TripCompleted`, unaffected.
- **Rating**: keyed on `TripCompleted`, unaffected.
- **Future verticals**: the *pattern* (three distinct ownership zones: demand, match, execution) generalizes; the *name* "Trip" correctly does not (Task 7A Section 11's own finding, unchanged).

**8. Implications:** unchanged from Task 7A Section 11 — no new implications surfaced by this re-check.

**9–10. Recommendation:**

> **RECOMMENDATION — NOT YET APPROVED (though this one is closest to settled):** preserve the model exactly as Task 7/7A left it. The only adjustment this task's own re-check surfaces is a reminder, not a change: mid-execution cancellation remains a named future modeling gap, not resolved by the existence of the three-way split itself.

## 18. Decision 15 — Pilot / Taxi V1 / Network Matrix

Consolidated across every decision above (**RECOMMENDATION**, not yet approved as a whole):

| Capability | Existing | Pilot | Taxi V1 | Future Network |
|---|---|---|---|---|
| Identity (phone+password) | ✅ | ✅ | ✅ | ✅ |
| Phone verification | ❌ | ❌ | ✅ | ✅ |
| Profile (name) | ✅ | ✅ | ✅ | ✅ |
| Driver profile (photo) | ❌ | ❌ | ✅ | ✅ |
| Vehicle | ❌ | ❌ | ⚠️ optional | ✅ |
| Trust (verified badge) | ❌ | ❌ | ✅ | ✅ |
| Rating | ❌ | ❌ | ✅ | ✅ cross-vertical |
| Reputation | ❌ | ❌ | ✅ | ✅ cross-vertical |
| Circle of Trust | ✅ | ✅ | ✅ | superseded conceptually, not replaced (Decision 3) |
| Primary Driver (behaviorally meaningful) | ❌ (structural field only) | ❌ | ✅ (Decisions 1/2) | ✅ |
| Repeat relationship (recognition) | ✅ | ✅ | ✅ | ✅ cross-vertical |
| Invitation (personal link) | ✅ | ✅ | ✅ | ✅ generalized |
| Matching (Coordinator) | ✅ | ✅ (primary) | ✅ (fallback, Stage B) | ✅ (permanent exception handler) |
| Relationship-first routing | ❌ | ❌ | ✅ (Decision 1) | ✅ |
| Trip (as distinct concept) | ❌ | ❌ | ✅ | ✅ per-vertical equivalent |
| Payment (cash) | ❌ | ❌ | ✅ | ✅ |
| Payment (card) | ❌ | ❌ | ❌ | ⚠️ candidate |
| Earnings view | ❌ | ❌ | ✅ | ✅ |
| Notifications (in-app status) | ⚠️ implicit | ⚠️ | ✅ explicit | ✅ |
| Communication (P2P) | ❌ | ❌ | ❌ | ❌ not proposed at all |
| Operations (dispute/payment-problem queue) | ⚠️ partial | ⚠️ | ✅ | ✅ |
| Business accounts | ⚠️ schema-ready | ❌ | ❌ | ✅ (Decision 12) |
| Network identity (`Person`) | ✅ built, dormant | ❌ | ❌ | ✅ activated |
| Network relationships (general graph) | ✅ built, dormant | ❌ | ❌ | ✅ activated |
| Cross-vertical reputation | ❌ | ❌ | ❌ schema-ready | ✅ |
| Cross-vertical transactions | ❌ | ❌ | ❌ | ✅ |
| Monetization (any mechanism) | ❌ | ❌ | ⚠️ architecture-ready | ✅ |

## 19. Decision 16 — BlaBlaCar Principles

| Principle | Classification | Why |
|---|---|---|
| Transparency (what's shown before interaction) | **ADAPT** | Real, valuable — but scoped to profile/trust facts, never a comparison surface (Decision 9) |
| Participant profile | **ADAPT** | Name/photo/verification yes; anything resembling a marketplace listing, no |
| Trust signals | **ADAPT** | Verified badge, non-numeric — never a score |
| Verification | **ADAPT** | Phone verification yes (Decision 4); document-level verification deferred, not rejected |
| History (trip count) | **ADAPT** | A count, never an average/rank |
| Reputation | **ADAPT** | Exists, but structurally forbidden from influencing routing (Decision 9) |
| Participant choice (browsing/selecting among drivers) | **REJECT** | Directly contradicts the Coordinator-mediated/personal-invitation dispatch model; would re-introduce driver comparison, which the no-ranking constraint (Decision 9) exists to prevent |
| Predictable interaction | **KEEP** | Already real (the state machine, Decision 14) — this is not a BlaBlaCar import, it's already PIOS's own architecture, just consistent with the principle |
| Repeat relationships | **KEEP** | Already PIOS's own actual differentiator (Decision 6), stronger and more central here than in BlaBlaCar's own model |

## 20. Consolidated Unresolved Product Owner Decisions

Every recommendation above, listed together, none approved:

1. Relationship-first routing mechanism (Decision 1) — **blocking**, feeds Decisions 2, 8, 9.
2. Primary Driver's precise definition (Decision 2) — **blocking**, feeds Decision 1.
3. Circle of Trust ↔ Network convergence shape (Decision 3) — not blocking V1, but should be settled conceptually before any Network-adjacent work begins.
4. Participant profile field set and phasing (Decision 4).
5. Rating tag vocabulary and Reputation aggregation threshold (Decision 5).
6. Formal adoption of the repeat-business vocabulary and the permanent rejection of customer-ownership-shaped features (Decision 6).
7. Driver business tooling phasing (Decision 7).
8. Confirmation of the three-stage Coordinator evolution (Decision 8) — low-risk, mostly already implied by other decisions.
9. Formal adoption of the matching-rules/ranking-algorithm boundary table (Decision 9) — candidate for its own ADR (Section 21).
10. Low-density advantage — treat as a pilot hypothesis, not yet a claim (Decision 10).
11. Payment custody posture (Decision 11) — cash/non-custodial recommended for V1, custody question deferred not decided.
12. Monetization model combination (Decision 12) — direction recommended, no pricing.
13. Data-ownership/no-broker commitment (Decision 13) — recommended as a standing constraint.
14. Order/Assignment/Trip — effectively settled; only the mid-Trip-cancellation gap remains open (Decision 14).

## 21. Recommended Decision Sequence

Dependency-aware, per this task's own request:

1. **Decision 2 (Primary Driver definition)** first — nothing else about relationships can be decided coherently without it.
2. **Decision 1 (routing mechanism)** — depends directly on Decision 2.
3. **Decision 9 (matching rules/ranking boundary)** — should be ratified alongside Decision 1, since Decision 1's own recommended option (B) is itself an instance of a matching rule and should be evaluated against the same boundary.
4. **Decision 8 (Coordinator evolution confirmation)** — follows directly once 1/2/9 are settled; mostly a formality given how strongly the existing documents already point this way.
5. **Decision 6 (repeat business vocabulary)** — can proceed in parallel with 1/2, informs Decision 7.
6. **Decision 3 (Network convergence shape)** — can proceed in parallel; only blocks future Network-adjacent work, not V1.
7. **Decision 4 (participant profile) and Decision 5 (trust/rating/reputation vocabulary)** — can proceed in parallel with each other and with 1–3; Decision 5 should precede any Rating implementation.
8. **Decision 7 (driver business model phasing)** — depends on Decision 6.
9. **Decision 11 (payment) and Decision 13 (data ownership)** — can proceed independently of the relationship-routing cluster (1/2/8/9) entirely.
10. **Decision 12 (monetization) and Decision 10 (low-density hypothesis)** — deliberately last, both depend on real pilot evidence more than on any other decision in this list.

## 22. What Must NOT Be Implemented Before These Decisions

Direct, restated from `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 9 and reinforced by every decision above — **FACT/DECISION, not this document's own invention**:

- No relationship-first routing code (Decision 1's own subject) until Decisions 1 and 2 are both approved.
- No ranking, scoring, AI selection, or reciprocity engine, regardless of any decision above — permanently excluded, not merely deferred (Decision 9, Section 3 above).
- No Network Management integration of any kind until Decision 3 is approved.
- No Rating/Reputation code until Decision 5's vocabulary is approved (building the wrong shape now is worse than waiting).
- No payment custody infrastructure until Decision 11's custody question is separately, explicitly resolved.
- No monetization mechanism activated until Decision 12 is approved — the architecture may be built ready (Task 7 Section 22), but nothing should charge anyone yet.
- No business-account or cross-vertical feature — all explicitly Future (Decision 15 matrix).

## 23. Proposed Next Architectural Step

Not implementation. The next legitimate step is presenting Decisions 1, 2, and 9 (the blocking cluster, Section 21 above) to the Product Owner for actual approval — these three, once approved, unblock Stage B (Decision 8) and give the Trip/Payment/Rating build sequence (Task 7 Section 25, Task 7A Section 16) a settled relationship-routing foundation to build against instead of an assumed one. Every other decision in this document can be approved on a slower, parallel track without blocking that first implementation milestone.

---

## Final Report

**File created:**
- `docs/PIOS_PRODUCT_DECISION_GATE.md`

**Files modified:** none.

**Files deleted:** none.

**Tests/checks executed:** none — documentation-only task.

**Production databases touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO
