> **STATUS: DRAFT / PROPOSAL — NOT APPROVED, NOT AUTHORIZED FOR IMPLEMENTATION**
> This is a proposal document only, written for review by the project architect (ChatGPT) and the product owner. It contains no architecture decision, no ADR, no product decision, and authorizes no code change. Per `.claude/CLAUDE.md` ("Architecture decisions are made by the project architect... Claude must not redesign architecture proactively") and root `CLAUDE.md` ("Never Change Architecture Without an ADR"), nothing below may be implemented as-is. File is intentionally uncommitted (see `git status`) pending review.

# Sprint 9 (Proposed) — Real Personal Network Activation

## 1. Origin and status of this proposal

This was proposed in conversation as: *"Артур сказал: 'Я первый установлю, если это сделаете.'"* This quote is **explicitly illustrative, not a recorded observation from an actual Pilot Review session** (confirmed by the product owner, 2026-07-29). It is therefore **not** evidence under `docs/PIOS_PRODUCT_EVIDENCE.md`'s own rule and cannot be cited as the reason a Sprint is necessary. That log remains empty; no Pilot Review has produced a recorded observation yet.

**Consequence:** this proposal does not currently satisfy the project's own precondition for starting a Sprint (`docs/PIOS_PRODUCT_EVIDENCE.md`, "Как это используется дальше": *"Какое доказательство из журнала делает этот Sprint необходимым? Если ответа нет — не начинаем."*). It is recorded here as a fully-scoped proposal so that review and any required ADR/product-decision work can happen in parallel with — not instead of — obtaining real pilot evidence, not as a signal that work should start now.

## 2. Proposed scenario

```
Артур
 |
приглашает
 |
Регина
 |
Регина становится частью сети Артура
 |
Регина приглашает маму
 |
мама создаёт заказ
 |
система сначала проверяет связь
 |
заказ идёт Артуру
```

This is the scenario `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` originally sketched and that ADR-037 explicitly deferred: *"Future work connecting `network-management`'s `Connection` concept to actual order routing (the deferred half of the founder's original Sprint 7 sketch) will need its own ADR when it introduces the first real cross-module dependency — not authorized here"* (ADR-037, Consequences).

## 3. Current state (per `docs/ARCHITECTURE_VERIFICATION_REPORT.md`, DRAFT/REVIEW REQUIRED)

The product today already runs a working version of the first three steps, but through a **different, self-built mechanism** that has nothing to do with `network-management`:

- "Invitation" = the driver's own `DriverId` in a URL (`/i/{driverId}`), resolved via `GET /v1/drivers/{id}` (Driver Management).
- "Регина становится частью сети Артура" = a `Connection` row created in **Passenger Experience** (`POST /v1/connections`, port 8082) — a passenger↔driver reference, not a graph edge in Network Management.
- `network-management`'s real, implemented `Person`/`Profile`/`Connection`/`Invitation` (`InvitationController.kt` et al.) is **fully built but never called** by any product code — confirmed isolated in all five `build.gradle.kts` files (no production or test-scope dependency in either direction).
- No priority/routing logic exists anywhere today. Dispatch has **no ratified relationship at all** to any driver–passenger connection concept (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2: *"Dispatch. No ratified role at all... none [of the four justified module-to-module contracts] gives Dispatch any access to Personal Client Relationship information."*).

## 4. The three proposed changes and what each actually requires

### 4.1 Replace `/i/{driverId}` with `/invite/{invitationCode}` generated via Network Management

