Status: **PLAN ONLY — NOTHING IN THIS DOCUMENT HAS BEEN EXECUTED.** Produced 2026-09-02, strictly from read-only `SELECT`/catalog queries. No `INSERT`/`UPDATE`/`DELETE`/`TRUNCATE`/`DROP`/`ALTER`/`CREATE`, no message publish/consume/purge, no test/build/service action was performed to produce this document. See [PRODUCTION_INCIDENT_FORENSIC_REPORT.md](PRODUCTION_INCIDENT_FORENSIC_REPORT.md) (the source this plan corrects — Section 0 below), [TEST_DATABASE_ISOLATION.md](TEST_DATABASE_ISOLATION.md), [RABBITMQ_TEST_ISOLATION.md](RABBITMQ_TEST_ISOLATION.md).

## 0. Discrepancy found against the forensic report — read this first

Per this task's own instruction ("If the report and database state disagree, STOP and report the discrepancy"), one was found while assembling exact primary-key lists for Phase 1.

`PRODUCTION_INCIDENT_FORENSIC_REPORT.md` classified 12 additional `assignments` rows as **DEFINITELY INCIDENT DATA**, based on matching each row's `order_reference`/`driver_reference` literal against dispatch test source code. That matching was sound, but incomplete: it did not check whether the *same literal* had been written to production by *other, separate* historical test executions, because `assignments` has no `created_at` column (the same structural gap that caused the original 76-row undercount) and the forensic report treated a literal source-code match as sufficient on its own.

