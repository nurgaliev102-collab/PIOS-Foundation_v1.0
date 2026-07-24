# PIOS Implementation Strategy

Status: Draft — Derived from Approved Foundation. This document sequences already-ratified decisions and already-existing code into a phased implementation roadmap, from the repository's current state through a first MVR (Minimum Viable Release) and onward toward a mature platform. It creates no code, no repository file, and no feature; it authorizes no Product Decision, no ADR, and no business rule. It selects no Fair Opportunity Policy, no legal structure, and no monetization model — where a phase depends on one of these, that dependency is named, not resolved. It is not itself a milestone commission; each phase still requires its own explicit task authorization per `.ai/EXECUTION_PROTOCOL.md`.

This document derives exclusively from [`project-brain/PIOS_MASTER_CONTEXT.md`](../project-brain/PIOS_MASTER_CONTEXT.md) and the current state of `backend/`/`frontend/` as of this writing. It introduces no product philosophy beyond what `PIOS_MASTER_CONTEXT.md` already records, and changes no Product Decision.

---

## 1. Current State (Summary)

Four backend modules (`order-management`, `dispatch`, `driver-management`, `passenger-experience`) — Kotlin/Spring Boot, PostgreSQL per module, RabbitMQ eventing, transactional outbox — plus a React/TypeScript frontend (`Coordinator`, `DriverHome`) implement Order → Proposal → Driver Acceptance → Assignment end to end. Four capability modules (Payments, Administration, Analytics, Notifications) are unscaffolded. The Proposal aggregate (ADR-035) is fully implemented but `DOMAIN_MODEL.md`/`EVENT_CATALOG.md` still lag it (`PIOS_MASTER_CONTEXT.md` §5.3). A cross-aggregate transaction gap between Proposal and Assignment writes remains open (§5.4). No Fair Opportunity Policy has been selected, blocking any Automatic Dispatch work (§6). The already-ratified MVP Pilot Boundary states the *existing* Assignment flow, run manually, already suffices for pilot validation — this strategy treats that as the ratified definition of "first MVR," not something to redesign.

## 2. Two Sequencing Approaches

Before phasing the work, one structural choice matters: **how Phase 0 (stabilization) and the eventual Electronic Dispatcher work (Phase 2) relate in time.**

### Path A — Strictly Sequential

Stabilize → run the pilot → wait for the Fair Opportunity Policy decision and pilot evidence → only then begin any Electronic Dispatcher work.

- **Advantages.** Simplest to reason about; no engineering investment made against a decision that might still change; smallest chance of wasted work.
- **Disadvantages.** The Fair Opportunity Policy decision and the pilot's own evidence-gathering are not on the engineering critical path — they can proceed in parallel with architecture-only work that touches no business rule. Sequential ordering leaves that parallel capacity idle and lengthens total time-to-mature-platform for no consistency benefit.

### Path B — Parallel-Track (recommended)

Stabilize and run the pilot (Phase 1) while, **in parallel**, preparing the *architecture-only* seam Automatic Dispatch will eventually plug into — the swappable Assignment Policy port and the mechanical, already-ratified parts of PD-1/2/3 (retry bookkeeping, timeout scheduling, sequential-proposal enforcement) — without selecting or implementing any actual policy inside that seam.

- **Advantages.** Exactly mirrors the distinction the project's own architecture already draws: naming a port and its already-ratified surrounding mechanics is architecture, not business-rule invention (ADR-002/ADR-034 forbid choosing the *algorithm*, not preparing the *seam* it will occupy). The moment a Fair Opportunity Policy is ratified, Phase 2 becomes "implement one strategy behind an existing interface," not a multi-sprint architecture project — the between-decision-and-launch lag shrinks from months to a small number of sprints.
- **Disadvantages.** Real risk that the ratified policy's shape turns out to need a different port signature than anticipated, wasting some of the interface work. Requires discipline not to let "preparing the seam" quietly grow into "guessing the algorithm."
- **Why recommended.** The wasted-work risk is small and bounded (an interface, not a feature); the benefit is real and compounding (the single named blocker to Automatic Dispatch — the policy decision — stops being also an engineering-lead-time blocker). This is the path used below.

---

## 3. Phase 0 — Stabilize the Foundation

**Goal.** Bring the already-built system (backend, frontend, Proposal/Assignment flow) to a state that is fully tested, internally consistent, and accurately documented — with no new product surface.

**Completion criteria.**
- Full test suite green across all four backend modules, re-verified by real execution (not quoted from a stale snapshot — `PIOS_MASTER_CONTEXT.md` §5.7 explicitly warns against this).
- Cross-aggregate transaction gap (Proposal↔Assignment) closed — see the comparison below.
- `DOMAIN_MODEL.md` names the Proposal aggregate (resolving the "Name Not Yet Ratified" lag) and `EVENT_CATALOG.md` catalogues `OrderProposed`/`ProposalAccepted`/`ProposalDeclined`/`ProposalLapsed`.
- `docs/README.md`'s own ADR table and Product section are complete (ADR-027–034, the three missing Product Decision links).
- The `.claude/CLAUDE.md` vs. root `CLAUDE.md` governance conflict is resolved by whoever owns Architecture Governance (Constitution §6) — this strategy cannot resolve it; it can only carry it forward as a blocking item for that owner.

