# Production Incident Forensic Report

Status: Read-only forensic investigation, completed 2026-09-02. **No production data was inserted, updated, or deleted to produce this report** — every finding below comes from `SELECT`/catalog-inspection queries and static source-code reading, all listed in this document. See [PIOS_REALITY_AUDIT.md](../PIOS_REALITY_AUDIT.md) Section 19 for the original incident disclosure, [TEST_DATABASE_ISOLATION.md](TEST_DATABASE_ISOLATION.md) for the PostgreSQL fix, and [RABBITMQ_TEST_ISOLATION.md](RABBITMQ_TEST_ISOLATION.md) for the RabbitMQ fix, both already applied (this report does not depend on either fix — it is a forensic reconstruction of what happened *before* they existed).

## 1. Executive Summary

On 2026-09-02, an interrupted `./gradlew build` run wrote real rows into 4 production PostgreSQL databases, because backend integration tests connected to production-named databases (root cause fixed by [TEST_DATABASE_ISOLATION.md](TEST_DATABASE_ISOLATION.md)). `PIOS_REALITY_AUDIT.md` originally disclosed **76 rows**. This forensic audit confirms that figure was an **undercount**, caused by one table (`assignments`) having no `created_at` column, so the original count — evidently timestamp-based — missed rows that never transitioned status. Read-only re-inspection, cross-referenced against test source code, finds:

- **88 rows are DEFINITELY INCIDENT DATA** (76 originally documented, +12 additional `assignments` rows, source-confirmed).
- **1 further row is PROBABLY INCIDENT DATA** (an `assignments` row with no timestamp, ambiguous between this incident and an earlier one).
- **3 further rows are UNKNOWN** — evidence points to a **separate, earlier, previously-undocumented contamination incident** (exact duplicate test fixtures with no distinguishing timestamp), not this one.
- **Real downstream effects beyond the row inserts are confirmed**: production's own live RabbitMQ outbox relays and consumers (still running throughout, on stable PIDs) processed several of the incident's fabricated events in real time — see Section 9.
- **A separate, older (pre-2026-09-02) contamination pattern was found incidentally** in `drivers` and `assignments`, unrelated to this specific incident — see Section 10.
- Nothing was deleted, updated, or repaired. All 76 originally-documented rows, and the 12 newly-identified ones, remain exactly as the incident left them.

## 2. Incident Scope

