# Final Product Readiness Closure — D/E Findings

**Date:** 2026-09-23
**Scope:** only D-1–D-4 and E-1–E-6 from the Final Product Readiness Audit.
**Result:** `READINESS: READY FOR LAUNCH PREPARATION`.

This is not authorization to deploy, restart services, apply production
migrations, or change production configuration. C-1, C-2, and C-3 remain
closed; this pass found no regression in them.

## Final readiness matrix

| ID | Current State | Classification | Blocking? | Action | Evidence |
|---|---|---|---|---|---|
| D-1 | Calendar previously selected `Assignment.driver`; after a consented Handoff that is the immutable committer, not the current executor. | FIX_REQUIRED | No — fixed | Calendar now selects Trips by `Trip.executingDriver`; committer and executor remain separately visible in the response. | `TripRepository.findByExecutingDriver`; `AssignmentControllerCalendarTest` Handoff case; ADR-084 append-only clarification. |
| D-2 | Calendar previously filtered raw `Assignment.status`, although ride progress has converged on `Trip.status`; a completed Trip could remain visible because its Assignment stayed `CREATED`. | FIX_REQUIRED | No — fixed | Live calendar membership now uses authoritative Trip status and excludes `COMPLETED`/`TERMINATED`. | `AssignmentController.driverCalendar`; regression proving completed Trip + CREATED Assignment is absent. |
| D-3 | Runtime was safe when VAPID was absent, but the WinSW deployment path did not inject all three VAPID values and the generation procedure was undocumented. | DOCUMENTATION_ONLY | No | Added public/private/subject placeholders to the Dispatch template, required-name validation to the generator, and generation/rotation guidance. No key material was added. | `pios-dispatch.xml.template`; `generate-service-xml.ps1`; `PIOS_DEPLOYMENT_SECRETS.md`; PowerShell AST/placeholder validation PASS. |
| D-4 | Audit concern is stale: Core integration tests already require explicit `pios.core.qa.postgres.*` / `PIOS_CORE_QA_POSTGRES_*` inputs and a fail-closed safety gate rejects missing, production-like, or privileged endpoints. | ACCEPTED_NON_BLOCKING | No | No code/config change. Environment-specific credentials intentionally remain outside source. | `CoreQaEndpoints`, `CoreQaSafetyGate`, `PostgreSQLTestDatabase`, and their safety-gate tests. Core `application.yml` was untouched by this task. |
| E-1 | Recovery invalidates old tokens immediately on Identity endpoints; stateless verifiers in five other modules accept an old token until its existing `exp`. | ACCEPTED_NON_BLOCKING | No | No redesign. This is the explicit ratified boundary of ADR-082/ADR-055, not an undisclosed implementation gap. | ADR-082 Parts 3/8 and test matrix; Identity 163 tests pass. |
| E-2 | D-07 Option C was violated: the single executor-valued `driverId` credited both execution and relationship facts to the substitute. | FIX_REQUIRED | No — fixed | Version-1 `AssignmentCompleted` now preserves `driverId` as committer and adds optional `executingDriverId`. Ride count/earnings use executor; client/repeat facts use committer. Old messages fall back to `driverId`. | Dispatch payload test, listener compatibility tests, Driver Management split-attribution test; D-07/ADR-081 append-only records. |
| E-3 | Secret documentation covered the session secret but not the completed product's SMS, OTP relay, VAPID, database, broker, owner, and AI-provider inputs. | DOCUMENTATION_ONLY | No | Replaced the partial inventory with names, purpose, scope, required/optional status, injection location, and fail-closed/preflight behavior. | `PIOS_DEPLOYMENT_SECRETS.md`; no real value or fingerprint recorded. |
| E-4 | Coordinator rendered the accepted Proposal's original driver under the ambiguous label “Driver” after Handoff. | FIX_REQUIRED | No — fixed | For accepted orders Coordinator reads the existing assignment API. Normal rides show the current driver; Handoff rides explicitly show both “Accepted by” and “Executing”. Owner event-feed execution facts also use `executingDriverId`. | `Coordinator.test.tsx` Handoff regression; full frontend 430/430. |
| E-5 | ADR-083 still said “no code has been written” after D-10 implementation. | DOCUMENTATION_ONLY | No | Added an append-only implementation record and VAPID deployment pointer; historical ratification text remains intact. | ADR-083 Status addendum. |
| E-6 | ADR-084 still said “no code has been written” and did not record how D-07 affects calendar ownership/status. | DOCUMENTATION_ONLY | No | Added an append-only implementation/D-07 clarification; historical text remains intact. | ADR-084 Status addendum. |

## Implementation boundary

- No new calendar state machine, aggregate, table, event version, or
  cross-module call was introduced.
- The additive `executingDriverId` event field keeps `eventVersion = 1` and
  old-envelope compatibility.
- No VAPID private material is present in frontend source or any API response.
- No C-2 provider/runtime behavior and no C-3 Identity↔Person code changed.
- No Product Owner decision remains necessary for these ten findings.

## Verification

- Dispatch focused changed-contract suite: **47/47 passed**, including the
  PostgreSQL Trip repository contract.
- Dispatch full suite: **864/867 passed**. The three failures are the existing
  shared-QA scheduler/broker isolation failures
  (`OutboxRelaySchedulerTest`, `OutboxRelayTest`,
  `ProposalLapseSchedulerTest`); isolated retry reproduced shared pending
  outbox/queue interference. None exercises a changed code path. This is test
  harness hygiene, not a launch correctness/security blocker; all 47 affected
  tests pass.
- Driver Management: **264/264 passed**, including the real
  PostgreSQL/RabbitMQ split-attribution path.
- Identity (C-2 regression, forced rerun): **162 passed, 1 skipped, 0 failed**.
- Network Management (C-3 regression, forced rerun): **48/48 passed**.
- Frontend: **430/430 passed**, including Web Push notification-click
  deep-link coverage and Coordinator Handoff coverage.
- Backend build: `:dispatch:build :driver-management:build -x test` — PASS.
- Frontend `tsc -b` + production Vite/PWA build — PASS.
- `git diff --check` — PASS (line-ending notices only).
- `generate-service-xml.ps1` AST parse and Dispatch VAPID placeholder check —
  PASS.

## Operational acceptance

The ADR-082 cross-module token-expiry window and explicit Core QA credential
contract are accepted non-blocking boundaries. Production deployment preflight
must still provision the secret inventory and reject local/default database or
RabbitMQ credentials. That work is launch preparation, not a remaining product
security/correctness defect.

No production system, service, database, DNS/domain, cloudflared file, or core
runtime configuration was changed by this task. Changes remain unstaged; no
commit and no push were performed.
