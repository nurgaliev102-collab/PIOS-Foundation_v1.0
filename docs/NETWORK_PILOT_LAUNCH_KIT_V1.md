# Network Pilot Launch Kit v1.0

Status: Operational Preparation for Market Validation — Manual Only. This document is **not** a Product Decision, **not** an ADR, and **not** an implementation plan. It consolidates [PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md), [PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md), and [PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md) into one practical, run-it-from-this-document kit, now that [PIOS_VERTICAL_SLICE_VERIFICATION_V1.md](PIOS_VERTICAL_SLICE_VERIFICATION_V1.md) has confirmed the technical foundation is ready and — critically — that this readiness was never the pilot's own gate, since the pilot itself uses no software at all. This document introduces no pilot mechanic beyond what those three documents (and [PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md), whose scripts it reuses directly) already established; it narrows nothing and reinterprets nothing. No production code, database, API, event contract, ADR, or Product Decision is created or modified.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly implied by an approved document, cited inline.
- **[DERIVED]** — a consolidation or practical restatement of already-ratified material, introducing no new mechanic.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; most of this kit's own content is HYPOTHESIS-graded, since it exists to observe, not assert.
- **[OPEN]** — not decided by any approved document, and not decided here; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is treated as validated, required, or permanent anywhere in this document.

---

## Part 1 — Pilot Objective

**What we are trying to learn.** Whether, in a small, real, manual setting, independent drivers holding genuine Personal Client Relationships will actually:

- **H1.** Initiate and accept an authorized network fallback when they personally cannot fulfill a request — i.e., drivers may want a trusted fallback when they cannot fulfill personal-client demand.
- **H2.** Perceive enough recurring value from that coordination to show willingness to pay for it in the future — i.e., drivers may show willingness to participate in a network model.

**[RATIFIED, reused verbatim]** from PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 1. Neither hypothesis is claimed true by running this pilot — only observed. This kit does not assert H1 or H2; it exists to find out.

**Validated by this pilot:**

- Whether drivers actually offer, request, and accept a manual fallback when unable to personally fulfill a request.
- Whether passengers actually authorize a different driver for a specific request, and how they feel about it.
- Whether drivers, after experiencing it, express or reveal genuine interest in paying for continued access to this coordination.
- Directional, qualitative, small-cohort signals only — not a statistically rigorous or generalizable conclusion.

**Not validated by this pilot:**

- The eventual production mechanism for Opportunity, Eligibility, Fair Opportunity Policy, or Fulfillment Authority — each remains exactly as open as its own source Product Decision left it.
- The exact commercial model, price point, or billing mechanism.
- Whether an automated system would produce the same driver behavior as this manual one.
- Passenger willingness to pay (H2 concerns driver willingness only).
- Whether the software vertical slice itself works — that is already independently confirmed (PIOS_VERTICAL_SLICE_VERIFICATION_V1.md), and this pilot uses none of it (Part 4).

---

## Part 2 — Participant Profile

**[DERIVED, consolidating PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 2 and PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 1]**

**Driver criteria (include):**

- Independent driver, operating their own business — never treated as platform-controlled supply.
- Holds at least one genuine, pre-existing Personal Client Relationship (not one created for the pilot).
- Regular, ongoing order flow with that relationship — enough that an inability-to-fulfill moment is realistically likely to occur during the pilot.
- Experience handling customers directly — comfortable having the authorization conversation with their own client.
- Willing to discuss failures honestly — declines, discomfort, and negative experiences are exactly as valuable as successes to this pilot's own purpose.

**Exclude:**

- Anyone who cannot or will not give honest feedback, including negative feedback.
- Anyone expecting guaranteed income, volume, or a minimum number of opportunities from participating.
- Anyone treating the pilot as a source of free leads or new clients, rather than as a fallback for their own existing relationships.

**Recommended cohort size.** Roughly 10–30 drivers **[RATIFIED, reused]** (PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 6) — explicitly not a hard requirement; the pilot may run with fewer or more.

**Recruitment approach.** Direct, personal outreach to drivers already known (through existing relationships, driver networks, or referrals) to hold genuine Personal Client Relationships — never a public advertisement or open sign-up, since the pilot depends on pre-existing relationships that cannot be manufactured for it. Passengers are never separately recruited; each arises only as the other side of a participating driver's own existing relationship, informed through that driver.

