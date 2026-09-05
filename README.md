# PIOS

Next Generation Taxi Platform

## Purpose

PIOS is a platform under active development, governed throughout by the documentation- and architecture-first discipline in [CLAUDE.md](CLAUDE.md). As of Sprint IMPLEMENTATION-005 (Driver Proposal MVP) and Sprint VALIDATION-001, a real, runnable first vertical slice exists: four independent Spring Boot/Kotlin backend modules and one React frontend implement the Order → Proposal → Driver Acceptance → Assignment dispatch flow described in [docs/DOMAIN_MODEL.md](docs/DOMAIN_MODEL.md) and [docs/ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md](docs/ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md).

## Repository Structure

```
PIOS-Foundation_v1.0/
├── CLAUDE.md                        Entry point and operating rules for Claude Code sessions
├── README.md                        This file
├── LICENSE                          Repository license
├── .ai/
│   └── EXECUTION_PROTOCOL.md        Execution rules, milestone workflow, and reporting format
├── backend/                         Four independently buildable Spring Boot/Kotlin modules — see backend/README.md
├── frontend/                        React/TypeScript SPA (Coordinator, Driver Home, Passenger flows) — see frontend/README.md
├── docs/
│   ├── README.md                    Documentation hierarchy and index
│   └── LAUNCH_CHECKLIST.md          Manual pre-flight checklist for running the full system locally
└── .github/
    └── PULL_REQUEST_TEMPLATE.md     Pull request template
```

## Technology

- **Backend:** Kotlin 1.9.24 / Spring Boot 3.3.4, Gradle (wrapper committed, Gradle 8.10.2), JDK 21. Four independent modules (`driver-management`, `order-management`, `dispatch`, `passenger-experience`), each with its own PostgreSQL database and, for three of them, its own RabbitMQ-based outbox/consumer pipeline. See [backend/README.md](backend/README.md).
- **Frontend:** React 19 + TypeScript + Vite 8, Node.js (verified against v24). See [frontend/README.md](frontend/README.md).
- **Infrastructure:** PostgreSQL (one database per backend module) and RabbitMQ (inter-module eventing). No Docker/Docker Compose setup exists in this repository as of this writing — see "Running Locally" below for a native-install setup.

## Documentation

All documentation lives under [docs/](docs/README.md), organized into Architecture, ADR, Domain, API, Database, Security, Observability, Deployment, Development, and Product sections. Documentation precedes implementation, as described in [CLAUDE.md](CLAUDE.md).

## Development Workflow

1. Work proceeds in defined milestones, each with an explicit scope. See [.ai/EXECUTION_PROTOCOL.md](.ai/EXECUTION_PROTOCOL.md).
2. Documentation and architecture are established before any implementation.
3. Architectural changes require an Architecture Decision Record in `docs/ADR/`.
4. Changes are submitted using the template in [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md).

## Running Locally

This section is operational (how to run what already exists), not architectural — it documents facts about the current repository, not new decisions. See [docs/LAUNCH_CHECKLIST.md](docs/LAUNCH_CHECKLIST.md) for a step-by-step checklist covering the same ground in tick-box form.

### Required software

| Tool | Version verified against | Notes |
| --- | --- | --- |
| JDK | 21 | Required by every backend module's Gradle toolchain (`build.gradle.kts`). |
| Gradle | 8.10.2 | Not required as a separate install — `./gradlew`/`gradlew.bat` (committed) downloads it automatically. |
| Node.js | v24 | Required for the frontend. |
| npm | v11 | Ships with the Node.js version above. |
| PostgreSQL | No specific version pinned by any document; any version compatible with `org.flywaydb:flyway-database-postgresql` under Spring Boot 3.3.4's dependency management (PostgreSQL 13+) is expected to work. Not verified by execution in this repository's own history — no CI or setup script pins one. |
| RabbitMQ | Same as above: required by three of the four backend modules (`driver-management`, `order-management`, `dispatch`) for outbox publishing/consumption; no version pinned anywhere. |
| Docker / Docker Compose | Not required — no `Dockerfile` or `docker-compose.yml` exists in this repository. All instructions below are for a native (non-containerized) local install. |

### 1. PostgreSQL

Start a local PostgreSQL server, then create one database per backend module used by the pilot flow (`network-management` also has its own datasource, but per ADR-037 it is isolated from the pilot flow and not covered here):

```sql
CREATE DATABASE pios_driver_management;
CREATE DATABASE pios_order_management;
CREATE DATABASE pios_dispatch;
CREATE DATABASE pios_identity;
CREATE DATABASE pios_passenger_experience;
```

Each module's `src/main/resources/application.yml` expects to connect as `postgres` with no password, at `127.0.0.1:5432` — adjust the `spring.datasource` block in the relevant module if your local instance differs. No environment-variable override exists for these values today; they are only changeable by editing `application.yml` directly.

