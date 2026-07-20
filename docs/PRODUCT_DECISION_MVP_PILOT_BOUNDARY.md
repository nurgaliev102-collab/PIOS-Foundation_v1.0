# PIOS Product Decision: MVP Pilot Boundary v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document defines the smallest realistic PIOS pilot boundary that validates the product core before any implementation expansion. It is not an ADR, not an architecture document, and authorizes no new code, aggregate, event, or database change. It is the pilot-stage follow-up [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md) Section 19 Item 9 and [ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md](ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md) Section 10 both anticipated, arriving only after [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md), [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), [PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md](PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md), and [PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md](PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md) had already classified the concepts a pilot would exercise.

Derived from PROJECT_CONSTITUTION.md, PRODUCT_BASELINE_V2.md, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md, PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md, PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md, PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md, and ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md. It introduces no new aggregate, event, command, or actor; where it selects a *pilot-scoped* mechanism among candidates those documents already classified (Section 2 below), it does so explicitly as a provisional, validation-only choice — never as a permanent resolution of those documents' own broader, still-open questions.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, or directly observable in committed code, cited inline.
- **[DERIVED]** — a decision this document actually makes, reasoning from already-ratified principles and already-classified candidates to a question those sources leave to a pilot-defining document such as this one.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; this entire document is, by its own nature, mostly HYPOTHESIS-graded, since a pilot boundary's purpose is to test hypotheses, not assert facts. Selecting a mechanism *for pilot purposes* does not promote it to [RATIFIED] — it remains [HYPOTHESIS], explicitly.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is promoted to a requirement, a permanent architectural decision, or a production commitment anywhere in this document.

## Central Finding, Stated First

Inspecting `Assignment.create(order, driver, existingAssignments)` directly: it already accepts **any** externally-supplied driver reference, with no candidate-pool, Eligibility, or Fair Opportunity Policy check of any kind (ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md Section 4.2). **This means a NETWORK-scope fallback — a request originally intended for one driver, fulfilled instead by another, eligible driver — can be recorded today, using only already-built, already-ratified code, by simply invoking the existing Assign Order command with a different driver reference than the one originally intended.** No Opportunity aggregate, Eligibility check, or Fair Opportunity Policy implementation is required for the pilot's *own* minimal recording of a fallback fulfillment — only a human process (Sections 2, 4, 9) that decides, outside the code, which driver to name.

**Consequence:** the pilot's own core loop can be validated with **zero new production code**, provided every decision this task's own upstream Product Decisions left open (authorization mechanism, admission/eligibility, ordering among eligible drivers) is handled manually, by people, not by new software. This is the single most load-bearing finding for Sections 2, 4, and 9 below.

## 1. What Exact User Scenario Does the Pilot Validate?

**[HYPOTHESIS]**, built only from already-classified pieces, never inventing a new one:

1. A driver already holds a recognized, recurring Personal Client Relationship with a passenger or corporate customer (DOMAIN_MODEL.md Section 5; PRODUCT_FOUNDATION.md Section 13) — a real, pre-existing relationship, not one created by the pilot.
2. That passenger or corporate customer requests transportation, intending the relationship-driver to fulfill it (DIRECT scope, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3).
3. The driver cannot personally fulfill this specific request.
4. The requester authorizes fallback (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 4 — only the requester may authorize this); for pilot purposes, this authorization is manual (Section 4 below).
5. An eligible driver within the pilot cohort (Section 6 below) is identified through a manual process and fulfills the request (NETWORK scope).
6. The order completes, and payment for the ride occurs directly between the requester and the fulfilling driver, outside PIOS (PRODUCT_BASELINE_V2.md Section 14).

This is the same candidate scenario PRODUCT_BASELINE_V2.md Section 3 already evaluated clause-by-clause and tagged **[HYPOTHESIS]** as a whole; this document does not promote it to fact, only operationalizes it as the pilot's own validation target.

## 2. What Is Included in MVP?

