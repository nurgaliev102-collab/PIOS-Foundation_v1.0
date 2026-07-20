# PIOS Product Decision: Dispatch Philosophy v1.0

Status: Decided — Product Strategy. This document is a Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7) answering "what does a fair digital dispatcher mean for PIOS?" It defines the product philosophy that will guide a future Assignment Policy implementation — the *why* behind an eventual assignment decision, never the *how*. It is not an ADR, not an architecture document, and does not authorize, imply, or preview any algorithm, scoring formula, or ranking mechanism. [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) already established that the algorithm itself remains undecided (ADR-002) and named the abstraction it will sit behind; this document supplies the product reasoning that abstraction exists to serve.

This document is derived from PROJECT_CONSTITUTION.md, PRODUCT_FOUNDATION.md, USE_CASE_CATALOG.md, DOMAIN_MODEL.md, EVENT_CATALOG.md, APPLICATION_ARCHITECTURE.md, MODULE_STRUCTURE.md, INTERFACE_CONTRACTS.md, and ADR-001 through ADR-034. It introduces no new actor, aggregate, event, or business rule not already established by these documents.

---

## Method: Evidence Grading

Per this task's own instruction to separate ratified decisions from principles from unvalidated ideas, every claim below carries one of three tags:

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, cited inline.
- **[PRINCIPLE]** — a reasonable, evidence-consistent extension of ratified material, but not itself literally stated anywhere; treat as directional, not binding.
- **[REQUIRES VALIDATION]** — one of the historically-discussed ideas this task asked to be evaluated, for which no approved document provides evidence either way. Not rejected — simply not yet product-owner-decided.

Nothing tagged [REQUIRES VALIDATION] is treated as a requirement anywhere in this document.

## 1. Product Mission of Dispatch

**The problem.** PRODUCT_FOUNDATION.md Section 4 states the core problem directly: *"the absence of a platform that allocates work to independent drivers in a way that is fair, explainable, and transparent to the people it affects, while still being capable of aggregating demand from multiple, heterogeneous sources and supporting durable relationships... This is a problem of trust and structural accountability in how work is allocated, not a problem of routing, payment processing, or any other specific mechanism."* Section 3's Market Context names the specific failure mode Dispatch exists to correct: *"opacity in how work is allocated to drivers; drivers having little visibility into, or influence over, the criteria used to assign them work... a structural tendency for the party that controls dispatch to accumulate disproportionate leverage over the driver's livelihood."* **[RATIFIED]**

**Primary value and priority.** PROJECT_CONSTITUTION.md Section 3 names **Fair Dispatch** as its own dedicated principle — the only Product Principle naming the dispatch mechanism specifically — and states it explicitly relative to efficiency: *"Mechanisms that connect riders and drivers must be designed and evaluated for fairness, not merely for efficiency."* This is a direct, ratified priority ordering: fairness is not balanced equally against efficiency, it is evaluated first, with efficiency explicitly subordinate. **[RATIFIED]**

Ranked, with evidence grade:

1. **Fairness** — primary, explicitly named for dispatch specifically. **[RATIFIED]**
2. **Transparency / explainability** — inseparable from fairness in the Core Problem statement itself ("fair, explainable, and transparent" appear as one clause, not three independent goals); Constitution Section 3: *"Opacity is not an acceptable trade-off for convenience."* **[RATIFIED]**
3. **Driver autonomy** — a boundary condition the decision must never violate, rather than a value optimized for: PRODUCT_FOUNDATION.md Section 12, *"the platform cannot assume centralized control or ownership of drivers, vehicles, or fleets"*; a driver's availability is self-determined and can never be overridden by an assignment decision. **[RATIFIED]**
4. **Reliability / passenger outcome** — Constitution Section 3 names Reliability and Trust as platform-wide principles, but no document ties either specifically to the dispatch mechanism the way Fair Dispatch is. **[PRINCIPLE]**
5. **Efficiency** — explicitly, deliberately subordinate to fairness by the Constitution's own wording above; still a real constraint (the platform must actually connect supply and demand), just never the value a decision is evaluated by first. **[RATIFIED as subordinate, not absent]**

