# PIOS Product Decision: Personal Client Relationship v1.0

Status: Decided — Product Concept Definition. This document is a Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7) defining what "Personal Client Relationship" means as a business concept in PIOS. It establishes the concept itself — it does not define assignment priority, does not define a dispatch algorithm, and does not extend [PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md](PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md) or [ADR-034](ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) beyond what they already state.

Derived from PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, APPLICATION_ARCHITECTURE.md, INTERFACE_CONTRACTS.md, MODULE_STRUCTURE.md, PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md, and ADR-001 through ADR-034. Introduces no new actor, aggregate, event, command, or business rule.

---

## Method

Every claim carries one of three tags: **[RATIFIED]** (stated or directly implied by an approved document, cited inline), **[PRINCIPLE]** (a reasonable, evidence-consistent extension, not itself literally stated), or **[REQUIRES VALIDATION]** (no approved document provides evidence either way). Nothing tagged [REQUIRES VALIDATION] is treated as a requirement anywhere below.

## Part 1 — Definition

**What it is.** *"The recognized, recurring relationship between a driver and a passenger or corporate customer"* (DOMAIN_MODEL.md Section 5; PRODUCT_FOUNDATION.md Section 10), existing *"independent of any single Order"* (DOMAIN_MODEL.md Section 13). **[RATIFIED]**

**What kind of relationship it is.** Structurally, it is explicitly a conceptual **entity**, not a ratified **aggregate** — DOMAIN_MODEL.md Section 4 states an aggregate is described "only where a purpose, an invariant, and a lifecycle are each already derivable," and Personal Client Relationship appears in Section 5's Entities list, not Section 4's Aggregates list. USE_CASE_CATALOG.md UC-002 confirms this directly: *"Related Aggregate: None — Personal Client Relationship is a conceptual entity, not a ratified aggregate (DOMAIN_MODEL.md Section 5)."* **[RATIFIED]** It is a named, recognized fact the platform represents — not yet a modeled thing with its own rules.

Evaluating the specific characterizations this task asks about, against that evidence:

- **Ownership.** No document frames a driver as "belonging to" a passenger, or the reverse. This would directly contradict Driver Ownership (drivers as independent stakeholders, PROJECT_CONSTITUTION.md Section 3) and Platform Neutrality. **[RATIFIED — it is NOT ownership]**
- **Commercial relationship.** No document ties it to payment, contract, pricing, or exclusivity terms; Payments' own scope (DOMAIN_MODEL.md Section 14: "any pricing, commission, or regulatory rule, none of which is established") is entirely separate and unconnected to it. **[RATIFIED — it is NOT a commercial relationship]**
- **Preference.** Plausible reading of "recognized, recurring," but the word does not appear in any approved document. **[PRINCIPLE]**
- **Trust.** Consistent with the platform's general Trust principle (Constitution Section 3), but no document ties Trust specifically to this relationship. **[PRINCIPLE]**
- **Service relationship.** No document frames it this way — it reads as a recognized, recurring *fact* about who has worked together before, not a defined service contract with terms. **[REQUIRES VALIDATION]**

**What it is not**, stated precisely: not an aggregate with its own invariant or lifecycle (above); not a guarantee of future assignment (DOMAIN_MODEL.md Section 13's relationship list never connects it to Assignment); not derived from or equivalent to a log of past rides (no "ride history" concept exists in any approved document at all); not something either party is documented as being able to actively declare (unlike Availability, which is explicitly the driver's own active declaration, DOMAIN_MODEL.md Section 6).

## Part 2 — Participants

