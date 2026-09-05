# Task 26 — PIOS Taxi V1 Production Deployment Readiness Audit

**Type:** Strictly read-only audit. No code, migration, configuration, ADR, or frontend file was modified. The only output of this task is this report.

**Method:** Every finding below is derived from re-reading the current working tree directly (source files, migrations, RabbitMQ topology classes, WinSW service XML, `application.yml`, `vite.config.ts`) and from local, non-invasive filesystem inspection (file modification timestamps). No production PostgreSQL connection, no production RabbitMQ connection, no production HTTP call, and no service restart was made during this task. Previous task reports (18–25) were treated as claims to re-verify, not as facts — every load-bearing claim below was re-checked against the current file contents in this session, not carried over from memory.

---

## 1. Executive Summary

Tasks 11–25 built a coherent, additive body of work — the Trip aggregate (ADR-063), Primary Driver / First Refusal (ADR-062), and a security-remediation arc (Tasks 20–25) that closed authentication gaps on `/v1/proposals`, `/v1/assignments`, `POST /v1/orders/{id}/cancel`, and `POST /v1/drivers/{id}/availability`. None of it is built into the current local build artifacts, and — per Tasks 18/19/22/24's own already-established non-invasive method, re-confirmed here — none of it can be assumed running in production either.

The repository is in a **pre-deployment, uncommitted** state: every file this arc touched is still an uncommitted working-tree change (`git status`), exactly as Task 25 was instructed to leave it.

Every schema change in scope (V12, V13, V14 in dispatch; V11 in order-management) is **additive** — new tables, a new nullable-free boolean column with a safe default, and a new partial unique index. Nothing in scope drops a column, renames a table, or changes a type. An additive migration-first strategy is safe **conditioned on one explicit pre-check**: V14's partial unique index can fail at migration time if production `proposals` already holds more than one `OPEN` row for the same `order_reference`. Section 10 gives the exact query to run first.

One new, previously unreported risk was found by this audit: **driver-management has never before required `pios.session.secret`.** Task 25 added the module's first `SessionTokenVerifier`. If the production `pios-driver-management` Windows service does not already have `PIOS_SESSION_SECRET` (or `pios.session.secret`) configured identically to `identity`'s own secret, `POST /v1/drivers/{id}/availability` will fail closed (401) for every driver the moment the new jar is deployed — a regression from "unauthenticated but working" to "authenticated but broken," not from "broken" to "fixed." This must be confirmed **before** deploying driver-management's new jar. See Section 9.

**Classification: READY WITH PRE-CHECK.** No BLOCKED condition was found. No item in scope requires a new architectural or product decision. Two concrete pre-checks (Section 10, Section 9) gate the deployment; neither requires code changes, only operator verification.

---

## 2. Current Source/Runtime Reality

Re-verified this session, not assumed from prior reports:

- **Git state:** working tree on `pios-product-main`, nothing from this arc committed. `git log` HEAD is `b701c6c feat(pios-taxi): complete first refusal runtime integration` — the last *commit* predates Tasks 17–25 entirely; all of Tasks 17–25's work exists only as uncommitted changes plus new untracked files (12 files from Task 25 alone, per that task's own report, re-confirmed present in `git status --short` output this session).
- **Local build artifacts are stale relative to source** — confirmed via the same non-invasive jar-timestamp-vs-source-timestamp method Task 18 established (no production connection):

  | Module | Local jar mtime | Security-fix source mtime | Stale? |
  |---|---|---|---|
  | dispatch | 2026-09-02 19:14 | `ProposalController.kt` 2026-09-03 17:07, `AssignmentController.kt` 2026-09-03 18:17 | **Yes** |
  | order-management | 2026-09-02 19:14 | `OrderCancellationController.kt` 2026-09-03 20:35 | **Yes** |
  | driver-management | 2026-09-02 19:14 | `DriverController.kt` / new `SessionTokenVerifier.kt` 2026-09-03 20:36–37 | **Yes** |
  | identity | 2026-08-12 16:31 | untouched by this arc | n/a |
  | passenger-experience | 2026-08-12 16:34 | untouched by this arc | n/a |

  This proves the **local** build artifacts don't yet contain Tasks 21/23/25's fixes — a rebuild is mandatory regardless of what is running in production. It does not, and cannot, prove what jar is actually loaded by the live Windows services (that would require production filesystem or process access, which this task does not have and did not attempt). Combined with Task 18's own finding (a real, disclosed production query at the time showed the deployed schema stopped before this arc's migrations existed) and the fact that nothing in this arc has been committed, the practical conclusion carried forward is the same one Tasks 18/19/22/24 already reached: **treat none of Tasks 11–25 as deployed until proven otherwise by the operator.**