- **Modules touched:** `network-management` (issue real `Invitation` codes — already implemented, `InvitationController.kt`), `frontend` (`DriverHome.tsx`, `invitationSource.ts`, `PassengerLanding.tsx` all change their call target from Driver Management to Network Management).
- **Existing services extended:** none in the backend (Network Management's invitation issuance already exists); the change is entirely in how the frontend resolves an invitation.
- **Architectural gate:** this alone does not yet cross a module boundary in production code if the frontend is the only thing that changes — but it's the first step of 4.2, which does.

### 4.2 Connect `PassengerLanding` to Network Management's `Connection` (Артур → Регина) instead of Passenger Experience's

- **Modules touched:** `network-management`, `passenger-experience`, `frontend`.
- **What actually changes:** today's real, working `Connection` write (`POST /v1/connections` on Passenger Experience) would be replaced or duplicated by a call to Network Management's `Connection`. This is the first real **production, non-test** integration point touching `network-management` — exactly what ADR-037 reserved.
- **Architectural gate — required before this can be implemented:** a new ADR extending ADR-037, per its own Consequences section, authorizing the first real cross-module dependency. `MODULE_STRUCTURE.md` Section 8's Extension Strategy governs this. Not optional, not a documentation nicety — ADR-037 states implementation may not proceed without it.
- **Open design question this ADR would need to resolve, not assumed here:** does this *replace* Passenger Experience's `Connection`, or do the two now represent different things (per ADR-037's own boundary note, `network-management`'s `Connection` is "a separate, narrower concept... not a substitute for" Personal Client Relationship, which is Passenger Experience/Driver Management's joint concept)? Collapsing them without that decision risks exactly the "two parallel, inconsistent models" risk `ARCHITECTURE_VERIFICATION_REPORT.md` Risk 1 already flags.

### 4.3 "Кто пригласил этого клиента?" → порядок предложения заказа (Правило 5)

**Update, 2026-07-29:** the business rule this section originally called undefined is now decided. `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` ("APPROVED PRODUCT DECISIONS") Rule 5 + Section 6 invariant specify the full mechanism:

1. Driver who created the original connection — **right of first offer only, not a guaranteed order** (explicit 2026-07-29 clarification; this is what stops 4.3 collapsing into unconditional priority).
2. Drivers with a history of good service to this passenger.
3. Suitable drivers in the trust network.
4. General PIOS queue.
5. Independent, always-available invariant: the passenger may choose a different driver regardless of any existing connection.

