# PIOS Product Experiment: Network Pilot Execution Plan v1.0

Status: Product Experiment — Operational Execution Plan, Manual Validation Only. This document is not a Product Decision, not an ADR, and not an implementation plan. It defines the concrete operational execution of [PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md)'s own scenario — participant selection, exact workflow, case data format, interview questions, and post-experiment decision criteria — without reinterpreting it or any of [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md), [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md), [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md), [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), [PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md](PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md), or [PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md](PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md). No production code, database, API, event contract, ADR, or domain model is created or modified by this document; every data format described below is a manual paper or spreadsheet template, never a database schema.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, cited inline.
- **[DERIVED]** — an operational detail this document decides, reasoning from already-ratified or already-classified material to a concrete execution question those sources leave to a plan such as this one.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; most operational choices below are HYPOTHESIS-graded, since the plan exists to observe, not assert.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is promoted to a requirement, a threshold, or a permanent process anywhere in this document.

## 1. Experiment Objective

**[HYPOTHESIS], reused verbatim.** Whether independent drivers holding genuine Personal Client Relationships will actually initiate and accept an authorized network fallback when personally unable to fulfill a request (H1), and whether they perceive enough recurring value from that coordination to indicate willingness to pay for it in the future (H2) — PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 1, unchanged. This plan does not restate what the experiment does not answer; that boundary is already fixed by that document and is not reopened here.

## 2. Participant Selection Criteria

**[DERIVED]**, candidates only, none mandatory:

- **Drivers.** Independent status (PRODUCT_FOUNDATION.md Section 12); holds at least one genuine, pre-existing Personal Client Relationship (not one created for the pilot); willing to participate in a manual process and report data honestly (Sections 5–6). Cohort scale: the same candidate range as PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 6 (roughly 10–30), **explicitly not a hard requirement**.
- **Passengers/corporate customers.** Not separately selected or recruited — they arise as the other side of a participating driver's own existing relationship (DOMAIN_MODEL.md Section 5). Their only required action is informed awareness that their driver may, with their own authorization, involve another driver for a specific request they cannot personally fulfill.
- **Coordinator.** Manually selected, not algorithmically — candidate criteria: known and trusted by the cohort; understands the pilot's own boundaries (Section 9) well enough not to introduce any ranking, scoring, or automated selection; willing to maintain the manual case log (Section 5). Who specifically fills this role (a participating driver, an external pilot operator, or another arrangement) remains **[OPEN]**, per PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 3 — this document does not resolve it, only states the criteria a Coordinator should meet once someone is chosen.

## 3. Pilot Roles

- **Driver A.** The relationship-holding driver who cannot personally fulfill this specific request. Responsible for: informing the passenger that a fallback may be possible; asking for and receiving the passenger's own authorization (never assuming it); notifying the Coordinator once authorization is given.
- **Driver B.** The candidate driver the Coordinator identifies. Responsible for: deciding, of their own accord, whether to accept or decline the specific request offered — consistent with PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3's own finding that no obligation exists before acceptance; fulfilling the trip if accepted; reporting completion.
- **Passenger.** The requester. Responsible for: the one essential act only they may perform — authorizing or declining fallback (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 4); optionally confirming completion from their own side.
- **Coordinator.** The human role introduced for this experiment (PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 3). Responsible for: receiving notice of an authorized fallback need; identifying one or more candidate Driver B(s) using their own manual judgment only, never a ranked list, score, or automated rule (Section 9); contacting candidates; recording each case (Section 5). The Coordinator never overrides a passenger's authorization decision or a driver's acceptance decision — both remain exactly as autonomous as PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 4 and PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3 already establish.

## 4. Exact Manual Workflow

**[DERIVED]**, a concrete sequencing of PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Sections 2 and 4, none of it software-mediated:

1. The passenger contacts Driver A directly, as they do today, entirely outside any PIOS system.
2. Driver A determines they cannot personally fulfill this specific request.
3. Driver A informs the passenger that a fallback may be possible and asks whether they authorize it.
4. The passenger authorizes, or declines. **If declined,** the case ends here — logged as "no fallback pursued" (Section 5), a real, complete case for data purposes, not a discarded one.
5. If authorized, Driver A (or the passenger) notifies the Coordinator that an authorized fallback need exists.
6. The Coordinator manually identifies one or more candidate Driver B(s) from the cohort, using their own knowledge of availability and standing — never a ranked or scored list.
7. The Coordinator contacts the first candidate, describes the request, and asks whether they will accept.
8. The candidate accepts or declines. **If declined,** the Coordinator may manually try another candidate (still no automation) or the case ends as "no driver accepted."
9. Once a Driver B accepts, the Coordinator confirms to the passenger and Driver A who will fulfill the request.
10. The trip occurs; Driver B and the passenger interact directly; payment remains direct between them (PRODUCT_BASELINE_V2.md Section 14).
11. Completion is confirmed manually — Driver B or the passenger reports back to the Coordinator that the trip occurred, or did not.
12. The Coordinator records the complete case per the format in Section 5.

## 5. Case Data Collection Format

**[DERIVED]** — a manual paper or spreadsheet template only; **not a database schema, domain model, or software artifact of any kind.** One row or card per case, with the following fields:

| Field | Description |
| --- | --- |
| Case ID | A simple sequential or date-based label the Coordinator assigns manually |
| Date | When the case began (Step 1) |
| Driver A | Which cohort driver |
| Passenger reference | Informal reference only (e.g., "Driver A's regular client #2") — no passenger identity record beyond what the Coordinator already needs to run the case |
| Reason A could not fulfill | Free text |
| Passenger authorized fallback? | Yes / No |
| If No, reason | Free text |
| Number of candidates contacted | Count |
| Driver B (if any) | Which cohort driver, if the case reached this step |
| Driver B accepted? | Yes / No |
| If No, reason | Free text |
| Trip completed? | Yes / No |
| Payment occurred directly? | Yes / No |
| Driver/passenger comments | Free text |
| Willingness-to-pay note | Free text, only if raised (Section 6) |

This format exists to make Section 7's behavioral signals countable from a simple manual log; it does not imply, authorize, or anticipate any database table, and none is created by this document.

## 6. Interview Questions

**[HYPOTHESIS]**, candidate open-ended questions only — not a survey instrument or scored questionnaire:

**For Driver A:**
- "Why did you feel you couldn't personally fulfill this request?"
- "What made you decide to seek — or not seek — a fallback?"
- "How comfortable did you feel authorizing another driver to serve your own client?"
- "Would you use this option again?"

**For Driver B:**
- "What made you decide to accept or decline this request?"
- "How did it feel to serve another driver's client?"
- "Would you want to be offered opportunities like this regularly?"