## 2. Participants and Incentives

### Driver

- **Personal client relationships.** *"A passenger or corporate customer with whom a driver has a recognized, recurring relationship"* (PRODUCT_FOUNDATION.md Section 10); a distinct, jointly-owned entity in DOMAIN_MODEL.md Section 5; PRODUCT_FOUNDATION.md Section 13 states such relationships are *"valuable enough to warrant explicit product support."* **[RATIFIED]**
- **Access to shared demand.** PRODUCT_FOUNDATION.md Section 13 Assumption: *"Transportation demand will originate from more than one kind of source, including but not limited to platform-aggregated orders, personal client relationships, and corporate customers"* — "Platform Order" exists precisely as demand not tied to any driver's own relationship (Section 10). **[RATIFIED]**
- **Predictable, transparent rules.** Direct consequence of the Core Problem statement (Section 1 above) and Constitution's Transparency principle. **[RATIFIED]**

### Passenger

- **Service quality, reliable execution.** Supported by the platform-wide Reliability principle (Constitution Section 3); no dispatch-specific document elaborates this further. **[PRINCIPLE]**
- **Continuity of relationship where appropriate.** Directly supported by the same Personal Client evidence above — this is symmetric: a passenger's interest in continuity is the other side of a driver's personal-client relationship, both drawing on the same ratified entity. **[RATIFIED]**

### Platform

- **Trust.** Named directly (Constitution Section 3; PRODUCT_FOUNDATION.md Section 5). **[RATIFIED]**
- **Neutrality.** PRODUCT_FOUNDATION.md Section 5: *"The platform does not favor one demand source, participant, or relationship over another except where a documented, explainable reason justifies it."* **[RATIFIED]**
- **Sustainable ecosystem.** PROJECT_CONSTITUTION.md Section 1 Vision: *"a long-lived system."* **[RATIFIED]**

### Where interests can conflict

- **Fairness-to-all-drivers vs. speed/efficiency of a single match.** Already resolved in priority (Section 1 above), not in mechanism — the ratified ordering says fairness wins, but not by how much or how.
- **An individual driver's benefit from receiving more shared orders vs. fair access across all drivers competing for the same shared demand.** Not resolved by any document; this is precisely what Assignment Policy will eventually have to balance (Section 5, 8 below).
- **Personal Client priority vs. Platform Neutrality.** This is *not* an unresolved conflict on inspection: Platform Neutrality's own text permits favoring a relationship *"where a documented, explainable reason justifies it,"* and Personal Client relationships are exactly such a reason, already product-endorsed (Section 13 Assumption above). Honoring a personal-client relationship is consistent with Neutrality, not an exception to it. **[RATIFIED reconciliation]**
- **Driver self-determined availability vs. the platform's own allocation needs.** A driver can decline participation at will (PRODUCT_FOUNDATION.md Section 12); fairness and efficiency must both be defined within that constraint, never against it. **[RATIFIED]**

## 3. Dispatch Principles

Principles only — no scoring formula or ranking mechanism is defined or implied anywhere in this section.

- **Transparency of assignment rules.** **[RATIFIED]** — Constitution Section 3; Core Problem statement.
- **No hidden dispatcher preference.** **[RATIFIED]** — direct reading of Platform Neutrality: any preference must be documented and explainable, never silent.
- **Preservation of legitimate driver-client relationships.** **[RATIFIED]** — Personal Client evidence above.
- **Fair access to shared orders.** **[RATIFIED in principle]** — Fair Dispatch plus the existence of Platform Order as a distinct demand category; the specific access mechanism is undecided (Section 5, 8).
- **Accountability through measurable rules.** **[PRINCIPLE]** — a reasonable reading of "explainable" and Trust, but no approved document uses the word "measurable" or requires quantification specifically; do not treat this as requiring a metric.

## 4. Personal Clients vs. Shared Orders

**Personal Client relationships are first-class product concepts.** **[RATIFIED]** — named terminology (PRODUCT_FOUNDATION.md Section 10), a modeled entity spanning Driver Management and Passenger Experience jointly (DOMAIN_MODEL.md Section 5; CONCEPTUAL_DATA_MODEL.md Section 4; LOGICAL_DATA_MODEL.md Section 4), and an explicit product assumption that they are "valuable enough to warrant explicit product support" (PRODUCT_FOUNDATION.md Section 13).

