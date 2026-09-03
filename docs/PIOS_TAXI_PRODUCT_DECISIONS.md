# PIOS Taxi Product Decisions

Status: **Ratified — Product Owner-authority decision (`PROJECT_CONSTITUTION.md` Section 7).** This document converts the Product Owner's approval (stated in this task's own prompt) into the repository's canonical product decision record for the current PIOS Taxi direction. It supersedes, for the specific items approved below, the corresponding **RECOMMENDATION — NOT YET APPROVED** items in `docs/PIOS_PRODUCT_DECISION_GATE.md`, and narrows (without contradicting) `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` and `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` where they had left the same questions open. **This document is not itself an ADR** — per this task's own explicit instruction, no ADR is created or modified here; Section 18 names which ADRs will need to be authored or extended to make these decisions architecturally real, as a future, separate step.

**Tagging convention** (per this task's own required structure): **APPROVED** (ratified by this task) / **OPEN** (explicitly not decided, carried forward) / **NON-GOAL** (explicitly excluded, not merely deferred) / **CONSEQUENCE** (an architectural implication this document identifies, not itself a new decision).

**Method**: Sections 1–17 restate the Product Owner's own approved decisions in the structure this task requires, each cross-checked against `docs/PIOS_PRODUCT_DECISION_GATE.md`, the target architecture, the convergence review, and the underlying ADRs/product decisions those documents cite. Where a genuine conflict exists between the newly-approved decisions and something already ratified elsewhere in the repository, it is named explicitly in Section 19 — **not silently resolved**, per this task's own instruction.

---

## 1. Purpose

This document exists to give the Product Owner's approval (this task's prompt) the same durable, citable status every other `PRODUCT_DECISION_*.md` document in this repository already has — so future implementation work has one canonical source for "what was actually decided about PIOS Taxi's relationship-first direction," rather than needing to reconstruct it from a task prompt. It creates no ADR, modifies no existing ADR or architecture document, and authorizes no code, schema, or configuration change (Section 20).

## 2. Product Principles

**APPROVED** (this task's own explicit restatement, ratified):

> «В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу.»

This is confirmed, not newly established — `PROJECT_CONSTITUTION.md`'s own Fundamental Laws (Driver Independence, Trust Preservation, Fair Shared Dispatch, Transparency, Relationship Protection, No Hidden Algorithmic Decisions) already ground it, and every decision below is evaluated against it, not in tension with it. **CONSEQUENCE**: every architectural choice from this point forward (Sections 3–18) must be traceable to this principle when challenged, exactly as `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` Section 7 already modeled.

## 3. Primary Driver

**APPROVED**: Primary Driver is a driver **explicitly preferred by a passenger** as a trusted/preferred service provider. It is:
- **NOT** ownership of the passenger by the driver.
- **NOT** exclusive control, permanent or otherwise, over the passenger's future orders.
- A **relationship signal for routing**, nothing more.

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 5 (Decision 2)'s own recommendation almost verbatim — that document recommended exactly this definition and explicitly rejected "exclusive service provider" and "driver's preferred status" (i.e., a claim the *driver* holds) as incompatible interpretations. **No conflict found** between this approval and the Gate document's recommendation; this is the Gate's own recommended option, now ratified rather than merely proposed.

**Mechanism, unchanged from existing implementation (FACT)**: this maps directly onto Circle of Trust's already-implemented `Connection.isPrimary` (Passenger Experience, `ADR-054`) — passenger-set, passenger-controlled, structurally "at most one primary per passenger." No new domain concept is required to *represent* Primary Driver; what changes (Section 4) is that this fact now has a real behavioral consequence for the first time.

## 4. First Refusal

**APPROVED** — the exact mechanism, restated precisely from this task's own prompt:

```
Passenger creates an order
  IF passenger has a Primary Driver:
    Primary Driver receives first opportunity to accept
    IF accepts       → assignment proceeds with Primary Driver
    IF declines       → order proceeds to normal matching
    IF no response within [configured window] → order proceeds to normal matching
    IF unavailable/ineligible → order proceeds to normal matching
  ELSE:
    order proceeds to normal matching (unchanged, Coordinator-mediated, Stage A)
```

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 4 (Decision 1)'s recommended Option B exactly, and directly matches `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` Section 8's own "Stage B" design. **No exclusive reservation exists at any point** — this is Option B, not Option A, explicitly.

**OPEN**: the exact response-window duration is a product/implementation parameter, **not decided by this document** — per this task's own explicit instruction not to invent it here. It remains exactly as open as `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 4 left it ("REQUIRES VALIDATION" on the window size, `docs/PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md`'s own uncommitted draft language, reused).

