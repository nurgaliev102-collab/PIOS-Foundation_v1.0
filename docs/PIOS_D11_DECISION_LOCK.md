# PIOS D-11 Decision Lock — Tier 2 / Driver Calendar / Vehicle Visibility

**Status: RATIFIED.** **Date: 2026-09-19.** **Ratified by: Product Owner.**

This document is the Product Owner's binding ratification of the three D-11 subjects reconciled in the prior read-only architect recon. It is documentation/decision ratification only — **no implementation code, migration, API code, frontend code, or test is authorized to exist yet on the strength of this document alone.** Each subject's own implementation may begin only once its own process gates (named below) are satisfied.

---

## Scope disambiguation — read this before acting on anything below

**"D-11" is ambiguous in this repository. There are two different things called D-11:**

1. The decision-index **D-11** — *"Driver business model — what PIOS is for the driver"* (`docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md`, decision index, downstream of D-4). **This document has nothing to do with that D-11.**
2. The recommendation-block **"D-11"** — *"Tier 2, the calendar, and the trust surface"* (`PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` §17 BLOCK D). **This is the D-11 this document ratifies.**

Their underlying analytical items in the same brief are **D-7** (Tier 2), **D-8** (calendar), and **D-6 + §12 S-2/D-2** (vehicle). **Anyone citing "D-11" without this document's own A/B/C1/C2/C3 labels is citing an ambiguous term — cite the specific decision (D-11.A, D-11.B, D-11.C1, D-11.C2, or D-11.C3) instead.**

The three subjects below have **different architectural bases, different decision types, and different evidence-basis status**. They are ratified as **three fully independent decisions**. Approving one implies nothing about the others.

---

## D-11.A — Tier 2 (dispatch fallback "Network" tier)

**Verdict: CANCELLED (permanently).**

Tier 2 ("Network") is **permanently cancelled** as a dispatch routing concept. PIOS Dispatch will not implement or reserve a future Tier 2 dispatch algorithm. Current dispatch remains, unchanged:

**Tier 1 (trusted) → Tier 3 (open pool) → `NoAvailableDriver`.**

No Tier 2 algorithm (none of the previously-recorded candidates N-a/N-b/N-c, and no other) is designed, implemented, or inferred by this decision. **Any future attempt to introduce a Tier 2 mechanism requires a completely new Product Decision** — this ratification does not merely defer the question, it closes it; reopening it is not a continuation of `ADR-068`'s own reservation but a fresh decision superseding this one.

**No implementation code is required or authorized by this decision.**

**ADR amendment:** `docs/ADR/ADR-068-Relationship-Ordered-Fallback-Dispatch.md` amended in place, dated 2026-09-19, at two points — the Status block's Q2 answer (converting "reserved" to "permanently cancelled") and Part 3's N-a/N-b/N-c candidate table (marked closed, preserved for the historical record, not deleted, per `CLAUDE.md` "Never Delete Documentation" and `ADR-015`).

**Process gates:** none beyond the ADR amendment itself — a decision not to build requires no evidence, no hypothesis, and no Sprint basis.

---

## D-11.B — Driver Calendar (read-only, informational)

**Verdict: GO, narrowly — read-only, informational only.**

Authorized:
- A driver can view their own already-accepted future rides.
- The system can show a non-blocking, purely informational overlap warning.

**The feature must not**, under any implementation: block a ride; reject a ride; auto-decline anything; create a reservation; create a hold; reserve driver capacity; change Dispatch eligibility; change Dispatch priority; change ranking; change trust; affect pricing; synchronize with any external calendar; expose a driver's schedule to any passenger; or create a paywall/monetization rule.

**CRITICAL — the overlap threshold is explicitly OPEN.** No minute value, default, or rule for what counts as "overlapping" or "close together" is decided by this document. **This is a separate Product Owner decision, not yet made.** Until it is made, the only authorized behavior is displaying the driver's own future pickup times adjacently, with no computed warning. No architect and no developer may choose a number on the Product Owner's behalf (`ADR-002`, `CLAUDE.md`).

**ADR:** `docs/ADR/ADR-084-Driver-Calendar-Read-Only-Informational-View.md` — created and accepted, 2026-09-19. It explicitly establishes that this is a read-only informational view with no Dispatch effect, and that the overlap threshold remains a separate, future Product Owner decision.

`docs/ADR/ADR-076-Server-Authorized-Named-Driver-Offer.md` amended in place, dated 2026-09-19, at two points (its factual claim "PIOS has no driver calendar," and its own "does not authorize" list), narrowing "any driver calendar" out while leaving "hold, reservation or conflict check" fully prohibited, unchanged.

**Process gates, both satisfied 2026-09-19:**
1. Hypothesis **H16** registered in `docs/PIOS_PRODUCT_HYPOTHESES.md`.
2. `ADR-084` accepted.

**Implementation may not begin until a specific overlap threshold is separately decided, OR the Product Owner explicitly accepts shipping the adjacency-only view with no computed warning as the complete v1.** This document does not itself choose between those two paths — that choice is recorded at implementation-handoff time, not here.

---

## D-11.C1 — Vehicle Plate Visibility (unauthenticated read)

**Verdict: GO.**