- **Frontend service** (`pios-frontend`) serves a static `vite preview` build from `frontend/dist/`, not live source (`windows-services/frontend/pios-frontend.xml`). A source change has zero runtime effect until `npm run build` regenerates `dist/`.
- Migrations auto-apply on backend service startup: every module's `application.yml` sets `spring.flyway.enabled: true` (confirmed in dispatch's `application.yml:8-10`; same pattern in every module inspected). There is no separate manual migration step — restarting a service with an updated jar **is** the migration step.
- The working tree also contains a substantial body of **uncommitted work outside this audit's stated scope** — a frontend design-system rollout (`frontend/src/components/`, `frontend/src/styles/`, `index.css`), AI Advisor provider additions (Ollama/Qwen), and other unrelated changes already present in `git status` before Task 17 began. These are **not evaluated by this report** and are called out only so this report's file-by-file diff isn't mistaken for the repository's entire uncommitted state.

---

## 3. Component Deployment Matrix

| Component | Source state | Required artifact | Required migration | RabbitMQ change | Restart required | Depends on | Backward-compat requirement | Rollback | Risk |
|---|---|---|---|---|---|---|---|---|---|
| Dispatch — Trip aggregate (Task 11) | Uncommitted, complete | Rebuilt `dispatch` jar | V12 (new tables) | None | dispatch | — | New tables only; no consumer of Trip data outside dispatch yet | Drop `trips` table (no other table references it) | LOW |
| Dispatch — Primary Driver / First Refusal (Tasks 14–16) | Uncommitted, complete | Rebuilt `dispatch` jar | V13 (new tables) | New queues: `dispatch.from-passenger-experience`(+dlq), `dispatch.from-order-management.order-submitted`(+dlq) | dispatch | passenger-experience (producer), order-management (producer) must already be publishing `PrimaryConnectionDesignated/Cleared` and `OrderSubmitted` — both already exist pre-arc | New queues only; existing `dispatch.from-order-management` (OrderCancelled) queue untouched | Unbind/delete the two new queues; drop `primary_driver_records`/`primary_driver_processed_events` | LOW |
| Dispatch — Proposal concurrency safety (Task 15C) | Uncommitted, complete | Rebuilt `dispatch` jar | **V14 (new partial unique index — see Section 10 pre-check)** | None | dispatch | — | None — enforces an invariant the application already believed true | `DROP INDEX proposals_one_open_per_order` | LOW, conditional on pre-check passing |
| Dispatch — Proposal API auth (Task 21) | Uncommitted, complete | Rebuilt `dispatch` jar | None | None | dispatch | identity (secret must match) | **Breaking for any caller not sending `Authorization: Bearer`** — frontend already updated in the same uncommitted tree | Redeploy prior jar (git stash/checkout of `ProposalController.kt`) | MEDIUM (auth-behavior change, mitigated: frontend deploys atomically with backend, see Section 8) |
| Dispatch — Assignment API auth (Task 23) | Uncommitted, complete | Rebuilt `dispatch` jar | None | None | dispatch | identity (secret must match) | Same as above, for `arrive`/`start`/`complete` only | Redeploy prior jar | MEDIUM |
| Order Management — cancel auth (Task 25) | Uncommitted, complete | Rebuilt `order-management` jar | None | None | order-management | identity (secret must match); order-management **already has** `SessionTokenVerifier` from ADR-060 | Breaking for any caller not sending `Authorization` | Redeploy prior jar | MEDIUM |
| Driver Management — availability auth (Task 25) | Uncommitted, complete | Rebuilt `driver-management` jar | None | None | driver-management | **identity — secret must be newly provisioned on this module, see Section 9** | Breaking for any caller not sending `Authorization` | Redeploy prior jar | **MEDIUM–HIGH** until Section 9's pre-check is confirmed |
| Order Management — `explicitDriverIntent` (Task 15C/17) | Uncommitted, complete | Rebuilt `order-management` jar | V11 (additive column, default `false`) | None (payload field added to existing `order.submitted` message, additive) | order-management | — | Additive; old consumers ignore the new field, new consumers get `false` for pre-migration rows | `ALTER TABLE orders DROP COLUMN explicit_driver_intent` | LOW |
| Frontend — Task 17/21/23/25 changes | Uncommitted, complete | `npm run build` → new `dist/` | n/a | n/a | pios-frontend (or dist replace, see Section 8) | Must not go live before the backend jars it calls with `Authorization` headers, else calls that used to work 401 against an old, still-unauthenticated backend are a non-issue, but calls against a **new, authenticated** backend **without** this frontend would fail — ordering matters, see Section 8 | Redeploy prior `dist/` | LOW (frontend-only change, but sequencing with backend matters) |

