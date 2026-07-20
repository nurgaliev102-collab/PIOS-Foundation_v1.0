# PIOS Product Decision: Fulfillment Authority and Scope v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document defines the authority boundary governing when and how a transport request may move from preferred/direct fulfillment toward alternative fulfillment. It is not an ADR, not an architecture document, and authorizes no aggregate, event, command, or implementation. It resolves the specific product question [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md) Section 6 ("Fulfillment Authority and Scope") and Section 16 Item 2 left **[OPEN]**, following the same evidence-grading discipline as [PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md](PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md) and [PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md).

This document is derived from PROJECT_CONSTITUTION.md, PRODUCT_BASELINE_V2.md, PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, APPLICATION_ARCHITECTURE.md, MODULE_STRUCTURE.md, ADR-002, ADR-034, PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md, and PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md. It introduces no new aggregate, event, command, or actor beyond what these documents already establish; where it names a new concept (Fulfillment Authority, DIRECT/REFERRED/NETWORK), it does so as a product-level boundary definition only, explicitly not as domain, architecture, or code.

---

## Method: Evidence Grading

Every claim below carries one of four tags:

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, cited inline.
- **[DERIVED]** — a decision this document actually makes, reasoning from already-ratified principles to a specific governance question those principles do not themselves literally answer. This is the primary tag this document uses for its own resolutions (Sections 4–5): it means the conclusion is a genuine act of this Product Decision, not a restatement, but one that cannot be read as contradicting anything above it in the hierarchy.
- **[HYPOTHESIS]** — a candidate requiring real-world or further product validation; recorded, not adopted.
- **[OPEN]** — not decided by any approved document, and not decided by this one either; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is treated as a requirement anywhere in this document.

## 1. The Fulfillment Authority Concept

