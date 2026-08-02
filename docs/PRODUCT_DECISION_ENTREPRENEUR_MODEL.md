# PIOS Product Decision: Entrepreneur Model v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7), ratified 2026-08-01. This document records what PIOS *is for*: digital infrastructure operated by an independent entrepreneur for their own business, with taxi as the first industry used to validate that model. It is not an ADR, not an architecture document, and authorizes no new code, aggregate, event, module, migration, or database change. It does not set a price, a fee, a commission, or any other commercial term.

It exists because that framing was being relied upon in scoping conversations while existing nowhere in the documentation record. A review of this repository on 2026-08-01 ("Final Product Review — PIOS Driver MVP v1.1", §0.1) searched `docs/` for `предпринимател|entrepreneur|Entrepreneur` and found exactly one occurrence — PROJECT_CONSTITUTION.md Section 20, *"A driver remains an independent entrepreneur, never an employee of the platform"* — a clause that predates and does not establish this framing. Under PROJECT_CONSTITUTION.md Section 5, a decision of this kind is a Product Decision, sitting directly beneath the Constitution and above every ADR. Under Section 4 ("No Hidden Assumptions") and Section 12 ("approval is a documented act, not an inference from code"), it could not continue to be used as an unwritten premise. This document closes that gap and nothing else.

Derived from PROJECT_CONSTITUTION.md, PRODUCT_FOUNDATION.md, PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md, PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md, PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md, PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md, PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md, ADR-005, ADR-019, ADR-034, ADR-037 and ADR-042, plus direct reading of committed code cited inline. It introduces no new aggregate, event, command, actor, or module.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated or directly, unambiguously implied by an approved document, or directly observable in committed code, cited inline.
- **[DERIVED]** — a decision this document actually makes, reasoning from already-ratified principles to a question those sources do not themselves literally answer.
- **[HYPOTHESIS]** — a candidate requiring real-world validation; recorded, not adopted as a requirement.
- **[OPEN]** — not decided by any approved document, and not decided by this one; carried forward explicitly.

Nothing tagged [HYPOTHESIS] or [OPEN] is promoted to a requirement anywhere in this document. In particular, Section 6 is [HYPOTHESIS] in full, and Section 7 records an open dependency this document deliberately does not resolve.

## Central Finding, Stated First

**The entrepreneur model is already implemented in this codebase — in exactly one mechanism, and nowhere else.** The personal invitation link is the whole of it: `frontend/src/identity/InvitationProvider.ts` lines 25–27 issue `<origin>/i/<driverId>`, and `frontend/src/pages/RideRequest/RideRequest.tsx` line 272 posts `{orderId, driverId}` to Dispatch with `driverId` taken **verbatim from the URL the passenger arrived through** — not from a pool, not from a queue, not from any platform selection. `frontend/src/pages/PassengerLanding/PassengerLanding.tsx` lines 180–185 names that driver, by name, on the first screen a new passenger ever sees. **[RATIFIED — read directly, 2026-08-01.]**

Two consequences follow, and they set this document's own boundaries:

