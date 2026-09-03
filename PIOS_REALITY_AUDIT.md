# PIOS Foundation Reality Audit

**Date:** 2026-09-02. **Method:** read-only inspection of the actual repository (git, source, migrations, tests, docs) plus four parallel research passes over the backend modules, each producing file:line-cited findings. Frontend, git, docs, and design findings were verified directly. Where a claim could not be verified by reading real code, it is marked as such rather than assumed.

**⚠️ See Section 19 first.** While preparing this audit, a real, unauthorized write to production data occurred — a `./gradlew build` run, launched for read-only verification, turned out to hit production-named databases because the backend's own integration tests have no isolated test database. 76 rows now exist in production `pios_driver_management`, `pios_order_management`, `pios_dispatch`, and `pios_passenger_experience` that were not real pilot activity, 72 of them not marked `is_test`. **Nothing has been deleted or modified — this is disclosure, not cleanup.** Full detail in Section 19.

---

## 1. Executive Summary

PIOS Foundation is a real, running, multi-module system, not a scaffold. Six Kotlin/Spring Boot backend modules (`identity`, `driver-management`, `passenger-experience`, `order-management`, `dispatch`, `network-management`) plus two standalone non-domain services (`ai-advisor`, `platform-ops`) and one React/TypeScript frontend are declared, build, and — for the modules that matter to the actual pilot — are deployed as live Windows services today, serving a real driver and passenger over a real public tunnel. A real, complete pilot ride (registration → order → proposal → accept → arrive → start → complete) happened on 2026-08-31, confirmed by this session's own earlier production-database read.

The core Order → Proposal → Assignment ride lifecycle is genuinely implemented end to end, with real Postgres persistence, real RabbitMQ outbox/consumer pipelines for the events that matter, and real automated tests (backend: ~600+ test methods across modules per the four research passes; frontend: 222 passing Vitest tests). There is **no automatic dispatch/matching algorithm anywhere** — every Proposal's driver is human- or frontend-chosen (the passenger's own inviting driver, or a Coordinator's manual pick); this is a confirmed architectural decision (ADR-002/ADR-034), not a gap.

Several capabilities that sound implemented from filenames or ADR titles are not: `network-management` (Person/Connection/Invitation) is fully built and tested but is not wired into any real product screen — only an internal test-harness page calls it, and the real Circle-of-trust feature the product actually uses lives entirely in `passenger-experience` instead, a different, non-integrated domain object also called "Connection." Rating/reputation, payment/billing, and a driver "vehicle" concept do not exist anywhere in the codebase. Push notifications do not exist (confirmed by an earlier read-only audit this session). A "PIOS 2.0 / Personal Business Network" vision document does not exist — the only related text is a general extensibility aspiration and an explicit disclaimer that no second vertical is scoped.

Security is uneven: `identity`, `passenger-experience`, and the sensitive query paths of `order-management`/`dispatch` require a real HMAC Bearer session token or Basic owner credential; every mutating endpoint in `driver-management`, `dispatch`, and `order-management`'s own order-submission/cancellation, plus **all** of `network-management`, has no authentication at all.

Documentation is extensive (97 files under `docs/`, 61 ADRs) but visibly lags implementation: the root `README.md` contradicts itself about how many backend modules exist and never mentions `ai-advisor`, `platform-ops`, `network-management`, Onboarding v1, or Install v1. A separate, self-declared-non-authoritative `project-brain/` directory contains at least one file (`PIOS_CURRENT_STATE.md`) that is flatly wrong today (claims no `frontend/` directory exists).

## 2. Repository / Git State