**Definition (this document's own act).** Fulfillment Authority is the scope of permission, held at a given moment by a specific actor, that determines which set of drivers or mechanisms may legitimately be used to fulfill a given transport request. It is a product-level boundary concept only — no aggregate, invariant, or lifecycle is created for it here (DOMAIN_MODEL.md Section 4 requires each to be "already derivable" before an aggregate is described; none is derivable yet, and this document does not manufacture one).

**Why this is necessary.** PRODUCT_BASELINE_V2.md Section 7 already found, by direct code inspection, that Dispatch's assignment decision has **no eligibility or authorization gate of any kind today** — `DispatchAssignmentApplicationService.handle` accepts an already-chosen driver from its caller with no read of any availability or authorization state at all **[RATIFIED — current code behavior, PRODUCT_BASELINE_V2.md Section 7]**. ADR-002 gives Dispatch exclusive ownership of *how* an assignment is computed once Dispatch is reached, but no document — including ADR-002 itself — states *when* Dispatch is authorized to be reached for a request that did not originate as open Platform Order demand. This is the specific gap Fulfillment Authority names. **[DERIVED]**

**What this document does not do.** It does not create an Order Origin value, an event, a command, or a Dispatch input. Any of those would be an architecture or domain act, belonging to a future ADR and DOMAIN_MODEL.md/EVENT_CATALOG.md extension (Section 7 below), consistent with the Documentation Hierarchy (PROJECT_CONSTITUTION.md Section 5).

## 2. Four Distinct Concepts

- **Order existence.** **[RATIFIED]** — OrderSubmitted marks only that a transportation request has been received and is being tracked (EVENT_CATALOG.md Section 5; DOMAIN_MODEL.md Section 8). It establishes nothing about who may fulfill the order.
- **Fulfillment intent.** **[DERIVED]** — the requester's own expression, at submission, of how they intend the order to be fulfilled (for example, naming a Personal Client relationship vs. accepting open Platform Order allocation). Consistent with Order Origin already being "fixed at submission" (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 4), but "fulfillment intent" as a named concept distinct from Order Origin is not itself stated anywhere; this document introduces the label to make the boundary in Section 3 precise, without creating a new domain field.
- **Authorization to search alternatives.** **[DERIVED]** — a distinct permission, separate from both Order existence and Fulfillment intent, that a requester (or another actor per Section 4) grants for fulfillment to expand beyond what was expressed at submission. This is the permission Fulfillment Authority (Section 1) actually governs. It is explicitly **not** the same thing as Dispatch's own assignment-decision authority (ADR-002): ADR-002 governs *how* Dispatch decides once it may act; this concept governs *whether* Dispatch — or any alternative-fulfillment mechanism — may be reached at all for a given request.
- **Assignment / commitment.** **[RATIFIED]** — already implemented as the Assignment aggregate's CREATED → ACCEPTED lifecycle (DOMAIN_MODEL.md Sections 4, 12; PRODUCT_BASELINE_V2.md Section 8). Authorization to search alternatives necessarily precedes Assignment and is a distinct, prior act — an order can be authorized for NETWORK scope without any assignment yet existing, and an assignment cannot legitimately exist for a NETWORK-fulfilled order without that authorization already having occurred.

The four are ordered and non-collapsible: Order existence → Fulfillment intent → (only if intent cannot be met) Authorization to search alternatives → Assignment/commitment. No document conflates them today, because only the first and last are currently ratified as implemented concepts; the middle two are this document's own contribution.

## 3. Candidate Fulfillment Scopes

None of the following is assumed ratified. Each is evaluated individually against existing evidence:

- **DIRECT.** **[DERIVED]** — fulfillment by the specific driver the requester's fulfillment intent names or implies (the personal-client-directed case). Grounded in Order Origin's personal-client-directed category (DOMAIN_MODEL.md Section 6, by implication) and in Personal Client Relationship being a first-class, product-supported concept (PRODUCT_FOUNDATION.md Section 13). The label "DIRECT" itself is new; the underlying case is not.
- **NETWORK.** **[DERIVED]** — fulfillment through Dispatch's own assignment decision, open to any eligible driver. Grounded directly in Platform Order (PRODUCT_FOUNDATION.md Section 10) being fulfilled through Dispatch's already-ratified, exclusive assignment mechanism (ADR-002). The label "NETWORK" is new; the underlying mechanism is the platform's existing, implemented default path for non-personal-client demand.
- **REFERRED.** **[OPEN]** — fulfillment by a specific alternative driver named by some actor other than open Dispatch allocation (for example, the original driver referring a colleague). No approved document supports, names, or implies this category in any form; it has no grounding comparable to DIRECT or NETWORK. This document records the candidate name only, per this task's own instruction not to assume it ratified, and takes no further position on whether it should ever exist.

**Conclusion.** DIRECT and NETWORK are product-level names for two mechanisms that already have real ratified grounding elsewhere; REFERRED does not, and is the most speculative of the three. None of the three is created here as a domain concept, Order Origin value, or code artifact.

## 4. Resolutions

### Who may authorize scope expansion?

**Decision:** Only the requester who expressed the original fulfillment intent (the passenger or corporate customer) may authorize expansion away from that intent. **[DERIVED]** — grounded in Platform Neutrality's deference to the demand source's own declared preference (PRODUCT_FOUNDATION.md Section 5) and in Relationship Protection's prohibition on the platform capturing or redirecting a relationship on its own initiative (Constitution Sections 3, 18); since Personal Client Relationship spans both the driver and the passenger/corporate customer jointly (DOMAIN_MODEL.md Section 5), a decision to move away from it cannot rest with the driver's side alone.

A driver's own request that fallback occur (see below) is real and legitimate, but it is a *request*, not an *authorization* — the requester's own agreement is what actually completes the authorization, unless a future pre-authorization mechanism changes when that agreement is understood to have been given (Section 6).

Dispatch may never authorize its own scope expansion (see below); Dispatch is a possible *destination* of an authorization already granted, never its *source*.

### Is passenger authorization required?

**Decision:** Yes, for any order whose fulfillment intent was personal-client-directed. **[DERIVED]** — a driver alone re-routing a passenger's expressed preference, without the passenger's own agreement, would be indistinguishable from the platform silently changing who fulfills the request — exactly what Transparency and No Hidden Algorithmic Decisions (Constitution Sections 3, 18) forbid, and what Relationship Protection exists specifically to prevent being treated as a byproduct of a single transaction.

For an order whose fulfillment intent was already Platform Order (no personal-client direction expressed at submission), no additional authorization is needed to reach NETWORK scope: NETWORK was never outside that order's original intent, so no "expansion" occurs. **[DERIVED]**

Whether passenger authorization may ever be given once, in advance, rather than per order, is not decided as a general rule here — it is recorded only as an MVP candidate (Section 6).

### May a driver request fallback?

**Decision:** Yes. **[DERIVED]** — a driver retains the same self-determined control over their own participation that Availability already grants them (PRODUCT_FOUNDATION.md Section 12; DOMAIN_MODEL.md Section 6); nothing in any ratified document prevents a driver from indicating, for a specific personal-client-directed request, that they cannot personally fulfill it and that fallback should be considered. This request is a real, legitimate act — but per the resolution above, it is not itself an authorization.

### May Dispatch ever initiate scope change on its own?

**Decision:** No. **[DERIVED, applied directly from Constitution Section 19]** — Dispatch's exclusive ownership (ADR-002) covers the assignment decision once Dispatch is authorized to be reached for a request; it does not extend to deciding, on its own, when that authorization exists. A Dispatch that could reach into a personal-client-directed request without the requester's own prior authorization would be exactly the "hidden dispatcher" and "unaccountable control" PIOS's own identity (Constitution Section 19) is defined in direct opposition to. This is not a new architectural restriction — it is a direct product-level consequence of an identity statement already ratified, applied to a scenario (Fulfillment Authority) that did not previously exist to apply it to.

### What rights are NOT granted by authorization?

Authorization to search alternatives, however it is eventually granted, does **not** grant:

- **Ownership or transfer of the Personal Client Relationship itself.** **[RATIFIED]** — the relationship "exists independent of any single Order" (DOMAIN_MODEL.md Section 13); one fulfilled order via fallback does not create or transfer it (PRODUCT_BASELINE_V2.md Section 12; Product Decision Personal Client Relationship Part 7).
- **Any change to the price or conditions of the request.** **[DERIVED]** — no document grants Dispatch, or any fallback mechanism, authority over Payments, which remains self-contained and interacts with no other module (INTERFACE_CONTRACTS.md Section 3); authorization is scoped strictly to *who* may fulfill the request, never to *on what terms*.
- **Any permanent or future-order effect.** **[DERIVED]** — a given order's authorization is scoped to that order alone, unless a future, separate decision ratifies standing pre-authorization (Section 6); it does not alter the underlying Personal Client Relationship's own standing or durability.
- **Disclosure of Personal Client Relationship information to Dispatch or any other module.** **[RATIFIED absence, carried forward]** — Dispatch still has no ratified access to this information (Product Decision Personal Client Relationship Part 2, "No ratified role... [RATIFIED absence]"); a future fallback mechanism must not grant such access as an incidental side effect, since doing so would be a new information-ownership decision this document does not make.

## 5. Explicit Protections

- **Relationship autonomy.** **[RATIFIED]**, restated not reinvented — Relationship Protection (Constitution Sections 3, 18). Neither party is compelled to use fallback; it exists only to prevent an order going unfulfilled, never to redirect the relationship itself.
- **No automatic client transfer.** **[RATIFIED]** — PRODUCT_BASELINE_V2.md Section 12; DOMAIN_MODEL.md Section 13.
- **No hidden platform control.** **[RATIFIED]**, directly enforced by this document's own resolution that Dispatch may never initiate scope change (Section 4).
- **No silent price or condition changes.** **[DERIVED]** — consistent with Transparency and No Hidden Algorithmic Decisions; no document previously stated this for a fallback scenario specifically, since fallback itself is new vocabulary, but the underlying transparency principle already forbids any silent change of terms, and this document extends it explicitly to this scenario rather than leaving it to be assumed.

## 6. MVP Candidate (Not Implemented)

Recorded as candidates only; nothing here is selected, decided, or implemented:

- **Manual authorization** — the requester confirms fallback per order, in the moment. **[HYPOTHESIS]** — consistent with every resolution in Section 4, and the simplest mechanism, requiring no standing-consent data model.
- **Pre-authorized fallback** — a standing passenger preference permitting fallback without per-order confirmation. **[HYPOTHESIS]** — would require its own future Product Decision, since it changes the moment at which "the requester's own agreement" (Section 4) is understood to have been given; not adopted here.
- **Pilot assumption.** **[HYPOTHESIS]**, consistent with PRODUCT_BASELINE_V2.md Section 14 — any pilot must pick one of the two mechanisms above explicitly; this document does not pick for it, and does not assume a pilot exists at all.

## 7. Explicitly Not Decided Here

Per this task's own instruction, none of the following is designed by this document, since each depends on the scope boundary this document defines rather than the reverse:

- **Opportunity design** — how a driver actually receives and responds to a NETWORK-scoped request once authorized. Not designed here.
- **Eligibility design** — which drivers qualify to receive a NETWORK-scoped request. Not designed here.
- **The concrete fairness/allocation mechanism** for NETWORK scope — remains PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md's own open question (Section 8, Section 5), untouched by this document.
- **REFERRED scope's mechanism**, if it is ever pursued — recorded as a name only (Section 3); no mechanism is defined, and no document is extended to support it.
- **Whether Personal Client Relationship carries any weight inside Dispatch's eventual assignment decision once NETWORK scope is reached** — this remains Product Decision Personal Client Relationship Part 8's own open question and ADR-034 Part 2's own "future candidate input" classification; this document governs only the authority gate *before* Dispatch's decision, never what Dispatch's decision itself may consider.

## Files Changed

`docs/PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md` (new) is the only content file this decision creates. `docs/README.md` receives one minimal traceability pointer (Section 8, below) under its existing "Product" heading, consistent with the precedent set for PRODUCT_BASELINE_V2.md. No ADR, code, database, event, or API is created or modified, consistent with this task's scope.

## Unresolved Decisions

1. Whether manual, per-order authorization or standing pre-authorization (or both) governs the MVP (Section 6).
2. Whether REFERRED scope should exist at all, and if so, its mechanism (Section 3).
3. Whether Personal Client Relationship carries weight inside Dispatch's own assignment decision once NETWORK scope is authorized (Section 7; unchanged from Product Decision Personal Client Relationship Part 8 and ADR-034 Part 2).
4. The concrete Opportunity and Eligibility designs, deliberately deferred (Section 7).
5. Whether "fulfillment intent" and the DIRECT/NETWORK labels should ever be promoted into a formal Order Origin value, an event, or an INTERFACE_CONTRACTS.md contract — an architecture-level decision this document does not make.

## Conflicts

**None found.** This document's resolutions were checked against, and do not contradict: ADR-002 (Dispatch's assignment-decision ownership is unchanged — this document governs only the gate before that ownership is reached); ADR-034 (Personal Client Relationship remains an unratified candidate input to Assignment Policy, exactly as before); Product Decision Personal Client Relationship (exclusivity, mutual confirmation, and Dispatch's own relationship to the entity all remain exactly as open as that document left them); PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md (the fairness metric and Personal Client priority rules remain exactly as open as that document left them). No existing ratified document is reinterpreted.

## Impact Analysis

**Documents:** No existing document is modified beyond the one minimal `docs/README.md` traceability line. EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, DOMAIN_MODEL.md, and every existing ADR remain exactly as they were; none is extended by this document, consistent with Section 1's own statement that doing so is a future architecture-level act.

**Code:** None. No production code, migration, API, or event contract is touched. `DispatchAssignmentApplicationService`, `Assignment.kt`, `AssignOrderCommand`, and the entire existing RabbitMQ/outbox infrastructure are unaffected.

**Future work enabled, not performed:** A future ADR could now define the architectural shape of a Fulfillment Authority gate (analogous to how ADR-034 named the Assignment Policy seam without implementing it), and a future Product Decision could resolve Section 6's MVP mechanism choice and Section 3's REFERRED-scope question. Neither is undertaken here.

## Recommended Next Step

Not Opportunity or Eligibility design (explicitly excluded, Section 7), and not an ADR. The next legitimate step is a further Product Owner decision resolving Section 6's MVP authorization-mechanism choice (manual vs. pre-authorized) with actual evidence, since that choice determines what any future architecture-level Fulfillment Authority definition would actually need to support. Only after that exists should an ADR naming the architectural boundary (mirroring ADR-034's own pattern for Assignment Policy) be attempted.

