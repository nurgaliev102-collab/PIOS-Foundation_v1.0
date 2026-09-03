# PIOS Post-Remediation Baseline

Status: Read-only technical baseline, produced after Tasks 0–5B (production incident remediation, PostgreSQL test isolation fix, RabbitMQ test isolation fix). Supersedes `PIOS_REALITY_AUDIT.md` as the current-state reference; that document is historical context only and was not assumed to still be accurate — every finding below was re-verified directly against the repository as it exists today (2026-09-03).

**Method.** Every claim below is tagged:
- **FACT** — directly observed in committed source code, a migration file, a config file, or a command run in this session.
- **INFERENCE** — a reasonable conclusion from FACTs and code comments/KDoc, not itself directly executed or exhaustively verified.
- **MISSING** — searched for and confirmed absent.

This audit inspected repository structure, `build.gradle.kts`/`package.json` files, every backend module's controller/migration file list, RabbitMQ topology and listener code, identity/auth source, frontend routing/pages/components, and cross-checked README/module-structure documentation against that code. It did **not** execute `./gradlew build`/`./gradlew test` (explicitly excluded by this task's scope), did not read every ADR line-by-line (61 ADRs exist), and did not read every one of the ~830 files in the repository. Where a claim rests on a sample rather than an exhaustive check, this is stated at the point it's made. No production database, RabbitMQ broker, or Windows service was touched to produce this document.

---

## 1. Executive Summary

PIOS-Foundation is past the "foundation-only" stage the repository's own name suggests: **six independently-deployable Spring Boot/Kotlin backend modules, one React SPA, and two auxiliary Spring Boot services (an AI advisor and a not-yet-live platform-ops scaffold)** exist, build, and (per `windows-services/`) are deployed as native Windows services for a real pilot. A real, human-mediated dispatch flow — passenger submits an order, a human Coordinator manually proposes it to a driver via a credential-gated console, the driver accepts/declines from their own phone, the ride proceeds through Arrived → In Progress → Completed — is implemented end to end with real REST APIs, a transactional outbox, RabbitMQ eventing with dead-letter queues, and Flyway-migrated PostgreSQL persistence per module.

What is **not** present: any payment/billing module or code (zero results for `Payment`/billing anywhere in the backend), any rating/review mechanism (zero results for `Rating` anywhere in production backend code), automated/algorithmic dispatch (ADR-034 explicitly leaves the assignment algorithm undecided; today a human picks the driver), and any small-town/monotown-specific product decision (zero mentions in `docs/`). A sixth backend module, **Network Management** (Person/Profile/Connection/Invitation — the "Personal Network" foundation, Sprint 7A), is fully implemented with its own database and four REST controllers, but has **no corresponding Windows service** in `windows-services/` and **no real product screen** — its only frontend integration is a page whose own KDoc calls it "not a real product screen."

Task 2 (RabbitMQ test isolation) and the earlier PostgreSQL test isolation fix are both confirmed **currently effective**: all six modules' test suites connect to `_test`-suffixed databases and (where RabbitMQ is used) a dedicated `pios-test` vhost with credentials that have no permissions on `/`. An exhaustive grep of every `src/test` tree found no remaining connection string pointing at a production-named database or the production vhost.

## 2. Current Architecture

**Repository layout (FACT):**

```
backend/            6 independent Gradle modules (own settings.gradle.kts, root "pios-backend")
  driver-management/ passenger-experience/ order-management/
  dispatch/ network-management/ identity/
ai-advisor/         separate Gradle root — AI Advisor for Owner Control Center (ADR-056)
platform-ops/       separate Gradle root — Health/OpsCredentialGate scaffold only
frontend/           single Vite/React SPA, all product screens
windows-services/   WinSW native-service wrappers for production deployment
docs/               129 markdown documents: ADRs, product decisions, architecture, sprints, pilot ops
project-brain/      a separate, hand-maintained "current state" context set (not verified against code by this audit)
.ai/EXECUTION_PROTOCOL.md, .claude/CLAUDE.md, CLAUDE.md   session/process governance
```

Each backend module is its own Gradle subproject with **no shared code module** — `backend/settings.gradle.kts`'s own comment states this is deliberate ("Each module below is its own independently buildable and deployable unit... No shared module is declared here; each module depends on no other."), consistent with ADR-023/ADR-026 (FACT). The one exception is a single `testImplementation(project(":dispatch"))` in `driver-management/build.gradle.kts`, explicitly scoped to test-only contract verification and explicitly noted as not affecting independent deployability (FACT).

**Module boundary discipline (FACT, from migration files):** no backend module's database table has a foreign key into another module's table. Cross-module references (`order_reference`, `driver_reference` in Dispatch's `assignments`/`proposals` tables) are plain `TEXT` columns, never foreign keys — consistent with `MODULE_STRUCTURE.md`'s "no module shares physical storage with another" rule (ADR-005/ADR-009). Within a single module's own schema, real foreign keys do exist (e.g. Network Management's `person_profiles.person_id REFERENCES persons(id)`).

**Frontend architecture (FACT):** a single Vite/React 19 SPA (`frontend/`) with a flat client-side route table (`app/routes.tsx`, `react-router-dom`), 8 routed pages, no layout/nested-route nesting, no state-management library (component state + `localStorage`, no Redux/Zustand/Context-based global store beyond a small identity provider), CSS Modules (no Tailwind, no component library — an explicit architecture decision, ADR-024).

**Infrastructure/deployment (FACT, from `windows-services/`):** production runs as native Windows services (WinSW), one per backend module plus `ai-advisor`, `frontend`, and a `cloudflared` tunnel — 8 live services total, plus a `postgresql-backup` PowerShell script (not itself a WinSW service; likely Task Scheduler-driven, not verified by this audit). `platform-ops` has a service `.xml` but **no `.exe`/wrapper log**, unlike every other module — INFERENCE: configured for deployment but not currently running as a live service, consistent with its own source being a two-file health-check scaffold. **`network-management` has no entry under `windows-services/` at all** (FACT) — it is not part of the deployed production topology, despite being fully implemented backend code with its own migrated database.

