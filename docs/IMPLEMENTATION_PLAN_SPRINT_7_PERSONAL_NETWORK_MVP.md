# Implementation Plan: Sprint 7 — Personal Network MVP v1.0

Status: Planning only — not an authorization to implement. This document designs, at file and module granularity, the smallest software change that realizes the scenario `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` authorized (Artur → Regina → order → Artur), reusing already-existing, already-tested code wherever the current repository already supports the needed behavior. It does not re-open any exclusion that Product Decision left standing (no ranking, no scoring, no AI selection, no reputation, no payments custody), and it does not touch `Order`, `Proposal`, `Assignment`, or ADR-036 at all.

Authorized by: `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`. Grounded in: `MODULE_STRUCTURE.md` (Passenger Experience Module's already-ratified ownership of "Personal Client relationships"), `ADR-009` (Domain Isolation), the existing `DriverReference`/`OrderReference`/`PassengerReference` reference-value-object pattern (Dispatch, Passenger Experience), and a direct reading of current code (Section 1 below).

---

## Method: Evidence Grading

Same convention as every preceding Product Decision / Implementation Plan in this repository: **[RATIFIED]** (observed directly in committed code or an approved document), **[DERIVED]** (a plan-level choice reasoning from ratified material), **[OPEN]** (a real fork this document does not resolve itself — flagged for a decision before code is written).

## Part 1 — Current State (Audit)

**[RATIFIED, verified directly against `backend/` and `frontend/src/` on 2026-07-25]**

- `driver-management`: `Driver` has only `id: DriverId` and `availability: Availability` — **no name or display field of any kind exists**.
- `order-management`: `Order` has origin, destination, status — no concept of "referred by," "connection," or any driver reference at all.
- `dispatch`: `Proposal`/`Assignment` accept any externally-supplied `DriverReference`/`OrderReference`; `ProposalController.createProposal` (`POST /v1/proposals`) performs no existence check on either (unchanged since ADR_MIGRATION_STRATEGY_ARCHITECTURE_IMPACT_ANALYSIS.md's own Central Finding). This is the same finding `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` already relied on for the manual pilot — it is what makes this plan small.
- `passenger-experience`: **no database of any kind**. `build.gradle.kts` has no `spring-boot-starter-data-jpa`, no `postgresql`, no `flyway-core` dependency; `src/main/resources` contains only `application.yml`. The module is today a pure, stateless REST relay (`RestClientOrderSubmissionClient` → Order Management's Submit Order). Adding any persisted concept here is a first for this module, not an incremental addition.
- Frontend already has a mocked version of exactly this scenario's shape, and it is worth being precise about how far it actually goes:
  - `frontend/src/pages/PassengerLanding/PassengerLanding.tsx`, route `/i/:driverCode` — resolves the driver's display name from `invitationSource.ts`'s `MOCK_INVITATIONS` **hardcoded dictionary** (one entry: `ILDAR001`). Not a real API call.
  - `frontend/src/pages/DriverHome/DriverHome.tsx` already builds `linkTo={`/i/${driver.id}`}` — i.e., the driver's own real `DriverId` is already used as the invitation code today. No separate "invitation code" concept needs to be invented.
  - `frontend/src/persistence/localPassengerIdentity.ts` — passenger identity is `localStorage`-only, never sent to any backend as its own record. `RideRequest.tsx` sends it only as a free-text `passengerReference` string on `POST /v1/orders`.
  - **`RideRequest.tsx`'s submit call drops `driverCode` entirely** — the request body is `{ passengerReference, destination }` only. This is the single concrete gap: today, nothing anywhere connects "this passenger came through Artur's link" to "this order should reach Artur." Closing exactly this gap is Sprint 7's real scope.

## Part 2 — Architecture Choice: Where Does "Connection" Live?

**[DERIVED]** — the founder's Sprint 7 sketch proposed a unifying `Person`/`Role` core spanning Driver and Passenger. That specific shape is not used here, and the reason is a real, already-ratified boundary, not a stylistic preference:

- `MODULE_STRUCTURE.md`, Section 2 (Domain Alignment): "no module spans more than one domain."
- `MODULE_STRUCTURE.md`, Passenger Experience Module: already lists "Personal Client relationships" as an owned responsibility.
- `ADR-009` (Domain Isolation): unchanged, still governs.

A `Person` entity merging Driver and Passenger would span two already-separately-owned modules (`driver-management`, `passenger-experience`), which Domain Alignment forbids without first superseding ADR-009/Module Structure — a materially bigger, riskier change than this scenario needs. Instead, this plan puts a `Connection` aggregate inside **Passenger Experience** (its ownership is already ratified, not new), referencing the driver only by a plain `DriverReference`-style value (mirroring exactly the pattern Dispatch already uses for `Proposal`/`Assignment` — a reference, never owned data, never a foreign key enforced across modules per ADR-005). Driver Management and Order Management keep exactly the shape they have today, plus one small field (Part 3).

**This is the one real architecture fork worth flagging explicitly [OPEN]:** if a future sprint wants driver-invites-driver, corporate-customer relationships, or any actor pairing beyond "a driver's existing passenger," the reference-pair shape here will need revisiting — deliberately not solved now (YAGNI), consistent with only building what Artur → Regina requires.

## Part 3 — Implementation Design

### 1. Driver Management: add a display name

`Driver` currently has no name at all — the invitation screen cannot show "Приглашены Артуром" for a real driver without one.

- New field on `Driver`: `displayName: String` (domain), added the same way `destination` was added to `Order` in Sprint 3B — a plain nullable/optional field, no new Value Object, no event change.
- Migration: `ALTER TABLE drivers ADD COLUMN display_name TEXT NULL;`
- `CreateDriverRequest`/`CreateDriverCommand`/`DriverResponse` gain `displayName: String? = null`, mirroring `SubmitOrderRequest`'s own optional-field precedent exactly.
- `PostgreSQLDriverRepository`'s reflection constructor lookup extends the same way `PostgreSQLOrderRepository`'s did for `destination` (Sprint 3B).

### 2. Passenger Experience: first-ever database, `Connection` aggregate

This module has never had persistence before — this is the first time it needs one.

- New Gradle dependencies: `spring-boot-starter-data-jpa` (or a plain JDBC template, matching whichever the other three modules actually use — confirm against `driver-management/build.gradle.kts` before choosing, do not assume), `org.postgresql:postgresql`, `flyway-core`.
- New `application.yml` datasource block + a new `pios_passenger_experience` database, mirroring `driver-management`'s/`order-management`'s own `application.yml` shape exactly.
- New migration: `V1__create_connections.sql`:
  ```sql
  CREATE TABLE connections (
      id TEXT PRIMARY KEY,
      driver_id TEXT NOT NULL,
      passenger_reference TEXT NOT NULL,
      created_at TIMESTAMP NOT NULL,
      UNIQUE (driver_id, passenger_reference)
  );
  ```
  The `UNIQUE` constraint is the same "upsert risk" caution Sprint 3A already established for `Driver` creation — inviting the same passenger twice must not create duplicate rows.
- New domain: `Connection` (id, `driverId: String`, `passengerReference: PassengerReference` — reuses the existing Value Object unchanged, `createdAt`).
- New application service: `CreateConnectionApplicationService` — existence check before insert (same pattern as `CreateDriverApplicationService`, Sprint 3A), returns the existing `Connection` rather than erroring if one already exists (idempotent — a passenger re-opening the same invitation link should not fail).
- New endpoint: `POST /v1/connections` — body `{ driverId, passengerReference }`, 201 on create, 200 on already-exists (not 409 — this call is expected to happen more than once per passenger, unlike driver creation).
- New endpoint: `GET /v1/connections?driverId=...` — lets a Coordinator (or future Driver Home screen) see who is connected to a given driver. Not required for the Artur→Regina path itself; included because it is the natural, already-established query-side counterpart (same shape as `GET /v1/proposals?driverId=`) and costs little once the write side exists.

### 3. Order Management, Dispatch: no backend change

Neither module needs new code. `Order` stays exactly as Sprint 3B left it — deliberately not extended with a "referred by" field, keeping this plan out of `Order`'s aggregate and away from ADR-036's Proposal↔Assignment transaction entirely. `Dispatch`'s `POST /v1/proposals` already accepts any caller-supplied `driverId`/`orderId` pair (Part 1's Central Finding) — the only change is *who calls it and when* (Part 4).

### 4. Frontend wiring (the actual new behavior)

- `PassengerLanding/invitationSource.ts`: replace `MOCK_INVITATIONS` with a real call to Driver Management's already-existing `GET /v1/drivers/:driverId`, using `driverCode` directly as the `driverId` (already true today per `DriverHome.tsx`'s own `linkTo`). Reads the new `displayName` field for the "You were invited by" screen.
- `PassengerLanding.tsx`, `handleNameSubmit`: after saving the local identity, call the new `POST /v1/connections` with `{ driverId: driverCode, passengerReference: identity.id }`. This is the "PIOS сохранил связь" moment from the founder's own scenario.
- `RideRequest.tsx`, `handleSubmit`: immediately after `POST /v1/orders` succeeds, call the already-existing Dispatch `POST /v1/proposals` with `{ orderId: response.orderId, driverId: driverCode }`. This is "PIOS предлагает Артуру" — no Coordinator step for this path. Errors from this second call should not block order confirmation (the order already exists); surfaced as a known limitation if it fails, not a hard failure — matching this repo's existing tolerance pattern (`RideRequest.tsx`'s own `notes` field is already a disclosed, accepted gap).
- `DriverHome.tsx`: **no change**. The auto-created Proposal appears through the exact same `GET /v1/proposals?driverId=` call already wired since Sprint IMPLEMENTATION-005; Accept/Decline already work.
- `Coordinator.tsx`: **no change, and not removed**. It remains the path for any order that arrives without a connection (the general queue), consistent with the Product Decision's narrow scope — this plan bypasses Coordinator only for the referred-order path, it does not delete Coordinator.

