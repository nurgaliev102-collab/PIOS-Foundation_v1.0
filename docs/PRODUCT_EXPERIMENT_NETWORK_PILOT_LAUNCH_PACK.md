# PIOS Product Experiment: Network Pilot Launch Pack v1.0

Status: Product Experiment — Operational Launch Package, Manual Validation Only. This document is not a Product Decision, not an ADR, and not an implementation plan. It is a practical package helping a human Coordinator start the first manual Network Pilot cases — recruiting participants, explaining the experiment, obtaining voluntary participation, running first cases, and recording learnings — built only from [PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md), [PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md), [PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md), [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md), [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md), and [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), without reinterpreting or expanding beyond them. No production code, database, API, event contract, ADR, domain model, or implementation task is created by this document.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by one of the six source documents above, cited inline.
- **[DERIVED]** — a practical script, template, or guide item this document adds, drawn directly from those sources' own roles, workflow, and boundaries, never introducing a new mechanism.
- **[HYPOTHESIS]** — a candidate practice requiring real-world validation; most of this package is HYPOTHESIS in effect, since it exists to help run an experiment, not assert a conclusion.
- **[OPEN]** — not decided by any of the six source documents, and not decided by this one; carried forward explicitly.

Section-level tags are given once per section to keep this document usable as a practical package.

## Section 1 — Pilot Overview

**[RATIFIED, reused directly.]**

**Why the pilot exists.** To observe, in a small, real, manual setting, whether independent drivers holding genuine Personal Client Relationships will actually share unmet demand through an authorized fallback, and whether they perceive enough recurring value to indicate future willingness to pay (PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 1).

**What is being tested.** H1 — will drivers actually initiate and accept a manual, authorized network fallback when personally unable to fulfill a request? H2 — do drivers perceive enough recurring value from this coordination to justify future payment? Neither is considered validated in advance (PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 5).

**What is not being tested.** Any specific Opportunity, Eligibility, Fair Opportunity, or Fulfillment Authority mechanism (all remain open, resolved by manual substitutes only); any commercial model, price, or billing mechanism; whether automation would reproduce the same behavior as this manual process; anything statistically generalizable from a small cohort (PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 1).

**Explicitly stated:**

- **This is not production.** PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 2: "MVP is validation, not production architecture."
- **This is not automated dispatch.** PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 2: Driver B is selected by a human Coordinator's own judgment, never by Dispatch, an algorithm, a ranking, or a score.
- **This is not a marketplace launch.** No commission, no payment custody, no commercial terms of any kind are introduced (PRODUCT_BASELINE_V2.md Sections 13–14); payment stays direct between passenger and driver throughout.

## Section 2 — Participant Recruitment Guide

**[DERIVED]**, drawn from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 2. **No mandatory cohort size is defined** — PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 6 states the 10–30 range explicitly as a candidate, not a hard requirement, and this document does not convert it into one.

**Driver A characteristics (candidate criteria, not mandatory):**

- Independent driver.
- Already holds at least one genuine, pre-existing Personal Client Relationship — not one created for the pilot.
- Willing to participate manually and report outcomes honestly.

**Driver B characteristics (candidate criteria, not mandatory):**

- Independent driver.
- Willing to be manually contacted about occasional fallback opportunities.
- No prior relationship with the passenger required or expected.

**Passenger participation approach.** Passengers are never separately recruited — each arrives only as the other side of a participating Driver A's own existing relationship. The approach is to inform them, through Driver A, that a fallback option may occasionally be offered, and to let them decide, case by case, whether to use it (Section 3, Passenger script).

## Section 3 — First Contact Scripts

**[HYPOTHESIS]** — draft conversation guides only, not a fixed script to be read verbatim. **No script below makes an unsupported promise** — none guarantees volume, income, a specific driver, or future availability, consistent with PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 2's own "what must not be promised."

**Driver A**

> "We're testing a simple idea: when you can't personally take a regular client's request, would you want the option to have another driver from a small trusted group cover it — only if your client agrees? This is a manual experiment, not a finished product — I'll be the one coordinating by hand, there's no app or automatic matching. Your client relationship stays yours; this doesn't transfer or share ownership of it. Payment for any covered trip still happens directly between the passenger and whichever driver takes it — PIOS doesn't touch the money. And this is entirely voluntary: you can try it once, use it regularly, or opt out at any time, no explanation needed."

**Driver B**

> "Occasionally, another driver in this small group may not be able to take one of their regular client's requests, and I'll ask if you're willing to cover it. You'll always hear the details first, and it's completely your call — accept or decline, every single time, with zero obligation and no ranking or scoring involved in how I ask. Payment works exactly like any other ride: direct between you and the passenger."

**Passenger**

> "Your driver may occasionally be unavailable for a specific request. We're testing whether, with your permission, another trusted driver could cover it instead — only if you say yes each time. Your relationship with your regular driver doesn't change either way; this is just a backup option, entirely your choice to use or decline."

