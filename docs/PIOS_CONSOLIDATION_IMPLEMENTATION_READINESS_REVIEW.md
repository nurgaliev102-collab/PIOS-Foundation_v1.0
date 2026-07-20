# PIOS Consolidation & Implementation Readiness Review v1.0

Status: Control Checkpoint — Repository-Wide Audit and Implementation-Readiness Analysis. This document is **not** a Product Decision, **not** an ADR, and **not** an implementation plan. It reconstructs PIOS's complete, current state from the entire authority chain and the actual repository — not only the most recent Product Decision sequence — and recommends, without authorizing, a first implementation tranche. It does not override PROJECT_CONSTITUTION.md, any ADR, or any Product Decision. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this document.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, or directly observable in committed code, cited inline.
- **[DERIVED]** — a reasoned conclusion from already-ratified material to a question those sources leave to an analysis such as this one.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; not adopted.
- **[OPEN]** — not decided by any approved document.
- **HISTORICAL / NOT RATIFIED** — discussed in this repository's history or in earlier drafts but never actually approved; explicitly not treated as authority.

---

## Part 1 — Reconstructing the Complete Product

**Inspection basis.** Full ADR set (`docs/ADR/ADR-001` through `ADR-034`), PROJECT_CONSTITUTION.md, PRODUCT_FOUNDATION.md, SYSTEM_ARCHITECTURE.md, DOMAIN_MODEL.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, APPLICATION_ARCHITECTURE.md, INTERFACE_CONTRACTS.md, MODULE_STRUCTURE.md, PERSISTENCE_ARCHITECTURE.md, all nine Product Decisions and Product Experiments, PRODUCT_BASELINE_V2.md, ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md, `.ai/EXECUTION_PROTOCOL.md`, and the full commit history (`git log`, 78 commits, `8b314dd` through `2a18a8b`) confirming every ADR and document below was actually committed, in the order the authority hierarchy expects (Constitution → ADR Foundation → Product Foundation → Architecture → Domain → Events → Use Cases → Application Architecture → API/Data/Database → Module Structure → Interface Contracts → Engineering Guidelines → Scaffolding → Implementation → RabbitMQ foundation → Product Decisions → Baseline v2.0 → the recent chain). No concept below is drawn from historical discussion outside this committed record.

### WHAT PIOS IS

**[RATIFIED]** — a trustworthy, transparent mechanism connecting independent drivers with transportation demand from multiple, heterogeneous sources (Platform Order, Corporate Order, and personal-client-directed demand), preserving durable driver-client relationships, without concentrating unaccountable control in a single intermediary (PRODUCT_FOUNDATION.md Section 1; PROJECT_CONSTITUTION.md Section 19). **This is broader than Dispatch, and broader than the recent fallback-pilot scenario** — it is a general-purpose independent-driver demand-aggregation platform, of which relationship-preserving fallback is one, currently-being-validated, extension.