- **Passenger / Corporate Customer.** A participant — the relationship's other subject, represented by Passenger Experience. **[RATIFIED]**
- **Driver.** A participant — the relationship's other subject, represented by Driver Management. **[RATIFIED]**
- **Dispatch.** No ratified role at all. INTERFACE_CONTRACTS.md Section 5 lists exactly four justified module-to-module contracts; none gives Dispatch any access to Personal Client Relationship information. **[RATIFIED absence]**

  **Superseded, narrowly, as of 2026-09-03 — historical text above preserved, not deleted, per `CLAUDE.md`'s "Never Delete Documentation."** [ADR-062: Primary Driver / First Refusal — Passenger Experience → Dispatch Contract](ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) (Accepted, Task 10B) grants Dispatch a **narrow, ratified role**: it may consume a binary Primary Driver routing signal (a passenger reference paired with a driver reference, nothing more) for the sole purpose of offering First Refusal (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 3–4) before an order falls through to normal matching. **This does not make Dispatch the owner of Personal Client Relationship information, and does not give Dispatch any role beyond this one narrow purpose**: Dispatch still cannot modify the relationship (Part 2's own "Who cannot modify it" below is unaffected), still has no visibility into passenger profile, contact, rating, or reputation data carried by this relationship, and still has no ratified role in whether or how this relationship affects the *assignment decision itself* — that question (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 4) remains exactly as open as before `ADR-062`. INTERFACE_CONTRACTS.md Section 5 now lists five justified contracts as of `ADR-053`, plus this sixth as of `ADR-062` (Section 17 of that document) — none beyond this one narrow addition gives Dispatch any access to Personal Client Relationship information.
- **Platform / Administration.** No ratified role. Administration's own scope is "governance and standing information" (MODULE_STRUCTURE.md Section 3); it does not extend here.

**Who can modify it.** Only Driver Management and Passenger Experience, jointly, each their own side (Part 5 below) — but note carefully: no ratified *command* exists for either to actually do so (DOMAIN_MODEL.md Section 9 lists six commands total; none touches this relationship). Ownership of the *right* to modify it is ratified; a *mechanism* to exercise that right is not. **[RATIFIED ownership / REQUIRES VALIDATION mechanism]**

**Who only observes.** No document establishes an observer role for anyone — not even Analytics, since no event exists for this relationship to consume (Part 3).

**Who cannot modify it.** Every other module — Dispatch, Order Management, Payments, Administration, Analytics, Notifications — excluded by the exclusive-ownership rule (ADR-005, ADR-009, ADR-019) applied to information owned exclusively by Driver Management and Passenger Experience. **[RATIFIED]**

## Part 3 — Lifecycle

DOMAIN_MODEL.md Section 12 (State Lifecycles) names exactly three lifecycles — Order, Assignment, Driver Availability — and Personal Client Relationship is not among them. USE_CASE_CATALOG.md UC-002 states this directly for the one use case that touches it: *"Main Success Outcome: The passenger's representation, and any Personal Client relationship they hold, remains current. Alternative Outcomes: Not applicable — no discrete lifecycle transition is catalogued for this ongoing responsibility. Related Events: None currently catalogued."*

The relationship is currently modeled as a static, ongoing fact that "remains current" — not as a stateful thing with transitions. Every stage this task asks about is **[REQUIRES VALIDATION]**, with no evidence either way:

- **Creation** — no command, event, or description of how one comes to exist.
- **Confirmation** — nothing about whether both sides must agree, or whether recognition is unilateral, mutual, or platform-inferred.
- **Suspension** — no concept of a temporarily-paused relationship exists anywhere.
- **Termination** — explicitly absent per UC-002's own quote above.
- **Expiration** — no document addresses durability or decay over time.

This is stated explicitly rather than filled: PIOS currently has a *name* for this concept and a *place* it belongs (Part 5), but no ratified lifecycle at all.

## Part 4 — Rights and Responsibilities

- **Choose** (to form or dissolve the relationship) — **[REQUIRES VALIDATION]**. Unlike Availability, which a driver actively declares, "recognized" is passive language; no document says either party actively chooses this relationship into existence.
- **Reject** — **[REQUIRES VALIDATION]**. No document addresses refusal by either party.
- **Preserve** — **[PRINCIPLE]**. PRODUCT_FOUNDATION.md Section 13's assumption that such relationships are *"valuable enough to warrant explicit product support"* implies preservation has value, though no document states a specific "right to preserve."
- **Observe** — only the two owning domains have any ratified relationship to the information at all (Part 2); no other participant has a ratified right to observe it, in any form. **[RATIFIED absence for everyone else]**
- **Override** — Dispatch has no ratified right to override, bypass, or act on this relationship, because Dispatch has no ratified relationship to it whatsoever (Part 2). **[RATIFIED absence]**
- **Recommend** — **[REQUIRES VALIDATION]**. No concept of the platform or any actor "recommending" a relationship exists anywhere.

