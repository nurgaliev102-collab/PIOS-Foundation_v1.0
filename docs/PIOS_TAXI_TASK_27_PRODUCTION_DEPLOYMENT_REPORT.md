# Task 27 — PIOS Taxi V1 Production Deployment Preflight & Controlled Deployment

**Status: BLOCKED. Phase A preflight failed. Phase B was not entered. No deployment action was taken.**

Per this task's own instruction: two independent Hard Stop conditions were confirmed during Phase A. Per "Do not work around a failed preflight" and "If Phase A fails, do NOT enter Phase B," this task stops here and reports BLOCKED.

Baseline used: `docs/PIOS_TAXI_TASK_26_PRODUCTION_DEPLOYMENT_READINESS_REPORT.md`.

---

## 1. Environment Confirmation

Before any other check: confirmed this host (`c:\Projects\PIOS-Foundation_v1.0`) **is** the production host, not a separate remote environment. All seven `pios-*` Windows services are installed here and were found running under `LocalSystem`, executing WinSW wrappers whose paired XML files live in this same repository checkout:

| Service | State | StartMode |
|---|---|---|
| pios-ai-advisor | Running | Automatic |
| pios-dispatch | Running | Automatic |
| pios-driver-management | Running | Automatic |
| pios-frontend | Running | Automatic |
| pios-identity | Running | Automatic |
| pios-order-management | Running | Automatic |
| pios-passenger-experience | Running | Automatic |

This matches the established understanding from Tasks 18/19/26: PIOS Taxi V1's pilot "production" is this single self-hosted machine, reached publicly via Tailscale Funnel.

## 2. Phase A — Preflight Results

### 2.1 Production WinSW service state — PASS (informational)
All five backend services in scope (dispatch, order-management, driver-management, identity, passenger-experience) are `Running`/`Automatic`. No service is stopped or in a failed state.