| | |
|---|---|
| Trigger | `./gradlew build` (repository-wide), launched during `PIOS_REALITY_AUDIT.md`'s own read-only verification pass |
| Root cause | `PostgreSQLTestDatabase.kt` (×6 modules, pre-Task-1-fix) hardcoded production database names with no test/prod isolation |
| Databases affected | `pios_driver_management`, `pios_order_management`, `pios_dispatch`, `pios_passenger_experience` (4 of 6 — `pios_identity` and `pios_network_management` confirmed clean, Section 6) |
| Tables affected (row inserts) | `drivers`, `orders`, `proposals`, `assignments`, `connections` (5 tables — the original audit covered 4 tables; `assignments`' undercount is corrected here) |
| Tables affected (downstream state, no new incident-row insert) | `driver_management_outbox`, `order_management_outbox`, `dispatch_outbox` (event records — expected, part of normal write path), `driver_availability` (dispatch's own projection — see Section 9, this *is* a genuine secondary effect), `driver_availability_processed_events`, `order_cancelled_processed_events` |
| Originally documented row count | 76 |
| Corrected row count (this report) | 88 definite + 1 probable + 3 unknown/likely-separate-incident = 92 rows touched across the same window, of which 88 are confidently this incident |
| Was anything deleted/modified to investigate this? | No — strictly `SELECT`/`\d`/`information_schema` queries throughout |

## 3. Incident Timeline

Reconstructed entirely from `created_at`/`published_at`/`processed_at` timestamps actually stored in production, cross-referenced with source code. All times `+05` (server's own timezone), 2026-09-02:

| Time | Event |
|---|---|
| 19:14:45.548 | First incident row (`connections`, passenger-experience) — earliest confirmed incident timestamp found |
| 19:14:47 – 19:15:12 | `driver-management`'s Postgres+RabbitMQ integration tests run: 3 `drivers` rows, 15 `driver_management_outbox` rows created (10 relayed to the real broker by production's own live `OutboxRelayScheduler` before 19:15:12) |
| 19:14:49 – 19:17:07 | `dispatch`'s Postgres+RabbitMQ integration tests run (overlapping driver-management's window): 34 `proposals` rows, 16 confirmed `assignments` rows (4 originally documented + 12 newly identified), 15 `dispatch_outbox` rows |
| 19:16:13 – 19:17:27 | `order-management`'s Postgres+RabbitMQ integration tests run: 33 `orders` rows, 24 `order_management_outbox` rows |
| 19:16:53 | Dispatch's real, live `OrderCancelledListener` processes 2 fabricated `OrderCancelled` messages published directly by dispatch's own test suite (bypassing order-management's outbox entirely) — 2 new rows in `order_cancelled_processed_events` |
| 19:16:58 | Dispatch's real, live `DriverAvailabilityChangedListener` processes a fabricated `DriverAvailabilityChanged` message — 2 new rows in `driver_availability` (dispatch's own projection) and `driver_availability_processed_events` |
| 19:17:27 | Last confirmed incident-window timestamp in `orders` |
| (undetermined) | Build detected as writing to production and killed; a detached Gradle daemon (PID 10008, per `PIOS_REALITY_AUDIT.md`) survived the initial kill and was separately force-stopped (already documented in Task 0/1) |

**Total incident window: 19:14:45 to 19:17:27 — 162 seconds.** `identity`'s tests did not run against production in this window (Section 6); `network-management` has no PostgreSQL/RabbitMQ dependency for this flow at all.

## 4. Affected Databases

| Database | Module | Confirmed affected |
|---|---|---|
| `pios_driver_management` | driver-management | Yes |
| `pios_order_management` | order-management | Yes |
| `pios_dispatch` | dispatch | Yes |
| `pios_passenger_experience` | passenger-experience | Yes |
| `pios_identity` | identity | **No** — 0 rows with `created_at::date = '2026-09-02'` in `identities` or `identity_credentials` |
| `pios_network_management` | network-management | Not applicable — this module was not part of the pilot-flow build path and shares no PostgreSQL migration set with the incident |

## 5. Affected Tables

| Table | Database | New incident rows (corrected) | Originally documented | `is_test` column? |
|---|---|---|---|---|
| `drivers` | driver-management | 3 | 3 | Yes |
| `orders` | order-management | 33 | 33 | Yes |
| `proposals` | dispatch | 34 | 34 | Yes |
| `assignments` | dispatch | **16** (4 timestamped + 12 source-confirmed, undated) | 4 | Yes |
| `connections` | passenger-experience | 2 | 2 | **No** (schema has none) |
| **Total (definite)** | | **88** | **76** | |

Plus 1 PROBABLY and 3 UNKNOWN `assignments` rows — see Section 7.

## 6. Exact Row Inventory

Rows are grouped by originating test file/fixture pattern rather than listed 88-deep individually — each group's exact `SELECT` predicate is given so any row can be reproduced/verified directly. All timestamps `2026-09-02`, server timezone `+05`.

| Database | Table | Group / originating test | Rows | PK pattern | Created/observed | `is_test` | Classification | Evidence |
|---|---|---|---|---|---|---|---|---|
| driver_management | drivers | `PostgreSQLCreateDriverTest` | 3 | `create-driver-<uuid>` | 19:15:05.949 – 19:15:06.943 | false ×3 | DEFINITELY | Exact ID prefix, exact display_name/availability values match source lines 29-58 of the test file, 1-second cluster |
| order_management | orders | `AssignmentCompletedConsumerIntegrationTest` | 5 | origin `consumer-passenger-completed*` | 19:16:13.37 – 19:17:00.46 | false ×5 | DEFINITELY | Exact origin literals match test source lines 56-114 |
| order_management | orders | Various Postgres-backed order tests (`OrderOutboxTransactionTest`, `OrderSubmittedEnvelopeTest`, `OrderSubmittedPublicationTest`, `OrderRepositoryLifecycleTest`, `PostgreSQLOrderLifecycleTest`, `PostgreSQLOrderRepositoryTest`) | 20 | origin `origin-1` | 19:17:15.64 – 19:17:27.20 | false ×19, true ×1 | DEFINITELY | Every Postgres-backed test file using this literal shares the exact `OrderOrigin("origin-1")` source string; tight 12-second cluster immediately following the `consumer-passenger-completed*` group |
| order_management | orders | `OrderRepositoryContractTest` | 8 | origin `contract-origin` / `contract-test-findall-1/2` | 19:17:22.51 – 19:17:24.64 | false ×8 | DEFINITELY | Exact literal match, source lines 14, 113-114 |
| dispatch | proposals | `ProposalControllerPostgreSQLIntegrationTest` | 6 | order ref `postgres-vertical-order-*` | 19:14:49.74 – 19:14:56.81 | false ×6 | DEFINITELY | Exact literal match to source (multiple `@Test` methods) |
| dispatch | proposals | Misc. Postgres/consumer proposal tests | 3 | `order-737...`, `consumer-order-*`, `consumer-driver-*` | 19:15:07.13 – 19:16:50.02 | false ×3 | DEFINITELY | UUID-suffixed literals match `OrderCancelledConsumerIntegrationTest`/related publisher fixture patterns |
| dispatch | proposals | `ProposalRepositoryContractTest` | 9 | `contract-test-*` | 19:17:00.52 – 19:17:03.81 | false ×9 | DEFINITELY | Exact literal match to source |
| dispatch | proposals | `PostgreSQLProposalRepositoryTest` | 6 | `postgres-order-1/2`, `postgres-order-is-test-*`, `postgres-order-timestamps-*`, `postgres-findbyorder-order-1` | 19:17:04.08 – 19:17:05.66 | false ×5, true ×1 | DEFINITELY | Exact literal match to source lines 27, 45, 56, 129-141 |
| dispatch | proposals | `ProposalAssignmentOrchestrationTransactionTest` | 4 | `tx-order-<uuid>` | 19:17:06.10 – 19:17:07.21 | false ×4 | DEFINITELY | Exact random-UUID match against `assignments` rows created by the same test (see below) — cross-table UUID collision is conclusive |
| dispatch | assignments | `PostgreSQLAssignmentRepositoryTest`, `AssignmentRepositoryContractTest`, `AssignmentOutboxTransactionTest`, `AssignmentEventEnvelopeTest`, `ProposalControllerPostgreSQLIntegrationTest`, `ProposalAssignmentOrchestrationTransactionTest` | 4 (timestamped, originally documented) + **12 (newly identified, status=CREATED, no `status_changed_at` — table has no `created_at` column at all)** | `postgres-order-*`, `contract-test-*`, `outbox-tx-order-*`, `envelope-order-*`, `tx-order-*`, `postgres-vertical-order-3d` | 4 confirmed 19:15:08–19:16:56 via `status_changed_at`; 12 undated (schema gap) but each literal directly source-matched, `is_test=false` | false ×16 | DEFINITELY (all 16) | Direct 1:1 source-code match for every literal (file:line cited in Section 9); several share exact random UUIDs with confirmed-incident `proposals` rows in the same window |
| dispatch | assignments | `PostgreSQLAssignmentRepositoryTest` "isTest true round-trips" | 1 of 4 duplicates | `postgres-order-is-test-true` | undated | true | PROBABLY | Matches the audit's own "4 of 76 is_test=true" figure once attributed one-per-table; exact literal match, but 3 other rows with the *identical* literal exist (see Section 10) — cannot be dated to distinguish which specific execution is this incident's |
| dispatch | assignments | Same literal, 3 further duplicate rows | 3 | `postgres-order-is-test-true` | undated | true | UNKNOWN | Identical literal, no timestamp column exists, no other distinguishing signal — evidence points to repeated, separate executions of this same test across multiple historical (undocumented) incidents, not one row four times over from this incident alone |
| passenger_experience | connections | `PostgreSQLConnectionIntegrationTest` | 2 | `artur-<uuid>` / `regina-<uuid>` | 19:14:45.55, 19:14:47.10 | n/a (no column) | DEFINITELY | Exact literal prefix match to source lines 30-31, 41-42; no `primary_connections` FK references either row (Section 8) |

## 7. Row Classification Summary

| Classification | Count |
|---|---|
| A. DEFINITELY INCIDENT DATA | 88 |
| B. PROBABLY INCIDENT DATA | 1 |
| C. UNKNOWN | 3 |
| D. LEGITIMATE PRODUCTION DATA | 0 (no row initially suspected was reclassified as legitimate — the 3 UNKNOWN rows are not legitimate either, they are ambiguous *incident-shaped* data from an indeterminate date) |

## 8. Foreign-Key / Dependency Analysis

- **`connections` → `primary_connections`**: FK `primary_connections_connection_fkey (passenger_reference, connection_id) REFERENCES connections(passenger_reference, id) ON DELETE CASCADE`. Checked directly: **zero** `primary_connections` rows reference either incident `connections` row. No legitimate data depends on them.
- **`identity_credentials` → `identities`**: not applicable — `identities` was confirmed unaffected (Section 4).
- **`orders` ↔ `proposals`/`assignments`**: no database-level FK exists between these tables (they live in separate databases — `pios_order_management` vs `pios_dispatch` — by design, per the modules' independent-database architecture). The relationship is **application-level only**, via the `order_reference`/`driver_reference` string fields. Checked application-level correlation directly: every incident `proposals`/`assignments` row's `order_reference` traces to either (a) another incident `orders` row with a matching origin/id, or (b) a synthetic literal (`postgres-order-1`, `contract-test-order-1`, etc.) that does **not** correspond to any real `orders.id` at all — these are dispatch-side tests that construct `OrderReference("...")` directly without ever calling order-management, so there is no real `orders` row for them to reference, legitimate or otherwise.
- **No incident row was found to be referenced by any pre-existing, legitimate production row**, in any of the 5 affected tables, via either a database FK or an application-level reference.
- **Incident rows referencing other incident rows**: within `dispatch`, several `proposals`/`assignments` pairs share the exact same `order_reference`/`driver_reference` literal (e.g. `postgres-order-1`/`postgres-driver-1` appears in both a `proposals` row and an `assignments` row) — these are two *independent* test fixtures reusing the same literal by convention, not a real domain reference (a `Proposal` and an `Assignment` are not database-linked to each other in this schema either — again, application-level only, via `order_reference`).

## 9. Downstream Effect Analysis

This is the most significant finding beyond the row-count correction. **The incident did not stay contained to raw row inserts.** Production's own live services (never stopped, confirmed on stable PIDs throughout) have their own real, scheduled `OutboxRelayScheduler` (polls every 2000ms per each module's `application.yml`) and real `@RabbitListener` consumers — both of which are entirely independent of whichever process inserted the row, and both were running the whole time.

Confirmed, with direct evidence (not inference):

1. **`driver_management_outbox`**: 15 incident-window rows, all routing key `driver.availability.changed`. **10 of the 15 have a non-null `published_at`** — production's own live relay actually published them to the real `driver-management.events` exchange. (The other 5 remain `published_at IS NULL` as of this report.)
2. **`order_management_outbox`**: 24 incident-window rows. Routing keys `order.submitted`/`order.completed` have **no bound consumer queue anywhere in the topology** (confirmed against the RabbitMQ topology audited in Task 2) — RabbitMQ silently drops unroutable messages, so these had **no further effect** even though most show a non-null `published_at`. One row (`order.cancelled`, order `81e5a039-778a-4a06-b707-dfa344b2023d`) *is* bound (to `dispatch.from-order-management`) and was published at 19:17:18.
3. **`dispatch_outbox`**: 15 incident-window rows. Routing key `order.assigned` has **no bound consumer anywhere** — harmless, silently dropped if published. Routing key `assignment.accepted` (2 rows, ids 585/589) **remains unpublished as of this report** (`published_at IS NULL`, checked live) — these never reached order-management's real consumer, confirmed zero effect.
4. **`order_cancelled_processed_events`** (dispatch's own idempotency ledger for its real `OrderCancelledListener`): **2 new rows at 19:16:53**, which is *before* the one traceable `order.cancelled` outbox publish at 19:17:18 — meaning these 2 came from dispatch's own test-only `OrderCancelledMessagePublisher`, which publishes **directly** to the real exchange, bypassing order-management's outbox entirely (this is by the test's own design, confirmed in source). **Dispatch's real, live, production `OrderCancelledListener` consumed and processed 2 fabricated messages.** The referenced order/proposal cannot be determined from available evidence — `processed_events` retains only an opaque `event_id`, no payload — marked UNKNOWN for "which specific order this affected," but the fact of processing is DEFINITELY confirmed.
5. **`driver_availability`** (dispatch's own live projection, upserted by `driver_reference`) and **`driver_availability_processed_events`**: **2 new rows each, at 19:16:58**, for driver references `repo-driver-a2fb3455-...` and `repo-driver-c76f1003-...` — **neither corresponds to any real driver in `pios_driver_management.drivers`**. Dispatch's real, live `DriverAvailabilityChangedListener` processed a fabricated event and **wrote fake availability state into its own live production projection table**, where it still sits today. This is a genuine, currently-live data-quality defect: dispatch's `driver_availability` table currently contains 2 rows for drivers that do not exist.
6. **A strong, but not fully provable, correlation**: `driver-management.events`' downstream queue `dispatch.from-driver-management` currently holds exactly **15 unconsumed messages with 0 registered consumers** (confirmed via `rabbitmqctl` during Task 2's own unrelated audit) — the same order of magnitude as this incident's 15 `driver_management_outbox` rows on the same routing key. This is circumstantial (message bodies were not inspected, which would require consuming them — explicitly forbidden by this task's read-only rule), but the coincidence in count and routing key is strong enough to flag as the probable explanation for that backlog, requiring separate investigation if pursued.

