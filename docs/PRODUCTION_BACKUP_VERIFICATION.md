Status: Backup created and verified 2026-09-02, specifically to satisfy Precondition 1 of [PRODUCTION_INCIDENT_REMEDIATION_PLAN.md](PRODUCTION_INCIDENT_REMEDIATION_PLAN.md) ("A verified production database backup/snapshot exists"), which [PRODUCTION_INCIDENT_CLEANUP_REPORT.md](PRODUCTION_INCIDENT_CLEANUP_REPORT.md) recorded as unmet. **This task did not delete, update, insert, or otherwise modify any production data** — every operation was `pg_dump` (a read-only, consistent-snapshot export) or local, read-only file/checksum verification.

## 1. Backup Date/Time

| | |
|---|---|
| Start | 2026-09-02 21:26:37 (local machine wall-clock) |
| End | 2026-09-02 21:27:35 (local machine wall-clock) |
| Duration | 58 seconds, all 4 databases |

## 2. Databases Backed Up

Verified against the actual repository configuration before backing up (`backend/*/src/main/resources/application.yml`), not invented — the 6 configured databases are `pios_driver_management`, `pios_order_management`, `pios_dispatch`, `pios_identity`, `pios_network_management`, `pios_passenger_experience`; the 4 backed up here are exactly the 4 the forensic report and remediation plan identify as incident-affected:

- `pios_driver_management`
- `pios_order_management`
- `pios_dispatch`
- `pios_passenger_experience`

