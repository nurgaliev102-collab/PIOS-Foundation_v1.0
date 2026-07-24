# PIOS Master Context v2.0

**Read `AI_HANDOFF.md` first if you have not.** This file is a full-repository consolidation, rebuilt from a complete scan of `docs/`, `docs/ADR/`, `project-brain/`, and the current backend/frontend code state, superseding the version of this file created by commit `2995548` ("PIOS Project Brain canonical consolidation v1.0"), which predates the Proposal aggregate, ADR-035, the Electronic Dispatcher design work, and the frontend entirely. No new idea is introduced anywhere in this document. Every claim is either traced to a specific `docs/` source, directly verified against current code, or explicitly tagged as unratified direction/hypothesis, exactly as the tag legend below requires.

## What "single source of truth" means for this file, precisely

`PROJECT_CONSTITUTION.md` Section 4 already states the governing rule: *"For any given fact, decision, or rule, exactly one document is authoritative."* This file does not change that rule and does not sit above the Constitution — it cannot, without a Section 17 constitutional amendment no one has made. What this file *does* do is what "single source of truth" can honestly mean in practice: **one place a developer or agent can go to get the full, current, deduplicated picture**, with every fact still traceable to its one authoritative origin. Where this file paraphrases something `docs/` already states, `docs/` remains authoritative if the two ever diverge — the same rule `AI_HANDOFF.md` already established, unchanged here.

## Tag Legend

| Tag | Meaning |
| --- | --- |
| `[RATIFIED]` | Explicitly approved by Constitution, Product Decision, or ADR. |
| `[CURRENT CODE]` | Directly verified in current `backend/`/`frontend/`. |
| `[CURRENT DIRECTION]` | Product Owner intent, not yet ratified. |
| `[HYPOTHESIS]` | Explicitly untested, including the Network Pilot's own H1/H2. |
| `[OPEN]` | Not yet decided by anyone with authority to decide it. |
| `[DEFERRED]` | Postponed with a stated reason. |
| `[CONFLICT]` | Two sources genuinely disagree; recorded, not resolved here. |

---

## 1. Product Identity

**`[RATIFIED]`** (PROJECT_CONSTITUTION.md §19; PRODUCT_FOUNDATION.md §1): PIOS is a trustworthy, transparent mechanism connecting independent drivers with transportation demand from multiple, heterogeneous sources, without concentrating unaccountable control over that connection in a single intermediary.

**What PIOS is explicitly not** (Constitution §19): not an opaque aggregator; not a hidden dispatcher; not an owner of driver relationships; **not necessarily commission-based** — stated precisely, not overclaimed: the commercial model is genuinely undecided (§26), not decided against commission.

**Core problem `[RATIFIED]`** (PRODUCT_FOUNDATION.md §4): *"the absence of a platform that allocates work to independent drivers in a way that is fair, explainable, and transparent... a problem of trust and structural accountability in how work is allocated, not a problem of routing, payment processing, or any other specific mechanism."*

**Vision `[RATIFIED]`** (PRODUCT_FOUNDATION.md §2): more than a single-purpose taxi-hailing app — an extensible ecosystem drivers, passengers, fleets, and organizations can build durable relationships on.

## 2. Ecosystem / Actors

**`[RATIFIED]`** (PRODUCT_FOUNDATION.md §6–7): Driver (primary stakeholder, independent, self-determined availability), Passenger, Dispatcher (historical role, superseded by Dispatch), Fleet (participant `[RATIFIED]`, domain owner `[OPEN]` — DOMAIN_MODEL.md §5, "Domain Decision Required"), Corporate Customer, Platform Administrator, Partner, External Service.

**`[CURRENT DIRECTION]`, unratified**: Network/Team Originator, Coordinator-as-production-actor. The only ratified "Coordinator" is the Network Pilot's own manual, pilot-scoped role (PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md §3) — not a production actor, not Dispatch, not a Dispatch override.

## 3. Fundamental Laws (Constitution §3, §18 — immutable without a constitutional amendment)

