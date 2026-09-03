# PIOS Taxi V1 — Git Checkpoint Report (Tasks 0–16)

Status: Git/governance checkpoint only. No application code was written, fixed, or refactored to produce this checkpoint. No production PostgreSQL or RabbitMQ was touched. No production infrastructure was changed.

## 1. Repository / Branch

- Repository root: `c:\Projects\PIOS-Foundation_v1.0`
- Current branch: `pios-product-main`
- Branch state: attached (not detached)

## 2. Remote / Upstream

- Remote: `origin` → `https://github.com/nurgaliev102-collab/PIOS-Foundation_v1.0.git` (fetch and push identical; no embedded credentials found in the URL)
- Upstream: `origin/pios-product-main`

## 3. HEAD Before Commit

```
b550ed42c31e38374acca27ac90776dc99a251f8  feat(pios): add mobile install guidance
```
Working tree before staging: **dirty** (184 changed/untracked entries).

## 4. Scope Classification

Every changed/untracked path was inspected (`git status --short`, `git diff`, `git diff --stat`, targeted content reads) and classified. The repository currently contains **two concurrent, unrelated work streams** on top of the same branch: (A) the Tasks 0–16 First Refusal / Trip backend chain this checkpoint covers, and (B) a separate, later, in-progress frontend design-system / onboarding effort (component library, `DriverTrustIndicator`, `PassengerLanding`/`RideRequest` copy changes, an unrelated `ai-advisor` Ollama/Qwen provider rollout, and local tooling setup). Category B is real, uncommitted work — it is left untouched on disk, simply not included in this commit.