1. **This decision names what an existing mechanism was always for.** It is not a redirection of the product. Every claim in Sections 2–5 below is checked against code or against an already-approved document, never asserted.
2. **Everything beyond that one mechanism is aspiration, not fact.** The four-step first-offer ladder that would make PIOS a genuine *network* for entrepreneurs (`PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Правило 5) is an approved Product Decision with **no implementation at all** — steps 2, 3 and 4 do not exist, and step 1 occurs only incidentally, because the passenger's URL happens to carry the driver's id. This document does not pretend otherwise (Section 8).

## 1. The Decision

**[DERIVED — this is the decision this document makes.]**

**PIOS is digital infrastructure that an independent entrepreneur operates for their own business.** It is not an application that dispatches rides on the platform's own behalf and lets drivers work inside it.

Stated as the distinction that has to survive every future scoping argument:

- The entrepreneur's business — their clients, their price, their working hours, their reputation, their income — **is theirs**. PIOS holds a record of some of it and provides tools to run it. PIOS is not a party to it.
- PIOS's own product is the **infrastructure**: the personal link, the order that arrives without a phone call, the lifecycle both sides can see, and — in future stages — the record of the entrepreneur's own work.
- **Taxi is the first industry, not the product.** The mechanism being validated is "an independent operator keeps and grows their own client flow through a tool they control." Nothing in this decision is specific to transportation, and no future capability may be justified on the grounds that "taxi apps do it."

This is consistent with, and is the product-level statement of, what the Constitution already establishes as law: Driver Ownership and Driver Independence (PROJECT_CONSTITUTION.md Sections 3, 18), Relationship Protection (Sections 3, 18), and Section 20's *"A driver remains an independent entrepreneur, never an employee of the platform."* **[RATIFIED — those sources; the synthesis into a named product model is [DERIVED].]**

## 2. How This Differs From an Aggregator — Specifically, Against This Codebase

**[RATIFIED where a file or line is cited; [DERIVED] where the contrast is drawn.]** Generic "platform not aggregator" language is deliberately avoided. Each row below is a difference that is true of the code committed to this repository on 2026-08-01, or an explicit absence that is equally load-bearing.

### 2.1 Demand reaches a named person, not a pool

An aggregator's defining act is that a request enters the platform's pool first, and the platform decides who receives it. PIOS's committed path does the opposite: the order is proposed to exactly the driver whose link the passenger used (`RideRequest.tsx` line 272), and `Proposal.propose` (`backend/dispatch/.../domain/Proposal.kt` lines 125–140) contains **one** check — that no other proposal for the same order is already `OPEN`. There is no candidate pool, no ranking, no selection step, and no platform decision anywhere in that path. **[RATIFIED.]**

The absence matters as much as the presence: PIOS has no assignment algorithm at any level of documentation, deliberately (ADR-002; ADR-034; PROJECT_CONSTITUTION.md Section 26). A platform with no algorithm cannot be an aggregator, because algorithmic allocation of demand *is* the aggregator's product.

### 2.2 The passenger knows whose business they are dealing with, from the first screen

`PassengerLanding.tsx` lines 177–185: an avatar bearing the driver's initial, *«Ваш водитель — Артур»*, and the headline *«👋 Вас пригласил Артур»*. `invitationSource.ts` lines 49–51 goes further — a driver with **no** `displayName` resolves to `not-found`, because *"Passenger Landing cannot introduce someone by a name it does not have."* An aggregator anonymizes the operator until after allocation. PIOS structurally cannot: the driver's name is the precondition for the link resolving at all. **[RATIFIED.]**

### 2.3 The entrepreneur states their own price; PIOS produces none

ADR-042 Decision item 1: *"PIOS records a stated price. It calculates nothing."* R4.1: no computation, derivation, estimate, suggestion, default or pre-fill; *"absent input means `null`, not zero."* In code: `Proposal.statedPrice` (`Proposal.kt` line 57) is set only inside `accept(...)` (lines 74, 79), and `DriverHome.tsx` `acceptRequestInit` (lines 440–449) sends **no request body at all** when the driver leaves the field blank. Price-setting is the second thing an aggregator owns; PIOS is prohibited from owning it. **[RATIFIED.]**

### 2.4 Money never passes through PIOS, and never buys position

`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33 and 46: *"payment for the ride occurs directly between the requester and the fulfilling driver, outside PIOS"*; *"no PIOS payment custody."* `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8: payment to PIOS *"should not secretly purchase higher priority … This constraint holds independent of which commercial model is eventually ratified."* Payments remains ratified-but-unscaffolded (`MODULE_STRUCTURE.md`; `PRODUCT_BASELINE_V2.md` line 164). **[RATIFIED.]** Section 6 below is bound by this and says so.

### 2.5 «Оплата следует за трудом» — the entrepreneur is paid for work, not for position in a hierarchy

`PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3: *«Оплата следует за трудом. Кто фактически выполнил поездку — получает оплату. Пригласивший человека не получает оплату автоматически только за факт приглашения.»* And Принцип 1: *«PIOS не владеет клиентами и не создаёт модель собственности клиента»*, with Принцип 2 establishing that every participant — including an invited passenger — may build their own network in turn (`Артур → Регина → Мама`). **[RATIFIED.]**

This is the sharpest single difference from both an aggregator *and* from a referral/MLM structure, and it is what makes "entrepreneur" the accurate word rather than "affiliate": the model pays for performed work, and recognition of a connection confers advantage at the moment of first offer only, never an annuity and never ownership (Правило 5's own уточнение of 2026-07-29, and Section 6's Инвариант права пассажира).

### 2.6 Reference, not ownership — the model is enforced structurally, not by policy

The rule (ADR-005, ADR-019; first established for `network-management` in ADR-037): a module may hold a plain string identifier pointing at another module's entity — never a foreign key, never a copy of that module's data, never a server-to-server call on a write path. In code, `backend/dispatch/.../domain/OrderReference.kt` lines 3–10 states it directly: Dispatch *"does not own or import Order Management's Order type; it holds only the identity value it needs … Dispatch does not verify that this order exists or is submitted."* **[RATIFIED.]**

The product consequence, and the reason this technical rule belongs in a Product Decision: **PIOS's architecture already makes client capture difficult by construction.** No module holds a joined, platform-owned graph of "this driver's customers." The passenger↔driver connection recorded at `PassengerLanding.tsx` lines 136–145 (`POST /v1/connections`) is a reference pair, deliberately non-blocking and invisible on failure. `network-management` — which owns Person, Connection and Invitation — remains isolated in both directions: nothing calls it and it calls nothing (ADR-037; confirmed in the 2026-08-01 review). An aggregator's core asset is exactly the graph PIOS has structurally declined to assemble.

### 2.7 What is *not* yet a difference, stated so this section is not read as complete

**[RATIFIED absence.]** Three claims a reader might expect here are **not** supported today and are excluded on purpose:

- PIOS does **not** route around a busy driver. There is no fallback of any kind (Section 8; Правило 5 unimplemented).
- PIOS does **not** respect a driver's declared working state. `Proposal.propose` performs no availability check, and Dispatch's own `driver_availability` projection is written and never read.
- PIOS holds **no** record the entrepreneur can review after a shift. No history, receipt, or earnings surface exists anywhere (ADR-042 R7.4, recorded there as a *"verified negative"*).

Each is addressed by `PIOS_MVP_EVOLUTION_PLAN.md`, not by this document.

## 3. What This Decision Establishes as Binding

**[DERIVED]** — each is a checkable constraint on future scope, not a sentiment:

1. **The entrepreneur's client flow is theirs.** No PIOS capability may be designed on the premise that PIOS owns, brokers, or may reassign it. This restates Relationship Protection (PROJECT_CONSTITUTION.md Sections 3, 18) and Принцип 1, and applies it forward.
2. **PIOS may never become the party that decides the entrepreneur's price.** ADR-042 Decision item 1 and R4.1 already forbid this; this decision makes it a product identity claim, not only a sprint constraint.
3. **Every capability must be justifiable as a tool the entrepreneur runs, not as a platform behaviour they are subject to.** A feature that only makes sense if PIOS is allocating demand on its own behalf is out of model, whatever its other merits.
4. **Recognition of a connection confers advantage, never entitlement.** Правило 5's уточнение and Section 6's Инвариант права пассажира are binding on any future implementation: *«Пассажир имеет право выбрать другого исполнителя независимо от существующей связи.»*
5. **Nothing about this model may be inferred from the taxi domain.** Where transportation-specific behaviour is wanted, it needs its own justification.

## 4. What This Decision Does Not Change

**[RATIFIED, restated so no reader mistakes a naming act for a scope change]:**

- **No bounded context, ownership boundary, or module changes.** All six contexts keep their current ownership; `INTERFACE_CONTRACTS.md` Section 3's justified relationships are unchanged; `network-management` and `identity` remain isolated.
- **No ADR is amended, superseded, or created by this document.**
- **No sprint is authorized.** This decision does not put anything into scope. Scope is governed by `PIOS_PRODUCT_EVIDENCE.md`'s own gate, unchanged and in full force, including its 2026-08-01 rule that *«элемент объёма, не обеспеченный ни `E-NNN`, ни зарегистрированной гипотезой, в объём не входит»*.
- **Driver MVP v1.1 is unchanged.** As ratified in ADR-042 Amendment 2026-08-01 (round 3) and `IMPLEMENTATION_PLAN_DRIVER_MVP_V1_1.md`, it remains exactly one change in one frontend file, on the basis of H3.

## 5. The Entrepreneur's Own Definition of Value

**[HYPOTHESIS]** — recorded as the claim to be tested, not asserted as fact.

The question this model must eventually survive is the one the 2026-08-01 review put in the entrepreneur's own voice: *«Почему я буду платить за PIOS каждый месяц?»* Under this model the answer cannot be "because PIOS gives me orders" — Section 2.4's standing constraint forbids payment from purchasing allocation, *independent of which commercial model is eventually ratified*. **So the model itself narrows the space of possible answers, and that narrowing is a finding, not a limitation:** whatever PIOS eventually charges for, it is a **tool**, never a **queue position**.

The candidate answer, recorded as a hypothesis and registered for testing as **H4** in `PIOS_PRODUCT_HYPOTHESES.md`:

> Предприниматель готов платить за PIOS, если платформа помогает ему сохранять и развивать собственный клиентский поток.

## 6. The Recurring Subscription Under This Model

**[HYPOTHESIS] in full. No price, tier, billing period, trial, or fee structure is set here, and none may be inferred from this section.**

What a subscription could legitimately be sold as, given everything already ratified above:

- **Retention of one's own client flow** — the personal link and the connection record that let an entrepreneur's existing clients reach them directly (Section 2.1, 2.2; Принцип 1).
- **Removal of coordination work** — the order that arrives with its details, and the lifecycle both parties watch, replacing the phone calls that would otherwise carry them (`RIDE_STATUS_LABEL`, `RideRequest.tsx` lines 57–63; the driver's `Прибыл`/`Начать поездку`/`Завершить поездку` chain, `DriverHome.tsx` lines 755–784).
- **A record of one's own work** — which does not exist yet, is the largest single gap in this answer, and is `PIOS_MVP_EVOLUTION_PLAN.md` MVP Stage 2's entire subject.

What a subscription may **never** be sold as, and this is [RATIFIED], not a preference:

- **Priority, position, or a larger share of shared demand.** `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8, binding *"independent of which commercial model is eventually ratified."*
- **Anything that makes PIOS a party to the entrepreneur's revenue.** Payment stays direct and outside PIOS (Section 2.4). Recording a stated amount is not payment custody and creates no settlement path (ADR-042 Decision item 1, R4.8).

## 7. The Known Open Dependency, Stated Rather Than Resolved

**This decision does not make the commercial model decided, and it does not clear the constraint that currently blocks its most obvious justification.** Both are recorded here as open dependencies of this decision, per PROJECT_CONSTITUTION.md Section 4's "No Hidden Assumptions."

**7.1 The commercial model remains genuinely undecided.** PROJECT_CONSTITUTION.md Section 19 states PIOS's identity *"does not include commission-based aggregation, but this is because the commercial model remains genuinely undecided, not because a commission model has been ratified against"*, and Section 26 lists *"The platform's commercial/commission model"* as an open product question requiring a Product Owner decision recorded as a Product Decision. Section 26 additionally records that *"Any driver incentive, contribution, or reward model"* is *"currently unsupported by any evidence, not merely undecided in detail."* **[RATIFIED — unchanged by this document.]** Section 6 above is therefore a hypothesis about *what would be worth paying for*, not a decision that anything is charged, or how much.

**7.2 The single number that would most directly justify a fee is currently prohibited.** ADR-042 **R4.3**: *"No comparison, ordering, or aggregation. Amounts are never compared across proposals, orders, drivers, or time; never sorted or ranked; never summed, averaged, or otherwise aggregated. **This is the single most important prohibition in this ADR**."*

The collision, stated precisely so it can be reconciled deliberately rather than drifted around:

- **R4.3's stated purpose is narrow.** ADR-042's own Consequences give the reason: *"Several priced proposals per order is structurally the shape of an auction"* — comparison **across competing proposals for one order** is what would turn stated amounts into a bidding mechanism, which is the `Negotiation` capability the Product Owner has excluded twice.
- **R4.3's stated wording is unrestricted.** "Across … drivers, or time … never summed" also catches a single entrepreneur's own retrospective total of their own past rides — an act that involves no competition, no second party, and no allocation decision, and which is exactly what a business tool exists to produce.
- **Consequence, undisguised:** under the constraints ratified today, PIOS can show an entrepreneur what they charged for one ride and can never show them what they earned this week. A business tool that cannot answer that is answering §5's question with its hands tied.

**This document neither amends R4.3 nor works around it.** ADR-042 is an ADR; only an ADR may amend it, and only after a Product Owner decision. What is recorded here is that **MVP Stage 2 of `PIOS_MVP_EVOLUTION_PLAN.md` has a hard prerequisite**: a Product Owner ruling on whether R4.3 should be given a scoped amendment distinguishing *cross-proposal comparison* (which must remain forbidden — it is the auction R4.3 exists to prevent) from *an entrepreneur's own retrospective total of their own completed work* (a different act with a different risk profile). Until that ruling exists, Stage 2 may be planned and may not be implemented. Carried as Unresolved Decision 2 below.

## 8. What Is True Today Versus What This Model Promises

**[RATIFIED — verified against committed code on 2026-08-01, recorded so this document is not later cited as evidence that the model is delivered.]**

| Element of the model | State today |
| --- | --- |
| Personal invitation link; order reaches the named driver | **Implemented.** `InvitationProvider.ts` 25–27; `RideRequest.tsx` 272 |
| Passenger sees whose business they are dealing with | **Implemented.** `PassengerLanding.tsx` 177–185; `invitationSource.ts` 49–51 |
| Entrepreneur states their own price; PIOS produces none | **Implemented.** `Proposal.kt` 57, 74, 79; `DriverHome.tsx` 440–449 |
| Payment direct, outside PIOS; no custody, no commission | **Implemented by absence.** Payments unscaffolded |
| Reference-not-ownership; no platform-owned client graph | **Implemented.** `OrderReference.kt` 3–10; ADR-037 isolation |
| Entrepreneur can declare when they are working | **Not implemented.** Label only; `Proposal.propose` performs no availability check; the `driver_availability` projection is written and never read |
| Order carries enough to work without a phone call | **Not implemented.** No pickup address on `Order`; `RideRequest.tsx` line 333 instructs the passenger that the driver will call |
| Both parties see the truth about the order's fate | **Not implemented.** Decline and lapse render to the passenger as *«✅ Водитель уведомлён о заказе»* (`RideRequest.tsx` 176–179) |
| Connection confers first-offer advantage (Правило 5) | **Not implemented.** Steps 2–4 absent; step 1 incidental. Approved Product Decision, zero code |
| Entrepreneur has a record of their own work | **Not implemented.** ADR-042 R7.4, verified negative; Open Question 9 open |
| Recurring commercial model | **Not decided.** Section 7.1 |

Six of eleven elements are aspiration. `PIOS_MVP_EVOLUTION_PLAN.md` sequences them; this document neither schedules nor authorizes any of them.

## Explicit Preservations

- **Driver Independence / Driver Ownership.** **[RATIFIED]** — PROJECT_CONSTITUTION.md Sections 3, 18, 20. Strengthened by naming, not altered.
- **Relationship Protection; no client-ownership model.** **[RATIFIED]** — Constitution Sections 3, 18; Принцип 1; `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1 and Part 7 ("The relationship does not imply ownership of either party").
- **No pay-to-win priority.** **[RATIFIED]** — `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8. Section 6 above is explicitly bound by it.
- **No payment custody, no commission engine, no dynamic pricing.** **[RATIFIED]** — `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33, 46, 52.
- **PIOS invents no pricing rule.** **[RATIFIED]** — ADR-002; `DOMAIN_MODEL.md` line 177; ADR-042 Decision item 1, R4.1–R4.4.
- **No hidden algorithmic decisions.** **[RATIFIED]** — Constitution Sections 3, 18. Unaffected: this model reduces, rather than expands, the set of decisions PIOS makes on anyone's behalf.
- **Reference, not ownership.** **[RATIFIED]** — ADR-005, ADR-019, ADR-037; `OrderReference.kt` 3–10.
- **The Evidence Gate is unchanged.** **[RATIFIED]** — `PIOS_PRODUCT_EVIDENCE.md`, including its 2026-08-01 append-only rule and its "одно основание на Sprint" boundary. This decision creates no exemption from it.

## Explicitly Out of Scope

None of the following is decided, designed, priced, or previewed here:

- Any price, fee, tier, billing period, trial, discount, or commission. Section 6 is a hypothesis about *what value would be sold*, never about *what is charged*.
- Any amendment to ADR-042 R4.3. Section 7.2 names the collision and routes it to a Product Owner ruling; it does not resolve it.
- Any Assignment Policy, candidate ordering, availability gating, or implementation of Правило 5. Those are ADR-034 Part 3 territory and need their own ADR.
- Any ride history, receipt, earnings, or statistics surface. ADR-042 round 3 excludes these by name and its Open Question 9 remains open.
- Any change to `Order`, `Proposal`, `Assignment`, any module boundary, any event payload, or any cross-module contract.
- Any expansion beyond taxi. The model is industry-agnostic by construction (Section 1); no second industry is scoped, scheduled, or implied.
- Any claim that the entrepreneur model is *delivered*. Section 8 records exactly how much of it is not.

## Files Changed

`docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` (new) is the only file this decision creates. No ADR, code, database, event, or API is created or modified.

**Deviation from convention, disclosed:** every preceding document in this class adds one traceability pointer to `docs/README.md`. That pointer is **not** added here, because the task authorizing this document restricted it to exactly three files. Adding the `docs/README.md` line remains outstanding and is recorded as Unresolved Decision 6.

## Unresolved Decisions

1. **The commercial model itself** — whether PIOS charges, what for, and how much (Section 7.1; PROJECT_CONSTITUTION.md Sections 19, 26). Unchanged and still open. This document narrows the space of legitimate answers; it selects none.
2. **Whether ADR-042 R4.3 receives a scoped amendment** distinguishing cross-proposal comparison from an entrepreneur's own retrospective total (Section 7.2). A hard prerequisite for `PIOS_MVP_EVOLUTION_PLAN.md` MVP Stage 2. Requires a Product Owner ruling, then an ADR.
3. **ADR-042 Open Question 9** — whether an entrepreneur needs to see previously stated amounts after a ride completes. Open; same Stage 2 prerequisite.
4. **Whether availability is meant to gate anything**, or remain a self-declaration with no consumer (Section 2.7). An ADR-034 Part 3 Assignment Policy question, not a UI one.
5. **When, and in what module, Правило 5 is implemented** (Section 8). The Product Decision is ratified; nothing else about it is.
6. **The `docs/README.md` traceability pointer** for this document (Files Changed, above), deferred by the authorizing task's file restriction.

## Conflicts

**One genuine conflict, named rather than resolved; the rest checked and clear.**

- **⚠ ADR-042 R4.3 versus this model's own value proposition (Section 7.2).** R4.3's unrestricted wording forbids the aggregation that a business tool for an entrepreneur most obviously needs. This document does not amend R4.3, does not reinterpret it, and does not design around it; it records the collision and routes it to a Product Owner ruling followed by an ADR. Until then R4.3 stands **in full**, and no implementation may aggregate any amount anywhere.
- **PROJECT_CONSTITUTION.md** — no conflict. Sections 3, 18, 20 and 25 are honored; this document adds detail within the scope Sections 1–17 already define, per Section 17, and amends nothing.
- **`PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md`** — no conflict. Принципы 1–4, Правило 5 and Section 6's Инвариант are reused verbatim and extended nowhere. Section 8 above records their unimplemented status without softening them.
- **`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`** — no conflict. Every Part 8 open question remains exactly as open; nothing here declares the relationship exclusive, mutually confirmed, or derivable from a ride log. Part 6's boundary is explicitly carried into `PIOS_MVP_EVOLUTION_PLAN.md` Stage 2 as a live constraint.
- **`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`** — no conflict. Its H2 (*"After experiencing recurring real value, will drivers actually pay enough for PIOS to support a viable business?"*, Section 5) is the same question H4 now registers in falsifiable, observable form. H4 does not supersede H2; it operationalizes it under this model.
- **`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`** — no conflict; Section 8's constraint is imported into Section 6 above as a hard boundary on any future subscription.
- **ADR-005 / ADR-019 / ADR-034 / ADR-037** — no conflict. No ownership moves, no forbidden input is admitted, no boundary changes, no module is touched.

## Impact Analysis

**Documents:** No existing document is modified by this one. `docs/PIOS_MVP_EVOLUTION_PLAN.md` and `docs/PIOS_PRODUCT_HYPOTHESES.md` (H4) are written in the same authorized pass and reference this decision; neither is amended *by* it.

**Code:** None. Every file cited above was read and quoted, never modified.

**Architecture:** None. No ADR is created, amended, or superseded.

**Future work enabled, not performed:** a Product Owner ruling on Unresolved Decisions 1–4 becomes possible on a documented basis rather than an unwritten premise. No sprint, ADR, or implementation task is authorized by this document, and the Evidence Gate applies to each future item unchanged.

## Recommended Next Step

Not an ADR and not a sprint. The most evidence-grounded next step is the pilot: run `PIOS_PILOT_REVIEW_PROTOCOL.md` against what already exists, so that `PIOS_PRODUCT_EVIDENCE.md` — empty on 2026-08-01 — acquires its first `E-NNN` entries, including entries bearing on **H4**. Every Unresolved Decision above is a question about what an entrepreneur actually values, and this project's own discipline (`PIOS_PRODUCT_HYPOTHESES.md`: *«Мы не изучаем, что люди говорят о PIOS. Мы изучаем, что они делают»*) says that question is answered by observation, not by further documentation.

## Traceability

| Section | Source |
| --- | --- |
| Central Finding | `frontend/src/identity/InvitationProvider.ts` 25–27; `frontend/src/pages/RideRequest/RideRequest.tsx` 272; `frontend/src/pages/PassengerLanding/PassengerLanding.tsx` 180–185 |
| 1. The Decision | Product Owner decision, 2026-08-01; PROJECT_CONSTITUTION.md Sections 3, 18, 20 |
| 2.1 Named person, not a pool | `Proposal.kt` 125–140; `RideRequest.tsx` 272; ADR-002; ADR-034; PROJECT_CONSTITUTION.md Section 26 |
| 2.2 Named operator | `PassengerLanding.tsx` 177–185; `invitationSource.ts` 49–51 |
| 2.3 Own price | ADR-042 Decision item 1, R4.1; `Proposal.kt` 57, 74, 79; `DriverHome.tsx` 440–449 |
| 2.4 No custody, no bought priority | `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` 33, 46; `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8 |
| 2.5 Оплата следует за трудом | `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принципы 1–3, Section 7 |
| 2.6 Reference, not ownership | ADR-005; ADR-019; ADR-037; `OrderReference.kt` 3–10; `PassengerLanding.tsx` 136–145 |
| 2.7 / 8. Verified absences | `Proposal.kt` 125–140; `Order.kt` 67–74; `RideRequest.tsx` 176–179, 333; ADR-042 R7.4 |
| 3. Binding constraints | PROJECT_CONSTITUTION.md Sections 3, 18, 25; `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Правило 5, Section 6 |
| 5–6. Value and subscription | `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8; `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 5 (H2); `PIOS_PRODUCT_HYPOTHESES.md` H4 |
| 7.1 Commercial model open | PROJECT_CONSTITUTION.md Sections 19, 26 |
| 7.2 R4.3 collision | ADR-042 R4.3 and its Consequences ("New"); ADR-042 Open Question 9 |

Where this document finds no evidence, it states so explicitly rather than filling the gap; resolution of any Unresolved Decision (above) requires a further Product Owner decision, per PROJECT_CONSTITUTION.md Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
