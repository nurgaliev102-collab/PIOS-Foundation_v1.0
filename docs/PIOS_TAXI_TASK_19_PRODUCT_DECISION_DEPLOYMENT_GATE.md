# PIOS Taxi V1 — Task 19: Product Decision & Deployment Gate

Status: **Read-only decision-preparation audit, building directly on `docs/PIOS_TAXI_TASK_18_RUNTIME_PRODUCT_FLOW_AUDIT.md`.** No code was changed. No production, RabbitMQ, or other live-infrastructure connection was made — every claim below is backed by source code, git, local build-artifact inspection, or Task 18's own already-disclosed evidence. No ADR was created. No decision was made on the Product Owner's behalf where this task named one as required — Parts 1 and 2 end in a labeled **recommendation**, not a choice.

## Part 1 — D1: Driver-Less Ordering

### The three options, each on the same four axes

#### Option A — Ordinary passenger order (no pre-chosen driver) becomes a mandatory V1 flow

- **Frontend impact.** New: a passenger-facing entry point independent of any `driverCode` — a home page, phone number, or general "order a ride" surface — plus a way to route the resulting order to *some* driver without a Circle-of-Trust relationship existing yet. `routes.tsx` currently has no such route; `RideRequest.tsx`'s own submission call has no driver-less code path (Task 18 Section 4.1/6). This is not a small addition — it is a new acquisition surface with its own onboarding, trust, and identity questions `PIOS_TAXI_DESIGN_BRIEF.md`/`DRIVER_IDENTITY_DESIGN_DECISION.md` were never asked to answer, since every existing screen assumes a driver relationship already exists by the time a passenger arrives.
- **First Refusal impact.** Directly completes First Refusal's own designed shape: `PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4's decision tree already has an `ELSE` branch ("no Primary Driver → normal matching") that this option would be the second, missing half of — First Refusal becomes a real "first look before falling through," rather than a mechanism whose fallback path a passenger can never actually enter themselves.
- **Coordinator impact.** Coordinator remains exactly what it already is (a manual matching tool) but its role becomes load-bearing rather than a safety net for the rare failed `attemptProposal` call — every driver-less order would depend on a human coordinator noticing it and assigning a driver, unless Option A is also read as authorizing some automated matching (not itself specified by this option, and explicitly named as a separate, later "Stage C" in `PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md`).
- **Commercial significance.** Directly enables passenger acquisition independent of any one driver's own outreach — the only way this product currently grows its passenger base is through a driver's own individual link-sharing (Section 6 evidence, Task 18). Removes that ceiling, at the cost of the "you build your own business" entrepreneur framing (`PIOS_TAXI_PRODUCT_DECISIONS.md` Section 2) becoming one path among several rather than the only one.
- **What already exists.** The domain model (`Order` has no driver field — genuinely driver-agnostic already), Coordinator (manual matching, live), the full Proposal/Assignment/ride-progress lifecycle (live), First Refusal's own fallback branch as a *decision*, though not as running code (Task 18 Section 3.1).
- **What does not exist.** Any frontend entry point; any specification of how a driver-less order should be *matched* beyond "Coordinator" (automated matching is explicitly a separate, later stage); any product decision about identity/onboarding for a passenger who has never met any driver.
- **What would need to change.** New route(s) and screen(s); a Product Owner-level design pass equivalent in weight to the original `PIOS_TAXI_DESIGN_BRIEF.md`; almost certainly a new product-decision document (not necessarily a new ADR, but likely one, given it touches acquisition, trust framing, and possibly Coordinator's own scale limits) before implementation could begin under this repository's own "Documentation First" rule.

#### Option B — V1 stays entrepreneur/driver-led; Coordinator remains the Stage A fallback; no general passenger entry point

- **Frontend impact.** None required. This is the status quo (Task 18 Section 6) — every existing screen, route, and flow already matches this option exactly as built.
- **First Refusal impact.** First Refusal remains fully coherent under this option: every order it will ever see already has a specific driver context (the invitation link) and, per Task 17, `explicitDriverIntent: true` — meaning, concretely, **the automatic-First-Refusal branch of the decision tree (`explicitDriverIntentDeclared = false`) would essentially never fire for any real order under Option B**, since the one real order-creation path always sets the flag true. First Refusal's practical value under Option B would come almost entirely from a future, different trigger (e.g., a "call my primary driver directly, no specific invitation" affordance already visible as a manual button on `PassengerLanding.tsx`, per Task 15's own STOP-report finding), not from the `OrderSubmitted`-driven path Tasks 14–17 actually built. This is a real, concrete tension worth the Product Owner's own attention: Option B is consistent with the product's own ratified principles, but it also means the specific mechanism this five-task chain (14–17) implemented would have close to no real invocations unless a second, different trigger for it is separately built.
- **Coordinator impact.** None — Coordinator continues exactly as today, the ratified, named Stage A mechanism, not something Option B asks to change.
- **Commercial significance.** Preserves the "trust infrastructure, driver builds their own book of business" value proposition `PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` Section 9 names as one of PIOS's own distinct value pillars; caps passenger growth at the sum of each driver's own outreach.
- **What already exists.** Everything this option needs (Task 18 Section 3.2) — it is a decision to *keep* the current shape, not to build anything.
- **What does not exist.** Nothing new is required by this option itself.
- **What would need to change.** Nothing in code. Documentation: the Product Owner's own choice should be recorded (this task's own instruction is not to create an ADR without separate authorization — recording the *decision itself*, once made, in a product-decision document is a smaller, likely-still-required follow-up, not code).