**Classification method, not assumption:** every "ambiguous" file (docs whose title didn't obviously map to a named task) was resolved by reading its actual diff/content, not by its path alone. Two docs not explicitly named in the task's own Phase 6 checklist — `PIOS_PRODUCT_DECISION_GATE.md` and `PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md` — were confirmed as Category A only after verifying they are directly cited by `ADR-062` and `PIOS_TAXI_PRODUCT_DECISIONS.md` (both already-confirmed Category A).

No Category C (genuinely unresolvable) file was found. Every file classified below is backed by an inspected diff or a direct cross-reference check.

## 5. Files Staged (Category A — Tasks 0–16)

### Backend — Dispatch (Tasks 11, 12, 14, 15C, 16)
Modified: `api/AssignmentController.kt`, `api/ProposalController.kt`, `application/DispatchAssignmentApplicationService.kt`, `persistence/RabbitMQOrderManagementTopologyConfiguration.kt`, `test/api/AssignmentControllerTest.kt`, `test/application/DispatchAssignmentApplicationServiceTest.kt`, `test/persistence/AssignmentRepositoryLifecycleTest.kt`, `test/persistence/PostgreSQLAssignmentLifecycleTest.kt`, `test/persistence/PostgreSQLTestDatabase.kt`, `test/persistence/RabbitMQTestConnection.kt`.
New: `application/FirstRefusalApplicationService.kt`, `NoOpTripRepository.kt`, `PrimaryDriverClearedCommand.kt`, `PrimaryDriverDesignatedCommand.kt`, `PrimaryDriverProjectionApplicationService.kt`, `PrimaryDriverRecord.kt`, `PrimaryDriverRepository.kt`, `TripRepository.kt`; `domain/PassengerReference.kt`, `Trip.kt`, `TripArrived.kt`, `TripCompleted.kt`, `TripId.kt`, `TripStarted.kt`, `TripStatus.kt`; `persistence/InMemoryPrimaryDriverRepository.kt`, `InMemoryTripRepository.kt`, `OrderSubmittedFirstRefusalListener.kt`, `PostgreSQLPrimaryDriverRepository.kt`, `PostgreSQLTripRepository.kt`, `PrimaryConnectionEventListener.kt`, `RabbitMQPassengerExperienceTopologyConfiguration.kt`; migrations `V12__trips.sql`, `V13__primary_driver.sql`, `V14__proposals_one_open_per_order.sql`; tests `DispatchAssignmentApplicationServiceRideProgressConvergenceTest.kt`, `DispatchAssignmentApplicationServiceTripCreationTest.kt`, `FirstRefusalApplicationServiceTest.kt`, `PrimaryDriverProjectionApplicationServiceTest.kt`, `TripIdTest.kt`, `TripTest.kt`, `OrderSubmittedFirstRefusalConcurrencyTest.kt`, `OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`, `OrderSubmittedFirstRefusalTestListenerHarness.kt`, `OrderSubmittedMessagePublisher.kt`, `PrimaryConnectionConsumerIntegrationTest.kt`, `PrimaryConnectionIdempotencyIntegrationTest.kt`, `PrimaryConnectionMessagePublisher.kt`, `PrimaryConnectionTestListenerHarness.kt`, `ProposalConcurrencyTest.kt`, `TripCreationTransactionTest.kt`, `TripRepositoryContractTest.kt`.

### Backend — Order Management (Tasks 15C, 16)
Modified: `api/OrderSubmissionController.kt`, `api/SubmitOrderRequest.kt`, `application/OrderLifecycleApplicationService.kt`, `application/OrderSubmissionRequestHandler.kt`, `application/SubmitOrderCommand.kt`, `domain/Order.kt`, `domain/OrderSubmitted.kt`, `persistence/PostgreSQLOrderRepository.kt`, `test/domain/OrderTest.kt`, `test/persistence/OrderSubmittedEnvelopeTest.kt`, `test/persistence/PostgreSQLOrderRepositoryTest.kt`, `test/persistence/PostgreSQLTestDatabase.kt`, `test/persistence/RabbitMQTestConnection.kt`.
New: migration `V11__add_order_explicit_driver_intent.sql`.

### Backend — Passenger Experience (Task 14)
Modified: `build.gradle.kts`, `PassengerExperienceApplication.kt`, `application/RemoveConnectionApplicationService.kt`, `application/SetPrimaryConnectionApplicationService.kt`, `resources/application.yml`, `test/api/ConnectionControllerTest.kt`, `test/persistence/PostgreSQLTestDatabase.kt`.
New: `application/EventPublisher.kt`, `NoOpEventPublisher.kt`, `NoOpOutboxRepository.kt`, `OutboxBacklog.kt`, `OutboxRecord.kt`, `OutboxRelay.kt`, `OutboxRelayScheduler.kt`, `OutboxRepository.kt`; `domain/PrimaryConnectionCleared.kt`, `PrimaryConnectionDesignated.kt`; `persistence/PostgreSQLOutboxRepository.kt`, `RabbitMQEventPublisher.kt`, `RabbitMQProducerTopologyConfiguration.kt`; migration `V3__outbox.sql`; tests `RecordingOutboxRepository.kt`, `RemoveConnectionApplicationServiceTest.kt`, `SetPrimaryConnectionApplicationServiceTest.kt`, `RabbitMQTestConnection.kt`, `SetPrimaryConnectionTransactionTest.kt`.

### Backend — Driver Management / Identity / Network Management (Task 1, test isolation)
Modified: `driver-management/test/persistence/PostgreSQLTestDatabase.kt`, `RabbitMQTestConnection.kt`; `identity/test/persistence/PostgreSQLTestDatabase.kt`; `network-management/test/persistence/PostgreSQLTestDatabase.kt`.

### Documentation
Modified: `docs/ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md`, `docs/EVENT_CATALOG.md`, `docs/INTERFACE_CONTRACTS.md`, `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`, `docs/README.md`.
New: `PIOS_REALITY_AUDIT.md` (repo root); `docs/ADR/ADR-062-...md`, `ADR-063-...md`; `docs/PIOS_POST_REMEDIATION_BASELINE.md`, `PIOS_PRODUCT_DECISION_GATE.md`, `PIOS_TAXI_ARCHITECTURE_CONTRACT.md`, `PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md`, `PIOS_TAXI_ARCHITECTURE_RATIFICATION.md`, `PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md`, `PIOS_TAXI_PRODUCT_DECISIONS.md`, `PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md`, `PIOS_TAXI_TASK_11_TRIP_FOUNDATION_REPORT.md`, `PIOS_TAXI_TASK_12_TRIP_CONVERGENCE_REPORT.md`, `PIOS_TAXI_TASK_13_FIRST_REFUSAL_RUNTIME_AUDIT.md`, `PIOS_TAXI_TASK_14_FIRST_REFUSAL_FOUNDATION_REPORT.md`, `PIOS_TAXI_TASK_15_FIRST_REFUSAL_RUNTIME_INTEGRATION_REPORT.md`, `PIOS_TAXI_TASK_15B_FIRST_REFUSAL_RUNTIME_INTEGRATION_REPORT.md`, `PIOS_TAXI_TASK_15C_CONTRACT_AND_CONCURRENCY_REPORT.md`, `PIOS_TAXI_TASK_16_FIRST_REFUSAL_RUNTIME_INTEGRATION_REPORT.md`, `PIOS_TAXI_V1_TARGET_ARCHITECTURE.md`, `PRODUCTION_BACKUP_VERIFICATION.md`, `PRODUCTION_INCIDENT_CLEANUP_REPORT.md`, `PRODUCTION_INCIDENT_FORENSIC_REPORT.md`, `PRODUCTION_INCIDENT_REMEDIATION_PLAN.md`, `RABBITMQ_TEST_ISOLATION.md`, `TEST_DATABASE_ISOLATION.md`; this file, `docs/PIOS_TAXI_GIT_CHECKPOINT_REPORT.md`.

## 6. Files Deliberately NOT Staged

**Category B — a separate, unrelated, in-progress work stream (left untouched on disk):**
- `.claude/CLAUDE.md`, `.claude/settings.json`, `.claude/settings.json.graphify-bak`, `.claude/skills/` — local `graphify` tooling installation, not Tasks 0–16 content.
- `CLAUDE.md` (root) — the `graphify` section addition, same tooling change.
- `ai-advisor/**` (all modified + new `Ollama*`/`Qwen*` files) — a separate AI Advisor provider rollout (ADR-056), unrelated to First Refusal; confirmed by content (`DailySnapshot`/provider-selection logic references ADR-056, never ADR-062/063).
- `frontend/src/identity/BackendIdentityProvider.ts(.test.ts)` — an unrelated same-origin proxy fix, confirmed by diff content.
- `frontend/src/index.css`, `frontend/src/styles/`, `frontend/src/components/**` (Button, Card, Divider, DriverTrustIndicator, ErrorState, FormField, Heading, IconButton, Input, LoadingState, StatusMessage, Text) — a new frontend design-system, confirmed by content and by `docs/PIOS_DESIGN_SYSTEM.md`/`PIOS_DESIGN_IMPLEMENTATION_LOG.md`.
- `frontend/src/pages/DriverHome/DriverHome.tsx`, `frontend/src/pages/PassengerLanding/**`, `frontend/src/pages/RideRequest/**` — onboarding/copy/UI changes, confirmed by diff content (e.g., DriverHome.tsx: copy-only change; PassengerLanding/RideRequest: large restructures matching `DRIVER_IDENTITY_DESIGN_DECISION.md`/`PASSENGER_INVITATION_DESIGN_DECISION.md`). **Task 16's own report states frontend changed: no** — staging any of these would contradict that record.
- `frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts(.test.ts)` — confirmed by content to be `ai-advisor`-facing (`DailySnapshot` shape), not Task 16 (which explicitly touched zero Owner Control Center files).
- `docs/CLAUDE_CODE_ENVIRONMENT_AUDIT.md`, `docs/DRIVER_IDENTITY_DESIGN_DECISION.md`, `docs/PASSENGER_INVITATION_DESIGN_DECISION.md`, `docs/PIOS_DESIGN_IMPLEMENTATION_LOG.md`, `docs/PIOS_DESIGN_SYSTEM.md`, `docs/PIOS_TAXI_DESIGN_BRIEF.md`, `docs/PIOS_VISUAL_VERIFICATION_PLAN.md` — documentation for the same unrelated design-system/tooling stream.

**Category — excluded for safety/hygiene regardless of relation:**
- `logs/` (`ai-advisor.log`, rotated `.gz`) — runtime logs; explicitly excluded by this task's own Phase 3 rule.
- `graphify-out/` (904+ generated cache files, `graph.json`, `graph.html`, `GRAPH_REPORT.md`, wiki) — generated output; explicitly excluded.
- `windows-services/cloudflared/run-tunnel.ps1` — production infrastructure (Cloudflare tunnel launcher for `piosapp.ru`); reads its token from a gitignored `tunnel.token` file and contains no embedded secret, but is itself a production-infrastructure change, explicitly out of scope and a HARD STOP item (condition 5) if staged.

No file in either exclusion group was modified, deleted, or moved — they remain exactly as they were on disk.

## 7. Secret / Production-Data Safety Check

- No file under `C:\PIOS_BACKUPS\` is inside the repository or the candidate set (that path is outside the repo entirely).
- No `.env`/`.envrc` file is tracked or staged (`.gitignore` already excludes them; none present in `git status`).
- No PostgreSQL dump (`.sql` dump/backup, `.dump`, `.bak`) is among the staged files — the three staged `.sql` files (`V11`–`V14`) are Flyway forward-only schema migrations, inspected and confirmed to contain `ALTER`/`CREATE INDEX` DDL only, no data.
- `windows-services/cloudflared/run-tunnel.ps1` was read in full: it reads its tunnel token from a gitignored file at runtime and never embeds a credential in tracked content — and it is excluded from staging regardless (Section 6).
- `logs/` (real `ai-advisor` runtime log output) is excluded from staging (Section 6).
- No RabbitMQ or PostgreSQL production credential appears in any staged file; every staged test-infrastructure file (`PostgreSQLTestDatabase.kt`, `RabbitMQTestConnection.kt`) points exclusively at isolated `*_test` databases / the `pios-test` vhost, per `docs/TEST_DATABASE_ISOLATION.md` and `docs/RABBITMQ_TEST_ISOLATION.md`.

**Result: clean. No secret, credential, or production dump is present in the staged set.**

## 8. Task 16 Checkpoint Integrity Verification

Verified directly against source (not against prior reports) immediately before staging:

1. `OrderSubmitted` carries `orderId`, `origin` (serialized as `payload.passengerReference`), and `isTest` — confirmed by reading `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/OrderSubmitted.kt` directly.
2. First Refusal runtime integration exists — `OrderSubmittedFirstRefusalListener` is a real `@Component` with a `@RabbitListener`, confirmed by direct read.
3. Dispatch has the `OrderSubmitted` consumer — same file, bound to `RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME`, a dedicated queue distinct from the `OrderCancelled` queue.
4. `PrimaryDriverRecord` is used — `FirstRefusalApplicationService` (present, confirmed by file listing and Task 14/15C history) is the listener's sole collaborator for the routing decision.
5. `FirstRefusalApplicationService` is called from the new runtime path — confirmed by direct read: `firstRefusalApplicationService.attempt(order, passengerReference, isTest, explicitDriverIntentDeclared)`.
6. Proposal concurrency protection from Task 15C exists — `V14__proposals_one_open_per_order.sql` (partial unique index) and `ProposalConcurrencyTest.kt`/`OrderSubmittedFirstRefusalConcurrencyTest.kt` are present in the candidate set.
7. Trip implementation from Tasks 11–12 remains present — `domain/Trip.kt`, `TripId.kt`, `TripStatus.kt`, `TripArrived.kt`, `TripStarted.kt`, `TripCompleted.kt`, `persistence/PostgreSQLTripRepository.kt`, migration `V12__trips.sql` all present.
8. PostgreSQL test isolation remains configured — every module's `PostgreSQLTestDatabase.kt` points at a `*_test`-suffixed database (confirmed by direct diff read for `dispatch`, `driver-management`, `identity`; pattern consistent across `network-management`, `order-management`, `passenger-experience`).
9. RabbitMQ test isolation remains configured — every module's `RabbitMQTestConnection.kt` is present in the candidate set, consistent with `docs/RABBITMQ_TEST_ISOLATION.md`.
10. No frontend change was introduced by Task 16 — confirmed: zero `frontend/` files appear in Category A; the frontend changes present in the working tree (Section 6) are all independently attributed to the unrelated design-system stream.
11. No production infrastructure configuration was changed — `windows-services/cloudflared/run-tunnel.ps1` is excluded (Section 6); no `application.yml`/`application.properties` production profile was touched (the one staged `application.yml`, Passenger Experience, adds only the local RabbitMQ producer block Task 14 introduced, unchanged from that task's own already-reported scope).

No check failed. Nothing was "fixed" as part of this verification — this section only confirms what Task 16's own report already claimed.

## 9. Commit

- Hash: see Section 9 result below (filled after commit).
- Message:
```
feat(pios-taxi): complete first refusal runtime integration

Checkpoint for the completed Tasks 0-16 backend chain: PostgreSQL/RabbitMQ
test isolation, the production-incident forensic/remediation/cleanup
record, Taxi V1 target architecture and product-decision documents,
ADR-062/ADR-063 ratification, Trip as a Dispatch-owned aggregate
(Tasks 11-12), and the First Refusal foundation through runtime
integration (Tasks 13-16): PrimaryConnection projection, the
FirstRefusalApplicationService, the OrderSubmitted consumer, and the
Proposal one-open-per-order concurrency guarantee.

This does not represent completion of PIOS Taxi V1 as a product. Known,
disclosed, unresolved items carried forward unchanged by this checkpoint:
the pre-existing Proposal accept/decline/lapse caller-identity
authorization gap; no real frontend caller yet sets
explicitDriverIntent=true; the narrow redelivery-after-resolution
idempotency gap Task 16's own report discloses. None of these are
addressed by this commit.
```

## 10. Push Result

*(filled after push)*

## 11. Remote Verification

*(filled after push)*

## 12. Final Working-Tree Status

*(filled after commit/push)*

## 13. Warnings

- The repository currently mixes two unrelated work streams on one branch (Section 4). This checkpoint deliberately leaves the second stream (frontend design system / onboarding, `ai-advisor` Ollama/Qwen rollout, local tooling) uncommitted and untouched — it is real, in-progress work, not discarded, simply out of this checkpoint's authorized scope.
- `logs/` (containing real `ai-advisor` runtime output) is not `.gitignore`d at the root (`backend/logs/` is; the root-level `logs/` is not). It was excluded here manually. Recommend adding a root-level `logs/` entry to `.gitignore` in a future, explicitly-authorized task — not done here, as it would be a repository-configuration change outside this checkpoint's own scope.

## 14. No History Rewriting

Confirmed: no `git reset`, `git rebase`, `git commit --amend`, or force-push was used at any point in this checkpoint. One ordinary commit was created on top of the existing `HEAD`.

## 15. No Production Infrastructure Touched

Confirmed: no production PostgreSQL connection was opened, no production RabbitMQ connection was opened, no WinSW service was started, stopped, or restarted, and no production-facing configuration file (`windows-services/**`, `tunnel.token`, any `application-prod*.yml`) was staged or modified.