(`pios_identity` and `pios_network_management` were not backed up — confirmed clean of incident data in [PRODUCTION_INCIDENT_FORENSIC_REPORT.md](PRODUCTION_INCIDENT_FORENSIC_REPORT.md) Section 4/6, and out of this task's stated scope.)

## 3. PostgreSQL Version

Server and tooling versions match exactly, eliminating any dump/restore compatibility risk:

- Server: `PostgreSQL 18.1 on x86_64-windows, compiled by msvc-19.44.35221, 64-bit`
- `pg_dump`: `18.1`
- `pg_restore`: `18.1`

## 4. Backup Format

Custom format (`pg_dump -Fc`) — compressed, supports selective/structural inspection via `pg_restore --list` without a full restore, and is the format recommended for exactly this kind of verified-before-use backup.

**Command structure used** (credentials omitted — password was the empty string already documented in `README.md`'s own "Running Locally" section for this local `postgres` user; no separate secret exists to redact):

```
pg_dump -h 127.0.0.1 -U postgres -d <database> -Fc -f "<destination>\<database>.dump"
```

Run once per database, sequentially, each as its own independent invocation.

## 5. Local Backup Location

```
C:\PIOS_BACKUPS\production_incident_2026-09-02\
```

Outside the repository, as required — nothing under this path is tracked by, or intended to be committed to, git.

## 6. File Sizes

| File | Size (bytes) |
|---|---:|
| `pios_driver_management.dump` | 30,695 |
| `pios_order_management.dump` | 99,222 |
| `pios_dispatch.dump` | 95,480 |
| `pios_passenger_experience.dump` | 10,559 |

All 4 files exist and are non-zero.

## 7. SHA-256 Checksums

Stored alongside the dumps at `C:\PIOS_BACKUPS\production_incident_2026-09-02\CHECKSUMS.sha256`.

| Database | Dump file | Size | SHA-256 | Verification result |
|---|---|---:|---|---|
| `pios_driver_management` | `pios_driver_management.dump` | 30,695 | `91952ef84e5c48db582607f412f4a20886edd4431bc07c7291027ad074acaf01`* | PASS |
| `pios_order_management` | `pios_order_management.dump` | 99,222 | `ec2a05a2ba45f5798618ef12449e923fdb2242de8cc5f9b452a7238c5893d404`* | PASS |
| `pios_dispatch` | `pios_dispatch.dump` | 95,480 | `6cfaaca8f2fff3ba2b20b242160e52b7f6055093cbdcd9fbbc635965d86c184d` | PASS |
| `pios_passenger_experience` | `pios_passenger_experience.dump` | 10,559 | `f079d2f0f9c887cfef0cd480bca0168dc6c61af68b7719c02583a0851ae1c064` | PASS |

*Each checksum is a 64-character SHA-256 hex digest, independently confirmed at exactly 64 characters via `awk '{print length($1)}'` against `CHECKSUMS.sha256` — no truncation or corruption in the recorded value.

## 8. Verification Method

For each of the 4 dumps:

1. **Existence**: confirmed via directory listing (`ls -la`).
2. **Non-zero size**: confirmed via the same listing (Section 6).
3. **PostgreSQL-tooling inspection**: `pg_restore --list <file>.dump` run against each dump.
4. **Structural validity**: each dump's TOC header confirms `Format: CUSTOM`, the correct `dbname`, `Dumped from database version: 18.1`, `Dumped by pg_dump version: 18.1`, and a well-formed Table of Contents (no truncation/corruption errors from `pg_restore`).
5. **Table completeness cross-check**: `pg_restore --list` output's `TABLE DATA` entry count was compared against each database's actual table count (from `information_schema.tables`, independently established during Task 3/4's own forensic work):

| Database | Expected tables | `TABLE DATA` entries found in dump | Match |
|---|---:|---:|---|
| `pios_driver_management` | 3 (`drivers`, `driver_management_outbox`, `flyway_schema_history`) | 3 | ✅ |
| `pios_order_management` | 4 (`orders`, `order_management_outbox`, `order_management_processed_events`, `flyway_schema_history`) | 4 | ✅ |
| `pios_dispatch` | 7 (`proposals`, `assignments`, `dispatch_outbox`, `driver_availability`, `order_cancelled_processed_events`, `driver_availability_processed_events`, `flyway_schema_history`) | 7 | ✅ |
| `pios_passenger_experience` | 3 (`connections`, `primary_connections`, `flyway_schema_history`) | 3 | ✅ |

No `pg_restore --list` invocation reported any archive error, truncation warning, or malformed-entry message.

**Restoration into a live database was not performed** — not required by this task, and no disposable/local instance separate from the production one was available to test into without introducing new infrastructure. `pg_restore --list`'s successful, error-free TOC parse is standard, sufficient evidence that a custom-format archive is not corrupted and is restorable in principle.

## 9. Verification Result

**4 / 4 databases backed up and verified.** All pass every check in Section 8.

## 10. Confirmation That No Production Data Was Modified

- `pg_dump` is inherently read-only against the source database — it opens a single `REPEATABLE READ` transaction, reads a consistent snapshot, and writes only to the local output file; it issues no `INSERT`/`UPDATE`/`DELETE`/`DDL` of any kind.
- No `DELETE`, `UPDATE`, `INSERT`, `TRUNCATE`, `ALTER`, `CREATE`, or `DROP` statement was executed against any production database in this task.
- No production PostgreSQL service was restarted.
- RabbitMQ was not touched, queried, or connected to at any point in this task.
- No application code, test, or build was run.
- The 83 incident rows identified in [PRODUCTION_INCIDENT_REMEDIATION_PLAN.md](PRODUCTION_INCIDENT_REMEDIATION_PLAN.md), the 8 manual-decision assignments, the 3 unknown assignments, the 58 event/outbox/processed-event records, and all older contamination remain completely untouched — this backup now simply *captures* their exact current state, it does not act on them.

## 11. What This Unblocks

With this backup, **Precondition 1 of [PRODUCTION_INCIDENT_REMEDIATION_PLAN.md](PRODUCTION_INCIDENT_REMEDIATION_PLAN.md) is now satisfied.** The remaining precondition checks (2–7) were all already confirmed passing in [PRODUCTION_INCIDENT_CLEANUP_REPORT.md](PRODUCTION_INCIDENT_CLEANUP_REPORT.md) as of 2026-09-02. Execution of the 83-row cleanup itself remains a **separate, not-yet-authorized task** — this task does not resume or perform it.