#### Option C — Defer the decision until after pilot

- **Frontend impact.** None now. Preserves optionality — the domain model's own driver-agnostic `Order` (Section "what already exists" above) means neither Option A nor B forecloses the other later; no irreversible architectural choice is made by waiting.
- **First Refusal impact.** Same practical consequence as Option B for the duration of the deferral: the automatic branch will see effectively zero real traffic (every real order already sets `explicitDriverIntent: true`) until/unless Option A is later chosen — this is true regardless of C's own eventual outcome, simply because it is true today.
- **Coordinator impact.** None during the deferral period.
- **Commercial significance.** Lets the pilot's own real data (how often a passenger without an existing driver relationship tries and fails to use the product, if that is even observable — Task 18 Section 6 notes there is currently no way for such a passenger to reach the product at all, so this specific signal cannot be collected without at least a minimal, deliberately-instrumented entry point) inform a decision that is otherwise being made on projection alone.
- **What already exists / does not exist / would need to change.** Identical to Option B for the deferral period; the only addition is a placeholder decision-record noting the question is open, revisited after pilot.

### Recommendation (Product Owner decision, not made here)

**Recommendation: Option B for pilot, with the tension named under Option B's own First Refusal section treated as a real, near-term open question, not a footnote.** Evidence for this recommendation: Option B requires zero new work and matches everything already ratified and already built; Option A's own commercial case is real but would require a scope and design effort this task was not asked to evaluate in depth and that has no existing product-decision document behind it yet (unlike every other major mechanism in this codebase); Option C mostly just delays the same choice while collecting a signal ("passengers who can't use the product because they have no driver link") that the product currently has no way to observe anyway. **This is a recommendation, explicitly not a decision** — the Product Owner may weigh the commercial upside of Option A differently, and the First Refusal tension named above is itself an argument some Product Owners would read as favoring Option A sooner rather than later, not later.

## Part 2 — D2: Primary Driver UI

### Can a user designate a Primary Driver today?