- **Driver Independence** — never an employee; self-determined availability/participation.
- **Fair Dispatch** — mechanisms connecting riders and drivers evaluated for fairness, not merely efficiency.
- **Transparency** — platform behavior must be explainable; opacity is never an acceptable trade-off.
- **No Hidden Algorithmic Decisions** — an algorithmic decision must remain as explainable as the human process it replaces.
- **Relationship Protection** — durable driver↔client relationships are never owned or captured by the platform.
- **Trust Preservation** — every decision weighed by whether it strengthens or weakens participant trust.

**Reciprocity is deliberately excluded as a law** (Constitution §18) — ADR-034 names "reciprocal balancing" only as one unranked, unapproved candidate Assignment Policy strategy. Carried as an open question (§9 below), never as a settled principle.

## 4. Governance Hierarchy (Constitution §5, unchanged)

```
Constitution → Product Decisions → ADR → Architecture → Domain → API → Database → Implementation
```

`project-brain/` (including this file) sits alongside this hierarchy, not inside it (AI_HANDOFF.md).

---

## 5. Architecture State

### 5.1 ADR status — a systemic, project-wide gap, stated plainly

**Every one of the 35 ADRs (`docs/ADR/ADR-001` through `ADR-035`) is formally marked `## Status: Proposed`.** Not one has been marked `Accepted`. `docs/README.md` itself states Proposed ADRs "require explicit approval by the Human Architect... before they become Accepted" (per ADR-007) — that formal approval step has not happened for any ADR in this repository's history, despite every other document (including this one, historically) treating them as binding, settled architecture. This is `[CONFLICT]` between the ADR lifecycle as documented and the ADR lifecycle as actually practiced — not resolved here; flagged for whoever owns Architecture Governance (Constitution §6).

### 5.2 Eight capability modules (MODULE_STRUCTURE.md, `[RATIFIED]`)

| Module | Status |
| --- | --- |
| Order Management | `[CURRENT CODE]` — full lifecycle, real REST (`POST /v1/orders`) |
| Dispatch | `[CURRENT CODE]` — Assignment + Proposal aggregates, orchestration |
| Driver Management | `[CURRENT CODE]` — availability lifecycle, real event publishing |
| Passenger Experience | `[CURRENT CODE, narrow]` — representation only, no persistence, no inbound endpoint |
| Payments, Administration, Analytics, Notifications | **Not scaffolded** — zero code. Notifications is entitled to become a module (ADR-033) but has **no ratified event-consumption relationship** and is `[DEFERRED]` (PRODUCT_DECISION_NOTIFICATIONS.md: "no currently ratified, concrete business need"). |

### 5.3 The pre-commitment business fact — `Proposal` — and its documentation lag

**`[RATIFIED]`**: a self-contained business fact exists between Dispatch's own candidate selection and a driver's own confirmation, distinct from Assignment (PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md). Its architectural form is a **separate Aggregate Root**, owned exclusively by Dispatch, referencing `Assignment` only by identity (ADR-035).

**`[CURRENT CODE]`**: the code has settled on the name **`Proposal`** — class `Proposal.kt`, table `proposals`, commands `ProposeDriverCommand`/`AcceptProposalCommand`/`DeclineProposalCommand`/`LapseProposalCommand`, REST surface `POST /v1/proposals`, `POST /v1/proposals/{id}/accept|decline|lapse`, `GET /v1/proposals`. Lifecycle: `OPEN → ACCEPTED/DECLINED/LAPSED`. Root Invariant: at most one `OPEN` proposal per order at a time.

**`[CONFLICT]`, directly verified**: `DOMAIN_MODEL.md:40` still names this "**Pre-Commitment Business Fact (Dispatch) — Name Not Yet Ratified**." `EVENT_CATALOG.md` contains **zero** mentions of `Proposal`, `OrderProposed`, `ProposalAccepted`, `ProposalDeclined`, or `ProposalLapsed`, despite all four being real, tested domain events in current code. This is a known, disclosed gap — `ProposalApplicationService`'s own KDoc states outbox publication for these events is deliberately not wired in because "EVENT_CATALOG.md and INTERFACE_CONTRACTS.md currently ratify only two Dispatch-outbound events" (`OrderAssigned`, `AssignmentAccepted`). The Domain-layer documentation has not caught up to code that has existed and been tested for several sprints.

