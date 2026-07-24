# Launch Checklist

Status: Operational runbook, produced by Sprint VALIDATION-001 (System Launch & End-to-End Validation). Not a Product Decision, not an ADR, not an implementation plan — a practical, tick-box sequence for standing up the current repository locally and proving the Coordinator → Proposal → Driver Acceptance → Assignment flow works. Introduces no new business rule, architecture, or technology; it only documents how to run what already exists. See [../README.md](../README.md)'s own "Running Locally" section for the narrative version of the same steps, and this session's own validation report for the evidence behind each item.

---

## Pre-flight

- [ ] JDK 21 installed (`java -version`)
- [ ] Node.js installed, v24-class (`node -v`)
- [ ] PostgreSQL running locally, reachable at `127.0.0.1:5432` as user `postgres` with no password
- [ ] `pios_driver_management`, `pios_order_management`, `pios_dispatch` databases created (`CREATE DATABASE ...;` — Flyway creates the schema inside each, not the database itself)
- [ ] RabbitMQ running locally, reachable at `127.0.0.1:5672`, default `guest`/`guest` credentials

## Backend

- [ ] `cd backend && ./gradlew build` completes (compiles all four modules; runs their test suites)
- [ ] `./gradlew :driver-management:bootRun` starts, Flyway migration completes, listening on `:8081`
- [ ] `./gradlew :order-management:bootRun` starts, Flyway migration completes, listening on `:8083`
- [ ] `./gradlew :dispatch:bootRun` starts, Flyway migration completes, listening on `:8084`
- [ ] No fatal startup error in any of the three logs (a RabbitMQ connection warning/retry, if RabbitMQ isn't up yet, is expected and non-fatal — a PostgreSQL connection failure is fatal and stops startup)

## Seed data

- [ ] `INSERT INTO drivers (id, availability) VALUES ('ILDAR001', 'AVAILABLE');` run against `pios_driver_management` (no REST endpoint creates a Driver — see the Repository Audit's own Defect D2)

## Frontend

- [ ] `cd frontend && npm install`
- [ ] `npm run dev` starts, listening on `:5173`
- [ ] `http://localhost:5173/` (Driver Home) loads without a console CORS error, shows driver `ILDAR001`'s card
- [ ] `http://localhost:5173/coordinator` loads without a console CORS error, shows the (empty, at first) Orders and Drivers lists

## End-to-end scenario

- [ ] Visit `http://localhost:5173/i/ILDAR001` (Passenger Landing) → "Request a Ride" → submit → a new Order is created (`POST /v1/orders`, real, visible afterward in Coordinator's Orders list)
- [ ] On `/coordinator`, the new order and driver `ILDAR001` both appear
- [ ] Select the order and the driver, click **Propose** → a Proposal is created (201, status `OPEN`), shown in the result panel
- [ ] On `/` (Driver Home, as `ILDAR001`), the same proposal appears under "Proposals" with **Accept**/**Decline** buttons
- [ ] Click **Accept** → the proposal's own status updates to `ACCEPTED` in the Driver Home UI
- [ ] Back on `/coordinator`, click **Check status** on the last proposal → status now shows `ACCEPTED`
- [ ] (Optional, requires direct API access) `GET http://localhost:8084/v1/proposals/{proposalId}` and cross-checking Dispatch's own `assignments` table/`GET` path confirms an `Assignment` was created automatically for the same order/driver, in status `CREATED`
- [ ] No runtime error surfaced in either browser console or any of the three backend logs during the sequence above

---

## Notes on what this checklist does not cover

- **Assignment lifecycle beyond `CREATED`.** No further state (ride started, completed, cancelled) is modeled anywhere in Dispatch today — `AssignmentStatus` has only `CREATED`/`ACCEPTED`. "Assignment lifecycle continues" stops here by design, not by omission.
- **Driver registration as a real product capability.** The manual `INSERT` above is a pilot-only workaround, not a substitute for a designed, ratified Register Driver capability — tracked as a named gap, not resolved by this checklist.
- **Docker/Docker Compose.** Not created this sprint — no `Dockerfile` or `docker-compose.yml` exists in this repository; every step above assumes natively-installed PostgreSQL, RabbitMQ, JDK, and Node.
