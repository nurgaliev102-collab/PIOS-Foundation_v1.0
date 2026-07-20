# PIOS Product Experiment: Network Pilot v1.0

Status: Product Experiment — Manual Validation Only. This document is not a Product Decision, not an ADR, and not an implementation plan. It operationalizes [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md)'s own pilot boundary into one concrete, executable, entirely manual scenario. It does not reinterpret [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md), [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md), [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), [PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md](PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md), [PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md](PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md), or [ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md](ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md) — it inherits their classifications and narrows only to a single, concrete operating scenario. No production code, database, API, event contract, ADR, or domain model is created or modified by this document.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, or directly observable in committed code, cited inline.
- **[DERIVED]** — a decision this document makes, reasoning from already-ratified or already-classified material to a concrete operational detail those sources leave to an experiment-defining document such as this one.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; most of this document is HYPOTHESIS-graded by nature, since its purpose is to observe, not assert.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is treated as validated, required, or permanent anywhere in this document.

## Section 1 — Experiment Objective

**What question this experiment answers.** Whether, in a small, real, manual setting, independent drivers holding genuine Personal Client Relationships will actually (H1) initiate and accept an authorized network fallback when they cannot personally fulfill a request, and (H2) perceive enough recurring value from that coordination to indicate willingness to pay for it in the future. **[HYPOTHESIS]** — reused verbatim from PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 5; neither hypothesis is considered validated by running this experiment, only observed.

**What this experiment does NOT answer:**

- The eventual production mechanism for Opportunity, Eligibility, Fair Opportunity Policy, or Fulfillment Authority (Section 8) — each remains exactly as open as its own source document left it.
- The exact commercial model, price point, or billing mechanism (PRODUCT_BASELINE_V2.md Section 13, unchanged).
- Whether an *automated* system would produce the same driver behavior as this *manual* one — automation-specific behavior is untested here, by design (Section 2).
- Passenger willingness to pay — H2, as stated, concerns driver willingness only.
- Any statistically rigorous or generalizable conclusion — a small, manual cohort produces qualitative and directional signals (Sections 5–6), not proof.

## Section 2 — Pilot Scenario

The exact manual flow, each step graded and grounded:

1. **Passenger has a relationship with Driver A.** **[RATIFIED]** — a recognized, recurring Personal Client Relationship (DOMAIN_MODEL.md Section 5; PRODUCT_FOUNDATION.md Section 13), pre-existing, not created by this experiment.
2. **Driver A cannot fulfill.** The scenario's own triggering condition — Driver A is unable to personally service this specific request.
3. **Passenger authorizes fallback.** **[DERIVED]** — only the requester may authorize scope expansion (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 4); for this experiment, authorization is manual — a verbal or message-based confirmation from the passenger, per PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 2's own pilot-scoped choice.
4. **Coordinator identifies Driver B.** **[HYPOTHESIS]** — PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 6 left whether any coordinator role is needed explicitly **[OPEN]**; this experiment's own concrete scenario stipulates one exists, for this experiment's operational purposes only. This does not resolve that Section 6 question generally — it is a scenario-specific operating choice, not a production or even pilot-wide requirement (Section 3 below).
5. **Driver B accepts.** **[RATIFIED]** — maps directly to the already-implemented, already-correct Acceptance/Commitment distinction (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Sections 4–5, 10): Driver B's own confirmation is what creates commitment, never anything earlier in this flow.
6. **Trip completes.** **[RATIFIED]** — ordinary Order completion (DOMAIN_MODEL.md Section 12), unaffected by this experiment.
7. **Payment remains direct between passenger and driver.** **[RATIFIED, reused]** — PRODUCT_BASELINE_V2.md Section 14; no PIOS payment custody is required or used.

**Clarification: this is not automated dispatch.** **[RATIFIED, direct consequence]** — Driver B is selected by a human Coordinator's own judgment, never by Dispatch, an algorithm, a ranking, or a score. This is consistent with PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md's own resolution that Dispatch may never initiate scope change, and with the Central Finding of ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md (Section 4.2): if PIOS's existing `Assignment`/`AssignOrderCommand` code is used at all to record the outcome, it is invoked with a driver reference the Coordinator — a human — has already chosen, exactly matching that code's own "supplied by the caller" design. Nothing in this experiment exercises Dispatch's own exclusive assignment-decision authority as an automatic or shared mechanism (ADR-002, unchanged).