- **Branch:** `pios-product-main`. **HEAD:** `b550ed42c31e38374acca27ac90776dc99a251f8` — `feat(pios): add mobile install guidance` (2026-09-01 00:01:51 +05:00). Ahead of `origin/pios-product-main` by 2 commits (not pushed).
- **Recent history** (most recent first): `b550ed4` (Install v1), `cda1721` (WinSW config reproducibility), `6febdee` (test/production data separation — `is_test` columns), `99ebc54`/`7daa286`/`af5f9dd` (AI Advisor rollout), several pilot-hardening fixes (session token refresh, owner coordinator access, firewall/backup hardening, Cloudflare→Tailscale Funnel switch).
- **Pre-existing uncommitted changes** (all present before this audit began; none of it was touched, reverted, or altered during this task):
  - Modified, not staged: `.claude/CLAUDE.md`, `.claude/settings.json`, `CLAUDE.md`, four `ai-advisor` source files (`PilotAnalysisRequest.kt`, `DeepSeekProvider.kt`, `MockAIProvider.kt`, `application.yml`) + 3 of their test files, `frontend/src/identity/BackendIdentityProvider.ts`/`.test.ts`, and partial edits inside `frontend/src/pages/DriverHome/DriverHome.tsx`, `frontend/src/pages/PassengerLanding/PassengerLanding.tsx`/`.test.tsx`, `frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts`/`.test.ts`, `frontend/src/pages/RideRequest/RideRequest.tsx`/`.test.tsx`.
  - Untracked, not added: `.claude/settings.json.graphify-bak`, `.claude/skills/`, four new `ai-advisor` provider files (`OllamaProvider.kt`, `OllamaRestClientConfiguration.kt`, `QwenProvider.kt`, `QwenRestClientConfiguration.kt`) + 4 test files, `graphify-out/`, `logs/`, `windows-services/cloudflared/run-tunnel.ps1`.
  - None of the above is new — it is the state this repository was already in.
- **Generated by this audit's own inspection:** exactly one file, this one (`PIOS_REALITY_AUDIT.md`). A temporary `git worktree` used earlier in this session for an unrelated deployment task, and a `PostgreSQL build+test` run described in Section 19, are the only other side effects, both already cleaned up or fully disclosed.
- **Structure:** `backend/` (6 Gradle modules), `ai-advisor/` and `platform-ops/` (2 standalone Gradle projects, explicitly NOT PIOS domain modules per their own docs), `frontend/` (React SPA), `docs/` (97 files incl. 61 ADRs), `.ai/EXECUTION_PROTOCOL.md`, `project-brain/` (7 files, self-declared non-authoritative), `windows-services/` (WinSW configs for production deployment), `graphify-out/`, `logs/`.

## 3. Actual Architecture