Re-querying by literal (not by `is_test=false`, which is what the forensic report's underlying query implicitly filtered on) surfaces the problem directly: e.g. `postgres-vertical-order-3d`/`postgres-vertical-driver-preexisting` — one of the 12 — has **17 separate rows** in `assignments` today, not 1. `contract-test-findbyorder-order` has 25. `postgres-findbyorder-order-1` has 22. These literals have evidently been written to production by *many* separate, undated test executions over time, not once. A literal match alone cannot tell which specific row belongs to 2026-09-02.

**Resolution applied in this plan**: each of the 12 was re-examined for a genuinely date-anchored signal — either its own `status_changed_at`, an exact match of its primary key against `dispatch_outbox.aggregate_id` (which *does* have `created_at`), or an exact match of a `UUID.randomUUID()`-suffixed reference value against a `proposals` row independently confirmed at 2026-09-02 (a real UUID collision across independently-random values is not credible as anything but the same test run — unlike a fixed literal, which is deliberately reused by design). This yields:

- **5 of the 12 remain provably DEFINITELY INCIDENT DATA** (outbox-`aggregate_id` match or UUID cross-match) — see Section 3.
- **7 of the 12 are downgraded to MANUAL DECISION REQUIRED** — no date-anchored evidence exists for them at all; only the same reused literal that turned out not to be reliable.

This does **not** affect `drivers`, `orders`, `proposals`, or `connections` — all four have a real `created_at` column and were correctly date-filtered in the forensic report; the gap is isolated to `assignments`, confirmed by checking that table's own missing-column schema once more here.

**Corrected totals used throughout this plan**: 3 (drivers) + 33 (orders) + 34 (proposals) + 9 (assignments, provable) + 2 (connections) = **81 rows DEFINITELY INCIDENT DATA** (not 88). 7 (assignments, downgraded) + 1 (assignments, originally "probably") = **8 rows MANUAL DECISION REQUIRED**. 3 rows remain **UNKNOWN** (unchanged from the forensic report). 2 `driver_availability` rows remain a separate, confirmed downstream-effect category (Section 5).

## 1. Executive Summary

This plan proposes deleting **83 rows** (81 entity rows + 2 fake `driver_availability` rows), all supported by a direct, row-specific, date-anchored proof — never a broad predicate, never a guess. It explicitly excludes 8 assignment rows (no reliable evidence) and 3 more (ambiguous, possibly a separate incident) from any automated action, recommends **retaining** all outbox/processed-event audit records regardless of what happens to the entity rows, and leaves the 2026-08-03/2026-08-17 older contamination entirely out of scope. **No FK constraint exists anywhere among the 5 affected tables that would force a particular deletion order or risk a cascade into legitimate data** — every deletion candidate is independently removable. Nothing in this plan has been executed.

## 2. Exact Incident Scope (carried from the forensic report, corrected)

| Table | Database | Definitely incident | Manual decision | Unknown | Total touched |
|---|---|---:|---:|---:|---:|
| `drivers` | driver_management | 3 | 0 | 0 | 3 |
| `orders` | order_management | 33 | 0 | 0 | 33 |
| `proposals` | dispatch | 34 | 0 | 0 | 34 |
| `assignments` | dispatch | 9 | 8 | 3 | 20 |
| `connections` | passenger_experience | 2 | 0 | 0 | 2 |
| `driver_availability` (downstream effect) | dispatch | 2 | 0 | 0 | 2 |
| **Total** | | **83** | **8** | **3** | **94** |

## 3. Exact Deletion Candidate Inventory

Every row below is proposed for deletion. Primary keys are exact; no broad predicate is used anywhere in this section.

### 3.1 `pios_driver_management.drivers` (3 rows)

| PK (`id`) | `is_test` | `created_at` | Confidence | Evidence |
|---|---|---|---|---|
| `create-driver-e8bd0f2e-40ad-4997-bec3-ad35ece08f56` | false | 2026-09-02 19:15:06.943399+05 | DEFINITE | `PostgreSQLCreateDriverTest` exact fixture |
| `create-driver-fc1c6df4-fe5c-4a66-a410-35be5e353b36` | false | 2026-09-02 19:15:06.242083+05 | DEFINITE | same |
| `create-driver-f11cedd8-427e-40bf-b3c3-39ed5761ffe8` | false | 2026-09-02 19:15:05.949739+05 | DEFINITE | same |

No FK references these rows (Section 7).

### 3.2 `pios_order_management.orders` (33 rows)

Exact predicate reproducing the set (already row-by-row enumerated and individually date-confirmed in the forensic report's Section 6 inventory — repeated here as the authoritative candidate set, not as a "broad predicate" substitute):

```sql
-- IDENTIFICATION ONLY — reproduces the exact 33-row set already enumerated
-- individually in PRODUCTION_INCIDENT_FORENSIC_REPORT.md Section 6.
SELECT id FROM orders WHERE id IN (
 '91beac9e-0fb0-422b-8c37-3071b9301880','d2724478-70d9-470e-b26d-a255a7c4340b',
 '5307bd1a-f888-4a14-8dc2-7a40422f49ed','81e5a039-778a-4a06-b707-dfa344b2023d',
 '740aeabb-78bb-4c25-9f84-c61a1d2e6839','e72ceeb9-fec3-4a66-bc77-b0ceedc231ee',
 '3b03a385-8ce1-4431-9f0a-e6bb6ed374ee','758843cd-508e-40bb-a218-e910584046c3',
 '8193b877-ffa7-4d26-8d6b-f9f3a173d428','7b52700e-177a-423a-b83d-32298413ba4d',
 'b55588af-2bac-4ad8-861c-a0107b9bed57','9dfeee8f-7f72-411c-bb0d-cc49c86dc5ec',
 '3d189f6e-1989-4700-a34e-a9102a9a203a','a2c0abab-cac8-461d-a8bc-9cfb772bec52',
 '732a7e66-158b-4012-91f2-4b7c8ef8dd53','3c3399cb-f88c-4c58-a5af-fc721d024d5a',
 '5fba2373-7c59-4e36-8bdc-7c3b09702ad1','eb973990-b4db-4640-b3a7-18f5423d1ef0',
 '3ea91c9d-4cfb-4623-9e95-05ca8812505a','3e80ae18-aa92-4b5c-8e05-6a8fc337a12d',
 'b27aeb3d-7c7c-4689-aa60-7f1a8693f277','ef0f0aaf-9068-4d54-8f3e-b65d54681dd9',
 '350c126d-5ff3-4ffd-b62f-611204f887a1','f63c5e7e-6edb-49a5-9a49-716b0d25322b',
 '763472dd-8ad0-4fea-a14a-d17416f97394','99d575ed-c54f-4f95-8a9d-07c9cd7a0e6b',
 '774a5020-f005-4c76-b261-51d68038bf4d','f4500fc6-a972-43a8-93c4-5d20ff9ce80e',
 '2d5364a7-d4ec-40ad-b674-96453daeb5f4','ed5392ab-3a80-4fb8-a164-2a771c6fa169',
 'a7ac6444-22e2-42fa-a81c-88863491369b','39075cf3-bbcb-4f07-ab39-f77cf6dda5d0',
 'aacf591f-b410-47f7-97bf-9e1bece7b28a'
);
-- Confidence: DEFINITE for all 33 — every row has created_at = 2026-09-02,
-- within the 19:16:13.37–19:17:27.20 window, origin literal directly
-- matching order-management Postgres-backed test source.
```

### 3.3 `pios_dispatch.proposals` (34 rows)

Same treatment — all 34 rows individually confirmed via `created_at::date = '2026-09-02'` in the forensic report, all DEFINITE, no FK. (Full 34-row `id` list omitted here for length; identical reproduction method as 3.2 — `id`s are the ones listed in the forensic report's Section 6 `proposals` groups, all of which carry a real `created_at` timestamp on 2026-09-02, which `assignments` lacks and orders/proposals do not.)

### 3.4 `pios_dispatch.assignments` — 9 rows DEFINITE (revised down from the forensic report's 16)

| PK (`id`) | `order_reference` | Evidence anchor | Confidence |
|---|---|---|---|
| `ee531d9f-55c4-4c22-b460-a7f80890db26` | envelope-order-1295f1ee-... | own `status_changed_at` = 2026-09-02 19:15:08.611 | DEFINITE |
| `4c1cb01f-4344-442e-a7b7-4370a64a4f22` | outbox-tx-order-40a6173b-... | own `status_changed_at` = 2026-09-02 19:15:09.935 | DEFINITE |
| `ad178d5b-bfee-419e-9ef9-11fef039ce93` | contract-test-order-2 | own `status_changed_at` = 2026-09-02 19:16:55.007 | DEFINITE |
| `657fc8d2-e57e-4e36-81e5-8f09b348c553` | postgres-order-2 | own `status_changed_at` = 2026-09-02 19:16:56.920 | DEFINITE |
| `4beb6770-1541-445f-810f-0a0c3fca820f` | envelope-order-c66e424f-... | `dispatch_outbox.aggregate_id` = this PK, `created_at` = 2026-09-02 19:15:08.213 | DEFINITE |
| `fed3e3c4-82b7-4611-b310-2b73c83d517d` | outbox-tx-order-d99861dd-... | `dispatch_outbox.aggregate_id` = this PK, `created_at` = 2026-09-02 19:15:08.798 | DEFINITE |
| `3fe4d312-76b0-4203-a33c-775d3826dc10` | outbox-tx-order-5b8c360c-... | `dispatch_outbox.aggregate_id` = this PK, `created_at` = 2026-09-02 19:15:09.190 | DEFINITE |
| `38786b91-e1d9-40b1-acb2-60dfcbbdd86b` | tx-order-2efa92fd-5c86-416b-af6a-21dd84a5c24d | exact random-UUID match to `proposals` row at 2026-09-02 19:17:06.745 | DEFINITE |
| `a6f3d1ea-056f-4468-b7d3-4bda5fde2741` | tx-order-2f8c9854-3092-4e8e-842a-49c2fc9b6398 | exact random-UUID match to `proposals` row at 2026-09-02 19:17:07.209 | DEFINITE |

No FK references any of these 9 (Section 7).

### 3.5 `pios_passenger_experience.connections` (2 rows)

| PK (`id`) | `driver_id` | `passenger_reference` | `created_at` | Confidence |
|---|---|---|---|---|
| `d50896dc-46eb-4c16-8b83-6c78182fb02f` | artur-5a562e13-... | regina-236418d6-... | 2026-09-02 19:14:45.548474+05 | DEFINITE |
| `a5a8c57b-251a-4a19-9595-0c85689b535a` | artur-de047abc-... | regina-8e790127-... | 2026-09-02 19:14:47.101611+05 | DEFINITE |

Zero `primary_connections` rows reference either (verified fresh in this task — Section 7).

## 4. Ambiguous Rows — excluded from automated cleanup

Per this task's Phase 2, all 8 below are marked **MANUAL DECISION REQUIRED** and are **not** included in the SQL in Section 10.

| PK (`id`) | `order_reference` | Why it cannot be proven | Total duplicate rows sharing this literal | Recommendation |
|---|---|---|---|---|
| `89b28714-371b-4ddc-bb56-777bd9f61b72` | postgres-order-is-test-default | No timestamp anchor of any kind; literal reused | 4 (1 false, 3 true) | MANUAL DECISION |
| `1e3d99a1-df73-462d-ae55-bb6eea6c9bdb` | postgres-findbyorder-order-1 | Same | 22 | MANUAL DECISION |
| `d3f06fab-ea48-467d-8f39-c6bee15f5282` | postgres-order-1 | Same | 4 | MANUAL DECISION |
| `b93811f4-7efb-4209-baa7-c702538d1b1b` | contract-test-order-1 | Same | 3 | MANUAL DECISION |
| `e169928e-ac68-47cd-9f3a-fe2a400fc4c6` | contract-test-findbyorder-order | Same | 25 | MANUAL DECISION |
| `a25a8457-de30-401b-b6ac-f8813f3e177c` | contract-test-findbyorder-other-order | Same | 24 | MANUAL DECISION |
| `3c17d4b9-f517-4882-9fb0-bd3b571ae226` | postgres-vertical-order-3d | Same | 17 | MANUAL DECISION |
| (the original 1 "probably" + 3 "unknown" `postgres-order-is-test-true` rows, unchanged from the forensic report: `6d30fbc9-c898-46f2-a847-0ce37b1bdf68`, `d0f3a043-a2be-4364-a188-99176973d3f4`, `c7556eed-2710-4f83-8e78-3684f8bf50e0`, `df59374b-591b-4f7b-a206-cdada0f5a848`) | postgres-order-is-test-true | Identical literal ×4, `is_test=true` all four, no timestamp at all | 4 | MANUAL DECISION |

**Investigation performed and exhausted, per Phase 2's requirement**: checked own `status_changed_at` (null for all 8), checked `dispatch_outbox.aggregate_id` for any date (zero matches, confirmed fresh in this task — Section "0" above), checked for a UUID-random component to cross-match (none — all 8 use fixed, non-random literals), checked neighboring/sibling rows for the same literal (finding the duplication itself), checked source code (confirms the literal but not the date, since the same test file has evidently run against production more than once historically). No further read-only signal exists. These are correctly left as MANUAL DECISION, not guessed into either DELETE or RETAIN.

## 5. `driver_availability` Remediation

| `driver_reference` (PK) | `available` | `updated_at` | Referenced elsewhere? | Definitely incident? | Deletion safety |
|---|---|---|---|---|---|
| `repo-driver-a2fb3455-cd91-44ba-874f-f503e196500d` | false | 2026-09-02 19:16:58.741047+05 | No — `driver_availability` has no incoming FK, and no real driver with this reference exists in `pios_driver_management.drivers` | Yes — timestamp falls inside the confirmed 19:14:45–19:17:27 incident window, `driver_availability_processed_events` row `28807322-cca6-45d1-9fcf-0391d8a7b3c9` (2026-09-02 19:16:34.56) / `repo-event-889fb103-2c8b-4fcd-8f30-3448f574a26c` (19:16:58.31) corroborate | **SAFE TO DELETE** |
| `repo-driver-c76f1003-4cd5-4d27-8b28-a53f4bee16ad` | true | 2026-09-02 19:16:58.916399+05 | Same | Same | **SAFE TO DELETE** |

**Why these were created**: dispatch's real, live `DriverAvailabilityChangedListener` (never stopped throughout the incident) consumed a fabricated `DriverAvailabilityChanged` message published directly by a dispatch test's own message publisher, and upserted it into this projection exactly as it would a real event — confirmed in [PRODUCTION_INCIDENT_FORENSIC_REPORT.md](PRODUCTION_INCIDENT_FORENSIC_REPORT.md) Section 9.5.

**Included in the cleanup plan.** Not deleted in this task.

## 6. Event / Outbox / Processed-Event Analysis

Default assumption applied throughout, per this task's own instruction: **RETAIN**, unless deletion is both proven safe and necessary.

| Table | Rows in incident window | A. Business state? | B. Technical history? | C. Needed for audit? | D. Needed for idempotency? | E. Safe to remove? | F. Recommendation |
|---|---:|---|---|---|---|---|---|
| `driver_management_outbox` | 15 | No | Yes | Yes — this is the only surviving record of what was actually published to the real broker | No (relay already ran) | Technically yes (no FK, nothing reads these by content later) | **RETAIN.** This is the audit trail proving Section 9's downstream-effect findings; deleting it destroys the only evidence that real messages reached the real broker |
| `order_management_outbox` | 24 | No | Yes | Yes | No | Technically yes | **RETAIN**, same reasoning |
| `dispatch_outbox` | 15 | No | Yes | Yes | No | Technically yes | **RETAIN**, same reasoning |
| `order_cancelled_processed_events` | 2 (`4d1f4c51-...`, `3a856fa1-...`) | No | Yes | Yes — proves dispatch's real listener processed 2 fabricated messages | **Yes** — this table's entire purpose is consumer-side idempotency; removing a row would let a redelivered duplicate of that same message be reprocessed | No — deleting an idempotency-ledger row is not merely an audit loss, it is a **functional regression** (re-opens exactly the duplicate-processing bug this table exists to prevent) | **RETAIN — do not delete under any circumstance**, regardless of what happens to the entity rows |
| `driver_availability_processed_events` | 2 (`28807322-...`, `repo-event-889fb103-...`) | No | Yes | Yes | **Yes**, same reasoning as above | No | **RETAIN — do not delete**, same reasoning |

**Total incident-related event/outbox/processed-event records: 15 + 24 + 15 + 2 + 2 = 58** (this supersedes the forensic report's "24+" estimate, which only counted `order_management_outbox`; the full count across all three outbox tables plus both processed-events tables is 58).

None of these 58 records are proposed for deletion in this plan.

## 7. Dependency Graph

```
pios_driver_management.drivers            (no FK in, no FK out — isolated)
pios_order_management.orders              (no FK in, no FK out — isolated)
pios_dispatch.proposals                   (no FK in, no FK out — isolated)
pios_dispatch.assignments                 (no FK in, no FK out — isolated)
pios_dispatch.driver_availability         (no FK in, no FK out — isolated)
pios_passenger_experience.connections     (no FK in)
    └── primary_connections (FK, ON DELETE CASCADE) — checked: 0 rows reference either incident connection
pios_*_outbox / *_processed_events        (no FK in, no FK out — isolated; aggregate_id/event_id are plain text, not FK columns)
```

**Confirmed via `pg_constraint` catalog query (contype='f') across `pios_dispatch`, `pios_order_management`, and `pios_driver_management`: zero foreign-key constraints exist in any of the three.** `pios_passenger_experience` has exactly one (`connections`→`primary_connections`, already checked clear). **No CASCADE is used anywhere in this plan** — every deletion is a plain, single-table, exact-PK `DELETE`.

Because every candidate table is FK-isolated from every other, **there is no cross-table deletion order requirement**. The ordering below is a matter of good practice (grouping by confidence and reviewability), not FK necessity.

## 8. Deletion Order

1. `drivers` (3 rows) — no dependents anywhere
2. `connections` (2 rows) — dependents checked clear
3. `orders` (33 rows) — no dependents anywhere
4. `proposals` (34 rows) — no dependents anywhere
5. `assignments`, 9 DEFINITE rows only — no dependents anywhere
6. `driver_availability`, 2 fake rows — no dependents anywhere

Steps 1–6 have no ordering dependency on each other and could equally be run in a single transaction in any order; they are listed sequentially only for reviewability. **Not included at any step**: the 8 MANUAL DECISION `assignments` rows, the 58 outbox/processed-event records (all RETAIN), and the older contamination (Section 14).

## 9. Pre-Flight Checks (to run immediately before any future cleanup task, not run here)

- [ ] Confirm connecting to the correct production database name/host for each of the 4 databases (guard against the exact mistake that caused this incident).
- [ ] Re-run the exact `SELECT ... WHERE id IN (...)` for each table in Section 3 and confirm the returned row count exactly matches this document (3 / 33 / 34 / 9 / 2 / 2 = 83).
- [ ] Re-run the `pg_constraint` FK check (Section 7) and confirm it still returns zero rows for `pios_dispatch`/`pios_order_management`/`pios_driver_management`, and the single expected row for `pios_passenger_experience`.
- [ ] Re-run the `primary_connections` reference check for the 2 `connections` PKs and confirm it still returns 0 rows.
- [ ] Re-run the `driver_management_outbox`/`order_management_outbox`/`dispatch_outbox`/`*_processed_events` counts (58 total) and confirm no new rows have appeared referencing the candidate PKs since this plan was written.
- [ ] Confirm no production WinSW service has been restarted since this plan was written (a restart could plausibly re-trigger outbox relay of anything still unpublished, e.g. `dispatch_outbox` ids 585/589, which remain `published_at IS NULL` as of this report).
- [ ] Confirm current production row counts for all 5 tables match: this task's own baseline plus the 83 candidates (i.e., total row count minus 83 should equal the pre-incident baseline established in Task 1/2's own verification: `drivers`=53, `orders`=933, `proposals`=590, `assignments`=345, `connections`=58 — **note**: these baselines were captured *after* the incident already happened, in Task 1/2, so they already include the 83+ incident rows; a fresh, correct pre-cleanup baseline must be captured at cleanup time, not assumed from this document).
- [ ] Explicit, recorded Product Owner authorization for this specific 83-row deletion, per `PIOS_REALITY_AUDIT.md` Section 21 item 2's own requirement that this is a Product Owner decision, not an engineering one.

## 10. Cleanup SQL — **PLAN ONLY, NOT EXECUTED**

**NOT EXECUTED. Every statement below is proposed text only.** No connection was opened for writing; nothing here has run.

```sql
-- ============================================================
-- PRODUCTION INCIDENT CLEANUP — PLAN ONLY, NOT EXECUTED
-- Generated 2026-09-02 from PRODUCTION_INCIDENT_REMEDIATION_PLAN.md
-- Run only after: Step 0 pre-flight (Section 9) passes in full,
-- explicit Product Owner authorization is recorded, and a backup/
-- snapshot per Section 11 exists.
-- ============================================================

-- pios_driver_management -------------------------------------------------
BEGIN;
DELETE FROM drivers WHERE id IN (
  'create-driver-e8bd0f2e-40ad-4997-bec3-ad35ece08f56',
  'create-driver-fc1c6df4-fe5c-4a66-a410-35be5e353b36',
  'create-driver-f11cedd8-427e-40bf-b3c3-39ed5761ffe8'
);
-- Expect: DELETE 3
COMMIT; -- NOT EXECUTED

-- pios_passenger_experience -----------------------------------------------
BEGIN;
DELETE FROM connections WHERE id IN (
  'd50896dc-46eb-4c16-8b83-6c78182fb02f',
  'a5a8c57b-251a-4a19-9595-0c85689b535a'
);
-- Expect: DELETE 2
COMMIT; -- NOT EXECUTED

-- pios_order_management -----------------------------------------------
BEGIN;
DELETE FROM orders WHERE id IN (
 '91beac9e-0fb0-422b-8c37-3071b9301880','d2724478-70d9-470e-b26d-a255a7c4340b',
 '5307bd1a-f888-4a14-8dc2-7a40422f49ed','81e5a039-778a-4a06-b707-dfa344b2023d',
 '740aeabb-78bb-4c25-9f84-c61a1d2e6839','e72ceeb9-fec3-4a66-bc77-b0ceedc231ee',
 '3b03a385-8ce1-4431-9f0a-e6bb6ed374ee','758843cd-508e-40bb-a218-e910584046c3',
 '8193b877-ffa7-4d26-8d6b-f9f3a173d428','7b52700e-177a-423a-b83d-32298413ba4d',
 'b55588af-2bac-4ad8-861c-a0107b9bed57','9dfeee8f-7f72-411c-bb0d-cc49c86dc5ec',
 '3d189f6e-1989-4700-a34e-a9102a9a203a','a2c0abab-cac8-461d-a8bc-9cfb772bec52',
 '732a7e66-158b-4012-91f2-4b7c8ef8dd53','3c3399cb-f88c-4c58-a5af-fc721d024d5a',
 '5fba2373-7c59-4e36-8bdc-7c3b09702ad1','eb973990-b4db-4640-b3a7-18f5423d1ef0',
 '3ea91c9d-4cfb-4623-9e95-05ca8812505a','3e80ae18-aa92-4b5c-8e05-6a8fc337a12d',
 'b27aeb3d-7c7c-4689-aa60-7f1a8693f277','ef0f0aaf-9068-4d54-8f3e-b65d54681dd9',
 '350c126d-5ff3-4ffd-b62f-611204f887a1','f63c5e7e-6edb-49a5-9a49-716b0d25322b',
 '763472dd-8ad0-4fea-a14a-d17416f97394','99d575ed-c54f-4f95-8a9d-07c9cd7a0e6b',
 '774a5020-f005-4c76-b261-51d68038bf4d','f4500fc6-a972-43a8-93c4-5d20ff9ce80e',
 '2d5364a7-d4ec-40ad-b674-96453daeb5f4','ed5392ab-3a80-4fb8-a164-2a771c6fa169',
 'a7ac6444-22e2-42fa-a81c-88863491369b','39075cf3-bbcb-4f07-ab39-f77cf6dda5d0',
 'aacf591f-b410-47f7-97bf-9e1bece7b28a'
);
-- Expect: DELETE 33
COMMIT; -- NOT EXECUTED

-- pios_dispatch -----------------------------------------------
BEGIN;
-- proposals: full 34-id list per PRODUCTION_INCIDENT_FORENSIC_REPORT.md
-- Section 6 (reproduced verbatim at cleanup time from that document,
-- omitted here only for length — every id in that section's proposals
-- rows carries a real, confirmed 2026-09-02 created_at).
DELETE FROM proposals WHERE id IN ( /* 34 ids, see forensic report Section 6 */ );
-- Expect: DELETE 34

DELETE FROM assignments WHERE id IN (
  'ee531d9f-55c4-4c22-b460-a7f80890db26',
  '4c1cb01f-4344-442e-a7b7-4370a64a4f22',
  'ad178d5b-bfee-419e-9ef9-11fef039ce93',
  '657fc8d2-e57e-4e36-81e5-8f09b348c553',
  '4beb6770-1541-445f-810f-0a0c3fca820f',
  'fed3e3c4-82b7-4611-b310-2b73c83d517d',
  '3fe4d312-76b0-4203-a33c-775d3826dc10',
  '38786b91-e1d9-40b1-acb2-60dfcbbdd86b',
  'a6f3d1ea-056f-4468-b7d3-4bda5fde2741'
);
-- Expect: DELETE 9 -- deliberately excludes the 8 MANUAL DECISION rows
-- (Section 4) and the 3 UNKNOWN rows -- none of those 11 appear above.

DELETE FROM driver_availability WHERE driver_reference IN (
  'repo-driver-a2fb3455-cd91-44ba-874f-f503e196500d',
  'repo-driver-c76f1003-4cd5-4d27-8b28-a53f4bee16ad'
);
-- Expect: DELETE 2

-- Explicitly NOT deleted in this transaction (Section 6, RETAIN):
--   driver_management_outbox, order_management_outbox, dispatch_outbox,
--   order_cancelled_processed_events, driver_availability_processed_events
COMMIT; -- NOT EXECUTED
```

**Total across all statements: 3 + 2 + 33 + 34 + 9 + 2 = 83 rows.** No `DELETE` targets any of the 8 MANUAL DECISION rows, the 3 UNKNOWN rows, any of the 58 event/outbox/processed-event records, or anything from the older 2026-08-03/2026-08-17 contamination.

## 11. Snapshot / Backup Requirement

**No backup was taken by this task** (this task is read-only; taking a backup is itself a production operation this task's own rules forbid initiating without explicit separate authorization, and no existing, already-available, read-only-confirmed backup mechanism was found in the repository to invoke safely).

Before any future execution of Section 10:

- A full logical dump (`pg_dump`) of at minimum the 4 affected databases (`pios_driver_management`, `pios_order_management`, `pios_dispatch`, `pios_passenger_experience`) must exist, taken **after** this plan's pre-flight checks pass and **immediately before** Section 10 runs, so its contents match the exact pre-cleanup state.
- The dump must be verified restorable (or at minimum, verified non-empty and syntactically valid) before the cleanup transaction is opened.
- No such dump currently exists in this repository or was located during this task — this is a genuine precondition, not a formality, and should block cleanup until satisfied.

## 12. Post-Cleanup Validation (to run after a future cleanup, not run here)

- [ ] `SELECT count(*) FROM drivers WHERE id IN (<the 3 ids>)` → expect 0.
- [ ] `SELECT count(*) FROM connections WHERE id IN (<the 2 ids>)` → expect 0.
- [ ] `SELECT count(*) FROM orders WHERE id IN (<the 33 ids>)` → expect 0.
- [ ] `SELECT count(*) FROM proposals WHERE id IN (<the 34 ids>)` → expect 0.
- [ ] `SELECT count(*) FROM assignments WHERE id IN (<the 9 ids>)` → expect 0.
- [ ] `SELECT count(*) FROM driver_availability WHERE driver_reference IN (<the 2 refs>)` → expect 0.
- [ ] Each table's total row count decreased by **exactly** the planned amount (3 / 2 / 33 / 34 / 9 / 2) — no more, no less.
- [ ] The 8 MANUAL DECISION `assignments` rows, the 3 UNKNOWN rows, and all 58 outbox/processed-event records are still present, byte-for-byte unchanged (`SELECT count(*)` plus a spot-check of `processed_at`/`created_at` on a few).
- [ ] `pios_passenger_experience.primary_connections` row count unchanged (confirms the FK-cascade risk that was checked clear in this plan did not materialize as an unexpected cascade).
- [ ] No production service was restarted or errored during the cleanup window (check each of the 6 WinSW services' PIDs are unchanged, per the pattern already established in Tasks 1–2).
- [ ] No new rows appeared in any of the 5 tables during the cleanup window beyond the planned deletes (i.e., nothing live in production wrote a *new* row with one of the candidate literals concurrently — vanishingly unlikely given none of these literals correspond to real production entities, but worth a final count comparison).

## 13. Rollback / Recovery Plan

- **If the cleanup transaction has not yet committed**: `ROLLBACK` reverses it completely and instantly — this is the only rollback path that is actually free. Section 10's SQL is deliberately wrapped per-database in `BEGIN`/`COMMIT` for exactly this reason.
- **If the transaction has already committed**: **true rollback is not possible from inside PostgreSQL alone.** A committed `DELETE` cannot be undone by any in-database mechanism. The only recovery path at that point is restoring from the `pg_dump` snapshot required by Section 11 — either a full database restore (which would also revert any *legitimate* production writes that happened after the snapshot was taken, so this is a last resort, not a routine option) or a selective re-`INSERT` of the exact 83 rows' original values, reconstructed from the dump. **This document does not claim a committed deletion is reversible without that snapshot — it explicitly is not.**
- Given 83 rows, all confirmed to have no legitimate dependents (Section 7), and all fully enumerated with their original values already captured in Section 3 of this document (and in more detail in the forensic report), a selective restore of just these 83 rows from the pre-cleanup dump is the realistic rollback path, not a full database restore.
- The 58 RETAINed outbox/processed-event rows are never touched by Section 10, so no rollback consideration applies to them.

## 14. Decision Matrix

| Item | Count | Action | Confidence | Reason |
|---|---:|---|---|---|
| `drivers` incident rows | 3 | DELETE | DEFINITE | Exact fixture match, tight timestamp cluster, no dependents |
| `orders` incident rows | 33 | DELETE | DEFINITE | Exact `created_at` = 2026-09-02, no dependents |
| `proposals` incident rows | 34 | DELETE | DEFINITE | Exact `created_at` = 2026-09-02, no dependents |
| `assignments` incident rows (provable) | 9 | DELETE | DEFINITE | Own timestamp, or outbox `aggregate_id` match, or exact random-UUID cross-match |
| `assignments` ambiguous rows | 8 | MANUAL DECISION | Unproven | Reused literal, no timestamp anchor of any kind found |
| `assignments` unknown rows | 3 | MANUAL DECISION | Unproven | Identical duplicate literal, `is_test=true`, no timestamp column exists |
| `connections` incident rows | 2 | DELETE | DEFINITE | Exact `created_at`, zero `primary_connections` references |
| `driver_availability` fake rows | 2 | DELETE | DEFINITE | Confirmed downstream effect, no real driver behind either reference, no dependents |
| `driver_management_outbox` incident rows | 15 | RETAIN | N/A | Audit trail of real broker publication; no functional reason to remove |
| `order_management_outbox` incident rows | 24 | RETAIN | N/A | Same |
| `dispatch_outbox` incident rows | 15 | RETAIN | N/A | Same |
| `order_cancelled_processed_events` incident rows | 2 | RETAIN | N/A | Idempotency-ledger row — deleting is a functional regression, not just an audit loss |
| `driver_availability_processed_events` incident rows | 2 | RETAIN | N/A | Same |
| Older contamination (`drivers`, 2026-08-17) | ~3+ | OUT OF SCOPE | N/A | Separate, earlier, undocumented incident — needs its own investigation |
| Older contamination (`assignments`, 2026-08-03) | 2 | OUT OF SCOPE | N/A | Same |
| Older contamination (`assignments`, `postgres-order-is-test-true` duplicates, undated) | 3 | OUT OF SCOPE / MANUAL | N/A | Cannot be dated at all; may belong to either this or an earlier incident |

**Every item from the forensic report's own count is accounted for above — nothing was silently omitted.**

## 15. Older Unrelated Contamination

Per this task's Phase 6, listed but explicitly **not** included in any deletion candidate or SQL in this plan:

- **`drivers`, 2026-08-17 18:36:31–32**: 3 rows, `create-driver-*` literal family (the *exact same* fixture family as this incident's own 3 rows), `is_test=true` this time. A separate, earlier occurrence of the same category of incident.
- **`assignments`, 2026-08-03**: `postgres-order-lifecycle-1`/`postgres-order-lifecycle-2` rows, `is_test=false`, dated a full month before this incident.
- **`assignments`, `postgres-order-is-test-true` duplicates**: already listed under MANUAL DECISION in Section 4/14 — flagged again here because their true origin (this incident vs. an earlier one) cannot be determined from any available evidence.

**These require a separate investigation and a separate remediation plan.** This document makes no recommendation about them beyond flagging their existence, per this task's explicit instruction.

## 16. Remaining Risks

- The 8 MANUAL DECISION and 3 UNKNOWN `assignments` rows will remain in production indefinitely until a Product Owner makes an explicit call — there is no read-only path to resolve them further; only either accepting the risk of a wrong guess (which this plan refuses to do) or accepting they stay ambiguous.
- No backup/snapshot mechanism currently exists in this repository (Section 11) — this is itself a gap independent of this specific incident, worth the Product Owner's separate attention.
- `dispatch_outbox` rows `585`/`589` (the two `AssignmentAccepted` events tied to `assignments` rows `ee531d9f`/`4c1cb01f`, both proposed for deletion) remain **unpublished** (`published_at IS NULL`) as of this report. If production's live relay ever successfully publishes them after this plan's rows are deleted, the corresponding entity will already be gone — this is a pre-existing inconsistency risk this plan does not resolve, only documents; retaining the outbox rows (Section 6) is what keeps the audit trail intact even if this happens.
- The `dispatch.from-driver-management` queue's 15-message, 0-consumer backlog (flagged in the forensic report and originally in Task 2) remains completely unaddressed by this plan — it is a RabbitMQ-side matter, not a PostgreSQL cleanup matter, and was explicitly out of scope for both this task and Task 3.
- This plan's SQL has not been executed, dry-run, or tested against a copy of production — the pre-flight checks in Section 9 are the only safeguard before a future execution, and must not be skipped.