**Onboarding conversation.** Before any participant is considered enrolled, confirm they understand and accept **[DERIVED, from PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md Section 4]**:

- The pilot's purpose (Part 1) — what is and is not being tested.
- Coordination is entirely manual — a person, not software, arranges every case.
- Payment stays direct between the passenger and whichever driver fulfills the trip.
- Participation is voluntary and may stop at any time, for any or no reason, without consequence.
- There is no guaranteed volume, income, or minimum number of opportunities.

---

## Part 3 — Pilot Roles

**[RATIFIED/DERIVED, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 3]**

**Driver A** (the relationship-holding driver who cannot personally fulfill a specific request):

- Receives the customer's request directly, exactly as they do today.
- Determines they cannot personally fulfill it.
- Informs the passenger a fallback may be possible and requests authorization directly — never assumes or defaults to it.
- Notifies the Coordinator once (and only once) authorization is given.
- Reports the case outcome.

**Driver B** (the candidate driver the Coordinator identifies):

- Receives the fallback opportunity, described in plain terms, one candidate at a time.
- Accepts or declines entirely of their own accord — no obligation exists until acceptance is given.
- Completes the service if accepted; payment is direct with the passenger.
- Reports completion.

**Passenger** (the requester):

- Provides authorization where applicable — the one essential act only they may perform; declining is a normal, acceptable outcome.
- Gives feedback, optionally, on the experience.

**Coordinator** (the human process role introduced for this pilot, exercised entirely outside PIOS software):

- Maintains the pilot process: receives notice of an authorized fallback need, identifies candidate Driver B(s) using their own manual judgment and personal knowledge only, contacts them, and records every case.
- Does **not** act as an algorithm — never ranks, scores, or applies any automated rule to select a candidate.
- Does **not** make permanent allocation rules — every selection is a one-off, manual judgment call for that specific case, never a standing policy binding future cases.
- Never overrides a passenger's authorization decision or a driver's acceptance decision — both remain exactly as autonomous as the underlying Product Decisions already establish.

Who specifically fills the Coordinator role remains **[OPEN]** — a participating driver, an external pilot operator, or another arrangement, per PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md Section 3; this kit states the criteria (known and trusted by the cohort, understands these boundaries, willing to maintain the case log) without resolving who, specifically, fills it.

---

## Part 4 — Pilot Rules

**[RATIFIED, reused directly]** — every rule below is already established by upstream Product Decisions and the Constitution; none is invented here, and every rule is a **preservation**, not a new constraint:

