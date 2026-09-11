# PIOS Core Slice 01 — QA Environment Plan

**Status:** Operational plan. Not an ADR, not a Product Decision. It records *how* Core Slice 01 QA runs on HOME-PC without any possibility of a test connection reaching production PostgreSQL or the production RabbitMQ namespace. It provisions nothing and changes no production system.

**Date:** 2026-09-10, revised **2026-09-11** after ADR-067 § QA / Production Gate item 4 was amended (Product Owner direction) from *"no `./gradlew` on any machine hosting a production PostgreSQL instance"* to *"QA execution MUST fail closed if the test process can address a production PostgreSQL endpoint or production RabbitMQ namespace."*

**Inputs (read-only):** `docs/ADR/ADR-067-*.md`, `docs/PIOS_CORE_SLICE_01_CONSTRAINTS.md`, `docs/TEST_DATABASE_ISOLATION.md`, `docs/RABBITMQ_TEST_ISOLATION.md`, `docs/PIOS_REALITY_AUDIT.md` §19, `backend/core/**`, HOME-PC infrastructure inspection (2026-09-11).

---

## 0. Summary

QA isolation for Core is now defined by **impossibility of reaching production**, not by the physical host. Two mechanisms, both fail-closed:

| | PostgreSQL | RabbitMQ |
|---|---|---|
| Isolation primitive | a **separate PostgreSQL cluster** on a distinct port (PostgreSQL has no per-connection namespace) | a **non-`/` vhost** reached by a user with **zero permission on `/`** (broker-enforced) |
| QA target | `127.0.0.1:5433` (new cluster) → `pios_core_test`, role `pios_core_qa` (password, non-superuser) | `127.0.0.1:5672` → vhost `pios-test`, user `pios_test` — **already provisioned** (`RABBITMQ_TEST_ISOLATION.md`) |
| Code enforcement | `CoreQaSafetyGate.validatePostgres(...)` before any connection | `CoreQaSafetyGate.validateRabbitMq(...)` before any connection |
| If misconfigured / absent / production-valued | `QaSafetyViolation` thrown, **nothing connects** | `QaSafetyViolation` thrown, **nothing connects** |

`./gradlew :core:test` is safe on HOME-PC: with no QA configuration it runs the unit tests and **fails every integration test closed**, connecting to nothing.

---

## 1. HOME-PC infrastructure (inspected read-only, 2026-09-11)

| | Finding |
|---|---|
| PostgreSQL | **One** instance — service `postgresql-x64-18`, `C:\Program Files\PostgreSQL\18`, `0.0.0.0:5432`. Holds every production DB (`pios_dispatch`, `pios_order_management`, `pios_identity`, `pios_driver_management`, `pios_passenger_experience`, `pios_network_management`, `pios`), every `_test` DB, and `pios_driver_management_qa` / `pios_identity_qa`. Roles: `postgres` (superuser), `taxi` (non-super). **No `pios_core`, no `pios_core_test`, no dedicated Core QA role. No second cluster / no other PG port.** |
| RabbitMQ | **One** instance — `rabbitmq_server-4.3.2`, `0.0.0.0:5672`. vhosts: `/` (production) and **`pios-test`** (test — already provisioned). Users: `guest` (admin) and **`pios_test`** (no tags). `rabbitmqctl list_user_permissions pios_test` → **only `pios-test`** — `pios_test` has **zero permission on `/`**, so a connection as `pios_test` to `/` is `ACCESS_REFUSED` by the broker itself. |
| Runtime | JDK 21 (Temurin 21.0.12), `JAVA_HOME` set. Gradle wrapper `8.10.2` (`backend/gradle/wrapper/gradle-wrapper.properties`). |
| Other environments | Beget VPS `62.217.176.214` = production `piosapp.ru`. Not a QA target. **No staging/QA server, no CI, exist anywhere.** |

**Consequence:** RabbitMQ QA isolation already exists (broker-enforced). PostgreSQL QA isolation does **not** — it needs a second cluster (§3), which the operator provisions.

---

## 2. Forbidden values (the Safety Gate rejects every one of these)

**PostgreSQL** — `CoreQaSafetyGate` throws if the resolved config matches:
- endpoint `127.0.0.1:5432` / `localhost:5432` / `[::1]:5432` / `::1:5432` / `0.0.0.0:5432` (the production instance) or `62.217.176.214:5432`
- host `62.217.176.214` or `piosapp.ru` at any port
- database name `pios_core`, `pios_dispatch`, `pios_order_management`, `pios_identity`, `pios_driver_management`, `pios_passenger_experience`, `pios_network_management`, `pios`, `postgres`, `template0/1`, any module's `_test`/`_qa`
- database name that is not exactly `pios_core_test`
- username `postgres`
- empty password
- absent URL / unparseable URL / URL with no explicit port

