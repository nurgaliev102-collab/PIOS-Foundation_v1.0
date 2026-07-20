# PIOS Open Questions

Read `AI_HANDOFF.md` first. This is a prioritized, unresolved-decision register. **Every item here stays `[OPEN]` until a human with Product Owner authority resolves it and records that resolution as a Product Decision or ADR (PROJECT_CONSTITUTION.md Section 7, Section 9).** No AI may resolve any item on this list on the project's behalf, per `AI_HANDOFF.md`'s "never silently fill an OPEN decision" rule.

For every item: status, why unresolved, what evidence/decision is needed, whether it blocks prototype work, whether it blocks public/commercial launch.

---

## P0 — Before Public Commercial Launch

### 1. Legal operating structure

- **Status:** `[OPEN]`. Three candidate structures named, none selected (`PIOS_MASTER_CONTEXT.md` Section 12): (A) PIOS itself performs regulated order-service functions; (B) PIOS is SaaS/infrastructure, a separate authorized operator performs regulated functions; (C) PIOS enables direct participant interaction without itself performing regulated functions, only if legally supportable.
- **Why unresolved:** No ratified document addresses this at all; PRODUCT_FOUNDATION.md Section 12 only acknowledges that regulatory regimes vary by jurisdiction, without resolving which structure PIOS adopts.
- **Evidence/decision needed:** A dedicated legal-design analysis comparing the three structures against actual target jurisdictions, followed by a Product Owner decision.
- **Blocks prototype?** No — prototype/development work may proceed without asserting a legal conclusion, per explicit Product Owner instruction.
- **Blocks public launch?** **Yes.**

### 2. Regulatory role of PIOS

- **Status:** `[OPEN]`. Directly downstream of Item 1 — whether PIOS itself is the "order-service" entity regulators recognize, or a technology provider to one.
- **Why unresolved:** Same as Item 1; no document distinguishes PIOS's own regulatory posture from a hosted operator's.
- **Evidence/decision needed:** Same legal-design analysis as Item 1; likely jurisdiction-specific.
- **Blocks prototype?** No.
- **Blocks public launch?** **Yes.**

### 3. Driver legal participation requirements