## 3. Technology Stack

| Layer | Technology | Version | Source |
|---|---|---|---|
| Backend language | Kotlin | 1.9.24 | `backend/build.gradle.kts` |
| Backend framework | Spring Boot | 3.3.4 | `backend/build.gradle.kts` |
| JVM toolchain | JDK | 21 | every module's `build.gradle.kts` (`JavaLanguageVersion.of(21)`) |
| Build tool | Gradle | 8.10.2 (wrapper) | `backend/gradle/wrapper/gradle-wrapper.properties` |
| Persistence | PostgreSQL, plain JDBC (`JdbcTemplate`) | no ORM; version unpinned (README: "any version compatible... PostgreSQL 13+ expected") | `build.gradle.kts` comments, `README.md` |
| Migrations | Flyway (`flyway-core` + `flyway-database-postgresql`) | — | every module's `build.gradle.kts` |
| Messaging | RabbitMQ via Spring AMQP (`spring-boot-starter-amqp`) | version unpinned; default `guest`/`guest`, vhost `/` in production | 3 of 6 modules |
| Frontend framework | React | ^19.2.7 | `frontend/package.json` |
| Frontend build tool | Vite | ^8.1.1 | `frontend/package.json` |
| Frontend language | TypeScript | ~6.0.2 | `frontend/package.json` |
| Routing | react-router-dom | ^7.18.1 | `frontend/package.json` |
| Frontend test framework | Vitest ^4.1.10 + Testing Library (React ^16.3.2, jest-dom ^7, user-event ^14.6.1) | | `frontend/package.json` |
| Frontend lint | oxlint ^1.71.0 | | `frontend/package.json` |
| Backend test framework | JUnit 5 (`kotlin("test")`, `useJUnitPlatform()`) | | every module's `build.gradle.kts` |
| AI providers (ai-advisor) | Mock (default), DeepSeek, Ollama (local), Qwen/DashScope | — | `ai-advisor/src/main/kotlin/.../domain/` (4 `AIProvider` implementations) |