**RabbitMQ** — `CoreQaSafetyGate` throws if the resolved config matches:
- vhost `/`, `""`, `" "`, `%2f`, `%2F`, or null
- username `guest`
- host `62.217.176.214` or `piosapp.ru`
- absent / non-numeric / out-of-range host or port

**Never** used anywhere: the real `pios.session.secret` (`HKLM:\SYSTEM\CurrentControlSet\Services\<svc>\Environment`), `PIOS_OWNER_*`, `frontend/.env.local`, any `windows-services/*.xml`, any `/opt/pios/*`, nginx, cloudflared, systemd.

---

## 3. Operator provisioning — the QA PostgreSQL cluster

> Additive and reversible. Nothing touches the existing `postgresql-x64-18` service, its data, its `pg_hba.conf`, its roles, or any production database. Same nature as `RABBITMQ_TEST_ISOLATION.md` adding a vhost + user.

A second PostgreSQL cluster on **port 5433**, containing only `pios_core_test`. PostgreSQL 18 is already installed, so no new download.

```powershell
$PGBIN = "C:\Program Files\PostgreSQL\18\bin"
$QADATA = "C:\PIOS-QA\pgdata-core"      # anywhere NOT under the production data dir

# 1. initialise a fresh, independent cluster
& "$PGBIN\initdb.exe" -D $QADATA -U pios_core_qa -A scram-sha-256 --pwprompt --encoding=UTF8
#    (enter a QA-only password when prompted — this becomes the pios_core_qa role's password)

# 2. put it on port 5433, loopback only
Add-Content "$QADATA\postgresql.conf" "`nport = 5433`nlisten_addresses = '127.0.0.1'"

# 3. register + start a Windows service for it (distinct name)
& "$PGBIN\pg_ctl.exe" register -N "pios-qa-postgres-core" -D $QADATA -S auto
Start-Service pios-qa-postgres-core

# 4. create the one database, owned by the QA role
& "$PGBIN\psql.exe" -h 127.0.0.1 -p 5433 -U pios_core_qa -d postgres -c "CREATE DATABASE pios_core_test OWNER pios_core_qa;"
```

This cluster's `pg_hba.conf` (generated by `initdb`) defaults to `scram-sha-256` for `pios_core_qa` on loopback — a password is required, satisfying the Gate's "empty password = FAIL". The cluster contains **no** production database, so even a hypothetical wrong URL there hits "database does not exist", not production data.

**RabbitMQ needs no provisioning** — `pios-test` / `pios_test` already exist and are broker-isolated. (One-time verification: `rabbitmqctl list_user_permissions pios_test` shows only `pios-test`.)

---

## 4. Runtime configuration (test-only)

Supplied as **system properties** on the Gradle invocation (forwarded to the test JVM by `backend/core/build.gradle.kts`) or the matching `PIOS_CORE_QA_*` environment variables. **Not** in `application.yml` / `application-test.yml`.

| Property | Value on HOME-PC | Env-var form |
|---|---|---|
| `pios.core.qa.postgres.url` | `jdbc:postgresql://127.0.0.1:5433/pios_core_test` | `PIOS_CORE_QA_POSTGRES_URL` |
| `pios.core.qa.postgres.username` | `pios_core_qa` | `PIOS_CORE_QA_POSTGRES_USERNAME` |
| `pios.core.qa.postgres.password` | *(the QA role password from §3)* | `PIOS_CORE_QA_POSTGRES_PASSWORD` |
| `pios.core.qa.rabbitmq.*` | *(omit — defaults to the ratified `pios-test` / `pios_test`)* | `PIOS_CORE_QA_RABBITMQ_*` |

No JAVA/Gradle env change beyond `JAVA_HOME` → JDK 21.

---

## 5. Run order

From `backend/`, using the wrapper.