**Yes, in production, right now.** `RideRequest.tsx`'s Circle-of-Trust step (`handleConfirmMakePrimary`) calls `POST /v1/connections/{connectionId}/primary` against Passenger Experience, which is live in production (its `connections`/`primary_connections` tables exist and predate this entire task chain — Task 18 Section 3.1). The action succeeds, is durably persisted, and the same screen's own state update (`setCircle`, re-sorting so the new primary sorts first) reflects it back to the passenger immediately, with the same "Основной" badge `DriverTrustIndicator` renders elsewhere. A passenger can also clear a primary designation the same way (`handleConfirmRemove`, `DELETE /v1/connections/{id}`, or implicitly by making a different connection primary instead).

### What happens after designation, right now?

Nothing beyond storage. `SetPrimaryConnectionApplicationService` (Passenger Experience) is written to also publish a `PrimaryConnectionDesignated` domain event through its own outbox — but Passenger Experience's production database has no outbox table at all (Task 18 Section 3.1), and its production jar predates the code that writes to one. The write to `primary_connections` succeeds; the event that is supposed to carry this fact to Dispatch is never produced. **No order, past or future, is affected by this action today, in production, in any way.**

### When will runtime actually be able to use this relationship?

Only after the full Part 3 deployment plan below is executed for all three affected services (Passenger Experience, for the outbox and event publication; Dispatch, for the `PrimaryDriverRecord` projection and the `OrderSubmittedFirstRefusalListener`) — and, separately, only for orders where `explicitDriverIntentDeclared` is `false` at submission (Part 1's own finding: today, that is never, for the one real order-creation path that exists).

### Is there a real UX risk here?

**Yes, and it is not hypothetical — it is the current, live behavior of a shipped screen.** A passenger who taps "Сделать основным" sees an immediate, confident, successful-looking confirmation (the badge moves, the sort order changes, no error is shown) for an action whose entire stated purpose — per this same screen's own surrounding UI and the product's own naming ("Primary Driver," "Основной") — is to influence future ride matching. It currently does not, and nothing in the UI discloses that. This is exactly the shape of UX debt this task asked to be named plainly: a control that works (in the narrow sense of "the button click succeeds and the state updates") while silently not doing the thing its own label promises.

### Recommendation (not implemented here)

**KEEP visible, but treat the wording gap as worth closing before broader pilot exposure — this task does not rank which of KEEP/DISABLE/reword is correct, only names the evidence.** Arguments for **KEEP**: the underlying relationship (Circle of Trust, "who is my usual driver") has real, independent value to a passenger even with zero routing effect — it is a legitimate, already-ratified feature (`ADR-054`) in its own right, not solely a means to First Refusal. Arguments for **DISABLE/hide the "primary" framing specifically** (not the whole Circle of Trust feature): if "Основной" is understood by passengers as "this driver gets my orders first," continuing to show it as fully functional while it has zero effect risks a trust cost once a passenger notices (e.g., their marked primary driver never gets first refusal on an order routed elsewhere) that is disproportionate to the feature's own current cost to disable. Arguments for a **wording/status change** (a middle option this task's own three choices allow for): a small, honest addition — e.g., a caption noting the designation is recorded but not yet used for routing — would resolve the gap without removing the feature's own independent value. This task does not choose among these three; it records that the current state (full-confidence UI, zero effect) is the one option this task's own evidence rules out as acceptable to leave unaddressed indefinitely.

## Part 3 — D3: Deployment Gate for Tasks 11–17

**No deployment, migration, or production/RabbitMQ connection was performed to produce this plan.** Every claim below is derived from reading the migration files, topology configuration classes, and application source directly.

### 3.1 Modules requiring rebuild + redeploy

