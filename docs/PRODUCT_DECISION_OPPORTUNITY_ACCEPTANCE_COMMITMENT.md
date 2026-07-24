# PIOS Product Decision: Opportunity, Acceptance & Commitment Semantics v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document defines the semantic boundary between a candidate opportunity, a driver's acceptance, and actual fulfillment commitment. It is not an ADR, not an architecture document, and designs no Opportunity aggregate, Eligibility boundary, Fair Opportunity Policy, ranking, pricing, payment, or reciprocity mechanism. It resolves the specific product question [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md) Section 16 Item 3 left **[OPEN]**, building directly on [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md), which resolved the authority gate that precedes everything discussed here.

Derived from PROJECT_CONSTITUTION.md, PRODUCT_BASELINE_V2.md, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md, PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, ADR-002, ADR-034, and the actual committed implementation of `Assignment`, `AssignOrderCommand`, `AcceptAssignmentCommand`, `DispatchAssignmentApplicationService` (`backend/dispatch`) and `OrderAssignmentRecognitionHandler` (`backend/order-management`). It introduces no new aggregate, event, command, or actor; where it names a new concept (Opportunity, Commitment as a distinct term), it does so as a product-level semantic clarification only, never as domain, architecture, or code.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, or directly observable in committed code, cited inline.
- **[DERIVED]** — a decision this document actually makes, reasoning from already-ratified principles and already-implemented behavior to a specific semantic question those sources do not themselves literally answer. This is the primary tag this document's own resolutions carry.
- **[HYPOTHESIS]** — a candidate requiring further validation; recorded, not adopted.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is promoted to a requirement anywhere in this document.

## Central Finding, Stated First

Inspecting `Assignment.kt` directly: `Assignment.create(order, driver, existingAssignments)` takes an **already-chosen** `driver` reference, persists a new `Assignment` in `CREATED` status, and returns the `OrderAssigned` event **immediately** — before any driver confirmation occurs. `AssignOrderCommand`'s own KDoc states the driver reference is "supplied by the caller." Only afterward does a separate `accept()` call transition `CREATED → ACCEPTED` and fire `AssignmentAccepted`. **[RATIFIED — current code behavior]**

This means today's `Assignment` aggregate already implements a two-step **proposal → confirmation** lifecycle for **one, already-selected driver** — but it does **not**, and cannot yet, represent a multi-candidate Opportunity phase (many eligible drivers exposed to a request, one of whom accepts), because the "which driver" decision has no architectural presence anywhere in this codebase (ADR-034's own Context section: "the assignment-selection seam has no architectural presence at all today"). Every resolution below follows from, and is checked against, this one finding.

## 1. What is an Opportunity?

**[DERIVED].** Opportunity is the product-level name for a driver's exposure to a specific fulfillment request — DIRECT or NETWORK scope, per PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3 — before that driver has confirmed anything. It is not a ratified domain concept, aggregate, or event; no invariant or lifecycle is created for it here, consistent with this task's explicit exclusion of Opportunity design.

Opportunity is not identical to `Assignment`'s `CREATED` state as currently implemented, for a precise reason: `CREATED` today already names one specific driver and already fires a business event (`OrderAssigned`) recording that naming — it is the record of a decision already made about *which* driver, not a broadcast or offer to a *candidate set* of drivers. A genuine Opportunity concept, if it is ever designed, would need to exist **before** that naming occurs — logically prior to, not coincident with, `Assignment.create()`.

## 2. How is Opportunity Different from Assignment?

**[DERIVED].** `Assignment` is already ratified: "the outcome of Dispatch allocating a specific order to a specific driver" (DOMAIN_MODEL.md Section 4). By its own ratified definition, an Assignment already names one specific driver — it is the record of an allocation decision, not the decision process itself. Opportunity, as named in Section 1, would sit **before** that allocation decision: it is what a driver is shown or offered, not what Dispatch has already decided.

Concretely: today's code conflates these because no candidate-exposure step exists — the caller of `AssignOrderCommand` has already picked the one driver `Assignment.create()` will name, so there is nothing between "no assignment exists" and "an Assignment naming exactly one driver exists." Opportunity, if designed, would occupy that missing space; it is not a synonym for `Assignment`'s `CREATED` status, even though `CREATED` currently does the closest available job of representing "not yet confirmed by the driver."

## 3. When Does a Driver Become Obligated?

**[DERIVED].** Only upon Acceptance — never upon exposure to an Opportunity, never upon Assignment creation (`CREATED`), and never upon mere Availability. Grounded in: DOMAIN_MODEL.md Section 12's own Assignment lifecycle, "made by Dispatch in connection with an order, then accepted" — acceptance is stated as a distinct, necessary second step, never folded into creation; and PRODUCT_FOUNDATION.md Section 12, driver participation being self-determined, which is incompatible with any obligation attaching without the driver's own confirming act.