### 2.2 Currently deployed JAR/build versions — checked, informational only
The `build/libs/*.jar` files on disk for dispatch, order-management, and driver-management are dated 2026-09-02 (unchanged since Task 26's own audit) — they do **not** contain the uncommitted Tasks 21/23/25 source changes (those source files are dated 2026-09-03). Running `java.exe` processes were enumerated; process start times range from 2026-09-01 through 2026-09-03, consistent with the WinSW-managed backend services having last (re)started before this session's own work began. This confirms, independently of Task 18/26's own jar-timestamp method, that **none of Tasks 11–25 is running in production right now** — the currently running jars predate this entire arc.

### 2.3 `PIOS_SESSION_SECRET` configuration — **FAILED — HARD STOP**
Checked the authoritative source for each service's actual runtime environment: the Service Control Manager's own per-service `Environment` registry override (`HKLM:\SYSTEM\CurrentControlSet\Services\<service>\Environment`), which is what WinSW actually launches its child `java.exe` with — **not** the checked-in XML files (which Task 26 already found do not set this variable at all, and which turned out to hold different, stale owner credentials than what's actually configured — see 2.7).

Result:

| Service | `PIOS_SESSION_SECRET` present in live registry Environment? |
|---|---|
| pios-dispatch | **Yes** |
| pios-order-management | **Yes** |
| pios-identity | **Yes** |
| pios-passenger-experience | **Yes** |
| pios-driver-management | **No — absent** |

The four services that already depended on `SessionTokenVerifier`/`SessionTokenIssuer` before this arc (identity as issuer; dispatch, order-management, passenger-experience as pre-existing verifiers per ADR-055/ADR-060) all carry the **identical** secret value (byte-for-byte compared). This is internally consistent and explains why session-token-authenticated flows already work in production today.

**Driver-management — the one module whose `SessionTokenVerifier` is new in the still-undeployed Task 25 change — has no `PIOS_SESSION_SECRET` configured in its live service environment at all.** This is exactly the risk Task 26 (Section 9) flagged as unconfirmed and asked this task to check. It is now confirmed present. Deploying Task 25's driver-management jar as-is would make `SessionTokenVerifier.verify()` return `null` for every call (fail-closed by design, per that class's own KDoc), turning every driver's `POST /v1/drivers/{id}/availability` call into an unconditional `401` — a regression from "working, unauthenticated" to "broken."

**This is Hard Stop condition "PIOS_SESSION_SECRET is missing or inconsistent," triggered exactly as written.** No attempt was made to add the missing value — doing so would itself be an unplanned production configuration change outside this task's read-only preflight mandate ("Do not work around a failed preflight").

*(The actual secret values were observed during this check, in order to compare them for consistency. They are deliberately not reproduced in this report beyond "present"/"absent"/"identical across the four services that have it," since this document is checked into the repository.)*

### 2.4 Production PostgreSQL backup — not created
Not reached as an action. The task makes backup creation conditional on explicit operator authorization, and since Phase A had already failed on 2.3 by the time backup would normally be requested, no backup was requested or taken. **No backup identifier exists as an output of this task.** (An existing backup *mechanism*, if any beyond ad hoc `pg_dump`, was not independently inventoried, since a Hard Stop had already been confirmed before this checklist item's dependent action — creating a fresh backup — would have mattered.)

### 2.5 V14 duplicate-OPEN-proposal read-only pre-check — **FAILED — HARD STOP**
Executed exactly the query specified by this task and by Task 26 Section 10, read-only, against production `pios_dispatch`:

```sql
SELECT order_reference, COUNT(*) AS open_count
FROM proposals
WHERE status = 'OPEN'
GROUP BY order_reference
HAVING COUNT(*) > 1;
```

**Result: 5 rows returned**, each with `open_count = 8` (40 duplicate `OPEN` proposal rows total, out of 61 `OPEN` proposals in production overall — roughly two-thirds of all currently-open proposals in the live database):

| order_reference | open_count |
|---|---|
| `contract-test-findbydriver-order-1` | 8 |
| `contract-test-findbydriver-order-2` | 8 |
| `contract-test-findbyorder-order` | 8 |
| `contract-test-findbyorder-other-order` | 8 |
| `postgres-findbyorder-order-1` | 8 |

**This is Hard Stop condition "V14 pre-check returns any rows," triggered exactly as written.** Applying V14 (`CREATE UNIQUE INDEX ... WHERE status = 'OPEN'`) against production right now would fail, aborting dispatch's Flyway migration and its startup.

**A second, independent concern beyond the immediate blocker:** every one of the five affected `order_reference` values is not a plausible real order identifier — each matches, verbatim, the fixed literal test-fixture naming convention this whole task arc has repeatedly documented for `ProposalControllerPostgreSQLIntegrationTest`/`PostgreSQLProposalRepositoryContractTest`-style tests (e.g. `contract-test-findbyorder-order`, `postgres-findbyorder-order-1`). These rows should never exist in `pios_dispatch` (production) at all — this pattern has, until now, only ever been documented recurring in the isolated `pios_dispatch_test` database. Their presence in production suggests test-suite-generated data has, at some point, been written to the production database rather than the isolated test database — a pre-existing data-hygiene condition, unrelated to this deployment's own code, that the operator should investigate independently of the V14 migration question. **This task did not investigate how this data got there, did not identify a root cause, and took no corrective action** — that is outside a read-only preflight's mandate and is exactly the kind of "any destructive/unplanned database change" this task's own Hard Stop list says to stop short of.

### 2.6 RabbitMQ connectivity/topology — inconclusive, not a blocker
Attempted a read-only check via the RabbitMQ HTTP management API (`GET /api/overview`, `GET /api/queues` on `127.0.0.1:15672`) — no response (management plugin not reachable with the credentials available in this shell). `rabbitmqctl` is not on this shell's `PATH`. No further attempt was made, per this task's own instruction to verify "only to the extent explicitly permitted by the deployment environment" — this environment did not expose a permitted read path from this session. This is recorded as **inconclusive**, not as a Hard Stop condition (the task's Hard Stop list does not include RabbitMQ inconclusiveness), and does not by itself change the BLOCKED classification, which is already determined by 2.3 and 2.5.

### 2.7 Deployment artifact buildability — not attempted
Not performed. Given two independent Hard Stop conditions were already confirmed (2.3, 2.5) before reaching this checklist item, attempting a build serves no purpose for a deployment that will not proceed and was skipped to avoid unnecessary work on an already-blocked task.

### 2.8 Disk space and service dependencies — PASS (informational)
`C:` drive: **102.7 GB free**. Ample headroom for new jars, a database backup, and log growth. Not a constraint.

### 2.9 Additional observation (not a Hard Stop, recorded for the operator)
The owner credentials (`PIOS_OWNER_USERNAME`, `PIOS_OWNER_PASSWORD_HASH`, `PIOS_OWNER_PASSWORD_SALT`) actually configured in each service's live registry `Environment` override are **different** from the values checked into this repository's `windows-services/*/pios-*.xml` files (live: username `owner`, a different hash/salt; repo: username `ildar`, a different hash/salt again from the machine-scope `PIOS_OWNER_*` variables seen in 2.3's own enumeration, which are different still). This confirms the checked-in WinSW XML files are **not** an accurate record of the live services' actual configuration for *any* environment variable — the registry-level `Environment` override is authoritative and has drifted from the repository. This is worth the operator's attention as a documentation/drift issue, independent of this deployment.

## 3. Phase A Determination

| Hard Stop condition (from this task's own list) | Triggered? |
|---|---|
| `PIOS_SESSION_SECRET` missing or inconsistent | **Yes** — missing on driver-management |
| V14 pre-check returns any rows | **Yes** — 5 rows, 40 duplicate proposal rows |
| Required backup cannot be established | Not reached |
| Required deployment artifact cannot be built | Not attempted (moot) |
| Migration ordering differs from Task 26's plan | Not applicable — no migration ordering was exercised |
| Production service state materially differs from expected baseline | No — services match expected running state |
| Any destructive/unplanned database change required | No — none was required or performed |
| Any new architectural/product decision becomes necessary | No |

**Phase A: FAILED.** Two independent Hard Stop conditions confirmed. Per this task's explicit instruction, Phase B was not entered.

## 4. Phase B — Controlled Deployment

**Not entered.** No jar was rebuilt for deployment purposes, no service was stopped or restarted, no migration was applied, no RabbitMQ topology was changed, and no smoke test was run, because Phase A did not reach PASS.

## 5. Production Safety Statement

This task connected to the production PostgreSQL database (`pios_dispatch`, `pios_order_management`, `pios_driver_management`) with **read-only `SELECT` queries only** — the exact V14 pre-check query this task and Task 26 both specify, plus `flyway_schema_history` reads and read-only aggregate counts to characterize the scope of the finding in 2.5. No `INSERT`/`UPDATE`/`DELETE`/`DDL` statement was executed against any production database. No production RabbitMQ message was published or consumed. No production Windows service was stopped, started, or restarted. No production file was modified. The only file this task wrote is this report.

## 6. Exact Preflight Results (summary)

- Service state: all 7 `pios-*` services Running/Automatic. ✅
- Deployed artifact freshness: current running jars predate Tasks 11–25 (confirmed via process start times and jar mtimes). Informational — expected, matches Task 18/26.
- `PIOS_SESSION_SECRET`: present and identical on dispatch, order-management, identity, passenger-experience; **absent on driver-management**. ❌ HARD STOP.
- Production backup: not created (moot — Phase A already failed by the time this gate would have been reached). N/A.
- V14 pre-check: **5 `order_reference` values with duplicate `OPEN` proposals (40 rows total)**, all matching known test-fixture naming, present in the production `proposals` table. ❌ HARD STOP.
- RabbitMQ: inconclusive (management API unreachable from this session; not a Hard Stop).
- Build artifacts: not attempted (moot).
- Disk space: 102.7 GB free. ✅

## 7. Production Artifacts Deployed
None.

## 8. Exact Migration Versions
No migration was applied by this task. Current production Flyway state, as read (informational, read-only):

| Module | Latest applied version | Latest applied description |
|---|---|---|
| dispatch (`pios_dispatch`) | V11 | add proposal and assignment is test |
| order-management (`pios_order_management`) | V10 | add order is test |
| driver-management (`pios_driver_management`) | V5 | add driver is test |

V12/V13/V14 (dispatch) and V11 (order-management) — the migrations in this arc's scope — are **not yet applied** to production, consistent with Task 18/26's own conclusion that none of Tasks 11–25 is deployed.

## 9. Exact Services Restarted
None.

## 10. RabbitMQ Changes
None.

## 11. Backup Identifier/Location
None created. No backup was requested from, or authorized by, the operator during this task, since Phase A had already failed before that gate would normally be exercised.

## 12. Smoke-Test Results
Not run. Phase B was not entered.

## 13. Failures and Corrective Actions

| Failure | Corrective action taken by this task | Corrective action required before retry (not performed here) |
|---|---|---|
| `PIOS_SESSION_SECRET` absent on `pios-driver-management` | None (read-only preflight; no workaround attempted) | Operator must add a `PIOS_SESSION_SECRET` registry `Environment` override to the `pios-driver-management` service, set to the exact same value already configured on dispatch/order-management/identity/passenger-experience, before that service's jar is ever replaced with the Task 25 build. |
| 40 duplicate `OPEN` proposal rows across 5 `order_reference` values in production `proposals`, matching test-fixture naming | None (read-only preflight; no data was modified) | Operator must resolve each affected order's duplicate `OPEN` proposals (e.g., via the already-authenticated decline/lapse path, or a reviewed manual data correction) down to at most one `OPEN` row per `order_reference` before V14 can apply. Separately, the operator should investigate how test-fixture-named rows reached the production database at all — this task did not determine a root cause and did not attempt to. |

## 14. Final Production State
Unchanged from before this task ran. All 7 services remain in the same running state observed at the start of this task. No schema, data, or configuration change was made to any production system.

## 15. Rollback State
Not applicable — no deployment action was taken, so there is nothing to roll back.

## 16. Exact Git Commit/Artifact Versions "Deployed"
None. Working tree remains on branch `pios-product-main`, uncommitted, at the same state as the end of Task 26. Nothing was committed, pushed, or deployed by this task.

## 17. Next Task
Not a further audit and not a deployment attempt yet. The next task is for the operator to (a) provision `PIOS_SESSION_SECRET` on `pios-driver-management`'s live service environment, and (b) resolve the 5 affected orders' duplicate `OPEN` proposals in production `pios_dispatch` (and investigate how they got there). Only after both are independently confirmed resolved should a Task 27-style preflight be re-run from the top — this report does not attempt to predict whether other conditions would still hold at that time.

---

*Per this task's own instruction: Phase A failed, Phase B was not entered, and this report is not committed or pushed automatically.*