**When a personal relationship has priority over shared dispatch.** **Not decided by any document.** No approved document states a priority rule between a Personal-Client-directed order and a Platform Order.

**When an order enters shared dispatch.** The ratified model does not describe a Personal Client order ever "converting" into a shared/Platform order. Order origin — Platform Order, Corporate Order, or (by implication) a personal-client-directed order — is a property fixed at submission (PRODUCT_FOUNDATION.md Section 10's definitions; DOMAIN_MODEL.md Section 6's "Order Origin," referenced by USE_CASE_CATALOG.md UC-009). This document does not invent a workflow for an order moving between these categories, since none is ratified.

**What remains undecided (stated explicitly, not filled):**

- Whether a Personal-Client-directed order is routed through Dispatch's assignment decision at all, or reaches the named driver by some other mechanism entirely. ADR-002 gives Dispatch exclusive ownership of *the* assignment decision with no stated carve-out — which is suggestive, but no document confirms a personal-client order is actually subject to that decision rather than bypassing it.
- What happens when a driver named in a personal-client relationship is unavailable or has declined participation.
- Any workflow, timing, or fallback mechanism connecting personal-client and shared demand.

## 5. Meaning of Fairness

No approved document defines fairness through any specific operational dimension — "fair, explainable, and transparent" (Section 1) is the full extent of what is ratified, and it is procedural (about how a decision is made and whether it can be explained), not a substantive formula for what a fair outcome looks like. The candidate dimensions below are evaluated individually against that evidence, not chosen from or ranked as a menu:

- **Equal opportunity** (every eligible driver has a genuine chance to receive shared demand). **[PRINCIPLE]** — consistent with Fair Dispatch's "not merely for efficiency" and Platform Neutrality; not itself stated.
- **Balanced participation** (distribution of shared demand does not concentrate on a few drivers over time). **[PRINCIPLE]** — same reasoning as above.
- **Reward for contribution** (drivers who do more receive more). **[REQUIRES VALIDATION]** — no approved document ties assignment to any notion of contribution or merit; this pulls toward an efficiency/incentive model the Constitution's own wording subordinates to fairness.
- **Service quality** (assignment favoring higher-performing drivers). **[REQUIRES VALIDATION]** — no rating, performance, or quality concept exists anywhere in DOMAIN_MODEL.md for Driver Management to even expose; would require new domain modeling before it could be a candidate at all (the same category ADR-034 Part 2 used for driver location).
- **Passenger outcome** (assignment favoring the passenger's own expected experience). **[REQUIRES VALIDATION]** — no document attributes the assignment decision a passenger-experience optimization goal distinct from Reliability generally.

The only thing safe to conclude operationally: fairness for PIOS is a property of *how the decision is made and whether it can be explained*, not yet a property of *what outcome distribution results*. Choosing among the above (or another model entirely) is an open product decision (Section 8), not something this document resolves.

## 6. Anti-Principles

Only principles with actual ratified support are included; a historically-discussed idea with no supporting evidence is named as unresolved rather than adopted as an anti-principle.

- **PIOS must not become an opaque aggregator.** **[RATIFIED]** — PRODUCT_FOUNDATION.md Section 1 Purpose: *"without concentrating unaccountable control over that connection in a single intermediary"*; Constitution Section 3: *"Opacity is not an acceptable trade-off for convenience."*
- **PIOS must not replace a manual dispatcher with an equally hidden algorithmic one.** **[RATIFIED]** — direct consequence of Transparency plus "no hidden dispatcher preference" (Section 3 above); an opaque digital system reproduces exactly the Core Problem's own named failure mode (Section 1), only in software.
- **PIOS must not optimize the assignment decision only for platform benefit.** **[RATIFIED]** — Driver Ownership: *"Drivers are treated as stakeholders of the platform, not as undifferentiated supply"*; Platform Neutrality; Fair Dispatch's explicit subordination of efficiency.
- **PIOS as a commission-based marketplace.** **Not a ratified anti-principle.** DOMAIN_MODEL.md Section 14 states directly: *"any pricing, commission, or regulatory rule, none of which is established by any approved document."* The historically-discussed idea that PIOS is "not an aggregator taking ride commission" is consistent in spirit with Purpose and Trust, but the commission/business model itself is genuinely undecided, not decided against. **[REQUIRES VALIDATION]** — listed here as a flag, not adopted as a stated anti-principle.

## 7. Relationship with Assignment Policy

- **Dispatch Philosophy (this document) answers WHY** a future assignment decision is made the way it is — the values it must honor and the boundaries it must never cross.
- **Assignment Policy (ADR-034) answers HOW** a specific decision is actually calculated — an architectural abstraction, still without a chosen algorithm.
- The policy must be replaceable without changing this philosophy, and — the reverse relationship, not stated by ADR-034 but implied by product-versus-architecture layering (PROJECT_CONSTITUTION.md Section 5's Documentation Hierarchy) — this philosophy is expected to outlive any single policy implementation. A future policy is evaluated against these principles; these principles are never adjusted merely to justify a particular policy's convenience.

## 8. Open Product Decisions

Requiring a future Product Owner decision, not resolved here:

- The concrete fairness metric or model (Section 5) — none of the five candidate dimensions is chosen.
- Personal Client priority rules — whether, when, and how a personal-client relationship takes precedence over shared dispatch (Section 4).
- Whether Personal-Client-directed orders are routed through Dispatch's assignment decision at all (Section 4).
- The balancing mechanism for shared/Platform Order distribution across drivers.
- Any incentive or reward structure — currently unsupported by any evidence, not merely undecided in detail (Section 5).
- A driver contribution model — same status as incentives; no ratified concept to build on.
- The platform's business/commission model — explicitly out of Dispatch's own scope (DOMAIN_MODEL.md Section 14), but relevant context for how "independent business participant" is ultimately realized.
- Any passenger-side priority signal beyond Personal Client continuity.

## Files Changed

`docs/PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` (new) is the only file this decision creates. No ADR is created, no code, database, or API is modified or implied, consistent with this task's own scope.

## Recommended Next Step

Not implementation, and not yet ADR-034's Assignment Policy port itself. The next legitimate step is a further Product Owner decision resolving one or more of Section 8's open questions with actual evidence (market research, driver/passenger input, or an explicit Product Owner judgment call recorded as such) — most usefully starting with Personal Client priority rules (Section 4), since that boundary affects whether Assignment Policy's future scope even includes personal-client-directed orders at all. Only once at least one concrete, evidenced answer exists should an implementation task build a first Assignment Policy against ADR-034's abstraction.

## Traceability

| Section | Source Document | Related ADR |
| --- | --- | --- |
| 1. Product Mission | PROJECT_CONSTITUTION.md Section 3; PRODUCT_FOUNDATION.md Sections 1, 3, 4, 12 | ADR-002 |
| 2. Participants and Incentives | PRODUCT_FOUNDATION.md Sections 5, 10, 12, 13 | ADR-005, ADR-009 |
| 3. Dispatch Principles | PROJECT_CONSTITUTION.md Section 3 | ADR-002 |
| 4. Personal Clients vs. Shared Orders | PRODUCT_FOUNDATION.md Sections 10, 13; DOMAIN_MODEL.md Sections 5–6; USE_CASE_CATALOG.md UC-009 | ADR-002, ADR-034 |
| 5. Meaning of Fairness | PRODUCT_FOUNDATION.md Sections 4–5; PROJECT_CONSTITUTION.md Section 3 | ADR-034 |
| 6. Anti-Principles | PRODUCT_FOUNDATION.md Section 1; PROJECT_CONSTITUTION.md Section 3; DOMAIN_MODEL.md Section 14 | — |
| 7. Relationship with Assignment Policy | PROJECT_CONSTITUTION.md Section 5 | ADR-034 |
| 8. Open Product Decisions | (absence of evidence, this document's own analysis) | ADR-034 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Open Product Decision (Section 8) requires a Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future Assignment Policy implementation — may act on it.