---

## 4. Migration Inventory (complete, all modules, source-of-truth re-read this session)

| Module | Migrations | Highest version | In scope for this arc |
|---|---|---|---|
| dispatch | V1–V14 | V14 | V12, V13, V14 |
| order-management | V1–V11 | V11 | V11 |
| driver-management | V1–V5 | V5 | None (Task 25 added no migration — auth-only change) |
| identity | V1–V3 | V3 | None |
| passenger-experience | V1–V3 | V3 | None |
| network-management | V1 | V1 | None (module undeployed per Tasks 18/24) |

Every migration file's `src/main/resources/db/migration/<module>` copy and its `build/resources/main/db/migration/<module>` copy were compared this session — identical in content for every file inspected (V12–V14 dispatch, V11 order-management, V5 driver-management); the `build/` copies are simply Gradle's resource-processing output of the same source files, confirming no drift between what's on disk and what a fresh build would package.

## 5. Migration Dependency Graph

```
dispatch:
  V11 (proposal/assignment is_test) → V12 (trips) → V13 (primary_driver_records) → V14 (proposals_one_open_per_order)
    V12 depends on V1/V5/V8/V11 columns existing (assignments table shape) — already applied, out of scope
    V14 depends on the `proposals` table (V4) and `status` column (V4) — already applied, out of scope
    V13 has no dependency on V12/V14 — could apply in any order relative to them, but Flyway applies strictly by version number regardless

order-management:
  V11 (explicit_driver_intent) depends only on V1 (orders table) — already applied, out of scope

driver-management:
  no migration in scope this arc (V5 already applied per Task 18/24's own findings)
```

All four in-scope migrations (V12, V13, V14 dispatch; V11 order-management) are **independent of each other across modules** — none references another module's schema (module-local databases, ADR-005/ADR-009 isolation). Within dispatch, Flyway's own strict-ordering guarantee applies them V12 → V13 → V14 in one sequential run; there is no scenario where V14 could apply before V12/V13 on a correctly-configured Flyway instance.

## 6. RabbitMQ Topology Deployment Plan

Full topology re-read from source this session (all `RabbitMQ*Topology*.kt` classes across dispatch, order-management, driver-management, passenger-experience):

| Producer exchange | Owning module | Consumer queue | Owning module | Binding key | DLQ | In scope |
|---|---|---|---|---|---|---|
| `driver-management.events` | driver-management | `dispatch.from-driver-management` | dispatch | `driver.availability.changed` | `dispatch.from-driver-management.dlq` | Pre-existing |
| `dispatch.events` | dispatch | `order-management.from-dispatch` | order-management | `assignment.accepted` | `order-management.from-dispatch.dlq` | Pre-existing |
| `dispatch.events` | dispatch | `order-management.from-dispatch.assignment-completed` | order-management | `assignment.completed` | `order-management.from-dispatch.assignment-completed.dlq` | Pre-existing (ADR-041) |
| `order-management.events` | order-management | `dispatch.from-order-management` | dispatch | `order.cancelled` | `dispatch.from-order-management.dlq` | Pre-existing |
| `order-management.events` | order-management | `dispatch.from-order-management.order-submitted` | dispatch | `order.submitted` | `dispatch.from-order-management.order-submitted.dlq` | **New — Task 16, First Refusal** |
| `passenger-experience.events` | passenger-experience | `dispatch.from-passenger-experience` | dispatch | `primary.connection.designated` **and** `primary.connection.cleared` (same queue, two bindings) | `dispatch.from-passenger-experience.dlq` | **New — Task 14, First Refusal Foundation** |

**Every queue, DLQ, exchange, and binding is declared as a Spring `@Bean` and created idempotently by Spring AMQP's `RabbitAdmin` on application startup** — there is no manual `rabbitmqadmin`/management-UI step. Deployment order therefore only matters for **exchange existence at binding time**, and every consumer-side topology class already re-declares its producer's exchange with identical parameters specifically so binding succeeds regardless of which module's Spring context starts first (explicit in each class's own KDoc, re-confirmed this session). **No RabbitMQ topology creation ordering constraint exists across dispatch/order-management/passenger-experience/driver-management** — any startup order is safe.