| Module | Rebuild required | Why |
|---|---|---|
| Dispatch | **Yes** | Trip, PrimaryDriverRecord, `OrderSubmittedFirstRefusalListener`, the new RabbitMQ queues, `V12`–`V14` |
| Order Management | **Yes** | `explicitDriverIntent`/`isTest` on `OrderSubmitted`'s own payload, `V11` |
| Passenger Experience | **Yes** | The entire outbox subsystem, `PrimaryConnectionDesignated`/`Cleared` publication, `@EnableScheduling`, `V3` |
| Driver Management, Identity, ai-advisor, Network Management (undeployed), frontend | **No** | Nothing in this chain touched them (Task 18 Section 6; independently re-confirmed here: `git diff --stat` of the `b701c6c` checkpoint plus Task 17's own three files touches no file under any of these) |

### 3.2 Flyway migrations that must apply, and in what state they are found

| Module | Migration | Content | Risk |
|---|---|---|---|
| Dispatch | `V12__trips.sql` | New `trips` table | Low — purely additive, no existing table touched |
| Dispatch | `V13__primary_driver.sql` | New `primary_driver` table | Low — purely additive |
| Dispatch | `V14__proposals_one_open_per_order.sql` | `CREATE UNIQUE INDEX ... ON proposals (order_reference) WHERE status = 'OPEN'` | **The one migration in this set with real deployment risk.** A partial unique index on an *existing* table with *existing* production data will fail outright at migration time if production already has more than one `OPEN` proposal for the same order. Task 15C's own report verified zero such duplicates only in `pios_dispatch_test` — **production's own `proposals` table has never been checked for this**, and this task made no production connection to check it either. This must be a read-only pre-deployment check, not an assumption. |
| Order Management | `V11__add_order_explicit_driver_intent.sql` | `ADD COLUMN explicit_driver_intent BOOLEAN NOT NULL DEFAULT FALSE` | Low — additive, constant default (modern PostgreSQL applies this as a metadata-only change, no table rewrite) |
| Passenger Experience | `V3__outbox.sql` | New outbox table | Low — purely additive |

### 3.3 Deployment order

**Dispatch → Order Management → Passenger Experience**, each a separate, independently-verifiable step, not a simultaneous cutover. Reasoning, traced from the actual RabbitMQ topology classes:

1. **Dispatch first.** Its own topology classes (`RabbitMQOrderManagementTopologyConfiguration`'s new `order.submitted` queue, `RabbitMQPassengerExperienceTopologyConfiguration`) are consumer-side — Spring's `RabbitAdmin` declares the queue and its binding (and, transitively, the exchange it binds to) at Dispatch's own startup, before either producer has anything new to say. Deploying Dispatch first means the moment either producer *does* start publishing the new fields/events, nothing is lost waiting for a consumer to catch up. Deploying Dispatch alone is safe on its own: `OrderSubmittedFirstRefusalListener` defaults missing `explicitDriverIntent`/`isTest` fields to `false` (Section on the listener's own source, already read this session), so it tolerates being deployed ahead of Order Management's own still-old payload shape without error — it would simply behave as if every order has no explicit intent, which, combined with an also-still-empty `primary_driver` table, means `FirstRefusalApplicationService` would return `NoPrimaryDriver` for everything and create no proposals — inert, not broken.
2. **Order Management second.** Once deployed, `OrderSubmitted` starts carrying real `explicitDriverIntent`/`isTest` values; Dispatch, already listening, consumes them correctly from the moment Order Management restarts.
3. **Passenger Experience last.** Only once Dispatch's own `RabbitMQPassengerExperienceTopologyConfiguration` queue already exists (step 1) does Passenger Experience's own outbox relay start publishing `PrimaryConnectionDesignated`/`Cleared` without any risk of publishing into an exchange no queue is yet bound to.

### 3.4 RabbitMQ queues/exchanges this plan introduces

- `dispatch.from-order-management.order-submitted` (+ its own dead-letter queue), bound to the already-existing `order-management.events` exchange, routing key `order.submitted` — declared by Dispatch (Task 16).
- `dispatch.from-passenger-experience` (+ dead-letter queue), bound to a **new** exchange, `passenger-experience.events` (this exchange does not exist in production today — Passenger Experience has never had a producer topology before this chain) — declared by both Dispatch (consumer side) and Passenger Experience (producer side, `RabbitMQProducerTopologyConfiguration`); Section 3.3's own ordering ensures Dispatch's declaration happens first.

No existing queue, exchange, or binding is removed, renamed, or altered by this plan.

### 3.5 Compatibility between old and new runtime

Every migration is additive (Section 3.2); every new RabbitMQ queue is additive (Section 3.4); the `Assignment` aggregate's own fields and behavior are explicitly preserved, not removed, by Task 12's own deliberate deviation from `ADR-063`'s literal wording (a live consumer — `DriverHome.tsx` — and existing pilot data made deletion unsafe; this is why old `Assignment*` events keep publishing unmodified alongside the new `Trip*` ones). A partially-deployed state (e.g., Dispatch deployed, Order Management and Passenger Experience not yet) is therefore safe and inert, per Section 3.3's own step-by-step reasoning — not merely "probably fine," but traced through each specific consumer's own default-handling behavior.

### 3.6 Rollback strategy

Because every schema change is additive and no destructive `ALTER`/`DROP` exists anywhere in `V11`–`V14`/`V3`, the safe rollback for any single module is **redeploy its own previous jar** (the exact `-plain.jar`/boot jar pair currently on disk, unless overwritten by a later build) — the new tables/columns/indexes simply go unused again, identical to today's actual state. **Do not** attempt to reverse a Flyway migration itself (this project's own Flyway setup, like most, has no down-migration mechanism configured) — rolling back code, not schema, is the correct and sufficient recovery path here specifically because nothing was destructive in the first place.