| # | Stage | Command | Needs |
|---|---|---|---|
| 1 | **Compile** | `.\gradlew.bat :core:compileKotlin :core:compileTestKotlin` | JDK 21 |
| 2 | **Safety-Gate + unit tests** (no DB, no broker) | `.\gradlew.bat :core:test --tests "com.pios.core.qa.*" --tests "com.pios.core.domain.*" --tests "com.pios.core.application.*" --tests "com.pios.core.persistence.OrderEventListenerTest" --tests "com.pios.core.persistence.AssignmentCompletedListenerTest" --tests "com.pios.core.persistence.PrimaryConnectionDesignatedListenerTest" --tests "com.pios.core.api.ParticipantHistoryControllerTest" --tests "com.pios.core.CoreBoundaryStaticTest"` | JDK 21 only — **runnable now, before any provisioning** |
| 3 | **PostgreSQL integration** | add `-Dpios.core.qa.postgres.url=... -Dpios.core.qa.postgres.username=pios_core_qa -Dpios.core.qa.postgres.password=...` and `--tests "com.pios.core.persistence.PostgreSQL*Test"` | + QA cluster on 5433 |
| 4 | **RabbitMQ integration** | same `-D` props + `--tests "com.pios.core.persistence.CoreOrderConsumerIntegrationTest"` | + QA cluster + `pios-test` vhost |
| 5 | **Context test** | same `-D` props + `--tests "com.pios.core.CoreApplicationContextTest"` | + QA cluster + `pios-test` vhost |
| 6 | **Full suite** | `.\gradlew.bat :core:test` + the three `-Dpios.core.qa.postgres.*` props | all of the above |

Without the `-Dpios.core.qa.postgres.*` props, stages 3–6 **fail closed** at `CoreQaEndpoints.postgres()` — a `QaSafetyViolation`, no connection.

---

## 6. Safety Gate — what proves QA ≠ production

**Automated, in code, before the first integration connection** (`com.pios.core.qa.CoreQaSafetyGate`, exercised by `CoreQaSafetyGateTest`):

1. test DB name is **exactly `pios_core_test`** — any other name (incl. every production name) → FAIL
2. PostgreSQL endpoint is **not** a production endpoint (5432 on HOME-PC, the VPS) → else FAIL
3. RabbitMQ vhost is **not** `/` (nor blank / `%2F`) → else FAIL
4. RabbitMQ user is **not** `guest`; PostgreSQL user is **not** `postgres`; PostgreSQL password is **not** empty → else FAIL
5. configuration is **fully defined** (URL parseable, explicit port, host+port present) → else FAIL (ambiguity = FAIL)
6. on any violation, `QaSafetyViolation` is thrown → the test stops, **no socket opened**

**Operator one-time checks before the first stage-3 run:**

- `psql -h 127.0.0.1 -p 5433 -U pios_core_qa -Atc "SELECT datname FROM pg_database WHERE datname LIKE 'pios%'"` → **exactly `pios_core_test`** (the 5433 cluster has nothing else)
- `psql -h 127.0.0.1 -p 5433 -U pios_core_qa -Atc "SELECT rolsuper FROM pg_roles WHERE rolname='pios_core_qa'"` → `f` (non-superuser)
- `rabbitmqctl list_user_permissions pios_test` → **only `pios-test`**
- confirm the `-Dpios.core.qa.postgres.url` value is `...:5433/pios_core_test` (not `5432`, not a production name)

---

## 7. Proving production was not touched (after any run)

- `psql -h 127.0.0.1 -p 5432 -U postgres -Atc "SELECT datname FROM pg_database"` — before/after the QA window, **identical**. No `pios_core` / `pios_core_test` on 5432.
- production row counts + newest-timestamp for `pios_dispatch.assignments`, `pios_order_management.orders`, `pios_passenger_experience` primary-connection table, `pios_driver_management.drivers` — before/after, **unchanged** (the same check `TEST_DATABASE_ISOLATION.md` / `RABBITMQ_TEST_ISOLATION.md` used for their incidents).
- `rabbitmqctl list_queues -p /` message counts + `list_consumers -p /` — unchanged; **no `core.*` queue on `/`** (Core's queues, if created, are in `pios-test`).
- `rabbitmqctl list_queues -p pios-test` — only `core.from-*` queues (test harness objects, in the isolated vhost).
- Gradle `--info` / test logs — connection lines show only `127.0.0.1:5433` and `127.0.0.1:5672` vhost `pios-test`; no `5432`, no `/`, no `62.217.176.214`.
- `git -C <repo> status --porcelain -- backend/core src` — no unexpected source change from the run.

A change on the two production row-count / queue-count checks is a stop-everything incident (as 2026-09-02 was).

---

## 8. What this document does not do

- Does not create the 5433 cluster, the `pios_core_qa` role, or `pios_core_test`. Does not run Gradle. Does not connect to any database or broker.
- Does not change `application.yml`, ADR-067's Boundary/Decision, or any Core architecture.
- Does not decide whether to deploy `pios-core` — that remains ADR-067 § QA / Production Gate item 2 and the later "PRODUCTION SAFETY GATE" task.
- Does not add CI.
