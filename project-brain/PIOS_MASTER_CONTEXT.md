# PIOS Master Context

Read `AI_HANDOFF.md` first if you have not. This file explains what PIOS is, why it exists, and the current shape of its product thinking — with every claim tagged for authority status (legend in `AI_HANDOFF.md`). It is a consolidation, not a new source of truth; every `[RATIFIED]` claim below is traceable to a specific `docs/` document.

---

## 1. Product Identity

**Core statement `[RATIFIED]`** (PROJECT_CONSTITUTION.md Section 19; PRODUCT_FOUNDATION.md Section 1): PIOS is a trustworthy, transparent mechanism connecting independent drivers with transportation demand from multiple, heterogeneous sources, without concentrating unaccountable control over that connection in a single intermediary. It is explicitly **not** an opaque aggregator, **not** a hidden dispatcher, and **not** an owner of driver relationships.

**Do not understand PIOS as only a "personal-client fallback system."** `[RATIFIED — this correction, not merely current opinion]`. This is not new Product Owner framing overriding the repository; the repository already reached this conclusion itself. `PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md` Part 5 explicitly warns that the Network Pilot documents' "intense, repeated focus on one Driver-A/Driver-B/Coordinator scenario... risks giving a future reader the false impression that this scenario *is* the product, rather than one currently-being-validated extension of a broader, older thesis." Part 6 of that same document names the actual product core: Order Management, Dispatch, Driver Management, Passenger Experience, the event-driven integration substrate connecting them, and Personal Client Relationship as a *protected concept* — with Personal Client Relationship explicitly classed as a **Supporting Capability**, not the product's existential center. Relationship-preserving fallback (the Network Pilot's own scenario) is **one demand/relationship mechanism among several**, not the entire product.

## 2. The Original Problem

**`[RATIFIED]`** (PRODUCT_FOUNDATION.md Section 4): *"The core problem PIOS addresses is the absence of a platform that allocates work to independent drivers in a way that is fair, explainable, and transparent to the people it affects, while still being capable of aggregating demand from multiple, heterogeneous sources and supporting durable relationships... This is a problem of trust and structural accountability in how work is allocated, not a problem of routing, payment processing, or any other specific mechanism."*

Named failure modes it corrects `[RATIFIED]` (PRODUCT_FOUNDATION.md Section 3): opacity in how work is allocated; drivers having little visibility into or influence over allocation criteria; fragmentation of tools and relationships across uncoordinated systems; a structural tendency for whoever controls dispatch to accumulate disproportionate leverage over a driver's livelihood.

## 3. Target Users / Ecosystem

**`[RATIFIED]`** (PRODUCT_FOUNDATION.md Section 6): Driver (primary stakeholder), Passenger, Dispatcher (historical role, superseded by the platform's own Dispatch capability), Fleet (participant, no ratified domain owner yet — DOMAIN_MODEL.md Section 5, "Domain Decision Required"), Corporate Customer, Platform Administrator, Partner, External Service.

## 4. The Electronic Dispatcher

**Central, load-bearing, already-ratified capability, not a new idea.** `[RATIFIED]` — ADR-002 (Dispatch Engine) establishes assignment as a platform-owned architectural capability, never an individual's private discretion. PROJECT_CONSTITUTION.md Section 3 names **Fair Dispatch** as its own dedicated Product Principle — the only principle naming the dispatch mechanism specifically: *"Mechanisms that connect riders and drivers must be designed and evaluated for fairness, not merely for efficiency."* `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 1 ranks Fairness first, above Efficiency, with Transparency/Explainability inseparable from it, and Driver Autonomy as a boundary the decision must never cross.

**What fairness currently means, precisely `[RATIFIED]`** (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 5; `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 1): fairness is **procedural**, not distributional — a property of *how the decision is made and whether it can be explained*, not yet a property of *what outcome distribution results*. No specific fairness metric, ranking, scoring, or algorithm is chosen anywhere.

**What is deliberately still undecided about it** — see `PIOS_OPEN_QUESTIONS.md`, but headline items: the concrete fairness metric; whether/how Personal Client Relationship influences priority (three separate open sub-questions, not one); the Assignment Policy algorithm itself (ADR-002, ADR-034: deliberately, permanently excluded from every level of documentation until a Product Owner decision selects one).

## 5. Independent-Driver Model

**`[RATIFIED]`** (PROJECT_CONSTITUTION.md Section 18, Driver Independence; PRODUCT_FOUNDATION.md Section 12): a driver is never an employee; the platform cannot assume centralized control or ownership of drivers, vehicles, or fleets; a driver's availability and participation are self-determined.

**`[CURRENT DIRECTION]`**, consistent with but more specific than the above (Product Owner context, not yet a Product Decision): PIOS should not be automatically understood as the driver's employer, the vehicle's owner, the transportation provider itself, or the necessary recipient of ride payments. A driver chooses their own lawful business/legal status and is responsible for their own transportation/legal obligations; passenger payment should preferably go directly to the actual service provider/driver. **Exact legal structure is `[OPEN]`** and explicitly requires separate legal-design analysis (see `PIOS_OPEN_QUESTIONS.md`, P0). **Do not claim PIOS is legally exempt from taxi/order-service regulation anywhere** — no ratified document makes that claim, and the Product Owner context explicitly forbids inventing it.

Note the narrower, already-`[RATIFIED]` precedent this direction builds on: `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 2 ratifies **direct payment for pilot purposes only** — not yet a ratified permanent production architecture; the Payments module remains a wholly separate, unbuilt, undecided capability for the platform's own payment-related *record*, distinct from custody.

## 6. Passenger Side

**`[RATIFIED]`**: Passenger Experience represents passengers and corporate customers and their interaction with the platform (PRODUCT_FOUNDATION.md Section 9; DOMAIN_MODEL.md Section 3). Its own ratified contract with Order Management is narrow: providing originating context for a submitted order, "limited to representation, not a discrete transactional interaction" (API_SPECIFICATION.md Section 5, as established during Tranche 2). No passenger-owned event exists or is justified by any approved document (EVENT_CATALOG.md Section 8).

## 7. Invitation / Growth Model

**Entirely `[CURRENT DIRECTION]` / `[HYPOTHESIS]` — no ratified vocabulary for any of this exists anywhere in `docs/` today.** "Invitation," "attribution," "referral," "QR code," "share link," "domino effect," and "team/network" do not appear in DOMAIN_MODEL.md, EVENT_CATALOG.md, or any ADR. Specifically, per Product Owner context:

- A universal invitation/attribution mechanism (personal QR code, personal link, native Share action, short code) resolving to one underlying invitation identity, for driver→passenger, driver→driver, and potentially later passenger→passenger relationships.
- Preserved attribution: who invited whom, through which channel, whether registration occurred, whether the invitee became active.
- **Explicit, load-bearing distinction the Product Owner insists on preserving:** "invited by" is **not** the same as "legally owns this customer forever." Invitation attribution and relationship ownership are separate concepts. This distinction is consistent with, and reinforces, the already-`[RATIFIED]` Relationship Protection principle (Constitution Sections 3, 18) — it does not contradict anything ratified, but the invitation mechanism itself is new and unratified.
- A domino/self-propagating growth hypothesis (an initial driver brings passengers → demand grows → more drivers join → they bring their own passengers → further growth) — explicitly `[HYPOTHESIS]`, not a validated fact, per the Product Owner's own framing.

## 8. Personal-Client Role (Restated Precisely, Not Diminished)

**`[RATIFIED]`**: Personal Client Relationship is a real, named, first-class product concept (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1) — the recognized, recurring relationship between a driver and a passenger or corporate customer, existing independent of any single order (DOMAIN_MODEL.md Section 13). It is **not** ownership, **not** a commercial relationship, and **not** guaranteed future assignment.

**Correctly scoped, per Section 1 above:** it is one important, protected mechanism within a broader platform — not the platform's entire reason for existing. Its lifecycle (creation, confirmation, exclusivity, termination) remains almost entirely `[OPEN]` (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 3, Part 8) — see `PIOS_OPEN_QUESTIONS.md`.

## 9. Driver-to-Entrepreneur Direction

**Entirely `[CURRENT DIRECTION]` / `[HYPOTHESIS]` — no ratified document mentions this at all.** Per Product Owner context: a driver may evolve from personally driving → owning their own passenger relationships → building a driver network/team → organizing a functioning local transportation micro-business, eventually performing fewer rides personally while the team continues serving real demand.

**Explicit, mandatory distinctions, preserved exactly as instructed, not collapsed:**
- **Network originator / team builder** (a role a driver may informally take on) is distinct from
- **Legal ownership** (of other drivers, vehicles, or the business entity) is distinct from
- **Economic rights** (any claim to a share of value another driver or the network generates).

The latter two are `[OPEN]`. Do not call a network-originating driver the legal "owner" of other drivers or passengers anywhere in this repository.

## 10. Team / Network Hypothesis

**Entirely `[CURRENT DIRECTION]` / `[HYPOTHESIS]`.** PIOS may eventually consist of multiple driver-created local networks/teams (conceptually: PIOS → Network A, Network B, Network C — each with its own drivers, passengers, demand, and relationships), connected through common infrastructure that may allow cross-network demand fulfillment under transparent rules. Team ownership, economic rights, hierarchy, cross-network allocation, and governance are all `[OPEN]`. **Do not implement any of this.**

**Anti-MLM guardrail, explicitly preserved `[CURRENT DIRECTION]`, consistent with already-`[RATIFIED]` material:** recruitment alone must never create automatic economic entitlement (A recruits B → A automatically earns from B → B recruits C → automatic upstream multi-level earnings is explicitly rejected as a design). This is not itself a new exclusion — it reinforces the already-`[RATIFIED]` exclusion of Reciprocity/Mutuality from the Constitution's Fundamental Laws (Section 18: "Reciprocity is deliberately not included as a law... carried instead as an open question") and ADR-034 Part 4's treatment of "reciprocal balancing" as one of several unapproved, unranked candidate strategies. Any eventual team/network economics must tie value to real, verifiable activity (active demand, real transportation, functioning service) — the exact mechanism remains `[OPEN]`.

## 11. Economic Direction

**`[OPEN]`, restated precisely `[CURRENT DIRECTION]` on top of it:** PROJECT_CONSTITUTION.md Section 19 already states the commercial model is genuinely undecided, not decided against a commission model; DOMAIN_MODEL.md Section 14 excludes "any pricing, commission, or regulatory rule" from every level of current documentation; PRODUCT_BASELINE_V2.md Section 13 names subscription as a **leading hypothesis only**, not ratified. Product Owner direction adds: explore **not** taking a traditional per-ride percentage commission; possible directions include subscription, an infrastructure/service fee, or another fixed/value-based model. **Do not invent pricing. Do not claim "0% commission forever" as a ratified commercial promise anywhere** — the task commissioning this Project Brain explicitly forbids it, and no document supports it.

**Already-`[RATIFIED]` constraint this direction must respect regardless of which model is eventually chosen:** payment or subscription status may never secretly purchase priority in fair shared-network opportunity allocation (`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8, extended from `PRODUCT_BASELINE_V2.md` Sections 11/13 and `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md` Section 7).

## 12. Legal Uncertainty

**Entirely `[OPEN]`.** No ratified document selects an operating structure. Product Owner context names three candidate structures for a future, separate legal-design analysis: (A) PIOS itself performs regulated order-service functions where required; (B) PIOS is SaaS/infrastructure and a separate, authorized operator performs regulated functions; (C) PIOS enables direct participant interaction without itself performing regulated dispatch/order-service functions, only if legally supportable. **None is selected.** PRODUCT_FOUNDATION.md Section 12 already acknowledges, generally, that "transportation-for-hire is subject to regulatory and licensing regimes that vary by jurisdiction; the product must be conceivable without assuming a single, universal regulatory environment" — this is an acknowledgment of the constraint, not a resolution of it. Prototype/development work may proceed without asserting any legal conclusion.

## 13. Current Development Philosophy

**`[CURRENT DIRECTION]`.** Move quickly toward an installable, mobile-first web application (PWA) tested with real drivers: build the smallest usable vertical slice → put it on phones → test with real drivers/passengers → observe behavior → add functions based on real evidence. Explicitly **do not** attempt to build the entire theoretical PIOS model first. See `PIOS_MVP_V01.md` for the candidate slice sequence — none of it is authorized for implementation by this Project Brain.

**Frontend technology is already `[RATIFIED]`** (ADR-024): TypeScript throughout; React for web surfaces; React Native for mobile surfaces. **What is not yet ratified:** any PWA-specific packaging decision, any installability mechanism, and the entire invitation/onboarding flow described in Sections 7–10 above. **Verified fact, not opinion:** zero frontend code of any kind exists in this repository as of the commit named in `PIOS_CURRENT_STATE.md` — this is a technology *decision*, not an implementation.

---

Where this file states `[OPEN]` or `[HYPOTHESIS]`, treat it as exactly that — a question or an untested idea, never a promise made on the product's behalf. See `PIOS_OPEN_QUESTIONS.md` for the full, prioritized register.