**CONSEQUENCE** (identified, not decided): the existing domain mechanism this reuses is `ProposalStatus.LAPSED` (FACT, `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/ProposalStatus.kt`) — a passive, time-based non-response outcome, already implemented and tested. First Refusal's "no response within the window" branch is structurally the same shape as an existing, proven pattern; its "declines" branch is structurally the same shape as existing `ProposalStatus.DECLINED`. The **new** thing this requires is not a new Proposal outcome, but a new **trigger**: something must decide, before creating the first Proposal for a new order, whether a Primary Driver exists for this passenger, and propose to them first. See Section 18 for the cross-module contract this implies.

## 5. Circle of Trust

**APPROVED**: Circle of Trust remains a **Taxi-level working relationship mechanism**. It is explicitly **not** a social network. It must **not** be expanded into unnecessary social functionality (no feed, no messaging, no follower graph, no public profile browsing).

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 6 (Decision 3)'s recommendation that Circle of Trust's two-role `Connection` (Passenger Experience, FACT) stay exactly as built — no conflict. **NON-GOAL**, explicitly, per this section: any feature that would make Circle of Trust resemble a social graph (multi-hop connections, connection browsing, public relationship visibility) — that capability, if it is ever needed, belongs to Network Management (Section 6), never to Circle of Trust itself.

## 6. PIOS Network Boundary

**APPROVED**: Network Management remains a **platform-level capability**. Taxi must remain **structurally compatible** with the broader PIOS Network direction, but:
- Do **not** implement the complete PIOS Network now.
- Do **not** merge Network Management functionality into Taxi.
- Do **not** create unnecessary cross-module coupling.
- Use clean conceptual boundaries.

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 6 (Decision 3)'s own recommendation and `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` Section 5's convergence model (Passenger Experience `Connection` referencing a future Network Management `Person` id, additive, no shared storage) as the **target shape**, explicitly **not authorized to build now**. **No conflict found.**

**Precise scope boundary, stated explicitly (CONSEQUENCE, not a new decision):** the First Refusal mechanism (Section 4) operates entirely within Passenger Experience's own existing Circle of Trust (`isPrimary`) — it does **not** require, use, or activate Network Management in any way. `ADR-037`'s own Consequences section, which deferred "connecting `network-management`'s `Connection` concept to actual order routing" to a future ADR, remains **exactly as deferred as before** — this document does not resolve that deferral, and First Refusal must not be built or described as if it does. These are two structurally distinct integration points (Circle of Trust ↔ Dispatch vs. Network Management ↔ Dispatch) and must not be conflated during implementation.

## 7. Passenger Freedom

**APPROVED**: the passenger:
- can choose;
- can decline;
- can use another driver;
- **cannot be trapped by a relationship.**

This is the direct behavioral guarantee First Refusal (Section 4) is designed to deliver — every branch of that state machine except "Primary Driver accepts" ends in the passenger reaching a different driver via normal matching, automatically, with no additional action required from the passenger. **CONSEQUENCE**: any future implementation that adds friction to this fallback path (e.g., requiring the passenger to manually re-request after a Primary Driver declines) would violate this approved decision and must not be built without a new, explicit decision overriding this one.

## 8. Driver Autonomy

