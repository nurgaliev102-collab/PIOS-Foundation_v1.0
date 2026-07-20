# PIOS Product Decision: Fair Opportunity Policy v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document defines the product principles governing fair access to shared network opportunities. It is not an ADR, not an architecture document, and designs no algorithm, ranking system, scoring mechanism, reciprocity engine, or reputation system. It resolves the specific product question [PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md](PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md) Section 8 and [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md) Section 19 Item 5 named as the next legitimate step, building directly on [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md) (the authority gate), [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md) (the Opportunity/Commitment boundary), and [PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md](PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md) (the eligibility gate this policy operates downstream of).

Derived from PROJECT_CONSTITUTION.md, PRODUCT_BASELINE_V2.md, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md, PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md, PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md, PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, ADR-034, PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md, and PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md. It introduces no new aggregate, event, command, or actor, and selects no metric, formula, or algorithm — it only classifies which fairness principles are already supported and which remain candidates.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, cited inline.
- **[DERIVED]** — a decision this document actually makes, reasoning from already-ratified principles to a question those sources do not themselves literally answer.
- **[HYPOTHESIS]** — a candidate requiring further validation; recorded, not adopted.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is promoted to a requirement anywhere in this document.

## Central Finding, Stated First

PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5 already resolved the single most load-bearing question this document would otherwise have to re-derive: *"fairness for PIOS is a property of how the decision is made and whether it can be explained, not yet a property of what outcome distribution results."* **[RATIFIED — reused directly, not redecided.]** Every distributional or merit-based candidate this document evaluates (Sections 3–5) is checked against that already-ratified boundary, not against this document's own preference.

## 1. What Does Fairness Mean for PIOS Dispatch?

**[RATIFIED, reused directly.]** Fairness is procedural, not distributional: it concerns how the assignment decision is made and whether it can be explained to the people it affects (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5; Constitution Section 3, Fair Dispatch, evaluated "not merely for efficiency"). No approved document defines fairness through any specific outcome — equal income, equal ride count, or any other distributional target is not what "fair" currently means for PIOS. This document does not redefine fairness; it applies the already-ratified definition to the specific question of shared-opportunity access.

## 2. Does Fairness Apply Only to NETWORK Scope?

**[DERIVED].** Fair Dispatch itself (Constitution Section 3) is a platform-wide principle, not textually scoped to any one fulfillment scope. However, **Fair Opportunity Policy specifically — the mechanism this document charters — has work to do only where a shared candidate pool exists.** PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3 already established that DIRECT scope names one specific driver and never reaches Dispatch's assignment decision at all; there is no pool to be fair *among* in DIRECT scope, only a single named driver's own decision whether to fulfill. REFERRED scope remains **[OPEN]** as a concept (Fulfillment Authority and Scope Section 3) and is not extended by this document.

**Conclusion:** the Fair Dispatch *principle* remains platform-wide; Fair Opportunity Policy as a concrete mechanism applies only to NETWORK-scoped requests, since that is the only scope in which a genuine, shared candidate pool currently exists to allocate fairly among.

## 3. Equal Opportunity, Balanced Participation, Contribution-Based Priority, Service Quality Priority

Reusing, and extending only for clarity of distinction, the four candidate dimensions PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5 already evaluated:

- **Equal opportunity** — every eligible driver has a genuine chance to receive a given shared opportunity. **[DERIVED]** — a procedural guarantee, evaluated at the moment of one opportunity; consistent with Fair Dispatch's "not merely for efficiency" wording and Platform Neutrality, but not itself stated as a requirement anywhere.
- **Balanced participation** — the distribution of shared opportunities does not concentrate on a few drivers over time. **[DERIVED]** — a statistical/distributional target evaluated across many opportunities, not any single one; same evidentiary grounding as equal opportunity, same caution: neither is itself stated as a requirement, only as a consistent extension.
- **Contribution-based priority** — drivers who do more receive more. **[HYPOTHESIS]** — no approved document ties assignment to any notion of contribution or merit; this pulls toward an incentive/efficiency model the Constitution's own wording subordinates to fairness (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 1).
- **Service quality priority** — assignment favoring higher-performing drivers. **[HYPOTHESIS]** — no rating, performance, or quality concept exists anywhere in DOMAIN_MODEL.md for Driver Management to even expose; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 2 already classifies Reputation as **[OPEN]** and, separately, forbidden as a current input (ADR-034 Part 2).

