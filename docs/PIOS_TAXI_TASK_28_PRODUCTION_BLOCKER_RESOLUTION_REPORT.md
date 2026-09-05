# Task 28 — Production Blocker Resolution Audit

**Status: Both Task 27 Hard Stop conditions resolved and re-verified. No deployment was performed — per this task's own objective, resolving the blockers is not authorization to deploy.**

Baseline: `docs/PIOS_TAXI_TASK_26_PRODUCTION_DEPLOYMENT_READINESS_REPORT.md`, `docs/PIOS_TAXI_TASK_27_PRODUCTION_DEPLOYMENT_REPORT.md` — both re-read in full this session before any check or change, per this task's own Critical Safety Rule.

Note on this task's own instructions: the incoming task message was cut off mid-transmission, ending inside the Phase 0-B SQL query block, before any Phase 1 (remediation mechanics) or Phase 2 (re-verification gate) instructions were received. Phase 0 (fresh verification) was completed in full as specified. Rather than infer a remediation approach for two production-data/production-configuration changes, the two concrete resolution options for each blocker were presented to the operator directly and executed only after explicit selection. See Section 3.

---

## 1. Phase 0 — Fresh Blocker Verification

Both blockers were re-verified from the live environment before anything was assumed carried over from Task 27.

### 1.A Driver-management secret