Trip (ADR-063) publishes **no** RabbitMQ event of its own — confirmed by grep across `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/` for `TripStarted`/`TripArrived`/`TripCompleted`: no publisher references them. Trip state is entirely local to dispatch, driven by the already-secured (Task 23) `arrive`/`start`/`complete` Assignment endpoints. No new topology entry is needed for Trip.

## 7. WinSW Deployment Plan

All five backend service definitions re-read this session (`windows-services/{dispatch,order-management,driver-management,identity,passenger-experience}/*.xml`). Each is structurally identical: `java -jar build\libs\<module>-0.1.0-SNAPSHOT.jar`, working directory the module's own folder, `PIOS_PILOT_FRONTEND_ORIGIN` + `PIOS_OWNER_USERNAME`/`PIOS_OWNER_PASSWORD_HASH`/`PIOS_OWNER_PASSWORD_SALT` as the only checked-in secrets, WinSW's own log disabled in favor of the app's Logback file, and a 30s/60s/120s restart backoff.

**None of the five checked-in service XML files sets `PIOS_SESSION_SECRET` (or any `pios.session.*` env var).** This is a deliberate, documented convention (each module's `application.yml` comment explicitly says the secret is "left unset here" so the fail-closed default applies to a clean checkout) — the actual production secret, if configured, lives outside this repository (an OS-level environment variable on the production host, or a locally-held, untracked copy of the XML/service config). This audit cannot and did not attempt to confirm which is true for the live services — that would require production host access. See Section 9 for why this specifically matters for driver-management.

Since every backend module runs Flyway on startup, **a WinSW restart with an updated jar is simultaneously the deployment and migration step** for that module — there is no separate "run migrations" action.

## 8. Frontend Deployment Plan

`pios-frontend` serves `frontend/dist/` via `vite preview --port 4173`, a static build — re-read from `windows-services/frontend/pios-frontend.xml` this session. Deploying frontend changes is `npm run build` followed by either a WinSW restart of `pios-frontend` or, since `vite preview` reads files from disk on each request, simply overwriting `dist/` in place (a restart is still the safer, verifiable option and is what's specified in Section 12).

**Sequencing constraint:** the Task 21/23/25 frontend changes (`RideRequest.tsx`, `DriverHome.tsx`, `Coordinator.tsx` now sending `Authorization` headers) must not go live **before** the corresponding backend jars that expect those headers, and the backend jars must not go live **before** the frontend that supplies them — an old frontend hitting a newly-secured backend without sending the header gets 401 on every proposal/assignment/cancel/availability action; a new frontend hitting an old, unauthenticated backend just sends a harmless extra header. **The safe order is therefore: deploy the backend jars and the frontend build together, backend jars first (by seconds, not by a separate release), frontend immediately after** — never frontend-then-wait or backend-then-wait-a-long-time. Exact sequence in Section 13.

## 9. Security Deployment Considerations

Re-confirms and extends Tasks 20–25's own findings; the material new item is the driver-management secret gap.

1. **Session secret propagation (NEW finding, this task).** `SessionTokenVerifier.verify()` (identical logic replicated in dispatch/order-management/passenger-experience/identity/driver-management, re-read this session) returns `null` — and therefore every caller sees `401` — whenever `pios.session.secret` is blank. `SessionTokenIssuer.issue()` (identity) throws under the same condition, meaning **if login/registration already works in production today, `pios.session.secret` is already configured on identity and on every module that already depended on it before this arc** (order-management via ADR-060, passenger-experience via ADR-055 itself). **Driver-management is the one exception**: it had zero authentication infrastructure before Task 25 (confirmed by Task 22's own audit and re-confirmed this session — no `SessionTokenVerifier.kt` existed in driver-management before this uncommitted change). **Before deploying driver-management's new jar, the operator must confirm `PIOS_SESSION_SECRET` is set on the `pios-driver-management` Windows service and is byte-for-byte identical to identity's own secret.** If it is not, every driver's "go online" toggle will start returning 401 the moment the new jar is deployed — a regression, not a fix.
2. **Frontend/backend deployment atomicity** — see Section 8. This is the same category of risk Task 21's own STOP-condition analysis already flagged for Proposal/Assignment; Task 25 extends it to order cancellation and driver availability.
3. **No new ADR, no new authentication mechanism.** Every fix in scope (Tasks 21/23/25) reused `SessionTokenVerifier`/`OwnerCredentialGate` exactly as they already existed; ownership comparisons reused ADR-060's already-ratified `Order.origin`-is-passenger-identity model and the `drv` claim already defined by ADR-055. This audit found nothing that requires new architectural authorization to deploy.
4. **Public reachability confirmed unchanged and complete**, re-read from `frontend/vite.config.ts` this session: `/v1/drivers` → 8081 (driver-management), `/v1/orders` → 8083 (order-management), `/v1/proposals` and `/v1/assignments` → 8084 (dispatch) are all proxied through the deployed pilot frontend origin — the exact same public surface Tasks 19/20/24 already established as exploitable pre-fix. This confirms the fixes in scope close real, not merely theoretical, exposure once deployed.

## 10. V14 Duplicate-OPEN Pre-Check

**This is the one migration in scope that can fail at apply time.** `V14__proposals_one_open_per_order.sql` creates `CREATE UNIQUE INDEX proposals_one_open_per_order ON proposals (order_reference) WHERE status = 'OPEN'`. Index creation fails outright — and the entire dispatch service fails to start, since Flyway aborts startup on a failed migration — if production `proposals` currently contains more than one row with `status = 'OPEN'` for the same `order_reference`.

**Exact read-only query to run against production `pios_dispatch` immediately before deploying dispatch's new jar** (NOT executed by this task):

```sql
SELECT order_reference, COUNT(*) AS open_count
FROM proposals
WHERE status = 'OPEN'
GROUP BY order_reference
HAVING COUNT(*) > 1;
```

- **Zero rows returned:** V14 will apply cleanly. Proceed.
- **One or more rows returned:** V14 will fail dispatch's startup. Do not deploy the new dispatch jar until every returned `order_reference` is manually resolved (e.g., transitioning all but one OPEN proposal per order to a resolved status via the existing, already-authenticated decline/lapse path) — this is an operational data-cleanup decision, not a code change, and this task does not perform it.

Task 15C's own pre-migration investigation (cited in `V14`'s own migration comment, re-read this session) found zero such rows in the isolated `pios_dispatch_test` database at that time, and reasoned from `Proposal`'s own transition-method preconditions that the application logic should make this structurally impossible going forward — but that was a test-database check, not a production one, and is now stale relative to whatever has happened in production since. **This query is the one production-facing action this entire deployment plan requires before the dispatch migration step, and it is read-only.**

## 11. Compatibility Analysis

- **`OrderSubmitted` payload:** `explicitDriverIntent: Boolean = false` and `isTest: Boolean = false` (re-read from `OrderSubmitted.kt` this session) are both defaulted fields added to an existing message shape under an unchanged routing key (`order.submitted`) and unchanged exchange (`order-management.events`). A consumer running old code that doesn't know these fields exist simply ignores them (Jackson's default non-strict deserialization); a consumer running new code processing a message from an old producer that never set them gets the documented default (`false`), which is explicitly the intended "no known value supplied" meaning per the migration's own comment. No breaking change either direction.
- **Driver availability / order cancellation endpoint contracts:** request/response JSON shapes are unchanged by Tasks 21/23/25 — only the `Authorization` header requirement and the resulting `401`/`403` status codes are new. This is a client-compatibility change, not a wire-format one; it's addressed by Section 8's sequencing, not by payload versioning.
- **`proposals_one_open_per_order` index:** purely a database-level invariant; no application code path changes behavior when the constraint holds (every code path already believed it true). It only becomes visible to a caller if application code somehow attempted to violate it, which Section 10's pre-check is designed to catch before that can happen against real data.

## 12. Rollback Analysis

| Change | Rollback method | Once applied, can it be cleanly reversed? |
|---|---|---|
| V12 (`trips`, `trip` FK to `assignments`) | `DROP TABLE trips;` | Yes — no other table references `trips` |
| V13 (`primary_driver_records`, `primary_driver_processed_events`) | `DROP TABLE primary_driver_records; DROP TABLE primary_driver_processed_events;` | Yes — both are Dispatch-local projections, source of truth stays in passenger-experience |
| V14 (unique index) | `DROP INDEX proposals_one_open_per_order;` | Yes, trivially — but see below |
| V11 order-management (`explicit_driver_intent` column) | `ALTER TABLE orders DROP COLUMN explicit_driver_intent;` | Yes, but loses recorded intent for any order submitted after the column was added |
| Auth changes (Proposal/Assignment/Cancel/Availability) | Redeploy the prior jar (no migration involved) | Yes, cleanly — these are code-only changes |
| Frontend `Authorization` header additions | Redeploy the prior `dist/` build | Yes, cleanly |

**Rollback limitation once migrations are applied:** rolling back V12/V13/V14/V11 (dropping tables/columns/index) is mechanically simple, but if the corresponding **application code has already run** against the new schema (e.g., Trips have been created, primary-driver records have been projected, or a request was rejected by the new unique index), rolling back the schema **without** first rolling back the application jar to matching code will crash the running application on its next query against the now-missing table/column — Flyway migrations in this codebase are forward-only (no `undo` scripts exist for any module, confirmed by the migration inventory in Section 4 containing no `U`-prefixed files). **The safe rollback order is always: revert the jar first, then the schema, never the reverse**, and only if the schema rollback is actually needed (in practice, for every migration in scope here, leaving the additive schema in place while rolling back only the jar is simpler and sufficient — the new tables/columns/index are inert to old code).

## 13. Exact Deployment Sequence (Windows/WinSW)

Given: no RabbitMQ topology ordering constraint (Section 6), no cross-module migration dependency (Section 5), and the one sequencing constraint that matters (backend auth changes and frontend header changes must go live together, Section 8).

1. **Pre-flight (no service touched):**
   a. Run Section 10's duplicate-OPEN query against production `pios_dispatch`. Confirm zero rows.
   b. Confirm `PIOS_SESSION_SECRET` (or `pios.session.secret`) is set on the production `pios-driver-management` service and matches identity's own secret byte-for-byte (Section 9, item 1).
   c. `git status` on the production checkout to confirm what's actually different from what's currently running, if the production checkout is a separate clone/pull from this repository.
2. **Build every changed backend module** (`./gradlew.bat :dispatch:build :order-management:build :driver-management:build`, or the module-scoped equivalent) — produces fresh jars containing Tasks 11–25's changes and this session's stale jars (Section 2) are superseded.
3. **Build the frontend** (`npm run build` in `frontend/`) — produces a fresh `dist/` containing Tasks 17/21/23/25's `Authorization`-header changes.
4. **Stop, in any order** (no inter-dependency at stop time): `pios-dispatch`, `pios-order-management`, `pios-driver-management`.
5. **Replace each jar** with its freshly built artifact (WinSW's `<arguments>` already point at the fixed `build\libs\<module>-0.1.0-SNAPSHOT.jar` path — no XML change needed unless the JAR filename itself changes).
6. **Start, in any order:** `pios-dispatch`, `pios-order-management`, `pios-driver-management`. Each triggers its own Flyway migration on startup (dispatch: V12→V13→V14; order-management: V11) as part of the same start action — watch each service's own log for a successful Flyway run before considering that module started. If dispatch's V14 fails despite step 1a, stop immediately — this means production changed between the pre-flight check and this step; do not proceed to any other module.
7. **Replace `frontend/dist/`** with the fresh build **immediately after** step 6 confirms all three backend services are healthy (Section 14's smoke tests, run against the backends directly first) — minimizing, though not eliminating, the window where an old frontend could hit a newly-secured backend. Restart `pios-frontend` (or rely on `vite preview`'s static file serving — a restart is the verifiable choice).
8. **identity and passenger-experience are untouched by this arc** (Section 2) — no restart required for either.

No step above requires touching `pios-identity` or `pios-passenger-experience`, and no step requires manual RabbitMQ administration.

## 14. Post-Deployment Smoke Test Checklist

Run against the live pilot surface after Section 13 completes, in this order:

- [ ] **Application health:** `GET /v1/health/dispatch`, `/v1/health/order-management`, `/v1/health/driver-management` (via the frontend's own proxy paths) all return healthy.
- [ ] **Order creation:** submit a real order as a passenger; confirm `201`/success and that the order appears with `explicit_driver_intent` and `is_test` present in its persisted row (schema-level confirmation V11/V12-adjacent columns are live).
- [ ] **Cancellation:** the order's own owning passenger cancels it — expect success; a different passenger's token attempting to cancel the same order — expect `403`; no token — expect `401`.
- [ ] **Driver availability:** the owning driver toggles availability on — expect success **(this is the one check most likely to fail if Section 9 item 1's pre-check was skipped — a 401 here means the driver-management secret is missing)**; a different driver's token against the same `driverId` — expect `403`.
- [ ] **Proposal creation:** create a proposal for an available driver — expect success with a valid token; expect `401` with none.
- [ ] **Proposal accept/decline:** the proposed driver accepts — expect success and an Assignment created; a second proposal attempt for the same still-open order — expect the database to reject it if it would violate `proposals_one_open_per_order` (proves V14 is live and enforcing).
- [ ] **First Refusal:** submit an order for a passenger with a designated primary driver and no explicit driver intent — expect exactly one OPEN proposal auto-created for that primary driver (proves the `order.submitted` → dispatch consumer path and `primary_driver_records` projection are both live).
- [ ] **Trip lifecycle:** `arrive` → `start` → `complete` an Assignment as its own assigned driver — expect each to succeed and the underlying Trip's status to advance; the same calls from a non-assigned driver's token — expect `403`.
- [ ] **Assignment lifecycle:** confirm `assignOrder` (still unauthenticated by design, Task 22/23's own deliberate scope boundary) continues to function for whatever internal caller uses it — this task did not change it and does not expect its behavior to change.
- [ ] **RabbitMQ event delivery:** confirm (via the RabbitMQ management UI, not a database or API call) that `dispatch.from-order-management.order-submitted`, `dispatch.from-passenger-experience`, and both existing pre-arc queues show message rates consistent with the smoke-test actions just performed, and that no DLQ has accumulated unexpected messages.
- [ ] **Owner Control Center:** confirm the Coordinator/owner view still loads driver and proposal data using `OwnerCredentialGate` (Basic auth) exactly as before — Task 25 added no new owner-facing capability and none should have changed here.
- [ ] **Frontend critical flows:** end-to-end, as a real browser session — passenger registration → order submission → (First Refusal or manual proposal) → driver accepts → trip progresses → passenger cancels a *different*, still-cancellable order — confirming the whole chain renders and functions with the new `Authorization`-header-sending frontend against the new backend.

## 15. Failure/Abort Conditions

Abort the deployment sequence (Section 13) and do not proceed further if:
- Section 10's pre-check query returns any row (V14 will fail dispatch's startup).
- Section 9 item 1's driver-management secret cannot be confirmed present and correct before starting `pios-driver-management` with the new jar.
- Any of steps 6's Flyway runs (Section 13) logs a migration failure for any reason, including but not limited to the duplicate-OPEN condition — stop immediately and do not start any subsequent module in the sequence.
- Any smoke test in Section 14 fails in a way that indicates a fixed endpoint returns 401/403 for a caller that should have succeeded (indicates a secret mismatch or a frontend/backend deployment-order violation, Section 8) — roll back per Section 12 rather than attempting a live fix.

## 16. Production Safety Constraints (observed by this task)

- No production PostgreSQL connection was made.
- No production RabbitMQ connection was made.
- No production or local service was started, stopped, or restarted.
- No application code, migration file, configuration file, ADR, or frontend file was modified. The only file this task wrote is this report.
- Every finding above was derived from local, read-only filesystem inspection (`Read`, `Grep`, `Glob`, `git status`, `git log`, `find`, `stat`) of the current working tree.

## 17. Remaining Known Risks (not blockers for this deployment)

Carried forward, unchanged, from Task 24's own already-published inventory — re-confirmed still open this session, and explicitly out of this task's scope per its own instructions:
- `POST /v1/orders` (`submitOrder`) has no passenger-ownership check at creation time (same class of gap Proposal's own `create` had before Task 21, deliberately deferred by Task 20/21's own scope).
- Network Management's five unauthenticated endpoints — module remains undeployed (Task 18/24).
- `AssignmentController.assignOrder` remains unauthenticated by deliberate scope decision (Task 22/23).
- `DriverController.createDriver` remains deliberately unauthenticated (pre-auth "first contact" action, mirrors `IdentityController.register`).

None of these are Taxi V1 deployment blockers per this task's own instruction not to treat undocumented gaps as blockers absent a ratified product decision saying otherwise — no such decision exists for any of the four items above.

## 18. Final READY/BLOCKED Classification

| Item | Classification |
|---|---|
| Dispatch (Trip, Primary Driver, First Refusal, Proposal/Assignment auth, V12–V14) | **READY WITH PRE-CHECK** (Section 10) |
| Order Management (`explicit_driver_intent`, cancel auth, V11) | **READY** |
| Driver Management (availability auth) | **READY WITH PRE-CHECK** (Section 9, item 1) |
| Frontend (Task 17/21/23/25 changes) | **READY**, conditioned on the sequencing in Section 8/13 |
| Identity, Passenger Experience | **NOT IN SCOPE** — untouched by this arc, no action required |
| Network Management | **NOT IN V1 SCOPE** — undeployed, unaffected by this arc |
| `submitOrder` ownership gap, `assignOrder` auth, `createDriver` auth | **NOT IN V1 SCOPE** for this deployment — pre-existing, deliberately deferred, no blocking product/architecture decision exists |

**Overall: READY WITH PRE-CHECK.** Two concrete, operator-executable pre-checks (Section 10's SQL query, Section 9's secret-configuration confirmation) gate an otherwise-additive, backward-compatible deployment. No BLOCKED condition exists in the audited scope. No new architectural or product decision is required.

## 19. Exact Next Task

Per this task's own instruction to stop after producing the report: the next task is for the user/architect to authorize execution of the deployment sequence in Section 13, beginning with the two pre-checks in Section 9 and Section 10 — not a further audit task, and not a code task. This audit performs no deployment action itself.

## 20. Files Inspected

`backend/dispatch/src/main/resources/db/migration/dispatch/V12__trips.sql`, `V13__primary_driver.sql`, `V14__proposals_one_open_per_order.sql`; `backend/order-management/src/main/resources/db/migration/ordermanagement/V11__add_order_explicit_driver_intent.sql`; `backend/driver-management/src/main/resources/db/migration/drivermanagement/V5__add_driver_is_test.sql`; full migration directory listing for all six backend modules (`find backend -iname "V*.sql"`); `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/RabbitMQTopologyConfiguration.kt`, `RabbitMQProducerTopologyConfiguration.kt`, `RabbitMQOrderManagementTopologyConfiguration.kt`, `RabbitMQPassengerExperienceTopologyConfiguration.kt`; `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/RabbitMQTopologyConfiguration.kt`, `RabbitMQConsumerTopologyConfiguration.kt`; `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/persistence/RabbitMQTopologyConfiguration.kt`; `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/persistence/RabbitMQProducerTopologyConfiguration.kt`; `windows-services/{dispatch,order-management,driver-management,identity,passenger-experience,frontend}/*.xml`; `backend/dispatch/src/main/resources/application.yml`; `backend/identity/src/main/resources/application.yml`; `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/SessionTokenVerifier.kt`; `backend/identity/src/main/kotlin/com/pios/identity/application/SessionTokenIssuer.kt` (located, existence and role confirmed); `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/OrderSubmitted.kt`; `frontend/vite.config.ts`; docs directory listing for deployment-related documents (`docs/ADR/ADR-014-Deployment-Philosophy.md`, `ADR-026-Deployment-Strategy.md`, `PIOS_PILOT_OPERATION_PROTOCOL.md`, `PIOS_NAMED_TUNNEL_DEPLOYMENT.md`, referenced for existing convention, not modified); ADR filename confirmation for ADR-041, 044, 053, 055, 060, 062, 063.

## 21. Commands Executed

All read-only: `git log --oneline -30`, `git status --short`; `find backend -iname "V*.sql"`; `find backend -iname "SessionTokenIssuer.kt"`; `find docs -iname "*DEPLOY*" -o -iname "*PILOT_OPERATION*"`; `find backend -iname "flyway*"`; `grep` (via Grep tool) for `secret`/`session` across `application.yml` files and `SessionTokenVerifier.kt`; `grep -rl`/`grep -rln` for `TripStarted|TripArrived|TripCompleted` and `outboxRecordFor|OutboxRecord` under dispatch's own source tree; `grep -n` for `isTest|explicitDriverIntent|passengerReference|driverCode` in `OrderSubmitted.kt`; `stat -c '%y'` on each module's build jar and on the security-fix source files, to compare mtimes non-invasively; `ls docs/ADR/` filtered to the ADR numbers cited; `graphify query` (three queries, for orientation on Trip/persistence, First Refusal consumer topology, and RabbitMQ configuration, per this repository's own `graphify` tooling convention) before the corresponding raw-file reads.

## 22. Final Production-Touch Statement

This task made **zero** connections to any production database, message broker, or HTTP endpoint, and performed **zero** service start/stop/restart actions and **zero** writes to any application, migration, configuration, or ADR file. The only artifact this task produced is this report, at `docs/PIOS_TAXI_TASK_26_PRODUCTION_DEPLOYMENT_READINESS_REPORT.md`.

---

*Report complete. Per Task 26's own closing instruction: no deployment was performed, no commit was made, no push was made. Stopping for review.*
