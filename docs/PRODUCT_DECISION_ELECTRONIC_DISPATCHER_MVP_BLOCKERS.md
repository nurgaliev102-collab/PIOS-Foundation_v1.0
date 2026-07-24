# PIOS Product Decision: Electronic Dispatcher MVP Blockers v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document ratifies exactly three decisions Sprint PRODUCT-DECISION-RESOLUTION-001 identified as blocking a first, honest implementation of Automatic Dispatch: Cycle-Exhausted Behavior, Proposal Timeout Policy, and Concurrent-Proposal Policy. It is not an ADR, not an architecture document, and authorizes no Fair Opportunity algorithm, no new aggregate, no new Proposal or Assignment state, and no code.

Derived from PROJECT_CONSTITUTION.md, `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` (the design blueprint these decisions resolve items from — Sections 6, 7, 15.1, 15.6), and every Product Decision and ADR that design blueprint itself was built on (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`, `PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md`, `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`, `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md`, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`, `PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md`, ADR-002, ADR-034, ADR-035), all re-read directly for this document rather than assumed from memory. It introduces no new aggregate, event, command, or actor; it ratifies product-level policy only, exactly as every preceding document in this decision chain has done.

---

## Method

Each of the three decisions below is recorded in the same **Ratified / Deliberately NOT Ratified / Rationale** structure `PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md` already established for a Product Owner decision delivered directly, verbatim, rather than reconstructed through evidence-grading of pre-existing documents. The Product Owner's own words are preserved, not paraphrased, in the Ratified section of each.

---

## PD-1 — Cycle-Exhausted Behavior

### Ratified

Electronic Dispatcher должен использовать: **LIMITED AUTOMATIC RETRY / RE-EVALUATION → затем HONEST CUSTOMER OUTCOME.**

Исчерпание текущего Candidate Pool не означает немедленный окончательный отказ. После того как все кандидаты текущего цикла DECLINED, LAPSED, либо больше не подходят, Electronic Dispatcher может повторно пересобрать Candidate Pool, поскольку состояние системы может измениться: водитель может стать AVAILABLE; может появиться новый eligible candidate; ранее недоступный кандидат может стать доступен.

Однако retry не должен быть бесконечным. После достижения установленного предела автоматического поиска система должна честно сообщить заказчику, что водитель не найден.

**Human Coordinator НЕ является штатным fallback-механизмом Automatic Dispatch.** Coordinator может существовать как pilot/debug/admin capability, exceptional operational tool — но не должен становиться обязательной частью нормального dispatch lifecycle.

### Deliberately NOT Ratified

- Количество retry.
- Retry interval.
- Общий максимальный search horizon.
- Точное состояние Order после окончательного exhaustion.
- Точный UX пассажира после exhaustion.

Эти параметры/решения классифицированы отдельно в Part 3 (Triage) of this sprint's own report, not resolved by this document.

### Rationale

