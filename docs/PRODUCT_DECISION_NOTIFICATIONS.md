# PIOS Product Decision: Notifications v1.0

Status: Decided — Product/Domain Analysis. This document is a Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7: "The Product Owner is responsible for defining product intent... approving product-level decisions") determining whether PIOS's MVP has a concrete business need for the Notifications capability, who would receive a notification, about what business fact, and which already-ratified domain event would legitimately trigger it. It is not an architecture decision, not an ADR, and not an implementation plan; it authorizes nothing that architecture-level documents (EVENT_CATALOG.md, INTERFACE_CONTRACTS.md) do not already independently ratify.

This document is derived exclusively from PRODUCT_FOUNDATION.md, USE_CASE_CATALOG.md, DOMAIN_MODEL.md, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, MODULE_STRUCTURE.md, API_SPECIFICATION.md, ADR-017, ADR-018, ADR-026, and ADR-028 through ADR-033. It introduces no actor, aggregate, or event not already established by these documents.

---

## 1. Purpose

[ADR-033: Notifications Module and Event Consumption Boundary](ADR/ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) confirmed that Notifications is architecturally entitled to exist as a module but found no ratified product evidence for any specific event-consumption relationship, and recommended a dedicated product-level decision before any further architecture or implementation step. This document is that decision: it evaluates, one at a time and without presuming the answer, every already-ratified domain event as a candidate Notifications trigger, and states plainly whether PIOS's MVP currently has a business need for Notifications at all.

## 2. Method

For each candidate, four questions are asked, each answerable only from already-approved documentation:

1. **Actor / recipient** — which already-ratified actor (PRODUCT_FOUNDATION.md Sections 6–7; USE_CASE_CATALOG.md Section 2) would plausibly receive this notification?
2. **Business purpose** — what use case (USE_CASE_CATALOG.md) or product principle (PRODUCT_FOUNDATION.md) attributes that actor a goal this notification would serve?
3. **Triggering business fact** — which already-ratified domain event (EVENT_CATALOG.md Section 4) represents that fact, if any?
4. **Delivery requirement** — is delivery immediate or eventual, and does failure to deliver affect the underlying business transaction?

Each candidate is then classified: **required for MVP**, **useful but post-MVP**, or **unsupported by current product requirements**. Consistent with the instruction governing this task, a candidate is classified as "useful but post-MVP" only where an actual, documented business goal exists but is not urgent — never as a placeholder for an intuitively plausible notification no approved document actually attributes to anyone.

## 3. Threshold Finding

Before evaluating individual events, one piece of existing evidence resolves most of this analysis by itself: **USE_CASE_CATALOG.md's own "Areas Not Catalogued" section already considered, and explicitly declined to catalog, a notification workflow.**

> "Login, registration, password reset, payment workflow, map workflow, notification workflow. None of these is supported by DOMAIN_MODEL.md or EVENT_CATALOG.md as a discrete business goal; none is catalogued here." — USE_CASE_CATALOG.md, Areas Not Catalogued