## Traceability

| Section | Source Document |
| --- | --- |
| 1. Fulfillment Authority Concept | PRODUCT_BASELINE_V2.md Section 7; ADR-002; ADR-034 Part 6 |
| 2. Four Distinct Concepts | DOMAIN_MODEL.md Sections 4, 6, 8, 12; EVENT_CATALOG.md Section 5; PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 4; PRODUCT_BASELINE_V2.md Section 8 |
| 3. Candidate Fulfillment Scopes | DOMAIN_MODEL.md Section 6; PRODUCT_FOUNDATION.md Sections 10, 13; ADR-002 |
| 4. Resolutions | PROJECT_CONSTITUTION.md Sections 3, 18, 19; PRODUCT_FOUNDATION.md Sections 5, 12; DOMAIN_MODEL.md Sections 5, 6, 13; Product Decision Personal Client Relationship Parts 2, 7; INTERFACE_CONTRACTS.md Section 3 |
| 5. Explicit Protections | PROJECT_CONSTITUTION.md Sections 3, 18; PRODUCT_BASELINE_V2.md Section 12; DOMAIN_MODEL.md Section 13 |
| 6. MVP Candidate | PRODUCT_BASELINE_V2.md Section 14 |
| 7. Explicitly Not Decided | PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Sections 5, 8; Product Decision Personal Client Relationship Part 8; ADR-034 Part 2 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Decision (above) requires a further Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