**The material difference between the two pairs:** equal opportunity and balanced participation describe *process and aggregate outcome neutrality* — nobody is structurally excluded or systematically favored — without introducing any new information about a driver beyond what already exists (identity, availability, eligibility). Contribution-based and service-quality priority are qualitatively different: both require a new, currently nonexistent measurement of a driver (contribution tracking, or a rating/performance signal) and both actively favor some eligible drivers over others on that measured basis — exactly the kind of ranking Eligibility was defined to exclude from itself (PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 1, "Eligibility is not ranking").

## 4. Which Principles Are Currently Supported by Existing Authority?

**[RATIFIED]:** Procedural fairness — how the decision is made and whether it is explainable (Section 1). **[DERIVED, consistent extensions, not themselves ratified as requirements]:** Equal opportunity; balanced participation. **Not supported by any existing authority — [HYPOTHESIS], not promoted here:** contribution-based priority; service quality priority. No document — including this one — elevates either of the latter two to a requirement.

## 5. Which Candidate Metrics Require Validation?

**[HYPOTHESIS], all of the following:** contribution-based priority and service quality priority in full (Section 3); and, notably, **any concrete operationalization of equal opportunity or balanced participation** — this document establishes only their qualitative direction as consistent extensions (Section 3–4), not any numeric threshold, formula, or measurement window. "Genuine chance" and "does not concentrate" remain undefined in any measurable sense; specifying either is future work this document does not perform.

## 6. Does Personal Client Relationship Influence Opportunity Priority?

**[OPEN] — unchanged, not resolved here.** This is the third, distinct PCR question this decision chain has now identified, each independently open: (a) whether a personal-client-directed order is routed through Dispatch's assignment decision at all — open since PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 4 and PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 7; (b) whether PCR affects the Eligibility gate — open since PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 6; and (c) whether PCR affects *priority within* an already-eligible NETWORK pool — the question this section addresses, left exactly as open as ADR-034 Part 2 (PCR as an unratified "future candidate input") and PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md Part 8 already left it. This document does not conflate (a), (b), and (c), and resolves none of them.

## 7. Does Reciprocity Influence Priority?

**[OPEN], and already excluded from the Fundamental Laws.** Constitution Section 18: "Reciprocity is deliberately not included as a law... carried instead as an open question." ADR-034 Part 4 names "reciprocal balancing" only as one of several unapproved candidate strategies — "none is approved, previewed, or ranked." This document reuses that status directly: reciprocity may not currently influence priority in any form, and no Reciprocity Engine is designed here, consistent with this task's own exclusion.

## 8. Can Payment or Subscription Ever Influence Opportunity Priority?

**[DERIVED — a standing constraint, reused and extended for the third time in this decision chain].** No. PRODUCT_BASELINE_V2.md Sections 11 and 13 already established that "payment to PIOS should not secretly purchase higher priority in fair shared-network opportunity allocation"; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 7 already extended this to the Eligibility gate. This document extends it a third time, explicitly, to whatever ranking or ordering mechanism a future Fair Opportunity Policy implementation eventually adopts. This constraint holds **independent of which commercial model is eventually ratified** (the model itself remains **[OPEN]**, Constitution Section 19) — whatever it turns out to be, it may never purchase priority within a shared opportunity pool.

## 9. What Transparency and Explainability Requirements Exist?

**[RATIFIED], restated not reinvented.** Transparency and No Hidden Algorithmic Decisions (Constitution Sections 3, 18); "No hidden dispatcher preference" (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 3); the Core Problem statement's own "fair, explainable, and transparent" (PRODUCT_FOUNDATION.md Section 4). Any future Fair Opportunity Policy must be explainable in principle to the drivers it affects — a driver could, in principle, be told the reason they did or did not receive a given opportunity. This document does not design a specific disclosure mechanism, timing, or channel; it states the requirement as already ratified and binding on whatever policy is eventually built.