- **Backend:** Kotlin 1.9.24, Spring Boot 3.3.4, Gradle 8.10.2 (wrapper committed), JDK 21. Six independent Gradle modules under `backend/settings.gradle.kts`: `driver-management`, `passenger-experience`, `order-management`, `dispatch`, `network-management`, `identity` — each with its own PostgreSQL database, no shared module, no module depends on another's code (confirmed: `settings.gradle.kts`'s own comment states this explicitly). `ai-advisor` and `platform-ops` are separate top-level Gradle projects, each explicitly documented as owning no domain capability.
- **Frontend:** React 19.2.7, TypeScript ~6.0.2, Vite 8.1.1, react-router-dom 7.18.1, `vite-plugin-pwa` 1.3.0. No state-management library (component state + `localStorage`, one file per persistence concern). No CSS framework — plain CSS Modules, no design-token system (zero `var(--...)` usage anywhere). No HTTP client library beyond a hand-written `fetch` wrapper (`api/apiClient.ts`). Test stack: Vitest 4.1.10 + Testing Library + jsdom. Lint: `oxlint` 1.71.0.
- **Database:** PostgreSQL, one database per backend module (`pios_identity`, `pios_driver_management`, `pios_passenger_experience`, `pios_order_management`, `pios_dispatch`, plus `network-management`'s own, isolated per ADR-037), all on **one shared local Postgres instance** with no environment separation between dev/test/production database names (see Section 19 — this is a real, currently-live risk, not a hypothetical).
- **Messaging:** RabbitMQ, one shared broker instance (also with no environment separation), used by `driver-management`, `order-management`, `dispatch` for a real transactional-outbox + relay + `@RabbitListener` consumer pattern. `identity` and `passenger-experience` have zero RabbitMQ dependency (confirmed absent from their own `build.gradle.kts`).
- **Auth:** Custom HMAC-SHA256 session tokens minted only by `identity` (`SessionTokenIssuer`), verified by a byte-for-byte-replicated `SessionTokenVerifier` class in `identity`, `passenger-experience`, `dispatch` (query endpoints only), `order-management` (query endpoints only). No JWT library, no OAuth, no third-party auth provider.
- **No Docker/Docker Compose** anywhere in the repository (confirmed by `README.md`'s own statement and by absence of any `Dockerfile`/`docker-compose.yml`).
- **No API gateway** — the frontend calls each of five backend module ports directly (8081–8086), each with its own CORS configuration.

## 4. Frontend Reality

**Routes** (`frontend/src/app/routes.tsx`): `/` (DriverHome), `/i/:driverCode` (PassengerLanding), `/i/:driverCode/request` (RideRequest), `/coordinator` (Coordinator — operator tool), `/network-test` (NetworkTest — explicitly documented internal test harness, not a product screen), `/help/install` (InstallHelp, public), `/owner` (OwnerControlCenter, owner-credential gated), `*` (NotFound).

**Pages** (`frontend/src/pages/`, line counts of the main file): DriverHome (1323 lines), RideRequest (1097), PassengerLanding (543), Coordinator (436), NetworkTest (230), OwnerControlCenter (191 + AIAnalystCard/LoginScreen/StatusCard/EventFeed/TodayCard), DriverOnboarding (112), PassengerOnboarding (68), InstallHelp (30), NotFound (27).

**Components** (`frontend/src/components/`): ActionButton, DriverCard, Header, OnboardingWalkthrough, PasswordInput, QRCard, Spinner — all genuinely reusable, each with a real single responsibility, no dead code found among them.

**Feature module:** `frontend/src/features/install/` (Install v1: `InstallPIOS`, `InstallInstructions`, `InstallSuccess`, `deviceDetection`, `installPrompt`) — the only `features/` directory; everything else follows a `pages/`+`components/` split.

**Supporting layers:** `identity/` (BackendIdentityProvider, InvitationProvider, `ContactProvider` — the latter an explicitly unimplemented placeholder interface), `persistence/` (one file per localStorage concern: `localCurrentOrder`, `localDisplayName`, `localOnboardingSeen`, `localInstallSeen`), `api/apiClient.ts` (the one shared fetch wrapper).

**Forms/loading/error/empty states:** present and consistent across DriverHome/PassengerLanding/RideRequest — a shared `Spinner` component for loading, inline `role="alert"` error paragraphs with retry buttons, an explicit "Пока нет заказов…" empty state, honest per-status ride messaging (`RIDE_STATUS_LABEL` in RideRequest.tsx maps every backend status to a distinct, plain-language string — verified this session, no raw technical status ever reaches the UI).

**Connected vs. merely present:** every route above is genuinely wired to a real backend call, verified by direct reading this session (not inferred from the route existing) — `NetworkTest.tsx` is the one exception, and it says so itself in its own KDoc ("Not a real product screen").

**Responsive/mobile:** verified directly this session via real-browser QA (Playwright) at 375×812, 412×915, and 1280×900 for DriverHome, PassengerLanding, and `/help/install` — all rendered without overflow or broken layout. No dedicated CSS breakpoint system exists; layouts use `max-width` + flex, not media-query breakpoints.

## 5. Backend Reality

All findings below are cited to file:line by four independent research passes this session; only load-bearing facts are repeated here.

**identity** (port 8086): `POST /register`, `POST /login` (public by design), `GET /me`, `GET /{id}`, `POST /{id}/driver` (Bearer-gated, `sub`-ownership checked), `GET /health/identity` (Basic owner-gated). `Identity`+`PasswordCredential` are the only persisted entities; `Device`, `Session`, `VerificationChallenge` are documented domain shapes with **no table and no application-service usage** — explicitly not built. Session tokens: HMAC-SHA256, claims `sub`/`drv`/`exp`, 30-day default TTL, no revocation mechanism. No RabbitMQ. 10 test files, ~52 tests, no dedicated unit tests for register/login application services (covered only at the controller/HTTP layer).

**driver-management** (port 8081): `POST /drivers`, `GET /drivers/{id}`, `GET /drivers`, `POST /drivers/{id}/availability` — **no auth on any of these four**. `Driver` = `id, availability, displayName, createdAt, isTest`; no vehicle concept anywhere (confirmed absent). Availability→`DriverAvailabilityChanged` outbox flow is real, transactional, RabbitMQ-published with publisher-confirm, tested against a real broker. No rating/reputation concept. 25 test files, ~89 tests.

**passenger-experience** (port 8082): all five `/v1/connections*` endpoints are Bearer-gated with real ownership checks (403/404 discipline documented per-endpoint). `Connection` = `id, driverId, passengerReference, createdAt` — deliberately minimal, no status/type field. "Primary" connection is a separate table with a structural single-primary-per-passenger invariant (PK on `passenger_reference`). Owns zero events (confirmed by code, matches its own documented scope) — submits orders to `order-management` over plain REST, not a broker publish. 14 test files, ~62 tests.

**order-management** (port 8083): `POST /orders`, `POST /orders/{id}/cancel` — no auth. `GET /orders` — three-mode auth (Basic owner / Bearer+passengerReference / Bearer driver+ids). `Order` = `id, status, origin, destination, passengerName, createdAt, pickupAddress, requestedPickupAt, isTest`; status is only `SUBMITTED`/`COMPLETED`/`CANCELLED` — **no HTTP "complete" endpoint exists**; completion happens only by consuming Dispatch's `AssignmentCompleted` event. Publishes `OrderSubmitted`/`OrderCompleted`/`OrderCancelled` for real (outbox+relay, scheduled every 2s). Consumes `AssignmentAccepted` as an **idempotency-only no-op** (never touches `Order.status`, by explicit design) and `AssignmentCompleted` as the real completion trigger. 38 test files, 149 tests.

**dispatch** (port 8084): Proposal (`create`/`accept`/`decline`/`lapse`/`GET`) and Assignment (`arrive`/`start`/`complete`/`GET`) endpoints — no auth except `GET /proposals?driverId=`. **No matching/selection algorithm exists anywhere** — `driverId` is always caller-supplied (confirmed by reading `Proposal.propose()`/`Assignment.create()`, both explicitly cite ADR-002/ADR-034 for why selection logic is deliberately not here); the only automation is an availability gate that rejects (never selects) an unavailable driver. `LAPSED` is driven by a real `@Scheduled` sweep every 30s against a configurable timeout (default 5 min) — not caller-only. Full CREATED→ACCEPTED→ARRIVED→IN_PROGRESS→COMPLETED lifecycle, each transition a real endpoint with real domain guards. Publishes `OrderAssigned`/`AssignmentAccepted`/`AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted` for real; the five Proposal-lifecycle events (`OrderProposed`/`ProposalAccepted`/`ProposalDeclined`/`ProposalLapsed`/`ProposalWithdrawn`) are **never** written to the outbox — in-process facts only, by explicit documented decision, not a bug. Consumes `DriverAvailabilityChanged` (projection) and `OrderCancelled` (auto-withdraws the affected OPEN proposal). 50 test files, 311 tests.

**network-management** (port 8085): `Person`/`PersonProfile`/`Connection`/`Invitation`, 8 endpoints, **no auth on any of them, no health endpoint, no `OwnerCredentialGate` at all** — the only backend module with zero auth infrastructure. Builds, persists, is tested (13 files, ~40 tests), and is a declared Gradle module — but the only caller anywhere in the repository is `NetworkTest.tsx`, self-documented as a non-product internal harness. `InvitationProvider.ts`/`ContactProvider.ts` on the frontend both explicitly defer real integration to a future, not-yet-written ADR.

**ai-advisor** (standalone, not a PIOS module per ADR-056): one endpoint, `POST /v1/advisor/analyze`, Basic owner-gated + its own rate/budget guard. Four provider implementations exist (Mock, DeepSeek, Ollama, Qwen) — Mock is real code but a template, the other three are real HTTP clients to real external APIs. **Mock is the active default** (`matchIfMissing = true`); no API key is configured in the checked-in config; even if switched on, a blank key fails closed before any network call. Confirmed: **calling this module today costs nothing and hits no real external API.** 13 test files, 104 tests, all against local fake HTTP servers, never the real providers.

**platform-ops** (standalone, explicitly "Not a PIOS module," ADR-046): one endpoint, `GET /v1/ops/health`, its own independent Basic credential. No target list, no service control, no logs reader — its own README says so. **Zero automated tests exist**, contradicting its own source-code KDoc's claim that its Definition of Done requires one. Not called by anything else in the repository.

## 6. Database / Infrastructure Reality

- 6 separate PostgreSQL databases, each with a Flyway-managed, additive-only migration history (no destructive migration found in any module except one same-sprint add/revert/re-add of `orders.destination`, explicitly documented as a within-sprint contract correction, not data loss). Every schema below deployment-history-verified from the actual `.sql` files, not inferred.
- One shared RabbitMQ broker instance, `guest`/`guest` credentials, no per-environment vhost separation found anywhere in configuration.
- **No environment isolation between "the database a developer runs tests against" and "the database production actually uses."** This is not a theoretical risk — see Section 19.
- No Docker, no infrastructure-as-code, no CI pipeline found that would run migrations or tests in an isolated environment automatically.
- Production runs as native Windows services (WinSW), one XML per module, `windows-services/`.

## 7. Authentication / Identity

Single-issuer HMAC session tokens (`identity` mints, four modules verify their own replicated copy of the verifier). Claims: `sub` (identityId), `drv` (driverId or null), `exp`. Shared secret via `pios.session.secret`, injected per-deployment, never committed. No revocation, no refresh mechanism beyond re-login, 30-day default TTL. A second, entirely separate Basic-auth mechanism (`OwnerCredentialGate`, PBKDF2-HMAC-SHA256) gates every module's own `/v1/health/*` and the Owner Control Center / Coordinator / AI Advisor screens — independent of the session-token system, by design (ADR-044).

**Real gap:** `driver-management` and `network-management` have no authentication mechanism of any kind on any endpoint. `dispatch`'s and `order-management`'s own order/proposal/assignment mutation endpoints (create, accept, decline, submit, cancel, arrive, start, complete) are also unauthenticated — only their query endpoints are gated. This has been true throughout the pilot to date (not introduced by recent work).

## 8. Network / Relationships / Trust

Two parallel, non-integrated systems exist under similar names:

1. **`passenger-experience`'s `Connection`** — the one actually used by the product. Backs the real "Circle of Trust" UX (invitation link → connection recorded → primary-driver designation, structurally enforced single-primary invariant). This is what `PassengerLanding.tsx`/`RideRequest.tsx` actually call.
2. **`network-management`'s `Person`/`Connection`/`Invitation`/`Profile`** — fully built, fully tested, real endpoints — but wired to nothing except `NetworkTest.tsx`'s own internal harness. ADR-037 explicitly keeps it isolated from the pilot flow. `ContactProvider.ts` (device-contact picking, meant to eventually feed this system) is an explicit, documented placeholder that always reports itself unavailable.

No document was found reconciling why two differently-scoped "Connection" concepts exist under two module names — this reads as an early architectural exploration (`network-management`) later superseded in practice by a narrower, shipped mechanism (`passenger-experience`), never formally deprecated.

## 9. Taxi Functionality

**Passenger:** real auth (phone+password via `identity`), a real profile-lite (display name only, no photo/preferences), real order creation with pickup/destination/optional scheduled time, real order status polling (3s interval) mapped to plain-language text, real cancellation (while still OPEN), real "circle of trust" driver selection when more than one connection exists, no ride history screen beyond the current/last order.

**Driver:** real auth+profile creation, real availability toggle (on/off duty), real incoming-proposal list with accept/decline, real accept-with-stated-price/ETA, full ride-progress buttons (arrive/start/complete), no ride history screen, no vehicle profile.

**Network:** real invitation-link mechanism (driver's own code in the URL), real connection recording, real primary-driver designation and removal — all via `passenger-experience`, not `network-management` (see Section 8).

**Order flow:** Order (order-management) → Proposal (dispatch, human/frontend-chosen driver) → Assignment (dispatch, auto-created on Proposal acceptance) → ride lifecycle. Confirmed end-to-end, real Postgres + real RabbitMQ, no mocked step in this specific chain.

## 10. API / Events / Messaging

REST is the primary integration mechanism for cross-module writes the frontend triggers directly (five separate ports the SPA calls). RabbitMQ (topic exchange per publishing domain, one queue per consumer, all with DLQs) is used only for the specific events listed in Section 5 — `driver-management`, `order-management`, `dispatch` publish; `dispatch` and `order-management` also consume from each other. **`identity` and `passenger-experience` publish nothing.** A **Notifications module does not exist** anywhere in the repository (confirmed by an earlier read-only audit this session, and reconfirmed here by absence from `backend/settings.gradle.kts`) — no push notifications, no email, no SMS integration of any kind exists in this codebase.

## 11. Working

- Full Order→Proposal→Assignment→ride-lifecycle flow, both the passenger-auto-propose path and the Coordinator manual-propose path.
- Driver/passenger registration, login, session tokens, CORS-gated per-origin.
- Availability toggle with real event publication.
- Circle of trust: connection recording, primary designation, removal.
- Owner Control Center: live health polling, "Сегодня" snapshot with real fan-out reads, event feed, AI Analyst card (against the mock provider by default).
- Onboarding v1 and Install v1 (both frontend-only, zero backend calls, verified this session with real-browser QA and zero production API calls observed).
- Proposal auto-lapse (real scheduled sweep).

## 12. Partially Implemented

- `AssignmentAccepted` consumption in order-management (real listener, but a no-op on `Order.status` by design — only recognizes/idempotency-tracks).
- Session/auth coverage — real and correct where it exists, entirely absent on several modules' mutation endpoints (Section 7).
- Documentation of the platform's own module count/scope (README vs. LAUNCH_CHECKLIST vs. actual `settings.gradle.kts` disagree with each other).

## 13. Mocked / Stubbed

- `MockAIProvider` (ai-advisor's active default — deterministic template text, not a real model call).
- `identity`'s `Device`/`Session`/`VerificationChallenge` (documented shapes, zero persistence, zero usage).
- `ContactProvider` (`UnimplementedContactProvider` — explicitly rejects every call).
- `network-management` as a whole, relative to the real product (real code, but not a real feature today).

## 14. Broken

- Backend integration test suites write to production-named databases with no isolation (Section 19) — this is broken infrastructure, not a broken feature, but it is real and live.
- `platform-ops`'s own documented Definition of Done (a required negative-path test) is unmet — zero tests exist in that project.
- No functional feature was found to be broken in the sense of "built but does not work as coded" — everything classified WORKING above was verified working by the cited tests/code.

## 15. Missing

- Automatic dispatch/matching algorithm (confirmed deliberate, not an oversight).
- Rating/reputation (any form).
- Payment/billing/monetization (any form) — zero code found anywhere.
- Vehicle/car profile for drivers.
- Push notifications, email, SMS (any form).
- Ride/order history screens for either role.
- A "PIOS 2.0 / Personal Business Network / multi-vertical" vision document (Section 18).
- Isolated test database/environment configuration for backend integration tests.
- Docker/CI-based reproducible environment.

## 16. Design / UX Current State

No design-token system: zero CSS custom properties in the entire frontend. A de facto consistent palette nonetheless emerged and holds across nearly all screens: `#0f172a` (primary text), `#64748b` (secondary text), `#ffffff`/`#f8fafc` (surfaces/background), `#e2e8f0` (borders), `#2563eb` (primary action). Semantic colors for error/success/warning exist but are used sparingly and consistently (`#dc2626`, `#16a34a`, `#ca8a04`). Buttons are consistently pill-shaped (`border-radius: 999px`, `min-height: 44px`), cards consistently `12px`–`16px` radius. Base typography is the system font stack (`system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif`), with exactly two deliberate `monospace` overrides (Coordinator and NetworkTest, for showing raw IDs to an operator — appropriate, not an inconsistency).

**Screens verified this session by real-browser QA** (Playwright, real viewports): DriverHome, PassengerLanding, `/help/install`, the onboarding overlays — all render cleanly at 375×812, 412×915, and 1280×900 with no overflow.

**Weakest UX surfaces, by evidence, not opinion:**
- **Coordinator and NetworkTest** are explicitly internal/operator tools, not designed for an end user — this is intentional, but means they are the least polished screens by design.
- **`network-management`'s absence from the real product** (Section 8) means there is no UI at all for whatever it was meant to eventually support.
- No ride/order history UI exists for either role (Section 15) — a real, user-facing gap, not a design defect.

## 17. Documentation vs Reality

- `README.md`'s opening paragraph claims "four independent Spring Boot/Kotlin backend modules"; its own "Backend startup" section, four paragraphs later, lists **five** and says "All five modules above are used by the current pilot flow." Neither figure mentions `network-management` (a sixth declared module), `ai-advisor`, or `platform-ops` at all. Stale.
- `docs/LAUNCH_CHECKLIST.md` is more current (correctly lists five pilot-flow modules, correctly notes `network-management`'s exclusion, correctly documents "no automatic driver matching/fallback" as a known scope boundary) but is dated to "Sprint 5" and predates the AI Advisor, Onboarding v1, and Install v1 entirely.
- `project-brain/PIOS_CURRENT_STATE.md` states "Frontend/PWA: does not exist... No `frontend/` directory" — flatly false against the current repository. The directory's own top-level file (`AI_HANDOFF.md`) explicitly disclaims authority over `docs/` for exactly this reason, so this is a known, self-acknowledged staleness risk rather than a hidden one.
- `docs/PROJECT_CONSTITUTION.md` and `docs/PRODUCT_FOUNDATION.md` are self-labeled "Foundational" (top of the documentation hierarchy). `docs/DOMAIN_MODEL.md` and `docs/SYSTEM_ARCHITECTURE.md` are self-labeled "Draft — Derived from Approved Foundation."
- The evidence-gate discipline in `docs/PIOS_PRODUCT_EVIDENCE.md` states its own Journal is "**Пока пусто**" (still empty) — true as of this audit, even though a real, complete pilot ride happened on 2026-08-31 (confirmed by this session's own earlier production-database read): the formal `PIOS_PILOT_REVIEW_PROTOCOL.md` interview session that would turn that ride into a recorded `E-NNN` entry has not yet run. Recent feature work (Onboarding v1, Install v1) proceeded under an explicit, scoped Product Owner exception to this gate rather than a registered hypothesis or evidence entry — a real, repeated departure from `.ai/EXECUTION_PROTOCOL.md`'s own stated rule ("Do not implement business logic... unless the corresponding documentation and architecture already exist"), disclosed and authorized each time it happened, not silent.

## 18. PIOS 2.0 Convergence Gap

No document titled or substantively describing "PIOS 2.0," "Personal Business Network," or a concrete multi-vertical/beyond-taxi roadmap exists anywhere in `docs/` (exhaustive search across all 97 files). The two closest hits:
- `docs/PRODUCT_FOUNDATION.md` Section 2 (Vision): PIOS is conceived as "more than a single-purpose taxi-hailing application... an extensible ecosystem" — a general aspiration, no concrete second vertical named.
- `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md`: an explicit **non-goal** statement — "no second industry is scoped, scheduled, or implied."

**What already exists that maps onto the target conceptual chain (Identity → Relationship → Trust → Network → Demand → Opportunity → Matching → Transaction → Reputation → Repeat Business → Monetization):**
- Identity ✅ (identity module), Relationship ✅ (passenger-experience Connection), Trust ✅ (primary-driver designation, "circle of trust" language throughout the frontend), Demand ✅ (Order), Opportunity ✅ (Proposal). Matching ❌ (confirmed absent — human-chosen only). Transaction ⚠️ (a ride happens and completes; no payment, no price is ever actually charged — a driver's "stated price" is display-only text, never enforced or collected). Reputation ❌ (confirmed absent). Repeat Business ⚠️ (the circle-of-trust/primary-driver mechanism is explicitly aimed at this, per product docs referenced by the modules themselves, but no repeat-order/history UI exists to make it visible to a user). Monetization ❌ (confirmed absent — zero payment/billing code anywhere).
- The driver-owns-their-own-customer-relationship principle ("PIOS must not claim ownership of participant relationships") is structurally consistent with what's built: a driver's invitation link and circle-of-trust connections belong to that driver's own data, not a platform-wide pool a dispatcher draws from — this is a real, load-bearing architectural property already in place (ADR-002/ADR-034's explicit rejection of automatic matching is part of the same stance), not just stated intent.
- Nothing in the current architecture would need to be un-built to pursue a second vertical — but nothing has been built to make that pursuit easier either (no industry-agnostic abstraction layer was found; `Order`/`Proposal`/`Assignment` are taxi-shaped by name and by field, not generically named).

## 19. Technical Risks

**Highest severity, live today:** Backend integration tests across at least `driver-management`, `order-management`, `dispatch`, and `passenger-experience` connect directly to the production-named PostgreSQL databases (confirmed in code: `PostgreSQLTestDatabase.kt` in `driver-management` hardcodes `jdbc:postgresql://127.0.0.1:5432/pios_driver_management` — the same name production's own WinSW-run instance uses). This was discovered mid-audit when a `./gradlew build` run — launched for this task's own allowed "safe build/test verification" — was found already writing to production and was killed immediately, but not before real writes landed:

| Table | New rows (today) | `is_test=false` |
|---|---|---|
| `pios_driver_management.drivers` | 3 | 3 |
| `pios_order_management.orders` | 33 | 32 |
| `pios_dispatch.proposals` | 34 | 33 |
| `pios_dispatch.assignments` | 4 | 4 |
| `pios_passenger_experience.connections` | 2 | — |

**72 of 76 rows are not marked `is_test`** and will appear as real activity in the Owner Control Center's own "Сегодня" snapshot and event feed until manually addressed. **Nothing has been deleted or corrected — this is disclosed, not remediated**, per the user's explicit instruction during this session to stop and not clean up yet. Also confirmed: a detached Gradle daemon process survived the initial kill of the launching shell command and had to be separately identified and force-stopped — anyone running `./gradlew build`, `./gradlew test`, or even an IDE's "run tests" action against this repository on this machine will reproduce this exact incident.

**Other risks, lower severity:**
- No authentication on `driver-management`'s or `network-management`'s endpoints, or on `dispatch`/`order-management`'s mutation endpoints (Section 7) — anyone who can reach these ports can create/modify orders, proposals, and assignments without any credential.
- Single shared RabbitMQ broker and single shared Postgres instance, no per-environment separation, confirmed by the incident above to be a real (not theoretical) source of cross-contamination.
- `platform-ops` ships without its own documented required test coverage.
- No CI pipeline was found anywhere in the repository — nothing currently prevents a repeat of the incident above from an automated context either.

## 20. Product / Architecture Risks

- Two non-integrated "Connection" concepts under similar names (Section 8) is a real source of future confusion for anyone extending either module without reading both.
- The evidence-gate discipline (`PIOS_PRODUCT_EVIDENCE.md`) has been formally bypassed via Product Owner exception more than once this session for real, shipped feature work — each instance was disclosed and explicitly authorized, but the pattern itself, repeated, is worth the Product Owner's own attention: is the gate still the intended process, or does it need revision to match how work is actually being authorized?
- No monetization model exists in code or in a ratified product document beyond the general principle that paid services "must not secretly buy priority in opportunity allocation" (stated in this task's own brief, not found ratified in `docs/` under that specific wording during this audit's search) — this remains entirely open.
- Documentation volume (97 files, 61 ADRs) versus documentation currency (README self-contradicting, `project-brain/` self-declared partially stale) suggests the project's own "documentation-first" discipline is not automatically self-maintaining — new work is documented at the point of decision, but existing top-level summary documents are not being revisited as later work accumulates.

## 21. Recommended Implementation Sequence

Ordered by what the evidence above shows is both urgent and unblocked — not a wishlist, no invented scope beyond what this audit directly observed a need for.

1. **Fix the test/production database isolation gap (Section 19).** Before any further backend build/test/CI work of any kind. This is the one item that actively endangers real pilot data today.
2. **Decide what to do with the 76 already-written rows** (delete, or explicitly retro-mark `is_test=true`) — a Product Owner decision, not an engineering one, since the ride-through-completion connection rows in particular could theoretically be mistaken for a second real pilot event if left as-is.
3. **Reconcile `README.md` (and ideally `docs/LAUNCH_CHECKLIST.md`) with the actual module list** — five-minute fix, currently actively misleading to anyone onboarding onto this repository.
4. **Decide `network-management`'s fate** — formally supersede/deprecate it in favor of `passenger-experience`'s own Connection model, or produce the ADR that was always deferred to wire it in for real. Leaving two same-named, non-integrated concepts live is a growing maintenance cost.
5. **Close the auth gap on `driver-management`/`network-management`/the mutation endpoints of `dispatch`/`order-management`** before any wider pilot audience, per this audit's own Section 7 findings — currently anyone who can reach these ports can act as any driver or manipulate any order.
6. Everything else this audit surfaced (Notifications, payment, rating, vehicle profile, ride history, PIOS 2.0 vision) is a real, evidenced gap but not urgent relative to the five items above — left for a future, separately issued task, per this task's own explicit instruction not to recommend implementation here.
