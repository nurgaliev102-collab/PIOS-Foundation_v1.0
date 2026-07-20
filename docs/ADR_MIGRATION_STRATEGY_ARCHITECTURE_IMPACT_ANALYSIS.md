# ADR Migration Strategy and Architecture Impact Analysis (After Product Baseline v2.0)

Status: Architecture Impact Analysis Checkpoint. This document is **not** an ADR, **not** a Product Decision, and **not** an implementation plan. It does not override PROJECT_CONSTITUTION.md, any existing ADR, or any existing Product Decision — where it finds a gap or an ambiguity, it surfaces it (Sections 9, 12) rather than resolving it. It assesses the current repository — documentation and code — against the five completed Product Decisions (Product Baseline v2.0, Fulfillment Authority and Scope, Opportunity/Acceptance/Commitment Semantics, Eligibility and Minimum Trust Boundary, Fair Opportunity Policy) without reinterpreting any of them.

---

## 1. Purpose and Scope

This document is the architecture impact assessment checkpoint the Product Decision chain's own recommended sequences (PRODUCT_BASELINE_V2.md Section 19 Item 6; PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md's Recommended Next Step) anticipated before any implementation begins. Its scope is limited to: inspecting current documentation and code, classifying every relevant artifact (KEEP/ADAPT/DEFER/MISSING/CONFLICT-NEEDS DECISION), and producing a dependency-ordered migration sequence. It creates no ADR, modifies no existing ADR, and authorizes no code, migration, API, or event contract change.

## 2. Authority Sources

Inspected, in the order PROJECT_CONSTITUTION.md Section 5 establishes: PROJECT_CONSTITUTION.md; PRODUCT_BASELINE_V2.md; PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md; PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md; PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md; PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md; DOMAIN_MODEL.md; EVENT_CATALOG.md; INTERFACE_CONTRACTS.md; MODULE_STRUCTURE.md; every ADR in `docs/ADR/` (ADR-002, ADR-010, ADR-015, ADR-030, and ADR-034 read in full for this task; the remainder already inspected in the course of producing PRODUCT_BASELINE_V2.md and reused here without reinterpretation). Code inspected: `backend/order-management`, `backend/driver-management`, `backend/dispatch`, `backend/passenger-experience`, including all SQL migrations under each module's `db/migration/` directory.

## 3. Current Architecture Summary

Four of the eight ratified capability-based modules are scaffolded (`backend/settings.gradle.kts`: `order-management`, `driver-management`, `dispatch`, `passenger-experience`; Payments, Administration, Analytics, Notifications remain unscaffolded, unchanged from PRODUCT_BASELINE_V2.md Section 17). Order Management's `Order` models SUBMITTED/COMPLETED/CANCELLED only, with no Origin field. Dispatch's `Assignment` models CREATED/ACCEPTED only; `Assignment.create()` takes an already-externally-chosen driver and fires `OrderAssigned` immediately at CREATED, before any acceptance. Driver Management's `Driver` models only `Availability` (AVAILABLE/UNAVAILABLE); standing and participation, though named in DOMAIN_MODEL.md Section 4, are not implemented. Event infrastructure (transactional outbox, RabbitMQ topology, idempotent consumption, retry/DLQ) is fully built and proven for OrderSubmitted (Order Management, publisher) and DriverAvailabilityChanged (Driver Management publisher → Dispatch consumer) — but **not** for OrderAssigned/AssignmentAccepted (Dispatch → Order Management), where only a contract-verification stub exists on each side (`OrderAssignedPublisher`, `OrderAssignmentRecognitionHandler`), with no outbox or RabbitMQ listener wired for either. No Fulfillment Authority, Opportunity, Eligibility, or Fair Opportunity Policy concept exists in code, an aggregate, an event, or a database table anywhere — each is now a classified product concept (per the four Product Decisions) but none has any architectural or implementation presence yet.

## 4. Domain Impact Analysis

### 4.1 Order

**Current meaning:** a request for transportation tracked through SUBMITTED → COMPLETED/CANCELLED (`Order.kt`, `OrderStatus.kt`); no Order Origin, no fulfillment-scope, no authorization field of any kind exists.

