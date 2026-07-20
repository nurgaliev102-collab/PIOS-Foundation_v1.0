# PIOS Decision Ledger

Read `AI_HANDOFF.md` first if you have not. This is a chronological, additive history of what was actually decided, built, or reframed in this repository — verified against `git log` on current HEAD (`f20c83f`, 82 commits, `8b314dd` "Initial commit" through `f20c83f`), not reconstructed from memory. **Entries are never deleted or edited to erase what they originally said** — a later entry that changes course points back to the one it changes, per `AI_HANDOFF.md`'s "never rewrite history" rule.

Every commit hash below was independently verified with `git show -s --format=... <hash>` against current HEAD before being recorded.

---

## Phase 1 — Constitutional and Architectural Foundation

| Commit | Decision | Status | What it established |
| --- | --- | --- | --- |
| `8b314dd` | Initial commit | active | Repository created |
| `246b985` | Foundation v1.0 | active | Repository scaffolding |
| `5c56ca1` | Add Project Constitution | **`[RATIFIED]`**, active | PROJECT_CONSTITUTION.md — highest authority |
| `722500f` | ADR Foundation v1.0 | active | ADR-001 through initial set |
| `62de094` | Product Foundation v1.0 | **`[RATIFIED]`**, active | PRODUCT_FOUNDATION.md — product domain, problem, ecosystem |
| `e6af779` | System Architecture v1.0 | active | SYSTEM_ARCHITECTURE.md |
| `596db2e` | ADR Expansion Pack v1.0 | active | Further ADRs |
| `cf43499` | Domain Model v1.0 | **`[RATIFIED]`**, active | DOMAIN_MODEL.md — aggregates, entities, events, commands |
| `fa800aa` | Event Catalog v1.0 | **`[RATIFIED]`**, active | EVENT_CATALOG.md — 6 catalogued events |
| `84e2896` | Use Case Catalog v1.0 | active | USE_CASE_CATALOG.md |
| `1883d26` | Application Architecture v1.0 | active | APPLICATION_ARCHITECTURE.md |
| `43d9339` | API Specification v1.0 | **`[RATIFIED]`**, active | API_SPECIFICATION.md — no transport defined |
| `d435d51`, `ed806f8` | Conceptual/Logical Data Model v1.0 | active | Data modeling layer |
| `d96a492` | ADR-021 Database Technology Strategy v1.0 | active | Evaluation framework |
| `c71eeb4`, `d3c0e7e` | Persistence Architecture, Database Design v1.0 | active | Persistence layer docs |
| `d32ec38` | Module Structure v1.0 | **`[RATIFIED]`**, active | 8 capability modules named |
| `eb4ca94` | Interface Contracts v1.0 | **`[RATIFIED]`**, active | 4 justified module-to-module contracts |
| `77bfb5f` | Engineering Guidelines v1.0 | active | Coding/process discipline |
| `3653fd1`, `50060a9`, `b849956`, `abdc584`, `1957dd7` | ADR-022 through ADR-026 | **`[RATIFIED]`**, active | Kotlin/Spring Boot backend (ADR-023); **TypeScript/React/React Native frontend (ADR-024)**; PostgreSQL per-domain (ADR-025); independent deployability (ADR-026) |

## Phase 2 — Scaffolding and First Implementation