### 3.7 Post-deployment checks

**Read-only checks (no explicit authorization needed beyond the deployment authorization itself, since they change nothing):**
- `flyway_schema_history` for each of the three services, confirming the expected version number.
- Table/column existence (`\d proposals`, `\dt`, etc.) matching Section 3.2.
- RabbitMQ management UI (read-only queue/exchange listing) confirming the two new queues (Section 3.4) exist and show zero unacked/ready-message backlog after a few minutes of normal traffic.
- Application log tail for each of the three services, watching for any startup error or repeated listener exception.

**Actions requiring explicit, separate authorization (these change or exercise production, not merely observe it):**
- The deployment/restart of each service itself (Section 3.3).
- Applying `V14` specifically — gate this on the pre-check named in Section 3.2's own risk row (a read-only `SELECT` for duplicate `OPEN` proposals per order, run *before* the restart that would trigger the migration, with an explicit go/no-go decision if any are found — not something to resolve improvisationally mid-deployment).
- Running any real order end-to-end against production after deployment (even one flagged `isTest: true`) — this is a production write, and per this project's own repeated precedent (every task in this chain reserves this kind of check for explicit authorization), it should not be treated as merely "verification."

## Part 4 — Security Gate: Proposal Caller-Identity Gap

Re-confirmed directly against current source (not assumed from Task 13's or Task 18's own prior wording).

### Which endpoints, exactly

`POST /v1/proposals` (create), `POST /v1/proposals/{id}/accept`, `POST /v1/proposals/{id}/decline`, `POST /v1/proposals/{id}/lapse` — all four read no `Authorization` header at all (confirmed by direct source read of `ProposalController.kt`; only `GET /v1/proposals?driverId=` has any check, added by ADR-060).

### Which real actors can call these endpoints today

**Anyone who can reach these paths over HTTP — and, concretely, that is not limited to Dispatch's own local network today.** `frontend/vite.config.ts`'s own `preview.proxy` table forwards `/v1/proposals` and `/v1/assignments` from the production frontend's own public origin straight to `http://localhost:8084`, and the production frontend is itself reachable at two public hostnames through the already-deployed Cloudflare tunnel (`piosapp.ru` and `home-pc.tail385153.ts.net`, both listed in `vite.config.ts`'s own `allowedHosts`; the tunnel's own service definition confirms it points only at the frontend, but the frontend's own proxy is exactly what re-exposes these specific backend paths publicly). **This means `POST https://piosapp.ru/v1/proposals/{id}/accept` is, today, a real, publicly-reachable, unauthenticated write endpoint for anyone who can guess or otherwise obtain a proposal id** — not a theoretical "if someone had internal network access" risk.

