# ADR-080: Commitment Termination and Order Cancellation Handshake

Status: Accepted by Product Owner, 2026-09-17 (D-01).

## Context

`Order.cancel()` previously allowed a submitted order to become `CANCELLED` after a proposal had been accepted. Dispatch's `OrderCancelled` consumer withdrew only an `OPEN` proposal, leaving an accepted assignment and Trip executable. A later `AssignmentCompleted` could then increment driver business counters despite the order being cancelled.

## Decision

Dispatch owns the terminal execution fact. `Trip` may transition from `CREATED`, `ARRIVED`, or `IN_PROGRESS` to `TERMINATED`; neither `TERMINATED` nor `COMPLETED` can transition further. The corresponding `Assignment` receives `TERMINATED` in the same Dispatch transaction. An accepted `Proposal` remains `ACCEPTED` as historical evidence. No second ride-progress lifecycle is created.

Each termination records server-derived initiator (`PASSENGER`, `DRIVER`, or `SYSTEM`), server timestamp, required reason code, and optional note. Passenger codes are `PLANS_CHANGED`, `FOUND_ANOTHER_DRIVER`, `DRIVER_UNRESPONSIVE`, `CANNOT_CONTINUE`, `OTHER`; driver codes are `CANNOT_FULFILL`, `PASSENGER_UNRESPONSIVE`, `PASSENGER_NO_SHOW`, `TRIP_CONDITIONS_CHANGED`, `OTHER`; the only system code is `SYSTEM_TIMEOUT`. No automatic timeout is introduced because no post-commitment deadline is specified. The note is not a metric.

Every Dispatch command that can accept a proposal, create an assignment, advance a Trip, terminate a Trip, manually assign, or dispatch/re-dispatch an order must first take the same PostgreSQL `SELECT ... FOR UPDATE` order guard. The guard precedes dispatch-request, proposal, assignment, and Trip locks. Completion and termination therefore have a single first committer. Completion first means termination is a conflict; termination first means no `AssignmentCompleted` is emitted.

Passenger cancellation becomes a durable request in Order Management, published through its outbox. The response is pending, not a final `CANCELLED`. Dispatch resolves the request under the order guard. Without an assignment it records a cancellation tombstone and withdraws an open proposal; with an assignment it requires a passenger reason and terminates the Trip; with a completed Trip it rejects the request. Order Management records final `CANCELLED` and publishes the legacy `OrderCancelled` only after receiving Dispatch's confirmed result. `OrderCancelled` is no longer a command to stop an active Trip. Driver termination is authorized against the committed assignment driver and emits the same confirmed terminal fact. Each command uses a stable request ID; replay is idempotent, while reuse with different input is rejected.

Termination must not emit `AssignmentCompleted` or change completed rides, client rides, earnings, penalties, fees, ratings, trust, or dispatch priority. Its operational audit is distinct from business-performance metrics. The counterparty receives the terminal fact through scoped reads and an in-app notification; free text is never used for ranking or metrics.

## Compatibility and limits

This decision narrowly supersedes ADR-040's fixed Assignment state list, ADR-041's assumption that `CANCELLED` may beat a previously completed Dispatch Trip, ADR-053's use of `OrderCancelled` as the only cancellation coordination signal, and ADR-063's fixed Trip state list. Existing historical ADR text remains intact. Legacy manual assignment remains a technical path that creates a Trip without proving driver consent; it must still be safely terminable, but this ADR grants it no new business meaning. Applied Flyway migrations remain immutable; all schema changes are additive migrations.

## Verification

Unit/domain, API authorization, real PostgreSQL locking, concurrent completion/termination and acceptance/cancellation, duplicate and reordered events, crash/restart between Dispatch commit and Order Management consumption, legacy manual assignment, and normal completed-ride regression are required before D-01 can be reported complete.

## Product Owner ratification, 2026-09-17

At implementation time, the reason-code taxonomy in the Decision section above was flagged as a genuine Product Owner decision still outstanding (see `docs/ATLAS_IMPLEMENTATION_RECONCILIATION.md`, section 16), not yet a ratified product rule. The Product Owner has since explicitly confirmed the taxonomy exactly as implemented, with no changes:

- Passenger: `PLANS_CHANGED`, `FOUND_ANOTHER_DRIVER`, `DRIVER_UNRESPONSIVE`, `CANNOT_CONTINUE`, `OTHER`.
- Driver: `CANNOT_FULFILL`, `PASSENGER_UNRESPONSIVE`, `PASSENGER_NO_SHOW`, `TRIP_CONDITIONS_CHANGED`, `OTHER`.
- System: `SYSTEM_TIMEOUT` only, reserved for system-originated termination; no automatic timeout is introduced at this stage, consistent with the original Decision text.

This taxonomy is now ratified and closed. It must not be redesigned or extended without a new Product Owner decision and a superseding ADR entry.