- **Customer relationship protection.** The Personal Client Relationship remains Driver A's own throughout, regardless of how many times Driver B fulfills on their behalf — a single fallback trip never transfers or shares ownership of it.
- **No hidden transfer of customers.** Driver B never gains a claim on the passenger's relationship by fulfilling one request; nothing about the pilot process creates or implies one.
- **No platform ownership of customers.** PIOS (and the Coordinator, acting on PIOS's behalf in this pilot) never takes custody of, or asserts any right over, either party's relationship.
- **Direct payment between customer and executor.** Payment for any completed trip is strictly between the passenger and whichever driver fulfills it; PIOS never holds funds, at any point, for any case.
- **No ranking.** The Coordinator never produces or consults a ranked list of candidate drivers.
- **No scoring.** No numeric or qualitative score is ever assigned to a driver, request, or match.
- **No AI selection.** No algorithm, model, or automated tool selects, suggests, or filters candidates at any point — every choice is the Coordinator's own manual judgment.

Every role guide in Part 3 and every workflow step in Part 6 has been checked against this list; none introduces any of the above.

---

## Part 5 — Recruitment Script

**[HYPOTHESIS, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md Section 3]** — a draft conversation guide, not a fixed script to be read verbatim. Answers why we are talking, what problem is being studied, what participation means, and what it does **not** promise — and deliberately avoids selling an imaginary finished product.

**Initial conversation with Driver A:**

> "We're testing a simple idea: when you can't personally take a regular client's request, would you want the option to have another driver from a small trusted group cover it — only if your client agrees? This is a manual experiment, not a finished product — I'll be the one coordinating by hand, there's no app or automatic matching. Your client relationship stays yours; this doesn't transfer or share ownership of it. Payment for any covered trip still happens directly between the passenger and whichever driver takes it — PIOS doesn't touch the money. And this is entirely voluntary: you can try it once, use it regularly, or opt out at any time, no explanation needed."

**Initial conversation with Driver B:**

> "Occasionally, another driver in this small group may not be able to take one of their regular client's requests, and I'll ask if you're willing to cover it. You'll always hear the details first, and it's completely your call — accept or decline, every single time, with zero obligation and no ranking or scoring involved in how I ask. Payment works exactly like any other ride: direct between you and the passenger."

**Initial conversation with the passenger** (delivered by Driver A, not the Coordinator, consistent with Part 3's own role split):

> "Your driver may occasionally be unavailable for a specific request. We're testing whether, with your permission, another trusted driver could cover it instead — only if you say yes each time. Your relationship with your regular driver doesn't change either way; this is just a backup option, entirely your choice to use or decline."

**What every script above deliberately never promises:** a guaranteed volume of opportunities, a minimum income, a specific driver, or that fallback will always be available in the future. This is an experiment, not a finished, guaranteed service.

---

## Part 6 — Interview Framework

**[HYPOTHESIS, consolidated from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 6 and PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 5]** — open-ended prompts throughout, never a scored survey; no answer is assumed, expected, or scored in advance.

**Before the pilot (driver onboarding conversation, beyond Part 2's confirmation items):**

- "Walk me through your current workflow when a regular client contacts you."
- "Have you ever had to turn down or lose an order from a regular client because you couldn't personally take it? What happened?"
- "Have you ever lost a client relationship because you couldn't be there for them at the right moment?"
- "What trust concerns, if any, would you have about another driver serving your client?"
- "What do you currently do, if anything, when you can't personally fulfill a request?"

**During the pilot (after each case, addressed to whichever participants were involved):**

- "What happened in this case, step by step?"
- "Why did you decide to accept — or decline — the fallback, at whichever step you were involved?"
- "What felt valuable about this experience?"
- "What felt risky or uncomfortable, if anything?"

**After the pilot:**

- "Would you use a service like this going forward?"
- "Under what conditions would you want to use it — regularly, occasionally, only in emergencies?"
- "What would prevent you from adopting this, if anything?"
- **Willingness-to-pay probe (drivers only, framed as open, not leading):** "If PIOS offered ongoing access to this kind of coordination, would that interest you? Roughly how much, if anything, would you consider paying?"

---

## Part 7 — Case Log

**[RATIFIED, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 5]** — a manual paper or spreadsheet template only. **Not a database.** No schema, table, or software artifact is created or implied by this section.

| Field | Description |
| --- | --- |
| Date | When the case began |
| Driver A | Which cohort driver |
| Passenger reference | Informal reference only (e.g., "Driver A's regular client #2") — no passenger identity record beyond what the Coordinator already needs to run the case |
| Reason for inability | Free text — why Driver A could not personally fulfill |
| Authorization result | Yes / No, and reason if No |
| Driver B contacted | Which cohort driver(s), and how many candidates were contacted |
| Acceptance result | Yes / No, and reason if No |
| Completion | Yes / No |
| Payment confirmation | Direct payment occurred: Yes / No |
| Comments | Free text — anything volunteered by any participant |
| Lessons learned | Free text — the Coordinator's own observation, added after the case closes |

Every case reaching the authorization step is logged, including declines — a "no fallback pursued" or "no driver accepted" outcome is a complete, valuable case for this log, never a discarded one.

---

## Part 8 — Success Signals

**[HYPOTHESIS]**, qualitative evidence patterns only. **No numeric threshold is defined** — no approved document provides evidence for any specific number, and setting one now would be fabrication, not evidence.

**Positive signals (favoring H1/H2):**

- Drivers voluntarily use fallback — offering or requesting it themselves when unable to fulfill, rather than simply declining the request or quietly working around the pilot.
- Customers (passengers) accept the fallback offered, rather than consistently declining it.
- Drivers perceive that their client relationship was protected, not threatened, by using fallback once.
- Repeated usage interest — the same driver uses or asks about fallback more than once over the pilot's duration.

**Negative signals (disfavoring H1/H2):**

- Drivers avoid sharing their customers even when personally unable to fulfill — declining to engage the pilot process at all.
- Customers refuse the fallback offered, preferring to wait or seek another means entirely.
- Trust barriers dominate the feedback — discomfort, suspicion, or reluctance overshadows any perceived benefit.
- The value of the coordination is unclear or unconvincing to participants, even where used.

These are categories for interpreting observed evidence, not a pass/fail gate; a Product Owner interprets the actual pattern observed.

---

## Part 9 — Stop Conditions

**[DERIVED]**, consolidating the safety boundaries already established (PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md Section 7; PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md Section 8) into explicit pause conditions:

- **Customer relationship risk.** Any sign that a passenger's relationship with Driver A is being weakened, confused, or put at risk by the pilot process — pause immediately and review with the affected driver before continuing.
- **Unclear ownership expectations.** Any participant (driver or passenger) expressing uncertainty about whether the relationship, once fallback is used, still belongs to Driver A — pause and re-clarify before any further case involving that participant.
- **Participants misunderstand the purpose.** Any sign a participant believes this is a finished product, a guaranteed income source, or a lead-generation channel rather than an experiment — pause their participation and re-run onboarding (Part 2, Part 5) before continuing.
- **Coordinator creates hidden dispatch behavior.** Any sign the Coordinator is applying a repeated rule, an informal ranking, a preference pattern, or anything resembling automated or algorithmic selection, rather than genuine per-case manual judgment — pause the pilot's Coordinator function and review Part 3/Part 4 with them before resuming.

A pause is a deliberate operational choice, not a failure of the pilot itself — it protects the very relationships and boundaries the pilot exists to observe without disturbing.

---

## Part 10 — Decision After Pilot

**[DERIVED, reused directly from PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md Section 10]** — three qualitative outcomes, a Product Owner interpretation, not an automatic calculation. **No implementation roadmap is defined here for any outcome.**

- **Continue.** The observed pattern favors both H1 and H2 — drivers actually pursue and accept fallback, and express genuine interest in future payment. Evidence supports further development; what that development looks like is a separate, future decision, not defined by this kit.
- **Modify.** The pattern is mixed or inconclusive — for example, strong fallback participation but no signal on willingness to pay, or the reverse. The problem may exist but the mechanism (cohort, process, or a specific rule in this kit) needs adjustment before continuing observation.
- **Stop.** The pattern clearly disfavors H1 or H2 — drivers consistently decline to participate, or show no interest in future payment. The underlying product premise is not confirmed by this evidence; the next step is a Product Owner reconsideration of the product core itself, not a larger or automated pilot.

---

## Part 11 — First 30 Days Plan

**[DERIVED]** — an operational sequence, entirely manual, introducing no new mechanic beyond Parts 2–7 above:

**Week 1 — Preparation and recruitment.**

- Identify and confirm the Coordinator (Part 3); brief them fully on Part 4's rules and Part 9's stop conditions.
- Identify candidate Driver A/Driver B participants from existing, genuine relationships (Part 2) — not a public call for participants.
- Prepare the case log (Part 7) in whatever simple format (notebook or spreadsheet) the Coordinator will actually use.

**Week 2 — First participant onboarding.**

- Run the recruitment conversation (Part 5) with each candidate driver individually.
- Complete the onboarding confirmation (Part 2) for every enrolled driver before any case begins.
- Confirm each driver understands how to reach the Coordinator when an authorized fallback need arises.

**Week 3 — Run cases and interviews.**

- Begin accepting real cases as they naturally arise (Part 3's workflow) — no case is manufactured or simulated for the pilot's sake.
- Log every case reaching the authorization step (Part 7), including declines.
- Conduct the "during the pilot" interview (Part 6) after each case where a fuller conversation is possible.

**Week 4 — Review evidence.**

- Compile the case log and review it against Part 8's success signals.
- Conduct "after the pilot" interviews (Part 6) with participating drivers, including the willingness-to-pay probe.
- Apply Part 10's decision categories to what was actually observed — a Product Owner interpretation, not an automatic determination.

This 30-day window is a starting operational cadence, not a fixed pilot duration — the cohort-size and duration guidance in Part 2 remains a candidate range, not a hard deadline; the pilot may continue past 30 days if the evidence gathered by then is not yet sufficient for a Part 10 decision.

---

## Files Changed (This Task)

`docs/NETWORK_PILOT_LAUNCH_KIT_V1.md` (new) is the only content file this task creates. `docs/README.md` receives one minimal traceability pointer. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified.

---

Where this kit finds no evidence, it states so explicitly rather than filling the gap. This document does not override PROJECT_CONSTITUTION.md, any ADR, or any Product Decision; running the pilot and interpreting its results requires a further Product Owner interpretation before any of Part 10's outcomes may be acted on by a lower-authority document or implementation task.