**No evidence was found of the incident altering `orders.status` for any order outside the 33 already-classified incident rows**, and no evidence of it altering any pre-existing, legitimate `drivers`/`proposals`/`assignments`/`connections` row.

## 10. Additional Suspicious Data (Beyond the Known 76/88)

Two distinct, previously-undocumented findings, incidental to this investigation:

1. **A separate, earlier contamination pattern in `drivers`**, unrelated to 2026-09-02: 26 of the table's 56 total rows are `is_test=true`, and several `is_test=false`/`is_test=true` rows carry test-fixture-shaped literal IDs (`postgres-driver-1`, `contract-test-driver-2`, `envelope-driver-*`, `outbox-tx-driver-*`) with **`created_at IS NULL`** — meaning they predate the `created_at` column's own existence, or were written before `is_test` was consistently populated. One dated cluster, 2026-08-17 18:36:31–32, shows 3 more `create-driver-*` rows (the *exact* fixture family as this incident's own 3 rows), all `is_test=true` this time — evidence of at least one **earlier, separate occurrence of this same category of incident**, seemingly already partially mitigated at the time (rows correctly marked `is_test=true` that time, unlike 2026-09-02's).
2. **The same pattern in `assignments`**: rows `postgres-order-lifecycle-1`/`postgres-order-lifecycle-2`, dated **2026-08-03**, `is_test=false` — a second, distinct, dated occurrence of production contamination, roughly a month before the incident this report investigates. Plus the 3 UNKNOWN `postgres-order-is-test-true` duplicate rows already discussed in Section 7.