**[DERIVED, consistent with every already-ratified principle this design chain established]:** this closes `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` Section 15.1 exactly as that document's own Option analysis framed it (a bounded hybrid of Option A + Option C, explicitly rejecting Option B — mandatory Coordinator escalation — as the *normal* path). It is consistent with, and does not contradict, `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`'s own "manual operations are acceptable" framing: that document describes a *different, earlier, entirely-manual* pilot mechanism (Section 2, 4 of that document) which remains valid and unchanged for its own scope; PD-1 governs a *later, narrower* question — what an already-built automatic mechanism does when it exhausts its own candidate pool — and keeps manual Coordinator involvement available as an exceptional tool, not a reversal of that earlier decision. It is also directly consistent with Transparency (PROJECT_CONSTITUTION.md Section 3, Section 18) and with `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 9's own explainability requirement: an honest "no driver found" outcome, rather than an indefinite silent state or a mandatory human bypass of the fairness mechanism, is the reading most consistent with both.

---

## PD-2 — Proposal Timeout Policy

### Ratified

Для MVP Proposal Timeout является: **SINGLE CONFIGURABLE OPERATIONAL PARAMETER.**

Один и тот же принцип timeout применяется ко всем водителям. Timeout:

- не должен быть скрыто персонализирован;
- не должен зависеть от алгоритмического рейтинга водителя;
- не должен использовать adaptive/dynamic logic;
- не должен создавать скрытый priority mechanism.

Конкретное числовое значение timeout **НЕ ратифицируется этим решением.** Оно должно задаваться конфигурацией, изменяться без изменения Domain Model, и корректироваться по данным реального пилота.

### Deliberately NOT Ratified

- Конкретное числовое значение timeout (секунды/минуты).

### Rationale

**[DERIVED]** — resolves `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` Section 7.2/15.6 by selecting the option that sprint's own analysis recommended (a single, globally-configurable value, mirroring the already-proven `pios.outbox.relay.fixed-delay-ms` convention in this exact codebase) and explicitly rejecting context-sensitive segmentation and adaptive/dynamic duration. The rejection of adaptive/dynamic logic is directly grounded in No Hidden Algorithmic Decisions (PROJECT_CONSTITUTION.md Sections 3, 18): a timeout that varies per driver by an undisclosed rule would function as a hidden priority mechanism — exactly the risk PRODUCT-DECISION-RESOLUTION-001's own analysis flagged and this ratification now forecloses explicitly, not merely by omission.

---

## PD-3 — Concurrent Proposal Policy

### Ratified

Для одного Order одновременно допускается: **MAXIMUM ONE OPEN PROPOSAL.** Electronic Dispatcher работает строго последовательно.

Lifecycle: Candidate Pool → Fair Opportunity Policy выбирает одного кандидата → Proposal OPEN для одного Driver. Если ACCEPTED → Assignment. Если DECLINED или LAPSED → Electronic Dispatcher рассматривает следующего допустимого кандидата.

**Broadcast / race-to-accept модель НЕ используется в MVP.** Запрещено без нового Product Decision: несколько OPEN Proposal для одного Order; simultaneous broadcast; first-driver-wins race; batch concurrency K > 1; скрыто превращать reaction speed водителя в fairness factor.

**Существующий Root Invariant Proposal — "не более одного OPEN Proposal на Order" — сохраняется.**

### Deliberately NOT Ratified

- Whether a broadcast/batch mechanism is ever pursued in the future (left fully open, not rejected forever — only foreclosed for MVP without a new, dedicated Product Decision).

### Rationale

**[RATIFIED — formalizes an already-implemented invariant as intentional, permanent MVP policy, not merely a technical restriction never product-examined].** `Proposal`'s own Root Invariant ("at most one `OPEN` Proposal per order at a time") was already implemented and tested (Sprint IMPLEMENTATION-001) and architecturally justified (ADR-035 Part 2). This decision was **not** previously one of `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` Section 15's twelve enumerated Product-Decision-Required items — that document's own Section 6.2 had already *assumed* sequential-only as its working design, without flagging it as a distinct open question requiring separate ratification. PD-3 closes that gap explicitly: it converts an implicit design assumption into an explicit, binding Product Decision, so that a future engineering change toward broadcast/batch concurrency cannot be introduced as "just an optimization" without first being recognized as exactly the kind of business-policy change (introducing "reaction speed" as an unevaluated fairness factor, per `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`'s own factor-classification discipline) that this whole decision chain requires Product Owner authority for.

---

## Consistency Check Against Existing Ratified Architecture

Performed by direct re-reading of the current repository state (not from memory), per this sprint's own instruction:

- **PROJECT_CONSTITUTION.md** (Sections 3, 18, 19, 22, 25, 26) — no conflict. PD-1's honest-outcome requirement directly serves Transparency/No Hidden Algorithmic Decisions (Section 18); PD-2's rejection of adaptive timeout directly serves the same; PD-3 introduces no new business rule, only formalizes an existing invariant.
- **DOMAIN_MODEL.md** (Sections 4, 6, 9, 11, 12) — no conflict. None of PD-1/2/3 changes `Order`, `Proposal`, or `Assignment`'s own ratified Purpose, Invariant, or Lifecycle. PD-1's "Dispatch does not touch Order's own status on exhaustion" reading (Part 3 of this sprint's own report, Triage item) is a direct, mandatory consequence of Section 11's existing invariant ("Only the domain that owns a piece of information... may change it") — not a new rule.
- **MODULE_STRUCTURE.md** (Dispatch Module section) — no conflict. PD-1/2/3 govern Dispatch's own already-exclusive assignment-decision responsibility; no other module's boundary is touched.
- **EVENT_CATALOG.md** — no conflict. No new event is introduced or implied; `Proposal`'s own internal, not-externally-published events (`OrderProposed`, `ProposalAccepted`, `ProposalDeclined`, `ProposalLapsed`) remain exactly as internal as Sprint IMPLEMENTATION-002/003 already decided.
- **INTERFACE_CONTRACTS.md** (Section 3, 5) — no conflict. No new cross-module contract is created; Candidate Pool assembly (design document Section 4) still only consumes Driver Management's already-ratified Availability contract.
- **APPLICATION_ARCHITECTURE.md** (Sections 2, 10) — no conflict. PD-1/2/3 are coordination-level policy, not a new business invariant invented at the application layer; "Domain Decides Business Meaning" is preserved — `Proposal`'s and `Assignment`'s own domain invariants remain the sole source of truth for what each aggregate permits.
- **PERSISTENCE_ARCHITECTURE.md** (Section 3, Dispatch) — no conflict. No new persisted entity is introduced by PD-1/2/3 themselves (a timeout scheduler's own bookkeeping, if any, remains an implementation detail for a future ADR, not decided here).
- **ADR-002, ADR-034, ADR-035** — no conflict. No algorithm is chosen (ADR-002's standing prohibition, restated by ADR-034 Part 4, remains fully intact — PD-1/2/3 govern the *lifecycle and orchestration* around candidate selection, never the selection criteria themselves). ADR-035's Aggregate Boundary Justification (in particular, its transactional-independence argument for `Proposal` as a separate aggregate) is not touched by any of PD-1/2/3; PD-3 in fact reinforces it by keeping `Proposal`'s own Root Invariant unchanged and explicitly foreclosing any mechanism that would pressure it to change.
- **`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`** — no conflict, refinement noted explicitly in PD-1's own Rationale above (different scope: that document's own manual pilot mechanism vs. PD-1's automatic-cycle-exhaustion behavior).
- **Sprint IMPLEMENTATION-004's own deprecation of `POST /v1/assignments`** (`AssignmentController.kt`'s KDoc: "Superseded as the normal path... Retained for manual override; not removed") — **refined, not contradicted**, by PD-1: PD-1 supplies the product-level confirmation that this "manual override" is exactly the pilot/debug/admin-only, non-normal-lifecycle status that endpoint's own deprecation note already anticipated but did not itself rule on at the product level. No code or KDoc is touched by this document (out of this sprint's own scope); a future documentation-only task should append an `Update:` note to that KDoc referencing this decision, mirroring the same non-destructive evolution mechanism ADR-034's own "Update:" paragraph already used for ADR-035.

**No conflict found anywhere.** No existing ratified document is reinterpreted, reopened, or silently overridden.

## Files Changed

`docs/PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` (new) is the only content file this decision creates. `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set for every preceding Product Decision in this chain. No ADR, code, database, event, or API is created or modified.

## Unresolved Decisions Carried Forward

See this sprint's own Triage (Part 3 of the accompanying report) for the complete, classified list of every remaining open item from `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` Section 15, plus the newly-surfaced first-Fair-Opportunity-Policy-candidate selection this triage identified as the sole remaining implementation blocker.

## References

- `PROJECT_CONSTITUTION.md`, Sections 3, 7, 18, 19, 22, 25, 26
- `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md`, Sections 6, 7, 15.1, 15.6
- `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`
- `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, Sections 9–10
- ADR-002, ADR-034, ADR-035
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt` — read, not modified.
