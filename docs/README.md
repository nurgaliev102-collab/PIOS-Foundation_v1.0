# Documentation

This directory is the documentation hierarchy for the PIOS platform. It is the source of truth for architecture, decisions, and domain knowledge, and it is maintained ahead of implementation as described in [CLAUDE.md](../CLAUDE.md).

As of Repository Foundation v1.0, implementation has begun in [../backend/](../backend/), per [PROJECT_SCAFFOLDING_PLAN.md](PROJECT_SCAFFOLDING_PLAN.md) and [REPOSITORY_INITIALIZATION_PLAN.md](REPOSITORY_INITIALIZATION_PLAN.md). Code never duplicates this documentation; it references it.

A new AI agent picking up this project should start at [../project-brain/AI_HANDOFF.md](../project-brain/AI_HANDOFF.md) — a portable, canonical consolidation of project history, current authority, and current code reality, subordinate to this documentation hierarchy, never a replacement for it.

## Structure

- [Constitution](#constitution)
- [Architecture](#architecture)
- [ADR](#adr)
- [Domain](#domain)
- [Events](#events)
- [Use Cases](#use-cases)
- [Application Architecture](#application-architecture)
- [API](#api)
- [Data](#data)
- [Database](#database)
- [Module Structure](#module-structure)
- [Interface Contracts](#interface-contracts)
- [Security](#security)
- [Observability](#observability)
- [Deployment](#deployment)
- [Development](#development)
- [Product](#product)

## Constitution

The [Project Constitution](PROJECT_CONSTITUTION.md) is the highest-level specification for PIOS. It defines the project's vision, mission, and governance, and every other document in this hierarchy must reference it and must not contradict it.

## Architecture

The [System Architecture](SYSTEM_ARCHITECTURE.md) describes how PIOS is organized: its major architectural components, their responsibilities, and how they relate to one another. It is derived exclusively from the Constitution, the ADR Foundation, and the Product Foundation; where a required architectural decision does not yet exist, the document marks that section "Architecture Decision Required" rather than inventing one.

## ADR

Architecture Decision Records. Every change to an architectural decision is recorded here before or alongside the change, per [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md) Section 7. Each ADR remains Proposed and requires explicit approval by the Human Architect, per [ADR-007](adr/ADR-007-AI-Development-Workflow.md), before it becomes Accepted — ADR-036 is the first to have completed that review and reached Accepted status (2026-07-24).

| ADR | Title | Status |
| --- | --- | --- |
| [ADR-001](adr/ADR-001-System-Philosophy.md) | System Philosophy | Proposed |
| [ADR-002](adr/ADR-002-Dispatch-Engine.md) | Dispatch Engine | Proposed |
| [ADR-003](adr/ADR-003-Event-Driven-Domain.md) | Event-Driven Domain | Proposed |
| [ADR-004](adr/ADR-004-API-Style.md) | API Style | Proposed |
| [ADR-005](adr/ADR-005-Data-Ownership.md) | Data Ownership | Proposed |
| [ADR-006](adr/ADR-006-Documentation-Driven-Development.md) | Documentation Driven Development | Proposed |
| [ADR-007](adr/ADR-007-AI-Development-Workflow.md) | AI Development Workflow | Proposed |
| [ADR-008](adr/ADR-008-Repository-Strategy.md) | Repository Strategy | Proposed |
| [ADR-009](adr/ADR-009-Domain-Isolation.md) | Domain Isolation | Proposed |
| [ADR-010](adr/ADR-010-Versioning-Strategy.md) | Versioning Strategy | Proposed |
| [ADR-011](adr/ADR-011-Security-Principles.md) | Security Principles | Proposed |
| [ADR-012](adr/ADR-012-Observability-Principles.md) | Observability Principles | Proposed |
| [ADR-013](adr/ADR-013-Testing-Philosophy.md) | Testing Philosophy | Proposed |
| [ADR-014](adr/ADR-014-Deployment-Philosophy.md) | Deployment Philosophy | Proposed |
| [ADR-015](adr/ADR-015-Evolution-Strategy.md) | Evolution Strategy | Proposed |
| [ADR-016](adr/ADR-016-Architectural-Layers.md) | Architectural Layers | Proposed |
| [ADR-017](adr/ADR-017-Bounded-Context-Strategy.md) | Bounded Context Strategy | Proposed |
| [ADR-018](adr/ADR-018-Core-Architectural-Components.md) | Core Architectural Components | Proposed |
| [ADR-019](adr/ADR-019-Conceptual-Data-Ownership.md) | Conceptual Data Ownership | Proposed |
| [ADR-020](adr/ADR-020-Integration-Philosophy.md) | Integration Philosophy | Proposed |
| [ADR-021](adr/ADR-021-Database-Technology-Strategy.md) | Database Technology Strategy | Proposed |
| [ADR-022](adr/ADR-022-Technology-Stack-Strategy.md) | Technology Stack Strategy | Proposed |
| [ADR-023](adr/ADR-023-Backend-Technology-Decision.md) | Backend Technology Decision | Proposed |
| [ADR-024](adr/ADR-024-Frontend-Technology-Decision.md) | Frontend Technology Decision | Proposed |
| [ADR-025](adr/ADR-025-Database-Technology-Decision.md) | Database Technology Decision | Proposed |
| [ADR-026](adr/ADR-026-Deployment-Strategy.md) | Deployment Strategy | Proposed |
| [ADR-035](adr/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) | Pre-Commitment Business Fact — Aggregate Boundary | Proposed |
| [ADR-036](adr/ADR-036-Proposal-Assignment-Shared-Transaction.md) | Proposal↔Assignment Shared Transaction | **Accepted** |
| [ADR-037](ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md) | Network Management Module — Bounded Context Extension | Proposed |
| [ADR-042](ADR/ADR-042-Stated-Ride-Price-Minimal-Model.md) | Stated Ride Price — Minimal MVP Model and Ownership Boundary | Proposed (one Product Owner input outstanding) |
| [ADR-062](ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) | Primary Driver / First Refusal — Passenger Experience → Dispatch Contract | **Accepted** |
| [ADR-063](ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md) | Trip as a New Dispatch-Owned Aggregate, Distinct from Assignment | **Accepted** |
| [ADR-067](ADR/ADR-067-PIOS-Core-Bounded-Context-Slice-01.md) | PIOS Core Bounded Context — Slice 01 (Participant History Projection) | **Ratified** |
| [ADR-068](ADR/ADR-068-Relationship-Ordered-Fallback-Dispatch.md) | Relationship-Ordered Fallback Dispatch — Trusted → Open (Network tier deferred) | **Accepted, narrowed scope** |
| [ADR-069](ADR/ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) | Test/Real Segregation in Fallback Driver Selection — `is_test` on Dispatch's Availability Projection | **Accepted** |
| [ADR-070](ADR/ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) | Channel 1 — Discovery Matching and Post-Ride Relationship Formation (Option B) | **Accepted** |
| [ADR-071](ADR/ADR-071-In-App-Poll-Derived-Notification-Surface.md) | In-App, Poll-Derived Notification Surface | **Accepted** |
| [ADR-073](ADR/ADR-073-Driver-To-Driver-Referral-Single-Hop-Origin-Fact.md) | Driver-to-Driver Referral — Single-Hop Origin Fact | **Accepted** |
| [ADR-074](ADR/ADR-074-Subscription-Billing-Bounded-Context-Foundation.md) | Subscription/Billing Foundation — a New Bounded Context That Gates Nothing | **Accepted, container only** |
| [ADR-075](ADR/ADR-075-Guest-First-Passenger-Identity-And-In-Place-Upgrade.md) | Guest-First Passenger Identity and In-Place Upgrade | **Accepted — documents already-built, uncommitted, undeployed code** |
| [ADR-076](ADR/ADR-076-Server-Authorized-Named-Driver-Offer.md) | Server-Authorized Named-Driver Offer — `requestedDriverId`, and No Client-Created Proposals | **Accepted — documents already-built, uncommitted, undeployed code; supersedes ADR-066's passenger branch** |
| [ADR-077](ADR/ADR-077-Dispatch-Routing-Obligation-Bounded-Retry-And-Unfulfilled-Order.md) | Dispatch Routing Obligation, Bounded Retry, and `UNFULFILLED` as an Order Terminal State | **Accepted — documents already-built, uncommitted, undeployed code; amends ADR-068 Part 4** |

Note (Sprint 1 — Foundation Stabilization): this table is also missing ADR-027 through ADR-034, which exist as real files under `docs/ADR/` but were never added here — a pre-existing indexing gap found during this sprint's own documentation-sync work, left uncorrected here since it is outside this sprint's authorized scope; recorded for Sprint 2.

Note (Sprint 4): the gap recorded above was never closed, and has since widened — ADR-038, ADR-039, ADR-040 and ADR-041 also exist as real files under `docs/ADR/` without a row here. Sprint 4 added the ADR-042 row above rather than rebuilding the whole index, which would exceed this sprint's own authorized scope. The files themselves, not this table, remain authoritative.

Note (Task 10B — Architecture Ratification & Document Reconciliation, 2026-09-03): the gap above was never closed either, and now also includes ADR-043 through ADR-061 — all real files under `docs/ADR/`, none indexed here. This task added only the two rows it directly ratified (ADR-062, ADR-063), following the same narrow-scope precedent the two notes above already set, rather than rebuilding the whole index. The files themselves, not this table, remain authoritative.

Note (ADR-067 ratification, 2026-09-10): the gap above still stands (ADR-043–061, ADR-064–066 remain unindexed). This task added only the single ADR-067 row it directly ratified, following the same narrow-scope precedent, and did not rebuild the index. The ADR files themselves, not this table, remain authoritative.

Note (ADR-068 ratification, 2026-09-15): the gap above still stands (ADR-043–061, ADR-064–066 remain unindexed). This task added only the single ADR-068 row it directly ratified, following the same narrow-scope precedent, and did not rebuild the index.

Note (ADR-069 implementation, 2026-09-15): the gap above still stands (ADR-043–061, ADR-064–066 remain unindexed). This task added the ADR-069 row alongside the ADR-068 row added moments earlier in the same day's work, following the same narrow-scope precedent, and did not rebuild the index. The ADR files themselves, not this table, remain authoritative.

## Domain

The [Domain Model](DOMAIN_MODEL.md) is the technology-independent business domain of PIOS: domains, aggregates, entities, value objects, domain services, events, commands, queries, invariants, lifecycles, and relationships. It is derived from the Constitution, the Product Foundation, the System Architecture, and the ADR Foundation, and is the single source of truth for any future API, database, event, or implementation design.

## Events

The [Event Catalog](EVENT_CATALOG.md) is a business-language catalog of the domain events already established in the Domain Model: what meaningful facts occur, why they matter, which domain owns them, and what they represent. It introduces no event the Domain Model does not already establish, and contains no message broker design, payload, or schema.

## Use Cases

The [Use Case Catalog](USE_CASE_CATALOG.md) describes who interacts with PIOS, what goals they achieve, and what a successful outcome means for each of them, mapped to the domains, aggregates, and events already established. It is a business behavior document, not a UI, API, or database specification.

## Application Architecture

The [Application Architecture](APPLICATION_ARCHITECTURE.md) describes how application responsibilities are organized between actor intentions and domain capabilities: how the goals in the Use Case Catalog are executed conceptually, and how application capabilities coordinate the domain behavior already established. It is the bridge between business intentions and system implementation, not an API, database, frontend, backend, or deployment design.

## API

The [API Specification](API_SPECIFICATION.md) defines what interactions cross PIOS's system boundaries, what capabilities are exposed, and what contracts exist conceptually, at a technology-independent level. It is not a REST, GraphQL, gRPC, or OpenAPI specification; transport and implementation are left to future documents.

## Data

The [Conceptual Data Model](CONCEPTUAL_DATA_MODEL.md) defines what information exists in PIOS, who owns it, and how information concepts relate, from the business domain perspective. It is not a database schema, table design, or storage architecture; it is the business-grounded source a future Logical Data Model or Database Design derives from.

The [Logical Data Model](LOGICAL_DATA_MODEL.md) carries the Conceptual Data Model one level closer to implementation: logical entities, relationships, attribute groups, and constraints, still independent of storage technology. It is not a database schema, SQL model, or physical storage design; it is the bridge to any future Database Design.

The [Persistence Architecture](PERSISTENCE_ARCHITECTURE.md) defines the architectural principles governing persistence across PIOS: responsibility, boundaries, ownership, consistency, and evolution. It is the architectural bridge between the Logical Data Model and any future Physical Database Design; it selects no technology and defines no schema.

## Database

The [Database Design](DATABASE_DESIGN.md) translates the Logical Data Model and Persistence Architecture into technology-neutral physical storage structures: physical shape, relationships, constraints, and indexing principles for each domain. It selects no specific database product or vendor; per-domain technology selection remains a distinct, later decision under ADR-021.

## Module Structure

The [Module Structure](MODULE_STRUCTURE.md) defines software module responsibilities, dependency boundaries, ownership, and architectural isolation, before any implementation. It maps one module to each of the eight ratified capabilities; it is not code, a repository or folder structure, a framework choice, or a microservice deployment design.

## Interface Contracts

The [Interface Contracts](INTERFACE_CONTRACTS.md) define architectural interaction contracts between modules: interaction boundaries, ownership of operations, allowed communication directions, and exchanged conceptual information. It is not an API specification, a programming interface, or a message schema; it consolidates what the Event Catalog, Application Architecture, and Module Structure already establish about which module depends on which.

## Security

Security model, authentication, authorization, and related concerns.

Content pending.

## Observability

Logging, metrics, tracing, and monitoring.

Content pending.

## Deployment

Deployment topology, environments, and release process.

Content pending.

## Development

The [Engineering Guidelines](ENGINEERING_GUIDELINES.md) define development principles, quality standards, change discipline, review practices, testing philosophy, and operational expectations, including explicit rules for AI-assisted development. It selects no programming language, framework, tool, or repository structure.

The [Project Scaffolding Plan](PROJECT_SCAFFOLDING_PLAN.md) defines how the repository and initial project structure will be organized before implementation begins: repository organization, module and frontend placement, documentation placement, development sequence, and the first implementation milestone. It creates no repository file, code, or infrastructure.

The [Initial Implementation Plan](INITIAL_IMPLEMENTATION_PLAN.md) converts the approved architecture into the first controlled implementation roadmap: MVP definition, implementation sequence, module priority, the first vertical slice, MVP exclusions, and validation criteria. It creates no code, user story, ticket, or implementation detail.

The [Repository Initialization Plan](REPOSITORY_INITIALIZATION_PLAN.md) defines the controlled transition from architecture documentation to the first real software repository: initial repository and module scope, deferred modules, first commit definition, and the transition to implementation. It creates no repository, folder, file, or source code.

The [Launch Checklist](LAUNCH_CHECKLIST.md) (Sprint VALIDATION-001) is the operational, tick-box runbook for actually starting every module locally and proving the current end-to-end dispatch flow works — required software, database/broker setup, startup order, and the scenario steps themselves. Purely operational; it creates no architecture, domain, or product decision.

The [Implementation Strategy](IMPLEMENTATION_STRATEGY.md) sequences already-ratified decisions and already-existing code into a phased roadmap from the repository's current state through a first MVR and onward toward a mature platform, comparing sequencing options where more than one exists. It authorizes no Product Decision, ADR, or business rule, and commissions no milestone by itself.

## Product

Product scope and requirements.

The [Product Foundation](PRODUCT_FOUNDATION.md) defines the product domain: what PIOS is, who participates in it, why it exists, and where its architectural boundaries originate. It derives from the Constitution and the ADR Foundation and precedes any future PRD; the Domain Model and Use Case Catalog now build on it.

The [Product Baseline v2.0](PRODUCT_BASELINE_V2.md) reconciles a consolidated product/domain model against current repository authority, classifying each claim as ratified, derived, hypothesis, or open, and records the resulting repository impact analysis and required decision sequence. It is subordinate to the Constitution and creates no Product Decision itself.

The [Product Decision: Fulfillment Authority and Scope v1.0](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md) defines the authority boundary governing when and how a transport request may move from preferred/direct fulfillment toward alternative fulfillment, resolving who may authorize scope expansion while explicitly deferring Opportunity and Eligibility design to future decisions.

The [Product Decision: Opportunity, Acceptance & Commitment Semantics v1.0](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md) defines the semantic boundary between a candidate opportunity, a driver's acceptance, and actual fulfillment commitment, finding that the current Assignment model already represents Commitment once accepted but has no architectural presence for a true multi-candidate Opportunity phase.

The [Product Decision: Eligibility and Minimum Trust Boundary v1.0](PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md) defines the minimum eligibility boundary for future shared-opportunity participation, finding that no Eligibility concept exists anywhere today and that Availability currently functions only as a de facto, undecided proxy for it.

The [Product Decision: Fair Opportunity Policy v1.0](PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md) defines the product principles governing fair access to shared network opportunities, confirming fairness as procedural (not distributional) and classifying candidate fairness dimensions and priority signals without selecting, ranking, or implementing any of them.

The [ADR Migration Strategy and Architecture Impact Analysis](ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md) assesses current documentation and code against the five completed Product Decisions, classifying every relevant artifact as KEEP/ADAPT/DEFER/MISSING and producing a dependency-ordered migration sequence, without overriding the Constitution, any ADR, or any Product Decision.

The [Product Decision: MVP Pilot Boundary v1.0](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md) defines the smallest realistic pilot validating PIOS's product core, finding that the existing Assignment flow already supports a manual, no-new-code fallback loop sufficient to test both existential hypotheses (driver willingness to share, willingness to pay).

The [Product Experiment: Network Pilot v1.0](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md) operationalizes the MVP Pilot Boundary into one concrete, entirely manual Driver A / Coordinator / Driver B scenario, defining data collection and evidence patterns for H1/H2 without any software, ranking, or automated matching.

The [Product Experiment: Network Pilot Execution Plan v1.0](PRODUCT_EXPERIMENT_NETWORK_PILOT_EXECUTION_PLAN.md) defines the concrete operational execution of the Network Pilot — participant selection, role responsibilities, the exact manual workflow, a manual case log template, interview questions, and qualitative (non-thresholded) decision criteria for interpreting results.

The [Product Experiment: Network Pilot Operations Checklist v1.0](PRODUCT_EXPERIMENT_NETWORK_PILOT_OPERATIONS_CHECKLIST.md) turns the Execution Plan into a runnable, itemized checklist for preparation, role guidance, case execution, data collection, interviews, weekly review, safety boundaries, and end-of-pilot review, without adding any new mechanism.

The [Product Experiment: Network Pilot Launch Pack v1.0](PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md) is the practical package for starting the first pilot cases — recruitment guidance, first-contact scripts, participation confirmation, a first-case runbook, case/weekly review templates, safety rules, and qualitative exit conditions.

The [PIOS Consolidation & Implementation Readiness Review v1.0](PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md) is a control-checkpoint audit reconstructing PIOS's complete state from the entire authority chain and actual repository, identifying the real product core, implementation-ready work, and a recommended first implementation tranche, independent of the manual pilot.

The [Implementation Plan: Tranche 1 — Dispatch Event Publishing Completion v1.0](IMPLEMENTATION_PLAN_TRANCHE_1_DISPATCH_EVENT_PUBLISHING.md) designs, at file and class granularity, closing Dispatch's missing OrderAssigned/AssignmentAccepted publishing and Order Management's missing AssignmentAccepted consumption, by mirroring the already-proven outbox/RabbitMQ pattern exactly. Planning only; not an authorization to implement.

The [Implementation Plan: Tranche 2 — Passenger Experience REST Transport v1.0](IMPLEMENTATION_PLAN_TRANCHE_2_PASSENGER_REST_TRANSPORT.md) designs the real ADR-028 REST transport for the Passenger Experience → Order Management contract still running on ADR-027's test-only mechanism, finding that Order Management (not Passenger Experience) owns the Submit Order endpoint per API_SPECIFICATION.md. Planning only; not an authorization to implement.

The [PIOS Hardening Review v1.0](PIOS_HARDENING_REVIEW_V1.md) verifies, against actual committed code, that Tranche 1 and Tranche 2 are individually working and domain-boundary-safe, while identifying that no production trigger exists anywhere for the outbox relay pattern (events are durably recorded but never automatically published in a live deployment) as the single highest-leverage next hardening item.

The [Implementation Plan: Production Outbox Relay Trigger v1.0](IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md) re-verifies the Hardening Review's own finding against current code, then designs the smallest fix — a module-local `@Scheduled` trigger per producer module — while explicitly flagging that safe multi-instance relay execution (row-claiming) is a distinct, out-of-scope future item. Planning only; not an authorization to implement.

The [PIOS Vertical Slice Verification v1.0](PIOS_VERTICAL_SLICE_VERIFICATION_V1.md) confirms every segment of the Submit-Order-through-Assignment-Acceptance chain is independently proven against real PostgreSQL/RabbitMQ/HTTP, finds no single test proves the composed whole, and concludes the technical foundation is sufficient to begin the (entirely manual) Network Pilot regardless of that gap.

The [Network Pilot Launch Kit v1.0](NETWORK_PILOT_LAUNCH_KIT_V1.md) consolidates the Network Pilot experiment, execution plan, and operations checklist into one practical, run-it-from-this-document kit — objective, participant profile, roles, rules, recruitment script, interview framework, case log, qualitative success signals, stop conditions, post-pilot decision categories, and a first-30-days operational sequence — introducing no new pilot mechanic.

The [Product Decision: Opportunity Before Assignment v1.0](PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md) ratifies that a self-contained business fact exists between Dispatch's own driver selection and a driver's own confirmation, distinct from Assignment, while deliberately deferring its architectural form — resolved separately by [ADR-035](adr/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md).

The [Electronic Dispatcher — Product & Domain Design v1.0](ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md) is a **Proposed design blueprint, not yet a Product Decision or ADR** — it designs the automatic replacement for today's manual Coordinator-driven candidate selection (Dispatch Lifecycle, Eligibility, Candidate Selection, Fair Opportunity Policy factors, Proposal Queue continuation, Timeout Strategy, Failure Handling, Personal Client Relationship decision points, Referral Origin placement, and future policy extension points), consolidating every open question it touches rather than resolving any of them, pending Product Owner review before any implementation or formal ratification.

The [PIOS UX Backlog](PIOS_UX_BACKLOG.md) is a living, non-binding list of small human-observed UX improvements (emotional wording, onboarding polish, driver/passenger conveniences, trust signals) — not a spec or Product Decision; items are promoted into a real sprint only after being checked against real user behavior (a Pilot Review), not on assumption alone.

The [PIOS Pilot Review Protocol v1.0](PIOS_PILOT_REVIEW_PROTOCOL.md) is an operational script for the product team, not developers — how to observe a new driver or passenger using Sprint 8 unaided, the exact one-line prompts to give them, five closing questions, success criteria (70% completing the scenario without help), the rule against selling the idea during a pilot, and a staged rollout (1+1 → fix → 2+3 → fix → full cohort) feeding directly into `PIOS_UX_BACKLOG.md`.

The [PIOS Product Hypotheses](PIOS_PRODUCT_HYPOTHESES.md) log requires every new feature to start as a falsifiable, pre-registered hypothesis about real user behavior rather than a desired feature — it fixes the measurement discipline (observed behavior over stated opinion, "if PIOS disappeared tomorrow" as the sharpest test) and the team's advance commitment not to rationalize away a disconfirmed hypothesis, and seeds the log with the first two hypotheses Sprint 8's own screens already embed, both awaiting `PIOS_PILOT_REVIEW_PROTOCOL.md`'s results.

The [PIOS Product Evidence Log](PIOS_PRODUCT_EVIDENCE.md) is the empirical base the other three product documents draw from — only observed facts (no interpretation beyond a single tentative line), entries never renumbered, and no hypothesis verdict decided from a single conversation; currently empty, seeded only with an illustrated (not real) entry format, pending the first real `PIOS_PILOT_REVIEW_PROTOCOL.md` session.

The [Product Decision: Electronic Dispatcher MVP Blockers v1.0](PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md) ratifies the three decisions Sprint PRODUCT-DECISION-RESOLUTION-001 identified as blocking Automatic Dispatch implementation — Cycle-Exhausted Behavior (limited automatic retry, then honest customer outcome; Coordinator as exceptional tool only, never the normal fallback), Proposal Timeout Policy (a single, non-adaptive, configurable parameter), and Concurrent Proposal Policy (strictly sequential, one `OPEN` Proposal per order, no broadcast) — while explicitly leaving every other open question from the design blueprint exactly as open as it found them.

The [Product Decision: Personal Network MVP Transition v1.0](PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md) explicitly and narrowly supersedes `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 9 and the "zero new software" premise of the Network Pilot Launch Kit, authorizing a minimal software Personal Network MVP (Sprint 7) for the specific driver-invites-passenger scenario, while leaving every other exclusion (ranking, scoring, AI selection, reputation, payments custody) exactly as excluded as before.

The [Implementation Plan: Sprint 7 — Personal Network MVP v1.0](IMPLEMENTATION_PLAN_SPRINT_7_PERSONAL_NETWORK_MVP.md) designs the smallest software change realizing that scenario: a `displayName` field added to `Driver`, a new `Connection` aggregate owned by Passenger Experience (its first-ever database), and three frontend wiring changes — reusing Dispatch's and Order Management's existing APIs unchanged, and explicitly not touching `Order`, `Proposal`, `Assignment`, or ADR-036. Planning only; not an authorization to implement.