**APPROVED**: the driver:
- can accept;
- can decline;
- can remain unavailable;
- **cannot be forced to accept.**

Unchanged from the platform's own already-ratified boundary condition (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 1: driver autonomy as a boundary condition, never optimized against) — First Refusal does not touch this. A Primary Driver's "first opportunity" is exactly that: an opportunity, never an obligation. **CONSEQUENCE**: no future implementation may attach a penalty, visible or invisible, to a Primary Driver's decline of a first-refusal offer — doing so would functionally reintroduce obligation through the back door, contradicting this section directly.

## 9. Order / Assignment / Trip

**APPROVED**, restated exactly as this task's prompt states it:
- **Order** = passenger's request.
- **Assignment** = accepted operational commitment between order and driver.
- **Trip** = actual execution of the service.

These must **not** be collapsed. **Trip remains conceptually distinct from Assignment**, and — the one placement decision this task's prompt makes explicitly — **Trip is expected to live inside the existing Dispatch bounded context for Taxi V1, not as a new deployable service.**

This ratifies `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 10's own recommendation (Option 1: Trip as a new aggregate inside Dispatch) exactly, re-validated twice more since (Task 7A Section 11, `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 17/Decision 14). **No conflict found** — this is the one place where all three prior documents already converged on the same answer before this ratification, and this task's prompt confirms it without alteration.

**CONSEQUENCE, restated from Task 7's own migration plan (not re-decided here, only carried forward):** implementing Trip will require a dual-publish migration window for the ride-progress events currently named `AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted`, renamed to `TripArrived`/`TripStarted`/`TripCompleted` — the one genuine breaking-shape event change either prior architecture document identified. This remains true and unaffected by this ratification.

## 10. Matching Philosophy

**APPROVED**: PIOS must **not** introduce an opaque "best driver" ranking system. Matching uses **explicit, explainable signals**:
- Primary Driver relationship (Section 3/4);
- Circle of Trust where applicable;
- availability;
- eligibility;
- operational constraints;
- passenger preferences where applicable.

**MATCHING RULES** (explicit, explainable, binary/filter-based) are explicitly and permanently distinguished from **RANKING ALGORITHM** (any mechanism that scores or orders eligible drivers against each other). The latter is **not** to be introduced implicitly.

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 12 (Decision 9)'s own matching-rules/ranking-algorithm table, and is fully consistent with `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 11's own exclusion of Round Robin, Ranking, Scoring, AI selection, Reciprocity, and Reputation-based selection from V1 scope. **No conflict found.**

**CONSEQUENCE**: First Refusal (Section 4) is itself a **matching rule**, not a ranking algorithm component — it is a binary gate ("does a Primary Driver exist for this passenger, yes or no"), fully consistent with this section. Any future work that turns "Primary Driver priority" into a weighted score relative to other signals (e.g., "40% relationship, 30% proximity, 30% rating") would cross from matching rule into ranking algorithm and would require its own new product decision — not authorized by this document.

## 11. Trust Model

**APPROVED**: keep **Profile**, **Trust**, **Rating**, **Reputation**, and **Relationship** conceptually distinct. Do **not** collapse them into one generic "score." Do **not** create a universal reputation score yet.

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 8 (Decision 5)'s own five-way table (Relationship / Trust / Rating / Reputation, plus Profile named separately here) as the canonical vocabulary. **No conflict found.**

**OPEN, carried forward unchanged**: the exact Rating qualitative-tag vocabulary and Reputation's aggregation threshold — neither is decided by this document, consistent with the Gate document's own Section 8 and this task's own instruction not to implement anything.

## 12. Low-Density Environments

**APPROVED**: PIOS Taxi must work in villages, small settlements, monotowns, small cities, and large cities. The architecture must **not assume high marketplace liquidity**, and must support a driver with a small number of recurring passengers.

