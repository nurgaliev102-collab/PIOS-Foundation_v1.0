# PIOS Product Baseline v2.0 — Consolidation After CORE-001–018

Status: Consolidation and Migration Baseline. This document is **not** a Constitution, **not** an ADR, and **not** an implementation plan. It is a reconciliation of a candidate product model (supplied by this task) against the repository's own already-approved authority, produced independently rather than copied from any prior conversation. It creates no new Product Decision, ratifies nothing by itself, and authorizes no implementation.

**PROJECT_CONSTITUTION.md remains the highest product authority.** This document is subordinate to it, to every existing Product Decision document, and to every existing ADR, per the Documentation Hierarchy in PROJECT_CONSTITUTION.md Section 5. Where this document identifies a conflict with existing approved authority, the conflict is surfaced explicitly (Section 16/17) rather than silently resolved in either direction.

---

## 1. Purpose and Authority

This document consolidates the product/domain stress-test referenced by this task (CORE-001–018) against the repository's actual, current documentation and code, and produces a repository impact analysis and a decision sequence. It was produced by independently inspecting the documents and code listed in Section 1's inspection scope below — not by assuming the candidate model supplied in this task's own prompt is already true of the repository.

**Authority order this document obeys** (PROJECT_CONSTITUTION.md Section 5): Constitution → Product Decisions → ADR → Architecture → Domain → API → Database → Implementation. This document sits at the Product Decision layer for evidence-classification purposes, but is explicitly **not itself a Product Decision** — it makes no Product-Owner-authority ruling; it only classifies what already exists and what does not.

