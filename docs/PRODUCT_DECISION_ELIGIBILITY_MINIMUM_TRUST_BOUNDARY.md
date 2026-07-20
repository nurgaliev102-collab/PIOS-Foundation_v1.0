# PIOS Product Decision: Eligibility and Minimum Trust Boundary v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document defines the minimum eligibility boundary for participation in future shared opportunities. It is not an ADR, not an architecture document, and designs no Fair Opportunity Policy, assignment algorithm, ranking, reputation system, or KYC automation. It resolves the specific product question [PRODUCT_BASELINE_V2.md](PRODUCT_BASELINE_V2.md) Section 10 and Section 16 Item 5 left **[OPEN]**, building directly on [PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md](PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md) (the authority gate) and [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md) (the Opportunity/Commitment boundary Eligibility sits upstream of).

Derived from PROJECT_CONSTITUTION.md, PRODUCT_BASELINE_V2.md, PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md, PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md, PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, MODULE_STRUCTURE.md, APPLICATION_ARCHITECTURE.md, ADR-002, ADR-034, and the actual committed implementation of `Driver`, `Availability`, `DriverRepository`, `DriverAvailabilityApplicationService` (`backend/driver-management`) and `DriverAvailabilityRecord`, `DriverAvailabilityRepository` (`backend/dispatch`). It introduces no new aggregate, event, command, or actor; where it names a new concept (Eligibility), it does so as a product-level boundary definition only, never as domain, architecture, or code.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, or directly observable in committed code, cited inline.
- **[DERIVED]** — a decision this document actually makes, reasoning from already-ratified principles and already-implemented behavior to a specific question those sources do not themselves literally answer.
- **[HYPOTHESIS]** — a candidate requiring further validation; recorded, not adopted.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is promoted to a requirement anywhere in this document.

## Central Finding, Stated First