## 10. Minimum Pilot-Level Fair Opportunity Policy Candidate

**[HYPOTHESIS] — one candidate recorded, not selected.** ADR-034 Part 4 already names Simple FIFO (submission order) as one candidate future strategy, alongside several others, with none "approved, previewed, or ranked." FIFO is the simplest candidate consistent with every ratified principle above: it requires no new driver measurement (no contribution tracking, no rating), is trivially explainable (submission order is externally verifiable), and satisfies Equal Opportunity's qualitative direction (Section 3) without requiring Balanced Participation to be separately measured. This document records it as one legitimate pilot candidate; it does not adopt, rank, or preview it over any other candidate ADR-034 Part 4 already named.

## 11. Candidate Mechanisms Evaluated, None Chosen

Per this task's own instruction, each of the following is evaluated only as a possible future mechanism — none is chosen, ranked, previewed, or implemented here:

- **Round Robin.** **[HYPOTHESIS]** — matches ADR-034 Part 4's "fair queue (some notion of rotation across drivers)." Requires no new driver measurement beyond Eligibility (Section 4 of PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md), and is a plausible operationalization of Balanced Participation (Section 3), but "rotation" itself is undefined in any measurable sense — same caution as Section 5.
- **Ranking system.** **[HYPOTHESIS]** — the general mechanism Contribution-Based Priority and Service Quality Priority (Section 3) would both require; not itself a distinct dimension, but the implementation shape either would need. Requires a new driver measurement no ratified document establishes.
- **Scoring system.** **[HYPOTHESIS]** — a specific technique for implementing a ranking system; carries the same evidentiary status. No formula, weighting, or scoring model is evaluated, previewed, or implied.
- **AI selection.** **[HYPOTHESIS]** — matches ADR-034 Part 4's "machine-learning-based selection," the most speculative candidate named there. Would require every other candidate input (contribution, service quality, or any other signal) to already be ratified before it could even be trained against; presupposes decisions this document explicitly does not make (Sections 4–5).
- **Reciprocity engine.** **[OPEN]** — unchanged from Section 7; matches ADR-034 Part 4's "reciprocal balancing," one of several unapproved candidates, "none... approved, previewed, or ranked."
- **Reputation system.** **[OPEN]** — unchanged from Section 3 and PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 2; matches ADR-034 Part 4's "reputation-based selection," explicitly forbidden as a current input (ADR-034 Part 2).

**Conclusion, restated:** all six are legitimate candidates for a *future* Assignment Policy implementation (ADR-034 Part 4), evaluated here only for how they would relate to the principles this document already established (Sections 1–9) — none is selected, designed, ranked, or implemented by this document, consistent with this task's own instruction.

## Explicit Preservations

- **No hidden dispatcher preference.** **[RATIFIED]** — PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 3.
- **No opaque algorithmic decisions.** **[RATIFIED]** — No Hidden Algorithmic Decisions (Constitution Sections 3, 18).
- **No pay-to-win priority.** **[DERIVED]** — Section 8 above.
- **No automatic client relationship ownership.** **[RATIFIED]** — Relationship Protection (Constitution Sections 3, 18), unchanged; nothing in this document's treatment of PCR-as-priority-candidate (Section 6) touches ownership, only the separate, still-open question of influence.
- **Fairness is access fairness, not equal outcomes.** **[RATIFIED]** — PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5's own conclusion, reused directly (Central Finding, above).

## Explicitly Out of Scope

Per this task's own instruction, none of the following is designed here:

- **Algorithm** — remains excluded from every level of documentation (ADR-002; ADR-034).
- **Round Robin** — evaluated only as a candidate mechanism (Section 11); not chosen or implemented.
- **Ranking system** — remains undesigned; Section 3 only classifies candidate dimensions, none selected.
- **Scoring system** — no scoring concept is introduced; Section 5's metrics remain unvalidated hypotheses.
- **AI selection** — evaluated only as a candidate mechanism (Section 11); not chosen, designed, or implied as a direction.
- **Reciprocity engine** — remains excluded (Section 7).
- **Reputation system** — remains **[OPEN]**, unchanged from PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 2.

## Files Changed

`docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` is the only content file this decision touches — originally created in an earlier pass of this same v1.0 decision, extended here (Section 11) to explicitly evaluate Round Robin and AI selection by name alongside the mechanisms already covered, per this task's own restated instruction. `docs/README.md` already carries its traceability pointer from that earlier pass; no further change to it is required. No ADR, code, database, event, or API is created or modified.

## Unresolved Decisions

1. Whether Personal Client Relationship influences NETWORK-scope priority (Section 6) — left open, distinguished from two other, separately open PCR questions.
2. Whether reciprocity ever becomes a ratified input to any Assignment Policy (Section 7) — unchanged.
3. Which candidate metric (if any) is eventually chosen, and its concrete operationalization (Sections 3–5) — none selected here.
4. Which MVP Fair Opportunity Policy candidate is actually adopted for a pilot (Section 10) — FIFO recorded as one legitimate candidate only.
5. The specific transparency/explainability disclosure mechanism (Section 9) — requirement is ratified; mechanism is not designed.

## Conflicts

**None found.** Checked against and consistent with: ADR-034 (Part 2's forbidden-inputs list and Part 4's unranked candidate-strategies list, both reused directly, not reopened); PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md (Section 5's fairness definition and Section 8's open questions, reused and extended, never contradicted); PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md (Part 8's open questions remain exactly as open); PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md and PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md (this document operates strictly downstream of both, redeciding neither authority nor commitment semantics); PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md (this document's ranking/priority scope is kept strictly distinct from that document's binary Eligibility gate, per Section 3's own closing observation).

## Impact Analysis

**Documents:** No existing document modified beyond the one minimal `docs/README.md` line. DOMAIN_MODEL.md, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, every ADR, and every prior Product Decision remain exactly as they were.

**Code:** None. No production code, migration, API, or event contract is touched or referenced for modification.

**Future work enabled, not performed:** A future ADR could define the architectural shape of a Fair Opportunity Policy implementation (mirroring ADR-034's own Assignment Policy port pattern) once a Product Owner selects a concrete metric or pilot candidate from Sections 5 and 10 with actual evidence. Neither is undertaken here.

## Recommended Next Step

Not an algorithm, ranking system, or ADR (all explicitly excluded). The most evidence-grounded next step is a further Product Owner decision selecting a concrete MVP Fair Opportunity Policy candidate (Section 10) — most simply, whether Simple FIFO is adopted for a pilot — since Sections 1–9 already establish the boundary's meaning, scope, and non-negotiable constraints precisely enough that a future ADR could act on them, but no implementation should proceed while Section 10 remains an unselected hypothesis.

## Traceability

| Section | Source Document |
| --- | --- |
| Central Finding | PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5 |
| 1. Fairness Meaning | PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5; PROJECT_CONSTITUTION.md Section 3 |
| 2. NETWORK-Scope Applicability | PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3 |
| 3–5. Candidate Dimensions | PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Sections 1–2; ADR-034 Part 2 |
| 6. Personal Client Relationship | PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Sections 4, 8; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 6; PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md Part 8; ADR-034 Part 2 |
| 7. Reciprocity | PROJECT_CONSTITUTION.md Section 18; ADR-034 Part 4 |
| 8. Payment/Subscription | PRODUCT_BASELINE_V2.md Sections 11, 13; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 7 |
| 9. Transparency | PROJECT_CONSTITUTION.md Sections 3, 18; PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 3; PRODUCT_FOUNDATION.md Section 4 |
| 10. MVP Candidate | ADR-034 Part 4 |
| 11. Candidate Mechanisms Evaluated | ADR-034 Part 4; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Sections 2–3 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Decision (above) requires a further Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