### 5.4 Proposal → Assignment orchestration — `[CURRENT CODE]`

`ProposalAssignmentOrchestrationService.acceptProposal` sequences: validate/accept the Proposal, then create the Assignment (`Assignment.create`, unchanged invariant: one active assignment per order). Chosen mechanism: plain Application Service coordination, not a Domain Event Handler or Process Manager — evaluated and rejected at the time as premature complexity (documented in the service's own KDoc). ADR-035 Part 4 explicitly leaves the *execution/orchestration mechanism* for this sequence as a distinct, still-open architectural question — not decided by ADR-035, not closed off by the current implementation choice either.

**Known, disclosed limitation, `[CURRENT CODE]`, not yet closed**: the Proposal write and the Assignment write are not one atomic transaction. A failure between them can leave a Proposal `ACCEPTED` with no corresponding Assignment — reproduced and confirmed against real PostgreSQL in prior validation work. As of the most recent work session, the read-before-write TOCTOU windows in `ProposalApplicationService`/`DispatchAssignmentApplicationService` (self-fetching overloads) and in the three REST controller methods that used to read state externally (`declineProposal`, `lapseProposal`, `assignOrder`) have been closed by moving those reads inside their own `TransactionRunner` boundaries. The cross-aggregate gap (Proposal commit vs. Assignment commit as two separate transactions) has **not** been closed — three architectural options for closing it (single shared transaction, Saga/Process Manager, Domain Event orchestration) were evaluated and documented, but no option has been selected or implemented; this remains `[OPEN]`, and any implementation of it would need to be reconciled against `PERSISTENCE_ARCHITECTURE.md` §6/§7, which is currently silent on intra-domain, cross-aggregate transactions.

### 5.5 Deprecated manual path — `[CURRENT CODE]`

`AssignmentController.assignOrder` (`POST /v1/assignments`) still exists, marked `@Deprecated`, retained as a manual override, not removed (per ADR-015's evolution discipline — no removal without a stated migration window). The normal path is Proposal → accept → orchestration.

### 5.6 Frontend — `[CURRENT CODE]`, correcting a prior internal contradiction

**A React 19 + TypeScript + Vite frontend exists** (`frontend/`) — this corrects the previous version of this file and of `PIOS_CURRENT_STATE.md`, both of which claimed no frontend exists anywhere, while `PIOS_CURRENT_STATE.md` simultaneously referenced `frontend/README.md` and a Driver Home REST call in its own "REST Flows" section — an internal self-contradiction in that file, now superseded by this correction. Current frontend: `Coordinator` page (creates Proposals, checks status), `DriverHome` page (lists/accepts/declines Proposals, availability toggle). No authentication anywhere. No Driver-creation REST endpoint exists — a real Driver must be seeded manually via SQL before the frontend flow works (documented operational gap, not silently papered over — see `README.md` "Running Locally" §6).

### 5.7 Tests — `[CURRENT CODE]`, most recently verified

`dispatch` module: 188 tests. 183 pass. 5 fail, all attributable to a real, non-isolated PostgreSQL test/dev database accumulating fixture rows across repeated runs (no test cleanup, `PostgreSQLTestDatabase.dataSource` points at the same persistent `pios_dispatch` database used for manual E2E work) — confirmed by direct SQL inspection to be pre-existing data pollution, not a logic defect, in 4 of 5 cases provably identical to pre-change behavior. Exact current counts for the other three backend modules were not re-verified in this consolidation; the last independently verified full-suite figure (`PIOS_CURRENT_STATE.md`, commit `f20c83f`) was 185 tests / 0 failures across all four modules — that figure predates the Proposal work and should not be quoted as current without re-running it.

---

## 6. Product Decision Chain — all `[RATIFIED]`, none withdrawn or contradicted

| Decision | What it ratifies |
| --- | --- |
| Dispatch Philosophy v1.0 | Fairness ranked first, above efficiency; fairness is **procedural**, not distributional |
| Personal Client Relationship v1.0 | First-class concept; not ownership, not commercial, not guaranteed assignment; lifecycle almost entirely `[OPEN]` |
| Fulfillment Authority and Scope v1.0 | Only the requester may authorize DIRECT→NETWORK scope expansion; Dispatch may never self-initiate it |
| Opportunity, Acceptance & Commitment Semantics v1.0 | Acceptance = Commitment; `Assignment`'s `CREATED` state is a single-driver proposal, not a candidate pool |
| Eligibility and Minimum Trust Boundary v1.0 | Availability ≠ Eligibility — a structural distinction; concrete criteria `[OPEN]` |
| Fair Opportunity Policy v1.0 | Fairness confirmed procedural; 7 candidate mechanisms classified (procedural fairness, equal opportunity, balanced participation, FIFO, Round Robin, ranking/scoring, AI selection) — **none chosen** |
| MVP Pilot Boundary v1.0 | The existing Assignment flow already supports a 100%-manual pilot fallback loop; names what must NOT be built before pilot validation |
| Opportunity Before Assignment v1.0 | Ratifies the pre-commitment business fact exists (architectural form resolved separately by ADR-035) |
| Electronic Dispatcher MVP Blockers v1.0 | Ratifies exactly three decisions: **Cycle-Exhausted Behavior** (limited automatic retry, then honest customer outcome; Coordinator is pilot/debug/admin-only, never a standing fallback), **Proposal Timeout Policy** (single, non-adaptive, configurable parameter — exact value not ratified), **Concurrent Proposal Policy** (strictly sequential, max one `OPEN` Proposal per order, no broadcast without a new decision) |
| Notifications v1.0 | Notifications entitled to become a module; `[DEFERRED]` — no ratified consumption need found |

**The Electronic Dispatcher design blueprint** (`ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md`) is explicitly **`Status: Proposed`, not a Product Decision or ADR** — a design blueprint consolidating open questions, not resolving them, pending Product Owner review.

**The one true remaining blocker to implementing Automatic Dispatch**: no concrete Fair Opportunity Policy has been *selected* — only FIFO has been *named* as the simplest candidate (Fair Opportunity Policy v1.0 §10). This is not resolvable by an AI agent per ADR-002/ADR-034's standing prohibition on inventing the algorithm; it requires a Product Owner decision.

---

## 7. Current Product Direction (not yet ratified anywhere)

Everything below is `[CURRENT DIRECTION]` and/or `[HYPOTHESIS]` — Product Owner context, never promoted to ratified status by being repeated here:

- **Reframing, not new information**: the electronic dispatcher is the platform's central, load-bearing capability (ADR-002, Constitution §3's Fair Dispatch already establish this) — the Network Pilot's personal-client-fallback scenario is one mechanism among several, not the whole product. `PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md` Part 5 already self-identified this emphasis risk before any external correction arrived.
- **Independent-driver model, more specific than ratified**: PIOS is not automatically the employer, vehicle owner, transportation provider, or payment recipient. Exact legal structure `[OPEN]` — three candidate structures named, none selected; explicitly forbidden to claim any regulatory exemption anywhere.
- **Invitation/attribution/referral system**: universal invitation identity (QR/link/share/code), driver→passenger and driver→driver, "invited by" explicitly distinct from relationship ownership. No vocabulary for this exists in `DOMAIN_MODEL.md`/`EVENT_CATALOG.md`/any ADR.
- **Driver→entrepreneur / team-network hypothesis**: a driver may build a network/team over time. Network originator / legal ownership / economic rights are three explicitly distinct, mostly `[OPEN]` concepts — do not conflate. Anti-MLM guardrail: recruitment alone must never create automatic economic entitlement. **Do not implement.**
- **Economic direction**: explore not taking a traditional per-ride commission (subscription, infrastructure fee, or other value-based model) — leading hypothesis only, not ratified. Payment/subscription status may never secretly purchase priority in fair allocation (already `[RATIFIED]`, Fair Opportunity Policy v1.0 §8).
- **Development philosophy**: smallest usable vertical slice → real phones → real drivers → observe → iterate, explicitly rejecting building the entire theoretical model first.

---

## 8. Known Documentation Contradictions and Gaps (full inventory: separate report from this same consolidation effort)

1. **`.claude/CLAUDE.md` vs. root `CLAUDE.md`** — `[CONFLICT]`. The `.claude/` version frames ChatGPT as "the system architect" and Claude as an "implementation engineer" that "does not redefine architecture on its own" — this appears nowhere in `PROJECT_CONSTITUTION.md` or ADR-007, and contradicts the Constitution's own Architecture Governance (§6, Chief Software Architect / equivalent project authority, not an external LLM). Not resolved here; requires a governance decision on which file is authoritative.
2. **ADR Proposed-vs-Accepted** — see §5.1 above.
3. **`docs/README.md`'s own ADR index is incomplete** — its ADR table lists ADR-001–026 then jumps to ADR-035, omitting ADR-027 through ADR-034 (eight real, existing ADRs). Its Product section never links `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, or `PRODUCT_DECISION_NOTIFICATIONS.md`, despite all three being ratified and in active use elsewhere.
4. **`DOMAIN_MODEL.md` / `EVENT_CATALOG.md` Proposal lag** — see §5.3.
5. **Prior `project-brain/` snapshot staleness** — the version of this file and of `PIOS_CURRENT_STATE.md` created by commit `2995548` predate ADR-035, `Proposal`, the Electronic Dispatcher design blueprint, its MVP-blocker ratification, and the entire frontend. This file supersedes that gap for product/architecture identity; `PIOS_CURRENT_STATE.md`, `PIOS_DECISION_LEDGER.md`, `PIOS_PRODUCT_MODEL.md`, `PIOS_MVP_V01.md`, and `PIOS_OPEN_QUESTIONS.md` were **not** rewritten as part of this consolidation and are now stale relative to this file specifically where they discuss frontend existence, Proposal/ADR-035, or the Electronic Dispatcher — treat their code-state claims as superseded by §5 above until they are themselves updated.

Duplicates folded into this consolidation, not deleted from `docs/` (Constitution §7/§9, "never delete documentation" — original documents remain intact and citable): the five-document Network Pilot chain (Experiment → Execution Plan → Operations Checklist → Launch Pack → Launch Kit) is represented here only as "the ratified MVP Pilot Boundary and its running, manual pilot" (§6); the three successive "state of the product" documents (`PRODUCT_BASELINE_V2.md`, `PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md`, the prior `PIOS_MASTER_CONTEXT.md`) are represented here as one current picture, with each original preserved as the historical record of its own moment.

---

## 9. Open Questions (consolidated; each requires a Product Owner decision — Constitution §9, §26)

**Blocks engineering work directly:**
- Fair Opportunity Policy mechanism selection (§6) — the one true Automatic Dispatch blocker.
- Cross-aggregate transaction gap for Proposal→Assignment (§5.4) — an architecture decision, evaluated, not yet made.

**Blocks public/commercial launch, not prototype work:**
- Legal operating structure (three candidates, none selected).
- Monetization model (commission vs. subscription vs. infrastructure fee — genuinely undecided).
- Eligibility concrete criteria (beyond manual admission).
- Personal Client Relationship lifecycle (creation, confirmation, exclusivity, termination — almost entirely open).

**Entirely hypothesis/unratified, do not build:**
- Invitation/attribution system, team/network graph, cross-network demand, driver→entrepreneur economics, domino growth loop.

**No genuine conflict found** between current Product Owner direction and anything already ratified — every item above is either a reaffirmation of already-established emphasis (the dispatcher's centrality) or a genuinely new, correctly-still-open extension. The one real conflict on record is the documentation-governance one in §8, not a product one.

---

## 10. What This Document Does Not Authorize

- No implementation. Every task still requires its own explicit commission per `.ai/EXECUTION_PROTOCOL.md`.
- No resolution of any `[OPEN]` item. Describing a candidate answer is not choosing one.
- No claim that this file outranks `PROJECT_CONSTITUTION.md`, any Product Decision, or any ADR — see the note at the top of this file.