| Commit | Decision | Status | What it established |
| --- | --- | --- | --- |
| `25ec60b`, `97e616e`, `320d33c`, `2089d1f` | Scaffolding/Initialization plans, Repository Foundation v1.0 | active | 4 MVP modules scaffolded (order-management, dispatch, driver-management, passenger-experience) |
| `4f269ef`, `557e4c2`, `a6c0df2`, `c0e9e8e`, `0a6559b` | Driver Management Availability, Order Management Lifecycle, Dispatch Assignment/Acceptance, Passenger Experience Order Submission (all v1.0) | **`[CURRENT CODE]`**, active | Core domain classes (`Order`, `Assignment`, `Driver`) and application services first implemented |
| `2d635df` | ADR-027: MVP Integration Mechanism | **`[RATIFIED]`**, `[SUPERSEDED in practice for 2 of 3 flows, not superseded as a document]` | Test-scoped-only contract verification between modules — no production dependency, no real transport yet |
| `02795f6`, `ea9c73a` | MVP Integration Contract Boundary, MVP Application Flow Verification v1.0 | active | Cross-module contract tests |
| `6ab9fbb`, `79af2b0`, `4f0d968` | Persistence Foundation, Application Persistence Wiring, Persistence Quality Foundation v1.0 | **`[CURRENT CODE]`**, active | In-memory then PostgreSQL-backed repositories wired |
| `4262c2f`, `1d6470c`, `07fe4f6` | PostgreSQL Persistence — Order Management, Driver Management, Dispatch v1.0 | **`[CURRENT CODE]`**, active | Real PostgreSQL persistence for all three, per-module database |

## Phase 3 — Event Infrastructure Foundation

| Commit | Decision | Status | What it established |
| --- | --- | --- | --- |
| `ef08738` | ADR-028: Production Integration Transport Decision | **`[RATIFIED]`**, active | Hybrid by category: events → message broker; direct interaction → REST |
| `09909e8` | ADR-029: Message Broker Selection | **`[RATIFIED]`**, active | RabbitMQ selected |
| `74f817d` | ADR-030: Event Schema and Versioning Strategy | **`[RATIFIED]`**, active | Exclusive schema ownership, breaking/non-breaking policy |
| `8886cb4` | ADR-031: Event Infrastructure Foundation Architecture | **`[RATIFIED]`**, active | Exchange-per-domain, queue-per-consumer topology |
| `3c65312` | ADR-032: Reliable Event Publication Strategy | **`[RATIFIED]`**, active | Transactional outbox, in-process polling relay |
| `aea1693`, `c7be3a5`, `80f16a5` | Outbox Foundation, RabbitMQ Event Publishing Foundation v1.0 (+ Hardening Review) | **`[CURRENT CODE]`**, active | First real outbox + RabbitMQ publisher (Order Management, then Driver Management) |
| `a04b09b`, `8f1d766` | ADR-033: Notifications Module and Event Consumption Boundary; Product Decision: Notifications v1.0 | **`[RATIFIED]`**, `[DEFERRED]` | Notifications entitled to become a module; no ratified event-consumption relationship found; "no currently ratified, concrete business need" — deliberately not built |
| `9d7cea9` | Driver Management Event Publishing v1.0 | **`[CURRENT CODE]`**, active | Driver Management's own outbox/publisher completed |
| `b794d8f` | Dispatch Consumer Foundation v1.0 | **`[CURRENT CODE]`**, active | Dispatch's own `DriverAvailabilityChanged` consumer (idempotent, retried, dead-lettered) |
| `eae9986` | ADR-034: Assignment Policy and Dispatch Decision Architecture | **`[RATIFIED]`**, active | Names a future, swappable Assignment Policy port; algorithm itself deliberately still undecided |

## Phase 4 — Product Decision Chain (the "Personal-Client Fallback" Sequence)

