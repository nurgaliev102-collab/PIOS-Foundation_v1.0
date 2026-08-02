# PIOS Product Strategy v1.0

Status: **Consolidation, not a decision.** This document makes the decisions already ratified elsewhere legible in one place. It introduces **no new product claim, no new constraint, and no new resolution.** Every statement below is a faithful summary of, and citation to, one of four sources.

**Synchronized 2026-08-02.** `PIOS_PRODUCT_HYPOTHESES.md` (H4) and `PIOS_MVP_EVOLUTION_PLAN.md` were each corrected on 2026-08-01 after this document's own consolidation pass surfaced the gaps. This revision brings Sections 2.2, 3, 5.2, and 6.2 back in line with those corrections — in particular, withdrawing a Stage 2 summary line that could be read as permission to display ride price or history, which ADR-042 forbids by name. No new decision is made by this synchronization; it only removes drift between this document and its own sources.

- `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` — the ratified Product Decision (2026-08-01) naming what PIOS is for.
- `docs/PIOS_MVP_EVOLUTION_PLAN.md` — the ratified three-stage sequencing, with its prerequisites.
- `docs/PIOS_PRODUCT_HYPOTHESES.md` — H1–H4, in particular **H4**.
- The Final Product Review of 2026-08-01 ("Final Product Review — PIOS Driver MVP v1.1"), cited as **FPR**, plus the ADRs those documents themselves cite.

Where something is genuinely undecided, it appears in **Section 5 (Open Questions)** and nowhere else. If a reader finds a resolution asserted outside Section 5 that is not traceable to one of the sources above, that is a defect in this document, not a decision.

This document sits **below** `PROJECT_CONSTITUTION.md` and below every Product Decision it summarizes (`PROJECT_CONSTITUTION.md` Section 5). It may not be cited to override any of them.

---

## 1. Product Identity

### 1.1 Кем является PIOS

**PIOS — это цифровая инфраструктура, которой независимый предприниматель пользуется для собственного бизнеса.** Not an application that dispatches rides on the platform's own behalf and lets drivers work inside it.

Summarized from `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 1, which states three things:

- The entrepreneur's business — their clients, their price, their working hours, their income — **is theirs**. PIOS holds a record of some of it and provides tools to run it; PIOS is not a party to it.
- PIOS's own product is the **infrastructure**: the personal link, the order that arrives without a phone call, the lifecycle both sides can see, and — in future stages — the record of the entrepreneur's own work.
- **Taxi is the first industry, not the product.** No future capability may be justified on the grounds that "taxi apps do it."

That Section grounds itself in already-ratified law rather than asserting a new one: Driver Ownership / Driver Independence and Relationship Protection (`PROJECT_CONSTITUTION.md` Sections 3, 18) and Section 20's *"A driver remains an independent entrepreneur, never an employee of the platform."*

### 1.2 Кем PIOS НЕ является

`PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 2 draws the aggregator contrast **against this codebase specifically**, not in general terms. Summarized, with its own citations preserved:

| PIOS is not… | Because, in committed code |
| --- | --- |
| **an allocator of demand** | The order is proposed to exactly the driver whose link the passenger used (`RideRequest.tsx` line 272). `Proposal.propose` (`Proposal.kt` lines 125–140) has **one** check — no other `OPEN` proposal for the same order. No pool, no ranking, no selection step. (§2.1) |
| **an anonymizer of the operator** | The passenger sees the driver by name on the first screen (`PassengerLanding.tsx` lines 177–185), and a driver with no `displayName` resolves to `not-found` — the name is a precondition for the link working at all (`invitationSource.ts` lines 49–51). (§2.2) |
| **the party that sets the price** | ADR-042 Decision item 1: *"PIOS records a stated price. It calculates nothing."* R4.1 forbids any computation, estimate, suggestion, default or pre-fill; a blank field sends no request body at all (`DriverHome.tsx` lines 440–449). (§2.3) |
| **a holder of money or a taker of commission** | Payment occurs directly between requester and driver, outside PIOS; no custody (`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33, 46). Payments remains unscaffolded. (§2.4) |
| **an owner of the client relationship** | Reference, not ownership (ADR-005, ADR-019, ADR-037). `OrderReference.kt` lines 3–10: Dispatch *"does not own or import Order Management's Order type; it holds only the identity value it needs."* No module holds a joined, platform-owned graph of "this driver's customers." (§2.6) |
| **a referral or annuity structure** | «Оплата следует за трудом» — `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3: *«Кто фактически выполнил поездку — получает оплату. Пригласивший человека не получает оплату автоматически только за факт приглашения.»* (§2.5) |

**Stated as that document states it, and not softened here:** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 8 records that **six of eleven elements of this model are aspiration, not implementation** — availability, a complete order, honest order statuses, the first-offer ladder, a record of the entrepreneur's work, and the commercial model itself. Section 2.7 of the same document lists three of these as explicit "not yet a difference" entries so that Section 2 is never read as a description of a finished product.

---

## 2. Entrepreneur Value Proposition

### 2.1 Почему предприниматель пользуется PIOS — what is true today

From `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 2 and its Section 8 table (the "Implemented" rows only):

- **Demand reaches him by name.** His own client's order goes to *him*, not into a pool (§2.1).
- **Without a phone call.** The order arrives on his screen by itself — the driver's list polls every 3 seconds and has no manual refresh button (`DriverHome.tsx`) — carrying the passenger's name, destination and time.
- **The client knows whose business they are dealing with** (§2.2).
- **He names his own price; PIOS produces none** (§2.3).
- **He is paid directly, for work performed** (§2.4, §2.5).
- **Nothing assembles his clients into a platform asset** (§2.6).

The FPR identified the moment of understanding precisely: it is when an order from his own client appears on his phone without a call. That already works.

### 2.2 Почему предприниматель платит подписку — **hypothesis, not a settled answer**

**This subsection is [HYPOTHESIS] in full.** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 6 is explicitly marked *"[HYPOTHESIS] in full. No price, tier, billing period, trial, or fee structure is set here, and none may be inferred from this section."* This document does not upgrade it.

**The hard boundary comes first, because it is [RATIFIED] and it narrows everything else.** `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8: payment to PIOS may never purchase priority, *"independent of which commercial model is eventually ratified."* Therefore, as `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 5 puts it: **whatever PIOS eventually charges for, it is a tool, never a queue position.** A subscription may also never make PIOS a party to the entrepreneur's revenue (§2.4, ADR-042 R4.8).

**Within that boundary, the candidate value** (Section 6, all three [HYPOTHESIS]):

1. **Retention of one's own client flow** — the personal link and the connection record.
2. **Removal of coordination work** — the order that arrives with its details, and the lifecycle both parties watch.
3. **A record of one's own work** — *which does not exist yet*, is named there as the largest single gap in this answer, and is MVP Stage 2's entire subject.

**The claim being tested, registered as H4** (`PIOS_PRODUCT_HYPOTHESES.md`, 2026-08-01):

> Предприниматель готов платить за PIOS, если платформа помогает ему сохранять и развивать собственный клиентский поток.

**H4 was corrected 2026-08-01.** Its first version tested retention (link sent, returned next day) — signals nearly identical to H1's, capable of confirming "willing to pay" without a single observation about money. The corrected version tests four independent signals instead: intent to continue unprompted, connecting a real client, a direct answer to a willingness-to-pay question (weighted no higher than the other three, per this document's own "watch behavior, not statements" discipline), and a substantive answer to the existing "if PIOS disappeared" value-check question — all four required together, not any one alone.

**H4's own status is «Ожидает проверки», and its «Доказательства» field is empty.** `PIOS_PRODUCT_EVIDENCE.md`'s journal is empty as of 2026-08-01. Per that document's own 2026-08-01 rule, a hypothesis **does not become confirmed by a Sprint being run for it** — *«Спринт — это инструмент проверки, а не результат проверки»*. H4 restates, in falsifiable form, the same question `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 5 raised as **H2** (*"will drivers actually pay enough for PIOS to support a viable business?"*) and never observed.

**Nothing in this section may be cited as evidence that anyone will pay for PIOS.** No one has been asked, no one has been observed, and no price exists.

---

## 3. MVP Evolution

Summarized from `PIOS_MVP_EVOLUTION_PLAN.md`. **That plan begins after Driver MVP v1.1** and does not alter it. Its own "Evidence Gate Status, Stated Up Front" applies to every item below: **no item in any stage has its own `E-NNN` or its own registered hypothesis today**, so each is a planned candidate, not an authorized Sprint (`PIOS_PRODUCT_EVIDENCE.md`: *«элемент объёма, не обеспеченный ни `E-NNN`, ни зарегистрированной гипотезой, в объём не входит»*).

### Stage 1 — Working Entrepreneur Tool

Closes the gaps between what PIOS claims and what it does. Four items:

| Item | Closes | Prerequisite |
| --- | --- | --- |
| **1.1 `pickupAddress`** | FPR Gap **C1** — no pickup location on `Order`; `RideRequest.tsx` line 333 tells the passenger the driver will call instead | **No ADR** — additive, `destination`-shaped (`Order.kt` lines 32–45). But a real backend diff (`Order.kt`, `SubmitOrderRequest.kt`, `OrderResponse.kt`, `OrderQueryController.kt`, `OrderLifecycleApplicationService`, `V8` migration) — its own sprint, not an addition to v1.1 |
| **1.2 Real availability** | The verified finding that `Proposal.propose` performs no availability check and Dispatch's `driver_availability` projection is written and never read | **⛔ ADR required — ADR-034 Part 3 (Assignment Policy).** May be *planned* here; may not be *built* until that ADR exists |
| **1.3 Correct order statuses** | FPR Gap **C2** — declined/lapsed renders to the passenger as the same green *«✅ Водитель уведомлён о заказе»* (`RideRequest.tsx` lines 176–179) | Rendering the truth needs no ADR; *acting* on it is order-lifecycle work behind ADR-041's gate and ADR-042 Open Question 10 |
| **1.4 Fixing false promises** | FPR Gap **C3** — `PassengerLanding.tsx` claims an availability gate and a fallback driver, neither of which exists | None architecturally; the choice between "correct the copy" and "build the promise" (= Stage 3) is a Product Owner call |

**⚠ Conflict carried, not resolved.** `PIOS_MVP_EVOLUTION_PLAN.md` names it explicitly: placing 1.2 in Stage 1 sequences it **before** the pilot, while `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.3 concludes *«не чинить до пилота. Основания для спринта сегодня нет»* and §4.3.3 requires the driver's status to reach Артур as `UNAVAILABLE` on purpose, so his reaction becomes the first observation. Both are ratified; the plan does not pick a winner and neither does this document.

### Stage 2 — Business Visibility

The entrepreneur's own record of their work — the largest unimplemented element of the model.

**⛔ Blocked in full — corrected 2026-08-01/02. Not a partial permission.** `PIOS_MVP_EVOLUTION_PLAN.md`'s Stage 2 withdrew a summary line from its own earlier draft — *"Stage 2 may show a single ride's price as plain text"* — because, even caveated, it could be read as authorizing a display ADR-042 round 3 excludes **by name** (*"any history, receipt or earnings screen"*), not only by its aggregation rule. **This document does not restate that withdrawn line, and no reader may infer it from anything below.** Until ADR-042 is separately revised, nothing in Stage 2 authorizes building, in any form: ride history; ride price shown anywhere beyond the single acceptance-time exchange already scoped to Driver MVP v1.1; or any other financial representation — a count, a total, an average, or a tally — of the entrepreneur's own completed work.

**Two hard prerequisites, per the plan:**

1. **ADR-042 Open Question 9 is open** — *does a driver need to see previously stated amounts after a ride completes?* Stage 2 cannot be designed against a question the Product Owner has not yet answered.
2. **ADR-042 R4.3 blocks aggregation of any kind.** R4.3: *"No comparison, ordering, or aggregation … never summed, averaged, or otherwise aggregated. This is the single most important prohibition in this ADR."* Its *purpose* is narrow (preventing cross-proposal auction behaviour on a single order); its *wording* is unrestricted and also catches an entrepreneur's own retrospective total. Stage 2 requires a Product Owner ruling, then a **scoped, ratified** ADR-042 amendment distinguishing the two — not merely a proposal.

**A third, separate constraint — also corrected.** `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6: the relationship *"is not defined as, or derived from, an accumulated count or log of past rides."* A distinct-passenger count is mechanically exactly that, whatever it is labeled. The plan's earlier "operational tally" framing — presenting such a count as work done rather than as the relationship — is **withdrawn**: a framing choice inside the plan does not satisfy Part 6. **Any client relationship metric requires its own, separate Product Decision**, not a wording choice in this document or in the evolution plan.

**One implementation fact for any future Developer task:** `OrderQueryController.listOrders` returns every order in the system, unfiltered, in a product with no authentication (ADR-042 Open Question 8, still open).

### Stage 3 — Entrepreneur Network

Network effects, reciprocity, fair distribution.

**This is the one stage whose Product Decision already exists and is ratified**: `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Правило 5 — the four-step first-offer ladder (inviting driver → drivers with quality history → trust network → general queue) — under Section 7's principle *«Связь даёт преимущество. Качество подтверждает право. Оплата следует за трудом.»* **The gap is purely implementation.**

**⛔ Blocked. Per `PIOS_MVP_EVOLUTION_PLAN.md` Stage 3, requiring two ADRs, not one — corrected 2026-08-01.** Step 1 happens today only incidentally, because the passenger's URL carries the inviting driver's id — not because PIOS implements a preference. **Steps 2–4 do not exist in any form.** `network-management` (ADR-037) is fully built and fully isolated: nothing calls it, and it calls nothing.

1. **An ADR on the rules of distribution.** Правило 5 is an ordering over candidate drivers — Assignment Policy territory (ADR-034 Part 3). Step 2 of the ladder additionally needs a quality signal that exists in no aggregate today and that ADR-034 Part 2 currently lists among forbidden inputs.
2. **An ADR on cross-module network interaction.** Activating the ladder means Dispatch consulting a module it has never consulted — a genuine new cross-module relationship, which under ADR-005/ADR-019's "reference, not ownership" test **requires its own ADR before any code is written.**

Neither ADR exists; this document does not write either or presume its content.

Two invariants bind this stage in advance, restated from the plan: connection confers **advantage at the moment of first offer only**, never an annuity and never ownership; and the Инвариант права пассажира — *«Пассажир имеет право выбрать другого исполнителя независимо от существующей связи»* — so fair distribution here means an ordered offer sequence, never an exclusive lock.

### Sequencing

Stage 1 → Stage 2 → Stage 3 is the intended order. Per the plan's own "Sequencing, Stated Plainly": each stage's items are **independently** gated, and satisfying one stage's prerequisites does not satisfy another's. Nothing authorizes skipping a prerequisite because an earlier stage shipped.

---

## 4. Product Constraints

Five binding constraints. Each is sourced; none is asserted freestanding.

### 4.1 Мы не владеем клиентом

**Source:** `PROJECT_CONSTITUTION.md` Sections 3 and 18 (Relationship Protection — durable relationships are *"never owned or captured by the platform, and never a byproduct of any single transaction"*), and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1 (**[RATIFIED — it is NOT ownership]**) and Part 7 (*"The relationship does not imply ownership of either party"*; *"The relationship exists independent of any single order"*). Reinforced by `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 1: *«PIOS не владеет клиентами и не создаёт модель собственности клиента»*, and Принцип 2, under which every participant — including an invited passenger — may build their own network in turn.

**Structurally enforced, not merely stated:** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.6 — reference-not-ownership (ADR-005, ADR-019, ADR-037) means no module holds a joined, platform-owned client graph.

### 4.2 Мы не берём комиссию и не держим деньги

**Source:** `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` line 33 — *"payment for the ride occurs directly between the requester and the fulfilling driver, outside PIOS"*; line 46 — *"Direct payment between requester and fulfilling driver; no PIOS payment custody"*; line 52 excludes *"dynamic pricing; PIOS payment custody or a commission engine."*

Reinforced by ADR-042 Decision item 5 (a stated price is **not** a Payment Record; Payments' ownership is untouched and Payments stays unscaffolded) and R4.8 (*"No settlement"* — no custody, no reconciliation, no dispute path, no commission). And by `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8: whatever the commercial model becomes, payment may never purchase priority.

### 4.3 Мы не являемся диспетчером

**Source:** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.1 — demand reaches a named person, not a pool. In code: `RideRequest.tsx` line 272 proposes to the driver from the passenger's own link; `Proposal.propose` (`Proposal.kt` lines 125–140) contains one check, unrelated to driver selection. There is **no candidate pool, no ranking, no selection step, and no platform allocation decision anywhere in that path.**

The absence is itself ratified: PIOS has no assignment algorithm at any level of documentation, deliberately (ADR-002; ADR-034; `PROJECT_CONSTITUTION.md` Section 26). §2.1 states the consequence — *a platform with no algorithm cannot be an aggregator, because algorithmic allocation of demand is the aggregator's product.*

**Bounded honestly:** this constraint describes what PIOS does **not** do. It does not claim PIOS currently implements a *better* distribution — Правило 5 is unimplemented (Section 3, Stage 3), and `PassengerLanding.tsx` currently promises a fallback that does not exist (Stage 1 item 1.4).

### 4.4 Мы не создаём финансовую систему на первом этапе

**Source:** ADR-042 in its entirety — the minimal stated-price model. Decision item 1: *"PIOS records a **stated** price. It calculates nothing … It does not compute, derive, validate, suggest, estimate, or settle it. No tariff, base fare, distance rate, time rate, surge factor, discount, commission, split, rounding rule, or currency conversion is introduced, implied, or reserved."*

R4's eight concrete prohibitions make this checkable rather than aspirational: no production of a value (R4.1); no validation beyond non-blank and a length limit (R4.2); no comparison, ordering or aggregation (R4.3); no currency semantics (R4.4); no influence on selection (R4.5); no propagation into any event, outbox record, cross-module contract, or other module's database (R4.6); no PIOS behaviour depends on it (R4.7); no settlement (R4.8).

Underneath: ADR-002 and `DOMAIN_MODEL.md` line 177 place *"any pricing, commission, or regulatory rule"* outside architectural scope entirely.

### 4.5 Оплата следует за трудом

**Source:** `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3 — *«Кто фактически выполнил поездку — получает оплату. Пригласивший человека не получает оплату автоматически только за факт приглашения»* — and Section 7's principle in full: *«Связь даёт преимущество. Качество подтверждает право. Оплата следует за трудом.»*

Carried into `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.5 as the sharpest distinction from both an aggregator and a referral structure: the model pays for **performed work**, and recognition of a connection confers advantage at the moment of first offer only — never an annuity, never ownership (Правило 5's own уточнение of 2026-07-29, and Section 6's Инвариант права пассажира).

---

## 5. Open Questions

Five, each with what is actually open and where it is tracked. **Nothing here is resolved by this document.**

### 5.1 Модель сети

**Open:** how the network actually distributes demand. `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Правило 5 is a **ratified** Product Decision, but steps 2 ("водители с историей качественного обслуживания"), 3 ("подходящие водители доверительной сети") and 4 ("общая очередь PIOS") **do not exist in any form**, and step 1 occurs only incidentally.

**Tracked in:** `PIOS_MVP_EVOLUTION_PLAN.md` Stage 3 and its Unresolved Decision 5 — the **cross-module ADR** required before Dispatch may consult `network-management` (ADR-037, isolated in both directions; ADR-005/ADR-019 reference-not-ownership). Additionally: step 2 needs a quality signal that exists in no aggregate and that ADR-034 Part 2 currently lists among forbidden inputs; reciprocity is deliberately **not** a Fundamental Law (`PROJECT_CONSTITUTION.md` Section 18, carried as an open question in Section 26; ADR-034 Part 4 — *"none is approved, previewed, or ranked"*).

**Also open:** no hypothesis has been registered for it. `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md`'s own «Проверка существующих документов» table already identified the needed one — whether a passenger will send an order to a driver **other than** their first inviter when that inviter does not respond — and deliberately did not add it.

### 5.2 Клиентские отношения

**Open:** what a Personal Client Relationship actually *is*, mechanically. `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 3 records that it has **no ratified lifecycle at all** — creation, confirmation, suspension, termination and expiration are each **[REQUIRES VALIDATION]** — and Part 8 lists eight open questions, including whether it can be exclusive, whether it requires mutual confirmation, and whether it carries any weight in Dispatch's eventual decision.

**Tracked in:** that document's Part 8; `PROJECT_CONSTITUTION.md` Section 26; `PIOS_MVP_EVOLUTION_PLAN.md` Stage 3 (two of Правило 5's dependencies sit in Part 8).

**Plus the Part 6 tension with Stage 2**, carried live and unresolved: Part 6 states the relationship *"is not defined as, or derived from, an accumulated count or log of past rides."* A distinct-passenger count is mechanically exactly that, whatever it is labeled — the FPR checked and found **no document in this repository contains or requires the string «Разных пассажиров обслужено»**. Relabeling does not satisfy Part 6, which constrains substance. **Corrected 2026-08-01:** the evolution plan no longer proposes a framing to route around Part 6 — any client relationship metric requires its own, separate Product Decision, not a wording choice inside a plan document.

### 5.3 Финансовая аналитика

**Open:** whether PIOS may ever total an entrepreneur's own earnings.

**The collision, as both prior documents state it identically:** ADR-042 **R4.3**'s stated *purpose* is narrow — ADR-042's own Consequences give it: *"Several priced proposals per order is structurally the shape of an auction"* — but its *wording* (*"across … drivers, or time … never summed"*) also catches a single entrepreneur's retrospective total of their own completed rides, an act with no competition, no second party and no allocation decision. Consequence, stated in `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §7.2 without disguise: **PIOS can show what was charged for one ride and can never show what was earned this week.**

**What is required:** a Product Owner ruling, then a **scoped ADR-042 amendment** distinguishing *cross-proposal comparison* (must remain forbidden — it is the auction R4.3 exists to prevent) from *an entrepreneur's own retrospective total*. Only an ADR may amend an ADR.

**Tracked in:** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Unresolved Decision 2 and §7.2; `PIOS_MVP_EVOLUTION_PLAN.md` Unresolved Decision 2. Coupled to **ADR-042 Open Question 9** (Unresolved Decision 3 in both), which is separately open.

### 5.4 Отмена заказов

**Open:** what a passenger may do about an order after it has been accepted.

**«Отказаться» is not implemented, deliberately.** ADR-042 **R10** forbids any passenger-side control of any kind next to the amount — no button, no confirm, no accept, no refusal, no counter-offer, no comment box — because the ruling is *«пассажир видит предложенную цену; решение — вне PIOS, до появления отдельного механизма»*. R10 also explains why it is not a small addition: a passenger-side refusal is an **order-lifecycle** feature that would have to decide what happens to an `Assignment` that already exists, what `OrderStatus` results, who may trigger it and until when — and ADR-041 placed a standing gate in front of exactly this class of change.

**Preserved as a named future Product Decision: «Управление жизненным циклом заказа»** — ADR-042 **Open Question 10**, *"Not started, not scheduled, not scoped."* **ADR-043 is not created and its number is not reserved**; the next ADR written takes 043, whatever its subject.

**Related and also open:** `PIOS_MVP_EVOLUTION_PLAN.md` Stage 1 item 1.3 distinguishes *rendering* the truth about a declined or lapsed proposal (no ADR needed) from *acting* on it (blocked behind this same Product Decision).

**Standing instruction, recorded in both ADR-042 R11.3.3 and H3's own note:** if a pilot passenger sees a price and tries to refuse, finding no way to — **that is the observation, not a defect.** It belongs in `PIOS_PRODUCT_EVIDENCE.md` and is the first real input to this Product Decision. It must not be patched mid-sprint by adding the control R10 forbids.

### 5.5 Подписка

**Open:** whether PIOS charges, what for, and how much.

`PROJECT_CONSTITUTION.md` Section 19 states PIOS's identity *"does not include commission-based aggregation, but this is because the commercial model remains genuinely undecided, not because a commission model has been ratified against."* Section 26 lists *"The platform's commercial/commission model"* as an open product question requiring a Product Owner decision recorded as a Product Decision, and separately records that *"Any driver incentive, contribution, or reward model"* is *"currently unsupported by any evidence, not merely undecided in detail."*

**H4 is registered and untested.** Status «Ожидает проверки»; «Доказательства» empty; the evidence journal empty as of 2026-08-01. Section 2.2 above is a hypothesis about what would be worth paying for — not a decision that anything is charged.

**The one thing that *is* settled, and it is a constraint rather than an answer:** whatever is eventually sold, it may never be priority or position (`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8, binding *"independent of which commercial model is eventually ratified"*), and it may never make PIOS a party to the entrepreneur's revenue (§4.2 above).

**Tracked in:** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 6, Section 7.1, Unresolved Decision 1; `PIOS_PRODUCT_HYPOTHESES.md` H4; `PROJECT_CONSTITUTION.md` Sections 19, 26.

---

## 6. Decision Gate

### 6.1 Готов ли PIOS Driver MVP v1.1 идти в разработку?

# **Да.**

**Driver MVP v1.1 — the single-file, passenger-visible-price change — is ready to hand to a Developer today.** This restates the FPR §6 verdict, and **nothing written since affects it**: `PIOS_MVP_EVOLUTION_PLAN.md` explicitly begins *after* v1.1 and states that *"nothing below adds to that scope, subtracts from it, or reorders it."*

Everything required is already in place, and the plan's central factual claim was verified directly against code:

| Requirement | Status |
| --- | --- |
| **ADR reference** | ADR-042 Amendment 2026-08-01 (round 3), R9/R10/R11 + Developer Scope. **No new ADR. ADR-043 must not be created and 043 must not be reserved.** |
| **Implementation plan** | `docs/IMPLEMENTATION_PLAN_DRIVER_MVP_V1_1.md` Parts 3 and 8 |
| **Evidence Gate basis** | **H3**, registered 2026-08-01 **before** the Sprint, under `PIOS_PRODUCT_EVIDENCE.md`'s ratified second door. Valid and sufficient |
| **Product Decision check** | Performed in ADR-042 R11.4 against `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` and `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` — no conflict; the FPR re-checked and concurred |
| **Scope** | Exactly two frontend files: `frontend/src/pages/RideRequest/RideRequest.tsx`, and `RideRequest.module.css` only if a style is genuinely needed |
| **Backend premise, verified** | `ProposalResponse.kt` line 25 carries `statedPrice`; `ProposalController.kt` lines 182–185 route **both** query branches through the same `toResponse()` at lines 192–193. The value is already on the wire |
| **Definition of Done** | Ten items, written, including the reload check and `git diff --stat backend/` being empty |

**Binding constraints the Developer must satisfy** (from ADR-042 round-3 Developer Scope and the implementation plan §3.1): take `statedPrice` from the same item already matched as `ACCEPTED` — no aggregation, no "best", no comparison (**R4.3**); render verbatim — no parsing, no currency symbol, no rounding, no formatting, no placeholder when absent; **no control of any kind beside it** (**R10**); zero backend diff; the value must survive a full page reload.

**One disclosed, intended consequence, so it is not treated as a defect during the pilot:** the passenger will see a number they cannot act on inside PIOS. Per ADR-042 R11.3.3 and H3's own note — *«это наблюдение, а не дефект»* — it belongs in the evidence log, and must not be patched by adding a button.

### 6.2 Что блокирует каждую стадию Evolution Plan — a different question, answered separately

**"v1.1 is ready" does not mean "the evolution plan is ready."** These are distinct, and the answer to the second is **no, for every stage**.

**Universal blocker, all three stages.** `PIOS_MVP_EVOLUTION_PLAN.md`'s own "Evidence Gate Status": the journal is empty, and **no item in Stage 1, 2, or 3 has its own `E-NNN` or its own registered hypothesis.** Under `PIOS_PRODUCT_EVIDENCE.md`'s "одно основание на Sprint" rule, each item needs its own basis before it may be scheduled — H3 belongs to v1.1 and is scoped verbatim to price visibility; H4 is scoped to willingness-to-pay, not to any individual feature.

| Stage | Blockers, specifically |
| --- | --- |
| **Stage 1 — 1.1 `pickupAddress`** | Evidence Gate basis only. No ADR required. Needs its own sprint (real backend diff, `V8` migration) — cannot ride inside v1.1 |
| **Stage 1 — 1.2 Real availability** | **ADR required (ADR-034 Part 3, Assignment Policy)** + Evidence Gate basis + **the unresolved conflict with `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.3** (*«не чинить до пилота»*, and §4.3.3's deliberate `UNAVAILABLE` handoff). A toggle without gating leaves `PassengerLanding.tsx`'s claim just as false |
| **Stage 1 — 1.3 Correct statuses** | Rendering the truth: Evidence Gate basis only. Acting on it: blocked behind the Product Decision **«Управление жизненным циклом заказа»** (§5.4) and then its own ADR |
| **Stage 1 — 1.4 False promises** | Evidence Gate basis + a Product Owner choice between correcting the copy and building the promise (= Stage 3) |
| **Stage 2 — all items** | **Blocked in full, not partially** (corrected 2026-08-01/02 — no ride history, ride price, or financial representation of any kind is authorized). **ADR-042 Open Question 9 must be answered** + **R4.3 must receive a scoped, ratified amendment** (§5.3) + a **separate Product Decision on client relationship metrics** if any passenger count is included (§5.2) + Evidence Gate basis |
| **Stage 3 — all items** | **Two ADRs required, not one:** a distribution-rules ADR (ADR-034 Part 3, plus Part 2's quality-signal gap for step 2) and a cross-module ADR before Dispatch may consult `network-management` (ADR-005/019/037) + reciprocity remaining a non-law open question (Constitution §18/26) + Evidence Gate basis, including a hypothesis nobody has registered yet |

### 6.3 The single act that unblocks the most

**Run the pilot.** Both `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` and `PIOS_MVP_EVOLUTION_PLAN.md` reach the same recommended next step independently. `PIOS_PILOT_DRY_RUN_CHECKLIST.md` and `PIOS_PILOT_REVIEW_PROTOCOL.md` are both written and both target functionality that already exists; the dry-run's own steps §4.1.1 and §4.3.2 are designed to produce exactly the observations Stage 1 items 1.3 and 1.2 need. Every open question in Section 5 is ultimately a question about what an entrepreneur actually values, and this project's own discipline answers that by observation: *«Мы не изучаем, что люди говорят о PIOS. Мы изучаем, что они делают.»*

---

## Traceability

| Section | Source |
| --- | --- |
| 1. Product Identity | `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Sections 1, 2 (2.1–2.7), 8 |
| 2. Value Proposition | Same, Sections 2, 5, 6, 7.1; `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8; `PIOS_PRODUCT_HYPOTHESES.md` H4; `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 5 (H2) |
| 3. MVP Evolution | `PIOS_MVP_EVOLUTION_PLAN.md` Stages 1–3, Sequencing, Unresolved Decisions, Conflicts |
| 4. Product Constraints | `PROJECT_CONSTITUTION.md` Sections 3, 18, 26; `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Parts 1, 7; `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` 33, 46, 52; `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.1, §2.4–2.6; ADR-002; ADR-042 Decision items 1, 5, R4.1–R4.8; `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3, Section 7 |
| 5. Open Questions | `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Правило 5 and its own review table; `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Parts 3, 6, 8; ADR-042 R4.3, R10, Open Questions 8, 9, 10; ADR-034 Parts 2–4; ADR-037; ADR-041; `PROJECT_CONSTITUTION.md` Sections 18, 19, 26 |
| 6. Decision Gate | Final Product Review §6; ADR-042 round-3 Developer Scope and Definition of Done; `IMPLEMENTATION_PLAN_DRIVER_MVP_V1_1.md` Parts 3, 8; `ProposalResponse.kt` 25; `ProposalController.kt` 182–193; `PIOS_PRODUCT_EVIDENCE.md`; `PIOS_PILOT_DRY_RUN_CHECKLIST.md` §4.1, §4.3 |

This document creates no decision. Every resolution above is traceable to a source; everything not resolved is in Section 5. Resolution of any Section 5 item requires a Product Owner decision, per `PROJECT_CONSTITUTION.md` Section 7, before any lower-authority document — including a future ADR or implementation task — may act on it.