Neither of these is part of the 76/88-row incident this report was commissioned to investigate — they are flagged here per this task's own Phase 9 instruction ("do not assume exactly 76 rows represent the complete incident") and left **entirely untouched**, exactly like the incident rows themselves. They indicate the underlying vulnerability (fixed by [[TEST_DATABASE_ISOLATION.md]]) was likely exploited by ordinary local development more than once before 2026-09-02's occurrence was the one that got caught and documented.

## 11. Deletion Safety Assessment

Per-group assessment (not per-row, since all 88 definite rows within a group share the same dependency profile):

| Group | Referenced by legitimate data? | Dependent rows? | Would violate FK? | Cascading risk? | Deletion assessment |
|---|---|---|---|---|---|
| `drivers` (3 rows) | No (Section 8) | No `driver_availability`/proposal/assignment row references these specific driver IDs | No | None | **SAFE TO DELETE** |
| `orders` (33 rows) | No | Corresponding `order_management_outbox` rows exist (24, no FK, same-incident) | No FK constraint from `orders` to anything | Outbox rows would become orphaned references (harmless — outbox rows are append-only event records, never read back by ID) | **SAFE TO DELETE**, but delete (or explicitly retain) the 24 related `order_management_outbox` rows in the same decision — see Section 13 |
| `proposals` (34 rows) | No | None found | No FK | None | **SAFE TO DELETE** |
| `assignments` (16 definite rows) | No | None found | No FK | None | **SAFE TO DELETE** |
| `assignments` (1 probable + 3 unknown, `postgres-order-is-test-true`) | No | None found | No FK | None | **REQUIRES MANUAL DECISION** — cannot confirm these 4 rows are *all* this incident; a blanket delete-by-literal would also remove the 3 UNKNOWN rows that may belong to an earlier, separately-relevant incident |
| `connections` (2 rows) | No (Section 8 — zero `primary_connections` references) | None | No | None | **SAFE TO DELETE** |
| `driver_availability` (2 fake rows, Section 9.5) | Not by other tables, but this **is itself currently live, incorrect production state** presented by dispatch's own API/projection | N/A | No | None | **SAFE TO DELETE** — arguably higher-priority than the raw incident rows, since this is actively-wrong *current* state, not dormant history |
| `driver_management_outbox` / `order_management_outbox` / `dispatch_outbox` / `*_processed_events` (event/audit trail) | N/A — these are the event/audit records themselves | N/A | No | None | **REQUIRES MANUAL DECISION** — these are exactly the kind of "corresponding event/outbox/audit records" Phase 7 asks to check before deleting the entities that produced them; deleting them independently of a decision on the entity rows above would destroy the audit trail this very report relies on |