| Commit | Decision | Status | What it established |
| --- | --- | --- | --- |
| `e6f602b` | Product Decision: Dispatch Philosophy v1.0 | **`[RATIFIED]`**, active | Fairness ranked first, above efficiency; fairness is **procedural**, not distributional; 4 open questions named (fairness metric, PCR priority, shared-order balancing, incentive model) |
| `9b69223` | Product Decision: Personal Client Relationship v1.0 | **`[RATIFIED]`**, active | PCR is a first-class concept, **not** ownership, **not** commercial, **not** a guaranteed assignment; almost its entire lifecycle (creation, confirmation, exclusivity, termination) left `[OPEN]` |
| `75af664` | Consolidate product philosophy into PROJECT_CONSTITUTION.md | **`[RATIFIED]`**, active | Constitution Sections 18–26 added: Fundamental Laws, Identity, Open Product Questions (an explicit governance act adding detail, not amending meaning, per Section 17) |
| `a9b90fa` | Product Baseline v2.0 after CORE validation | active, analysis-only | Reconciled a candidate consolidated model against full repo authority; not itself a Product Decision |
| `330e2e7` | Product Decision: Fulfillment Authority and Scope v1.0 | **`[RATIFIED]`**, active | Only the requester may authorize scope expansion from DIRECT to NETWORK; Dispatch may never self-initiate it |
| `5465779` | Product Decision: Opportunity, Acceptance and Commitment Semantics v1.0 | **`[RATIFIED]`**, active | Acceptance = Commitment (already true in code); Assignment's CREATED state is a single-driver proposal, not a candidate pool; whether a true multi-candidate Opportunity is ever built remains `[OPEN]` |
| `52917d0` | Product Decision: Eligibility and Minimum Trust Boundary v1.0 | **`[RATIFIED]`**, active | Availability ≠ Eligibility, a structural distinction; concrete criteria `[OPEN]` |
| `bafd53d` | Product Decision: Fair Opportunity Policy v1.0 | **`[RATIFIED]`**, active | Fairness confirmed procedural (reused, not redecided); 7 candidate mechanisms named, none chosen |
| `0c038a6` | Extend Fair Opportunity Policy v1.0 (Round Robin, AI selection named explicitly) | **`[RATIFIED]`**, active | Same document extended, not superseded — Round Robin and AI selection now explicitly evaluated (`[HYPOTHESIS]`), still none chosen |
| `f8d2b42` | ADR migration strategy and architecture impact analysis | active, analysis-only | Central Finding reused later: `Assignment.create()` already accepts any externally-supplied driver — no Opportunity/Eligibility/Policy code needed to record a manual fallback |
| `c3b0446` | Product Decision: MVP Pilot Boundary v1.0 | **`[RATIFIED]`**, active | Entire pilot runs on already-existing code + 100% manual process; names exactly what must NOT be built before pilot validation |

## Phase 5 — Network Pilot Documentation

| Commit | Decision | Status | What it established |
| --- | --- | --- | --- |
| `5171fc1` | Product Experiment: Network Pilot v1.0 | **`[RATIFIED]`** (as an experiment design), `[HYPOTHESIS]` (H1/H2 themselves) | Operationalized MVP Pilot Boundary into one concrete Driver-A/Driver-B/Coordinator scenario |
| `f67a55d` | Product Experiment: Network Pilot Execution Plan v1.0 | active | Roles, exact workflow, case-log format, interview questions |
| `bb27585` | Product Experiment: Network Pilot Operations Checklist v1.0 | active | Itemized checklist form of the Execution Plan |
| `2a18a8b` | Product Experiment: Network Pilot Launch Pack v1.0 | active | Recruitment scripts, first-case runbook, safety rules |
| `aca7c21` | PIOS consolidation and implementation readiness review | active, analysis-only, **self-correcting** | **Explicitly names the over-emphasis risk** (Part 5, Part 14): the Network Pilot documents' intense focus on one scenario "risks giving a future reader the false impression that this scenario *is* the product." Names the actual product core (Part 6) and recommends Tranche 1 as the next engineering step |

**Note on Phase 4–5 as a whole, stated explicitly rather than left implicit:** every decision in this phase is internally consistent — none contradicts another, none was withdrawn. What happened is documented **emphasis concentration**, not error: nine Product Decision/Experiment documents produced in direct succession, each individually correct and narrowly scoped, whose *cumulative volume* on one scenario created a misleading impression when read in isolation from Phase 1–3's own broader, older foundation. `aca7c21` already caught and corrected this within the repository itself, before any external Product Owner correction arrived. This is a **`[SUPERSEDED — in emphasis only, not in content]`** situation: nothing in Phase 4–5 is wrong or withdrawn; its relative weight in a reader's mental model is what needed correcting, and `aca7c21` plus this Project Brain both perform that correction.

## Phase 6 — Engineering Track (Product-Decision-Independent)