This is not silence by omission; it is an explicit, already-ratified statement that no discrete business goal currently requires a notification workflow. PRODUCT_FOUNDATION.md Section 8 mentions "notification" exactly once, categorically, as one of several "supporting capabilities... needed to operate the platform responsibly" alongside administration and analytics — this establishes that Notifications exists as a *capability area* (consistent with ADR-018's ratification of it as one of eight bounded contexts) but attributes no actor, no trigger, and no concrete outcome to it. No use case in USE_CASE_CATALOG.md's ten catalogued use cases (UC-001 through UC-010) lists a notification as a goal, a main success outcome, or an alternative outcome for any actor.

## 4. Candidate Analysis

### OrderSubmitted

- **Actor / recipient.** Candidate: Passenger (UC-001) or Corporate Customer (UC-009), as the order's originator.
- **Business purpose.** None documented. UC-001's and UC-009's Main Success Outcome is the order's own lifecycle progressing (submitted → connected → completed); the passenger's or corporate customer's visibility into that progress is attributed to their ongoing interaction with Passenger Experience (PRODUCT_FOUNDATION.md Section 9; UC-002), not to a distinct notification.
- **Triggering business fact.** OrderSubmitted (ratified, Order Management-owned; EVENT_CATALOG.md Section 5) would be the legitimate trigger if a business need existed.
- **Delivery requirement.** Not applicable — no requirement exists to specify one for.
- **MVP classification.** Unsupported by current product requirements.

### OrderAssigned

- **Actor / recipient.** Candidate: Driver (UC-005, "Receive Assignment" — the driver's own stated goal is "be connected to a submitted order").
- **Business purpose.** A genuine, already-ratified consumption relationship exists here — but it is **not** a Notifications relationship. API_SPECIFICATION.md Section 8 states explicitly: *"OrderAssigned is likewise exposed to the Driver consumer it concerns (UC-005)"* — this is Dispatch's own direct event exposure across its API boundary to the Driver actor (ADR-004; INTERFACE_CONTRACTS.md Section 9: "Driver... receives OrderAssigned, per API_SPECIFICATION.md Section 8"), governed by Dispatch's own ownership of the event, not by the Notifications domain's "informing participants" capability. Folding this into a Notifications-routed consumption relationship as well would require its own separate justification (for example, an out-of-band channel distinct from the existing API exposure) that no approved document currently establishes.
- **Triggering business fact.** OrderAssigned (Dispatch-owned) — already consumed directly by the Driver actor via Dispatch's own boundary; not available to be independently "discovered" as a fresh Notifications need without duplicating an existing mechanism.
- **Delivery requirement.** Governed by the existing Dispatch→Driver API exposure, not by this decision.
- **MVP classification.** Unsupported as a *Notifications* use case specifically — the underlying business need is already served by an already-ratified, different mechanism.

### AssignmentAccepted

- **Actor / recipient.** Candidate: Passenger (as the party awaiting confirmation an assignment will proceed) or Order Management (as a system-internal consumer).
- **Business purpose.** EVENT_CATALOG.md Section 9 already states the sole ratified consumption relationship for this event: *"AssignmentAccepted is relied upon by Order Management, which needs to know an assignment has been confirmed in order to track the order's status"* — a system-internal, domain-to-domain dependency, not a human-facing notification. No use case attributes a passenger-facing goal to learning of AssignmentAccepted independent of the order's own status, which Passenger Experience already represents on an ongoing basis (UC-002).
- **Triggering business fact.** AssignmentAccepted (Dispatch-owned).
- **Delivery requirement.** Not applicable — no requirement exists to specify one for.
- **MVP classification.** Unsupported by current product requirements.

### DriverAvailabilityChanged

Note: the task's candidate list includes "DriverBecameAvailable" and "DriverBecameUnavailable" as two separate events. EVENT_CATALOG.md ratifies only **one** event covering both transitions — DriverAvailabilityChanged (Section 7) — no such split event exists; this analysis evaluates the event that is actually ratified, rather than inventing the two named in the request.

- **Actor / recipient.** Candidate: the declaring Driver themselves, or a Fleet/Operator.
- **Business purpose.** EVENT_CATALOG.md Section 9's sole ratified consumption relationship is system-internal: *"DriverAvailabilityChanged is relied upon by Dispatch, which needs to know which drivers are currently available in order to make an assignment."* No use case attributes any actor a goal of being *notified* of a driver's own availability change — a driver already knows their own declaration (UC-004) without being told about it, and Fleet is explicitly unratified as a domain (DOMAIN_MODEL.md Section 5, "Domain Decision Required"), so no Fleet-facing notification can be authorized without first resolving that separate, standing gap.
- **Triggering business fact.** DriverAvailabilityChanged (Driver Management-owned).
- **Delivery requirement.** Not applicable — no requirement exists to specify one for.
- **MVP classification.** Unsupported by current product requirements.

### OrderCompleted / OrderCancelled

- **Actor / recipient.** Candidate: Passenger or Corporate Customer, symmetric to OrderSubmitted.
- **Business purpose.** Same finding as OrderSubmitted: UC-001/UC-009 attribute the order's lifecycle outcome to the order itself and the originator's ongoing platform interaction (UC-002), not to a distinct notification act. No use case names a notification as part of either event's outcome.
- **Triggering business fact.** OrderCompleted / OrderCancelled (Order Management-owned).
- **Delivery requirement.** Not applicable.
- **MVP classification.** Unsupported by current product requirements.

## 5. Summary Table

| Candidate Event | Candidate Recipient | Ratified Business Goal Found? | Existing Consumption Mechanism | MVP Classification |
| --- | --- | --- | --- | --- |
| OrderSubmitted | Passenger / Corporate Customer | No | None | Unsupported |
| OrderAssigned | Driver | Yes, but already served | Direct Dispatch→Driver API exposure (API_SPECIFICATION.md Section 8) | Unsupported as a Notifications use case |
| AssignmentAccepted | Passenger | No | System-internal (Order Management) only | Unsupported |
| OrderCancelled | Passenger / Corporate Customer | No | None | Unsupported |
| OrderCompleted | Passenger / Corporate Customer | No | None | Unsupported |
| DriverAvailabilityChanged | Driver / Fleet | No | System-internal (Dispatch) only | Unsupported |

**No candidate is classified "required for MVP" or "useful but post-MVP."** Reaching either of those classifications for any candidate would require attributing a business goal no approved document currently attributes, which this decision declines to do.

## 6. Decision

**PIOS's MVP has no currently ratified, concrete business need for the Notifications capability.** Every catalogued domain event either has no documented human-facing consumption need at all, or its only documented consumption relationship is already served by a different, already-ratified mechanism (Dispatch's direct API exposure of OrderAssigned to the Driver, or a system-internal cross-domain dependency). This is consistent with, and reinforces rather than contradicts, USE_CASE_CATALOG.md's own prior, explicit exclusion of "notification workflow" as unsupported by any discrete business goal.

**No event-consumption relationship is authorized by this decision.** In particular, Notifications is **not** authorized to consume OrderSubmitted, OrderAssigned, AssignmentAccepted, DriverAvailabilityChanged, OrderCompleted, or OrderCancelled.

**Recommendation: defer Notifications implementation entirely**, including module scaffolding, database technology evaluation, and any RabbitMQ consumer work, until a Product Owner decision creates the missing evidence this analysis found absent.

**General constraint for any future authorized notification (recorded now, decided later):** consistent with Notifications' already-ratified boundary as derived, calculated information (DOMAIN_MODEL.md Section 5; PERSISTENCE_ARCHITECTURE.md Section 4) that never owns the fact triggering it, any future notification's delivery must never be allowed to affect the underlying business transaction's own outcome — the same asynchronous, decoupled-from-the-transaction posture ADR-031's topology and ADR-032's outbox pattern already establish for every other event consumer. This is a constraint carried forward for whenever a real use case is authorized, not a new decision made here.

## 7. Unresolved Product Questions

1. **Is there a real participant-facing notification need PIOS's MVP requires, not yet captured by any use case?** For example: should a passenger be told when their order is assigned or their driver arrives? Should a driver be told about anything beyond what Dispatch's own direct API exposure already provides? This document does not answer that — it only confirms no approved document currently does either. Answering it is a Product Owner decision, not an architectural or engineering one.
2. **Fleet's standing as an unratified domain** (DOMAIN_MODEL.md Section 5, "Domain Decision Required") blocks any Fleet-facing notification candidate independent of the Notifications question itself.
3. **Should OrderAssigned ever be additionally routed through Notifications** (for example, a second, out-of-band delivery channel alongside Dispatch's existing direct API exposure)? Not evaluated here, since no product requirement currently justifies duplicating an already-served need.

## 8. Files Changed

None of USE_CASE_CATALOG.md, PRODUCT_FOUNDATION.md, EVENT_CATALOG.md, or INTERFACE_CONTRACTS.md is modified by this decision, consistent with its own instruction: no concrete notification need was found, so no artificial requirement, use case, or event-consumption authorization is created. This document is the only file this decision produces.

## 9. Recommended Next Task

Defer Notifications entirely. If and when the Product Owner identifies a genuine, specific participant-facing notification need (a named recipient, a named business purpose, and a named triggering event not already served by an existing mechanism), the next task should be a follow-up product decision documenting that specific need — at which point USE_CASE_CATALOG.md would gain a new use case, and EVENT_CATALOG.md Section 9 / INTERFACE_CONTRACTS.md Sections 5 and 7 would be extended to name the specific authorized contract, consistent with ADR-033's already-established scaffolding requirements. Until then, no ADR, module scaffolding, database technology evaluation, or RabbitMQ consumer task for Notifications should be undertaken.

## 10. Traceability

| Section | Source Document | Related ADR |
| --- | --- | --- |
| 3. Threshold Finding | USE_CASE_CATALOG.md, Areas Not Catalogued; PRODUCT_FOUNDATION.md Section 8 | ADR-018 |
| 4. OrderSubmitted / OrderCompleted / OrderCancelled | USE_CASE_CATALOG.md UC-001, UC-002, UC-009; EVENT_CATALOG.md Section 5 | ADR-005, ADR-009 |
| 4. OrderAssigned | USE_CASE_CATALOG.md UC-005; API_SPECIFICATION.md Section 8; INTERFACE_CONTRACTS.md Section 9 | ADR-002, ADR-004 |
| 4. AssignmentAccepted | EVENT_CATALOG.md Section 9; USE_CASE_CATALOG.md UC-006 | ADR-002 |
| 4. DriverAvailabilityChanged | EVENT_CATALOG.md Section 9; USE_CASE_CATALOG.md UC-004; DOMAIN_MODEL.md Section 5 (Fleet) | ADR-005, ADR-009 |
| 6. Decision | ADR-033 | ADR-017, ADR-018, ADR-026, ADR-028–ADR-033 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Product Question (Section 7) requires a Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document may act on it.

---

## 11. Amendment (2026-09-19) — D-10 Driver Web Push

**Nothing above this section is edited, deleted, or rewritten.** This is an append-only amendment, ratified by the Product Owner, resolving Section 7 Q1 narrowly — for exactly one recipient and exactly two facts — and nothing beyond that.

**Section 7 Q1 is answered: yes, narrowly.**

- **Named recipient.** The driver named on the `Proposal` (`Proposal.driver`) — driver only. No passenger-facing resolution is made here; Section 7 Q1 remains otherwise unresolved for any other recipient.
- **Named business purpose.** Reach that driver when the application is **closed**, for exactly the two facts the in-app surface (`ADR-071` Part 3) already shows when the application is open: D1 (a new order is waiting for a decision) and D2 (the passenger confirmed the driver's price). This is the gap `ADR-071` Part 5 Gap 3 names and explicitly refuses to fake.
- **Named triggering facts.**
  1. **N1** — a `Proposal` is created for that driver in status `OPEN`.
  2. **N2** — that driver's `Proposal` transitions `PRICE_PROPOSED → ACCEPTED`.

**Scope, stated as hard limits, not examples:**

- Driver-facing only. **No passenger-side push notification of any kind is authorized by this amendment (N3 — NO-GO).**
- Limited to N1 and N2. No other fact — D3 through D7, or any passenger fact P1 through P7 — is authorized for push delivery by this amendment.
- **No event-consumption architecture is authorized.** Section 6's finding — *"No event-consumption relationship is authorized by this decision. In particular, Notifications is not authorized to consume OrderSubmitted, OrderAssigned, AssignmentAccepted, DriverAvailabilityChanged, OrderCompleted, or OrderCancelled"* — is **not amended** and remains fully in force. N1 and N2 are delivered from an in-process method call inside the module that already owns both aggregates, not from any event consumer.
- **No general Notifications module is authorized.** `ADR-033` Part A (database-technology gate) and Part B (event-consumption gate) both remain open and unresolved. This amendment scaffolds nothing.
- **Section 6's general constraint is reaffirmed, unamended, and is the binding constraint of this amendment:** *"any future notification's delivery must never be allowed to affect the underlying business transaction's own outcome."* Push delivery, failure, or absence must never change a Proposal's, Assignment's, Trip's, or Order's state, ordering, eligibility, or any business metric.

**What this amendment does not do**, stated per Section 9's own prescribed sequence: it does **not** extend `USE_CASE_CATALOG.md`, `EVENT_CATALOG.md` Section 9, or `INTERFACE_CONTRACTS.md` Sections 5/7 — the Product Owner has confirmed no new use case or cross-module contract is created, because N1/N2 create no event, no consumer, and no inter-module contract for those documents to name.

**Architecture record.** The architecture satisfying this amendment is recorded in `ADR-071`'s append-only supersession pointer (dated 2026-09-19) and in `ADR-083` (Driver Web Push for Open Proposal and Price Confirmation).
