# PIOS Presentation Claim Audit — Working Document

Status: Internal working document for presentation production, not a Product Decision, ADR, or canonical PIOS document. Not shown to Regina/Artur. Exists so every claim used in `PIOS_VISION_AND_NETWORK_RU` and `PIOS_AGIDEL_MVR_PILOT_RU` can be traced to its actual status — per the founder's own decision (Presentation Brief Update, point 7), STATUS tags are used here for preparation only and never appear on a slide.

STATUS values: **IMPLEMENTED** (verified in running code/tests), **RATIFIED** (approved product/architecture decision, not yet code), **HYPOTHESIS** (explicitly unratified direction), **FUTURE POSSIBILITY** (named candidate, not decided), **ILLUSTRATIVE** (example/story used to explain an idea, not itself a documented fact), **FOUNDER-PROVIDED** (given directly by the founder this session, not found in any repo document, taken as-is per instruction not to alter it).

| # | Claim used in a slide | Source | Status |
|---|---|---|---|
| 1 | Arthur's dispatcher story (owner's wife as dispatcher, best orders going to the owner and to drivers close to her) | Founder message, this session | FOUNDER-PROVIDED / ILLUSTRATIVE |
| 2 | "The problem isn't the specific people, it's the system: one person controlled how opportunity was distributed" | Generalization of #1, consistent with `PRODUCT_FOUNDATION.md` §4 Core Problem | ILLUSTRATIVE (framing), Core Problem itself is RATIFIED |
| 3 | Core Problem: absence of a fair, explainable, transparent work-allocation platform | `PRODUCT_FOUNDATION.md` §4, verbatim | RATIFIED |
| 4 | PIOS is not an aggregator / not a dispatcher / not a platform that owns client relationships | `PROJECT_CONSTITUTION.md` §19 (Identity) | RATIFIED |
| 5 | PIOS vision: extensible ecosystem beyond single-purpose taxi app | `PRODUCT_FOUNDATION.md` §2 | RATIFIED |
| 6 | Personal Client Relationship exists as a recognized, recurring concept, independent of any single order | `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1 | RATIFIED (concept only) |
| 7 | How the relationship is created, whether exclusive, whether it requires mutual confirmation | `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8 | OPEN / HYPOTHESIS — must not be stated as decided |
| 8 | "Client follows inviter" / attribution mechanism | Closest analog: "Referral Origin," `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` §1, tagged "New, this document"; that whole document is `Status: Proposed` | HYPOTHESIS — not ratified anywhere; **removed from both decks per founder decision 2026 (point 1 of this brief update)** |
| 9 | Taxi driver ↔ hairdresser example: independent people, one trust network, neither owns the other's clients | Founder's own illustration, this session; consistent with (does not contradict) Relationship Protection (`PROJECT_CONSTITUTION.md` §3/§18) | ILLUSTRATIVE |
| 10 | Person → connections → local network → city → network of cities | `PIOS_MASTER_CONTEXT.md` §7, explicitly "Entirely hypothesis/unratified, do not build" | HYPOTHESIS — must be visually marked as vision, not roadmap |
| 11 | Anti-MLM guardrail: recruitment alone must never create automatic economic entitlement | `PIOS_MASTER_CONTEXT.md` §7, derived from Constitution's anti-principles | RATIFIED constraint on the HYPOTHESIS above — must not be violated by slide wording |
| 12 | Why start with taxi: understandable pain, frequent transactions, personal clients, real allocation conflict, fast feedback loop | `IMPLEMENTATION_STRATEGY.md` Phase 1 rationale; MVP Pilot Boundary reasoning | RATIFIED-derived |
| 13 | MVP Pilot Boundary: the existing manual Assignment flow already suffices for pilot validation; do not build more before evidence | `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` | RATIFIED |
| 14 | Working mechanism: Passenger → Order (+ destination) → Coordinator → Proposal → Driver → Accept → Assignment | Sprint 3A/3B code + Sprint 6 dry run (real HTTP calls + direct SQL verification against `pios_dispatch`) | IMPLEMENTED |
| 15 | Proposal → Assignment is atomic (one commits, or neither does) | ADR-036, Accepted 2026-07-24; proven by `ProposalAssignmentOrchestrationTransactionTest` against real PostgreSQL | IMPLEMENTED |
| 16 | Driver creation via the system (`POST /v1/drivers`) | Sprint 3A, tested (unit + PostgreSQL integration) | IMPLEMENTED |
| 17 | Full MVR Pilot Package exists (acceptance criteria, operation plan, driver guide, coordinator guide, feedback template, launch checklist, first pilot scenario) | Committed `docs/MVR_*.md`, this session | IMPLEMENTED (as documentation/process, not as code) |
| 18 | "Does PIOS create additional value for a driver compared to their current way of getting orders?" | `MVR_PILOT_FEEDBACK_TEMPLATE.md` §6, verbatim | RATIFIED (as the pilot's own stated central question) |
| 19 | Definition of pilot success: at least one real driver + passenger complete the cycle; at least one driver says the equivalent of "yes, I'd use this" | `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §6/§7 | RATIFIED (as pilot plan) |
| 20 | What we are NOT building now: automatic dispatcher, aggregator, payment system, services marketplace, network of cities all at once | `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`; `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §8 (non-goals); Electronic Dispatcher remains `Status: Proposed`, no Fair Opportunity Policy selected | RATIFIED (as current non-goals) |
| 21 | Financial perspective — five levels (pilot → city model validated → driver network grows → other small-business spheres connect → platform scales) | No ratified monetization model exists anywhere (`PROJECT_CONSTITUTION.md` §26: commission vs. subscription vs. infrastructure fee "genuinely undecided") | HYPOTHESIS / FUTURE POSSIBILITY — must be shown as a possible development ladder, never a promise or a number |
| 22 | Roles for Arthur and Regina (connecting drivers, local context, observation, feedback / organization, communication, observation, feedback) | Founder message, this session — explicitly proposed for discussion, not decided | FOUNDER-PROVIDED, presented as open proposal, not assignment |
| 23 | Three first drivers, Agidel as first pilot city | Founder message, this session | FOUNDER-PROVIDED |
| 24 | Electronic Dispatcher exists as a design blueprint | `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md`, `Status: Proposed`, not a Product Decision or ADR | RATIFIED that the *document* exists; the *mechanism* itself is explicitly not decided |

## Removed from both decks per founder decision (this brief update, point 1)

- The "three fundamental laws" framing (Payment follows work / Client follows inviter / Trust follows quality) — confirmed by the founder as a deliberate simplification for this specific first meeting, not a retraction of the real Constitution §18 laws, which remain the internal foundation of the project untouched.