**Before running any backend module's tests**, also create each module's own separate `_test` database — never run tests against the databases above directly. See [docs/TEST_DATABASE_ISOLATION.md](docs/TEST_DATABASE_ISOLATION.md) for the full convention and why it exists:

```sql
CREATE DATABASE pios_driver_management_test;
CREATE DATABASE pios_order_management_test;
CREATE DATABASE pios_dispatch_test;
CREATE DATABASE pios_identity_test;
CREATE DATABASE pios_passenger_experience_test;
CREATE DATABASE pios_network_management_test;
```

### 2. RabbitMQ

Start a local RabbitMQ server on the default port (`5672`), with the default `guest`/`guest` credentials — three of the five pilot-flow backend modules (`driver-management`, `order-management`, `dispatch`) expect exactly this, with no override mechanism today; `identity` and `passenger-experience` have no RabbitMQ configuration at all. Spring Boot does not fail to start if RabbitMQ is unreachable (connections and topology declaration retry in the background per Spring AMQP's own default behavior) — but outbox events will never actually publish or be consumed until it is running.

**Before running `driver-management`'s, `order-management`'s, or `dispatch`'s tests**, also provision a separate, isolated test vhost and user on that same broker — never run their RabbitMQ-touching tests against the default (`/`) vhost directly. See [docs/RABBITMQ_TEST_ISOLATION.md](docs/RABBITMQ_TEST_ISOLATION.md) for the full convention and why it exists:

```powershell
$rmq = "C:\Program Files\RabbitMQ Server\rabbitmq_server-<version>\sbin\rabbitmqctl.bat"
& $rmq add_vhost pios-test
& $rmq add_user pios_test "u2cZAscL4EP4dCwAFHSeDeP2elucyROf"
& $rmq set_permissions -p pios-test pios_test ".*" ".*" ".*"
```

### 3. Migrations

No separate migration step exists or is needed: each backend module runs its own Flyway migrations (`db/migration/<module>`) automatically, synchronously, on startup, against its own database (`spring.flyway.enabled: true` in each `application.yml`). A failed migration (for example, because the database above wasn't created first) fails that module's startup.

### 4. Backend startup

From `backend/`, each module is independently runnable (no module depends on another's code or needs to start in a particular order relative to the others — only PostgreSQL and RabbitMQ must already be running):

```
cd backend
./gradlew :driver-management:bootRun       # http://localhost:8081
./gradlew :passenger-experience:bootRun    # http://localhost:8082
./gradlew :order-management:bootRun        # http://localhost:8083
./gradlew :dispatch:bootRun                # http://localhost:8084
./gradlew :identity:bootRun                # http://localhost:8086
```

All five modules above are used by the current pilot flow: `passenger-experience` records the connection created when a passenger opens a driver's invitation link (`POST /v1/connections`, called from `PassengerLanding.tsx`), and `identity` backs a driver's own self-onboarding (`POST /v1/identities`, `POST /v1/identities/{id}/driver`, called from `DriverHome.tsx`'s `BackendIdentityProvider`). `network-management` (port 8085) is not part of this flow (ADR-037) and is not started here.

### 5. Frontend startup

```
cd frontend
npm install
npm run dev       # http://localhost:5173
```

`api/apiClient.ts` and each page that calls a second (or further) backend module default to the ports above with no `.env` file required; override only if your local setup differs:

| Variable | Module | Default |
| --- | --- | --- |
| `VITE_API_BASE_URL` | driver-management | `http://localhost:8081` |
| `VITE_PASSENGER_EXPERIENCE_BASE_URL` | passenger-experience | `http://localhost:8082` |
| `VITE_ORDER_MANAGEMENT_BASE_URL` | order-management | `http://localhost:8083` |
| `VITE_DISPATCH_BASE_URL` | dispatch | `http://localhost:8084` |
| `VITE_IDENTITY_BASE_URL` | identity | `http://localhost:8086` |

See [frontend/README.md](frontend/README.md) for details.

### 6. Driver onboarding

No manual seed step exists or is required: a driver creates their own profile the first time they open the app (`/`, `DriverHome.tsx`) — an Identity is created silently through the real `identity` module, then the driver provides only their name and `POST /v1/drivers` creates their Driver profile, linked to that Identity (ADR-038 Identity Module Foundation, ADR-039 Identity-Driver Association). The manual `INSERT INTO drivers ...` step and the `CURRENT_DRIVER_ID` constant this section used to document no longer exist in this repository.

## Current Status

Implementation, not foundation-only. Four backend modules and a frontend SPA exist and build; the Coordinator → Proposal → Driver Acceptance → Assignment flow (Sprints IMPLEMENTATION-001 through 005) is implemented end to end. See [docs/LAUNCH_CHECKLIST.md](docs/LAUNCH_CHECKLIST.md) for the current, evidence-graded verdict on whether this repository is ready for an internal pilot.