This is a **requirement**, distinct from — and should not be conflated with — `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 13 (Decision 10)'s own framing of the *relationship-first advantage in low-density markets* as an unvalidated **ASSUMPTION**, pending real pilot evidence. **Precise distinction, stated explicitly (not a conflict, a scope clarification):** this task's approval ratifies that the architecture **must support** low-density operation (a requirement, now settled); it does not, and this document does not, ratify the *claim* that PIOS is structurally *better* than a conventional aggregator in that environment (still a hypothesis, still to be validated by the actual pilot, unchanged from the Gate document's own careful framing).

## 13. BlaBlaCar Principles

**APPROVED**, restated exactly as this task's prompt states it:

- **KEEP**: participant profile, transparency, trust signals, predictable interaction, history, participant choice.
- **ADAPT**: verification, reputation, ratings, trip history.
- **REJECT**: copying BlaBlaCar's marketplace model, opaque driver ranking, pay-to-win visibility, treating participants as marketplace inventory.

**Conflict identified against `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 19 (Decision 16), stated explicitly, not silently resolved:** that document classified participant profile, transparency, trust signals, and history as **ADAPT** (not KEEP), and — the most material difference — classified **"participant choice" as REJECT**, reasoning that BlaBlaCar's own core mechanic (a passenger browsing and selecting among multiple advertised drivers before committing) would reintroduce a driver-comparison surface incompatible with PIOS's Coordinator-mediated/personal-invitation dispatch model.

**This document's reading of the conflict, offered for confirmation, not asserted as resolved:** the now-approved REJECT list separately and explicitly excludes "copying BlaBlaCar's marketplace model" and "treating participants as marketplace inventory" — the same concern the Gate document's own REJECT-for-"participant choice" was protecting against. This suggests the Product Owner's "participant choice" (KEEP) most plausibly refers to the passenger's own **freedom to choose/decline/use another driver** (Section 7 above, already unambiguously approved) — not to a BlaBlaCar-style driver-browsing marketplace mechanic, which the same approved list rejects one line below under a different name. **This document does not resolve this itself** — it flags the apparent terminology mismatch between the two documents so a future review can confirm that both documents are describing the same underlying position (passenger choice as freedom-to-decline, not as a browsing UI) rather than silently assuming it. Until confirmed, no implementation should build a driver-browsing/selection screen on the strength of "participant choice: KEEP" alone.

The KEEP/ADAPT placement differences for profile/transparency/trust-signals/history (this document: KEEP; Gate document: ADAPT) are **not treated as a substantive conflict** — both documents agree these principles are wanted; they differ only in whether "already partially real, needs extension" (ADAPT, the Gate document's framing) or "wanted outright" (KEEP, this task's framing) is the more accurate label. This ratification's classification governs going forward.

## 14. Payment Boundary

**APPROVED**: pilot is **cash-first**. Taxi V1 architecture **must not prevent** card/payment integration later. No payment gateway is implemented by this task. No payment economics are invented by this task.

This ratifies `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 14 (Decision 11)'s own recommendation (cash-only, non-custodial, recorded-transaction Payment for V1) and `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 11's architecture (decomposable `PaymentIntent`, method-agnostic from the start) directly. **No conflict found.**

