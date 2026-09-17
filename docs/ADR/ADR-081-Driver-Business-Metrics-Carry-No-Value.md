# ADR-081: Driver Business Metrics Carry No Value

## Status

**Ratified by the Product Owner, 2026-09-17 (D-02).** Documentation/verification-only — no code, schema, migration, or API change is authorized or required by this decision.

## Context

`completedRidesCount`, `repeatClientsCount`, `totalStatedEarnings`, `driver_client_rides`, and `invitedDriversCount` are five self-reported, derived read-model figures Driver Management computes from Dispatch's and Order Management's own event stream (`AssignmentCompleted`, `OrderSubmitted`), for a driver's own private "Мой бизнес" screen.

The restriction this ADR ratifies was not invented here. It already existed, piecemeal, in three places:

- `ADR-065` (Driver Earnings) Decision 1's own narrow amendment boundary: aggregation permitted "only within one driver's own completed history, for that driver's own eyes," and its own "What This ADR Does Not Authorize" list forbidding any use in candidate selection, Assignment Policy, First Refusal, or dispatch, and any display to a passenger or another driver.
- `ADR-073` (Driver-to-Driver Referral) Part 5 clause 3: "Nothing of value may attach to this fact... without a new Product Decision," and clause 4: "It is never an input to Dispatch."
- `docs/PIOS_AI_HANDOFF.md`'s own KNOWN RISKS note, already labeling this "HIGH conditional fraud risk, D-02" before this ADR existed.

D-02 generalizes these three narrow, per-ADR restrictions into one explicit Product Owner decision covering all five metrics together, so the boundary is checkable in review rather than remembered piecemeal across three documents.

### Reconciliation performed before ratification

Per the Product Owner's D-02 Continuation/Reconciliation mission (2026-09-17), a full-codebase reconciliation was performed before this decision was ratified: every creation site, storage table, display surface, and use site for all five metrics was read directly (not inferred from documentation), and `dispatch`, `order-management`, `passenger-experience`, and `billing` were exhaustively searched for the exact metric names and their semantic equivalents. The reconciliation report — reproduced in full in this conversation and treated as the evidentiary basis for this ADR — found:

- All five metrics are created exclusively in `driver-management`, from consuming `AssignmentCompleted`/`OrderSubmitted`.
- All five are stored exclusively in `driver-management`'s own database (`driver_milestones`, `driver_client_rides`, `driver_ride_stated_prices`), each migration's own header comment already stating these are "derived, read-model state — never the source of truth."
- All five are displayed exclusively on `Bearer`/self-only-gated endpoints (`GET /v1/drivers/{driverId}/milestones`, `GET /v1/drivers/{driverId}/clients`) and exclusively on the driver's own private `DriverHome.tsx` screen.
- Zero references to any of the five, or their semantic equivalents, exist anywhere in `dispatch`, `order-management`, `passenger-experience`, or `billing` source (confirmed by exhaustive grep — the only dispatch hits for "milestone" are unrelated "Sprint Milestone 14A/14B" comments).
- `billing`'s `Subscription` aggregate carries no field, event, or query touching any of the five.
- No conditional branch, eligibility check, dispatch candidate filter, billing computation, trust/rating field, or ranking/sort key anywhere in the codebase reads any of the five.
- `DriverTrustIndicator.tsx`, the one shared component that renders anything about a driver to a passenger, is explicitly designed to render "never a rating, count, rank, or any fabricated trust score" — confirmed by direct reading, not assumed from its name.

**Conclusion of the reconciliation, ratified by this ADR: no violation of D-02 exists anywhere in the codebase today.** The architecture already enforces the boundary this decision ratifies, by construction (module isolation, self-only gating, the existing forbidden-inputs lists in ADR-034/ADR-062/ADR-065/ADR-073/ADR-074) — not merely by discipline that could later be forgotten.

## Decision

The following are ratified as **private, self-reported business metrics, for that driver's own use**:

- `completedRidesCount`
- `repeatClientsCount`
- `totalStatedEarnings`
- `driver_client_rides`
- `invitedDriversCount`

They are **not**, and may never become, without a new Product Owner decision that explicitly supersedes this one:

- A source of truth for anything beyond themselves.
- A basis for payment, tariff calculation, or subscription price.
- Dispatch priority or queue priority.
- Driver tier or eligibility.
- Ranking of any kind.
- Trust score or rating.
- Penalty or fee.
- A passenger-facing verified fact.