| Commit | Decision | Status | What it established |
| --- | --- | --- | --- |
| `acffa83` | Implementation plan for Dispatch event publishing completion | active, planning-only | Tranche 1 design |
| `41b9b24` | Tranche 1: Dispatch Event Publishing Completion | **`[CURRENT CODE]`**, active | Dispatch's own outbox + RabbitMQ publisher for `OrderAssigned`/`AssignmentAccepted`; Order Management's real `AssignmentAccepted` consumer |
| `721ca54` | Implementation plan for Passenger Experience REST transport | active, planning-only | Tranche 2 design |
| `182520e` | Implement Tranche 2: Passenger Experience REST Transport | **`[CURRENT CODE]`**, active | Order Management's real `POST /v1/orders` REST endpoint; Passenger Experience's real REST client |
| `eaadf6a` | PIOS hardening review after Tranche 1 and Tranche 2 | active, analysis-only | Found `OutboxRelay.relay()` existed but was invoked only by tests — no production scheduler anywhere |
| `543e441` | Implementation plan for production outbox relay trigger | active, planning-only | Design for closing the relay gap |
| `e01451d` | Implement production outbox relay trigger | **`[CURRENT CODE]`**, active | `@Scheduled` `OutboxRelayScheduler` added to all three producer modules |
| `1d60f71` | PIOS vertical slice verification | active, analysis-only | Confirms every segment independently proven against real infra; no single test proves the composed whole (a documentation gap, not a functional one); technical foundation declared **READY FOR MARKET VALIDATION** |
| `f20c83f` | Network Pilot Launch Kit v1.0 | active | Consolidates the three Network Pilot documents into one operational kit |

---

## Recent Product-Direction Additions (Not Yet Commits — Supplied Directly by Product Owner)

The following did **not** arrive as a repository commit and is **not yet** a Product Decision or ADR. It is recorded here, separately from the chronological code/document history above, exactly as `AI_HANDOFF.md` requires — never silently merged into the ratified timeline above.

| Date received | Source | Content | Status |
| --- | --- | --- | --- |
| This consolidation task (`Canonical Knowledge Consolidation v1.0`, commit follows this ledger) | Product Owner context supplied directly to the AI performing this consolidation | Core identity reframing (electronic dispatcher central, personal-client fallback is one mechanism among several); independent-driver model detail; economic direction (explore non-commission models); driver→entrepreneur trajectory; domino/self-propagating network hypothesis; team/network graph hypothesis; anti-MLM principle; invitation/attribution system; passenger acquisition experience; Pilot Web App direction; candidate first vertical slice; Electronic Dispatcher MVP direction; legal design direction | **`[CURRENT DIRECTION]`** / **`[HYPOTHESIS]`** / **`[OPEN]`**, item by item — see `PIOS_MASTER_CONTEXT.md` Sections 5, 7, 9–13 and `PIOS_OPEN_QUESTIONS.md` for the precise tag on each individual claim. **None of this is ratified by this ledger or by writing it down here.** |

**What became over-emphasized:** the single Driver-A/Driver-B/Coordinator personal-client-fallback scenario, across four Network Pilot documents (Phase 5) — a documentation *volume* effect, not a factual error (see Phase 4–5 note above).

**What is NOT actually superseded:** every Product Decision in Phase 4 (Fulfillment Authority and Scope, Opportunity/Acceptance/Commitment, Eligibility, Fair Opportunity Policy, MVP Pilot Boundary) remains fully active and correctly reasoned; none is contradicted by the Product Owner's newer context. The newer context adds scope (electronic dispatcher, growth model, entrepreneurship trajectory) **around** these decisions; it does not invalidate anything they already concluded.

**What is a broader reframing rather than a contradiction:** the "electronic dispatcher is central" statement. This is not new information contradicting Phase 4–5; it is a return to emphasis already present in Phase 1 (ADR-002, Constitution Section 3's Fair Dispatch) and already self-identified as needing restatement by `aca7c21` itself (Phase 5).

---

Where this ledger is silent on a document or commit, that document was either not independently re-verified for this specific ledger entry or genuinely does not exist — never assume a citation here without checking `git log` and the file itself, per `AI_HANDOFF.md`'s own rule.