No Docker/Docker Compose exists anywhere in the repository (FACT, confirmed by README's own statement and no `Dockerfile`/`docker-compose.yml` found).

## 4. Frontend State

**Routed pages (FACT, `app/routes.tsx`):**

| Route | Page | Role |
|---|---|---|
| `/` | `DriverHome` (1323 lines) | Driver's own device: onboarding, availability, proposal inbox, active ride |
| `/i/:driverCode` | `PassengerLanding` (549 lines) | Personal invitation landing — the entrepreneur-model entry point |
| `/i/:driverCode/request` | `RideRequest` (1126 lines) | Passenger booking flow, Circle of Trust, confirmed-ride status |
| `/coordinator` | `Coordinator` (436 lines) | Credential-gated, human-mediated dispatch console |
| `/network-test` | `NetworkTest` (230 lines) | **Not a real product screen** — its own KDoc: "a minimal, internal page to exercise Network Management's own backend directly... not a polished user interface" |
| `/help/install` | `InstallHelp` | Static PWA-install guidance, public/unauthenticated by design |
| `/owner` | `OwnerControlCenter` (191 lines + `LoginScreen`/`StatusCard`/`TodayCard`/`EventFeed`/`AIAnalystCard`) | Credential-gated operations dashboard: live module health polling, daily metrics, AI-generated pilot analysis |
| `*` | `NotFound` | — |

**Design system (FACT, this session's own Tasks 4–7):** token-based CSS custom properties (`frontend/src/styles/tokens.css`), a foundation component set (`Button`, `IconButton`, `Heading`, `Text`, `Input`/`Select`, `FormField`, `Card`, `StatusMessage`, `Divider`, `LoadingState`, `ErrorState`, `DriverTrustIndicator`), applied so far to `RideRequest` and `PassengerLanding` only — `DriverHome`, `Coordinator`, `OwnerControlCenter`, `NetworkTest` still use the pre-existing, un-tokenized `ActionButton`/`Spinner`/raw CSS Modules (INFERENCE, from the component-usage pattern established across Tasks 4–7; not individually re-verified per screen in this audit).

**Loading/empty/error states:** real, componentized (`LoadingState`, `ErrorState`, `StatusMessage`) on the two migrated screens; present but less standardized elsewhere (`Spinner`, ad hoc error `<p>` blocks) — FACT for the migrated screens, INFERENCE for the rest, consistent with the design-system implementation log's own stated scope.

**Accessibility (FACT, sampled):** `StatusMessage` renders `role="status" aria-live="polite"`; focus-visible outlines defined as design tokens; touch targets sized to a `44px` minimum token. Not independently re-audited screen-by-screen in this pass — carried forward from Tasks 4–7's own verification.

**Mock/hardcoded data:** none found in product screens during this audit's sampling; `NetworkTest.tsx` explicitly keeps created test people in local component state only (by its own design, not a data-model limitation). No dedicated audit of every screen's data source was performed.

**Classification:**
- **WORKING**: DriverHome, PassengerLanding, RideRequest, Coordinator, OwnerControlCenter — real API integration, no mocked data found.
- **PARTIAL**: design-system token migration (2 of 8 screens); dark mode (tokens declare `color-scheme: light dark` but no dark-specific token overrides exist — carried-forward finding from `PIOS_DESIGN_IMPLEMENTATION_LOG.md`).
- **MOCKED/INTERNAL-ONLY**: `NetworkTest` — a real backend behind a deliberately non-product internal harness.
- **MISSING**: no dedicated screen exists yet for Network Management's product-facing use (Person/Profile/Connection/Invitation beyond `NetworkTest`).

## 5. Backend State

For each module: responsibility (per `MODULE_STRUCTURE.md`, cross-checked against actual controllers), what's real.

### Driver Management (`:8081`)
- **Responsibility**: a driver's standing and availability (FACT: `DriverController.kt`, `HealthController.kt`).
- **Real**: `POST /v1/drivers` (create), availability declaration, `GET /v1/drivers/{id}` query; PostgreSQL persistence (`drivers` table: `id`, `availability`, plus later-added `display_name`, `created_at`, `is_test` columns per V3–V5 migrations); publishes `DriverAvailabilityChanged` via a transactional outbox + RabbitMQ (`driver-management.events` exchange).
- **Tests**: 25 Kotlin test files (FACT, file count; not executed by this audit).

### Passenger Experience (`:8082`)
- **Responsibility**: passenger representation and the Circle-of-Trust "personal driver relationship" (`ConnectionController.kt`, ADR-054).
- **Real**: `POST /v1/connections`, primary-driver marking (`isPrimary`) — this is the module RideRequest's Circle of Trust and PassengerLanding's invitation-acceptance actually call (FACT, confirmed via this session's own earlier work on those screens).
- **No RabbitMQ integration** (FACT — no `RabbitMQ*.kt` file in this module; confirmed by the earlier exhaustive test-isolation grep, which found no `RabbitMQTestConnection.kt` here either).
- **Tests**: 14 Kotlin test files.

### Order Management (`:8083`)
- **Responsibility**: an order's lifecycle from submission through completion/cancellation.
- **Real**: `OrderSubmissionController`, `OrderQueryController`, `OrderCancellationController`; `orders` table (`id`, `status`, plus 8 further migrations adding origin/destination, pickup address, requested pickup time, passenger name, `is_test`); publishes `OrderSubmitted`/`OrderCancelled`/(presumably `OrderCompleted`, not individually re-verified) via outbox; consumes `AssignmentAccepted` and `AssignmentCompleted` from Dispatch (`@RabbitListener`, FACT) to update its own order status — this is the confirmed, real cross-module read-side synchronization (ADR-041).
- **Idempotency**: a dedicated `assignment_accepted_idempotency` table (V3 migration) and an `order_cancelled_idempotency`-named migration in Dispatch's own schema (V9) — FACT, real idempotency-tracking tables exist for at least these two consumed events.
- **Tests**: 38 Kotlin test files.

### Dispatch (`:8084`)
- **Responsibility**: the assignment decision — connecting an order to a driver (ADR-002).
- **Real**: `ProposalController` (propose/accept/decline/withdraw), `AssignmentController` (assign/accept/arrive/complete); `proposals` and `assignments` tables; consumes `DriverAvailabilityChanged` (from Driver Management) and `OrderCancelled` (from Order Management, bound routing key `order.cancelled` only — **not** `order.submitted`, see Section 6); publishes `AssignmentAccepted`/`AssignmentCompleted`/(likely `OrderAssigned`, not individually re-verified) consumed by Order Management.
- **Assignment mechanism is human, not algorithmic**: ADR-034 (Assignment Policy and Dispatch Decision Architecture) explicitly leaves the actual matching algorithm undecided; the real, working mechanism today is a human Coordinator (`Coordinator.tsx`, credential-gated) who looks up available drivers and orders via REST and manually calls `ProposalController` — FACT, confirmed by reading `Coordinator.tsx`'s own header and imports.
- **Tests**: 50 Kotlin test files — the largest test suite of any module, consistent with Dispatch owning the most state-machine complexity (Proposal + Assignment lifecycles, dead-letter handling, idempotency).

### Identity (`:8086`)
- **Responsibility**: authentication — registration, login, session issuance, associating an Identity with a Driver.
- **Real**: phone+password registration/login, PBKDF2-derived password hashing (`PasswordHasher.kt`), signed stateless session tokens (`SessionTokenIssuer`/`SessionTokenVerifier`, `Bearer` auth), a login rate limiter (`LoginRateLimiter.kt`), `POST /v1/identities/{id}/driver` to associate a Driver Management driver id with an Identity.
- **Explicitly not implemented, by the code's own KDoc**: phone verification (SMS OTP / Silent Network Auth / Telegram) — `Device.kt` and `VerificationChallenge.kt` exist only as **documented future shapes**, unpersisted (no migration table), unreferenced by any application service, and their own KDoc states this outright ("No code sends an SMS... Nothing constructs this type outside of tests that exist solely to document its shape"). A registered phone number is **never verified** today — FACT.
- **Tests**: 10 Kotlin test files (the smallest suite; consistent with the module's narrower, newer scope).

### Network Management (`:8085`)
- **Responsibility**: Person, Profile, Connection, Invitation — a person's identity independent of role, per ADR-037 (explicitly **not** a merger of Driver Management's or Passenger Experience's own data).
- **Real**: `PersonController`, `ProfileController`, `ConnectionController`, `InvitationController`; `persons`/`person_profiles`/`connections`/`invitations` tables with real in-module foreign keys.
- **Not deployed** (Section 2) and **not consumed by any real product screen** (only `NetworkTest.tsx`, a self-described internal harness) — FACT. This is architecturally complete but product-integration-incomplete.
- **Tests**: 13 Kotlin test files.

### AI Advisor (separate Gradle root, `:8091`)
- Not one of the 6 domain modules; supports the Owner Control Center's "AI Analyst" card (ADR-056). Real controller, budget guard (daily/monthly call caps), request-size limiting, and 4 swappable `AIProvider` implementations (Mock — the safe default; DeepSeek and Qwen — paid cloud APIs, fail closed with no key configured; Ollama — local, no key required by design). Owner-credential-gated, same shared credential mechanism as every domain module's `OwnerCredentialGate`.

### Platform Ops (separate Gradle root)
- Two files: `PlatformOpsApplication.kt`, `HealthController.kt`, `OpsCredentialGate.kt`. A scaffold only — no operational capability beyond a gated health check. Not currently deployed as a live Windows service (Section 2).

**Not implemented as modules at all** (MISSING, confirmed by directory listing and a repository-wide code search): **Payments**, **Administration**, **Analytics**, **Notifications** — all four are capabilities *ratified* in `MODULE_STRUCTURE.md` Section 3 (each with its own "Purpose/Responsibility/Owned capabilities" entry), but none has a corresponding `backend/` directory, and Notifications' own entry explicitly states scaffolding is "not authorized yet." OwnerControlCenter/ai-advisor cover some of what "Administration"/"Analytics" describe in spirit but are separate, later initiatives (ADR-043/044/056), not literally these ratified modules.

## 6. Database State

Every module: Flyway-migrated PostgreSQL, one database per module, `TEXT`-based primary keys and status columns throughout (no native Postgres enums — a deliberate choice per Dispatch's V1 migration comment, "to keep evolution of AssignmentStatus a plain data change, not a schema migration").

| Module | DB | Tables (from migrations, sampled) | Real FKs? |
|---|---|---|---|
| Driver Management | `pios_driver_management` | `drivers` (+ outbox) | n/a (single table) |
| Order Management | `pios_order_management` | `orders` (+ outbox, idempotency) | n/a |
| Dispatch | `pios_dispatch` | `assignments`, `proposals`, driver-availability projection (+ outbox, idempotency) | none (cross-module refs are plain `TEXT`) |
| Passenger Experience | `pios_passenger_experience` | `connections` (Circle of Trust) | not individually verified this pass |
| Identity | `pios_identity` | `identities`, `identity_credentials` (V1–V3) | not individually verified this pass |
| Network Management | `pios_network_management` | `persons`, `person_profiles`, `connections`, `invitations` | yes — `person_profiles.person_id`, `connections.from_person_id`/`to_person_id`, `invitations.creator_person_id` all `REFERENCES persons(id)` |

**Integrity risk identified (real, not hypothetical):** every status-like `TEXT NOT NULL` column sampled (`assignments.status`, `orders.status`, `drivers.availability`, `proposals.status`) has **no `CHECK` constraint** restricting it to the enum's actual values — the enum is enforced only in Kotlin application code. A direct SQL write (a manual fix, a bad migration, a future ORM bypass) could insert an invalid status string with nothing at the database layer to reject it. This pattern is consistent across every module sampled — INFERENCE that it holds for the remaining un-sampled tables too, not individually confirmed for each.

**Cross-module reference integrity** is, by architecture, never enforced by the database (Section 2) — a `driver_reference` in Dispatch's `proposals` table pointing at a driver id that no longer exists in Driver Management's own `drivers` table would not be caught by Postgres; this is the direct, accepted cost of the "no shared storage" module-isolation principle (ADR-005/ADR-009), not an oversight, but worth naming explicitly as a standing risk this document is asked to surface.

## 7. Event Architecture

**Implemented (FACT, confirmed via `@RabbitListener` usages and topology `companion object` constants — an exhaustive grep of `main/**/*.kt` for `@RabbitListener`, 4 results, all read):**

| Publisher | Event | Exchange | Consumer | Queue | Routing key |
|---|---|---|---|---|---|
| Driver Management | `DriverAvailabilityChanged` | `driver-management.events` | Dispatch | `dispatch.from-driver-management` | `driver.availability.changed` |
| Order Management | `OrderCancelled` | `order-management.events` | Dispatch | `dispatch.from-order-management` | `order.cancelled` |
| Dispatch | `AssignmentAccepted` | `dispatch.events` | Order Management | `order-management.from-dispatch` | (not individually re-verified) |
| Dispatch | `AssignmentCompleted` | `dispatch.events` | Order Management | `order-management.from-dispatch` | (not individually re-verified) |

**Published but confirmed unconsumed:** `OrderSubmitted` (Order Management) — the event class, its outbox-record production, and its RabbitMQ publication all exist (FACT: `OrderSubmitted.kt`, `OutboxRelayScheduler.kt` references), but Dispatch's own topology binds **only** the `order.cancelled` routing key on its `order-management.events` queue, never `order.submitted` (FACT, read directly from `RabbitMQOrderManagementTopologyConfiguration.kt`) — and no other module has any RabbitMQ configuration at all that could consume it. This is real, deliberate architecture (the Coordinator learns about new orders via a direct REST query, not an event), not a bug — but it means the event catalog's own documentation-level claims about `OrderSubmitted` should not be read as implying a live consumer exists.

**Outbox pattern**: transactional outbox + scheduled relay implemented independently in the three publishing modules (`OutboxRelay.kt`/`OutboxRelayScheduler.kt` in Driver Management, Order Management, Dispatch — FACT, all 6 files found and confirmed to exist). Identity, Passenger Experience, and Network Management have no outbox and no RabbitMQ dependency at all — they are REST-only.

**Dead-letter handling**: real, per-consumer DLQs exist for at least Dispatch's two consumer queues (`x-dead-letter-exchange`/`x-dead-letter-routing-key` arguments routing to a named `.dlq` queue on the default exchange, FACT from the topology file read in full) and are exercised by dedicated integration tests (`DriverAvailabilityDeadLetterIntegrationTest`, `AssignmentAcceptedDeadLetterIntegrationTest`).

**Idempotency**: DB-table-backed idempotency records confirmed for `AssignmentAccepted` (Order Management V3 migration) and an order-cancelled idempotency table in Dispatch (V9) — real, not just documentation.

**Documentation vs. implementation**: `EVENT_CATALOG.md` was not read in full this pass (out of scope given time); the four pub/sub pairs above are the ones this audit directly verified in code. Any further events the catalog describes should be treated as documentation-level until similarly verified.

## 8. Test Infrastructure (Post Tasks 1–2 Verification)

**PostgreSQL isolation — CONFIRMED CURRENTLY EFFECTIVE.** All 6 modules' `PostgreSQLTestDatabase.kt` connect to a hardcoded, `_test`-suffixed database name (`pios_driver_management_test`, `pios_passenger_experience_test`, `pios_order_management_test`, `pios_dispatch_test`, `pios_identity_test`, `pios_network_management_test`) — none configurable via environment variable or system property (by design, per each file's own KDoc, "so there is no override path back to production"). An exhaustive grep of every `jdbc:postgresql://` occurrence under any `src/test` tree across the whole backend found exactly these 6 matches and no others.

**RabbitMQ isolation — CONFIRMED CURRENTLY EFFECTIVE** (this session's own Task 2 fix). The 3 modules that use RabbitMQ (Dispatch, Driver Management, Order Management) each have a `RabbitMQTestConnection.kt` connecting to a hardcoded `pios-test` vhost with a dedicated `pios_test` user, plus a fail-closed `check()` guard against the constant ever silently reverting to `/`. An exhaustive grep for `virtualHost`/`CachingConnectionFactory(` under every `src/test` tree found exactly these 3 files and no others touching RabbitMQ.

**Cannot accidentally target production**: both isolation mechanisms are hardcoded (not env-var-overridable) and, for RabbitMQ, additionally broker-enforced (`pios_test` has no permissions on `/` at all — the broker refuses the connection, not just the application). This matches the design both `TEST_DATABASE_ISOLATION.md` and `RABBITMQ_TEST_ISOLATION.md` describe.

**One note, not a defect**: the RabbitMQ test password (`u2cZAscL4EP4dCwAFHSeDeP2elucyROf`) is a plaintext hardcoded credential committed to source control. It authenticates only to the isolated `pios-test` vhost with no production access, so the practical risk is low, but it is a real secret-in-source-control pattern worth naming under Section 17.

**Safe isolated tests exist** for all 6 backend modules and the frontend (`npx vitest run`, already executed multiple times this session against mocked network calls only, most recently 222/222 passing). Per this task's explicit instruction, **no backend build or test run was executed** in this audit — their existence and isolation were confirmed by reading the connection code, not by running them.

## 9. Authentication

**Implemented (FACT):**
- Registration: `POST /v1/identities/register` (phone + password).
- Login: `POST /v1/identities/login` — returns a signed session token; failure is undifferentiated (ADR-055 Decision 3's own "never distinguish why login failed" discipline, mirroring ADR-044's admin-credential precedent).
- Session: stateless, signed `Bearer` tokens (`SessionTokenIssuer`/`SessionTokenVerifier`); `GET /v1/identities/me` resolves the caller from the token's own `sub` claim; **no server-side revocation** — a token remains valid until it expires, by design (FACT, stated in `IdentityController.kt`'s own KDoc).
- Password storage: PBKDF2-HMAC-SHA256, matching the same hashing convention `OwnerCredentialGate` uses elsewhere in the codebase (210,000 iterations, per `application.yml` comments) — INFERENCE that Identity's own `PasswordHasher.kt` uses an equivalent scheme, not independently re-derived from its source in this pass.
- Driver identity: one Identity can be **associated** with a Driver Management driver id (`POST /v1/identities/{id}/driver`) — this is how a phone/password account becomes recognized as "this specific driver," not a separate driver-only auth system.
- Rate limiting: `LoginRateLimiter.kt` exists as a dedicated class (FACT of existence; behavior not independently re-verified this pass).
- Service-to-service / admin authentication: a **separate**, shared static credential mechanism (`OwnerCredentialGate`, replicated per-module, ADR-044/046/047/061) gates the Coordinator, Owner Control Center, and AI Advisor — this is not per-user identity, it's a single shared operator credential checked independently by every module that needs it.

**Explicitly not implemented (MISSING, confirmed by the code's own KDoc, not inferred):**
- Phone number verification of any kind (SMS OTP, Silent Network Auth, Telegram) — `Device`/`VerificationChallenge` are documented future shapes only, with no migration table and no application-service reference.
- Passenger-specific authentication distinct from the general Identity flow — a passenger and a driver share the same `Identity`/register/login mechanism; the only distinction is whether a `driverId` has been associated.
- Any OAuth/social login, multi-factor auth, or passkey support.

## 10. Taxi Lifecycle

| Stage | State | Evidence |
|---|---|---|
| Passenger requests ride | **IMPLEMENTED** | `RideRequest.tsx` → `OrderSubmissionController` |
| Order created | **IMPLEMENTED** | `orders` table, `OrderSubmitted` published |
| Dispatch (match to driver) | **IMPLEMENTED, but human not algorithmic** | `Coordinator.tsx` manually calls `ProposalController`; ADR-034 leaves the algorithm itself undecided |
| Driver receives offer | **IMPLEMENTED** | `DriverHome.tsx` polls/receives proposals |
| Driver accepts/declines | **IMPLEMENTED** | `ProposalController` accept/decline/withdraw; `ProposalStatus` (OPEN/ACCEPTED/DECLINED/LAPSED/WITHDRAWN) |
| Assignment | **IMPLEMENTED** | `AssignmentController`; `AssignmentStatus` ACCEPTED/ARRIVED/IN_PROGRESS/COMPLETED |
| Trip (arrived → in progress) | **IMPLEMENTED** | Assignment status transitions, driver-initiated |
| Completion | **IMPLEMENTED** | `CompleteAssignmentCommand` → `AssignmentCompleted` → Order Management marks `orders.status = COMPLETED` |
| Payment | **MISSING** | No Payments module, no payment code anywhere in the backend (repository-wide search) |
| Rating | **MISSING** | No rating/review code anywhere in the backend; `DriverTrustIndicator`'s own design decision doc confirms this was independently audited and confirmed absent |
| Price/fare | **PARTIAL — "stated," not calculated or charged** | ADR-042: a driver manually types a price (`statedPrice`) and, per ADR-057, an ETA (`statedEtaMinutes`) into the Proposal — a passenger-visible number, not a computed fare and not a transaction. |

## 11. Passenger / Driver / Operations State

**Passenger.** Screens: `PassengerLanding` (personal invitation entry), `RideRequest` (booking + Circle of Trust + live status). Actions available: accept an invitation, register/login, add a driver to their circle, submit an order, cancel an OPEN order, see live status through completion, reorder after a terminal state. Missing: no way to see trip history beyond the current order (not confirmed either way this pass — not found in the screens read, not exhaustively searched), no payment step, no rating step.

**Driver.** Screen: `DriverHome` (1323 lines — the largest single screen, consistent with it carrying onboarding + availability + proposal inbox + active-ride management). Actions available: self-onboard (creates Identity + Driver in one flow, per README Section 6), declare availability, view/accept/decline proposals, progress an assignment (arrive → in progress → complete). Missing: no visible mechanism in this audit's sampling for a driver to see their own historical earnings/stated-price totals (Owner Control Center's `TodayCard`/analytics are operator-facing, not driver-facing — not independently re-verified this pass).

**Platform / Operations.** Screens: `Coordinator` (manual dispatch), `OwnerControlCenter` (health polling every 15s with self-throttling to avoid overlapping polls under PBKDF2 cost — a real, documented production incident and fix per that screen's own KDoc; daily metrics; event feed; AI-generated pilot analysis via `ai-advisor`). Both are gated by the shared `OwnerCredentialGate` mechanism, not per-operator accounts — there is one shared operator credential, not individual operator identities (FACT, consistent with ADR-044's own design).

## 12. BlaBlaCar-Derived Principles

| Principle | Status | Evidence |
|---|---|---|
| Trust between participants | **PARTIALLY PRESENT** | Circle of Trust (`isPrimary`, ADR-054) and `DriverTrustIndicator` exist; no bidirectional trust signal from passenger to driver |
| Clear participant profiles | **PARTIALLY PRESENT** | A driver has name + availability, shown via `DriverTrustIndicator`; no photo, vehicle, or plate field exists anywhere in the domain model (confirmed absent by this session's own earlier `DriverTrustIndicator` data audit) |
| Reputation | **MISSING** | No rating/review mechanism anywhere in the backend |
| Transparent trip/order information | **PARTIALLY PRESENT** | Stated price/ETA are shown; no live map, no route, no precise fare breakdown (ADR-042/057 explicitly scope this as minimal) |
| Predictable interaction flow | **ALREADY PRESENT** | The OPEN→ACCEPTED/DECLINED/LAPSED→ARRIVED→IN_PROGRESS→COMPLETED state machine is real, tested, and consistently surfaced in both driver and passenger UIs |
| Communication | **MISSING** | ADR-059 (Ride Contact Disclosure) — "No Disclosure in the Pilot (Option C)" per its own title; no in-app messaging or contact-sharing mechanism exists |
| Safety signals | **MISSING** | No emergency/SOS mechanism, no ride-sharing-with-a-contact feature found |
| Matching | **PARTIAL, human-mediated** | Real assignment flow exists; matching itself is a human Coordinator's manual choice, not an algorithm (Section 5, Dispatch) |
| Repeat relationships | **ALREADY PRESENT — this is PIOS's core differentiator** | The entrepreneur-model personal-invitation-link mechanism (`/i/:driverCode`) and Circle of Trust are both explicitly designed around a driver's own repeat client base, per `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` (not re-read in full this pass; cited as already-ratified product decisions, consistent with this session's own earlier design-system work grounding `DriverTrustIndicator`/`PassengerLanding` in exactly these documents). |

## 13. PIOS Core Concept Alignment

The ratified product identity (`PROJECT_CONSTITUTION.md`, cited via `docs/PRODUCT_BASELINE_V2.md`'s own evidence-classified consolidation, not independently re-read in full this pass) is explicit that PIOS is infrastructure for independent drivers, not a hidden-dispatcher aggregator, and that whether it is or is not a commission-based marketplace is **still an open product decision**, not yet ratified either way.

**Architectural support for "drivers build their own business":**
- The personal-invitation-link mechanism (`/i/:driverCode`, `driverId` taken verbatim from the URL, never assigned from a platform-controlled pool) is real, implemented, and is the entire passenger-acquisition path for `PassengerLanding`/`RideRequest` — FACT, confirmed directly by this session's own earlier work on those two screens.
- Circle of Trust's `isPrimary` relationship is passenger-experience-owned (ADR-054's own title), not platform-assigned.
- No code anywhere computes a platform commission, takes a cut of `statedPrice`, or routes payment through PIOS — consistent with "not structurally reduced to a commission-based platform worker," but also consistent with "no commercial model exists yet at all" (Section 15).

**No architectural conflict was found** with this direction in this pass — the absence of a payment/commission mechanism is a gap (Section 15), not a conflict; nothing observed forces a commission model. This is a narrower claim than a full product-strategy review would need to make, since Section 12–15's product-decision-layer content was reviewed via existing consolidation documents (`PRODUCT_BASELINE_V2.md`) rather than re-derived from ADRs directly in this pass.

## 14. Small-Town / Monotown Assessment

**MISSING at the product-decision layer**: an exhaustive grep of every `docs/*.md` file for "monotown," "small town," "посёлок," and related terms returned zero matches. No product decision, ADR, or design document addresses this question anywhere in the repository today.

**Architectural observations relevant to it (INFERENCE, not a ratified decision):**
- The human-mediated Coordinator dispatch model (Section 5) does not assume driver density or an algorithmic matching pool — it could plausibly work at any scale, including one coordinator and a handful of drivers, though this was never tested or decided for that case.
- The personal-invitation-link / Circle-of-Trust model is arguably *better suited* to a small, high-repeat-relationship community than to a large anonymous city market — but this is an observation about the shape of the mechanism, not a ratified product finding.
- Nothing in the codebase hardcodes large-city assumptions (no map/routing/traffic-service dependency, no city-specific configuration found) — but nothing configures for small-town operation either; the question has not been engineered for either way.

## 15. Future PIOS Network Compatibility

**Network Management module (Section 5) is the concrete architectural seam for this** — ADR-037 explicitly separates "a person's identity independent of any single role" from Driver Management's and Passenger Experience's own role-specific data, referencing other modules only by plain string id. This is real, implemented (not just decided) architecture that could plausibly support a future "person who is sometimes a driver, sometimes a service provider, sometimes a customer of a different vertical" model — but it is currently **undeployed and unused by any real product screen** (Section 5), so this compatibility is architectural potential, not yet exercised capability.

**No architectural decision was found that would foreclose future verticals or B2B relationships** — module isolation (no shared storage, event-only cross-module communication) is, if anything, the pattern that would make adding a 7th module for a new vertical cleanest, following the same shape as the existing 6. This is an INFERENCE from the existing pattern's consistency, not a decision anyone has made about a specific future vertical.

## 16. Commercialization Readiness

**Existing monetization mechanisms**: none (MISSING — Section 10).

**Missing billing/payment infrastructure**: total — no Payments module exists (Section 5), `MODULE_STRUCTURE.md`'s own ratified "Payments Module" entry ("the platform's own record of payment-related information... never owns external settlement information") has zero corresponding code.

**Potential transaction points already visible in the domain model**: `statedPrice` (Proposal, ADR-042) is the one place a monetary figure already flows through the system today — it is passenger-visible and driver-entered, but purely informational; no code reads, sums, or acts on it beyond display. This is the most direct existing seam a future commission/subscription/fee mechanism would attach to.

**Risk of a commission-only model**: `PRODUCT_BASELINE_V2.md` itself flags whether PIOS is a commission-based marketplace as an **open** product question, not a **[RATIFIED]** direction (Section 13 above) — so no architecture currently commits PIOS to a per-order-commission model, but none has been built to support an alternative (subscription, flat platform fee, B2B licensing) either. The `statedPrice` field's own design (ADR-042, "Minimal MVP Model and Ownership Boundary") was deliberately scoped away from any commission concern.

## 17. UX/UI Baseline

Carried forward largely from this session's own Tasks 1–7 (`PIOS_TAXI_DESIGN_BRIEF.md`, `PIOS_DESIGN_SYSTEM.md`, `PIOS_DESIGN_IMPLEMENTATION_LOG.md`), re-stated here as current FACT rather than re-derived:

- **Visual language**: token-based (`--pios-*` custom properties) — color (surface/text/border/accent/semantic status/trust), typography scale, 4px spacing scale, 2-level elevation, motion durations, focus ring, 44px touch-target minimum.
- **Components**: a foundation set of 11 components (Section 4), applied to 2 of 8 screens; the remaining 6 screens still use the pre-existing `ActionButton`/`Spinner`/raw CSS.
- **Accent color**: `#2563eb`, flagged as a lower-risk *default*, not a ratified brand decision — still open for Product Owner sign-off.
- **Inconsistencies still on record** (from the implementation log, not re-verified individually here): dark mode tokens undefined; Circle-of-Trust heading/card alignment inconsistency on `RideRequest`; `'confirm-add'` step on `PassengerLanding` still has one redundant driver-name mention.
- **No rating/ranking/gamification UI exists anywhere** — a deliberate, structurally-enforced constraint (no numeric slot in `DriverTrustIndicator`'s own props), consistent with Section 10/12's findings that no such data exists on the backend either.

This audit did not re-screenshot or re-verify any screen visually — it relies on the already-executed, already-documented verification from this session's own Tasks 4–7.

## 18. Technical Risks

- **DB status columns unconstrained** (Section 6) — real, moderate-priority integrity risk, consistent across every table sampled.
- **Cross-module reference integrity is application-only** (Section 6) — architecturally deliberate, but a standing operational risk (a deleted/malformed driver id elsewhere would not be caught by any database).
- **RabbitMQ test credential hardcoded in source** (Section 8) — low practical risk (isolated vhost, no production access) but a real secret-in-source-control pattern.
- **Phone numbers are never verified** (Section 9) — anyone can register with any phone number string that passes basic validation; this is a real trust/abuse surface for a platform whose entire personal-invitation model depends on knowing who someone actually is.
- **No server-side session revocation** (Section 9) — a compromised token remains valid until natural expiry; explicitly accepted in ADR-055's own stated tradeoffs, not an oversight, but still a standing risk worth naming.
- **`network-management` deployed nowhere** (Sections 2, 5) — a fully-built module with no operational path to production; if forgotten, it will silently drift from the other 6 modules' own migration/dependency-version discipline over time.
- **0 TODO/FIXME markers found** in `backend/**/*.kt` and `frontend/src/**/*.{ts,tsx}` (FACT, direct grep) — either genuinely disciplined, or (less likely, given the density of KDoc/comment discipline observed throughout this audit) markers are simply not this codebase's convention for tracking open work; docs-based tracking (`PIOS_UX_BACKLOG.md`, implementation logs) appears to be the actual mechanism.
- **No CI configuration found** during this audit's sampling (not exhaustively searched — `.github/` was not inspected beyond the PR template mentioned in `README.md`) — build/lint/test health depends entirely on manual, session-by-session verification.

## 19. Documentation Inconsistencies

- **`README.md` says "Four independent modules" in two places** (Purpose section, Technology section) while its own "Backend startup" section lists 5 commands (`driver-management`, `passenger-experience`, `order-management`, `dispatch`, `identity`) and separately documents `network-management` as a 6th, deliberately-not-started module — the "four" language is stale, predating Identity's and Network Management's addition (FACT, direct read of `README.md`).
- **`README.md` does not mention `ai-advisor` or `platform-ops` at all** — both exist as real, separate Gradle roots with real (if differently-scoped) implementations; the README's own "Repository Structure" and "Technology" sections predate them.
- **`MODULE_STRUCTURE.md` ratifies "Payments," "Administration," "Analytics," and "Notifications" as modules** with full Purpose/Responsibility/Owned-capabilities entries, none of which has any corresponding code — this is architecture-ahead-of-implementation by design (consistent with this repository's own documentation-first philosophy), not a contradiction, but worth distinguishing from modules that exist in both doc and code.
- **Two different `ConnectionController.kt`s exist** (Passenger Experience's Circle-of-Trust connections, and Network Management's generic person-to-person connections) — this is explicitly intentional per ADR-037's own text ("not a merger of Driver Management's or Passenger Experience's own existing responsibilities"), but the identical class name across two modules is worth flagging as a documentation/searchability trap for anyone reading code without also reading ADR-037 first.
- **`PIOS_REALITY_AUDIT.md`** (the predecessor to this document) is, per this task's own framing, understood to predate the remediation work — this document does not itself re-verify every specific claim that document made, only the areas this task's own 21 sections cover.

## 20. Complete Gap Analysis

| AREA | CURRENT STATE | TARGET | GAP | PRIORITY |
|---|---|---|---|---|
| Architecture | 6 isolated modules, event-driven + REST, no shared storage | Same, extended to future verticals | Payments/Administration/Analytics/Notifications ratified but unbuilt | P2 |
| Frontend | 8 screens, 2 design-system-migrated | All screens on token/component system | 6 screens still pre-migration | P2 |
| Backend | 6 modules + 2 auxiliary, real REST/events | Same, Network Management deployed & used | NM undeployed, unused by any product screen | P1 |
| Database | Flyway-migrated, module-isolated | Same + status-column integrity | No CHECK constraints on any status enum | P2 |
| Messaging | Outbox + RabbitMQ, DLQs, idempotency for 4 event flows | Same, verified for all published events | OrderSubmitted published, zero consumers; other events not individually re-verified | P2 |
| Authentication | Phone+password, signed sessions, no verification | Verified identity | No phone verification implemented (only documented shapes) | P1 |
| Passenger | Real booking/status/Circle-of-Trust flow | Same + history + payment + rating | No trip history screen confirmed; no payment; no rating | P1 (payment/rating), P2 (history) |
| Driver | Real onboarding/availability/proposal/assignment flow | Same + earnings visibility | No driver-facing earnings/history confirmed | P2 |
| Operations | Coordinator + Owner Control Center, credential-gated | Same, individuated operator accounts | Shared static credential, not per-operator | P3 |
| Trust/Reputation | `isPrimary` + `DriverTrustIndicator`, no rating | Structured trust w/o gamification (per product constraint) | No reputation signal beyond binary primary/non-primary | P2 |
| Dispatch | Real state machine, human-mediated matching | Same + explicit assignment policy | ADR-034 leaves algorithm undecided; today fully manual | P2 |
| Payments | None | At least a recorded-payment mechanism | Entire Payments module unbuilt | P1 |
| Ratings | None | Product-constraint-compliant (non-numeric) trust signal | No mechanism exists; open product question | P2 |
| UX/UI | Token-based system on 2/8 screens; accent color unratified | Full-system consistency, ratified brand palette | 6 screens outstanding; accent sign-off pending | P2 |
| Mobile | Responsive CSS Modules, no native app | Same, PWA install path exists (`InstallHelp`) | No gap beyond general design-system rollout | P3 |
| Testing | Isolated per-module suites, ~150 backend test files, 222 frontend tests, no CI found | Same + CI enforcement | No CI configuration confirmed in this pass | P2 |
| Security | PBKDF2 auth, no phone verification, no server-side revocation, hardcoded test secret | Verified identity, defensible session model | Verification, revocation both open | P1 |
| Commercialization | `statedPrice` display only, no transaction code | At least one real monetization mechanism | Entirely unbuilt; open product question | P1 |
| Small-town support | Not addressed anywhere in docs or code | An explicit product decision | Zero documentation, zero configuration for this case | P3 |
| Monotown support | Same as above | Same as above | Same as above | P3 |
| Future PIOS Network | Network Management module exists, architecturally isolated | Deployed, used, extensible to new verticals | Undeployed, unused (Section 5/15) | P2 |

## 21. Recommended Development Sequence

This is a dependency-aware ordering, not a feature backlog — each stage is placed where it is because a later stage would otherwise be built on an assumption the earlier stage either confirms or invalidates.

1. **Foundation integrity first** — resolve the status-column/CHECK-constraint gap (Section 6) and decide the `network-management` deployment question (deploy it, or explicitly document it as intentionally dormant) before building anything new on top of either. Cheap, low-risk, and every later stage that touches these modules inherits whichever answer is chosen.
2. **Authentication/verification before any passenger- or driver-trust-facing feature** — phone verification (Section 9) is a prerequisite for any reputation, rating, or trust mechanism (Section 12/20) to mean anything: a trust signal built on top of an unverified identity is trust built on sand. This must precede Trust/Reputation work, not follow it.
3. **Dispatch decision architecture (ADR-034) before scaling matching volume** — the current human-Coordinator model works at pilot scale; deciding (even partially) the assignment-policy question is a prerequisite for any small-town/monotown or multi-city expansion (Section 14), since "one human coordinator" does not scale the same way across those contexts.
4. **Payments/commercial model before any real pricing decision is finalized** — `statedPrice` (Section 16) is the existing seam; a payments mechanism should be designed against this seam deliberately, before further product decisions (subscription vs. commission, Section 16) are made informally through feature requests instead of a dedicated Product Decision document.
5. **Trust/reputation mechanism, after verification (step 2) is real** — the product's own explicit constraint (no numeric rating/ranking, established across this session's own Tasks 1–7) means this needs its own deliberate design, not a bolt-on; doing it after verified identity exists ensures it has something real to attach to.
6. **UX/UI design-system rollout to the remaining 6 screens** — lower-risk, but should follow (not precede) any dispatch/payment/trust changes that would alter those screens' own information architecture, to avoid re-doing migrated work.
7. **Operations tooling maturity (individuated operator accounts, CI)** — can proceed in parallel with the above; it doesn't block or get blocked by product-facing work, but is a real gap (Section 18/20) worth closing before operator headcount grows past "one shared credential" being workable.
8. **Future PIOS Network activation** — deliberately last: Network Management's own architecture (Section 15) is sound and isolated, so activating it for a second vertical is additive once the first vertical (Taxi) has payments, trust, and dispatch decisions actually settled — activating it earlier would mean designing a second vertical's needs into a foundation that's still moving under the first one.

---

## Final Report

**Files created:**
- `docs/PIOS_POST_REMEDIATION_BASELINE.md` (this document)

**Files modified:** none.

**Tests/checks executed:** none in this task. (Frontend test/lint/typecheck numbers cited in Sections 4/17/18 are carried forward from this session's own already-executed Task 7 run, not re-run for this audit.)

**Production databases touched:** NO.

**RabbitMQ touched:** NO.

**Production services restarted:** NO.