**This decision changes nothing about how these metrics are currently computed, stored, exposed, or displayed.** No migration, schema change, API change, or frontend change is authorized or required — the reconciliation above found the existing implementation already compliant in full.

## What this ADR does not decide

- **The anti-fraud question** — that these metrics are fabricable by a party submitting synthetic order/completion traffic — is explicitly **not** part of D-02 and is **not** resolved here. It remains an open, separate question, tracked in `PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` §12 (finding S-1) and `PIOS_AI_HANDOFF.md`'s own KNOWN RISKS section.
- **Whether `invitedDriversCount` should exclude `isTest` invitees** remains exactly as open as `ADR-073`'s own Consequences section already left it — a separate ruling for that ADR, not decided or touched by this one.
- **No historical metric is deleted or re-modeled.** This decision restricts the *role* these figures may play, not their existence, their current shape, or their retention.

## Consequences

- The boundary already enforced by ADR-034, ADR-062, ADR-065, and ADR-073 individually is now also ratified as one explicit, general Product Owner decision, reducing the chance a future commercial or dispatch feature attaches value to one of these metrics without recognizing it needs its own superseding decision.
- Zero implementation cost: the reconciliation this ADR relies on found full compliance already in place.
- Any future proposal to use one of these five metrics for money, tier, priority, eligibility, ranking, trust, rating, penalty, fee, or passenger-facing display must cite this ADR and obtain an explicit superseding Product Owner decision — it may not be introduced as an implementation detail of an unrelated change.

## What This ADR Does Not Authorize

Any change to how `completedRidesCount`, `repeatClientsCount`, `totalStatedEarnings`, `driver_client_rides`, or `invitedDriversCount` are computed, stored, exposed, or displayed; any new migration; any new API field or endpoint; any frontend change; any resolution of the fabricable-metrics/anti-fraud question; any resolution of the `invitedDriversCount`/`isTest` question left open by `ADR-073`; any deletion or re-modeling of historical metric data; any use of these metrics as a passenger-facing fact, a dispatch/queue priority input, a driver tier or eligibility input, a trust or rating input, or a monetary/tariff/subscription/fee/penalty basis, without a new decision that explicitly supersedes this one.

## Related ADRs

- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — the forbidden-inputs list these metrics remain excluded from.
- [ADR-062: Primary Driver / First Refusal](ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) — the precedent that a driver-relationship fact may enter Dispatch only as a narrow, ratified, binary projection, never a score.
- [ADR-065: Driver Earnings from Self-Stated Prices](ADR-065-Driver-Earnings-From-Self-Stated-Prices.md) — the original narrow amendment this ADR generalizes for `totalStatedEarnings`.
- [ADR-073: Driver-to-Driver Referral](ADR-073-Driver-To-Driver-Referral-Single-Hop-Origin-Fact.md) — Part 5 clause 3/4, the original narrow restriction this ADR generalizes for `invitedDriversCount`, and the still-open `isTest` question this ADR does not touch.
- [ADR-074: Subscription / Billing Foundation](ADR-074-Subscription-Billing-Bounded-Context-Foundation.md) — confirms `billing` has no wiring to any of these metrics today.
- [ADR-080: Commitment Termination and Order Cancellation Handshake](ADR-080-Commitment-Termination-and-Order-Cancellation-Handshake.md) — D-01, ratified and implemented immediately before this decision; unmodified by this ADR.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — supersession discipline for any future change to this decision.

## References

- `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` §17, Decision D-02 — the original recommendation this ADR ratifies.
- `docs/PIOS_AI_HANDOFF.md` — KNOWN RISKS, "HIGH conditional fraud risk, D-02."
- The D-02 reconciliation report produced 2026-09-17 (this conversation) — the evidentiary basis for this ADR's Context and Decision sections; read, not modified, by this document.
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/DriverMilestones.kt`, `domain/DriverClientRecord.kt`, `application/AssignmentCompletedApplicationService.kt`, `api/DriverController.kt`, `api/DriverMilestonesResponse.kt`, `api/DriverClientResponse.kt` — read, not modified.
- `backend/driver-management/src/main/resources/db/migration/drivermanagement/V6__driver_milestones.sql`, `V7__driver_milestones_repeat_clients.sql`, `V8__driver_earnings.sql`, `V14__driver_client_rides_last_ride_at.sql` — read, not modified.
- `frontend/src/components/DriverTrustIndicator/DriverTrustIndicator.tsx`, `frontend/src/pages/DriverHome/DriverHome.tsx` — read, not modified.