**Dependencies.** None — this phase can start immediately; it is pure hardening and documentation-catch-up on work already done.

**Sub-decision: how to close the transaction gap.** Three options exist on record, none yet chosen:

| Option | Advantage | Disadvantage | Fit here |
| --- | --- | --- | --- |
| **A. Single shared transaction** | Closes the gap by construction; smallest code change (`ProposalAssignmentOrchestrationService` becomes the transaction owner) | Requires an explicit persistence-architecture clarification — `PERSISTENCE_ARCHITECTURE.md` is currently silent on intra-domain, cross-aggregate transactions, not opposed to them | **Recommended for Phase 0** — smallest, most reversible, no new architectural concept, closes the concrete defect fastest |
| **B. Saga / Process Manager** | Extensible to future compensating actions | Needs a compensating action for `Proposal.ACCEPTED` that does not exist today; genuinely new persisted concept | Deferred — disproportionate to the problem at MVR scale |
| **C. Domain Event orchestration** | Decouples the two writes | Requires reopening the deliberate choice not to wire Proposal events to the outbox yet, and re-litigating a rejected "premature abstraction" finding from Sprint IMPLEMENTATION-004 | Deferred — same reasoning as B |

**Risks.** Scope creep toward Automatic Dispatch work disguised as "hardening" — must be actively resisted; the MVP Pilot Boundary already forbids it. Documentation-catch-up (Domain/Event Catalog) touching a Domain-layer document requires the same rigor as any other Domain change — not a rubber-stamp edit.

**Effort.** Small. Mostly finishing and documenting work already substantially done; the transaction-gap fix (Option A) is a scoped, single-service change already designed in prior architecture review work.

---

## 4. Phase 1 — First MVR: Production-Hardened Manual Pilot

**Goal.** Turn the already-ratified MVP Pilot Boundary from "code exists" into "a real, releasable pilot a non-engineer can run with real drivers and passengers."