**A naming caution, not a document conflict, is flagged here explicitly:** `OrderAssigned`'s ratified meaning is "Dispatch has connected an order to a driver" (EVENT_CATALOG.md Section 6), and it fires at `CREATED`, before Acceptance. The word "connected" could be misread as implying the driver is already bound. It does not: EVENT_CATALOG.md Section 6 states only that a connection has been recorded, and DOMAIN_MODEL.md Section 12's own two-step lifecycle already makes clear that this recorded connection still awaits acceptance before it "stands." This document does not change `OrderAssigned`'s ratified meaning; it clarifies, for product purposes, that no obligation should ever be inferred from that event's firing alone.

## 4. What Does Acceptance Mean?

**[RATIFIED].** DOMAIN_MODEL.md Section 6, verbatim: Acceptance is "a driver's or passenger's confirmation that a proposed assignment or order will proceed." Already directly stated; this document adds nothing to its meaning.

## 5. Does Acceptance Immediately Create Commitment?

**[DERIVED].** Yes — Acceptance and the creation of Commitment are the same act, not two sequential steps. Grounded in DOMAIN_MODEL.md Section 12: once accepted, an assignment "stands until its order is completed or cancelled" — no further step is described or required for the assignment to take effect. This matches the current implementation directly: `Assignment.accept()` transitions `CREATED → ACCEPTED` and returns `AssignmentAccepted` in one atomic domain operation, with no intermediate state. **[RATIFIED — current code behavior confirms the same atomicity]**

## 6. Who Owns the Opportunity Lifecycle?

**[DERIVED].** For NETWORK scope, Opportunity — if ever designed — would need to be owned by Dispatch exclusively, since exposing or withdrawing an opportunity is inseparable from the assignment decision itself, which ADR-002 already gives Dispatch alone. No other module has any ratified access to the assignment decision (INTERFACE_CONTRACTS.md Section 4). This document assigns conceptual ownership only; it creates no aggregate, service, or lifecycle for Dispatch to actually own.