- **Status:** `[OPEN]`. PRODUCT_FOUNDATION.md Section 12 states drivers "participate as independent stakeholders, not as employees," and licensing/certification is "the responsibility of the relevant regulatory authority" — but no document specifies what PIOS itself must verify, require, or record about a driver's own legal standing before allowing participation.
- **Why unresolved:** Out of scope for every document produced so far; explicitly named as a gap by this consolidation.
- **Evidence/decision needed:** Legal-design analysis (Item 1) plus a Product Decision on driver admission requirements.
- **Blocks prototype?** No, for a small, manually-admitted pilot cohort (already the Network Pilot's own model).
- **Blocks public launch?** **Yes.**

### 4. Payment-flow legal design

- **Status:** `[OPEN]`. Direct payment between passenger and driver is `[RATIFIED for pilot purposes only]` (`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 2); the Payments module (the platform's own payment-related *record*, not custody) remains entirely unbuilt and undecided. Whether PIOS ever needs money-transmission licensing, escrow, or similar under any jurisdiction is unaddressed.
- **Why unresolved:** Genuinely contingent on Items 1–2.
- **Evidence/decision needed:** Legal-design analysis; a Product Decision on whether PIOS ever takes payment custody at all (current direction leans against it, but this is `[CURRENT DIRECTION]`, not ratified either way as a permanent architecture).
- **Blocks prototype?** No — direct payment already validated as sufficient for the pilot.
- **Blocks public launch?** **Yes**, at any scale where regulatory exposure becomes material.

---

## P0/P1 — Product

### 5. Exact electronic-dispatch MVP policy

- **Status:** `[OPEN]`. Dispatch's own decision logic remains deliberately undefined at every level (ADR-002, ADR-034); Candidate Slice 4 (`PIOS_MVP_V01.md`) names the desired *flow shape* without a mechanism.
- **Why unresolved:** ADR-002 and ADR-034 both deliberately exclude the algorithm from documentation until a Product Owner decision selects one, gated on real evidence.
- **Evidence/decision needed:** A Product Decision selecting a concrete pilot-level mechanism (Simple FIFO is the one candidate `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 10 already names as simplest, not chosen), followed by an ADR mirroring ADR-034's Assignment Policy port pattern.
- **Blocks prototype?** Blocks only Candidate Slice 4 specifically; Slices 1–3 and 5 do not depend on it.
- **Blocks public launch?** Blocks any launch that includes automated (non-manual) dispatch of shared/network demand.

### 6. Opportunity semantics where still open

- **Status:** `[OPEN]`. `Assignment`'s CREATED/ACCEPTED split already fully covers Commitment (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`); whether a true multi-candidate Opportunity concept is ever built is unresolved.
- **Why unresolved:** No evidence yet on whether a single-candidate proposal (today's model) or a genuine candidate pool is actually needed — explicitly gated on pilot/validation evidence.
- **Evidence/decision needed:** Observed pattern from running pilots (how often a referral is one-candidate vs. many); a Product Decision.
- **Blocks prototype?** Blocks only an automated multi-candidate dispatch flow.
- **Blocks public launch?** Only if the launch depends on automated multi-candidate matching.

### 7. Eligibility implementation

- **Status:** `[OPEN]`. Availability ≠ Eligibility is a ratified structural distinction (`PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md`); concrete criteria are unresolved. Manual admission stands in today.
- **Why unresolved:** No ratified criteria for trust/standing beyond the label exists (DOMAIN_MODEL.md Section 4).
- **Evidence/decision needed:** A Product Decision defining concrete Eligibility criteria.
- **Blocks prototype?** No — manual admission already sufficient for pilot-scale work.
- **Blocks public launch?** **Yes**, at any scale beyond manually-vetted cohorts.

### 8. Fair Opportunity mechanism

- **Status:** `[OPEN]`. 7 candidates classified (procedural fairness, equal opportunity, balanced participation, FIFO, Round Robin, ranking/scoring, AI selection), none chosen (`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`).
- **Why unresolved:** Explicitly gated on Product Owner selection with real evidence; no approved document provides evidence for any one candidate over another.
- **Evidence/decision needed:** Product Decision selecting a candidate, most simply starting with whether Simple FIFO is adopted for a pilot.
- **Blocks prototype?** Blocks only Candidate Slice 4.
- **Blocks public launch?** Blocks any automated shared-opportunity allocation.

### 9. Invitation relationship semantics

- **Status:** `[OPEN]`, entirely `[CURRENT DIRECTION]`/unratified. No document defines how attribution is created, whether it can be revoked, transferred, or expires, or what "invited by" legally or operationally means beyond "not ownership."
- **Why unresolved:** Newly introduced by Product Owner context; no prior repository document addresses invitation at all.
- **Evidence/decision needed:** New Domain-layer documentation (a DOMAIN_MODEL.md extension) before any code, per Documentation First.
- **Blocks prototype?** **Yes** — blocks Candidate Slice 1 specifically.
- **Blocks public launch?** Yes, if invitation is load-bearing for growth strategy.

### 10. Personal-client protection semantics

- **Status:** `[OPEN]` on almost every dimension: creation, confirmation (mutual or unilateral?), exclusivity, expiration, termination — `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 3, Part 8 leave all of these open, only the *concept* (not ownership, not commercial, not guaranteed assignment) is ratified.
- **Why unresolved:** No lifecycle was ever ratified for this concept — it exists today as a static, "recognized, recurring" fact, not a stateful thing.
- **Evidence/decision needed:** A Product Decision resolving any of Part 8's specific questions.
- **Blocks prototype?** No — the running Network Pilot already operates without resolving these (relationships are pre-existing, observed, never created/confirmed/terminated by the pilot itself).
- **Blocks public launch?** Yes, once PIOS needs to represent this relationship as software state rather than an outside-the-system fact a human already knows.

---

## P1 — Business

### 11. Monetization

- **Status:** `[OPEN]`/`[HYPOTHESIS]`. Subscription named as a leading hypothesis only (PRODUCT_BASELINE_V2.md Section 13); commission genuinely undecided, not decided against (Constitution Section 19). Product Owner direction adds: explore not taking a traditional per-ride commission.
- **Why unresolved:** No pricing, billing period, fee structure, or payer identity is established by any document.
- **Evidence/decision needed:** Willingness-to-pay evidence (the Network Pilot's own H2 is a first, narrow data point); a Product Decision.
- **Blocks prototype?** No.
- **Blocks public launch?** **Yes** — a commercial launch requires an actual monetization mechanism.

### 12. Subscription vs. other model

- **Status:** `[OPEN]`/`[HYPOTHESIS]`. Subsumed within Item 11; recorded separately because the Product Owner named specific alternatives (subscription, infrastructure/service fee, other fixed or value-based model) without choosing among them.
- **Blocks prototype?** No.
- **Blocks public launch?** Yes, as part of Item 11.

### 13. Economics of team/network originator

- **Status:** `[OPEN]`, entirely `[CURRENT DIRECTION]`/`[HYPOTHESIS]`. No document addresses what, if anything, a network-originating driver earns from a team/network's activity.
- **Why unresolved:** The entire team/network concept is new, unratified product direction (`PIOS_MASTER_CONTEXT.md` Section 10).
- **Evidence/decision needed:** A Product Decision, informed by evidence the domino-growth hypothesis has not yet produced.
- **Blocks prototype?** Blocks any prototype that implies team/network economics, not the invitation mechanism itself in isolation.
- **Blocks public launch?** Yes, if team/network economics are load-bearing for the launch's own value proposition.

### 14. Anti-MLM safeguards

- **Status:** `[CURRENT DIRECTION]`, a guardrail, not yet a ratified mechanism. Principle stated (recruitment alone must never create automatic economic entitlement) but no concrete rule, check, or architecture exists.
- **Why unresolved:** Same as Item 13 — the whole team/network economic model is unbuilt.
- **Evidence/decision needed:** A Product Decision defining the actual value-tied-to-real-activity mechanism, once Item 13 is addressed.
- **Blocks prototype?** Only blocks a prototype that implements team/network earnings.
- **Blocks public launch?** Yes, if any team/network earnings feature ships.

---

## P1/P2 — Growth

### 15. Driver→driver invitation

- **Status:** `[CURRENT DIRECTION]`/`[OPEN]`. Named as a potential invitation channel; no semantics defined (does it imply mentorship, team membership, nothing at all?).
- **Blocks prototype?** Blocks only the driver→driver invitation channel specifically within Candidate Slice 1.
- **Blocks public launch?** Yes, if this channel is part of the launch scope.

### 16. Passenger→passenger invitation

- **Status:** `[CURRENT DIRECTION]`/`[OPEN]`, explicitly named as a "potentially later" channel by the Product Owner — the least defined of all invitation directions.
- **Blocks prototype?** Blocks only this specific channel.
- **Blocks public launch?** No, unless explicitly included in launch scope — the Product Owner's own framing already treats this as lower priority.

### 17. Network/team boundaries

- **Status:** `[OPEN]`, entirely `[HYPOTHESIS]`. Whether a network/team has any formal boundary at all, and what defines membership, is unaddressed.
- **Blocks prototype?** Blocks the team/network graph concept (`PIOS_MASTER_CONTEXT.md` Section 10) entirely — explicitly "do not implement yet."
- **Blocks public launch?** Yes, if team/network features ship.

### 18. Cross-network demand

- **Status:** `[OPEN]`, entirely `[HYPOTHESIS]`. Whether/how networks help fulfill demand across each other's boundaries under transparent rules is unaddressed — governance, allocation, and hierarchy are all named `[OPEN]` by the Product Owner directly.
- **Blocks prototype?** Blocks any cross-network feature entirely.
- **Blocks public launch?** Yes, if cross-network fulfillment ships.

---

## Cross-Cutting Note: No Conflict Found

Reconciling every item above against the full authority chain (`PIOS_DECISION_LEDGER.md`), **no genuine `[CONFLICT / NEEDS DECISION]` was found** between the Product Owner's Current Direction and anything already `[RATIFIED]`. Every item here is either (a) a reaffirmation of something already established (the electronic dispatcher's centrality), or (b) a genuinely new extension correctly left open rather than silently resolved. Where a tension is worth watching but is not yet a conflict — for example, whether privileging network-originator drivers with future demand access could ever strain Platform Neutrality (PRODUCT_FOUNDATION.md Section 5) — it is recorded here as a dependency to monitor once Item 13 is addressed, not asserted as an unresolved contradiction today.
