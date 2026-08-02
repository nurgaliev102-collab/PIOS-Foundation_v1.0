# PIOS MVP Evolution Plan v1.0

Status: Decided — Product Owner-authority sequencing, ratified 2026-08-01, companion to `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md`. This document sequences future work into three stages. It is not an ADR, not an implementation plan, and authorizes no code, migration, or module change by itself. Each stage becomes buildable only when its own prerequisites (named below) are separately satisfied — an ADR where architecture changes, a Product Owner ruling where a ratified constraint is in the way, and in every case an Evidence Gate basis per `PIOS_PRODUCT_EVIDENCE.md`'s existing rule.

Written together with `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` and the `H4` entry in `PIOS_PRODUCT_HYPOTHESES.md`; read those first. Grounded in the Final Product Review of 2026-08-01, cited inline throughout.

## Method: Evidence Grading

Same convention as `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md`:

- **[RATIFIED]** — stated in an approved document or directly observable in committed code, cited inline.
- **[DERIVED]** — a decision this document makes, reasoning from ratified sources.
- **[OPEN]** — not decided here; a named prerequisite.

## Relationship to Driver MVP v1.1

**[RATIFIED — unchanged by this document.]** `IMPLEMENTATION_PLAN_DRIVER_MVP_V1_1.md` and ADR-042 Amendment 2026-08-01 (round 3) remain exactly what they were ratified as: one frontend file, on the basis of hypothesis H3. Nothing below adds to that scope, subtracts from it, or reorders it. This plan begins **after** v1.1, and none of its stages may be read backward into v1.1's Definition of Done.

## Evidence Gate Status, Stated Up Front