`Driver.kt`'s own KDoc states directly: "This implementation covers only the availability capability. A driver's standing and participation... are outside this capability's scope and are not represented here." **[RATIFIED — current code behavior.]** DOMAIN_MODEL.md Section 4 itself already names all three — "standing, availability, and participation" — as the Driver aggregate's purpose, but only Availability has ever been implemented. `DriverAvailabilityRecord` (Dispatch's own local projection) carries only `driverReference` and `available` — "no profile, standing, participation, ranking, or matching-criteria data," per its own KDoc, "none of which any ratified document attributes to Dispatch."

**Consequence:** no Eligibility concept, gate, or check exists anywhere in this codebase today, at any layer. Because nothing else exists, every driver who is currently declared `AVAILABLE` is, today, treated as a candidate by omission — not because any document equates Availability with Eligibility, but because no separate Eligibility boundary has ever been built to distinguish the two. Every resolution below is checked against this finding.

## 1. What Does Eligibility Mean?

**[DERIVED].** Eligibility is the product-level name for the minimum standing and trust boundary a driver must satisfy before being considered a candidate for a NETWORK-scoped Opportunity (PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3; PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 1). It is not ratified vocabulary — DOMAIN_MODEL.md never uses the word — and no aggregate, invariant, or lifecycle is created for it here.

Eligibility answers exactly one question: **may this driver be considered at all?** It is a binary gate, not a comparison. It never answers "how should this driver be ordered relative to other eligible drivers" — that question belongs entirely to a future Fair Opportunity Policy (PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 8), explicitly out of this document's scope.

## 2. Identity, Participation Status, Availability, Eligibility, Reputation

- **Identity.** **[RATIFIED]** — `DriverId`, the driver's own identity within Driver Management (DOMAIN_MODEL.md Section 4). A precondition for everything below; carries no standing or trust information by itself.
- **Participation Status.** **[RATIFIED as a named concept, MISSING as an implementation]** — DOMAIN_MODEL.md Section 4 names "standing... and participation" as distinct concerns of the Driver aggregate, alongside availability; neither is implemented (`Driver.kt`'s own KDoc, quoted above). Participation Status would describe whether a driver remains a recognized, standing participant of the platform at all — logically prior to, and independent of, whether they currently happen to be available.
- **Availability.** **[RATIFIED]** — "a driver's own declaration of their readiness to receive an assignment" (DOMAIN_MODEL.md Section 6); implemented as exactly `AVAILABLE`/`UNAVAILABLE`, nothing else (`Availability.kt`).
- **Eligibility.** **[DERIVED]** — this document's own new name (Section 1). Grounded in the same ratified "standing... and participation" concepts DOMAIN_MODEL.md Section 4 already names but never implements; not itself ratified vocabulary, not implemented.
- **Reputation.** **[OPEN]** — no ratified concept exists at all. ADR-034 Part 2 explicitly classifies "Driver profile, standing, participation, or ranking beyond availability" as information "Driver Management owns... but exposes none of them to Dispatch," and separately lists it among **forbidden inputs** to the assignment decision today. Reputation is open both as a concept and, independently, already excluded as a current input to anything Dispatch does.

These five are five distinct concepts. None is a synonym for another, and only Identity and Availability are both ratified and implemented today.

## 3. Why Availability ≠ Eligibility

**[RATIFIED as a structural distinction, reused directly from PRODUCT_BASELINE_V2.md Section 10].** Availability is a moment-to-moment, self-declared readiness signal with zero standing or trust content (DOMAIN_MODEL.md Section 6). Eligibility, as defined in Section 1, would be a more durable boundary — whether a driver has met whatever minimum participation requirement exists — logically prior to and independent of whether they currently happen to be available. A driver could, in principle, be eligible but unavailable (declared `UNAVAILABLE`), or available but not eligible if a minimum trust boundary were ever unmet.

**The precise, evidence-based point this document makes:** today, Availability functions as a **de facto proxy** for Eligibility purely because no Eligibility concept has ever been built — not because any ratified document equates the two, and not because doing so was ever decided. This document records that gap explicitly rather than allowing the current absence of an Eligibility check to be silently read as a decision that Availability is sufficient.

## 4. Who Owns Eligibility-Related Facts?

**[DERIVED].** Driver Management, exclusively — a direct extension of its already-ratified, exclusive ownership of "a driver's standing, availability, and participation" (MODULE_STRUCTURE.md Section 3; DOMAIN_MODEL.md Section 4). Since Eligibility would be built from standing/participation facts, and Driver Management already owns those exclusively, Driver Management would own Eligibility too, if and when it is ever built. No other module has any ratified claim to this information (INTERFACE_CONTRACTS.md Section 4); this is not a new ownership decision, only its direct application to a concept (Eligibility) that did not previously exist to apply it to.

## 5. What Dispatch May Consume Versus What It May Decide

**[RATIFIED, reused directly from ADR-034 Part 2].** Dispatch may consume only Driver Availability today — the sole ratified Driver Management → Dispatch contract (INTERFACE_CONTRACTS.md Section 5). Any Eligibility signal is, at most, a **future candidate input**, exactly as ADR-034 Part 2 already classifies "any Driver Management-owned information beyond availability (standing, participation)": it "would require both a new INTERFACE_CONTRACTS.md contract and a Driver Management-side decision to expose it, neither of which exists."

Dispatch may never *decide* what counts as eligible — that determination belongs exclusively to Driver Management, consistent with Domain Decides Business Meaning (APPLICATION_ARCHITECTURE.md Section 2). Dispatch may, at most, someday *consume* an already-decided eligibility fact exposed through a new contract — the same pattern already proven for Availability, applied here without alteration.

## 6. Does Personal Client Relationship Affect Eligibility?

**[OPEN] — explicitly, per this task's own instruction not to assume either answer.** No ratified document connects Personal Client Relationship to Eligibility in either direction. This is a distinct question from whether PCR affects the *assignment decision itself* — that question remains open in PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Sections 4 and 8, and in ADR-034 Part 2's own classification of PCR as an unratified "future candidate input" to Assignment Policy. Eligibility (Section 1) is a coarser, prior gate — "may this driver be considered at all" — while any PCR role in the assignment decision would be a preference or priority signal *within* an already-eligible pool. This document does not conflate the two, and resolves neither: PCR's relationship to Eligibility remains exactly as undecided as PCR's relationship to the assignment decision.

## 7. May Ratings, Scores, Contribution Balance, or Payment Tiers Influence Eligibility?

**[OPEN, and already forbidden by direct extension of existing exclusions].** ADR-034 Part 2 already classifies "Driver profile, standing, participation, or ranking beyond availability" as forbidden inputs to the assignment decision; PRODUCT_BASELINE_V2.md Section 15 already records composite driver trust scores, opaque ranking, and dynamic pricing as deferred/forbidden concepts, none ratified anywhere. No ratified document establishes ratings, scores, or contribution balance as concepts at all, so none may influence Eligibility either — extending the existing exclusion to a boundary (Eligibility) that did not previously exist for it to apply to.

**Payment tiers specifically.** **[DERIVED, extending PRODUCT_BASELINE_V2.md Sections 11 and 13 directly]** — "payment to PIOS should not secretly purchase higher priority in fair shared-network opportunity allocation" was already recorded as a derived anti-conflict principle for the assignment decision itself; this document extends it explicitly to Eligibility, closing a narrower reading that might otherwise treat "priority" and "the gate to being considered at all" as different things. Neither may be purchased.

## 8. What Could Minimum Pilot Eligibility Look Like?

**[HYPOTHESIS]** — candidate only, not selected, consistent with PRODUCT_BASELINE_V2.md Section 14's own pilot framing ("manual admission is acceptable as a pilot mechanism... NOT a hard requirement"). One legitimate candidate: MVP Eligibility could consist of nothing more than **Identity** (the driver is represented within Driver Management at all) plus **manual admission** (an operational, non-algorithmic decision to admit a driver to the pilot cohort) plus current **Availability**. Under this candidate, no separate Eligibility check or data model would be built for the pilot at all — a deliberate, documented minimalism, not the silent conflation Section 3 describes as today's actual (undecided) state. This is recorded as one candidate among possibly others; this document does not select it.

## Explicit Preservations

- **No hidden algorithmic exclusion.** **[RATIFIED]** — No Hidden Algorithmic Decisions (Constitution Sections 3, 18); any future Eligibility check must be as explainable as any other decision this principle already governs.
- **No pay-to-win priority.** **[DERIVED]** — Section 7 above.
- **No automatic trust score.** **[RATIFIED absence]** — no such concept is ratified anywhere; already forbidden as an assignment-decision input (ADR-034 Part 2) and, per Section 7, forbidden as an Eligibility input by the same reasoning.
- **No ownership of passenger relationships.** **[RATIFIED]** — Relationship Protection (Constitution Sections 3, 18), unchanged; Eligibility, however it is eventually built, does not touch Personal Client Relationship ownership in any way (Section 6 above leaves the only open question — influence, not ownership — exactly as open as it was).
- **Eligibility is not ranking.** **[DERIVED]** — direct consequence of Section 1's own definition: a binary "may this driver be considered" gate, never an ordering of already-eligible drivers, which remains Fair Opportunity Policy's own, separate, unbuilt responsibility.

## Explicitly Out of Scope

Per this task's own instruction, none of the following is designed here:

- **Fair Opportunity Policy** — remains PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 8's own open question.
- **Assignment algorithm** — remains excluded from every level of documentation (ADR-002; ADR-034).
- **Ranking** — remains undesigned; Eligibility is explicitly not ranking (above).
- **Reputation system** — remains **[OPEN]** as a concept (Section 2); no mechanism is designed.
- **KYC automation** — no such concept exists in any approved document; not introduced here.

## Files Changed

`docs/PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md` (new) is the only content file this decision creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set for the three preceding documents. No ADR, code, database, event, or API is created or modified.

## Unresolved Decisions

1. Whether Personal Client Relationship influences Eligibility (Section 6) — deliberately left open, not assumed either way.
2. Whether an actual Eligibility concept is ever formally designed as a domain fact, contract, or check, and its exact minimum criteria (Sections 1, 4–5).
3. Which specific pilot Eligibility candidate (Section 8) is chosen, if any — recorded as one hypothesis only.
4. Fair Opportunity Policy and any ranking/ordering mechanism among eligible drivers — unchanged, remain open per their own source documents.
5. Whether Driver Management's currently-unimplemented "standing" and "participation" concepts are ever built out at all, independent of whether Eligibility is ever built on top of them.

## Conflicts

**None found.** Checked against and consistent with: ADR-002 (Dispatch's exclusive assignment-decision ownership, unchanged); ADR-034 Part 2 (the forbidden-inputs list and PCR's status as an unratified candidate input, both reused directly, not reopened); PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md and PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md (this document sits strictly downstream of both, redeciding neither authority nor commitment semantics); PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 5 (service quality/performance-based assignment remains **[REQUIRES VALIDATION]** there and is not promoted here either); PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md Part 8 (PCR's open questions remain exactly as open, Section 6 above adds a new open question rather than resolving an old one).

## Impact Analysis

**Documents:** No existing document modified beyond the one minimal `docs/README.md` line. DOMAIN_MODEL.md, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md, MODULE_STRUCTURE.md, and every ADR remain exactly as they were.

**Code:** None. `Driver.kt`, `Availability.kt`, `DriverId.kt`, `DriverRepository.kt`, `DriverAvailabilityApplicationService.kt`, `DriverAvailabilityRecord.kt`, and `DriverAvailabilityRepository.kt` (dispatch) are all read, not touched.

**Future work enabled, not performed:** A future ADR could define the architectural shape of an Eligibility fact and a new Driver Management → Dispatch contract exposing it (mirroring how Availability's own contract already works), once a Product Decision resolves Section 6's PCR question and Section 8's pilot-candidate question with actual evidence. Neither is undertaken here.

## Recommended Next Step

Not Fair Opportunity Policy design (explicitly excluded), and not an ADR. The most evidence-grounded next step is a further Product Owner decision selecting a concrete MVP Eligibility candidate from Section 8 — since Sections 1–5 already establish the boundary's meaning, ownership, and consumption rules precisely enough that a future ADR could act on them, but no pilot-level implementation should proceed while Section 8 remains an unselected hypothesis.

## Traceability

| Section | Source Document |
| --- | --- |
| Central Finding | `Driver.kt`, `DriverAvailabilityRecord.kt` (commit `9d7cea9`, `b794d8f`); DOMAIN_MODEL.md Section 4 |
| 1. Eligibility Definition | PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md Section 3; PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 1 |
| 2. Five Distinct Concepts | DOMAIN_MODEL.md Sections 4, 6; ADR-034 Part 2 |
| 3. Availability ≠ Eligibility | PRODUCT_BASELINE_V2.md Section 10; DOMAIN_MODEL.md Section 6 |
| 4. Ownership | MODULE_STRUCTURE.md Section 3; DOMAIN_MODEL.md Section 4; INTERFACE_CONTRACTS.md Section 4 |
| 5. Consume vs. Decide | ADR-034 Part 2; INTERFACE_CONTRACTS.md Section 5; APPLICATION_ARCHITECTURE.md Section 2 |
| 6. Personal Client Relationship | PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Sections 4, 8; ADR-034 Part 2; Product Decision Personal Client Relationship Part 8 |
| 7. Ratings/Scores/Payment Tiers | ADR-034 Part 2; PRODUCT_BASELINE_V2.md Sections 11, 13, 15 |
| 8. MVP Pilot Candidate | PRODUCT_BASELINE_V2.md Section 14 |
| Explicit Preservations | PROJECT_CONSTITUTION.md Sections 3, 18; PRODUCT_BASELINE_V2.md Sections 11–13 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Decision (above) requires a further Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