## Section 3 — Participants

- **Driver cohort assumptions.** A small cohort of independent drivers, each already holding at least one real Personal Client Relationship — candidate scale of roughly 10–30, per PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 6, **explicitly not a hard requirement**, only a starting assumption this experiment may run with fewer or more participants than.
- **Passenger participation.** **[DERIVED]** — passengers and corporate customers are not separately recruited; they arise as the other side of each participating driver's own existing relationship (DOMAIN_MODEL.md Section 5). Their participation in any single scenario instance is essential (Section 2, step 3 requires their own authorization), but no passenger-specific recruitment, quota, or mandatory count is assumed.
- **Coordinator role.** **[DERIVED, not a ratified PIOS actor]** — closest in spirit to the legacy "Dispatcher / Operational Role" USE_CASE_CATALOG.md Section 2 already describes as superseded by Dispatch's own capability, "though operational participants may still interact with that capability on behalf of a fleet." This experiment's Coordinator is a human process role, exercised entirely outside PIOS software, explicitly **not** Dispatch and **not** a Dispatch override. Who fills this role — a participating driver, an external pilot operator, or another arrangement — is **[OPEN]**; this document does not make it mandatory that a single, dedicated Coordinator exists for every scenario instance.

## Section 4 — Operating Model (Manual, No Software)

Each step described as a manual, human process — no PIOS software, API, or stored record is required for any of them:

- **How requests are received.** The passenger contacts Driver A directly, exactly as they do today, entirely outside any PIOS system (phone, message, or in person). **[HYPOTHESIS]** — no ratified or required channel; this experiment does not assume the request ever passes through Order Management's own Submit Order flow.
- **How authorization is recorded.** The passenger's fallback authorization (Section 2, step 3) is recorded manually — for example, a note taken by Driver A or the Coordinator at the time it is given (verbally or by message). No form, field, or software record is required.
- **How fallback is approved.** Fallback is "approved" in the sole sense PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 4 already establishes: the passenger's own authorization is the approval. No separate platform approval step exists or is needed.
- **How a candidate driver is selected.** The Coordinator selects Driver B using their own manual judgment and personal knowledge of the cohort's availability and standing — never a ranking, score, or automated rule (Section 7).
- **How acceptance is recorded.** Driver B's own confirmation (verbal or by message) that they will fulfill the request is noted manually by the Coordinator, mirroring the ratified Acceptance concept (Section 2, step 5) without any software representation.
- **How completion is confirmed.** The trip's completion is confirmed manually — for example, Driver B or the passenger reporting back to the Coordinator that the ride occurred — with no system-of-record beyond whatever manual log the Coordinator keeps (Section 5).

## Section 5 — Data Collection

Manual, qualitative and simple quantitative signals only — **no analytics infrastructure, dashboard, or instrumented system is created by this experiment**:

- **Fallback requests.** A count of how many times Driver A could not personally fulfill and the passenger was asked whether fallback should be pursued.
- **Driver willingness.** Whether Driver A actually offers or agrees to pursue fallback when unable to fulfill personally, versus simply declining the request without engaging the experiment at all (directly informs H1).
- **Driver acceptance.** Whether an identified Driver B agrees to fulfill a given fallback request, and how often.
- **Passenger acceptance.** Whether the passenger authorizes fallback when offered, versus declining it (for example, preferring to wait for Driver A or use another means entirely).
- **Completed rides.** A count of fallback-originated requests that reach actual completion.
- **Reasons for rejection.** Qualitative notes on why a driver or passenger declined at any step — a real signal for later Decline/Expiration/Failure semantics (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 9, still open), not resolved by collecting it.
- **Willingness to pay.** Driver-stated or informally revealed interest in paying for continued access to this kind of coordination once the experiment's own manual, free coordination ends (directly informs H2).

All of the above may be recorded in a simple manual log (for example, a shared notebook or spreadsheet maintained by the Coordinator) — this document does not specify a tool, schema, or system, consistent with its own "no software implementation" scope.

## Section 6 — Success / Failure Signals

**[HYPOTHESIS]**, evidence patterns only — **no artificial success threshold (for example, "X% of requests must convert") is defined here**, since no approved document provides evidence for any specific number; setting one would be invention, not evidence, and is explicitly avoided per this task's own instruction.