**[RATIFIED — checked against `PIOS_PRODUCT_EVIDENCE.md` on 2026-08-01.]** The evidence journal is empty; the only registered, usable hypothesis is H3 (scoped to price visibility, already used as v1.1's basis) and H4 (registered by this same pass, scoped to willingness-to-pay specifically — see `PIOS_PRODUCT_HYPOTHESES.md`). **Terminology, checked 2026-08-01:** per `PIOS_PRODUCT_EVIDENCE.md`'s own 2026-08-01 rule, a hypothesis-based Sprint **produces** evidence — it does not **consume** it, unlike an `E-NNN`-based Sprint. H3 and H4 both produce; neither consumes. An earlier draft of this document called H3 "consumed by v1.1" — that was the wrong verb and is corrected here. **No item named in Stage 1, 2, or 3 below has its own `E-NNN` citation or its own registered hypothesis today.** Per the Gate's own rule (*«элемент объёма, не обеспеченный ни `E-NNN`, ни зарегистрированной гипотезой, в объём не входит»*), every item below is a planned candidate, not an authorized Sprint, until one is registered for it specifically. This is stated once here rather than repeated under each item, and is not a gap in this document — it is the Gate working as designed.

---

## MVP Stage 1 — Working Entrepreneur Tool

Purpose: make the entrepreneur model (`PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 1) actually true end to end, by closing the gaps the 2026-08-01 review found between what PIOS claims and what it does. Every item here fixes an existing false statement or missing mechanism — none adds a new capability the product doesn't already imply.

### 1.1 `pickupAddress`

Closes Gap C1 (Final Product Review §5): `Order` has no pickup location; `RideRequest.tsx` line 333 tells the passenger the driver will call to arrange it instead. **[RATIFIED gap.]** Architecturally additive and `destination`-shaped — the review concurred no ADR is required, by direct precedent (`Order.kt` lines 32–45, Sprint 3B). The real cost the review flagged is scope, not architecture: `Order.kt`, `SubmitOrderRequest.kt`, `OrderResponse.kt`, `OrderQueryController.kt`, `OrderLifecycleApplicationService`, and a new `V8` migration — a real backend diff, which is why this is its own Stage-1 sprint and not an addition to v1.1.

### 1.2 Real driver availability

Closes the gap the review stated plainly: `Proposal.propose` (`Proposal.kt` lines 125–140) performs no availability check at all, and Dispatch's own `driver_availability` projection is written and never read. Today's toggle, if built, would change a label a passenger is told matters (`PassengerLanding.tsx`: *«Если он свободен — заказ сразу придёт ему»*) without changing what actually happens.

**[OPEN — architecture prerequisite.]** Making availability real means Dispatch must consult it before proposing — an Assignment Policy change under ADR-034 Part 3, which the review named as needing its own ADR. This item may be *planned* here; it may not be *built* until that ADR exists.

### 1.3 Correct order statuses

Closes Gap C2: a declined or lapsed proposal renders to the passenger as the same green *«✅ Водитель уведомлён о заказе»* as an accepted one (`RideRequest.tsx` lines 176–179) — an active false statement, not silence, already recorded in `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.1 as a known, deferred discrepancy.

### 1.4 Fixing false promises

Closes Gap C3: `PassengerLanding.tsx` states two things the backend does not do — that an available driver is what makes the order arrive (no such check exists, 1.2 above), and that a busy driver's order is offered to someone else (no fallback of any kind exists; see Stage 3). Until 1.2 and Stage 3 are real, the correct fix here is to stop making the claim, not to leave the copy ahead of the product.

**Product Owner decision, 2026-08-02 — resolves the choice this item previously left open.** Fix the interface text. Do **not** implement a new distribution mechanic (i.e., do not build any part of Stage 3 — the fallback-to-another-driver behavior — to make the existing copy true). This item is copy-only, in `PassengerLanding.tsx`: frontend text, no ADR, no backend change, no Assignment Policy work. Stage 3 remains fully excluded (Section "Stage 3 — Entrepreneur Network" above) and is not brought forward by this decision.

### Conflict, named rather than resolved

**⚠ This stage's own ordering conflicts with a standing, ratified tactical recommendation.** `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.3 concludes, in these words: *«Вывод: не чинить до пилота. Основания для спринта сегодня нет»* — and §4.3.3 goes further, requiring the driver's status to reach Артур as `UNAVAILABLE` *on purpose*, so that his own unprompted reaction becomes the pilot's first observation. Placing 1.2 in Stage 1 sequences it **before** that observation is collected; the dry-run checklist sequences it **after**. Both are ratified documents and they disagree. This document does not pick a winner — the Product Owner does, before 1.2 is scheduled against a real pilot date.

---

## MVP Stage 2 — Business Visibility

Purpose: give the entrepreneur the record of their own work that `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 8 lists as the largest unimplemented element of the model.

**[BLOCKED IN FULL — corrected 2026-08-01. Not a partial permission; nothing below may be built before a separate ADR-042 revision.]**

Stage 2's subject — ride history, ride price shown at any point, and any financial representation of the entrepreneur's own work — is excluded today by two ratified sources, together, not separately:

1. **ADR-042 round 3 excludes it by name**: *"any history, receipt or earnings screen."* Its **Open Question 9** — *does a driver need to see previously stated amounts after a ride completes?* — is open. Stage 2 cannot be designed against a question the Product Owner has not yet answered.
2. **R4.3 blocks aggregation of any kind.** ADR-042 R4.3: *"No comparison, ordering, or aggregation … never summed, averaged, or otherwise aggregated. This is the single most important prohibition in this ADR."* Its *purpose* is narrow (preventing cross-proposal auction behavior on one order); its *wording* is unrestricted and also catches an entrepreneur's own retrospective total of their own completed rides (`PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §7.2).

**Correction, superseding a prior draft of this section.** An earlier version of this plan read: *"Stage 2 may show a single ride's price as plain text — never a sum, an average, or a count presented as a business total."* That sentence is withdrawn. Even with its own caveat, it could be read as authorizing a price display — which ADR-042 round 3 excludes by name, not only by its aggregation rule. **Until ADR-042 is separately revised, no part of this plan may be read as permission to build, in any form: ride history; ride price displayed outside the single acceptance-time exchange already scoped to Driver MVP v1.1; or any other financial representation — a count, a total, an average, or a tally — of the entrepreneur's own completed work.**

**Client relationship metrics are excluded for a separate reason, and are also withdrawn from this plan.** `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6 states the Personal Client Relationship *"is not defined as, or derived from, an accumulated count or log of past rides."* An earlier version of this plan proposed presenting a count of distinct passengers served as an "operational tally," to distinguish it from a measure of the relationship. **That proposal is withdrawn — a framing choice inside this plan does not satisfy Part 6.** The development of client relationship metrics — whether, and how, an entrepreneur's client relationships may be counted, shown, or summarized at all — requires its own, separate Product Decision. This plan does not propose one.

**Prerequisites for Stage 2 to become buildable, restated as a full gate:**
1. ADR-042 Open Question 9 answered by the Product Owner.
2. A scoped ADR-042 amendment, ratified (not merely proposed), distinguishing cross-proposal comparison (remains forbidden) from any retrospective view of an entrepreneur's own work.
3. If Stage 2 is to include any passenger-facing count: a separate, dedicated Product Decision on client relationship metrics.

One implementation fact for any future Developer task, unaffected by the correction above: `OrderQueryController.listOrders` returns every order in the system, unfiltered — a per-driver business screen needs a scoped query, not a client-side filter over all drivers' data, in a system with no authentication (ADR-042 Open Question 8, still open).

---

## MVP Stage 3 — Entrepreneur Network

Purpose: build the one part of the entrepreneur model whose Product Decision already exists and is ratified — `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md`'s Правило 5, the four-step first-offer ladder (inviting driver → drivers with quality history → trust network → general queue), and its governing principle: *«Связь даёт преимущество. Качество подтверждает право. Оплата следует за трудом.»*

**[RATIFIED decision, unimplemented mechanism.]** Step 1 of the ladder happens today only incidentally — because the passenger's URL happens to carry the inviting driver's id, not because PIOS implements a preference. Steps 2–4 do not exist in any form. `network-management` (Person, Connection, Invitation; ADR-037) is fully built and fully isolated: nothing calls it, and it calls nothing.

**Stage 3 requires two ADRs, not one — corrected 2026-08-01.** A prior version of this section named only the cross-module ADR; that understated the gate:

1. **An ADR on the rules of distribution.** Правило 5 is an *ordering over candidate drivers* — which driver is offered first, second, and so on. Deciding who receives an offer and in what order is Assignment Policy territory (ADR-034 Part 3), and step 2 of the ladder ("drivers with quality history") additionally needs a quality signal that exists in no aggregate today and that ADR-034 Part 2 currently lists among forbidden inputs. This ADR does not exist and is not presumed by this plan.
2. **An ADR on cross-module network interaction.** Activating the ladder means Dispatch consulting a module it has never consulted before — a genuine new cross-module relationship, which under ADR-005/ADR-019's "reference, not ownership" test requires its own ADR before any code is written. This document does not write either ADR or presume its content.

Two invariants from `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` bind this stage in advance, restated here so they aren't rediscovered mid-sprint: connection confers **advantage at the moment of first offer only**, never an annuity and never ownership; and the Инвариант права пассажира — *«Пассажир имеет право выбрать другого исполнителя независимо от существующей связи»* — meaning fair distribution here means an ordered offer sequence, never an exclusive lock.

---

## Sequencing, Stated Plainly

Stage 1 → Stage 2 → Stage 3 is the Product Owner's intended order. Each stage's items are independently gated (an ADR here, a Product Owner ruling there, an Evidence Gate basis everywhere) — satisfying one stage's prerequisites does not satisfy another's, and nothing in this plan authorizes skipping a prerequisite because an earlier stage shipped.

## Unresolved Decisions

1. **1.2 vs. the dry-run's "not before the pilot" recommendation** — named above, unresolved by this document.
2. **Whether ADR-042 R4.3 receives a scoped amendment** for an entrepreneur's own retrospective total — hard prerequisite for Stage 2 (same as `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Unresolved Decision 2).
3. **ADR-042 Open Question 9** — hard prerequisite for Stage 2.
4. **The Assignment Policy ADR** for real availability (1.2) — required before 1.2 is implemented.
5. **An ADR on the rules of distribution** (ADR-034 Part 3/Part 2) for Правило 5's driver ordering — required before any of Stage 3 is implemented. Added 2026-08-01; a prior version of this document did not name it.
6. **The cross-module ADR** for Dispatch consulting `network-management` — required before any of Stage 3 is implemented.
7. **A separate Product Decision on client relationship metrics** — required before Stage 2 may include any passenger-facing count. Added 2026-08-01, replacing a withdrawn in-plan framing proposal.
8. **An Evidence Gate basis for every individual item above** — none currently has one; each needs its own `E-NNN` citation or its own registered hypothesis before it may be scheduled as a Sprint.