### What identity information already exists

Both authorization primitives this gap would need already exist and are already used elsewhere in this exact controller: a `sessionTokenVerifier` yielding a verified `drv` (driver id) claim from a `Bearer` token (used today only by `GET /v1/proposals?driverId=`), and an `ownerCredentialGate` verifying `Authorization: Basic` against the single owner/coordinator credential (used today only by the same endpoint, and by `/coordinator`'s own page-level gate). **Neither is wired to `accept`/`decline`/`lapse`/`create`.** Independently confirmed: even the legitimate callers of these endpoints today send no identity at all — `DriverHome.tsx`'s own `respondToProposal` (accept/decline) sends no `Authorization` header despite already holding the driver's own `identity.token` in scope for other calls on the same screen; `Coordinator.tsx`'s own `handleAssign`/`handleCheckStatus` likewise send none, by that screen's own explicit KDoc ("Neither this call... was ever gated... only the page itself is gated, not these two actions").

### What is missing

A caller-identity check on all four endpoints, verifying (at minimum) that `accept`/`decline` is called by the driver actually named on that `Proposal` (via the already-existing `sessionTokenVerifier`, mirroring the pattern `?driverId=` already uses), and that `create`/`lapse` are restricted to a caller with a legitimate reason to act on the named order (a passenger's own token verified against the order's own origin, or the owner credential) — the exact shape of "legitimate reason" for `create` in particular is itself a product/authorization-model question this audit does not resolve, only names as missing.

### P0/P1/P2 and pilot-blocking assessment

**P1 — necessary before any pilot exposure broader than a fully trusted, closed group, not P0 only because the current pilot's own actual user base is small and known** (this reasoning must not be read as "therefore ignore it" — see below). It is not classified P0 in the strict "blocks V1 outright" sense Task 18's own P0/P1 convention used, because the *feature set* of V1 does not structurally require this fix to function for its own intended, trusted pilot users. But the finding in this Part (public reachability via the already-deployed tunnel) means the actual **exploitability** is already live today, independent of anything in Tasks 11–17 or this deployment plan — this is not a "before deploying new work" gate, it is a **currently-open exposure on the already-running production system**, and should be weighed accordingly by whoever owns pilot risk tolerance, not deferred as if it were contingent on the rest of this document's own deployment plan.

## Part 5 — Production Reality Matrix

| Capability | Source | Tests | Production | User-visible |
|---|---|---|---|---|
| Trip (aggregate, dual-publish) | ✅ exists | ✅ `TripTest`, `TripIdTest`, `DispatchAssignmentApplicationServiceRideProgressConvergenceTest`, etc. — Dispatch 417/417 green | ❌ not deployed (`V12` not applied, no `trips` table) | ❌ |
| Trip ride-progress (arrive/start/complete via Trip) | ✅ exists | ✅ covered by the same Dispatch suite | ❌ — production still runs the pre-Task-11, Assignment-only path | ✅ (via `Assignment`, not `Trip` — the passenger/driver-visible ride-progress states are real today, just not through this new code) |
| Primary Driver (`PrimaryDriverRecord` projection) | ✅ exists | ✅ `PrimaryDriverProjectionApplicationServiceTest`, consumer integration tests | ❌ not deployed (`V13` not applied, no `primary_driver` table) | ➖ the passenger-facing *designation* action is user-visible and real (Part 2); the *projection* this row names is not |
| First Refusal (automatic proposal to primary driver) | ✅ exists | ✅ `FirstRefusalApplicationServiceTest`, `OrderSubmittedFirstRefusalConsumerIntegrationTest`/`ConcurrencyTest` — all green | ❌ not deployed, and (Part 1) would see near-zero real invocations even once deployed, under the current, only-real order flow | ❌ |
| Explicit driver intent (`explicitDriverIntent`) | ✅ exists, and the one real frontend caller (`RideRequest.tsx`, Task 17) sends it | ✅ Order Management + Dispatch suites, both green | ❌ `V11` not applied — the column does not exist in production; the field is silently dropped on arrival today | ❌ (invisible — has no observable effect either way today) |
| `OrderSubmitted` → `passengerReference` in the outbox payload | ✅ exists (Task 15C) | ✅ `OrderSubmittedEnvelopeTest` and related | ❌ not deployed — production's own `OrderSubmitted` payload predates this field | ❌ |
| Proposal uniqueness (`proposals_one_open_per_order`) | ✅ exists | ✅ `ProposalConcurrencyTest`, real concurrent-thread proof | ❌ `V14` not applied — production relies solely on the older, in-memory-only check (still real and still enforced, just weaker) | ➖ invisible to a user either way; matters only under genuine concurrency |
| Ordinary driver-less ordering | ❌ does not exist anywhere (Part 1) | — | — | ❌ |
| Coordinator fallback (manual matching) | ✅ exists, predates this chain | ✅ (pre-existing coverage, unaffected) | ✅ **live** | ✅ (to staff only — not passenger-visible) |

## Part 6 — Final Gate

| Item | Status |
|---|---|
| Current production behavior (explicit-driver ordering, Proposal/Assignment/ride-progress lifecycle, Coordinator fallback) | **GO NOW** — already live, already working, unaffected by anything in Tasks 11–19 |
| Deploying Tasks 11–17 (Trip, Primary Driver, First Refusal, explicit-intent wiring) as a technical rollout | **DEPLOYMENT REQUIRED** — Part 3 is the plan; blocked only on authorization and the `V14` pre-check, not on any unresolved technical question |
| Whether driver-less/general ordering (D1) belongs in V1 | **DECISION REQUIRED** — Part 1; no default answer exists in this repository today |
| Primary Driver UI wording/visibility (D2) | **DECISION REQUIRED** — Part 2; three named options, none chosen here |
| Treating "First Refusal is complete" as meaning the target user flow is complete | **BLOCKED** on D1 — per Part 1, the automatic-First-Refusal branch has near-zero real invocations under the current, only-real order flow; deploying it alone does not deliver the feature's own full intended value |
| Proposal `accept`/`decline`/`lapse`/`create` public exposure | **SECURITY REQUIRED** — Part 4; already-live exposure on the currently-running production system, independent of this deployment plan, and not something to defer behind the rest of this gate |

### What NOT to do now

- Do not deploy Tasks 11–17 before the `V14` duplicate-proposal pre-check (Part 3.2/3.7) has been run and reviewed.
- Do not deploy Tasks 11–17 all at once, out of the order Part 3.3 establishes, or as a single simultaneous cutover.
- Do not treat a Tasks-11–17 deployment, by itself, as "First Refusal is now live for users" — under Option B/C of Part 1 (today's actual state), it would not meaningfully be, for the reasons Part 1's own First Refusal analysis names.
- Do not change, hide, or reword the Primary Driver UI unilaterally — Part 2 names three real options and does not choose one.
- Do not build a driver-less ordering flow (Option A) without a separate, explicit Product Owner decision and — per this repository's own "Documentation First" rule — a product-decision document ahead of any implementation.
- Do not fix the Proposal caller-identity gap as a side effect of this or a future unrelated task — it is real, already-exploitable, and deserves its own explicitly-scoped fix, not an incidental patch.
- Do not create a new ADR for any of the above without separate authorization, per this task's own instruction.
- Do not make any further production, RabbitMQ, or other live-infrastructure connection to "double check" any finding in this document — everything above is already sourced from code, tests, and local file inspection only.