**[HYPOTHESIS]** — a coherent, minimal composition selected *for pilot purposes only*, consistent with, and the most conservative synthesis of, the candidates each upstream document already left open:

- One local cohort of independent drivers, each already holding at least one real Personal Client Relationship (PRODUCT_BASELINE_V2.md Section 14).
- Manual admission to the cohort (the pilot-scoped answer to PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 8's own candidate).
- Manual, per-order authorization by the requester for fallback (the pilot-scoped answer to PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 6's own candidate — not pre-authorization, which remains a separate, unvalidated candidate that the pilot does not need in order to test H1/H2).
- Manual, human-mediated selection of which eligible driver receives a given fallback request (the pilot-scoped answer to PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 10 — not Round Robin, FIFO, or any other named candidate specifically; a human decides, transparently, per Section 9 of that document's own transparency requirement).
- Recording the resulting fallback fulfillment through the **already-existing** `AssignOrderCommand`/`Assignment` flow (Central Finding, above) — no new domain concept.
- Direct payment between requester and fulfilling driver; no PIOS payment custody (PRODUCT_BASELINE_V2.md Section 14).

**This selection does not resolve any sibling document's broader question.** Choosing "manual" for the pilot does not decide that manual authorization, manual admission, or human-mediated ordering are the eventual production answer — each remains exactly as open, beyond the pilot's own scope, as its own source document left it (Section 12 below).

## 3. What Is Explicitly Excluded?

**[RATIFIED, reused directly]** — consistent with PRODUCT_BASELINE_V2.md Section 15 (DEFER list), PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 11, and PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md's own Explicitly Out of Scope: Reciprocity Engine or any reciprocity-based mechanism; composite driver trust score, opaque ranking, or any reputation system; dynamic pricing; PIOS payment custody or a commission engine; AI-based or ML-based selection; city-scale geospatial dispatch; complex automated KYC infrastructure; REFERRED scope (still **[OPEN]** as a concept, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3, and not needed to test H1/H2). None of these is required to observe whether H1 or H2 (Section 5) holds.

## 4. Which Processes May Remain Manual?

**[HYPOTHESIS]**, extending PRODUCT_BASELINE_V2.md Section 14's own "manual admission is acceptable as a pilot mechanism" consistently to every gate this decision chain has identified: admission/Eligibility; per-order fallback authorization; selection among eligible drivers (Fair Opportunity Policy); and payment. All four may remain entirely manual and human-mediated for the pilot, consistent with the Central Finding's own conclusion that no new software is required to test the core loop.

## 5. The Two Existential Hypotheses

**[HYPOTHESIS], reused verbatim, neither claimed validated, consistent with PRODUCT_BASELINE_V2.md Section 14:**

- **H1.** Will drivers actually share unmet, relationship-sourced demand when another driver may interact directly with their client?
- **H2.** After experiencing recurring real value, will drivers actually pay enough for PIOS to support a viable business?

This document does not answer either. Sections 7–8 below define what observing an answer would look like, without asserting one.

## 6. What Participants Are Required?

**[DERIVED].** A cohort of independent drivers — approximately 10–30, per PRODUCT_BASELINE_V2.md Section 14, explicitly **not** a hard requirement, only a candidate scale. At least some cohort drivers must already hold a real Personal Client Relationship generating genuine, occasionally-unmet demand — without this, H1 has nothing to observe. Passengers/corporate customers are not separately recruited: they arrive as the other side of each driver's own existing relationship (DOMAIN_MODEL.md Section 5), consistent with the relationship being jointly held, not platform-originated.

**Whether any operational/coordinator role is required is [OPEN].** PIOS's own Administration module remains unscaffolded (ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md Section 3); if the manual processes in Sections 2 and 4 require a human coordinator, that role exists entirely outside PIOS software during the pilot, not through any Administration capability. This document does not decide whether such a coordinator is needed, or who they would be.

## 7. What Signals Should Be Measured?

**[HYPOTHESIS]** — candidate signals only, no instrumentation or data model designed here:

- The rate at which drivers actually initiate a fallback authorization request versus simply declining or losing the request without engaging the pilot mechanism at all (directly informs H1).
- The rate of successful NETWORK-scope fulfillment once fallback is authorized.
- Driver-stated satisfaction with, and willingness to continue using, the fallback mechanism.
- Driver-stated or revealed willingness to pay for continued access after experiencing real value (directly informs H2).
- Frequency of decline or non-response by an eligible driver offered a fallback request — informs, but does not resolve, PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 9's still-open Decline/Expiration/Failure semantics.
- Any friction where an admitted, eligible driver receives no fallback opportunities at all over the pilot's duration — informs, but does not resolve, Fair Opportunity Policy's own still-open metric question (Section 5 of that document).

## 8. What Would Count as Evidence For or Against the Product?

**[HYPOTHESIS]**, qualitative categories only, no specific threshold or statistical design set here:

- **Evidence for H1.** Drivers repeatedly and voluntarily choose to authorize fallback through the pilot mechanism when their own relationship-driver cannot fulfill, rather than declining the request outright or routing around the pilot mechanism entirely.
- **Evidence against H1.** Drivers consistently decline to authorize fallback, preferring to lose the ride, or handle any substitution entirely outside the pilot mechanism (for example, by informally texting another driver themselves without ever engaging PIOS's own process).
- **Evidence for H2.** Drivers who experience real value from the fallback mechanism express, or better, reveal (through an actual willingness-to-pay test) genuine interest in paying for continued access once the pilot's manual coordination is no longer free.
- **Evidence against H2.** Drivers value the coordination but are unwilling to pay anything beyond what a free or informal, manual arrangement already provides.

## 9. What Must NOT Be Built Before Pilot Validation?

**[DERIVED, directly from the Central Finding above and from every upstream document's own exclusions]:**

- Any Opportunity aggregate, event, or domain concept — the pilot's own fallback recording uses the already-existing `AssignOrderCommand`/`Assignment` flow, unmodified.
- Any Eligibility check, field, or contract beyond manual, human admission to the cohort.
- Any Assignment Policy port implementation or algorithm — including Round Robin, ranking, scoring, or AI selection, all evaluated only, never chosen, by PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 11.
- Any Fulfillment Authority code, field, or automated authorization mechanism — the pilot's own authorization is manual, per Section 4.
- Any reciprocity engine, reputation system, payment-custody infrastructure, or commission engine — unchanged exclusions (Section 3).
- Any new database migration, API, or event contract — consistent with this document's own MODE restriction and with ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md Section 7's finding that every existing table already correctly supports today's Order/Assignment flow without modification.

## Explicit Preservations

- **MVP is validation, not production architecture.** **[RATIFIED, reused]** — PRODUCT_BASELINE_V2.md Section 14's own framing; nothing selected in Section 2 above is a production commitment.
- **Manual operations are acceptable.** **[RATIFIED, reused]** — same source, extended consistently across every gate (Section 4).
- **No AI dispatch.** **[DERIVED]** — consistent with PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 11 (AI selection evaluated only, never chosen) and ADR-002/ADR-034's standing exclusion of any algorithm.
- **No ranking.** **[DERIVED]** — same reasoning; ranking remains undesigned per Fair Opportunity Policy Section 3.
- **No reciprocity engine.** **[RATIFIED]** — Constitution Section 18; unchanged.
- **No payment marketplace.** **[RATIFIED, reused]** — PRODUCT_BASELINE_V2.md Sections 13–14: the commercial model remains open, and PIOS payment custody is explicitly not required to validate the core loop.
- **No hidden prioritization.** **[RATIFIED]** — No Hidden Algorithmic Decisions (Constitution Sections 3, 18); the pilot's own manual, human-mediated ordering (Section 2) is, by construction, as explainable as any process this principle already governs.

## Files Changed

`docs/PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` (new) is the only content file this decision creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set for every preceding document in this chain. No ADR, code, database, event, or API is created or modified.

## Unresolved Decisions

1. Concrete Eligibility criteria beyond manual admission (PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 8) — unchanged, still open beyond the pilot.
2. Concrete Fair Opportunity Policy mechanism for any scale beyond fully manual coordination (PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 10) — unchanged, still open beyond the pilot.
3. Concrete Fulfillment Authority mechanism for production (manual vs. pre-authorized, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 6) — unchanged, still open beyond the pilot.
4. Exact pilot cohort size, geography, and duration — recorded only as a candidate range (Section 6), not fixed here.
5. Exact success thresholds, statistical design, or measurement instrumentation for the signals in Section 7 — categories only, not designed here.
6. Whether any coordinator/operational role is needed to run the manual processes, and who fills it (Section 6).
7. Whether minimal internal tooling (for example, a spreadsheet or an internal script an operator uses to track manual admissions) counts as "not building" — this document assumes any such tooling sits entirely outside PIOS's own production system and is not itself a PIOS artifact, but does not resolve the boundary precisely.

## Conflicts

**None found.** Checked against and consistent with: PRODUCT_BASELINE_V2.md Sections 14–15 (this document operationalizes, does not contradict, its MVP/pilot framing and DEFER list); PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md, PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md, PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md, and PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md (every pilot-scoped selection in Section 2 is drawn only from candidates those documents already named, never inventing a new one, and none of their own broader open questions is claimed resolved); ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md (this document's Central Finding reuses, rather than re-derives, that analysis's own Section 4.2 and Section 10 conclusions about `Assignment.create()`'s existing flexibility).

## Impact Analysis

**Documents:** No existing document modified beyond the one minimal `docs/README.md` line. Every source document's own open questions remain exactly as open as before; this document narrows only which *pilot-scoped* candidate is provisionally exercised, not which is permanently decided.

**Code:** None. `Assignment.kt`, `AssignOrderCommand.kt`, and every other file referenced are read and cited, not modified.

**Future work enabled, not performed:** A future operational plan could stand up the manual cohort, admission, authorization, and ordering processes described in Sections 2 and 4 using only already-existing PIOS code plus human process — no implementation task is required to begin a pilot under this boundary. A future Product Owner decision would still be needed to move any of Sections 2's provisional choices from pilot-only to production-permanent, per Section 12 (Section titled Unresolved Decisions above) of the broader decision chain.

## Recommended Next Step

Not an ADR, and not an Assignment Policy or Opportunity implementation (both remain excluded, Section 9). The most evidence-grounded next step is an operational decision — outside this document's own product-decision scope — to actually stand up the manual pilot process described in Sections 2 and 4, since Sections 1–8 already establish the boundary, participants, and evidence criteria precisely enough to begin observing H1 and H2 without building anything new.

## Traceability

| Section | Source Document |
| --- | --- |
| Central Finding | ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md Section 4.2; `Assignment.kt`, `AssignOrderCommand.kt` |
| 1. User Scenario | PRODUCT_BASELINE_V2.md Section 3; DOMAIN_MODEL.md Section 5; PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Sections 3–4 |
| 2. MVP Composition | PRODUCT_BASELINE_V2.md Section 14; PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 6; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 8; PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 10 |
| 3. Explicit Exclusions | PRODUCT_BASELINE_V2.md Section 15; PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 11; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Explicitly Out of Scope |
| 4. Manual Processes | PRODUCT_BASELINE_V2.md Section 14 |
| 5. H1/H2 | PRODUCT_BASELINE_V2.md Section 14 |
| 6. Participants | PRODUCT_BASELINE_V2.md Section 14; DOMAIN_MODEL.md Section 5; ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md Section 3 |
| 7–8. Signals and Evidence | PRODUCT_BASELINE_V2.md Section 14; PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 9; PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 5 |
| 9. Not Built | ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md Sections 4.2, 7, 9, 12 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Decision (above) requires a further Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
