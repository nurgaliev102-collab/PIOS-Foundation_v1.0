# ADR-076: Server-Authorized Named-Driver Offer — `requestedDriverId` on `OrderSubmitted`, and the Removal of Client-Created Proposals

## Status

**Accepted — documenting already-built, uncommitted code (2026-09-16).**

Retroactive, on the same terms as `ADR-075`: the code exists in the working tree, was written without the Stage 1 Architect review `.claude/CLAUDE.md` requires for a domain-model change, and is **not committed and not deployed**. This ADR records what was built and names what must be reconciled before it ships. It does not re-open the design.

**Author:** Architect role. **Ratification:** a human act (`ADR-007`).

**Evidence basis: absent.** No H13/H14 exists in `docs/PIOS_PRODUCT_HYPOTHESES.md` (ends at H12). Registering one is a Product Owner act and is not performed here.

**This ADR supersedes part of `ADR-066` and narrows part of `ADR-068`. Both supersessions are stated explicitly below rather than left as silent disagreement (`ADR-015`).**

---

## Context

### What the passenger browser could do before

`ADR-066` (Proposal Participant Authorization) left a gap it named itself, in `ProposalController`'s own KDoc (lines 130–147):

> *"Naming which `driverId` remains unrestricted, exactly as before (Task 20's own residual, deliberately not addressed by this ADR either — see ADR-066 Decision 10 for the one gap this leaves: an authenticated passenger can still create a proposal on another passenger's own `orderId`, under their own identity, never under a false one)."*

In other words: the passenger's browser called `POST /v1/proposals` directly, choosing the driver, and the server checked only that the `passengerReference` matched the caller's own token `sub`. **Which driver received the offer was a client-side decision with no server-side authority behind it.** This was the mechanism behind `ADR-070`'s repeat-order path (`explicitDriverIntent` → propose to the saved driver).

### What `explicitDriverIntent` meant before

`Order.explicitDriverIntent` (Task 15C) recorded only *that* a driver was already chosen, never *which*. `Order.kt`'s own KDoc is explicit:

> *"Carries no driver identity — Order Management does not own that information — only the bare fact that one is already spoken for."*

Dispatch's only use of it was negative: `FirstRefusalApplicationService.attempt` returns `ExplicitDriverIntentDeclared` immediately and touches nothing (`ADR-062`'s complete-no-op guarantee, reaffirmed by `ADR-068` Part 4).

---

## Decision

### 1. `Order` carries an optional `requestedDriverId`, and `OrderSubmitted` reaches version 3

`Order.requestedDriverId: String?` (`backend/order-management/.../domain/Order.kt` line 163) is a plain, optional string identifier pointing at a `driver-management`-owned `DriverId` — **never a foreign key, never a copy of Driver data**. This is the reference-not-ownership rule (`ADR-005`, `ADR-019`, first stated for cross-module identifiers in `ADR-037`) applied unchanged, and it follows `Order`'s own `destination`/`pickupAddress` precedent for an optional, appended-last, set-once-at-submission field.

Two invariants are enforced in `Order.submit` and again at the transport boundary:

```kotlin
require(requestedDriverId == null || requestedDriverId.isNotBlank())
require(requestedDriverId == null || explicitDriverIntent)
```

The second is the one that matters: a named driver can only ride along with the already-ratified `explicitDriverIntent` fact. `OrderSubmittedFirstRefusalListener` re-checks the same pairing on the wire (`require(requestedDriverId == null || explicitDriverIntent)`) and rejects the message otherwise.

`OrderSubmitted` gains `requestedDriverId` and `requestedPickupAt`; the envelope is **version 3**, and Dispatch continues to accept versions 1 and 2 (`SUPPORTED_EVENT_VERSIONS = setOf(1, 2, 3)`), per `ADR-030`. `docs/EVENT_CATALOG.md` §5 and `docs/INTERFACE_CONTRACTS.md` §5 were updated with this contract.

**`Order.kt`'s own KDoc paragraph on `explicitDriverIntent` — quoted above, "Carries no driver identity" — is now false and must be corrected in code by the developer role.** This ADR cannot correct it; it is code, not documentation.

### 2. The driver choice is now made and authorized server-side

