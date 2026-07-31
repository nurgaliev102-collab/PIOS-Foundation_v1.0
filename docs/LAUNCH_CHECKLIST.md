# Launch Checklist

Status: Operational runbook, produced by Sprint VALIDATION-001 (System Launch & End-to-End Validation), corrected in Sprint 5 (Pilot Session Readiness) to match the repository's current state. Not a Product Decision, not an ADR, not an implementation plan — a practical, tick-box sequence for standing up the current repository locally and proving the Order → Proposal → Driver Acceptance → Assignment → ride lifecycle flow works. Introduces no new business rule, architecture, or technology; it only documents how to run what already exists. See [../README.md](../README.md)'s own "Running Locally" section for the narrative version of the same steps.

---

## Pre-flight

- [ ] JDK 21 installed (`java -version`)
- [ ] Node.js installed, v24-class (`node -v`)
- [ ] PostgreSQL running locally, reachable at `127.0.0.1:5432` as user `postgres` with no password
- [ ] `pios_driver_management`, `pios_order_management`, `pios_dispatch`, `pios_identity`, `pios_passenger_experience` databases created (`CREATE DATABASE ...;` — Flyway creates the schema inside each, not the database itself). `network-management`'s own database is not part of this checklist — ADR-037 keeps it isolated from the pilot flow.
- [ ] RabbitMQ running locally, reachable at `127.0.0.1:5672`, default `guest`/`guest` credentials (used by `driver-management`, `order-management`, `dispatch`; `identity` and `passenger-experience` have no RabbitMQ configuration)

## Backend

- [ ] `cd backend && ./gradlew build` completes (compiles all five pilot-flow modules; runs their test suites)
- [ ] `./gradlew :driver-management:bootRun` starts, Flyway migration completes (through `V3__add_optional_display_name.sql`), listening on `:8081`
- [ ] `./gradlew :passenger-experience:bootRun` starts, Flyway migration completes (through `V1__create_connections.sql`), listening on `:8082`
- [ ] `./gradlew :order-management:bootRun` starts, Flyway migration completes (through `V7__add_passenger_name_and_created_at.sql`), listening on `:8083`
- [ ] `./gradlew :dispatch:bootRun` starts, Flyway migration completes (through `V6__proposal_stated_price.sql`), listening on `:8084`
- [ ] `./gradlew :identity:bootRun` starts, Flyway migration completes (through `V2__add_driver_id.sql`), listening on `:8086`
- [ ] No fatal startup error in any of the five logs (a RabbitMQ connection warning/retry, if RabbitMQ isn't up yet, is expected and non-fatal for the three modules that use it — a PostgreSQL connection failure is fatal and stops startup)

## Driver onboarding

- [ ] No manual seed step is needed. A driver creates their own profile from `http://localhost:5173/` (Driver Home): an Identity is created silently through the `identity` module, the driver types only their own name, and `POST /v1/drivers` creates their Driver profile linked to that Identity (ADR-038 Identity Module Foundation, ADR-039 Identity-Driver Association). There is no `CURRENT_DRIVER_ID` constant and no manual `INSERT INTO drivers ...` step in this repository.

## Frontend

- [ ] `cd frontend && npm install`
- [ ] `npm run dev` starts, listening on `:5173`
- [ ] `http://localhost:5173/` (Driver Home) loads without a console CORS error; a fresh browser walks through the welcome screen and name prompt described above and ends up showing the new driver's own card
- [ ] `http://localhost:5173/coordinator` loads without a console CORS error, shows the (empty, at first) Orders and Drivers lists

## End-to-end scenario

Reachable from a driver's own invitation link (`http://localhost:5173/i/<driverId>`, `driverId` being the id the driver profile above was created with):

- [ ] Visit `http://localhost:5173/i/<driverId>` (Passenger Landing) → onboard with a name → "Создать первый заказ" → `/i/<driverId>/request` → submit a destination → a new Order is created (`POST /v1/orders`, real, visible afterward in Coordinator's Orders list)
- [ ] Submitting the order automatically proposes it to the inviting driver (`POST /v1/proposals`, `RideRequest.tsx`'s own `attemptProposal`) — no Coordinator action needed for this path
- [ ] On `/` (Driver Home, as the same driver), the proposal appears under "Ваши заказы" within a few seconds without reloading (polling every 3s) with **Принять**/**Отклонить** buttons
- [ ] Click **Принять** → the proposal's own status updates to `ACCEPTED` in the Driver Home UI, and an Assignment is created automatically in status `CREATED`
- [ ] Back on the passenger's own confirmed-order screen (`RideRequest.tsx`), the ride status updates within a few seconds (same 3s polling) to reflect acceptance, without a reload
- [ ] On Driver Home, click **Прибыл** (`POST /v1/assignments/{id}/arrive`) → status becomes `ARRIVED`, reflected on the passenger's screen shortly after
- [ ] Click **Начать поездку** (`POST /v1/assignments/{id}/start`) → status becomes `IN_PROGRESS`, reflected on the passenger's screen shortly after
- [ ] Click **Завершить поездку** (`POST /v1/assignments/{id}/complete`) → status becomes `COMPLETED`, reflected on the passenger's screen; the completed order also stops appearing in the driver's own list
- [ ] (Optional, exercises the Coordinator screen's own manual path instead of the invited-passenger auto-propose path above) On `/coordinator`, select an order and a driver, click **Propose** → a Proposal is created (201, status `OPEN`); **Check status** re-fetches it manually (this screen does not poll)
- [ ] No runtime error surfaced in either browser console or any of the five backend logs during the sequence above

---

## Notes on what this checklist does not cover

- **Automatic driver matching/fallback.** The coordinator's manual "Propose" and the passenger's auto-propose to their inviting driver are the only two ways a Proposal is created — there is no automatic reassignment if the inviting driver is unavailable or does not respond.
- **Docker/Docker Compose.** No `Dockerfile` or `docker-compose.yml` exists in this repository; every step above assumes natively-installed PostgreSQL, RabbitMQ, JDK, and Node.