| Capability / Concept | Classification | Basis |
| --- | --- | --- |
| Independent driver business ownership | **RATIFIED** | Constitution Sections 3, 18 (Driver Ownership / Driver Independence); PRODUCT_FOUNDATION.md Section 12 — driver is never an employee |
| Personal Client Relationships (concept) | **RATIFIED** as concept; **OPEN** as mechanism | DOMAIN_MODEL.md Section 5; Product Decision Personal Client Relationship Part 8 — creation, confirmation, exclusivity all unresolved |
| Passenger Experience (representation) | **RATIFIED**, scaffolded, minimally implemented | ADR-018; `backend/passenger-experience` |
| Driver Management (standing/availability/participation) | **RATIFIED** scope; only availability implemented | ADR-018; DOMAIN_MODEL.md Section 4; `Driver.kt`'s own KDoc |
| Order Management (demand lifecycle, any source) | **RATIFIED**, scaffolded, implemented core lifecycle | ADR-018; `backend/order-management` |
| Dispatch (exclusive assignment decision) | **RATIFIED**, scaffolded, partially implemented | ADR-002, ADR-018; `backend/dispatch` |
| Payments (platform's own payment record) | **RATIFIED** as capability; **not scaffolded** | ADR-018; MODULE_STRUCTURE.md Section 3 |
| Administration | **RATIFIED** as capability; **not scaffolded** | ADR-018 |
| Analytics | **RATIFIED** as capability; **not scaffolded** | ADR-018 |
| Notifications | **RATIFIED** as capability; **deliberately deferred, not merely unbuilt** | ADR-033; Product Decision Notifications v1.0 — "no currently ratified, concrete business need" |
| Shared/network demand when personal fulfillment fails | **DERIVED** | Order Origin already ratified (DOMAIN_MODEL.md Section 6); the DIRECT/NETWORK naming is Product Decision Fulfillment Authority and Scope's own new vocabulary over already-ratified fragments |
| Fairness in distribution | **RATIFIED** as principle; concrete metric **OPEN** | Constitution Section 3 (Fair Dispatch); Product Decision Dispatch Philosophy Section 5 |
| Transparency of dispatch decisions | **RATIFIED** | Constitution Sections 3, 18 |
| Driver autonomy | **RATIFIED** | PRODUCT_FOUNDATION.md Section 12 |
| Platform never silently owning client relationships | **RATIFIED** | Constitution Sections 3, 18 (Relationship Protection) |
| Reciprocity / Mutuality / contribution balancing | **HISTORICAL / NOT RATIFIED** | Constitution Section 18 explicitly excludes it from the Fundamental Laws; ADR-034 Part 4 names it only as one of several *unapproved* candidates, "none... approved, previewed, or ranked" |
| Subscription commercial model | **HYPOTHESIS** | PRODUCT_BASELINE_V2.md Section 13 — leading hypothesis only, not ratified |
| Commission-based model | **OPEN, not ratified against either** | Constitution Section 19; DOMAIN_MODEL.md Section 14 |
| Direct payment to executor | **RATIFIED for pilot purposes only** — not a ratified permanent production architecture | PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 2; Payments module remains a wholly separate, unbuilt, undecided capability |
| Platform boundaries (what PIOS is not responsible for) | **RATIFIED** | PRODUCT_FOUNDATION.md Section 8 |

## Part 2 — As-Is Architecture Map

Verified directly against code (`backend/order-management`, `backend/driver-management`, `backend/dispatch`, `backend/passenger-experience`), migrations, and test directories. **No frontend, shared library, or infrastructure-as-code directory exists anywhere in this repository** — `backend/` is the entire implementation surface. **No REST controller, `@RestController`, or any HTTP endpoint exists anywhere in this codebase** (verified by direct grep) — every module today is invoked only through direct Kotlin method calls in tests; there is no way for an external client to interact with any of this in production yet.

| Module | Main files | Test files | Migrations | Functional end-to-end | Contract-only/stubbed | Missing |
| --- | --- | --- | --- | --- | --- | --- |
| Order Management | 29 | 18 | V1 (orders), V2 (outbox) | OrderSubmitted/OrderCompleted/OrderCancelled outbox recording exists for all three; RabbitMQ publication is dedicated-tested for OrderSubmitted specifically | AssignmentAccepted consumption (`OrderAssignmentRecognitionHandler`, test-scoped contract verification only, no RabbitMQ listener); its own inbound Submit Order contract from Passenger Experience (ADR-027 stub only, never upgraded to ADR-028's real transport) | Order Origin field; real AssignmentAccepted consumer; real inbound transport for Submit Order |
| Driver Management | 24 | 18 | V1 (drivers), V2 (outbox) | DriverAvailabilityChanged outbox + RabbitMQ publication, fully tested | — | Standing, participation (deliberately, per `Driver.kt`'s own KDoc) |
| Dispatch | 29 | 23 | V1 (assignments), V2 (driver_availability + idempotency ledger) | DriverAvailabilityChanged consumption — idempotent, retried, dead-lettered, most thoroughly tested flow in the repository | OrderAssigned/AssignmentAccepted publication (`OrderAssignedPublisher`, primitive-payload translator, test-scoped contract verification only — **no outbox, no RabbitMQ producer topology exists for Dispatch's own events at all**) | Its own outbox/EventPublisher for OrderAssigned/AssignmentAccepted; Assignment Policy port (deliberately, ADR-034); "list all available drivers" query (ADR-034 Part 6, named, not closed) |
| Passenger Experience | 7 | 4 | **none** | — (no persistence layer exists at all) | Its entire outbound contract to Order Management (ADR-027 stub, test-scoped only) | Personal Client Relationship representation (deliberately, per PCR decision); any persistence; real transport for its own contract |

**Central, previously under-emphasized finding:** two distinct infrastructure gaps exist, not one, and they are architecturally different in kind:

1. **Dispatch → Order Management (OrderAssigned, AssignmentAccepted).** These are **events** (ADR-003) — the correct production transport is the message broker (ADR-028), and the pattern is already proven twice (Order Management's and Driver Management's own publishers). This is a narrow, low-risk completion of already-built infrastructure.
2. **Passenger Experience → Order Management (Submit Order).** This is a **command**, a direct request-driven interaction (ADR-004) — the correct production transport is REST (ADR-028: "REST for direct request-driven interaction"), not a message broker, and **no REST endpoint of any kind exists anywhere in this repository** to build it on. This is a broader gap than gap 1, touching a transport category nothing in this codebase has used yet.

## Part 3 — Traceability Matrix

| Capability / Invariant | Authority | Architecture Decision | Current Code | Current Tests | Status |
| --- | --- | --- | --- | --- | --- |
| Order submission, completion, cancellation | PRODUCT_FOUNDATION.md §10; DOMAIN_MODEL.md §4, 12 | ADR-002, ADR-005, ADR-009 | `Order.kt`, `OrderLifecycleApplicationService.kt` | `OrderTest`, `OrderLifecycleApplicationServiceTest`, `PostgreSQLOrderLifecycleTest` | **IMPLEMENTED** |
| OrderSubmitted publication (outbox + RabbitMQ) | ADR-028–032 | Same | `OutboxRelay`, `RabbitMQEventPublisher` | `RabbitMQEventPublisherTest`, `OrderSubmittedPublicationTest` | **IMPLEMENTED** |
| Assignment creation/acceptance | DOMAIN_MODEL.md §4, 12 | ADR-002 | `Assignment.kt`, `DispatchAssignmentApplicationService.kt` | `AssignmentTest`, `DispatchAssignmentApplicationServiceTest` | **IMPLEMENTED** (scope: driver externally supplied, by design) |
| Assignment Policy (which driver) | ADR-034 | ADR-034 (seam named, not built) | none | none | **DOCUMENTED ONLY** |
| DriverAvailabilityChanged publish → consume | EVENT_CATALOG.md §7, 9 | ADR-028–032, ADR-034 (consumer) | Full publisher + consumer + idempotency + retry/DLQ | 9 dedicated Dispatch test files | **IMPLEMENTED** |
| OrderAssigned/AssignmentAccepted publish | EVENT_CATALOG.md §6 | ADR-028–032 (pattern only, not applied here) | `OrderAssignedPublisher` (stub) | Contract-verification test only | **PARTIALLY IMPLEMENTED** (domain event exists; infrastructure does not) |
| AssignmentAccepted consumption | INTERFACE_CONTRACTS.md §5 | ADR-028–032 (pattern only, not applied here) | `OrderAssignmentRecognitionHandler` (stub) | Contract-verification test only | **PARTIALLY IMPLEMENTED** |
| Submit Order transport (Passenger Experience → Order Management) | ADR-004, ADR-028 | REST selected, not implemented | none | Contract-verification test only | **SCAFFOLDED** (ADR-027 stub stage only) |
| Personal Client Relationship | DOMAIN_MODEL.md §5 | none (entity only, no aggregate ratified) | none | none | **DOCUMENTED ONLY** |
| Fulfillment Authority / Scope | PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md | none | none | none | **DOCUMENTED ONLY** |
| Opportunity | PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md | none | none (Assignment's `CREATED` state substitutes only for a single, pre-chosen driver) | none | **OPEN** (concept named, no implementation, no architecture) |
| Eligibility | PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md | none | none (Availability substitutes de facto only) | none | **OPEN** |
| Fair Opportunity Policy | PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md | none | none | none | **OPEN** (7 candidates classified, none selected) |
| Driver standing/participation | DOMAIN_MODEL.md §4 | none | none | none | **DOCUMENTED ONLY** |
| Fleet as a domain | DOMAIN_MODEL.md §5 | none — "Domain Decision Required" since before the recent chain even began | none | none | **OPEN** (long-standing, unrelated to recent work) |
| Reciprocity / reputation / rating | — | Explicitly excluded (Constitution §18; ADR-034 Part 2) | none | none | **DEFERRED** |
| Commercial model | PRODUCT_BASELINE_V2.md §13 | none | none | none | **OPEN** |
| Manual Network Pilot | Four Product Experiment documents | Not an architecture concern by design | none (entirely human process) | Not applicable | **DOCUMENTED ONLY**, ready to run |

**No CONFLICT / NEEDS DECISION status applies anywhere** — every gap above is either a completion of already-ratified infrastructure, a deliberately deferred concept, or an already-acknowledged open product question. This audit does not invent a conflict where none exists.

## Part 4 — Auditing the Recent Product Decision Chain

| Decision | What was decided | Remains OPEN | Requires code now? | Requires ADR first? | Only relevant post-pilot? | Existing partial coverage |
| --- | --- | --- | --- | --- | --- | --- |
| Fulfillment Authority / Scope | Who authorizes scope expansion (requester only); Dispatch may never self-initiate it; rights authorization does NOT grant | Ownership (Passenger Experience vs. Order Management); MVP mechanism (manual vs. pre-authorized) | **No** | Yes, eventually, once ownership is resolved | Ownership question can be resolved pre-pilot; MVP mechanism is being tested by the pilot now | None — no code exists |
| Opportunity / Acceptance / Commitment | Acceptance = Commitment (already true in code); Assignment's `CREATED` state is a single-driver proposal, not a candidate pool | Whether/how a true multi-candidate Opportunity is ever built | **No** | Yes, if ever built | Yes — a true Opportunity concept is only relevant once NETWORK-scope automation is pursued, itself gated on pilot evidence | `Assignment`'s CREATED/ACCEPTED split already covers the Commitment half completely |
| Eligibility | Availability ≠ Eligibility (structural distinction); ownership (Driver Management) | Concrete criteria; whether PCR affects it | **No** | Yes, if formalized | Yes for any formal criteria; manual admission already suffices for the pilot | `Availability` already exists and is reused as-is for the pilot |
| Fair Opportunity Policy | Fairness is procedural, not distributional (reused, not newly decided); 7 candidate mechanisms classified, none selected | Which mechanism, if any | **No** | Yes, mirroring ADR-034's own Assignment Policy port pattern | Yes — entirely post-pilot | None — `Assignment.create()` has zero selection logic today, by design (ADR-002) |
| MVP Pilot Boundary | The entire pilot can run on **already-existing code** (`Assignment.create()`'s own flexibility) plus 100% manual process | Cohort size, coordinator identity, exact mechanism choices | **No** | No | This *is* the pilot | Fully covered by existing `AssignOrderCommand`/`Assignment` flexibility |

**Important, explicitly answered per this task's own instruction:** none of Fulfillment Authority, Opportunity, Eligibility, or Fair Opportunity Policy is concluded here to require becoming a domain entity, aggregate, table, API, or service. Every one of them is currently, correctly, substituted for by either an already-existing ratified mechanism (Availability, Assignment's CREATED/ACCEPTED split) or a manual human process (the pilot itself) — and this audit does not conclude otherwise.

## Part 5 — Revisiting the Original Taxi Product Thesis

| Historically important idea | Classification | Basis |
| --- | --- | --- |
| Drivers as independent business owners | **RATIFIED** | Constitution Sections 3, 18; PRODUCT_FOUNDATION.md Section 12 |
| Drivers having personal clients | **RATIFIED** (concept) | DOMAIN_MODEL.md Section 5 |
| Passenger relationship with a preferred/personal driver | **RATIFIED** (concept) | Same |
| Ability to preserve customer relationships | **RATIFIED** | Constitution Sections 3, 18 |
| Shared/network orders when personal fulfillment impossible or network demand exists | **RATIFIED (Platform Order via Dispatch, general case) + DERIVED (the specific DIRECT/NETWORK naming for the fallback case)** | ADR-002; PRODUCT_FOUNDATION.md Section 10; Product Decision Fulfillment Authority and Scope |
| Fairness in distribution | **RATIFIED** (principle); **OPEN** (metric) | Constitution Section 3 |
| Transparency of dispatch decisions | **RATIFIED** | Constitution Sections 3, 18 |
| Driver autonomy | **RATIFIED** | PRODUCT_FOUNDATION.md Section 12 |
| Platform not silently taking ownership of customer relationships | **RATIFIED** | Constitution Sections 3, 18 |
| Reciprocity / Mutuality / contribution balancing | **HISTORICAL / NOT RATIFIED** | Constitution Section 18; ADR-034 Part 4 — explicitly and deliberately excluded, not merely unmentioned |
| Subscription vs. commission business model | **HYPOTHESIS** (subscription) / **OPEN** (commission, not decided against) | PRODUCT_BASELINE_V2.md Section 13; Constitution Section 19 |
| Direct payment to executor | **RATIFIED for the pilot only** | PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md Section 2 |

**Self-check against distortion.** The general Platform-Order-to-any-available-driver path — the platform's oldest, most basic ratified mechanism (ADR-002, present since before any of the recent chain) — is **unaffected** by the recent chain. `Assignment.create()` has always accepted any externally-supplied driver; nothing about Fulfillment Authority, Opportunity, Eligibility, or Fair Opportunity Policy changes that general case. **The recent chain adds vocabulary for one specific extension (relationship-preserving fallback into a network) — it does not replace, narrow, or redefine the broader, older thesis that PIOS aggregates demand from multiple heterogeneous sources generally.** This distinction is real and worth stating plainly, because the four Product Experiment documents' own intense focus on one Driver-A/Driver-B/Coordinator scenario could, if read in isolation, give the false impression that this scenario *is* the product. It is one validated extension of it (Part 14 returns to this).

## Part 6 — The Real Product Core

**"If PIOS is stripped down to its smallest coherent product — not the smallest manual experiment — what remains?"**

### A. Product Core (without which PIOS is not the intended product)

- Order Management — receiving and tracking demand from any source.
- Dispatch — the exclusive, accountable assignment decision, and the *values* it must honor (fairness as procedure, transparency, no hidden preference) even before any specific policy exists.
- Driver Management — driver representation, self-determined availability, independence.
- Passenger Experience — representation of demand originators.
- The event-driven integration substrate connecting the three above (outbox, RabbitMQ, idempotent consumption) — this is not a "feature," it is the mechanism by which the four core modules remain both independently deployable and mutually consistent, per ADR-001/003/005/009.
- Personal Client Relationship **as a protected concept** (even though its mechanics are unresolved) — Relationship Protection is a Fundamental Law (Constitution Section 18), not an optional feature.

### B. Supporting Capabilities (important, not existential)

- Payments, Administration, Analytics, Notifications (all ratified capabilities, none built, none currently needed).
- The *specific* mechanism eventually chosen for Fulfillment Authority, Opportunity, Eligibility, and Fair Opportunity Policy — the underlying *values* (Section A) are core; the specific mechanism is interchangeable and, per ADR-034's own design, deliberately built behind a swappable port.

### C. Experimental / Unvalidated (need evidence)

- H1 (driver willingness to share unmet demand) and H2 (willingness to pay).
- Any specific candidate mechanism for the four still-open Product Decisions.
- The manual pilot's own operational design.

### D. Deferred (explicitly not now)

- Reciprocity/Mutuality Engine, reputation/rating/trust score, dynamic pricing, payment custody/commission engine, AI/ML selection, geospatial optimization, KYC automation, Fleet as a domain.

**Conclusion:** the smallest coherent PIOS *product* is the four-module skeleton plus the event substrate plus the values layer (fairness, transparency, autonomy, relationship protection) — this already exists, mostly, in code. The manual pilot is not the product; it is a *validation instrument* aimed at one specific future extension (B) of an already-larger product (A).

## Part 7 — Implementation Readiness

| Capability | Classification | Reasoning |
| --- | --- | --- |
| Dispatch publishing OrderAssigned/AssignmentAccepted | **READY NOW** | Contract fully specified (INTERFACE_CONTRACTS.md §5), event meaning fully ratified (EVENT_CATALOG.md §6), pattern proven twice already, zero new product decision |
| Order Management consuming AssignmentAccepted | **READY NOW** | Same reasoning, symmetric to Dispatch's own already-proven consumer pattern |
| Passenger Experience → Order Management real transport (REST) | **READY NOW at the architecture level, but broader in scope** | ADR-004/ADR-028 already select REST; no concrete endpoint design exists yet — a real but boundable next step, not blocked by any missing decision, only by scope size (kept out of Tranche 1 for narrowness, Part 9) |
| "List all available drivers" query | **DEFER** | Named, not needed (no consumer exists yet); building it now would be a speculative abstraction |
| Driver standing/participation implementation | **BLOCKED BY PRODUCT DECISION** | No ratified criteria exist for what "standing" concretely means beyond the label (DOMAIN_MODEL.md Section 4) |
| Personal Client Relationship (aggregate/lifecycle) | **BLOCKED BY PRODUCT DECISION** | Creation, confirmation, exclusivity all explicitly unresolved (PCR decision Part 8) |
| Fulfillment Authority mechanism (automated) | **BLOCKED BY PRODUCT DECISION + BLOCKED BY VALIDATION** | MVP mechanism unselected; pilot is actively testing the underlying premise |
| Opportunity / Eligibility / Fair Opportunity Policy implementation | **BLOCKED BY VALIDATION** (primarily) **+ BLOCKED BY PRODUCT DECISION** | ADR-034's own Negative Consequences already state implementation cannot proceed until a policy is both architecturally defined and product-authorized; H1/H2 evidence does not yet exist |
| Payments/Administration/Analytics/Notifications scaffolding | **DEFER** | No use case identified; Notifications explicitly found no MVP need |

## Part 8 — Safe Work That Can Proceed Now

Each of the task's own suggested examples verified against code and authority, not assumed:

| Candidate | Verified against | Classification |
| --- | --- | --- |
| Dispatch publishing OrderAssigned | Confirmed missing (Part 2); contract fully specified; zero product dependency | **DO NOW** |
| Dispatch publishing AssignmentAccepted | Same module, same effort, naturally bundled with the above | **DO NOW** |
| Order Management consuming AssignmentAccepted | Confirmed missing (Part 2); symmetric to Dispatch's own proven consumer pattern | **DO NOW** |
| Outbox/event delivery integration generally | 2 of 3 flows already complete; this is the third | **DO NOW** (as above) |
| Contract verification upgrade, Passenger Experience ↔ Order Management (ADR-027 stub → ADR-028 real transport) | Confirmed still on the older stub mechanism (INTERFACE_CONTRACTS.md Section 12); real, valuable, product-decision-independent | **DO LATER** — broader scope (new REST endpoint design) than Tranche 1's own narrowness requirement; sequenced after it, not blocked by it |
| Missing end-to-end tests | `MvpVerticalSliceScenarioTest` exists in `order-management`; its actual cross-module coverage was not independently re-verified line-by-line in this audit | **DO NOW**, as part of Tranche 1's own test requirements (Part 9, Item 9) — verify or extend it, don't assume it already covers the new flow |
| "List all available drivers" query gap (ADR-034 Part 6) | Confirmed named, not needed by any existing consumer | **DO NOT DO** — speculative until a real consumer exists |

## Part 9 — Implementation Tranche #1

**This is a recommendation, not an authorization to implement.**

### 1. Objective

Give Dispatch's own `OrderAssigned` and `AssignmentAccepted` events the same production-grade outbox + RabbitMQ publishing infrastructure Order Management and Driver Management already have, and give Order Management a real RabbitMQ consumer for `AssignmentAccepted` — closing the only two genuine event-flow gaps identified anywhere in the current architecture (Part 2).

### 2. Why This Is First

Zero new product decision or ADR required. The pattern is already proven twice (Order Management's and Driver Management's own publishers; Dispatch's own consumer). The contract is fully specified (INTERFACE_CONTRACTS.md Section 5; EVENT_CATALOG.md Section 6). It is narrow, bounded, and independently testable. It delivers real, observable, end-to-end product capability — Order Management finally reflects real assignment outcomes in production — regardless of pilot outcome or any Opportunity/Eligibility/Fair Opportunity Policy decision (Part 6, Section A).

### 3. Exact Capabilities Delivered

Dispatch reliably publishes `OrderAssigned` when an assignment is created and `AssignmentAccepted` when accepted, using the same transactional-outbox pattern already proven (aggregate write + outbox record in one transaction). Order Management reliably consumes `AssignmentAccepted` idempotently, mirroring Dispatch's own already-proven idempotent consumption of `DriverAvailabilityChanged`.

### 4. Modules Affected

Dispatch (publishing side); Order Management (consuming side). Neither Driver Management nor Passenger Experience is touched.

### 5. Existing Files Likely Affected

- Dispatch: `DispatchAssignmentApplicationService.kt` (add outbox + transaction wiring, mirroring `OrderLifecycleApplicationService.kt` and `DriverAvailabilityApplicationService.kt` exactly).
- Order Management: `OrderAssignmentRecognitionHandler.kt` (today a pure stub) becomes real logic invoked by a new listener, mirroring Dispatch's own `DriverAvailabilityChangedListener.kt` pattern.

### 6. New Files Likely Required

- Dispatch (application layer): `OutboxRecord.kt`, `OutboxRepository.kt`, `NoOpOutboxRepository.kt`, `EventPublisher.kt`, `NoOpEventPublisher.kt`, `OutboxRelay.kt` — all following the exact, already-proven shape from `order-management`/`driver-management`.
- Dispatch (persistence layer): `PostgreSQLOutboxRepository.kt`, `RabbitMQEventPublisher.kt`, a producer-side `RabbitMQTopologyConfiguration` (or extension of the existing consumer-side one), a new `V3__outbox.sql` migration.
- Order Management: a new `AssignmentAcceptedListener.kt`, consumer-side `RabbitMQListenerContainerConfiguration`/topology/recoverer (mirroring Dispatch's own), a new idempotency-ledger migration.

### 7. Events/Contracts Involved

`OrderAssigned`, `AssignmentAccepted` — both already fully specified. No new event, no schema change, no contract change.

### 8. Database Impact

Additive only: a new `dispatch_outbox` table (mirroring `order_management_outbox`/`driver_management_outbox` exactly) and a new idempotency-ledger table in Order Management (mirroring Dispatch's own `driver_availability_processed_events`). No existing table is altered.

### 9. Tests Required

Mirroring the existing proven suites exactly: a publisher test, an outbox-relay test, and a publication/envelope test for Dispatch's publishing side; a consumer integration test, an idempotency test, a transaction-rollback test, and a dead-letter test for Order Management's consuming side (mirroring Dispatch's own four-test pattern for `DriverAvailabilityChanged`). Verify or extend `MvpVerticalSliceScenarioTest` to actually exercise the new flow, rather than assuming it already does.

### 10. Acceptance Criteria

An assignment created or accepted in Dispatch produces a durable outbox record in the same transaction; a running relay publishes it via RabbitMQ; Order Management's consumer receives it, records it idempotently (a redelivered message produces no duplicate effect), and an exhausted-retry message reaches Order Management's own dead-letter queue — all verified against real PostgreSQL and RabbitMQ instances, consistent with this project's existing constructor-based testing philosophy (ADR-013).

### 11. Explicit Non-Goals

No Opportunity, Eligibility, or Fair Opportunity Policy concept. No Assignment Policy port. No change to `Assignment`'s own CREATED/ACCEPTED semantics. No REST API. No change to any existing event's schema or meaning.

### 12. Risks

Low. The pattern is proven twice already; the main risk is migration-filename collision across modules (already solved once, per existing per-module migration-path convention). A secondary risk is scope creep toward "since we're touching Dispatch's outbox anyway, add the Assignment Policy port too" — explicitly guarded against by Section 11.

### 13. Dependencies

None outside this tranche. PostgreSQL and RabbitMQ are already running infrastructure. No new ADR, no new Product Decision, no pilot evidence required.

**If the correct first tranche is to finish already-ratified event-driven infrastructure rather than add a new product concept — yes, that is exactly this recommendation.**

## Part 10 — Roadmap After Tranche #1

```
ENGINEERING TRACK (product-decision-independent, proceeds regardless of pilot outcome)
Tranche 1: Dispatch event publishing completion
   -> Tranche 2 candidate: Passenger Experience <-> Order Management real transport (REST, ADR-028)
   -> Tranche 3 candidate: further infra hardening (observability, security — ADR-011/012 depth not
      fully re-audited here; flagged for a future, separate check)

VALIDATION TRACK (product-decision-dependent, gated on evidence)
Manual Network Pilot (running now, per the four Product Experiment documents)
   -> Pilot Evidence Gate (continue / modify / stop, per PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md
      and PRODUCT_EXPERIMENT_NETWORK_PILOT_LAUNCH_PACK.md Section 9)
   -> [if continue] Product Decision: select MVP mechanisms (Fulfillment Authority mechanism,
      Eligibility criteria, Fair Opportunity Policy candidate)
   -> ADR: Assignment Policy port + Fulfillment Authority architecture + Opportunity architecture
      (mirroring ADR-034's own already-established pattern)
   -> Additive event/contract extension -> additive DB changes -> application implementation
   -> Controlled larger-scale pilot/rollout
```

**Where they converge.** The Engineering Track's Tranche 1 output — a fully event-consistent Order↔Dispatch↔Driver system — becomes the substrate any future automated Opportunity/Eligibility/Fair-Opportunity implementation would build on. Tranche 1 must complete *before* that future implementation begins, but Tranche 1 itself does **not** wait on the Validation Track's own Pilot Evidence Gate; the two tracks run in parallel until that gate, after which the Validation Track's next Product Decision and ADR work is informed by both the pilot's evidence and the Engineering Track's by-then-completed infrastructure.

## Part 11 — Document Hygiene Audit

No document is deleted or merged by this audit; only classification is provided.

- **Authoritative (top-down):** PROJECT_CONSTITUTION.md → the nine Product Decisions and Experiments (Dispatch Philosophy, Personal Client Relationship, Fulfillment Authority and Scope, Opportunity/Acceptance/Commitment, Eligibility, Fair Opportunity Policy, Notifications, MVP Pilot Boundary, plus the four Network Pilot experiment documents) → the 34 ADRs → DOMAIN_MODEL.md/EVENT_CATALOG.md/INTERFACE_CONTRACTS.md/MODULE_STRUCTURE.md/APPLICATION_ARCHITECTURE.md → API/Data/Database documents.
- **Supporting analysis (subordinate to authority, not itself authority):** PRODUCT_BASELINE_V2.md, ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md, and this document itself.
- **Experiment documents:** PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md, `_EXECUTION_PLAN.md`, `_OPERATIONS_CHECKLIST.md`, `_LAUNCH_PACK.md`.
- **Potentially fragmented (flagged, not acted on).** The four Network Pilot experiment documents describe the **same** manual scenario and workflow at four, increasingly operational levels of detail, each faithfully cross-referencing the others — but the same 12-step workflow, the same four roles, and the same manual case-log concept are independently restated in three of the four documents (Execution Plan, Operations Checklist, Launch Pack). No document is factually wrong or contradictory, and each was scoped exactly as its own task commissioned it — but a newcomer must read all four to get the complete operational picture. **A future documentation cleanup — consolidating these four into a single "Network Pilot Playbook" once the pilot's real operational needs are known from actually running it — is worth considering, but not now**, since the pilot has not yet run and premature consolidation risks discarding detail before it is known which level of detail was actually needed.
- **Obsolete/superseded material:** **none found.** No ADR has been superseded; no Product Decision has been superseded; every document remains current as of this audit.

## Part 12 — Required Output Document

This document: `docs/PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md`. `docs/README.md` receives one minimal traceability entry (below). No other document is created or modified.

## Part 13 — Required Final Decision Table

| Area | Current State | Authority | Next Action | Blocker |
| --- | --- | --- | --- | --- |
| Order Management | Lifecycle + OrderSubmitted/Completed/Cancelled publishing implemented; AssignmentAccepted consumption stubbed only | ADR-002, ADR-018; EVENT_CATALOG.md §5 | Build real AssignmentAccepted consumer (Tranche 1) | None |
| Driver Management | Availability fully implemented and published; standing/participation unimplemented | DOMAIN_MODEL.md §4 | None urgent | Product Decision on what "standing" concretely means |
| Dispatch | Assignment CREATED/ACCEPTED implemented; own event publishing unbuilt; Assignment Policy named, not built | ADR-002, ADR-034 | Build OrderAssigned/AssignmentAccepted publishing (Tranche 1) | None for Tranche 1; Assignment Policy blocked by pilot evidence |
| Passenger Experience | Thinnest module; no persistence; contract to Order Management still on ADR-027 stub | ADR-004, ADR-027, ADR-028 | Design first real REST transport (Tranche 2 candidate) | Scope/endpoint design, not a missing decision |
| Personal Client Relationship | Concept only, zero code, lifecycle fully open | DOMAIN_MODEL.md §5; PCR Product Decision | Await Product Decision resolving Part 8's open questions | Product Decision |
| Fulfillment Authority | Boundary and authorization rules decided; mechanism unselected | Product Decision Fulfillment Authority and Scope | Await MVP mechanism selection or pilot evidence | Product Decision + Validation |
| Opportunity | Semantic boundary decided; no implementation, no architecture | Product Decision Opportunity/Acceptance/Commitment | Do not build yet | Validation |
| Eligibility | Boundary decided; Availability substitutes de facto | Product Decision Eligibility and Minimum Trust Boundary | Do not build yet | Validation |
| Fair Opportunity Policy | 7 candidates classified; none selected | Product Decision Fair Opportunity Policy | Await MVP mechanism selection or pilot evidence | Product Decision + Validation |
| Assignment / Commitment | Already correctly implemented (CREATED = proposal, ACCEPTED = commitment) | Product Decision Opportunity/Acceptance/Commitment; `Assignment.kt` | None | None |
| Event infrastructure | 2 of 3 flows complete and proven; 1 flow (Dispatch's own publishing) missing | ADR-028–032 | Tranche 1 | None |
| Business model | Entirely open; direct payment ratified for pilot only | PRODUCT_BASELINE_V2.md §13; Constitution §19 | Await pilot evidence (H2) | Validation |
| Manual pilot | Fully documented, zero code required, ready to run | Four Product Experiment documents | Run it | None |

## Part 14 — Self-Challenge

1. **Are we over-documenting instead of shipping?** Partially, yes — nine Product Decision/Experiment documents were produced in direct succession with zero code shipped in between. This review is itself another documentation artifact. The honest mitigation is Part 9's own recommendation: the next actual output should be code (Tranche 1), not another document.
2. **Are we about to implement concepts that have not been validated?** No, provided Tranche 1 is followed as scoped — it introduces no Opportunity, Eligibility, or Fair Opportunity Policy concept. The risk would materialize only if a future task tried to implement any of those three before pilot evidence exists.
3. **Are we using "pilot first" as an excuse to stop safe engineering work?** This was the exact trap Part 8 was designed to test for. The answer is no, provided Tranche 1 proceeds in parallel with the pilot rather than waiting for it — which this review explicitly recommends (Part 10).
4. **Have recent documents accidentally narrowed or distorted the original PIOS vision?** Partially, in emphasis only, not in substance. Part 5's self-check found the underlying general Platform-Order mechanism entirely intact and unmodified by the recent chain. But the four Network Pilot experiment documents' intense, repeated focus on one Driver-A/Driver-B/Coordinator scenario risks giving a future reader the false impression that this scenario **is** the product, rather than one currently-being-validated extension of a broader, older thesis. This review's own Part 1 and Part 6 exist specifically to correct that impression.
5. **Is Implementation Tranche #1 actually the highest-leverage safe next move?** Yes, among product-decision-independent work — it is the most complete, most proven, most narrowly-scoped gap identified anywhere in this audit. The one genuine competing candidate — building the first REST API surface (Part 2's second infrastructure gap) — is real and valuable, but broader in scope (no endpoint design exists yet to build on) and is therefore sequenced as Tranche 2, not folded into Tranche 1.
6. **What would be wasted work if the pilot disproves H1?** None of Tranche 1. Order Management correctly reflecting Dispatch's own assignment outcomes is valuable for *any* version of PIOS, including one where relationship-preserving fallback is never automated at all.
7. **What engineering work remains valuable even if H1 fails?** All of Tranche 1, and the eventual Tranche 2 (real transport for Submit Order) — both are foundational plumbing for the product core identified in Part 6, Section A, independent of whether the specific fallback extension (Section B) ever gets built.

**No revision to the Part 9 recommendation was required by this self-challenge.**

---

Where this document finds no evidence, it states so explicitly rather than filling the gap. This review does not override PROJECT_CONSTITUTION.md, any ADR, or any Product Decision; acting on Part 9's recommendation requires a further, explicit implementation task, never inferred from this review alone.
