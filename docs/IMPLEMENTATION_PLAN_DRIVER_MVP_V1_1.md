# Implementation Plan: Driver MVP v1.1 — Passenger-Visible Stated Price

Status: **Planning only — not an authorization to implement.** This document designs, at file granularity, the smallest software change that realizes the Product Decision the Product Owner ratified on 2026-08-01: *«пассажир видит предложенную цену; решение — вне PIOS, до появления отдельного механизма»* — the passenger sees the stated price; the decision happens outside PIOS, until a separate mechanism exists.

**Authorized by:** Product Decision (Product Owner, 2026-08-01), recorded in `docs/ADR/ADR-042-Stated-Ride-Price-Minimal-Model.md`, **Amendment 2026-08-01 (round 3)**, items **R9** (the passenger sees it), **R10** (PIOS offers no way to respond to it) and **R11** (this Sprint's basis).

**Basis for the Sprint:** hypothesis **H3** in `docs/PIOS_PRODUCT_HYPOTHESES.md`, registered 2026-08-01, *before* this Sprint — not an `E-NNN` evidence entry, under the append-only rule ratified the same day in `docs/PIOS_PRODUCT_EVIDENCE.md` ("Дополнение 2026-08-01 — зарегистрированная гипотеза как основание для Sprint"). This Sprint **produces** evidence rather than consuming it.

**New ADR required: none.** R9's own analysis is why — this changes no boundary, creates no cross-module contract, moves no ownership, adds no field and touches no backend file. The architectural decision was already taken in ADR-042; round 3 amends it in place rather than opening a new one. **`ADR-043` is not created and its number is not reserved.**

---

## Method: Evidence Grading

Same convention as every preceding Implementation Plan in this repository: **[RATIFIED]** (observed directly in committed code or an approved document), **[DERIVED]** (a plan-level choice reasoning from ratified material), **[OPEN]** (a real fork this document does not resolve — flagged for a decision before code is written).

---

## Part 1 — Current State (Audit)

**[RATIFIED, verified directly against `backend/` and `frontend/src/` on 2026-08-01.]** Sprint 4 (ADR-042 rounds 1–2) is implemented and in the repository. This plan builds on what is there, not on what was planned.

**Backend — the value already exists and is already delivered to the passenger's own query:**

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` — `var statedPrice: String? = null` (line 57), set **only** inside `accept(statedPrice: String? = null)` (lines 74, 79). `decline()` and `lapse()` never set it.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalResponse.kt` lines 20–26 — `statedPrice: String? = null` is a field of the single response type every Proposal endpoint returns. Its own KDoc (lines 13–18) already records that the field is delivered on **both** `GET /v1/proposals?driverId=…` and `GET /v1/proposals?orderId=…`, and cites ADR-042 R8 for why no per-caller projection was introduced.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt` — line 183 is the `?orderId=` branch (the passenger's own query); lines 192–193 are the shared `toResponse()`, which passes `statedPrice`. **Both branches map through the same function.**
- `backend/dispatch/src/main/resources/db/migration/dispatch/V6__proposal_stated_price.sql` — present and applied. `V6` remains the highest Dispatch migration; this Sprint adds none.

**Frontend — the passenger screen already receives the value and throws it away:**

- `frontend/src/pages/RideRequest/RideRequest.tsx` line 171 polls `GET /v1/proposals?orderId=${orderId}` against `DISPATCH_BASE_URL` (line 23) every `STATUS_POLL_INTERVAL_MS = 3000` (line 45), for as long as the confirmed screen is open (effect at lines 165–209).
- The response is typed by a **local** interface, `ProposalStatusItem` (lines 32–34), which declares `status` and nothing else. TypeScript's structural typing means `statedPrice` arrives in the JSON and is simply not named. This is exactly what ADR-042 R8 predicted and recorded.
- The confirmed screen renders the ride status at line 372 (`RIDE_STATUS_LABEL[rideStatus]`), inside the `step === 'confirmed'` block (lines 351–375). There is no price anywhere on this screen.
- The driver's side already renders the same value: `frontend/src/pages/DriverHome/DriverHome.tsx` lines 705–713 — `{proposal.status === 'ACCEPTED' && proposal.statedPrice && (<p className={styles.status}>Стоимость: {proposal.statedPrice}</p>)}`. **This is the shape to mirror**, not to reinvent.

**Consequence [DERIVED], and it is the whole reason this plan is short:** the delta between "the passenger does not see the price" and "the passenger sees the price" is **one frontend file**. No Kotlin file, no migration, no endpoint, no contract, no event.

---

## Part 2 — What v1.1 Is, and What It Is Not

### 2.1 What it is

The passenger sees the price the driver stated, on the confirmed-order screen, after the driver has accepted. Read-only. Nothing else.

### 2.2 What it is not — the wording matters, and it was corrected before ratification

The ruling is **not** "the passenger sees the amount and can decide." That earlier drafting was corrected by the Product Owner into the wording used throughout this plan:

> **пассажир видит предложенную цену; решение — вне PIOS, до появления отдельного механизма**

**"Can decide" implies a decision control inside the product, and no such control exists or is authorized.** PIOS displays a number. Whatever the passenger then does about it — call the driver, agree, refuse, do nothing — happens entirely outside PIOS and leaves no trace in it. The correction is recorded here, and in ADR-042 R9's own Product Decision section, rather than quietly applied: anyone reading "can decide" would have been entitled to implement a button, and this plan exists partly to make sure nobody does.

### 2.3 «Отказаться» — not implemented, and deliberately so

**[RATIFIED — Product Owner, 2026-08-01.]** No passenger-side refusal control of any kind is built in v1.1.

**[DERIVED]** — why it is not a small addition, so that "just add a button" is understood as the scope change it would be: a passenger-side refusal is not a price feature at all, it is an **order-lifecycle** feature. It would have to decide what happens to an `Assignment` that already exists (the Proposal was accepted, and ADR-036's shared transaction already created the Assignment), what `OrderStatus` results, who may trigger it and until when, and what the driver sees when it happens. ADR-041 settled how an Order's lifecycle synchronizes with Assignment completion and placed a standing gate in front of exactly this class of change.

It therefore requires a **Product Decision first**, now named: **«Управление жизненным циклом заказа» (Order Lifecycle Management)** — and then its own ADR, at whatever number is free when that ADR is written. **Not started, not scheduled, not scoped. `ADR-043` is not created and 043 is not reserved for it.** Recorded as ADR-042 Open Question 10.

---

## Part 3 — Implementation Design

### 3.1 `frontend/src/pages/RideRequest/RideRequest.tsx` — the only file

**(a) Widen the local response interface.** `ProposalStatusItem` (lines 32–34) gains `statedPrice?: string | null`.

Optional and nullable, both deliberately: the backend field is nullable (`ProposalResponse.kt` line 25) and an `OPEN` proposal never carries one. No backend change accompanies this — the field is already on the wire (Part 1).

**(b) Keep the value from the accepted proposal.** The poll at lines 171–179 already locates the accepted item via `items.some((item) => item.status === 'ACCEPTED')`. Take `statedPrice` from **that same item** into local state, alongside the existing `rideStatus`.

**Binding constraint:** do not aggregate across items, do not pick a "best" or "lowest" one, do not compare. **ADR-042 R4.3 — no comparison, ordering or aggregation — is the single most important prohibition in that ADR**, and this line of code is the most plausible place in the whole product to break it by accident. If more than one accepted item were ever present, that is the multi-candidate question ADR-035 Part 3 leaves open; this screen does not resolve it and must not silently invent a resolution by choosing between amounts.

**(c) Render it, read-only.** On the confirmed screen, alongside the existing ride-status line (line 372), and only when a value is present — the same `{value && <p …>}` shape the driver's card already uses (`DriverHome.tsx` lines 711–713).

Forbidden in the render, each mapping to an ADR-042 prohibition rather than to taste:

- no parsing the value as a number (R4.2);
- no currency symbol added by the UI, and no currency inferred (R4.4 — Open Question 2 is still open, and a symbol in the UI would answer it by implication);
- no rounding, thousands separators or other reformatting (R4.2);
- no placeholder, dash, zero or "цена не указана" when the value is absent — render nothing at all (R4.1: absent means absent, and a default is a produced value).

**(d) No control beside it.** No button, no confirm, no accept, no «Отказаться», no counter-offer field, no comment box. Nothing to press, nothing to answer. This is ADR-042 R10.1, not a styling preference.

**(e) Copy is not ratified by this plan.** The label wording is a product/UX matter. Mirroring the driver screen's existing «Стоимость: …» is the smallest consistent choice and is what this plan recommends. **Excluded:** any copy that frames the amount as an offer awaiting a reply — «Подтвердите стоимость», «Согласны?», «Принять цену» — because such copy asserts the decision control R10 forbids even when no control is rendered. If different copy is wanted, it comes from the Product Owner, not from the implementation.

### 3.2 `frontend/src/pages/RideRequest/RideRequest.module.css`

Only if 3.1(c) needs a style. Reuse the existing `styles.status` if it fits — the ride-status line beside which the price renders already uses it.

### 3.3 Backend — nothing

No Kotlin file, no migration, no endpoint, no contract, no event, no test-visible behaviour change. `git diff --stat backend/` must be empty at the end of this Sprint. If it is not, something outside this plan's scope was changed.

### 3.4 Timing behaviour, stated so it is not "fixed"

**[RATIFIED]** `statedPrice` is set only inside `Proposal.accept()`. Before the driver accepts, **no amount exists**, so the passenger sees none while the proposal is `OPEN`.

**This is not a defect and must not be filled in.** Producing a value PIOS was not given — an estimate, a range, a "от … ₽", a suggested price — is precisely what ADR-042 R4.1 forbids and what ADR-002 and `DOMAIN_MODEL.md` line 177 exclude from architectural scope entirely. If a price *before* acceptance is ever wanted, it has a different producer and needs its own Product Decision.

---

## Part 4 — Explicitly Out of Scope

Restated at file granularity because several of these become tempting the moment a price appears on the passenger's screen:

- Any passenger-side control, «Отказаться», or anything that records a passenger reaction to the amount (R10).
- Any backend change of any kind: `dispatch`, `order-management`, `passenger-experience`, `driver-management`, `network-management`, `identity`.
- Any event payload, outbox record, event version or cross-module contract. Every payload stays v1 (ADR-042 Decision item 6); `INTERFACE_CONTRACTS.md` Section 3 is unchanged.
- Any change to `Assignment`, `AssignmentStatus`, `Assignment.create`, `AssignOrderCommand`, the availability projection, or any Assignment Policy.
- Any change to `frontend/src/pages/DriverHome/DriverHome.tsx`, `Coordinator.tsx` or `PassengerLanding.tsx`.
- Any history, receipt or earnings screen, for either role (ADR-042 Open Question 9 — still open, still unanswered).
- Any comparison, sorting, parsing, summing or formatting of an amount, anywhere, backend or frontend (R4.3).
- Creating `docs/ADR/ADR-043-*` or reserving the number 043.
- Any edit to `docs/PIOS_UX_BACKLOG.md` under this plan.

---

## Part 5 — Definition of Done

Mirrors ADR-042's round-3 Definition of Done exactly; repeated here so the developer role needs one document open, not two.

1. A passenger whose order was accepted **with** a stated price sees that price on the confirmed-order screen.
2. **The price is still there after a full page reload.** This is the check that matters: it proves the value round-trips from `pios_dispatch` through the existing poll, rather than surviving in React state. Same reasoning ADR-042 R7.1 step 3 used for the driver.
3. An accepted order with **no** stated price shows no price line, no empty label, no placeholder.
4. Before acceptance, no price and no price-shaped placeholder appears (3.4).
5. **No control appears anywhere near the amount** — nothing to press, nothing to answer, no «Отказаться».
6. `git diff --stat backend/` is empty. No migration added; the highest Dispatch migration is still `V6`.
7. No event payload, outbox record or cross-module contract changed.
8. The driver's own screen behaves exactly as before.
9. Nothing anywhere parses, compares, sorts, sums or formats the amount.
10. The result is observable in a `PIOS_PILOT_REVIEW_PROTOCOL.md` session, so the Sprint can actually produce the `E-NNN` entries H3 needs (R11.3).

---

## Part 6 — Risks and Disclosed Limitations

- **The passenger sees a number they cannot act on inside PIOS.** Disclosed, intended, and exactly what H3 is testing. **If a pilot passenger tries to refuse and finds no way to, that is the observation, not a defect** — it belongs in `PIOS_PRODUCT_EVIDENCE.md` and is the first real input to «Управление жизненным циклом заказа». It must not be patched mid-sprint by adding the control R10 forbids.
- **Currency is still unspecified, and now it is passenger-facing.** ADR-042 Open Question 2 remains open — none of the three rulings answered it. The passenger now sees a bare string a driver typed, with no currency, no formatting and no validation behind it. This plan does not resolve it and must not invent a currency to tidy it up. What v1.1 does do is **raise the cost of leaving it open**, from a driver-only inconvenience to something a first-time passenger reads. **[OPEN]** — recommended, not decided here: answer Open Question 2 before exposure widens further.
- **The read-side exposure ADR-042 R8 identified is unchanged.** PIOS still has no authentication and no authorization (ADR-038), so the amount remains readable by any caller of either query branch, not only by the passenger it is meant for. v1.1 makes one reader *intended*; it makes no reader *impossible*. ADR-042 Open Question 8, reframed by marker M12, stays open.
- **Multi-candidate remains the principal residual risk.** Several priced proposals per order is structurally the shape of an auction. R4.3 forbids the comparison that would make it one; design item 3.1(b) restates that prohibition at the exact line where a frontend implementation would be tempted to break it.
- **Wording risk, named because it already occurred once.** The framing "the passenger sees the amount and can decide" was drafted and corrected before ratification. UI copy that reintroduces it — a label implying the passenger is being asked something — reintroduces the same error at the surface where real users actually read it (3.1(e)).

---

## Part 7 — Traceability

| Question | Answer |
| --- | --- |
| Is there evidence? | No `E-NNN` exists; the journal is empty. Basis is **H3**, registered before the Sprint, under the append-only rule ratified in `PIOS_PRODUCT_EVIDENCE.md` on 2026-08-01. This Sprint **produces** evidence. |
| Does it match an existing Product Decision? | Checked in ADR-042 R11.4. `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` and `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`: no conflict. The one genuine conflict is with the Product Owner's own round-2 ruling of 2026-07-31, superseded openly by the Product Owner's own later ruling and marked in eleven places (M1–M4, M6–M12). |
| Is a new ADR needed? | **No.** ADR-042 Amendment round 3 covers it: no boundary change, no new contract, no ownership move, no new field. `ADR-043` is not created and its number is not reserved. |
| What produces the next decision? | The pilot. Observations go to `PIOS_PRODUCT_EVIDENCE.md` under H3; a passenger who wants to refuse feeds **«Управление жизненным циклом заказа»**. |

---

## Part 8 — Handoff to the Developer Role

**ADR reference:** `docs/ADR/ADR-042-Stated-Ride-Price-Minimal-Model.md`, Amendment 2026-08-01 (round 3), R9 / R10 / R11 and the round-3 Developer Scope.

**In scope — exactly two files, both frontend:**

- `frontend/src/pages/RideRequest/RideRequest.tsx` (Part 3.1 (a)–(e))
- `frontend/src/pages/RideRequest/RideRequest.module.css` (only if a style is genuinely needed)

**Out of scope:** everything in Part 4. In particular **no backend file may be touched**, and no ADR may be created.

**Constraints the implementation must satisfy:**

1. The passenger **sees** the stated price. *Пассажир видит предложенную цену; решение — вне PIOS, до появления отдельного механизма.* No control, in any form, may accompany it.
2. Read-only, verbatim, uninterpreted: no parsing, no currency, no formatting, no rounding, no default, no comparison, no aggregation.
3. Zero backend diff, zero migration, zero contract change.
4. The value must survive a full page reload (Definition of Done item 2).
5. If implementation reveals that any of the above cannot hold — in particular if the value turns out **not** to be present in the `?orderId=` payload as Part 1 records — **stop and report the conflict rather than adding a backend change to make it work.** A backend change here would mean the plan's central factual claim is wrong, which is an architecture question, not an implementation detail.