**No deletion was performed.** This section is an assessment only, per the task's explicit instruction.

## 12. Unknown / Unresolved Items

- The 3 UNKNOWN `assignments` rows (`postgres-order-is-test-true` duplicates) — cannot be dated; likely belong to an earlier, separate incident (Section 10), not this one.
- The specific order/proposal affected by the 2 fabricated `OrderCancelled` messages dispatch's real listener processed at 19:16:53 (Section 9.4) — the processed-events ledger retains no payload, only an opaque `event_id`.
- Whether the `dispatch.from-driver-management` queue's current 15-message backlog is in fact these incident messages (Section 9.6) — circumstantially very likely, not conclusively proven without consuming the messages (explicitly out of scope for a read-only task).
- The exact scope and date of the pre-2026-09-02 contamination pattern found in `drivers` and `assignments` (Section 10) — flagged, not investigated further, as it is a separate incident from the one this report covers.

## 13. Recommended Next Action

This is a forensic report, not an action plan — but per `PIOS_REALITY_AUDIT.md` Section 21's own item 2, a Product Owner decision on the 76 (now 88+) rows remains outstanding. This report's contribution to that decision:

1. The corrected count is **88 definite rows**, not 76 — the Product Owner's decision should be made against this corrected figure.
2. Any deletion decision should explicitly cover the 24 related `order_management_outbox` rows and the 2 fake `driver_availability` rows (Section 11) together with the entity rows, not as an afterthought.
3. The 1 PROBABLY + 3 UNKNOWN `assignments` rows (Section 7/10) need their own, separate decision — they should not be silently swept into whatever decision is made about the 88.
4. The separate, older contamination found in `drivers`/`assignments` (Section 10) is a distinct matter, from before this incident, and deserves its own decision rather than being conflated with this one.
5. No code or infrastructure change is recommended by this report — [[TEST_DATABASE_ISOLATION.md]] and [[RABBITMQ_TEST_ISOLATION.md]] already close the root cause.

## 14. Production Safety Statement

This investigation executed **only** `SELECT`, `\d`, and `information_schema` queries against production PostgreSQL, and read-only `rabbitmqctl list_*`/broker-status commands already run and disclosed in Task 2 (no new RabbitMQ inspection was needed for this report — Section 9's RabbitMQ findings reuse Task 2's own already-recorded, already-disclosed read-only snapshot). No `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `DROP`, `ALTER`, `CREATE`, migration, or message publish/consume/purge was performed. No production service was restarted. No application code was run against production. No test suite or build was executed. The 76 originally-documented rows, and the 12 additional rows this report identifies, remain completely unmodified — exactly as the incident left them.