**Completion criteria.**
- A Driver can be onboarded without a manual SQL insert (the one named operational gap in `PIOS_MASTER_CONTEXT.md` §5.6) — a minimal, documented admission path, not a full Eligibility system (which remains `[OPEN]`, out of scope).
- Minimal authentication sufficient for a small, manually-vetted pilot cohort — not a general auth system.
- Frontend installable on real phones (PWA-level packaging is sufficient — ADR-024 already selects React/React Native as the technology; no new packaging ADR is required to ship an installable web app).
- Direct payment recording (already ratified for pilot purposes) instrumented well enough to produce real H1/H2 evidence.
- Basic observability (the platform must be debuggable in the field, not just in a developer's own environment).

**Dependencies.** Phase 0 complete — an unreliable or undocumented foundation should not be the thing real drivers first experience.

**Risks.** Legal/regulatory exposure is bounded but not zero even at pilot scale (`PIOS_MASTER_CONTEXT.md` §9 already notes driver legal-participation requirements are `[OPEN]`, though not blocking for a small, manually-vetted cohort). Real-device/real-user unknowns (network conditions, install friction) cannot be fully retired before real usage. Temptation to quietly build Eligibility/Opportunity/Fair Opportunity Policy "just for this pilot" — explicitly forbidden by the MVP Pilot Boundary itself.

**Effort.** Small. This phase is hardening and packaging, not new domain design — the domain work (Order, Proposal, Assignment, Driver) is already done.

---

## 5. Phase 2 — Electronic Dispatcher v1 (Automatic Dispatch)

**Goal.** Replace the manual Coordinator as the *normal* dispatch path with automatic assignment, per the already-ratified blockers (Cycle-Exhausted Behavior, Proposal Timeout Policy, Concurrent Proposal Policy) and one concrete Fair Opportunity Policy.

**Completion criteria.**
- The swappable Assignment Policy port (prepared in parallel per §2, Path B) has exactly one real strategy behind it, selected by a ratified Product Decision — this document does not name which one.
- PD-1/2/3 are implemented literally as ratified: limited automatic retry then an honest customer outcome on cycle exhaustion; a single, configurable, non-adaptive timeout; strictly sequential, one `OPEN` Proposal per order.
- The Coordinator becomes an exceptional/admin tool, never the standing fallback (already the ratified meaning of PD-1).
- Every automatic assignment decision remains explainable per the Fair Dispatch / No Hidden Algorithmic Decisions laws — an auditable "why this driver" record, not just application logs.

**Dependencies — hard blocker, not sequenced by this document.** A Product Owner decision selecting a concrete Fair Opportunity Policy. Nothing in this phase can begin its actual implementation before that decision exists; only the architecture-only seam (§2, Path B) can be prepared ahead of it.

**Risks.** Implementing any part of the actual selection logic ahead of the Product Decision would violate the standing "never invent business rules" rule (Constitution §9) — the single largest risk to this phase is impatience turning "prepare the seam" into "guess the answer." The chosen policy's real-world fairness properties are untested until real usage — plan for the port to remain swappable in production, not just in theory.

**Effort.** Medium, contingent entirely on how much of §2's parallel-track preparation has already landed by the time the decision arrives — potentially small if the seam is ready, large if it is not.

---

## 6. Phase 3 — Supporting Capabilities (Need-Gated)

**Goal.** Build Payments (record-keeping), Notifications, Administration, and Analytics — but strictly only once each has its own concrete, ratified trigger, not on a fixed schedule.

**Completion criteria (per module, independently).**
- **Payments**: a real ratified need beyond "direct payment already works" — e.g., reconciliation, subscription billing once a monetization model is chosen (Phase 4).
- **Notifications**: explicitly `[DEFERRED]` today with "no currently ratified, concrete business need" — this phase does not start for Notifications until that changes.
- **Administration / Analytics**: triggered by real operational pain from running Phase 1/2 without them (e.g., the pilot's own manual case-tracking becoming unworkable at higher volume).

**Dependencies.** A named, ratified need per module — this document does not manufacture one.

**Risks.** Building any of these ahead of a genuine need is the exact "No Premature Optimization" failure mode the project's own engineering discipline already warns against.

**Effort.** Variable per module; not estimated here since none is currently triggered.

---

## 7. Phase 4 — Product-Direction Validation

**Goal.** Convert the pilot's own evidence (Phase 1) into resolved Product Decisions on the items currently `[OPEN]` and blocking public launch: Personal Client Relationship lifecycle, monetization model, Eligibility concrete criteria, legal operating structure.

**Completion criteria.** Each item resolved as its own Product Decision document, per the existing governance process — not resolved by this strategy document, which only names the dependency.

**Dependencies.** Real pilot evidence (Phase 1); legal-design analysis (named but not performed by any engineering phase).

**Risks.** Treating pilot-scale anecdote as sufficient evidence for a permanent architecture; conflating "the pilot worked" with "the legal/monetization question is answered."

**Effort.** Not an engineering effort in the usual sense — primarily product/legal analysis with engineering participation.

---

## 8. Phase 5 — Mature Platform (Evidence-Gated)

**Goal.** Only once Phases 1–4 produce real usage and real traction data, invest in scale-oriented architecture — independent scaling of Dispatch/Location-heavy paths, broader observability, and whatever the real bottlenecks turn out to be.

**Completion criteria.** Not specified here — deliberately, since specifying it now would mean designing for a scale and a bottleneck profile that do not yet exist.

**Dependencies.** Demonstrated real traction; without it, this phase should not start.

**Risks.** The single largest risk to the whole strategy: starting this phase before Phases 1–4 produce evidence, which is the classic premature-scale failure mode — significant investment against unconfirmed assumptions, at the exact moment the actual open risks (legal structure, monetization, Fair Opportunity Policy, PCR lifecycle) are still unresolved.

**Effort.** Not estimated — genuinely open-ended, contingent on what real usage reveals.

---

## 9. Cross-Phase Risk Summary

| Risk | Where it applies | Mitigation this strategy proposes |
| --- | --- | --- |
| Scope creep toward Automatic Dispatch before its blocker is resolved | Phases 0–1 | Explicit non-goal in every phase's own completion criteria |
| Guessing the Fair Opportunity Policy instead of waiting for it | Phase 2 | Path B limited strictly to architecture-only seam preparation |
| Building supporting modules ahead of real need | Phase 3 | Need-gating, no fixed schedule |
| Treating pilot-scale evidence as sufficient for permanent decisions | Phase 4 | Explicit call-out; legal/monetization decisions stay separate from engineering completion |
| Premature scale investment | Phase 5 | Hard evidence gate before the phase starts at all |
| Governance conflict (`.claude/CLAUDE.md`) left unresolved | All phases | Named as a Phase 0 completion item, owner-assigned, not resolved by this document |

## 10. What This Document Does Not Authorize

- No Product Decision, ADR, or business rule — including no Fair Opportunity Policy, no legal structure, no monetization model.
- No milestone is commissioned by this document alone; each phase's actual work still requires its own explicit task authorization per `.ai/EXECUTION_PROTOCOL.md`.
- No claim that any phase's rough effort sizing is a committed estimate — sizing here is relative (small/medium/variable), not a schedule.

## 11. Traceability

- [`project-brain/PIOS_MASTER_CONTEXT.md`](../project-brain/PIOS_MASTER_CONTEXT.md) — the sole product/architecture source this strategy sequences.
- [`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md) — the ratified definition of "first MVR" this strategy treats as given, not redesigned.
- [`PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md`](PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md), [`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`](PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md) — govern Phase 2's hard blocker.
- [`ADR-034`](ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md), [`ADR-035`](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) — govern the architecture Phase 0/2 build against.
- [`PERSISTENCE_ARCHITECTURE.md`](PERSISTENCE_ARCHITECTURE.md) §6–7 — the silence Phase 0's transaction-gap option (A) must be reconciled against before implementation.
- [`ENGINEERING_GUIDELINES.md`](ENGINEERING_GUIDELINES.md) — No Premature Optimization, directly grounding Phases 3 and 5's need-gating.
