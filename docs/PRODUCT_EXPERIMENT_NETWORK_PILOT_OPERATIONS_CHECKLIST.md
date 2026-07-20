# PIOS Product Experiment: Network Pilot Operations Checklist v1.0

Status: Product Experiment — Operational Checklist, Manual Validation Only. This document is not a Product Decision, not an ADR, and not an implementation plan. It is a checklist enabling a human team to run [PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md) and [PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md) consistently, without reinterpreting either of them or [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md), [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md), [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md), [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), [PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md](PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md), or [PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md](PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md). Every item below is manual, experimental, non-production, and non-automated. No production code, database, API, event contract, ADR, domain model, or implementation task is created by this document.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, cited inline.
- **[DERIVED]** — an operational checklist item this document adds, reasoning from the already-approved Execution Plan and Product Decisions to a concrete, repeatable action those sources describe but do not itemize as a checklist.
- **[HYPOTHESIS]** — a candidate practice requiring real-world validation; most individual checklist items are HYPOTHESIS-graded in effect, since the checklist exists to run an experiment, not assert a fact.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Section-level tags are given once per section rather than per line, to keep this document usable as an actual checklist; no item, individually or collectively, is promoted beyond its section's tag.

## Section 1 — Pilot Preparation Checklist

**[DERIVED, itemizing PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Sections 2–3]**

**Participants**

- [ ] Driver A selection confirmed: independent driver, holds at least one genuine, pre-existing Personal Client Relationship, willing to participate and report honestly.
- [ ] Driver B cohort selection confirmed: small candidate group (roughly 10–30, not a hard requirement), independent drivers, willing to be manually contacted for fallback opportunities.
- [ ] Passenger participation confirmed: no separate recruitment — each Driver A's own existing relationship is the only source; passenger is aware fallback may someday be offered.
- [ ] Coordinator assignment confirmed: a specific person named, known and trusted by the cohort, briefed on Section 7's boundaries, willing to maintain the manual case log.

**Before starting, for every participant**

- [ ] Pilot purpose explained: this validates whether drivers will share unmet demand and value ongoing coordination (H1/H2) — not a finished product.
- [ ] Boundaries explained: manual only, no automation, no ranking, no AI, no payment custody, no client transfer (Section 7).
- [ ] Voluntary participation confirmed: every participant may decline to take part, or to continue, at any time, with no consequence.
- [ ] Direct payment model confirmed: payment for any completed trip stays strictly between passenger and the fulfilling driver; PIOS never holds funds.
- [ ] No client ownership transfer confirmed: a fallback-fulfilled trip never makes the passenger "belong" to Driver B — the Personal Client Relationship remains Driver A's own throughout.

## Section 2 — Role Guides

**[DERIVED, itemizing PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 3]**

**Driver A**

- [ ] When unable to fulfill a request personally, tell the passenger plainly and ask whether they would like a fallback to be attempted — never assume or default to it.
- [ ] Request fallback authorization directly and in the passenger's own words; do not proceed without it.
- [ ] Information that may be shared with the Coordinator: the nature of the request and that authorization was given — nothing more about the passenger than is needed to arrange fulfillment.
- [ ] What must not be promised: a specific driver, a specific time, or that fallback will always be available in the future — this is an experiment, not a guaranteed service.

**Driver B**

- [ ] Opportunities are presented one at a time, by the Coordinator, describing the request in plain terms.
- [ ] Acceptance or decline is entirely the driver's own free choice, every time — no obligation exists until acceptance is given (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3).
- [ ] Payment expectation: direct from the passenger, exactly as for any other ride; PIOS is not involved in payment.

**Passenger**

- [ ] Authorization is always a choice, never a default — declining fallback is a normal, acceptable outcome.
- [ ] Direct relationship expectations: the existing relationship with Driver A is unaffected by using fallback once.
- [ ] Acceptance of alternative fulfillment is entirely voluntary; the passenger may decline the specific Driver B offered and simply wait for Driver A or make other arrangements instead.

**Coordinator**

- [ ] Role is manual coordination only — identifying and contacting candidate drivers by personal knowledge, never by rule, score, or list ranking.
- [ ] Logging responsibility: complete the case log (Section 4) for every case reaching Step 4 of the workflow, including declined and incomplete ones.
- [ ] Neutrality requirement: no favoritism toward any driver beyond ordinary availability and judgment; no participant is owed a particular outcome.
- [ ] Prohibited actions: never overrides a passenger's authorization decision or a driver's acceptance decision; never uses any automated tool, ranking, or scoring method to select a candidate; never takes custody of payment; never promises a client relationship transfer.

## Section 3 — Case Execution Checklist

**[DERIVED, itemizing PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 4]**

- [ ] 1. Request received — passenger contacts Driver A directly, as normal.
- [ ] 2. Driver A unavailable — Driver A determines they cannot personally fulfill.
- [ ] 3. Passenger fallback authorization — Driver A asks, passenger authorizes or declines; if declined, log the case as complete here.
- [ ] 4. Coordinator notified — Driver A (or passenger) informs the Coordinator of the authorized need.
- [ ] 5. Candidate contacted — Coordinator manually identifies and contacts a candidate Driver B.
- [ ] 6. Acceptance/decline recorded — candidate's answer is logged; if declined, Coordinator may try another candidate manually, or log the case as "no driver accepted."
- [ ] 7. Trip completed — Driver B and passenger interact directly; payment is direct.
- [ ] 8. Outcome recorded — completion (or non-completion) is confirmed and logged.
- [ ] 9. Feedback collected — Coordinator notes any comments volunteered; interview questions (Section 5) are used where a fuller conversation is possible.