The whole path is: authenticated `POST /v1/orders` (token `sub` must equal `passengerReference`, already ratified) → `Order` → outbox → `OrderSubmitted` v3 → `DispatchRequestApplicationService.attemptOffer`, which, when `requestedDriverId != null`, calls `ProposalApplicationService.handle` for exactly that driver.

The security property this buys: **the Proposal's driver is now chosen inside a write path the passenger cannot reach directly**, and it is bound at submission time to an order whose passenger was authenticated. `ADR-066` Decision 10's residual — "an authenticated passenger can still create a proposal on another passenger's own `orderId`" — is closed for the passenger role, because the passenger no longer creates proposals at all.

### 3. `POST /v1/proposals` becomes owner/coordinator-only — **this supersedes `ADR-066`'s passenger branch**

`ProposalController.createProposalHttp` (lines 156–162) now returns **403 to every `Bearer` caller** and permits only the owner/coordinator `Basic` credential:

```kotlin
val verified = sessionTokenVerifier.verify(authorization)
if (!ownerCredentialGate.verify(authorization)) {
    if (verified != null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
}
```

`ADR-066`'s ratified behavior — a `Bearer` caller may create a proposal whose `passengerReference` equals its own `sub` — is **superseded for the HTTP endpoint**. The frontend was changed in the same body of work to stop calling it; `frontend/src/pages/RideRequest/RideRequest.test.tsx` now asserts, in three places, that `POST /v1/proposals` is never called.

**`ProposalController.createProposalHttp`'s own KDoc still describes the superseded ADR-066 behavior** ("for a `Bearer` caller it must equal the verified token's own `sub` (403 on mismatch)"). That KDoc now contradicts the code directly beneath it and must be corrected by the developer role. Named here as a blocking finding, not a style note.

### 4. Deployment is not independently safe across the three modules

`ADR-026` guarantees independent deployability. This change does **not** honor it, and that is disclosed rather than assumed away:

- Deploying `dispatch` (403 on `POST /v1/proposals`) **before** the new frontend breaks `ADR-070`'s repeat-order flow: the old browser code posts a proposal and receives 403.
- Deploying the new frontend (which sends `requestedDriverId` and never posts a proposal) **before** `order-management` means the field is dropped and the named driver is never offered — the order simply produces no proposal.

`frontend`, `order-management`, and `dispatch` must be deployed **together** for this change, in that dependency order, with the golden-path E2E re-run after. See the deploy checklist at the end.

### 5. A past `requestedPickupAt` is now rejected

`OrderSubmissionRequestHandler.parseRequestedPickupAt` adds `require(parsed.isAfter(clock.instant()))`. `ADR-058` accepted any parseable instant; a caller that previously submitted a past time received 201 and now receives 400. This is a **narrowing of an existing public contract** and is recorded as such. It validates the request timestamp only — it is not scheduling, not a reminder, and not a conflict check.

### 6. Availability is relaxed for an advance request to a **named or primary** driver — **this narrows `ADR-068` Part 2, property 3**

`ProposalApplicationService.handle` (lines 134–146) and `FirstRefusalApplicationService.isEligible` (lines 186–191) now read:

```kotlin
val advanceRequest = command.requestedPickupAt?.isAfter(Instant.now()) == true
check(record != null && (record.available || advanceRequest))
check(record.isTest == command.isTest)
```

So a driver whose current availability is `UNAVAILABLE` **may** be offered an order whose pickup is in the future. `ADR-068` Part 2, property 3 states *"Availability remains the sole eligibility gate at selection time"*; that sentence now holds only for immediate orders and is narrowed accordingly.

Three limits are deliberate and must not be read past:

- It applies **only** to the direct (`requestedDriverId`) and First-Refusal (primary driver) paths. `FallbackDispatchApplicationService.attempt` passes no `requestedPickupAt` and is therefore **unchanged** — an anonymous Tier 1/Tier 3 fallback candidate still must be `available = true`. `ADR-068`'s Tier 1/Tier 3 queries are untouched.
- The `record != null` fail-closed rule is unchanged: an unknown driver is never proposed.
- **This is an offer for a future time. It is not a calendar reservation, not a hold, and not a guarantee of fulfilment.** PIOS has no driver calendar and none is created here.

### 7. `isTest` classification is now a fail-closed gate on **every** offer path

`ProposalApplicationService.handle` enforces `record.isTest == command.isTest` for direct and First-Refusal offers, alongside the fallback-path gate `ADR-069` already established. Unknown classification (`record == null`) fails closed. This **strengthens** `ADR-069` and contradicts nothing in it.

One gap is carried forward unresolved, and is stated here so it is not mistaken for solved: **`isTest` on order submission is still a client-supplied flag, not bound to a server-owned classification of the submitting identity.** A caller can still declare its own order to be a test order. `ADR-069` did not close this either.

## What this ADR does not authorize

Any matching, ranking, scoring or priority mechanism (`ADR-002`, `ADR-034`, `PIOS_TAXI_PRODUCT_DECISIONS.md` §10 — a named driver is the passenger's own explicit choice, not a computed one); any driver calendar, hold, reservation or conflict check; any broadcast or parallel offering; reinstating a passenger-callable proposal-creation endpoint; deriving `isTest` from anything; any change to `ADR-070`'s Option B passenger-confirmed relationship formation, which is untouched by this ADR.

---

## Consequences

### Positive

- The single largest client-trust hole in the ride path — the browser naming which driver gets an offer — is closed by construction, not by validation.
- `ADR-070`'s repeat-order product behavior is preserved exactly (same driver, same outcome) while its mechanism moves behind an authenticated server write path.
- `ADR-062`'s `ExplicitDriverIntentDeclared` complete-no-op guarantee is preserved: `DispatchRequestApplicationService.attemptOffer` never calls `FirstRefusalApplicationService` at all when `explicitDriverIntent` is true (line 118 returns first), so First Refusal still touches nothing.
- `ADR-069`'s test/real segregation now covers every offer path instead of fallback only.

### Negative

- `ADR-026`'s independent deployability is not honored for this change (Decision 4). Three artifacts must move together.
- Two ratified documents and two code KDocs now disagree with the code until amended (`ADR-066`; `Order.kt`'s `explicitDriverIntent` paragraph; `ProposalController.createProposalHttp`'s KDoc).
- An existing public contract narrowed without a deprecation window (Decision 5).
- An offline driver can now receive an advance offer and may reasonably read it as a commitment PIOS cannot honor. Nothing in the product explains the difference.

---

## Blocking Prerequisites

1. **`ADR-066` amendment** — its passenger branch is superseded by Decision 3. Amendment pointer added in this session; the ADR text must not be deleted (`CLAUDE.md`, "Never Delete Documentation").
2. **Two code KDoc corrections** (developer role, not this session): `Order.kt`'s "Carries no driver identity" paragraph, and `ProposalController.createProposalHttp`'s ADR-066 paragraph. Both now state the opposite of what their own code does.
3. **Coordinated deploy** of `frontend` + `order-management` + `dispatch`, with the `ADR-068`/`069`/`070` golden-path E2E re-run after — specifically the repeat-order leg, which is the one this change re-implements.

---

## Related ADRs

- [ADR-066](ADR-066-Proposal-Participant-Authorization.md) — partially superseded by Decision 3.
- [ADR-068](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) — Part 2 property 3 narrowed by Decision 6; Tier 1/Tier 3 selection itself unchanged.
- [ADR-069](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — extended, not changed, by Decision 7.
- [ADR-070](ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — the repeat-order behavior preserved; Option B untouched.
- [ADR-062](ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) — `ExplicitDriverIntentDeclared` no-op guarantee, preserved.
- [ADR-058](ADR-058-Scheduled-Pickup-Time-On-Order.md) — narrowed by Decision 5.
- [ADR-030](ADR-030-Event-Schema-Versioning-Strategy.md) — `OrderSubmitted` v3, additive, old versions still consumable.
- [ADR-026](ADR-026-Deployment-Strategy.md) — independent deployability, not honored here (Decision 4).
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — reference-not-ownership, applied to `requestedDriverId`.

## References

Read for this ADR (working tree, 2026-09-16, uncommitted):

- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/OrderSubmitted.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/OrderSubmissionController.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/application/OrderSubmissionRequestHandler.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalListener.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FirstRefusalApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
- `frontend/src/pages/RideRequest/RideRequest.tsx`, `RideRequest.test.tsx`
- `docs/EVENT_CATALOG.md` §5, `docs/INTERFACE_CONTRACTS.md` §5