**For the passenger (optional, lighter touch — not the experiment's primary subject):**
- "How comfortable were you with a different driver fulfilling your request?"
- "Would you want this option available in the future?"

**Willingness-to-pay probe (both drivers, framed as open, not leading):**
- "If PIOS offered ongoing access to this kind of coordination, would that interest you? Roughly how much, if anything, would you consider paying?"

No specific answer to any of these is assumed, expected, or scored in advance.

## 7. Behavioral Signals

**[HYPOTHESIS]**, reused and made concrete from PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 5, tied directly to the workflow in Section 4:

- **Fallback-authorization rate** — Step 4 "Yes" count over total Step 3 occurrences.
- **Candidate-acceptance rate** — Step 8 "Yes" count over total candidates contacted.
- **Completion rate** — Step 10–11 "Yes" count over total authorized fallback cases.
- **Repeat participation** — whether the same Driver A or Driver B uses the mechanism more than once over the pilot's duration.
- **Organic/unprompted requests** — whether drivers begin proactively asking the Coordinator about fallback availability, rather than only reactively per Step 3.

## 8. Failure Reasons

**[HYPOTHESIS]**, descriptive categories only, not root-caused definitively by this document — collected via the case log (Section 5) and interviews (Section 6):

- Passenger declined authorization.
- No candidate driver available or willing.
- Driver B declined.
- Logistics or timing failure (a candidate was found too late to help).
- Trip started but did not complete for reasons unrelated to the experiment itself (an ordinary cancellation).
- Relationship or trust concern — the passenger was uncomfortable with an unfamiliar driver.

## 9. Pilot Boundaries

Reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 7, unchanged, none used or implied anywhere in this plan: AI dispatch; automated matching; ranking; scoring; reciprocity; reputation; payments custody; commission model; geospatial optimization; production Eligibility engine.

## 10. Decision Criteria After the Experiment

**[DERIVED]** — qualitative interpretation categories only. **No specific numeric go/no-go threshold is defined here**, since no approved document provides evidence for any particular number; setting one now would be fabrication, not evidence, consistent with the discipline every document in this chain has already followed (PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 8; PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 6).

- **Proceed toward broader validation.** The observed pattern favors both H1 and H2 (PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 6) — drivers actually pursue and accept fallback, and express genuine interest in future payment.
- **Revise the experiment design.** The pattern is mixed or inconclusive — for example, strong fallback participation but no signal on willingness to pay, or the reverse — in which case the next step is adjusting this plan's own execution (Sections 2–8), not a product-level conclusion either way.
- **Do not proceed without revisiting product assumptions.** The pattern clearly disfavors H1 or H2 — drivers consistently decline to participate, or show no interest in future payment — in which case the next step is a Product Owner reconsideration of the underlying product core (PRODUCT_BASELINE_V2.md Section 3's own identity hypothesis), not a larger or automated pilot.

Which of these three applies to any actual result is a Product Owner interpretation (PROJECT_CONSTITUTION.md Section 7), not an automatic determination this document makes in advance.

## Explicit Preservations

- **This is validation, not production.** **[RATIFIED, reused]** — PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 2; nothing in this plan is a production commitment.
- **Manual coordination is allowed.** **[RATIFIED, reused]** — same source; every step in Section 4 is manual by design.
- **No automation assumptions.** **[RATIFIED]** — Section 4's own workflow assumes no software mediation at any step.
- **No ranking.** **[DERIVED]** — the Coordinator's selection (Section 3, Step 6) is explicitly manual judgment, never a ranked list.
- **No AI dispatch.** **[HYPOTHESIS, evaluated only elsewhere, unused here]** — PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 11; not used in this plan.
- **No payment custody.** **[RATIFIED, reused]** — payment remains direct (Section 4, Step 10).
- **No client ownership transfer.** **[RATIFIED]** — Relationship Protection (PROJECT_CONSTITUTION.md Sections 3, 18); a single fallback-fulfilled trip does not create or transfer Driver A's Personal Client Relationship to Driver B, consistent with PRODUCT_BASELINE_V2.md Section 12 and DOMAIN_MODEL.md Section 13 — the relationship this experiment observes remains Driver A's own throughout, regardless of how many times Driver B fulfills on their behalf.

## Files Changed

`docs/PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md` (new) is the only content file this plan creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set by every preceding document in this chain. No ADR, code, database, event, or API is created or modified.

## Open Questions

1. Who specifically fills the Coordinator role (Section 2) — remains open, as in the source experiment document.
2. The exact cohort size and duration — candidate range only (Section 2), not fixed.
3. Whether any specific numeric threshold should ever govern the decision criteria in Section 10 — deliberately left to future Product Owner judgment, not invented here.
4. Whether pre-authorized (standing) fallback authorization would produce different behavior than the per-instance manual authorization this plan exercises — untested by this design, an explicit, named gap (PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 8).
5. Every Section 8 item from PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md (Opportunity, Eligibility, Fair Opportunity mechanism, Fulfillment Authority mechanism, Commercial model) remains exactly as open as that document left it; this plan does not narrow any of them further.

## Conflicts

**None found.** This plan is a direct operational elaboration of PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md's own scenario and PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md's own boundary; nothing here narrows, contradicts, or reinterprets either, or any of the four upstream Product Decisions.

---

Where this document finds no evidence, it states so explicitly rather than filling the gap. This plan does not override PROJECT_CONSTITUTION.md, any existing ADR, or any Product Decision; executing it and interpreting its results requires a further Product Owner decision before any of Section 10's outcomes may be acted on by a lower-authority document or implementation task.