## Section 4 — Data Collection Checklist

**[RATIFIED, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 5 — the same manual case-log concept, not a new one.]** No database schema, table, or software artifact is created by this section or by using this checklist.

Every case log entry must record:

- [ ] Case identifier.
- [ ] Participants involved (Driver A, Driver B if reached, passenger reference).
- [ ] Authorization outcome (Yes/No, and reason if No).
- [ ] Fallback attempt details (number of candidates contacted).
- [ ] Acceptance result (Yes/No, and reason if No).
- [ ] Completion (Yes/No).
- [ ] Payment confirmation (direct payment occurred: Yes/No).
- [ ] Feedback notes (free text from any participant).

## Section 5 — Interview Checklist

**[HYPOTHESIS, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 6]** — open-ended prompts, not a scored survey.

**Driver A**

- [ ] Comfort with fallback — how did it feel to hand off a request to another driver?
- [ ] Fear of losing the client — did anything about this feel risky to the relationship?
- [ ] Perceived value — was this helpful enough to want again?

**Driver B**

- [ ] Motivation — why accept (or decline)?
- [ ] Concerns — anything that gave pause?
- [ ] Repeat participation willingness — would they want to be offered this again?

**Passenger**

- [ ] Trust — how did it feel to be served by an unfamiliar driver?
- [ ] Acceptance of alternative driver — would they choose this again, or prefer to wait?
- [ ] Experience quality — anything notably better or worse than the usual arrangement?

## Section 6 — Weekly Review Checklist

**[DERIVED]** — a recurring, lightweight review, not a formal analytics process:

- [ ] What happened this week? (case count, outcomes)
- [ ] Where did participants hesitate? (at authorization, at candidate acceptance, elsewhere)
- [ ] What repeated patterns appeared, if any?
- [ ] What assumptions from the Execution Plan failed to hold in practice?
- [ ] What should remain manual, based on what was observed so far?
- [ ] What, if anything, now looks like it requires a future product decision rather than continued manual observation?

## Section 7 — Pilot Safety Boundaries

**[RATIFIED, reused directly — none of the following is used, tested, or implied anywhere in this checklist]:**

- No automated dispatch.
- No ranking.
- No scoring.
- No AI decisions.
- No hidden prioritization.
- No client transfer promises.
- No payment custody.
- No commission logic.

Every checklist item in Sections 1–6 has been checked against this list; none introduces any of the above.

## Section 8 — End of Pilot Review

**[DERIVED]** — the final review's structure only; **no numeric threshold is defined**, consistent with PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 10's own refusal to invent one without evidence:

- [ ] **Evidence collected** — total cases logged, complete case log compiled.
- [ ] **Observed behavior** — summarized patterns across Section 6's weekly reviews.
- [ ] **Validated signals** — which behavioral signals (PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 7) were actually observed, and how strongly.
- [ ] **Invalidated assumptions** — which Execution Plan or Product Decision assumptions did not hold in practice (Section 6's own weekly question, consolidated).
- [ ] **Open product decisions** — which of PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 8's five items (Opportunity, Eligibility, Fair Opportunity mechanism, Fulfillment Authority mechanism, Commercial model) now have some real evidence behind them, and which remain exactly as open as before.
- [ ] **Recommendation** — one of three qualitative outcomes, a Product Owner interpretation (PROJECT_CONSTITUTION.md Section 7), not an automatic calculation:
  - **Continue** — evidence favors both H1 and H2; proceed toward broader validation.
  - **Modify** — evidence is mixed or inconclusive; revise the Execution Plan (Sections 2–8 of that document) before continuing.
  - **Stop** — evidence clearly disfavors H1 or H2; return to the Product Owner for a product-level reconsideration, not a larger or automated pilot.

## Files Changed

`docs/PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md` (new) is the only content file this document creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set by every preceding document in this chain. No ADR, code, database, event, or API is created or modified.

## Open Questions

Unchanged from the documents this checklist operationalizes — this document narrows none of them further: who fills the Coordinator role; exact cohort size and duration; whether any numeric threshold should ever govern Section 8's recommendation; whether pre-authorized (standing) fallback authorization would behave differently than the per-instance manual authorization this checklist exercises; and all five of PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 8's items (Opportunity, Eligibility, Fair Opportunity mechanism, Fulfillment Authority mechanism, Commercial model).

## Conflicts

**None found.** This checklist is a direct, itemized restatement of PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md's own roles, workflow, data format, and interview questions, and of PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md's own scenario and boundaries; nothing here contradicts, narrows, or reinterprets either document, PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md, or any of the four upstream Product Decisions.

---

Where this document finds no evidence, it states so explicitly rather than filling the gap. This checklist does not override PROJECT_CONSTITUTION.md, any existing ADR, or any Product Decision; running it and interpreting its results requires a further Product Owner decision before any of Section 8's outcomes may be acted on by a lower-authority document or implementation task.