- **Modules touched:** `order-management` and/or `dispatch` (wherever the check and the resulting routing decision would live), plus whichever module ends up owning the connection data per 4.2.
- **Existing services to extend:** `ProposalApplicationService` / `ProposalAssignmentOrchestrationService` (Dispatch) — currently create a Proposal for whichever `driverId` the frontend already passes in; implementing steps 1–4 as an ordered fallback is new decision logic, not an extension of existing logic.
- **What remains an architecture gap, not a business-rule gap, now that the rule itself is decided:**
  - Step 1 requires no new data beyond what 4.1/4.2 already produce (origin of connection).
  - Step 2 requires a "service quality/history" concept that **does not exist anywhere in `DOMAIN_MODEL.md` today** — `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 3 already classified the closest existing concept ("service quality priority") as `[HYPOTHESIS]`, and `ADR-034` Part 2 lists "Driver profile, standing, participation, or ranking beyond availability" as a **Forbidden input** to Assignment Policy today — step 2 cannot be implemented without an ADR lifting that specific boundary.
  - Step 3 requires a definition of "suitable" that no document provides (geography? order type? unspecified) — this is a modeling gap, not just a wiring gap.
  - Dispatch has **no ratified contract** to any of steps 1–3's data at all (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2) — `INTERFACE_CONTRACTS.md` Section 5's four justified contracts do not include this.
- **Gate required:** no further Product Owner decision is needed on *whether* or *what* the rule is (done). What's still required is the ADR work: extend `ADR-034` (lift the forbidden-input boundary for step 2, define the Assignment Policy port shape for the ordered fallback) and extend `INTERFACE_CONTRACTS.md` (new Dispatch-facing contract). See Section 7 below for the full ADR list.

## 5. Consolidated blockers (nothing below is resolved by this document)

| # | Blocker | Type | Status |
|---|---|---|---|
| 1 | Empty evidence log — no Pilot Review has produced a citable observation for this Sprint | Process (`PIOS_PRODUCT_EVIDENCE.md`) | Open |
| 2 | First real `network-management` cross-module dependency requires an ADR extending ADR-037 | Architecture | Open — not drafted |
| 3 | Priority/routing business rule | Business rule | **Fully resolved 2026-07-29** — `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` (APPROVED). Ownership model, origin tracking, payment-vs-recognition split, the 4-step offer order, and the passenger-override invariant are all decided. Nothing further needed from the product owner on *what* the rule is. |
| 4 | Dispatch has no ratified contract to read any driver–passenger connection data at all | Architecture (`INTERFACE_CONTRACTS.md`) | Open — no contract exists |
| 5 | Whether Passenger Experience's existing `Connection` is replaced or kept alongside Network Management's is undecided | Design | Open — the 2026-07-29 decision's "any participant can create connections" language (Section 2.6 of `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md`) suggests `network-management`'s generic model fits better than a two-role Driver/Passenger pair, but this is flagged, not resolved |
| 6 | Step 2's "service quality/history" concept doesn't exist in `DOMAIN_MODEL.md`, and its use is currently a `ADR-034` Part 2 Forbidden input | Architecture + domain modeling | Open — new, identified while updating this section 2026-07-29 |
| 7 | Step 3's "подходящие водители доверительной сети" has no definition of "suitable" anywhere | Domain modeling | Open — new, identified while updating this section 2026-07-29 |

## 6. What would need to happen, in order, before implementation could start

1. A real Pilot Review session produces at least one genuine `E-NNN` entry in `docs/PIOS_PRODUCT_EVIDENCE.md` that this Sprint can cite. **Still open — nothing below changes this.**
2. Architect (ChatGPT) drafts and the product owner ratifies the ADR bundle in Section 7.2 below.
3. `INTERFACE_CONTRACTS.md` gets a fifth justified module-to-module contract (Dispatch ← connection/quality data), per Section 7.3.
4. A domain-modeling pass defines "service quality/history" (blocker #6) and "suitable network driver" (blocker #7) concretely enough for a data model — currently nothing beyond the named concept exists.
5. Only then does implementation scoping (which files, which services, test plan) belong in an `IMPLEMENTATION_PLAN_*.md`, following the same pattern as `IMPLEMENTATION_PLAN_SPRINT_7_PERSONAL_NETWORK_MVP.md`.

## 7. Sprint 9 readiness bundle (planning only — no implementation authorized)

Requested explicitly by the product owner alongside `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md`: a list of modules, required ADRs, interface changes, risks, and an implementation plan — prepared so that review can happen, not so that work can start. Nothing in this section is authorized for implementation; Section 5's blockers (evidence log above all) still gate actual work.

### 7.1 Modules to change

| Module | Change | Depends on |
|---|---|---|
| `network-management` | None to its own domain logic — `InvitationController.kt`/`Connection` already implemented (per `ARCHITECTURE_VERIFICATION_REPORT.md`). Becomes reachable from production code for the first time. | ADR extending ADR-037 |
| `frontend` | `DriverHome.tsx`, `invitationSource.ts`, `PassengerLanding.tsx` repointed from Driver Management/Passenger Experience to Network Management (§4.1–4.2) | ADR extending ADR-037 |
| `passenger-experience` | Decision on whether its existing `Connection` (`POST /v1/connections`) is replaced, deprecated, or kept alongside Network Management's — not decided (blocker #5) | ADR extending ADR-037 |
| `dispatch` | New Assignment Policy port implementing Rule 5's 4-step ordered fallback (`ProposalAssignmentOrchestrationService` extension); new inbound contract to read connection/quality data | ADR extending ADR-034; new `INTERFACE_CONTRACTS.md` entry |
| `driver-management` and/or a new owner (undetermined) | Owns the "service quality/history" concept (blocker #6) — no existing module is a clean fit; this may itself need a Product Owner/architect decision, not assumed here | New domain modeling decision |
| `order-management` | Likely unaffected directly — Proposal creation already carries `driverId`; the ordering logic lives in Dispatch, not here. Flagged as "likely," not confirmed, since it wasn't traced line-by-line for this bundle. | — |

### 7.2 ADRs required (none drafted; listed for architect scoping)

1. **Extend ADR-037** — authorize the first real `network-management` cross-module dependency (§4.2), resolving the replace-vs-coexist question with Passenger Experience's `Connection` (blocker #5).
2. **Extend ADR-034** — lift the Part 2 Forbidden-input classification for "driver standing/ranking beyond availability" specifically for the quality-history signal Rule 5 step 2 needs; define the Assignment Policy port shape for a 4-step ordered fallback (not a single-value ranking) plus the passenger-override invariant (Section 6 of the network rules doc) as a hard override at the top of that port's contract, not merely a policy preference.
3. **New `INTERFACE_CONTRACTS.md` entry** — Dispatch as consumer of connection-origin and quality-history data; provider module(s) depend on where blocker #6 lands (driver-management extension vs. a new owner).
4. **Possibly a domain-modeling ADR or Product Decision** for "service quality/history" (blocker #6) and "suitable network driver" (blocker #7) if either turns out to need its own aggregate/entity rather than fitting inside an existing one — not determined here.

### 7.3 Interface changes (illustrative, not specified — no schema exists yet)

- Frontend invitation resolution: `GET /v1/drivers/:id` → `GET /v1/invitations/:code` (Network Management, already implemented endpoint, just not called today).
- Connection creation: `POST /v1/connections` (Passenger Experience) → `POST /v1/connections` (Network Management) or both, pending blocker #5.
- New Dispatch-facing read contract (event or query) exposing: connection origin (driver id, timestamp), quality/history signal (undefined shape — blocker #6), network-suitability signal (undefined shape — blocker #7).
- `ProposalController`/`ProposalApplicationService`: no new public endpoint necessarily — the change is internal ordering logic behind the existing `POST /v1/proposals` flow, unless the passenger-override invariant needs its own explicit endpoint (e.g., "request different driver") — not decided here.

### 7.4 Risks

1. Implementing step 1 (right of first offer) without steps 2–4 fully built risks the exact degeneration into absolute priority that `PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` Section 2 (Вариант 1 risk) warned about — a phased rollout must keep the fallback-to-general-queue path live even before quality/history data exists, or effectively ship Вариант 1 by omission.
2. Two parallel `Connection` concepts (Passenger Experience vs. Network Management) already exist and are already a flagged architectural risk (`ARCHITECTURE_VERIFICATION_REPORT.md` Risk 1) — this Sprint is exactly where that risk gets resolved or gets worse, depending on the ADR-037 extension's replace-vs-coexist answer.
3. The passenger-override invariant (Section 6, network rules doc) needs a UI affordance and a backend path — if only the ranking logic is built and the override isn't, the product silently violates its own just-ratified rule.
4. No existing test (per `ARCHITECTURE_VERIFICATION_REPORT.md`'s audit of `MvpVerticalSliceScenarioTest.kt`) exercises the real Proposal→Assignment flow end-to-end over HTTP/outbox/RabbitMQ — new ordering logic would ship without that safety net unless the test gap is closed first or alongside.
5. "Quality/history" and "network suitability" are both undefined data models — building Dispatch's port against a guessed shape risks a rework once the real definition lands.

## Traceability

| Claim | Source |
|---|---|
| Current invitation/connection mechanism, isolation of `network-management` | `docs/ARCHITECTURE_VERIFICATION_REPORT.md` (DRAFT) |
| ADR-037 reserves cross-module wiring for a future ADR | `docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md`, Consequences |
| Approved network rules (ownership, origin, payment, quality, offer order, passenger override) | `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` (APPROVED) |
| Trust priority mechanism analysis and recommendation | `docs/PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` |
| Forbidden-input boundary for step 2 | `docs/ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md` Part 2 |
| Dispatch has no existing contract to this data | `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2; `docs/INTERFACE_CONTRACTS.md` Section 5 |
| Evidence-log precondition for starting a Sprint | `docs/PIOS_PRODUCT_EVIDENCE.md` |
| New module/ADR requirement | `docs/MODULE_STRUCTURE.md` Section 8; `.claude/CLAUDE.md`; root `CLAUDE.md` |