For DIRECT scope, the analogous question — whether a named driver can personally fulfill a personal-client-directed request — is not Dispatch's concern at all: PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md's own resolution already establishes that Dispatch has no ratified role in DIRECT-scope authorization. That question sits, if it is ever modeled, between Driver Management (the driver's own declaration) and Passenger Experience/the relationship itself — not Dispatch. This document does not resolve which of those two owns it, since Personal Client Relationship's own lifecycle remains unratified (Product Decision Personal Client Relationship Part 3).

## 7. Who Owns the Commitment Lifecycle?

**[RATIFIED].** Dispatch, exclusively. `Assignment` is already Dispatch-owned (DOMAIN_MODEL.md Section 4: "within Dispatch's exclusive ownership"; ADR-005, ADR-019), and Commitment, per Section 5 above, is created by Acceptance — already part of Assignment's own ratified lifecycle. No new ownership decision is needed; Commitment lifecycle ownership is identical to Assignment's own already-ratified ownership.

## 8. Relationship Between Opportunity, Assignment, and Order

**[DERIVED].** Order exists and is tracked independently by Order Management, unaffected by anything below **[RATIFIED, unchanged]**. Once Fulfillment Authority (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md) permits DIRECT or NETWORK scope for a given order, an Opportunity — if designed — would represent the request's exposure to one or more drivers. Assignment, per its own ratified definition, is created only once a specific driver is connected to the order.

The precise relationship this document establishes: **Assignment creation should conceptually follow Opportunity resolution (a specific driver's own acceptance of exposure), not precede or substitute for it.** Today's code inverts this for the single case it actually implements: the driver is already chosen before `Assignment.create()` is even called, meaning there is no Opportunity step logically prior to Assignment at all today — Assignment's `CREATED` state does the work of "proposed" only for a driver already, externally, singularly selected. This is not a defect in what has been built (nothing here contradicts ADR-002's exclusion of the selection algorithm, or ADR-034's own naming of this exact gap as the still-missing Assignment Policy seam) — it is a precise description of what currently exists, so that any future Opportunity design knows exactly what it must insert, and where.

## 9. Meaning of Decline, Expiration, Cancellation, and Failure

Definitions only — no mechanism, state, event, or field is designed for any of these, consistent with this task's exclusions:

- **Cancellation.** **[RATIFIED]** — DOMAIN_MODEL.md Section 6, verbatim: "the indication that an order or assignment has been terminated before completion." An actor-driven, deliberate termination; already implemented for Order (`Order.cancel()`) and explicitly out of scope for Assignment in the current implementation (`Assignment.kt`'s own KDoc: "Cancellation of an assignment... is a separate capability and is explicitly out of this task's scope").
- **Decline.** **[OPEN]** — no ratified concept; not implemented (`Assignment.kt` has no `decline()` method). Defined here only descriptively, for classification: a driver's active refusal of an Opportunity or an unaccepted (`CREATED`) Assignment, before Acceptance. Because Section 3 already establishes no obligation exists before Acceptance, a decline cannot be a violation of anything — there is nothing yet to violate.
- **Expiration.** **[OPEN]** — no ratified concept; not implemented (no timeout exists anywhere in `Assignment` or `Order`). Defined here only descriptively: a time-driven, system-recognized lapse of an unconfirmed Opportunity or unaccepted Assignment, distinct from Decline (passive non-response vs. active refusal) and from Cancellation (system/time-driven vs. actor-driven).
- **Failure (post-commitment).** **[OPEN]** — no ratified concept; `Assignment` has no state beyond `ACCEPTED`. Defined here only descriptively: an accepted commitment that does not result in completion for a reason other than the requester's own cancellation (for example, the driver becomes unable to fulfill after already accepting). This is a genuine, presently unaddressed gap — no document or code represents this outcome in any form today.

These four are confirmed as **conceptually distinct** — none is a synonym for another — but only Cancellation is ratified and implemented; Decline, Expiration, and Failure remain open, undesigned concepts this document does not build a mechanism for.

## 10. Does the Current Assignment Model Already Represent Commitment?

**[DERIVED] — Partially, and precisely as follows.**

- Assignment's `ACCEPTED` state **does** already represent Commitment, for the one driver an Assignment already names. This part is genuinely ratified and implemented (Section 5 above): acceptance and commitment are the same act, already built.
- Assignment's `CREATED` state does **not** represent a true, multi-candidate Opportunity. No candidate pool concept exists in code or in any ratified document; the "which driver" decision has already occurred, by an unspecified external means, before `Assignment.create()` is ever invoked (ADR-034 Context: the driver is "supplied by the caller"). `OrderAssigned` firing at `CREATED` — before the driver's own confirmation — reinforces that `CREATED` records an already-made selection, not an open offer to a set of eligible drivers.

**Conclusion:** the current Assignment model already correctly represents Commitment once accepted, attached to a proposal already directed at one, externally pre-selected driver. It is not, and cannot yet be, a representation of a competitive or candidate-based Opportunity phase, because that phase — the actual decision of *which* driver(s) are offered a given request — has no architectural presence anywhere in this codebase today, exactly as ADR-034 already found and did not resolve.

## Explicit Preservations

- **Selection ≠ Commitment.** **[RATIFIED]** — already true of the implemented model: `Assignment.create()` (selection already made) and `Assignment.accept()` (commitment) are distinct operations with distinct events (PRODUCT_BASELINE_V2.md Section 8).
- **Availability ≠ Obligation.** **[RATIFIED]** — Availability is "a driver's own declaration of readiness to receive an assignment" (DOMAIN_MODEL.md Section 6); nothing in its ratified definition, or in `DriverAvailabilityRepository`'s implementation, attaches any obligation to a driver being available.
- **No response ≠ Violation.** **[DERIVED]** — direct consequence of Section 3: no obligation exists before Acceptance, so a driver's silence or non-response to a `CREATED` Assignment cannot be a violation of anything.
- **No hidden reputation impact.** **[DERIVED]** — no rating, ranking, or trust-score concept exists in DOMAIN_MODEL.md at all; ADR-034 Part 2 explicitly classifies "Driver profile, standing, participation, or ranking beyond availability" as a **forbidden input** to the assignment decision today. A mechanism penalizing decline or non-response would require inventing a wholly new domain concept no document ratifies; this document forecloses that being introduced silently.
- **No automatic relationship transfer.** **[RATIFIED]** — unchanged from PRODUCT_BASELINE_V2.md Section 12 and DOMAIN_MODEL.md Section 13: nothing in Opportunity, Acceptance, or Commitment semantics creates or transfers a Personal Client Relationship.

## Explicitly Out of Scope

Per this task's own instruction, none of the following is designed here:

- Eligibility (which drivers may receive an Opportunity) — remains PRODUCT_BASELINE_V2.md Section 10's own open question.
- Fair Opportunity Policy — remains PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 8's own open question.
- Ranking or scoring of any kind — remains forbidden as an input per ADR-034 Part 2, unchanged.
- Pricing or Payments — remain Payments' own, entirely separate, self-contained scope (INTERFACE_CONTRACTS.md Section 3); untouched.
- Reciprocity — remains excluded from the Fundamental Laws (PROJECT_CONSTITUTION.md Section 18) and an unapproved candidate only (ADR-034 Part 4); untouched.

## Files Changed

`docs/PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` (new) is the only content file this decision creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set for the two preceding documents. No ADR, code, database, event, or API is created or modified.

## Unresolved Decisions

1. Whether an Opportunity concept is ever formally designed as a domain aggregate/event, and if so, its exact shape — deliberately deferred (Sections 1, 2, Explicitly Out of Scope). **Status update:** Product Decision: Opportunity Before Assignment v1.0 and ADR-035 have since ratified that this fact exists as a separate Aggregate Root within Dispatch; its exact shape (name, identity, states, invariants, commands, events, storage) remains exactly as open as stated above.
2. Mechanisms for Decline, Expiration, and post-commitment Failure — meanings are defined here (Section 9); mechanisms are not.
3. Eligibility and Fair Opportunity Policy — unchanged, remain open per their own source documents.
4. Who owns the DIRECT-scope analogue of Opportunity (Driver Management vs. Passenger Experience vs. jointly) — depends on Personal Client Relationship's own still-unratified lifecycle (Product Decision Personal Client Relationship Part 3).
5. Whether `OrderAssigned`'s firing point (at `CREATED`, before Acceptance) should ever be revisited at the architecture level to reduce the naming-ambiguity risk flagged in Section 3 — an ADR-level question, not decided here.

## Conflicts

**None found between ratified documents.** One naming ambiguity is flagged, not a contradiction: `OrderAssigned`'s ratified wording ("connected") could be misread as implying obligation despite firing before Acceptance; this document clarifies the correct reading (Section 3) without altering EVENT_CATALOG.md's own text. Checked against and consistent with: ADR-002 (Dispatch's exclusive assignment-decision ownership, unchanged); ADR-034 (the missing Assignment Policy seam and forbidden-inputs list, both reused directly, not reopened); PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md (this document operates entirely downstream of its authority-gate resolution, never redeciding who may authorize scope); PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md (Part 8's open questions remain exactly as open).

## Impact Analysis

**Documents:** No existing document modified beyond the one minimal `docs/README.md` line. EVENT_CATALOG.md, DOMAIN_MODEL.md, INTERFACE_CONTRACTS.md, and every ADR remain exactly as they were.

**Code:** None. `Assignment.kt`, `AssignmentStatus.kt`, `AssignOrderCommand.kt`, `AcceptAssignmentCommand.kt`, `DispatchAssignmentApplicationService.kt`, `OrderAssigned.kt`, `AssignmentAccepted.kt`, and `OrderAssignmentRecognitionHandler.kt` are all read, not touched.

**Future work enabled, not performed:** A future ADR could define the architectural shape of an Opportunity concept and where it inserts relative to today's `Assignment.create()` (Section 8's own finding gives that future ADR a precise starting point). A future Product Decision could resolve Decline/Expiration/Failure mechanisms and Eligibility. Neither is undertaken here.

## Recommended Next Step

Not Eligibility or Fair Opportunity Policy design (explicitly excluded), and not an ADR. The most evidence-grounded next step is a further Product Owner decision addressing Decline, Expiration, and post-commitment Failure mechanisms concretely enough that a future ADR could give Opportunity an architectural shape — since Section 10's central finding shows the current Assignment model has no place today for a driver's refusal, non-response, or post-acceptance failure to go, and that gap is more foundational than Eligibility or Fair Opportunity Policy, both of which presuppose it.

## Traceability

| Section | Source Document |
| --- | --- |
| Central Finding | `Assignment.kt`, `AssignOrderCommand.kt` (commit `b794d8f` and prior); ADR-034 Context |
| 1–2. Opportunity vs. Assignment | DOMAIN_MODEL.md Section 4; ADR-034 Context, Part 3 |
| 3. Obligation | DOMAIN_MODEL.md Section 12; PRODUCT_FOUNDATION.md Section 12; EVENT_CATALOG.md Section 6 |
| 4–5. Acceptance and Commitment | DOMAIN_MODEL.md Section 6, 12; `Assignment.kt` |
| 6–7. Ownership | DOMAIN_MODEL.md Section 4; ADR-002, ADR-005, ADR-019; INTERFACE_CONTRACTS.md Section 4; PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md |
| 8. Opportunity/Assignment/Order relationship | DOMAIN_MODEL.md Section 4; ADR-002, ADR-034 |
| 9. Decline/Expiration/Cancellation/Failure | DOMAIN_MODEL.md Section 6; `Assignment.kt` |
| 10. Central Conclusion | ADR-034 Context; PRODUCT_BASELINE_V2.md Sections 7–8 |
| Explicit Preservations | PRODUCT_BASELINE_V2.md Sections 8, 12, 15; DOMAIN_MODEL.md Sections 6, 13; ADR-034 Part 2 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Decision (above) requires a further Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