- **Does Order represent demand only?** Yes. **[RATIFIED]** — Order's ratified purpose is "a request for transportation... before or as it becomes a ride" (PRODUCT_FOUNDATION.md Section 10); nothing in its implementation or its ratified definition represents authorization.
- **Does Order incorrectly imply fulfillment authorization?** No. **[RATIFIED]** — precisely because no Origin or authorization field exists today, Order carries no such implication; this is correct alignment, by omission, with PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md's own finding that Order existence never itself implies authorization (that document's Section 2). The risk is prospective, not current: a future Origin field must be added carefully to preserve this separation, not because the separation is currently violated.
- **Is OrderSubmitted still valid?** Yes. **[RATIFIED]** — its ratified meaning ("an order has been received... before any assignment has occurred," EVENT_CATALOG.md Section 5) is unaffected by any of the four Product Decisions; none requires OrderSubmitted itself to change.

**Baseline impact: KEEP** (current scope and meaning both remain correct); **MISSING** (Order Origin / any Fulfillment Authority carrier is absent, consistent with PRODUCT_BASELINE_V2.md Section 17's own finding, not newly discovered here).

### 4.2 Assignment

- **`Assignment.create()`.** Takes an externally-supplied driver reference; no selection logic exists (consistent with ADR-002's exclusion of the algorithm from every level of documentation). **KEEP** as a decision-recording mechanism; the seam ADR-034 already named (Assignment Policy) remains the correct, still-unbuilt extension point — nothing about `create()` itself needs to change to accommodate a future policy.
- **Assignment `CREATED` state.** Fires `OrderAssigned` immediately, before acceptance, for one already-chosen driver. **KEEP** as a correct representation of "a proposal has been recorded for this one named driver" (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Sections 2, 10) — it is not broken, and does not need to change to remain valid for the case it already handles.
- **Assignment `ACCEPTED` state.** **KEEP** — already, correctly, represents Commitment (same document, Sections 5, 10: "[RATIFIED — current code behavior confirms the same atomicity]").

**Does current Assignment correctly represent Commitment?** Yes, for the `ACCEPTED` state specifically — this is settled (Section 4.2 of PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md). **What changes are needed?** None to existing `Assignment` code as a documentation-only checkpoint; a future implementation would need an Assignment Policy port (ADR-034) resolving "which driver" from a genuine candidate pool *before* `Assignment.create()` is ever invoked — `Assignment` itself requires no redesign.

**Is Opportunity a missing pre-assignment concept?** Yes. **MISSING** — confirmed directly by PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md's own Central Finding: no candidate-pool concept exists anywhere in this codebase; the "which driver" decision already occurs, by an unspecified external means, before `Assignment.create()` is ever called.

### 4.3 Fulfillment Authority

**Is there any existing representation?** No. **[RATIFIED absence]** — confirmed OPEN in PRODUCT_BASELINE_V2.md Section 6 and named, not implemented, by PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md itself; no field, aggregate, event, or check exists anywhere in `Order`, `Assignment`, or any other class in this codebase.

**Where would the boundary logically belong?** Evidence points in two directions without clearly resolving between them, and this analysis does not decide implementation ownership without stronger support than currently exists:

- PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 4 already resolved that only the **requester** (passenger or corporate customer) may authorize scope expansion — the authorizing *act* belongs to the party Passenger Experience represents.
- Order Origin, the closest existing ratified concept to a scope marker, is conceptually part of the Order itself (DOMAIN_MODEL.md Section 6) — suggesting Order Management as the natural home for any resulting *record* of authorized scope.
- The existing Passenger Experience → Order Management contract (INTERFACE_CONTRACTS.md Section 5) fires only at submission; Fulfillment Authority's "authorization to search alternatives" (per the same Product Decision, Section 2) may occur *after* submission, when a preferred/direct fulfillment attempt has already failed — meaning today's single existing contract does not naturally carry a later authorization act regardless of which module ends up owning the fact.

**Conclusion:** MISSING (no mechanism exists), with **ownership itself recorded as an Open Decision (Section 12)** rather than resolved here, per this task's own instruction not to decide ownership without evidentiary support.

### 4.4 Opportunity

**Is it required?** Only once NETWORK-scope fallback is actually authorized and implemented — it is not required for DIRECT scope or for today's Platform-Order-only implementation. **Is it only required for NETWORK?** Yes. **[RATIFIED, reused directly]** — PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 2 and PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3 both establish that DIRECT scope has no candidate pool to expose an opportunity to. **Is it missing, or can existing concepts cover it?** MISSING — `Assignment`'s `CREATED` state, the only existing candidate, does not and cannot cover it, per Section 4.2 above.

### 4.5 Eligibility

**Current Availability model:** `AVAILABLE`/`UNAVAILABLE`, self-declared by the driver, nothing else (`Availability.kt`). **Driver Management ownership:** **[RATIFIED]** — Driver Management owns "standing, availability, and participation" (DOMAIN_MODEL.md Section 4), but only availability is implemented; if Eligibility is ever built, it would belong to Driver Management by the same, already-ratified ownership (PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 4). **Difference between availability and eligibility:** reused directly from that document's Section 3 — Availability is a moment-to-moment, self-declared signal; Eligibility would be a more durable standing-based gate; today, Availability functions only as a **de facto, undecided proxy** for Eligibility, purely because nothing else has ever been built, not because any document equates the two.

**Baseline impact: KEEP** (Availability, as implemented, is correct and untouched); **MISSING** (Eligibility as its own concept, at every layer).

### 4.6 Fair Opportunity Policy

**Current existence:** None. No metric, ranking, scoring, FIFO, Round Robin, or any other mechanism is implemented anywhere; ADR-034 names the Assignment Policy port without implementing it; PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md classifies seven candidate mechanisms (FIFO, Round Robin, ranking, scoring, contribution-based priority, service-quality priority, AI selection) plus two explicitly excluded ones (reciprocity engine, reputation system) without selecting any.

**Should Assignment Policy be postponed until a policy is selected?** Yes. **[DERIVED, directly consistent with ADR-034's own Negative Consequences: "No implementation of assignment selection can proceed until a policy is both architecturally defined... and product-authorized (a still-open decision)."]** Building an Assignment Policy port implementation now, before a Product Owner selects even one MVP candidate (Fair Opportunity Policy Section 10, still unselected), would risk building infrastructure around an unvalidated hypothesis — contradicting Architecture First and Documentation First (Constitution Section 4). This analysis recommends explicit postponement, consistent with what ADR-034 already anticipated.

## 5. Module Impact Analysis

### Order Management

- **Current responsibility.** Order lifecycle (submit/complete/cancel); publishes OrderSubmitted/OrderCompleted/OrderCancelled via a fully-built outbox+RabbitMQ pipeline; consumes AssignmentAccepted only through a contract-verification stub (`OrderAssignmentRecognitionHandler`), never a real RabbitMQ listener.
- **Future responsibility (not yet authorized or built).** Possible host for an Order-Origin/Fulfillment-Authority-related record (Section 4.3); completion of real AssignmentAccepted consumption.
- **KEEP:** existing lifecycle and publishing infrastructure.
- **ADAPT:** none required to existing code; a future Order Origin field would be an additive schema/event change (ADR-030's non-breaking category), not a semantic break.
- **MISSING:** a real, production RabbitMQ consumer for AssignmentAccepted; any Order Origin/Fulfillment-Authority representation.

### Driver Management

- **Current responsibility.** Driver identity and availability; publishes DriverAvailabilityChanged via a fully-built outbox+RabbitMQ pipeline.
- **Future responsibility.** Possible eventual owner of Eligibility-related facts (standing/participation), if and when Eligibility is ever authorized as a concrete concept (PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 4).
- **KEEP:** existing availability capability, in full.
- **MISSING:** standing/participation implementation; any Eligibility fact and the new Driver Management → Dispatch contract that would be required to expose it (none exists today; INTERFACE_CONTRACTS.md Section 5 authorizes availability only).

### Dispatch

- **Current responsibility.** Assignment lifecycle (create/accept); local availability projection consumed from DriverAvailabilityChanged with full idempotency/retry/DLQ; `OrderAssigned`/`AssignmentAccepted` domain events exist but are **not** published via RabbitMQ — only the contract-verification stub (`OrderAssignedPublisher`) exists.
- **Future responsibility.** Eventual home for the Assignment Policy port (ADR-034); Opportunity lifecycle ownership for NETWORK scope specifically (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 6); completion of its own outbound event publishing infrastructure.
- **KEEP:** Assignment CREATED/ACCEPTED lifecycle; availability projection and consumer infrastructure; exclusive assignment-decision ownership (ADR-002), all unchanged.
- **ADAPT:** `DriverAvailabilityRepository`'s query shape — today only `findByDriverReference` (one known driver) — would need extension to a "list currently available drivers" query once any real policy needs it; already flagged, unchanged status, by ADR-034 Part 6.
- **MISSING:** its own outbox/`EventPublisher` for `OrderAssigned`/`AssignmentAccepted`; an Assignment Policy port implementation; any Opportunity mechanism, if one is ever built.

### Passenger Experience

- **Current responsibility.** Passenger/Corporate Customer representation; `OrderSubmissionIntent`/`OrderSubmissionRequested` as Order Management's originating context at submission; no Personal Client Relationship representation in code.
- **Future responsibility.** Possible home for the requester's own Fulfillment-Authority authorization act (Section 4.3), if the ownership question is ever resolved in its favor; possible home for a Personal Client Relationship lifecycle, if that separate, still-open question is ever resolved (PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md, unchanged).
- **KEEP:** existing originating-context responsibility, in full.
- **MISSING:** Personal Client Relationship representation (deliberately absent, per already-ratified documents, not an oversight); any Fulfillment Authority authorization mechanism.

## 6. Event and Contract Impact Analysis

- **OrderSubmitted.** Compatible. **[RATIFIED]** — no change needed; consistent with PRODUCT_BASELINE_V2.md Section 17's own KEEP finding.
- **OrderAssigned.** Ambiguous, in one specific, already-flagged respect: it fires at `CREATED`, before acceptance, and its ratified wording ("Dispatch has connected an order to a driver") could be misread as implying obligation. This is a documentation/interpretation ambiguity, not a technical or schema defect.
- **AssignmentAccepted.** Compatible. Its meaning ("a proposed assignment has been confirmed") is precise and unambiguous; no clarification needed.
- **DriverAvailabilityChanged.** Compatible. Meaning is precise; implementation is complete and consistent with it.

**Special focus — "OrderAssigned before acceptance":** is the event's meaning already safe, or does it need future ADR clarification? **[DERIVED]** — the event's *technical* meaning is already safe: EVENT_CATALOG.md Section 6 states it precisely as a fact about Dispatch's own recorded decision, not a claim about the driver's obligation, and every actual consumer today (the contract-verification stub; the Driver's own direct API exposure per API_SPECIFICATION.md Section 8) already treats it that way — none infers obligation from it. The product-level naming risk PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3 flagged remains real as a *documentation* risk for a future reader, not as a defect in any current consumer's behavior. Per ADR-015, if this is ever judged serious enough to formally address, it would require a **new** ADR (existing ADRs are never edited) — but this analysis does not conclude that ADR is required now, since no ratified document currently misreads it and no consumer's behavior depends on a wrong reading. **Recommendation: monitor; revisit only if a real Opportunity-aware consumer is later built and actual confusion surfaces in practice.**

## 7. Database Impact Analysis

| Table | Module | Current Shape | Classification |
| --- | --- | --- | --- |
| `orders` | Order Management | `id`, `status` | KEEP — unaffected by any Product Decision; a future Order Origin column would be an additive ADAPT, not decided here |
| `assignments` | Dispatch | `id`, `order_reference`, `driver_reference`, `status` | KEEP — already correctly separates CREATED/ACCEPTED via `status`; no change implied |
| `drivers` | Driver Management | `id`, `availability` | KEEP — unaffected; a future standing/participation/Eligibility column would be a future ADAPT, not decided here |
| `driver_availability` + `driver_availability_processed_events` | Dispatch (local projection) | `driver_reference`/`available`/`updated_at`; `event_id`/`processed_at` | KEEP — correctly scoped; the query-shape gap ADR-034 Part 6 already flagged (no "list all available" query) is an ADAPT once a real policy needs it, not a defect today |
| Outbox tables (Order Management, Driver Management) | — | Aggregate id / event type / routing key / payload / timestamps | KEEP — unaffected |
| Any Opportunity, Eligibility, Fulfillment Authority, or Fair Opportunity Policy table | — | Does not exist | MISSING, consistent with Section 4's domain findings |
| Any reciprocity, reputation, rating, or dynamic-pricing table | — | Does not exist | DEFER, consistent with PRODUCT_BASELINE_V2.md Section 15 |

## 8. KEEP / ADAPT / DEFER / MISSING Matrix

| Artifact | Classification | Basis |
| --- | --- | --- |
| Order lifecycle (submit/complete/cancel) | KEEP | Section 4.1 |
| OrderSubmitted event | KEEP | Section 6 |
| Order Origin / Fulfillment Authority carrier | MISSING | Section 4.1, 4.3 |
| Assignment `create()`/`CREATED` | KEEP | Section 4.2 |
| Assignment `ACCEPTED` (Commitment) | KEEP | Section 4.2 |
| OrderAssigned event | KEEP (technically); documentation ambiguity noted | Section 6 |
| AssignmentAccepted event | KEEP | Section 6 |
| Dispatch's own OrderAssigned/AssignmentAccepted publishing (outbox/RabbitMQ) | MISSING | Section 5 (Dispatch) |
| Order Management's real AssignmentAccepted consumption | MISSING | Section 5 (Order Management) |
| Driver availability (declaration + projection) | KEEP | Section 4.5 |
| DriverAvailabilityChanged event | KEEP | Section 6 |
| `DriverAvailabilityRepository` query shape | ADAPT (future) | Section 5 (Dispatch); ADR-034 Part 6 |
| Driver standing/participation | MISSING | Section 4.5 |
| Eligibility concept (any layer) | MISSING | Section 4.5 |
| Opportunity concept (any layer) | MISSING | Section 4.4 |
| Fulfillment Authority mechanism | MISSING | Section 4.3 |
| Fulfillment Authority ownership (Passenger Experience vs. Order Management) | OPEN (not CONFLICT) | Section 4.3, 12 |
| Assignment Policy port (ADR-034) | MISSING, deliberately postponed | Section 4.6 |
| Fair Opportunity Policy mechanism (any of the seven candidates) | MISSING, none selected | Section 4.6 |
| Personal Client Relationship (code representation) | MISSING, deliberately absent | Section 5 (Passenger Experience) |
| Reciprocity, reputation, rating, dynamic pricing | DEFER | Section 7 |
| Any existing ADR | KEEP, unmodified | Section 9 |

No artifact was found in CONFLICT / NEEDS DECISION — every gap identified is MISSING or OPEN against an already-acknowledged silence, never a contradiction of ratified authority. This analysis does not invent a conflict where none exists.

## 9. ADR Impact

**No existing ADR requires modification.** ADR-002's algorithm exclusion stands, unmodified. ADR-034's named-but-unbuilt Assignment Policy seam stands, unmodified — it anticipated exactly this dependency ("no implementation... until a policy is both architecturally defined... and product-authorized"). ADR-030's event-schema evolution policy already governs any future OrderAssigned/AssignmentAccepted schema change without needing to be touched now.

**Anticipated future ADRs** (not created here; each requires its own prerequisite Product Decision first, per ADR-015's rule that architecture changes come only through new, superseding-or-additive ADRs, never edits):

1. An ADR defining the Assignment Policy port's concrete interface shape — gated on a Product Owner selecting an MVP Fair Opportunity Policy candidate (Section 12).
2. An ADR defining the Fulfillment Authority boundary's architectural home — gated on resolving the Passenger Experience vs. Order Management ownership question (Section 4.3, 12).
3. An ADR defining Opportunity's architectural shape (aggregate, event, or neither) — gated on both of the above, since Opportunity's design depends on knowing where authorization and policy selection already live.
4. An ADR (or EVENT_CATALOG.md/INTERFACE_CONTRACTS.md extension under existing ADR authority) for any new Eligibility-exposing contract — gated on Driver Management's own future decision to expose standing/participation, per PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 5.

## 10. Migration Dependency Order

This task's own expected shape — (1) clarify existing semantics, (2) define missing boundaries, (3) ADR updates if required, (4) event contract evolution if required, (5) database changes, (6) application changes, (7) pilot validation — is confirmed as the correct order, with one refinement repository evidence supports:

1. **Clarify existing semantics** — substantially complete. Four Product Decisions already did this work (Fulfillment Authority and Scope, Opportunity/Acceptance/Commitment, Eligibility, Fair Opportunity Policy). **Remaining gap, not yet closed:** selecting concrete MVP candidates each of those documents left unselected (Fair Opportunity Policy Section 10; Eligibility Section 8; Fulfillment Authority Section 6). **This is the actual next step**, not Step 2 — designing boundaries around an unselected candidate space would risk the same premature-commitment risk Section 4.6 already flagged for Assignment Policy specifically.
2. **Define missing boundaries** — Fulfillment Authority's ownership; Opportunity's architectural home; Eligibility's concrete minimum criteria. Depends on Step 1's MVP selections being made first.
3. **ADR updates if required** — new ADRs only (Section 9's four anticipated candidates), never edits to ADR-002/ADR-030/ADR-034. Depends on Step 2.
4. **Event contract evolution if required** — additive changes only, per ADR-030's own non-breaking-change category, wherever possible (for example, a new optional field or a wholly new event, rather than a breaking change to OrderAssigned/AssignmentAccepted, both of which remain compatible as-is per Section 6). Depends on Step 3.
5. **Database changes** — additive migrations only (new tables/columns), consistent with every KEEP classification in Section 7 remaining untouched. Depends on Step 4.
6. **Application changes** — implement the Assignment Policy port, any Opportunity mechanism, and the two already-identified infrastructure gaps (Dispatch's own event publishing; Order Management's real AssignmentAccepted consumption) — the latter two are independent of the product-decision chain and could, in principle, proceed in parallel with Steps 1–5 since they involve no new product semantics, only completing already-authorized contracts. Depends on Step 5 for anything touching the new concepts; independent for the two pre-existing infrastructure gaps.
7. **Pilot validation** — tests H1/H2 (PRODUCT_BASELINE_V2.md Section 14); last, since it depends on every prior step having produced a concrete, ratified mechanism to validate.

**Refinement over the task's proposed order:** Step 6's two pre-existing infrastructure gaps (Dispatch's own publishing, Order Management's real consumption) do not depend on Steps 1–5 at all — they complete contracts already fully authorized by INTERFACE_CONTRACTS.md Section 5 and EVENT_CATALOG.md Section 6, untouched by any of the four new Product Decisions. They may be scheduled independently, in parallel with the Fulfillment Authority/Opportunity/Eligibility/Fair Opportunity Policy work, without waiting for it.

## 11. Explicit Non-Changes

No production code was modified: `Order.kt`, `OrderStatus.kt`, `Assignment.kt`, `AssignmentStatus.kt`, `AssignOrderCommand.kt`, `AcceptAssignmentCommand.kt`, `DispatchAssignmentApplicationService.kt`, `Driver.kt`, `Availability.kt`, `DriverAvailabilityRepository.kt`, `DriverAvailabilityProjectionApplicationService.kt`, `OrderAssignedPublisher.kt`, `OrderAssignmentRecognitionHandler.kt`, and every other file inspected were read only. No database migration was created or modified: `orders`, `assignments`, `drivers`, `driver_availability`, `driver_availability_processed_events`, and both modules' outbox tables remain exactly as they were. No API or event contract was modified: OrderSubmitted, OrderAssigned, AssignmentAccepted, and DriverAvailabilityChanged all retain their existing, ratified meaning. No existing ADR was modified: ADR-002, ADR-010, ADR-015, ADR-030, and ADR-034 are cited, not altered.

## 12. Open Decisions Before Implementation

1. **Fulfillment Authority ownership** — Passenger Experience, Order Management, or a joint arrangement (Section 4.3) — evidence does not yet clearly favor one, not decided here.
2. **MVP Fair Opportunity Policy candidate** — FIFO, Round Robin, or another, from the seven already-classified candidates — none selected (PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md Section 10).
3. **MVP Eligibility candidate** — Identity + manual admission + Availability, or another — none selected (PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md Section 8).
4. **MVP Fulfillment Authority mechanism** — manual per-order authorization vs. pre-authorized fallback — none selected (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 6).
5. **Opportunity's architectural shape** — aggregate, event, or neither — entirely undecided.
6. **Personal Client Relationship's role at each of its three independently-open gates** — DIRECT/NETWORK routing, Eligibility influence, NETWORK-priority influence — none resolved, none conflated.
7. **Whether/when the two pre-existing infrastructure gaps are closed** — Dispatch's own OrderAssigned/AssignmentAccepted publishing; Order Management's real AssignmentAccepted consumption — independent of the product-decision chain, schedulable in parallel (Section 10).
8. **Whether OrderAssigned's "connected" wording ever warrants a clarifying ADR** — recommended to monitor only; not concluded necessary now (Section 6).

Resolution of any of these requires a further Product Owner decision or, where architecture itself must change, a new ADR — never a silent edit to an existing one, per ADR-015 — before any lower-authority document or implementation task may act on it.

---

Where this document finds no evidence, it states so explicitly rather than filling the gap. This analysis does not override PROJECT_CONSTITUTION.md, any existing ADR, or any existing Product Decision; it is an impact analysis only.
