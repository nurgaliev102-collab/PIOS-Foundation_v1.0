# ADR-040: Assignment Ride Lifecycle (Arrived / In Progress / Completed)

## Status

Accepted

## Context

Sprint "PIOS — Full Ride Lifecycle" asked for a driver to progress a ride
through Arrived → In Progress → Completed, with the passenger seeing each
change live via polling. The sprint's own text proposed adding these states
directly to `Order` (`order-management`).

That placement conflicts with what is already ratified and already coded:

- `Order.kt`'s own KDoc: *"The assigned, accepted, and ride states an order
  passes through once Dispatch allocates a driver (DOMAIN_MODEL.md Section
  12) depend on Dispatch's own capability and remain outside this scope;
  they are not modeled here."* `Order.status` today is only
  `SUBMITTED / COMPLETED / CANCELLED`.
- `DOMAIN_MODEL.md` Section 12: *"Order. Submitted, then either assigned by
  Dispatch and carried through acceptance, the ride itself, and completion,
  or cancelled..."* — the ride-progress narrative is attached to the
  Order↔Assignment relationship (Section 13: "An Order becomes connected to
  an Assignment once Dispatch allocates it to a driver"), not to `Order`
  itself.
- `Assignment.kt` already exists in `dispatch` for exactly this purpose, and
  its own KDoc already names the pattern: *"Only the states exercised by
  this task's scope are modeled"* — it stops at `CREATED`/`ACCEPTED` today
  only because nothing needed more until now.

Per this project's own rule (`.claude/CLAUDE.md`: architecture decisions
belong to the project architect; Claude does not redesign architecture
proactively), this placement conflict was surfaced to the product owner
before writing code. Decision: ride-progress state lives on **Assignment**
(`dispatch`), not `Order`. This ADR records that decision and its
consequences.

## Decision

1. `AssignmentStatus` gains three states: `ARRIVED`, `IN_PROGRESS`,
   `COMPLETED`, alongside the existing `CREATED`/`ACCEPTED`. Transitions:
   `→ ARRIVED → IN_PROGRESS → COMPLETED`, each rejecting any other current
   status (mirrors `accept()`'s own `check` pattern exactly).
2. `arrive()` accepts either `CREATED` or `ACCEPTED` as its precondition —
   not `ACCEPTED` alone. This is a deliberate, disclosed accommodation of an
   existing gap: `ProposalAssignmentOrchestrationService.acceptProposal`
   (Sprint IMPLEMENTATION-004) creates the Assignment when a driver accepts
   a Proposal, but never separately calls `Assignment.accept()` — the
   Proposal's own acceptance already is the "acceptance" DOMAIN_MODEL.md
   Section 12 describes at the Order level. Every Assignment reaching a
   driver's ride-progress buttons today is therefore `CREATED`, not
   `ACCEPTED`. Requiring `ACCEPTED` first would make `arrive()`
   unreachable through the one real path that creates an Assignment.
   Closing that gap "properly" (making Proposal-acceptance also call
   `Assignment.accept()`) is out of this ADR's scope — it touches
   `ProposalAssignmentOrchestrationService`'s own carefully-scoped shared
   transaction (ADR-036), and isn't required to satisfy this sprint's own
   acceptance criterion.
3. `Assignment` gains `statusChangedAt: Instant?` — the time of the most
   recent transition, set by `accept()`/`arrive()`/`start()`/`complete()`
   alike, nullable for the same reason `Order.createdAt` is (rows
   persisted before this change have none).
4. New REST surface on `dispatch`, not `order-management`:
   `GET /v1/assignments?orderId=...`, `POST /v1/assignments/{id}/arrive`,
   `POST /v1/assignments/{id}/start`, `POST /v1/assignments/{id}/complete`.
5. `order-management`'s own `Order.status` is **not** touched by this ADR.
   A completed ride does not (yet) mark its Order `COMPLETED` — that would
   require Dispatch → Order Management event propagation (the same kind of
   outbox/listener pattern `AssignmentAcceptedListener` already
   establishes for `AssignmentAccepted`), which is real, additional
   infrastructure work this sprint's own acceptance criterion (two phones,
   full button sequence, live polling) does not require. Tracked as
   deferred, not silently dropped.
6. No ride history screen is built. "The order stops being active" is
   satisfied at the UI layer only: a driver's own list stops offering
   actions once its Assignment reaches `COMPLETED`.

## Consequences

- `order-management` is entirely unchanged by this sprint.
- The frontend now calls a third Dispatch resource (`/v1/assignments`)
  directly, the same direct-module-call precedent `DriverHome.tsx`/
  `RideRequest.tsx` already established for Proposal and Order.
- The `CREATED`-or-`ACCEPTED` precondition on `arrive()` is a known,
  disclosed looseness, not a hidden one — revisit if a future sprint
  actually needs to distinguish "assignment made" from "driver confirmed
  the assignment" as separate, independently-observable states.