## Part 4 — What This Plan Deliberately Does Not Build

**[RATIFIED, reused directly from `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 3 and `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`'s own narrow-scope list]** — unchanged, none of this is opened by this plan:

- No ranking, scoring, or AI/ML candidate selection.
- No reputation or reciprocity engine.
- No PIOS payment custody or commission engine.
- No city-scale or multi-city graph, no geospatial data.
- No unified `Person`/`Role` aggregate (Part 2).
- No change to `Order`, `Proposal`, `Assignment`, or ADR-036.
- No Fair Opportunity Policy for the general-queue path — Coordinator's existing manual behavior is untouched for orders with no connection.

## Part 5 — Open Questions Requiring a Decision Before Code

**[OPEN]** — each is a real fork, not decided by this document:

1. Whether Passenger Experience's new persistence layer uses Spring Data JPA or plain JDBC/reflection-constructed aggregates (the pattern `driver-management`/`order-management`/`dispatch` already use) — should match existing modules for consistency; confirm which those actually use before scaffolding (this plan assumes reflection-based, unmanaged aggregates, matching Order/Driver, but this must be verified, not assumed).
2. Whether `displayName` is required at driver creation going forward, or stays optional indefinitely for already-created drivers (backfill question) — affects `CreateDriverRequest` validation.
3. Whether a lightweight ADR should record the `Connection` aggregate's shape and its "reference-only, no cross-module foreign key" rule, or whether Module Structure's existing ratification is sufficient precedent (as it was for `Order.destination` and `Driver.availability`, neither of which got a dedicated ADR). This plan's own recommendation: no new ADR is strictly required, since no module boundary is crossed or changed — but this is the Product Owner's call, not this document's.
4. Whether `RideRequest.tsx`'s auto-proposal call should be synchronous (as designed above, matching this repo's existing "frontend calls each module directly" precedent) or should instead go through a backend-to-backend event (`OrderSubmitted` extended, Dispatch subscribes) — the synchronous frontend call is recommended here because it requires zero new event-catalog entries and zero new cross-module coupling in the backend, but it does mean the auto-proposal can silently fail if the passenger's browser loses connectivity between the two calls; this tradeoff should be confirmed, not assumed.

## Part 6 — File Impact Summary

| Module | New files | Modified files |
| --- | --- | --- |
| `driver-management` | 1 migration | `Driver.kt`, `CreateDriverCommand.kt`, `CreateDriverRequest.kt`, `DriverResponse.kt`, `DriverController.kt`, `PostgreSQLDriverRepository.kt`, plus tests |
| `passenger-experience` | `Connection.kt`, `ConnectionRepository.kt` (+ in-memory and PostgreSQL implementations), `CreateConnectionCommand.kt`, `CreateConnectionApplicationService.kt`, `ConnectionController.kt`, `CreateConnectionRequest.kt`, `ConnectionResponse.kt`, 1 migration, `application.yml` datasource block, Gradle dependency additions | none |
| `order-management`, `dispatch` | none | none |
| `frontend` | none | `invitationSource.ts`, `PassengerLanding.tsx`, `RideRequest.tsx` |

## Part 7 — Test Plan

Same discipline as every prior sprint in this repository: unit tests for `CreateConnectionApplicationService` (create, idempotent-already-exists, blank input), a PostgreSQL integration test for `Connection` persistence (mirroring `PostgreSQLCreateDriverTest.kt`'s own shape), a `DriverControllerTest`/`ConnectionControllerTest` pair for the new/changed endpoints, and a frontend `tsc -b` + `oxlint` + `vite build` pass plus a manual dry run of the full Artur→Regina scenario against freshly rebuilt services (same method Sprint 6 already used and already proved catches real drift).

## Files Changed (This Planning Task)

`docs/IMPLEMENTATION_PLAN_SPRINT_7_PERSONAL_NETWORK_MVP.md` (new). `docs/README.md` receives one minimal traceability pointer. No code, migration, API, or event contract is created or modified by this planning task itself — only by whatever implementation task is explicitly commissioned next, once Part 5's open questions are answered.