**Documents inspected:** PROJECT_CONSTITUTION.md, PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, MODULE_STRUCTURE.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, APPLICATION_ARCHITECTURE.md, INTERFACE_CONTRACTS.md, ADR-002, ADR-033, ADR-034 (and, through their citations, ADR-025's still-open database-technology gate), PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md, PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md, PRODUCT_DECISION_NOTIFICATIONS.md.

**Code inspected:** `backend/order-management` (`Order.kt`, `OrderStatus.kt`, `OrderLifecycleApplicationService.kt`, `OrderAssignmentRecognitionHandler.kt`, `OrderSubmissionRequestHandler.kt`), `backend/dispatch` (`Assignment.kt`, `AssignOrderCommand.kt`, `AcceptAssignmentCommand.kt`, `DispatchAssignmentApplicationService.kt`, `DriverAvailabilityRepository.kt`, `DriverAvailabilityProjectionApplicationService.kt`, `DriverAvailabilityChangedListener.kt`, `OrderAssignedPublisher.kt`), `backend/driver-management` (`Driver.kt`, `DriverAvailabilityApplicationService.kt` and its outbox/publishing chain), `backend/passenger-experience` (`OrderSubmissionIntent.kt`, `PassengerOrderSubmissionApplicationService.kt`), and `backend/settings.gradle.kts`.

## 2. Evidence Classification

Every substantive statement below is tagged:

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved repository document, or directly observable in committed code, cited inline.
- **[DERIVED]** — logically required by the consolidated product model and compatible with higher authority, but not itself stated anywhere and not yet formally ratified.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; not rejected, not adopted.
- **[OPEN]** — not decided by any approved document. Not invented here.

Nothing tagged HYPOTHESIS or OPEN is promoted to a requirement anywhere in this document.

## 3. Product Identity

**What is already ratified about PIOS's identity** (PROJECT_CONSTITUTION.md Sections 18–19, itself consolidating PRODUCT_FOUNDATION.md): a trustworthy, transparent mechanism connecting independent supply with demand from multiple sources, without concentrating unaccountable control in a single intermediary **[RATIFIED]**; not an opaque aggregator, not a hidden dispatcher, not an owner of driver relationships **[RATIFIED]**; whether it is or is not a commission-based marketplace is explicitly **[OPEN]** (Constitution Section 19; DOMAIN_MODEL.md Section 14).

**The candidate identity statement this task supplies** — "PIOS is infrastructure for independent service professionals/drivers that allows existing client relationships to continue receiving service when the preferred driver cannot personally fulfill a request, by enabling explicitly authorized, transparent access to alternative eligible independent drivers without hidden dispatcher preference or automatic appropriation of participant relationships" — is evaluated clause by clause:

- "Infrastructure for independent drivers" — **[RATIFIED]**, consistent with Driver Independence (Constitution Section 18) and PRODUCT_FOUNDATION.md Section 12.
- "Allows existing client relationships to continue receiving service when the preferred driver cannot personally fulfill" — **[HYPOTHESIS]**. No document states this as PIOS's primary purpose or even a documented use case; Personal Client Relationship is ratified as a concept (DOMAIN_MODEL.md Section 5) but no lifecycle, fallback mechanism, or "cannot personally fulfill" trigger is ratified anywhere (Product Decision Personal Client Relationship Part 3, Part 8).
- "Explicitly authorized, transparent access to alternative eligible drivers" — **[DERIVED]** from Transparency, No Hidden Algorithmic Decisions, and Fair Dispatch (Constitution Section 18), but "authorized," "alternative," and "eligible" are not ratified vocabulary anywhere in DOMAIN_MODEL.md or EVENT_CATALOG.md.
- "Without hidden dispatcher preference" — **[RATIFIED]**, directly stated in PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 3, "No hidden dispatcher preference."
- "Without automatic appropriation of participant relationships" — **[RATIFIED]**, directly stated as Relationship Protection (Constitution Sections 3, 18).

**Conclusion:** the candidate identity statement is a plausible, internally consistent narrative **[HYPOTHESIS]** built from genuinely ratified fragments, but it is not itself a ratified identity statement. It should not be treated as decided without a Product Owner decision naming it as such.

## 4. Fundamental Actors and Relationships

No new actor is introduced. USE_CASE_CATALOG.md Section 2 already ratifies: Passenger, Driver, Dispatcher/Operational Role, Fleet (Domain Decision Required), Corporate Customer, Platform Administrator, Partner, External Service **[RATIFIED]**. Personal Client Relationship is a ratified conceptual entity, not an aggregate, spanning Driver Management and Passenger Experience jointly **[RATIFIED]** (DOMAIN_MODEL.md Section 5; Product Decision Personal Client Relationship Part 1–2). No "requester," "eligible driver pool," or "network participant" role exists as a distinct ratified actor beyond Passenger/Corporate Customer and Driver already named — introducing new actor terminology for the consolidated lifecycle would exceed what USE_CASE_CATALOG.md establishes; this document does not do so.

## 5. Core End-to-End Lifecycle

The candidate conceptual lifecycle (existing relationship → transport request → fulfillment intent/authority → direct/fallback → network → eligibility → opportunity → accept/decline/expire → assignment/commitment → fulfillment → completion) is evaluated against DOMAIN_MODEL.md Section 12's three ratified lifecycles (Order, Assignment, Driver Availability):

- **Transport request → Order.** **[RATIFIED]** mapping — Order Management's Submit Order / OrderSubmitted (DOMAIN_MODEL.md Sections 4, 8, 9).
- **Fulfillment intent/authority, DIRECT/REFERRED/NETWORK scoping.** **[OPEN]**. No ratified concept of "fulfillment authority" or a DIRECT/REFERRED/NETWORK taxonomy exists anywhere. Order Origin (DOMAIN_MODEL.md Section 6: Platform Order, Corporate Order, and — by implication — a personal-client-directed order) is the only ratified categorization of an order's source, and it is fixed at submission, not later "expanded." **Not implemented in code at all** — `Order.kt`'s own KDoc states Order Origin "is likewise not represented here."
- **Eligibility as distinct from availability.** **[RATIFIED]** as a structural distinction (Section 8 below), but no "Eligibility" concept, aggregate, or boundary is ratified.
- **Opportunity, Accept/Decline/Expire.** **[DERIVED]** in substance from the already-ratified Assignment lifecycle's CREATED-then-ACCEPTED distinction (Section 8 below); the specific vocabulary "Opportunity," "Decline," and "Expire" is **[OPEN]** — none exists in DOMAIN_MODEL.md, EVENT_CATALOG.md, or code.
- **Assignment/Commitment, Fulfillment, Completion.** **[RATIFIED]** mapping onto the existing Assignment and Order lifecycles (DOMAIN_MODEL.md Section 12).
- **Recovery after commitment failure, "within already-authorized scope, subject to revalidation."** **[OPEN]**. No document or code models any commitment-failure or recovery concept; Assignment has no state beyond CREATED/ACCEPTED and no decline/failure/reassignment path exists.

**Conclusion:** the conceptual lifecycle is a legitimate consolidation lens over already-ratified fragments (Order, Assignment, Availability), but every box added beyond those three — Fulfillment Authority, Opportunity, Eligibility, Recovery — is genuinely new vocabulary, consistent with existing authority but not established by it.

## 6. Fulfillment Authority and Scope

**[OPEN]**, in full. No approved document defines "fulfillment authority," "scope," or a DIRECT/REFERRED/NETWORK distinction. The closest ratified material is Order Origin (DOMAIN_MODEL.md Section 6) and the explicitly-undecided question of whether a personal-client-directed order is even routed through Dispatch's assignment decision at all (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 4; Product Decision Personal Client Relationship Part 5, Part 9). Introducing this concept formally requires a Product Decision (Section 17, Item 2), not invention here.

## 7. Network Opportunity Model

**[OPEN]**, in full, for the same reason as Section 6. "Network authorization," "eligibility boundary," and "opportunity" have no ratified basis. The one adjacent, already-ratified fact is that Dispatch's assignment decision is exclusively Dispatch's own (ADR-002) and currently has **no eligibility filter of any kind** in code — `DispatchAssignmentApplicationService.handle` accepts an already-chosen `driver` from its caller with no read of `DriverAvailabilityRepository` at all **[RATIFIED — current code behavior]**. ADR-034 Part 6 already, independently, surfaced the concrete gap this implies: `DriverAvailabilityRepository` supports only `findByDriverReference` (one known driver), not "every currently available driver" — no query shape exists yet for a candidate pool of any kind.

## 8. Acceptance and Commitment

**Candidate selection is not the same as accepted commitment.** **[RATIFIED]** — directly implemented: `Assignment.kt` models `AssignmentStatus.CREATED` and `AssignmentStatus.ACCEPTED` as distinct states; `create()` and `accept()` are separate operations; DOMAIN_MODEL.md Section 12 states the Assignment lifecycle as "made by Dispatch... then accepted." This is the strongest, most directly evidenced invariant among the candidate set.

**Opportunity is conceptually distinct from obligation.** **[DERIVED]** — the substance is ratified via the CREATED/ACCEPTED split above; the word "Opportunity" itself is not ratified vocabulary.

**Before acceptance, a driver may decline or fail to respond without violating a fulfillment commitment.** **[DERIVED]** from driver autonomy (PRODUCT_FOUNDATION.md Section 12) plus the CREATED/ACCEPTED split, but **not implemented**: `Assignment.kt` has no `decline()` method and no timeout/expiry concept at all — only `accept()` exists. An un-accepted assignment today has no modeled fate other than remaining CREATED indefinitely.

**Acceptance requires revalidation of relevant current state before commitment is established.** **[OPEN]**. `Assignment.accept()` checks only `status == CREATED`; it does not re-check driver availability or any other state at acceptance time. No document requires such revalidation either.

## 9. Failure, Cancellation, Expiration, and Recovery

- **Failure to secure fulfillment is semantically different from requester cancellation.** **[DERIVED]** — Cancellation is ratified as "the indication that an order or assignment has been terminated before completion" (DOMAIN_MODEL.md Section 6), which reads as an actor-driven act, conceptually distinct from a fulfillment attempt simply not succeeding. **Not implemented**: `Order.kt`/`OrderStatus.kt` model only SUBMITTED/COMPLETED/CANCELLED — there is no "unfulfilled," "expired," or "no driver found" state anywhere in code or in EVENT_CATALOG.md.
- **Decline, expiration, cancellation, and post-commitment failure are distinct concepts.** **[OPEN]**. Only Cancellation is ratified and implemented. Decline, expiration, and post-commitment failure have zero representation in any approved document or in code.
- **Recovery may automatically continue only inside already-authorized fulfillment scope.** **[OPEN]** — depends entirely on Section 6's unresolved Fulfillment Authority concept.
- **Technical message retry is not business fulfillment recovery.** **[RATIFIED]** — already a real, structural distinction in the implemented infrastructure: ADR-031/ADR-032's RabbitMQ retry-then-DLQ mechanism operates purely at the transport/delivery level, while `DriverAvailabilityProjectionApplicationService.handle`'s `markProcessed(eventId)` idempotency check exists specifically so a redelivered (retried) message never re-triggers a business effect a second time. The two are already cleanly separated in the one consumer path that exists today.
- **PIOS does not need to guarantee that every request will be fulfilled.** **[DERIVED]** — consistent with driver autonomy and the absence of any SLA/fulfillment-guarantee language in any document; no document states or denies a guarantee.

## 10. Eligibility and Minimum Trust Boundary

**Availability is not equivalent to eligibility.** **[RATIFIED]** as a structural distinction, though "eligibility" itself is not ratified vocabulary: DOMAIN_MODEL.md Section 4 already separates a Driver's "standing, availability, and participation" into distinct concerns, and ADR-034 Part 2 explicitly separates "Driver availability" (a current ratified input) from "any Driver Management-owned information beyond availability (standing, participation)" (a future candidate input requiring separate ratification). Availability, standing, and participation are already three distinct ratified concepts; no single one of them is defined as sufficient for "eligibility" to receive network opportunities, because "eligibility" as a boundary concept does not exist yet. **[OPEN]** what a minimum trust/eligibility boundary would actually require.

## 11. Fairness and Explainability

**Fairness applies primarily to transparent access, not forced equality of outcomes or income.** **[RATIFIED]** — PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5 states this almost verbatim: "fairness for PIOS is a property of how the decision is made and whether it can be explained, not yet a property of what outcome distribution results." The specific fairness metric or model remains **[OPEN]** (same document, Section 8).

**Payment to PIOS must not silently buy priority in shared opportunity access.** **[DERIVED]** — consistent with Platform Neutrality, No Hidden Algorithmic Decisions, and Fair Dispatch's subordination of efficiency, but not itself stated in these words by any approved document. Carried forward per Section 12 below.

## 12. Relationship Protection

- **One fulfilled order does not automatically create or transfer a Personal Client Relationship.** **[RATIFIED]** — DOMAIN_MODEL.md Section 13, verbatim: the relationship "exists independent of any single Order"; Product Decision Personal Client Relationship Part 7 restates this as a ratified Product Invariant.
- **PIOS must not automatically appropriate, transfer, or remarket a participant's client relationship.** **[RATIFIED]** — Relationship Protection (Constitution Sections 3, 18): "never owned or captured by the platform."
- **Relationship Protection does not mean ownership of a human being/customer, permanent exclusivity, or prohibition on voluntary new relationships.** Split: **not ownership** is **[RATIFIED]** (Constitution Section 3; Product Decision Personal Client Relationship Part 1, "it is NOT ownership"). **Whether it can be exclusive, and whether it prohibits or permits voluntary new relationships,** is explicitly **[OPEN]** — Product Decision Personal Client Relationship Part 8 lists exclusivity as an unresolved open question; asserting either answer here would contradict that document.
- **No participant gains unilateral hidden power to exclude competitors from the common network via private whitelist/blacklist rules.** **[DERIVED]** — consistent with No Hidden Algorithmic Decisions and Platform Neutrality, and with the fact that Dispatch itself has no ratified relationship to Personal Client Relationship information at all (Product Decision Personal Client Relationship Part 2: "No ratified role... [RATIFIED absence]"), meaning no mechanism for such exclusion exists in any ratified document or in code. Not itself stated as an anti-principle anywhere.

## 13. Economic Model Status

Per PROJECT_CONSTITUTION.md Section 19 and DOMAIN_MODEL.md Section 14, the commercial/commission model is **[OPEN]**, not ratified in either direction.

**Leading hypothesis to evaluate — driver-paid membership/subscription for recurring coordination and network infrastructure.** **[HYPOTHESIS]**. No approved document supports or rejects this specific model. Recorded as a candidate only.

**Explicitly preserved as OPEN, not decided by this document:** exact payer; subscription price; free/freemium structure; billing period; whether network access is separately monetized; whether transaction fees may ever exist; legal/commercial structure; payment-processing role.

**"Commission-free forever" is not established as a constitutional law.** **[RATIFIED absence]** — Constitution Section 19 states this precisely: the commercial model "remains genuinely undecided, not because a commission model has been ratified against."

**Anti-conflict principle for future evaluation — payment to PIOS should not secretly purchase higher priority in fair shared-network opportunity allocation.** **[DERIVED]**, per Section 11 above. Not yet a ratified constraint on any specific mechanism, since no mechanism (subscription, fee, or otherwise) is ratified at all.

## 14. MVP / Pilot Boundary

**[HYPOTHESIS]**, in full — no approved document describes any pilot, cohort size, admission mechanism, or payment-custody boundary. The candidate pilot design (one local cohort, ~10–30 drivers, manual admission, direct requester/executor payment, no PIOS payment custody required) is recorded as a candidate validation loop, not a ratified plan.

**H1 (will drivers share unmet relationship-sourced demand when another driver may interact directly with their client?)** and **H2 (will drivers pay enough, after real value, to sustain PIOS?)** are recorded as **unvalidated existential hypotheses** — neither is claimed validated by this document, consistent with the task's own instruction.

**Driver-attested vs. passenger-confirmed authorization** are explicitly distinguished as different things, neither ratified: Product Decision Personal Client Relationship Part 3/Part 8 already flags "must both sides confirm" as unresolved — the same unresolved distinction applies directly to any pilot-level authorization design.

## 15. Explicitly Deferred Concepts

Per ADR-034 Part 4 ("Candidate future strategies... none is approved, previewed, or ranked") and PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5 (dimensions marked **[REQUIRES VALIDATION]**), the following are **[RATIFIED as deferred / not core]** — it is ratified that they are undecided and deferred, not that any is adopted:

- Reciprocity Engine, Mutuality Registry, 90-day give/receive balance, reciprocity-based dispatch priority — ADR-034 Part 4 names "reciprocal balancing" only as one of several unapproved candidates; Constitution Section 18 explicitly excludes reciprocity from the Fundamental Laws for exactly this reason.
- Composite driver trust score, opaque ranking, passenger star-rating dependency — no rating/reputation/trust-score concept exists in DOMAIN_MODEL.md at all; ADR-034 Part 2 classifies "Driver profile, standing, participation, or ranking beyond availability" as a **forbidden input** to the assignment decision today.
- Dynamic pricing, payment custody, commission engine — Payments' own scope excludes any pricing/commission rule (DOMAIN_MODEL.md Section 14); no payment custody concept exists anywhere.
- AI-based assignment, pay-to-win dispatch priority, city-scale geospatial dispatch — driver location/positioning is explicitly "structurally unsupported... not yet even a candidate" (ADR-034 Part 2, citing EVENT_CATALOG.md Section 7).
- Complex automated KYC infrastructure — no such concept exists in any approved document.

**None of these references are deleted or altered by this document** — each is identified above with its existing location, consistent with this task's own instruction not to silently remove deferred references.

## 16. Open Product Decisions

Consolidated from every source document inspected, without resolving any of them:

1. The concrete fairness metric or model for Dispatch's eventual assignment decision (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 8).
2. Whether, and how, a Fulfillment Authority / Scope concept (DIRECT/REFERRED/NETWORK or equivalent) is ever formally ratified (Sections 5–7 above).
3. Personal Client Relationship's creation, confirmation, exclusivity, and expiration mechanics (Product Decision Personal Client Relationship Part 8).
4. Whether a personal-client-directed order is routed through Dispatch's assignment decision at all (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 4; Product Decision Personal Client Relationship Part 5, 9).
5. Any Eligibility/minimum-trust boundary distinct from availability (Section 10 above).
6. Decline, expiration, and post-commitment failure semantics (Section 9 above).
7. The platform's commercial/commission model, including the membership/subscription hypothesis (Section 13 above).
8. Any driver incentive, contribution, or reward model (Constitution Section 26 — already carried there; not reopened differently here).
9. The MVP/pilot boundary itself, including cohort definition and authorization mechanism (Section 14 above).

Resolution of any of these requires a Product Owner decision (PROJECT_CONSTITUTION.md Section 7) recorded as a Product Decision, before any lower-layer document may act on it — consistent with Constitution Section 26's own closing rule, which this document does not override.

## 17. Repository Impact Analysis

No production code, migration, API, event contract, or existing ADR is modified by this document. The matrix below classifies current repository artifacts against the candidate baseline in Sections 3–15.

Categories: **KEEP** / **ADAPT** / **DEFER** / **MISSING** / **CONFLICT-NEEDS DECISION**.

| Artifact | Current Meaning | Baseline Impact | Evidence | Recommended Next Action | Change Required Now |
| --- | --- | --- | --- | --- | --- |
| Order Management (module) | Order lifecycle: submit, complete, cancel | KEEP | `Order.kt`, MODULE_STRUCTURE.md §3 | None | NO |
| Driver Management (module) | Driver standing/availability/participation; availability implemented | KEEP | `Driver.kt`, `DriverAvailabilityApplicationService.kt` | None | NO |
| Dispatch (module) | Assignment decision, exclusively; availability projection (consumer) implemented; selection/policy absent | ADAPT (input boundary only, per ADR-034; no code change implied by this document) | `Assignment.kt`, `DispatchAssignmentApplicationService.kt`, ADR-034 | Await Assignment Policy product authorization before any code change | NO |
| Passenger Experience (module) | Passenger/Corporate Customer representation, originating context only; no PCR data modeled in code | KEEP (scope), MISSING (PCR implementation) | `OrderSubmissionIntent.kt`, `PassengerOrderSubmissionApplicationService.kt` | Await PCR lifecycle Product Decision before modeling | NO |
| Payments / Administration / Analytics / Notifications (modules) | Ratified as bounded contexts; none scaffolded | MISSING (deliberately, per ADR-033/025 gates) | `backend/settings.gradle.kts` (4 modules only) | Leave deferred; no urgency created by this baseline | NO |
| Order (aggregate) | Submitted/Completed/Cancelled; no Order Origin field implemented | KEEP (ratified scope); MISSING (Order Origin implementation) | `Order.kt`, `OrderStatus.kt` | None until Fulfillment Authority/Scope is ratified | NO |
| Assignment (aggregate) | Created/Accepted; driver supplied externally; no decline/expire/policy | KEEP (ratified scope); MISSING (policy, decline, expiry) | `Assignment.kt` | Await ADR-034's Assignment Policy port implementation, itself awaiting product authorization | NO |
| Personal Client Relationship | Ratified conceptual entity only; no aggregate, lifecycle, command, or event; no code representation anywhere | MISSING (by ratified design, not oversight) | DOMAIN_MODEL.md §5; Product Decision PCR Parts 1–3 | Await Product Decision resolving Part 8's open questions before any modeling | NO |
| Driver Availability (value object / projection) | Ratified in Driver Management; separately, real local projection exists in Dispatch (read-only) | KEEP | `Availability.kt`, `DriverAvailabilityRepository.kt` (dispatch) | None | NO |
| OrderSubmitted | Ratified, implemented, published via outbox+RabbitMQ | KEEP | `OrderLifecycleApplicationService.kt`, EVENT_CATALOG.md §5 | None | NO |
| OrderAssigned | Ratified event; domain object exists; **not yet published via outbox/RabbitMQ** — only a contract-verification stub (`OrderAssignedPublisher`) exists | ADAPT (infrastructure catch-up, not a contract change) | `OrderAssignedPublisher.kt` (no outbox/EventPublisher found in `backend/dispatch`) | Future implementation task: Dispatch publishing foundation, mirroring Order/Driver Management's own outbox pattern | NO (documentation-only task) |
| AssignmentAccepted | Ratified event; Order Management's consumer side exists only as a contract-verification stub (`OrderAssignmentRecognitionHandler`), no real RabbitMQ listener | MISSING (implementation) | `OrderAssignmentRecognitionHandler.kt`; no listener/topology file found in `order-management` | Same future task as above, consumer side | NO |
| DriverAvailabilityChanged | Ratified, implemented, published (Driver Management) and consumed (Dispatch), idempotent, retried, dead-lettered | KEEP | `DriverAvailabilityChangedListener.kt`, Dispatch Consumer Foundation v1.0 (commit `b794d8f`) | None | NO |
| Dispatch Consumer Foundation (infrastructure) | Real RabbitMQ consumer, idempotent projection, retry/DLQ | KEEP | Section 1 code inspection above | None | NO |
| ADR-034 Assignment Policy abstraction | Names the missing seam; no interface exists yet in code | KEEP (as an ADR); MISSING (as code) | ADR-034 Part 3 | Await product authorization of a specific policy (ADR-034 Evolution Path, Item 1) before implementing the port | NO |
| Current Assignment creation behavior | `Assignment.create(order, driver, existingAssignments)` — driver fully externally supplied, no selection logic anywhere | KEEP (matches ADR-002's exclusion of algorithm from every level) | `Assignment.kt`, `AssignOrderCommand.kt` | None until Assignment Policy is authorized | NO |
| DriverAvailabilityRepository query shape | `findByDriverReference` only — no "list all available drivers" query | MISSING (already flagged by ADR-034 Part 6, not newly discovered) | `DriverAvailabilityRepository.kt` | Extend only once an Assignment Policy needs it (ADR-034 Evolution Path, Item 2) | NO |
| Tests (all modules) | Constructor-based, no Spring context; RabbitMQ/PostgreSQL integration tests exist for implemented paths only | KEEP | Test suite structure across all four modules | None | NO |

## 18. KEEP / ADAPT / DEFER / MISSING Summary

**KEEP (verified, not assumed):**
- Modular monolith / module boundaries as scaffolded (4 of 8 ratified modules).
- Order Management, Driver Management, Dispatch, Passenger Experience — all four match their ratified scope exactly.
- PostgreSQL, RabbitMQ, transactional outbox, versioned event envelopes, producer-owned topology, consumer idempotency, retry/DLQ — all verified present and correctly separated from business logic (Section 9 above).
- The ratified CREATED/ACCEPTED distinction in Assignment — the strongest existing anchor for any future Opportunity/Commitment model.

**ADAPT (real gaps between ratified contract and current implementation, not contract changes):**
- Dispatch's own publication of OrderAssigned/AssignmentAccepted — contract exists, implementation does not yet.
- Order Management's consumption of AssignmentAccepted — contract exists, implementation does not yet.
- Assignment's future accommodation of a Policy port (ADR-034) — architecturally named, not yet built, correctly gated on product authorization.

**MISSING (genuinely absent from every layer, not merely undecided):**
- Fulfillment Authority / Scope model (DIRECT/REFERRED/NETWORK or equivalent).
- Explicit Network Authorization semantics.
- Opportunity lifecycle (Accept/Decline/Expire) as named concepts — substance partially covered by Assignment's CREATED/ACCEPTED split, but decline/expire have zero representation.
- Eligibility boundary distinct from availability.
- Business-level fulfillment-failure/recovery model, distinct from technical retry.
- Any pilot-level Fair Opportunity Policy.
- Personal Client Relationship as an actual aggregate, lifecycle, or code representation (deliberately, per ratified documents — not an oversight).

**DEFER (per Section 15 — repository inspection confirms these remain exactly as deferred, nothing promotes any of them):**
- Reciprocity, ratings/ranking, payment infrastructure, complex trust scoring, advanced marketplace mechanics, AI/geospatial dispatch.

## 19. Required Decision Sequence Before Implementation

Proposed smallest ordered sequence, none of it executed here:

1. **Ratify or reject this Product Baseline v2.0's findings** — a Product Owner act, since this document itself is not a Product Decision and cannot self-ratify.
2. **Product Decision: Fulfillment Authority and Scope** — resolves Sections 5–7's open DIRECT/REFERRED/NETWORK question; this precedes Opportunity/Commitment semantics because those concepts are only meaningful once a scope model exists to attach them to.
3. **Product Decision: Opportunity / Acceptance / Commitment semantics** — extends the already-ratified CREATED/ACCEPTED distinction (Section 8) with Decline/Expire and revalidation-at-acceptance rules.
4. **Product Decision: Eligibility and pilot admission boundary** — resolves Section 10, needed before any real driver pool concept can exist for a policy to select from.
5. **Product Decision: Pilot Fair Opportunity Policy** — the first concrete, evidenced Assignment Policy criterion (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md's own Recommended Next Step already named this as the correct next product step, independent of this task).
6. **ADR migration/update strategy** — once 2–5 exist, determine whether ADR-034 needs a superseding ADR or a new ADR building on it (ADR-015 governs).
7. **Contract/event migration design** — extend EVENT_CATALOG.md/INTERFACE_CONTRACTS.md only after 2–5 provide the vocabulary to extend them with.
8. **Implementation plan** — including closing the two already-identified infrastructure gaps (Dispatch's own publishing, Order Management's AssignmentAccepted consumption) and ADR-034's flagged repository-query gap.
9. **Controlled real-world pilot** — tests H1/H2 (Section 14); not before 2–8 exist, since a pilot without a ratified scope/eligibility/policy model would be validating an undocumented mechanism.
10. **Monetization validation after demonstrated utility** — Section 13's economic model remains open until pilot evidence exists to evaluate it against.

This order follows repository evidence directly: Sections 5–10 show that Fulfillment Authority and Opportunity/Commitment semantics are prerequisite vocabulary for Eligibility and Fair Opportunity Policy, which are themselves prerequisite to any implementation or pilot — the same dependency structure PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 8 and ADR-034's own Evolution Path already independently established for the narrower Assignment Policy question.

---

Where this document finds no evidence, it states so explicitly (OPEN or HYPOTHESIS) rather than filling the gap. Resolution of any Section 16 or Section 19 item requires a Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including this one — may be acted on as though it were already ratified.