**OPEN, carried forward unchanged**: whether PIOS ever takes payment custody (Gate document Section 14's own flagged question) remains undecided — this task's approval does not address it, consistent with "do not invent payment economics."

## 15. Monetization Principle

**APPROVED**: PIOS must generate revenue — a real product requirement, not an afterthought. The governing principle:

> PIOS should monetize value it creates rather than making driver participation economically hostile.

Per-ride commission is **not assumed to be the only viable model** (a materially different, weaker exclusion than "rejected outright"). Potential future models named: transaction services, premium business tools, subscriptions, business accounts, network services — **none approved as the final pricing model.**

This is consistent with, but **narrower than**, `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 15 (Decision 12)'s own recommendation, which went further and recommended *against adopting per-ride commission as the primary model*. **No conflict** — the Gate document's own recommendation was explicitly marked "RECOMMENDATION — NOT YET APPROVED"; this task's approval ratifies the *weaker* form of that recommendation (commission is not the *only* option, not that it is *excluded*) and this document records that distinction precisely rather than overstating what was actually approved. **CONSEQUENCE**: no future implementation may design the Payment/monetization architecture in a way that structurally *requires* per-ride commission as the only possible revenue path — the architecture must remain compatible with the full set of named models, exactly as `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 22's own decomposable-`PaymentIntent` recommendation already ensures.

## 16. Pilot vs Taxi V1 vs Future Network

**APPROVED implicitly** (this task's prompt does not restate a fresh capability matrix, so the one already produced and not contradicted by anything above governs): `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 18's capability matrix, with **one row now upgraded from RECOMMENDATION to APPROVED**:

| Capability | Prior status (Gate doc) | Status after this ratification |
|---|---|---|
| Primary Driver (behaviorally meaningful) | RECOMMENDATION — NOT YET APPROVED | **APPROVED for Taxi V1** (Section 3) |
| Relationship-first routing (First Refusal) | RECOMMENDATION — NOT YET APPROVED | **APPROVED for Taxi V1** (Section 4) |
| Every other row of that matrix | RECOMMENDATION | **Unchanged — still RECOMMENDATION, not approved by this task** |

This task's own prompt approves exactly Decisions 1, 2, and 9 from `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 21's own "blocking cluster" (the three decisions that document identified as blocking implementation), plus reaffirms — without newly approving — the surrounding decisions (3–8, 10–13) that were already directionally recommended. **No other capability row moves from RECOMMENDATION to APPROVED by this document.** Payment (Section 14) and Monetization (Section 15) remain architecturally-ready-but-not-activated, exactly as before.

## 17. Explicit Non-Goals

**NON-GOAL**, consolidated from Sections 3–16 above and this task's own explicit instructions:

- Exclusive Primary Driver / any form of passenger reservation for a single driver (Section 3/4 — this was Option A, explicitly not the approved option).
- Driver ownership of a passenger, in any form (Section 3/7).
- Circle of Trust expanding into social-network functionality (Section 5).
- A complete PIOS Network implementation now, or merging Network Management into Taxi (Section 6).
- Collapsing Order/Assignment/Trip into fewer concepts, or extracting Trip into a new deployable service (Section 9).
- Any opaque "best driver" ranking system, implicit or explicit (Section 10).
- A universal, generic reputation "score" collapsing Profile/Trust/Rating/Reputation/Relationship (Section 11).
- Assuming per-ride commission is the only monetization path (Section 15).
- Implementing a payment gateway, payment economics, First Refusal, Primary Driver, Trip, or monetization in this task (this task's own explicit restriction — documentation only).

## 18. Architectural Consequences

Identified, not decided — these are what Sections 3–17 above imply must eventually be built, named here so a future implementation task has a starting inventory, per this task's own request that consequences be distinguished from decisions:

1. **A new cross-module contract is required: Passenger Experience → Dispatch.** Today, `INTERFACE_CONTRACTS.md` Section 5 lists exactly four justified contracts (Passenger Experience → Order Management, Driver Management → Dispatch, Dispatch → Order Management, Order Management → Dispatch) and explicitly does **not** include any Passenger-Experience-to-Dispatch contract. First Refusal (Section 4) cannot be implemented without Dispatch being able to ask "does this passenger have a Primary Driver, and who is it?" before creating the first Proposal for a new order — a genuinely new interaction this repository's own module-boundary discipline (`MODULE_STRUCTURE.md` Section 8, Extension Strategy) requires to be authorized by an ADR before being built, not invented ad hoc during implementation. See Section 19 for the direct conflict this creates with an already-ratified statement.
2. **A new Trip aggregate inside Dispatch** (Section 9) — architecture already specified (`docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 10/24), migration path already planned (dual-publish event rename), not newly decided here.
3. **The response-window parameter (Section 4) needs a value** before First Refusal can actually run — an implementation/product parameter this document explicitly does not set.
4. **`ProposalStatus`/`AssignmentStatus` do not need new states** for First Refusal itself — the existing `LAPSED`/`DECLINED` outcomes already cover the fallback branches (Section 4's own consequence note); what's new is the *trigger* (who gets proposed to first), not the *outcome vocabulary*.
5. **Payment/Monetization architecture must stay decomposable** (Sections 14–15) — already the target architecture's own recommendation, reaffirmed, not newly required by this ratification.

## 19. Product Decisions Still Intentionally Open

**OPEN**, explicitly, consolidated:

- The exact First Refusal response-window duration (Section 4).
- The Rating tag vocabulary and Reputation aggregation threshold (Section 11).
- Whether PIOS ever takes payment custody (Section 14).
- The specific monetization model or combination, and any pricing (Section 15).
- The reconciling reading of "participant choice" (KEEP) against the Gate document's own "participant choice" (REJECT) classification (Section 13) — **flagged for explicit Product Owner confirmation**, not assumed resolved by this document's own offered reading.
- **Direct conflict, requiring resolution before Section 18 item 1 can be implemented:** `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 currently states, as a **[RATIFIED absence]**: *"Dispatch. No ratified role at all. INTERFACE_CONTRACTS.md Section 5 lists exactly four justified module-to-module contracts; none gives Dispatch any access to Personal Client Relationship information."* This task's approval of First Refusal (Section 4) directly requires the opposite — Dispatch **must** gain access to this information for the mechanism to function at all. **This document does not silently resolve this** — the existing ratified statement and the newly-approved product decision are in direct tension, and this repository's own discipline (`MODULE_STRUCTURE.md` Section 8, `CLAUDE.md`'s "Never Change Architecture Without an ADR") requires a future ADR to formally supersede that specific ratified absence before Section 18 item 1's new contract may be built. This document is not that ADR (per this task's own explicit instruction not to create one) — it only surfaces that one is now required, and why.
- **Related, same root cause:** `ADR-034` Part 2 currently classifies Personal Client Relationship as a "future candidate input," never ratified, to Assignment Policy. This task's approval effectively ratifies it as a real input **for the specific, narrow First Refusal mechanism** (Section 4) — but this is not a general ratification of PCR as an Assignment Policy input for every future purpose (e.g., it does not authorize PCR influencing a future ranking algorithm, which Section 10 continues to forbid regardless). This distinction — PCR-as-a-binary-first-refusal-trigger is now approved; PCR-as-a-ranking-weight remains exactly as unratified as before — should be made explicit in whatever future ADR resolves the item above, so the two are never conflated.

## 20. Implementation Principles

Restated for the future engineering task that will actually build Section 4/9 (not this task — **this task implements nothing**, per its own explicit instruction):

- Build the **new cross-module contract** (Section 18 item 1) only after the conflict in Section 19 is resolved by a proper ADR — not before, and not as a side effect of implementing First Refusal itself.
- Reuse existing domain mechanisms wherever the shape already matches (`ProposalStatus.LAPSED`/`DECLINED`, Section 4/18) rather than inventing new ones.
- Keep First Refusal's own logic a **binary gate**, never a scored input — any future temptation to make it "smarter" (weighted by trip count, by recency, by anything) crosses into ranking-algorithm territory (Section 10) and requires its own new product decision, not an incremental extension of this one.
- Treat the response-window value, the Rating vocabulary, and the monetization model (Section 19) as blocking prerequisites for their respective implementation areas, not as details to be improvised during a build.
- Preserve every existing, working test/isolation/production-safety discipline already established in this repository (Task 0–2's own RabbitMQ/PostgreSQL test isolation, Task 5B's production-incident remediation) — none of the above requires touching either.

---

## Final Report

**File created:**
- `docs/PIOS_TAXI_PRODUCT_DECISIONS.md`

**Files modified:** none.

**Files deleted:** none.

**Tests/checks executed:** none — documentation-only task, no code run.

**Production databases touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO
