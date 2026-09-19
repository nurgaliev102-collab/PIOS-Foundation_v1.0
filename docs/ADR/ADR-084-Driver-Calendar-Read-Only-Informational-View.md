# ADR-084: Driver Calendar — Read-Only Informational View (D-11.B)

## Status

**Accepted, 2026-09-19, by the Product Owner.** Architecture-only ratification — **no code has been written for this ADR.** Implementation may begin only after the process gates in Part 6 exist.

**Proposed Date:** 2026-09-19. **Ratified Date:** 2026-09-19. **Author:** Architect role.

### The Product Owner decision this ADR sits under

D-11.B is **GO**, narrowly, for a **read-only, informational** calendar only:

- A driver can view their own already-accepted future rides.
- The system can show a non-blocking, purely informational overlap warning.

**Explicitly out of scope, stated here as a hard boundary this ADR does not authorize and no implementation may cross:** the feature must not block a ride; reject a ride; auto-decline anything; create a reservation; create a hold; reserve driver capacity; change Dispatch eligibility; change Dispatch priority; change ranking; change trust; affect pricing; synchronize with any external calendar; expose a driver's schedule to any passenger; or create a paywall/monetization rule of any kind.

### The overlap threshold is explicitly OPEN — this ADR does not decide it

**No number is chosen here, and none may be inferred from this ADR.** "Overlap" or "close together" is not defined by any minute value in this document. Per `ADR-002` and `CLAUDE.md` ("Never Invent Business Rules"), a specific threshold (e.g. "warn when two pickups are within N minutes of each other") is a business rule that only the Product Owner may set, in a separate, explicit, dated decision. **Until that decision exists, the only authorized behavior is displaying the driver's own accepted future pickup times adjacently, with no computed warning of any kind.** A future dated amendment to this ADR (or a narrow follow-up ADR) may introduce a computed proximity warning once the Product Owner supplies the threshold — this ADR reserves the shape for that (Part 3) without building it.

---

## Context

### What already exists, verified

`dispatch_requests.requested_pickup_at` (migration `V19__dispatch_requests.sql`) already stores the future pickup time locally in Dispatch's own database, written and read by `PostgreSQLDispatchRequestRepository`. The value originates from `Order.requestedPickupAt` (`ADR-058`, Scheduled Pickup Time) and already travels to Dispatch on the `OrderSubmitted` event (`ADR-076` Decision 1). **No new event, no new event version, and no cross-module call is required to obtain this fact — it is already local.**

`Assignment` itself carries only actual-event timestamps (`statusChangedAt`, `arrivedAt`, `startedAt`, `completedAt`) — no planned/future time. `AssignmentResponse` does not currently expose `requestedPickupAt`.

There is no driver-scoped assignment query today. `GET /v1/assignments` (`AssignmentController.kt`) accepts only `orderId`/`orderIds` and post-filters by the caller's own driver/passenger claim — a driver cannot ask "what are all my assignments" without already knowing every relevant order id. The scheduled pickup time is already rendered today, but only one order at a time, on the proposal/assignment card (`DriverHome.tsx`) — the fact is not hidden, only un-aggregated.

No calendar, reservation, overlap-detection, booking, or external-calendar-sync code exists anywhere in this repository today (verified by repository-wide search).

### Why `ADR-076` needed an amendment pointer, not a supersession

`ADR-076:117` ("What this ADR does not authorize") named "any driver calendar, hold, reservation or conflict check" — a non-authorization clause, not a prohibition. `ADR-076` was never the governing authority for whether a calendar could exist; it only correctly recorded that it did not itself authorize one. This ADR is that separate authorization. `ADR-076:107`'s factual claim ("PIOS has no driver calendar") is narrowed by a dated, in-place amendment pointer (per `ADR-015`) rather than left to go silently false — see the pointers added to `ADR-076` on 2026-09-19.

### Governing risk already on record

`ADR-076:135` (Negative consequences) already discloses: *"An offline driver can now receive an advance offer and may reasonably read it as a commitment PIOS cannot honor. Nothing in the product explains the difference."* A calendar-shaped screen makes this misreading **more** likely, not less, because a timeline visually implies a booking. This ADR therefore makes screen copy a **binding implementation constraint**, not a developer preference (Part 5).

---

## Decision

### Part 1 — Scope: read-only, driver-scoped, informational only

The calendar is a read model over data Dispatch already owns. It has no write path of its own, holds nothing, reserves nothing, and has no effect on any other aggregate's behavior.

### Part 2 — Minimum architecture

**Backend — `dispatch` module only. No new aggregate, no new table, no new event, no event version bump, no cross-module call, no migration.**

- One driver-scoped, `Bearer`-gated read, added either as a new query parameter branch on `GET /v1/assignments` or as a sibling endpoint. Gating must copy the shape already ratified at `ADR-060` Decision 4: `Bearer` required, `401` without a valid token, `403` when the token's own driver claim does not match the requested `driverId`. The existing per-row filter in `AssignmentController.listAssignmentsHttp` already establishes that only the committing or executing driver may see a given assignment — the new branch must not widen that boundary.
- A read joining `assignments` to `dispatch_requests.requested_pickup_at` within `pios_dispatch` — both tables already exist in the same database.
- `requestedPickupAt` added to `AssignmentResponse` as an **additive, appended-last, nullable** field — the same precedent already used for `agreedAmount`, `executingDriverId`, and `viaTrustedFallback`.

**Frontend:** one driver-only screen (the existing «Маршруты» tab placeholder is a plausible location, not mandated by this ADR), reading the new endpoint, plus the binding copy constraint below.

### Part 3 — Overlap presentation (bounded by the open threshold)

Until the Product Owner supplies a specific threshold (see Status, above), the screen displays the driver's own accepted future pickup times adjacently, in chronological order, with **no computed warning**. If and when a threshold is later ratified, the same read model may compute a purely informational, non-blocking proximity flag between two adjacent times using that threshold — this ADR reserves that shape without building it now. **No implementation may invent a default threshold** (e.g. "30 minutes seems reasonable") in the absence of that decision.

### Part 4 — What this ADR does not authorize

Any blocking, rejection, or auto-decline of any proposal or assignment on the basis of an overlap or any other calendar-derived fact; any hold, reservation, or capacity-reservation semantics of any kind; any effect on Dispatch eligibility, priority, ranking, or trust (`FallbackDispatchApplicationService`, `FirstRefusalApplicationService`, and `ProposalApplicationService.handle`'s availability check are untouched by this ADR); any effect on pricing; any recurrence, series aggregate, or auto-generated order; any external calendar synchronization; any passenger-facing exposure of a driver's schedule (a driver's forward commitments are private business data, in the same class as `DriverController.getMilestones`/`getClients`); any paywall, monetization, or billing-gating decision (a separate commercial decision, which per `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §8 must never coincide with this feature acquiring a dispatch effect); any specific overlap-minute threshold (Status, above).

### Part 5 — Binding screen-copy constraint

The calendar screen must state, in terms a driver will actually read, that PIOS holds nothing, reserves nothing, and blocks nothing — directly addressing the misreading risk `ADR-076:135` already discloses. This is a binding acceptance criterion for implementation, not a suggestion.

### Part 6 — Process gates (both required before any code)

1. Hypothesis **H16** registered in `docs/PIOS_PRODUCT_HYPOTHESES.md` — done, 2026-09-19.
2. This ADR, accepted — done, 2026-09-19.

---

## Alternatives Considered

- **Inventing a default overlap threshold now, to ship a complete feature.** Rejected outright — `ADR-002` and `CLAUDE.md` forbid inventing a business rule; the threshold must come from the Product Owner.
- **A calendar that also warns Dispatch or soft-excludes a driver near an overlap.** Rejected — this is precisely the "conflict check" `ADR-076:117` (now narrowed, not removed) continues to prohibit, and it would introduce a reservation-like effect on driver selection with no ratified basis.
- **Recurrence / auto-generated orders (the broader "Вариант C" concept).** Rejected — not requested by the Product Owner's D-11.B decision, and it would require a ratified conflict invariant this ADR deliberately does not create.

---

## Consequences

### Positive

- A driver gets a forward view of commitments already made, closing a real gap (`GET /v1/assignments` today cannot answer "what are all my future rides").
- Zero new backend surface area beyond one gated read and one additive field — the underlying data was already local to Dispatch.
- `ADR-076` remains accurate via a narrow, dated pointer rather than a silent inconsistency.

### Negative

- The misreading risk `ADR-076:135` already discloses (an advance offer read as a guaranteed commitment) is made more likely by a calendar-shaped UI; mitigated only by the binding copy constraint (Part 5), not eliminated.
- The overlap warning remains unbuilt until a threshold is ratified — this ADR authorizes the view, not the complete originally-imagined feature.

---

## Related ADRs

- [ADR-076: Server-Authorized Named Driver Offer](ADR-076-Server-Authorized-Named-Driver-Offer.md) — narrowly amended in place (2026-09-19) to permit this read-only calendar; "hold, reservation or conflict check" remain fully prohibited by both documents.
- [ADR-058: Scheduled Pickup Time On Order](ADR-058-Scheduled-Pickup-Time-On-Order.md) — the origin of `requestedPickupAt`.
- [ADR-060: Order Query Authorization](ADR-060-Order-Query-Authorization.md) — Decision 4's gating shape, reused for the new driver-scoped read.
- [ADR-077: Dispatch Routing Obligation](ADR-077-Dispatch-Routing-Obligation-Bounded-Retry-And-Unfulfilled-Order.md) — the 2-minute routing window, explicitly untouched by this ADR.
- [PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md](../PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md) §8 — binds if this feature ever acquires a dispatch effect or a paywall simultaneously; neither is authorized here.

## References

- `docs/PIOS_D11_DECISION_LOCK.md` — the ratified D-11 decision this ADR implements.
- `docs/PIOS_PRODUCT_HYPOTHESES.md` — H16.
- `backend/dispatch/src/main/resources/db/migration/dispatch/V19__dispatch_requests.sql`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLDispatchRequestRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentResponse.kt`
- `frontend/src/pages/DriverHome/DriverHome.tsx`