## Part 5 — Visibility

- **Should Dispatch know?** Not resolved — this is exactly the open question [PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md](PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md) Section 4 already surfaced: whether a personal-client-directed order is even routed through Dispatch's assignment decision at all is undecided by any document. This document does not resolve it either. **[REQUIRES VALIDATION]**
- **Should Driver Management own it?** Yes, jointly. **[RATIFIED]** — DOMAIN_MODEL.md Section 5 ("spanning Driver Management and Passenger Experience"), CONCEPTUAL_DATA_MODEL.md Section 4 and LOGICAL_DATA_MODEL.md Section 4 (both: "Passenger Experience and Driver Management, each maintaining its own side" / "jointly").
- **Should Passenger Experience own it?** Yes, jointly, and more consistently documented. **[RATIFIED]** — the same sources above, plus MODULE_STRUCTURE.md Section 3, APPLICATION_ARCHITECTURE.md Section 5, PERSISTENCE_ARCHITECTURE.md Section 3, and INTERFACE_CONTRACTS.md Section 4 all *explicitly* restate Passenger Experience's role in this relationship by name.
- **A documentation asymmetry worth recording precisely, not resolving:** Driver Management's own entries in MODULE_STRUCTURE.md, APPLICATION_ARCHITECTURE.md, PERSISTENCE_ARCHITECTURE.md, and INTERFACE_CONTRACTS.md never restate its side of this joint ownership — only the data-model-tier documents (DOMAIN_MODEL.md, CONCEPTUAL_DATA_MODEL.md, LOGICAL_DATA_MODEL.md) do. This reads as incomplete restatement rather than a substantive disagreement (nothing anywhere denies Driver Management's side), but it is exactly the kind of gap this document should surface rather than silently resolve by assuming symmetry.
- **Should other modules see only projections?** No — today the answer is stricter than "projection-only": INTERFACE_CONTRACTS.md Section 5's four justified contracts do not include any exposure of this relationship to any other module, in any form, including a projection. No other module sees it at all yet.

Per this document's own scope, module ownership is not redesigned here — the above only restates what already exists.

## Part 6 — Relationship Boundaries

Explicitly distinguished, none collapsed into another:

- **vs. Ride history.** No "ride history" concept exists in DOMAIN_MODEL.md at all. The relationship is defined to exist *"independent of any single Order"* (Section 13) — it is not defined as, or derived from, an accumulated count or log of past rides.
- **vs. Favourite driver.** No such concept exists in any approved document. "Recognized, recurring" implies some durability/mutuality; a unilateral "favourite" marker is a different, unratified idea.
- **vs. Contact list.** No such concept exists anywhere; nothing implies a messaging or communication capability between the parties.
- **vs. Assignment.** Entirely distinct: Assignment is a ratified Dispatch-owned aggregate with its own invariant and lifecycle (DOMAIN_MODEL.md Section 4); Personal Client Relationship is a Driver-Management/Passenger-Experience-owned entity with neither. DOMAIN_MODEL.md Section 13's relationship list never connects the two.
- **vs. Availability.** Availability is a Driver Management value object describing a driver's own, actively-declared, ratified-lifecycle readiness state (DOMAIN_MODEL.md Section 6, 12); Personal Client Relationship describes a relationship *between* a driver and a passenger or corporate customer, unrelated to whether that driver currently declares themselves available.

## Part 7 — Product Invariants

Only principles with actual supporting evidence:

- **The relationship does not imply ownership of either party.** **[RATIFIED]** — direct consequence of Driver Ownership and Platform Neutrality (Part 1).
- **The relationship exists independent of any single order.** **[RATIFIED]** — DOMAIN_MODEL.md Section 13, verbatim.
- **Relationship information stays within its owning domains.** **[RATIFIED]** — exclusive-ownership rule (ADR-005, ADR-009, ADR-019); no contract exposes it elsewhere (Part 5).
- **The relationship should be transparent to the parties it concerns.** **[PRINCIPLE]** — consistent with the platform's general Transparency principle (Constitution Section 3), though no document specifically requires transparency of this relationship to either party.
- **The relationship should not be silently reassigned.** **[PRINCIPLE]** — consistent with Trust and "no hidden dispatcher preference" (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 3), though no document addresses reassignment directly, since no lifecycle exists yet at all (Part 3).

## Part 8 — Open Product Questions

Listed, not answered:

- How is the relationship created — declared by the driver, by the passenger, mutually, or inferred by the platform from a pattern of activity?
- Must both sides confirm it for it to be "recognized"?
- Can multiple drivers hold a personal-client relationship with the same passenger or corporate customer?
- Can a single driver hold a personal-client relationship with multiple passengers or corporate customers?
- Can the relationship expire from inactivity, or does it persist indefinitely once recognized?
- Can it be exclusive (precluding a personal-client relationship with another driver)?
- Can it be delegated — for example, to a Fleet, an entity PRODUCT_FOUNDATION.md itself notes has no ratified domain owner yet (DOMAIN_MODEL.md Section 5, "Domain Decision Required")?
- Does it carry any weight in Dispatch's eventual assignment decision at all, and if so, how much relative to fair access for other drivers (cross-references PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 4 and 8 directly; not re-answered here)?

## Part 9 — Relationship with Future Assignment Policy

Only the architectural dependency, no priority rule:

```
Personal Client Relationship
  (this document's own product concept)
        |
        v   (NOT YET RATIFIED -- Part 8's open question)
Priority Rules
  (whether, and how, this relationship would ever influence a decision)
        |
        v   (ADR-034's abstraction)
Assignment Policy
        |
        v
Assignment Decision
```

The first arrow does not exist today. ADR-034 Part 2 already classifies Personal Client Relationship only as a *future candidate input* to Assignment Policy, never a ratified one; this document does not change that. No priority rule is defined, implied, or previewed here.

## Files Changed

`docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` (new) is the only file. No ADR, code, database, event, or API is created or modified, consistent with this task's scope.

## Traceability

| Section | Source Document |
| --- | --- |
| Part 1. Definition | DOMAIN_MODEL.md Sections 4–6, 13; PRODUCT_FOUNDATION.md Section 10; USE_CASE_CATALOG.md UC-002 |
| Part 2. Participants | INTERFACE_CONTRACTS.md Sections 3–5; DOMAIN_MODEL.md Section 9; ADR-005, ADR-009, ADR-019 |
| Part 3. Lifecycle | DOMAIN_MODEL.md Section 12; USE_CASE_CATALOG.md UC-002 |
| Part 4. Rights and Responsibilities | DOMAIN_MODEL.md Section 6; PRODUCT_FOUNDATION.md Section 13 |
| Part 5. Visibility | DOMAIN_MODEL.md Section 5; CONCEPTUAL_DATA_MODEL.md Section 4; LOGICAL_DATA_MODEL.md Section 4; MODULE_STRUCTURE.md Section 3; APPLICATION_ARCHITECTURE.md Section 5; PERSISTENCE_ARCHITECTURE.md Section 3; INTERFACE_CONTRACTS.md Sections 4–5 |
| Part 6. Relationship Boundaries | DOMAIN_MODEL.md Sections 4, 6, 13 |
| Part 7. Product Invariants | PROJECT_CONSTITUTION.md Section 3; DOMAIN_MODEL.md Section 13; PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 3 |
| Part 9. Relationship with Assignment Policy | ADR-034 Part 2; PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Sections 4, 8 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Part 8 question requires a Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future Assignment Policy implementation — may act on it.