## Conflicts

- **⚠ Stage 1.2 versus `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.3** — named above, not resolved.
- **ADR-042 "Explicitly NOT in scope" versus Stage 2** — not a conflict this plan creates; it is the reason Stage 2 is blocked in full, corrected 2026-08-01 to remove language that could be read as a partial permission.
- **`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6 versus a Stage 2 passenger count** — corrected 2026-08-01: this plan no longer proposes a framing (an "operational tally") to route around Part 6. Client relationship metrics require their own Product Decision, not a wording choice in this document.
- No conflict found with `PROJECT_CONSTITUTION.md`, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, or `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`: nothing in any stage proposes payment-for-priority, payment custody, or a computed price.

## Traceability

| Stage / Item | Source |
| --- | --- |
| 1.1 `pickupAddress` | Final Product Review §5 Gap C1; `Order.kt` 32–45; `RideRequest.tsx` 333 |
| 1.2 Real availability | `Proposal.kt` 125–140; `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.3, §4.3.3; ADR-034 Part 3 |
| 1.3 Correct statuses | Final Product Review §5 Gap C2; `RideRequest.tsx` 176–179; `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.1 |
| 1.4 False promises | Final Product Review §5 Gap C3; `PassengerLanding.tsx` |
| Stage 2 (all items, blocked in full) | ADR-042 round 3 "Explicitly NOT in scope", Open Question 9, R4.3; `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §7.2; `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6; `OrderQueryController.kt` |
| Stage 3 (all items) | `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Правило 5, Section 7; ADR-034 Part 2, Part 3; ADR-037; ADR-005, ADR-019 |
| Evidence Gate status | `PIOS_PRODUCT_EVIDENCE.md`, 2026-08-01 addition |

## Files Changed

`docs/PIOS_MVP_EVOLUTION_PLAN.md` (new) is the only file this document creates. No ADR, code, database, event, or API is created or modified. `docs/IMPLEMENTATION_PLAN_DRIVER_MVP_V1_1.md` is left untouched and unaffected — see the companion note in the delivery report on what, if anything, now reads as stale.