- **Pattern favoring H1.** Drivers repeatedly and voluntarily pursue fallback when personally unable to fulfill, rather than simply declining the request or resolving it entirely outside the experiment (for example, informally contacting another driver themselves without ever involving the Coordinator).
- **Pattern against H1.** Drivers consistently decline to pursue fallback, or route around the experiment's own manual process even when it is available to them.
- **Pattern favoring H2.** Drivers who experience a completed fallback express or reveal genuine interest in paying for continued access to this coordination once free, manual support is no longer available.
- **Pattern against H2.** Drivers value the coordination but indicate no willingness to pay anything for it beyond the experiment's own free, manual support.

These are qualitative categories to interpret evidence against, not a pass/fail gate; a Product Owner interprets the pattern of what is actually observed, per PROJECT_CONSTITUTION.md Section 7.

## Section 7 — Pilot Boundaries (Explicit Exclusions)

None of the following is used, tested, or implied by this experiment — each is excluded consistent with, and grounded directly in, upstream documents already establishing its status:

- **AI dispatch.** **[HYPOTHESIS, evaluated only]** — PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 11; not used here.
- **Automated matching.** Excluded by Section 2's own clarification — Driver B is selected manually, by the Coordinator, never by any system.
- **Ranking.** Undesigned per PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 3; not used here.
- **Scoring.** Same status; not used here.
- **Reciprocity.** **[OPEN]**, excluded from the Fundamental Laws (PROJECT_CONSTITUTION.md Section 18); not used here.
- **Reputation.** **[OPEN]**, forbidden as a current input (ADR-034 Part 2); not used here.
- **Payments custody.** PIOS never holds funds in this experiment; payment remains direct (Section 2, step 7).
- **Commission model.** The commercial model remains **[OPEN]** (PROJECT_CONSTITUTION.md Section 19); no commission is charged, tested, or implied.
- **Geospatial optimization.** No location, routing, or positioning data is used or required — consistent with EVENT_CATALOG.md Section 7's own statement that no such data is described or implied anywhere in this domain today.
- **Production Eligibility engine.** No formal Eligibility check exists; cohort participation is manual admission only (PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 8; PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 2).

## Section 8 — Learnings Required Before Building

What this experiment can, and cannot, tell a future implementation task, for each still-open concept:

- **Opportunity model.** This experiment tests no specific Opportunity design — no software Opportunity concept is used at all (Section 2, step 4 is a human Coordinator, not a system). What it *can* surface: the observed pattern of how often a single referral (one Coordinator, one candidate) versus a broader need (multiple plausible candidates) actually arises — informing, not deciding, whether a future Opportunity concept needs to support one candidate or a genuine pool (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Sections 1–2, still open).
- **Eligibility model.** Manual admission stands in for Eligibility entirely; no formal criteria, threshold, or check is tested. This experiment cannot validate any specific Eligibility rule — only that manual admission was sufficient to run a small cohort at all.
- **Fair Opportunity mechanism.** The Coordinator's own manual selection stands in; if a Coordinator ever must choose among more than one plausible Driver B, qualitative notes on how that choice was made and perceived (Section 5) may inform a future policy discussion, but no metric, formula, or mechanism (PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Sections 3–5, 10) is validated by this experiment.
- **Fulfillment Authority mechanism.** Only manual, per-instance authorization (Section 2, step 3) is exercised. Whether a pre-authorized, standing-consent mechanism (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 6) would produce different driver or passenger behavior is **explicitly not tested** by this design — a real, named gap, not an oversight.
- **Commercial model.** Only driver-stated or revealed willingness-to-pay signals (Section 5, H2) are collected. No actual price, billing mechanism, subscription structure, or conversion is tested; PRODUCT_BASELINE_V2.md Section 13's open questions (payer, price, billing period, fee structure) remain entirely unaddressed by running this experiment.

## Section 9 — Files Changed

`docs/PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md` (new) is the only content file this experiment creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set by every preceding document in this chain. No ADR, code, database, event, or API is created or modified.

---

Where this document finds no evidence, it states so explicitly rather than filling the gap. This experiment does not override PROJECT_CONSTITUTION.md, any existing ADR, or any Product Decision; running it, and observing its results, requires a further Product Owner interpretation before any of Section 8's learnings may be acted on by a lower-authority document or implementation task.