`vehiclePlateNumber` is removed from `GET /v1/drivers/{driverId}` when accessed as an unauthenticated/public driver read. The driver's authenticated self-view **must** remain capable of reading and editing the stored plate, unchanged.

**CRITICAL DATA-INTEGRITY REQUIREMENT, recorded as a binding future implementation acceptance criterion:** removing the plate from the public read path must not remove or overwrite the stored plate. The driver's own vehicle-edit flow must continue to preserve the stored plate when the driver changes make/model/colour/seat count — a plain field deletion from the shared response mapper would silently erase every driver's plate on their next vehicle edit, because the driver's own edit screen today reads from the same unauthenticated endpoint and saves all vehicle fields together. This is stated as an explicit, non-negotiable acceptance criterion for whoever implements D-11.C1.

**Not authorized by this decision:** any new `Vehicle` aggregate; any change to vehicle storage or the data model; any change to phone-disclosure rules (`ADR-059` untouched); any redesign of driver identity.

**ADR:** `docs/ADR/ADR-085-Driver-Vehicle-Plate-Visibility.md` — created and accepted, 2026-09-19, covering D-11.C1 and D-11.C2 together (per the architect's own recommendation that these are one field-level-redaction decision, not two).

**Process gates:** none beyond the ADR — this is remediation of a confirmed, live, credential-free disclosure (the plate is actively exposed today via the public invite-preview flow), which this repository's own precedent (`ADR-060`'s treatment of confirmed vulnerabilities) treats as admissible without a registered hypothesis or evidence-log entry.

---

## D-11.C2 — Passenger Plate Visibility

**Verdict: NO-GO, for now.**

No new passenger-facing plate endpoint or new plate-visibility model is introduced. No future rule for when, or whether, a passenger may see a driver's plate is inferred, implied, or reserved by this decision. This question is left **fully open** for a separate, future Product Decision, should the Product Owner choose to make one.

**ADR:** covered by `docs/ADR/ADR-085-Driver-Vehicle-Plate-Visibility.md` Part 2, alongside C1.

**Process gates:** none — this is a NO-GO; nothing is authorized to be built.

---

## D-11.C3 — Vehicle Data on Assigned-Ride Screen

**Verdict: GO — implemented SEPARATELY from D-11.C1.**

A passenger with an assigned driver may see: vehicle make; vehicle model; vehicle colour, on the assigned-ride screen. (Make/model/colour already exist in the domain model and are already returned by the backend on other paths — this decision authorizes their display on the assigned-ride surface specifically, where they are currently absent.)

**This does not authorize:** plate disclosure of any kind (D-11.C2 remains NO-GO); phone disclosure (`ADR-059` untouched); any rating, score, or other numeric trust signal (`DRIVER_IDENTITY_DESIGN_DECISION.md` §4 remains fully in force); any redesign of driver identity.

**CROSS-FEATURE RULE — binding:** **D-11.C1 and D-11.C3 must NOT be implemented in the same commit.** D-11.C1 is disclosure remediation (removing something). D-11.C3 is a new passenger-facing product surface (adding something). Keeping them in independently attributable commits means that if a future incident involves plate visibility, it is unambiguously traceable to one change, not conflated with the other. This rule is recorded in both this document and `ADR-085` Part 4.

**ADR:** none required — the backend already returns make/model/colour on other paths; this is an additive, frontend-only display change with no new authorization boundary to cross.

**Process gates:** hypothesis **H17** registered in `docs/PIOS_PRODUCT_HYPOTHESES.md`, 2026-09-19 — registered separately from H16 because it tests a distinct, unrelated behavioral claim (passenger vehicle recognition, not driver schedule awareness), per this repository's existing one-hypothesis-per-Sprint convention.

---

## Cross-feature dependencies (recorded, not re-litigated here — see the architect recon for full reasoning)

- **A is independent of B and C.** Tier 2 appears in no scheduling, vehicle, or metric code.
- **A → B, weak:** cancelling Tier 2 permanently makes B's "never becomes a dispatch input" guardrail structurally easier to hold.
- **C1 ↔ C3, strong — governed by the binding cross-feature rule above.**
- **C1 has a hard implementation dependency** on preserving the driver's own authenticated self-view (see D-11.C1's data-integrity requirement).
- **B is independent of C** — they share no aggregate, no endpoint, and no data.

---

## Summary of documentation changes this ratification produced

1. `docs/ADR/ADR-068-Relationship-Ordered-Fallback-Dispatch.md` — amended in place (Tier 2 permanently cancelled).
2. `docs/ADR/ADR-084-Driver-Calendar-Read-Only-Informational-View.md` — new, accepted.
3. `docs/ADR/ADR-076-Server-Authorized-Named-Driver-Offer.md` — amended in place (calendar narrowly permitted, hold/reservation/conflict-check still prohibited).
4. `docs/ADR/ADR-085-Driver-Vehicle-Plate-Visibility.md` — new, accepted (C1 GO, C2 NO-GO).
5. `docs/PIOS_PRODUCT_HYPOTHESES.md` — H16 (calendar) and H17 (vehicle-on-assigned-ride) registered.
6. This document.

No Kotlin, TypeScript, SQL, or frontend implementation file is created or modified by this ratification. No test is created or modified. No migration is created.