Re-confirmed the mechanism: WinSW's child `java.exe` process for each `pios-*` service is launched by the Service Control Manager using that service's own registry `Environment` override at `HKLM:\SYSTEM\CurrentControlSet\Services\<service>\Environment` — not the checked-in XML (already established by Task 27 as stale/inaccurate for every service's owner credentials too).

Improvement over Task 27's method: instead of visually comparing raw secret values (which required displaying the secret in a tool result), each service's `PIOS_SESSION_SECRET` was fingerprinted with SHA-256 and only the hash and length were compared — the raw value was never printed to any output, report, or chat message at any point in this task.

| Service | `PIOS_SESSION_SECRET` present? | SHA-256 fingerprint |
|---|---|---|
| pios-dispatch | Yes | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| pios-order-management | Yes | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| pios-identity | Yes | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| pios-passenger-experience | Yes | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| pios-driver-management | **No — absent** | — |

Confirms Task 27's finding exactly, with a stronger consistency proof (identical hash across all four, not just "observed to match"). No change in the underlying facts since Task 27 (git diff confirmed no source/config file touching this changed in between).

### 1.B Duplicate OPEN proposals

Re-ran the exact V14 pre-check query, read-only, plus (new this task) a full row-level inspection of every affected row's relationships, per this task's own Critical Safety Rule ("do NOT modify production proposal rows until the exact duplicate rows and their relationships are fully inspected").

Query (with the `ORDER BY` this task specified):
```sql
SELECT order_reference, COUNT(*) AS open_count
FROM proposals
WHERE status = 'OPEN'
GROUP BY order_reference
HAVING COUNT(*) > 1
ORDER BY order_reference;
```

Result: identical to Task 27 — same 5 `order_reference` values, `open_count = 8` each.

**New finding from full row-level inspection (121 rows total: 81 `LAPSED` + 40 `OPEN`, across the 5 order references):**
- **Every single one of the 121 rows has `is_test = true`.** This is not inferred or assumed — it is the literal value of the `proposals.is_test` column (added by V11, already applied in production) on every affected row, checked individually.
- Every row's `driver_reference` and `order_reference` matches, verbatim, `PostgreSQLProposalRepositoryContractTest`/`ProposalControllerPostgreSQLIntegrationTest`-style fixed test-fixture identifiers (e.g. `contract-test-findbydriver-driver`, `postgres-findbyorder-driver-1`).
- `created_at` timestamps span 2026-08-03 through 2026-08-17 — repeated occurrences over roughly two weeks, consistent with recurring test runs, not a single one-off incident.
- Dispatch's own `PostgreSQLTestDatabase.kt` (re-read this session) explicitly targets `pios_dispatch_test` and its own comment states "deliberately never `pios_dispatch` itself" — the currently-checked-in test harness is correctly configured. This task did not determine when or how these particular 121 rows reached the production database instead of the test database; that is a historical/forensic question outside a blocker-resolution task's scope. What is now established with certainty is that the data itself is unambiguously self-identified as test data via the same `is_test` mechanism this codebase's own "test/production data separation" effort built for exactly this purpose.

**New finding beyond the original two blockers, discovered during relationship inspection — flagged, not acted on:**
Before touching anything, the `assignments` table (dispatch's own, no FK to `proposals` — cross-aggregate references in this schema are by string, not foreign key, consistent with the module's established "reference, not ownership" pattern) was checked for rows sharing the same 5 order references:

```sql
SELECT status, is_test, COUNT(*) FROM assignments
WHERE order_reference IN (<the 5 order_references>)
GROUP BY status, is_test;
```

| status | is_test | count |
|---|---|---|
| CREATED | true | 73 |
| CREATED | **false** | **3** |

**76 assignment rows share these same 5 order references. 73 are `is_test = true` (consistent with the proposals finding), but 3 are `is_test = false`** — despite carrying an order reference that is otherwise unambiguously a test fixture identifier. This is a genuine anomaly this task did not resolve: it was outside the operator's approved scope (which covered `proposals` only), and the safety rule against modifying data before full inspection applies with even more force to a set of rows whose own self-tagging is internally inconsistent. **No row in `assignments` was read, modified, or deleted by this task.** This is flagged for a separate, dedicated decision — see Section 5.

`trips` was also checked and does not exist in production yet (V12 not applied — consistent with Task 26/27's own finding that the dispatch migration arc has not been deployed).

## 2. Operator Decision Point

Since the incoming task message was truncated before specifying remediation mechanics, and both fixes are production mutations (a registry-level service configuration write; a `DELETE` against live proposal rows), two concrete options were presented for each blocker rather than one being chosen unilaterally. The operator selected:
- **Secret:** copy the existing, already-verified-identical secret value into driver-management's own registry `Environment` override — no new value generated, matching ADR-055's one-shared-secret model exactly.
- **Duplicate proposals:** delete all 121 `is_test = true` rows (not just the minimum 35 needed to pass V14) for the 5 affected order references — a full cleanup of the confirmed-test contamination, not a partial workaround.

## 3. Remediation Actions Taken

### 3.1 Backup, before any deletion
Before the `DELETE`, the exact 121 rows about to be removed were exported, unmodified, to a local CSV file:
`C:\Temp\claude\c--Projects-PIOS-Foundation-v1-0\97986fc3-ab79-4c27-a6e6-87657d17a31b\scratchpad\task28_proposals_backup_20260903_221755.csv`
(122 lines: 1 header + 121 data rows, verified by line count immediately after export, before the delete ran.) This file is local to the working environment, not committed to the repository.

### 3.2 Deletion executed
```sql
DELETE FROM proposals
WHERE order_reference IN (
  'contract-test-findbydriver-order-1',
  'contract-test-findbydriver-order-2',
  'contract-test-findbyorder-order',
  'contract-test-findbyorder-other-order',
  'postgres-findbyorder-order-1'
)
AND is_test = true;
```
Result: `DELETE 121` — exactly the row count backed up in 3.1, no more, no fewer. The `AND is_test = true` guard means this statement could only ever remove rows already self-identified as test data, regardless of naming — it would not have matched any real row even if one had coincidentally shared a name.

### 3.3 Secret provisioned
Read `PIOS_SESSION_SECRET` from `pios-dispatch`'s own registry `Environment` value (already confirmed identical across the four services that had it), appended it to `pios-driver-management`'s own `Environment` multi-string registry value alongside its existing `PIOS_OWNER_USERNAME`/`PIOS_OWNER_PASSWORD_ITERATIONS`/`PIOS_OWNER_PASSWORD_HASH`/`PIOS_OWNER_PASSWORD_SALT` entries (all four preserved, verified present after the write), via a direct registry write. **The secret value was never printed to any tool output, this report, or the conversation at any point** — only its length (44, matching on both sides) and a SHA-256 fingerprint were compared.

**This registry write does not take effect on the already-running `pios-driver-management` process** — Windows services read their registry `Environment` block at process start, not live. No service was restarted by this task (confirmed in 4.2), so this change is currently inert on the live process and will only take effect the next time `pios-driver-management` is started — i.e., during an actual deployment, not before.

## 4. Re-Verification Gate (Phase 2)

Both checks re-run fresh, independently, after the above actions.

### 4.1 V14 pre-check — now PASSES
```sql
SELECT order_reference, COUNT(*) AS open_count
FROM proposals WHERE status = 'OPEN'
GROUP BY order_reference HAVING COUNT(*) > 1 ORDER BY order_reference;
```
**Result: 0 rows.** Total `OPEN` proposals in production dropped from 61 to **21** (61 − 40 deleted `OPEN` rows = 21, exact match). Total `proposals` table row count: 435 (down from 556 by the 121 deleted rows). V14's `CREATE UNIQUE INDEX ... WHERE status = 'OPEN'` would now apply cleanly against production.

### 4.2 Secret — now consistent across all five services
Same SHA-256 fingerprint (`0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839`) confirmed present on **all five** services, including driver-management, immediately after the registry write.

**No service restart occurred as a side effect of this task**, confirmed by re-checking every `java.exe` process's own `StartTime` before and after — identical process IDs and start times throughout (earliest 2026-09-01 22:47, latest 2026-09-03 20:36, all predating this task's own work). The registry write is present but dormant until an actual future restart.

## 5. Remaining Open Item (not a Task 27 Hard Stop, but flagged)

**76 `assignments` rows share the same 5 test-fixture order references as the deleted proposals; 3 of them are `is_test = false`.** This was discovered only during this task's own relationship inspection (Section 1.B) and was never part of Task 27's two Hard Stop conditions, so it does not block V14 (which only concerns `proposals`) and was left completely untouched. It is a separate, real data-hygiene question — specifically, the 3 `is_test = false` rows deserve individual review before any assumption is made about them, since their own self-tagging disagrees with what their order reference otherwise strongly suggests. **Recommend a dedicated follow-up task before any further data cleanup in `assignments`.**

## 6. Production Safety Statement

- **Database:** one `DELETE` statement executed against production `pios_dispatch.proposals`, scoped by `order_reference IN (...) AND is_test = true`, affecting exactly the 121 rows backed up beforehand and inspected in full. No other table was modified. `assignments`, `trips` (does not exist yet), and every other `proposals` row (all 435 remaining) were read-only or untouched.
- **Service configuration:** one registry `Environment` write to `pios-driver-management`, additive only (existing keys preserved), copying an already-verified value — no new secret generated, no other service's configuration touched.
- **No service was stopped, started, or restarted.**
- **No migration was applied.** Flyway state is unchanged from Task 27 (dispatch still at V11, order-management at V10, driver-management at V5) — this task cleared the *pre-conditions* for V12–V14/V11 to apply cleanly in a future deployment, it did not apply them.
- **No application code, migration file, or frontend file was modified.**
- The backup file (Section 3.1) exists locally as a rollback artifact for the deleted proposal rows, should the operator ever want them restored.

## 7. Final State

| Item | Before this task | After this task |
|---|---|---|
| `pios-driver-management` `PIOS_SESSION_SECRET` | Absent | Present, verified byte-length-identical to the other four services (dormant until next restart) |
| Production `proposals` duplicate `OPEN` rows (V14 blocker) | 5 order references, 40 duplicate `OPEN` rows | 0 |
| Production `proposals` total rows | 556 | 435 |
| Production `proposals` total `OPEN` rows | 61 | 21 |
| `assignments` rows sharing the same 5 order references | 76 (73 test-tagged, 3 not) | 76 (unchanged — out of scope, flagged for follow-up) |
| Flyway state (dispatch / order-management / driver-management) | V11 / V10 / V5 | V11 / V10 / V5 (unchanged — no migration run) |
| Services running | All 7, same processes | All 7, same processes (no restart) |

## 8. Next Task

Both of Task 27's Hard Stop conditions are now resolved and independently re-verified. Per this task's own objective ("do NOT deploy anything yet"), this is **not** authorization to proceed to deployment. The appropriate next step is a fresh Task 27-style Phase A preflight re-run — from the top, not assumed — since Task 27's own report explicitly cautioned that its findings would not necessarily still hold at retry time; this task's own new finding (Section 5, the 3 non-test-tagged `assignments` rows) is a candidate for a dedicated look before or alongside that retry, though it does not block V14 itself.

---

*Per this task's own implicit framing: blockers resolved, nothing deployed. This report is not committed or pushed automatically.*