## Section 4 — Participation Confirmation

**[DERIVED]**, checklist form, before any participant is considered enrolled:

- [ ] Understands the pilot's purpose (Section 1) — what is and is not being tested.
- [ ] Understands coordination is entirely manual — a person, not software, arranges every case.
- [ ] Understands payment stays direct between passenger and the fulfilling driver.
- [ ] Understands participation is voluntary and may stop at any time without consequence.
- [ ] Understands there is no guaranteed volume — no promise of a minimum number of requests, opportunities, or income.

## Section 5 — First Case Runbook

**[DERIVED]**, condensed from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 4 and PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 3:

**Before the case**

- [ ] Confirm participants (Driver A, candidate Driver B pool, relevant passenger) have completed Section 4.
- [ ] Confirm the Coordinator is reachable and ready to log the case.

**During the case**

- [ ] Authorization — Driver A asks the passenger; record the outcome even if declined.
- [ ] Candidate contact — Coordinator manually identifies and contacts a Driver B.
- [ ] Acceptance/decline — record the candidate's answer; try another candidate manually if declined.
- [ ] Completion — confirm whether the trip actually occurred.

**After the case**

- [ ] Log update — complete the case log entry (fields per PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 4).
- [ ] Feedback collection — note any comments volunteered; use Section 6 below for a fuller review where possible.

## Section 6 — Case Review Template

**[DERIVED]** — a manual review template only; **no analytics schema, database, or software artifact is created.**

- **Case summary** — one or two sentences: what was requested, who was involved, what happened.
- **What worked** — anything that went smoothly.
- **What failed** — anything that broke down (declined authorization, no driver accepted, logistics failure).
- **Participant reactions** — how Driver A, Driver B, and the passenger each seemed to feel about it.
- **Unexpected behavior** — anything nobody anticipated.
- **Questions raised** — anything the case surfaced that the pilot documents don't already answer.
- **Possible product implications** — a brief, non-binding note on whether this case suggests anything relevant to Opportunity, Eligibility, Fair Opportunity mechanism, Fulfillment Authority mechanism, or the commercial model — recorded as an observation only, not a conclusion.

## Section 7 — Weekly Pilot Review

**[RATIFIED, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 6.]** Suggested agenda:

- Cases reviewed this week.
- Repeated friction points across cases.
- Participant feedback (from Case Review Templates and any direct conversations).
- Assumptions challenged — anything from the source documents that didn't hold up in practice.
- Decisions requiring product review — anything that looks like it needs a Product Owner decision rather than continued manual observation.

## Section 8 — Safety Rules

**[RATIFIED, reused directly — preserved without exception]:**

- No hidden priority.
- No ranking.
- No scoring.
- No AI selection.
- No payment custody.
- No commission.
- No client transfer promises.
- No automatic obligations — a driver is never bound until they themselves accept.

Every script (Section 3), checklist (Sections 4–5), and template (Section 6) in this package has been checked against this list.

## Section 9 — Exit Conditions

**[DERIVED]**, qualitative only — **no numeric threshold is defined**, consistent with PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 10 and PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 8, both of which already declined to invent one without evidence:

- **Continue.** Evidence of recurring value — participants keep choosing to use and re-use the fallback mechanism, and express genuine interest in future value.
- **Modify.** Value exists but assumptions fail — for example, drivers want the mechanism but the manual process itself (authorization, candidate contact, or logging) creates friction that a different operational design might resolve.
- **Stop.** Participants do not perceive value — drivers consistently decline to engage, or show no interest in the coordination once tried.

Which condition applies is a Product Owner interpretation of the evidence gathered through Sections 5–7, not an automatic determination.

## Files Changed

`docs/PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md` (new) is the only content file this document creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set by every preceding document in this chain. No ADR, code, database, event, or API is created or modified.

## Open Questions

Unchanged from the six source documents — this package narrows none of them further: who fills the Coordinator role; exact cohort size and duration; whether any numeric threshold should ever govern Section 9's exit conditions; whether pre-authorized (standing) fallback authorization would behave differently than the per-instance manual authorization this package scripts for; and every open item in PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 8 (Opportunity, Eligibility, Fair Opportunity mechanism, Fulfillment Authority mechanism, Commercial model).

## Conflicts

**None found.** This package is a practical, script-level restatement of the roles, workflow, data format, and boundaries already established by PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md, PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md, and PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md; nothing here contradicts, narrows, or reinterprets any of them, PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md, PRODUCT_BASELINE_V2.md, or PROJECT_CONSTITUTION.md.

---

Where this document finds no evidence, it states so explicitly rather than filling the gap. This package does not override PROJECT_CONSTITUTION.md, any existing ADR, or any Product Decision; using it and interpreting its results requires a further Product Owner decision before any of Section 9's outcomes may be acted on by a lower-authority document or implementation task.
