# ADR-058: Scheduled Pickup Time — Requested Pickup Instant on `Order`

## Status

**Accepted** — Product Owner ruling, 2026-08-15. Authorized as a **pilot
prerequisite on Product Owner authority**, explicitly **not** on the strength of
an `E-NNN` evidence entry and **not** on the strength of a registered
hypothesis. See "Sprint basis" below, which records the declaration in full
rather than leaving it to be inferred from this line.

Amends no existing ADR. Touches the boundary ADR-040 and ADR-041 established
between Order status and ride progress, and therefore explains at length why it
does **not** add a state to `OrderStatus`.

## Sprint basis — evidence-gate note (Product Owner ruling, 2026-08-15)

`PIOS_PRODUCT_EVIDENCE.md` line 80: the evidence journal is empty. The
registered hypotheses are H1–H7 (`PIOS_PRODUCT_HYPOTHESES.md` lines 99–178) and
**none of them concerns pre-booked or future-dated rides.** This capability
therefore has no basis of either ratified kind.

The Product Owner has authorized it as a **pilot prerequisite on Product Owner
authority** — the same route ADR-055 took and recorded in its own
"Evidence-gate note" (line 20). **ADR-057's "Sprint basis" section states the
full terms of that declaration and they apply identically here**, and are not
restated at length: not evidence-backed; not hypothesis-backed; no hypothesis
may be registered retroactively to cover it; the gate itself is not amended,
relaxed, or reinterpreted, and its two ratified bases remain in full force for
every other Sprint; H3/H5/H6/H7 keep their «Ожидает проверки» status; and the
paper trail lives inside these ADRs only, exactly as ADR-055's does.

One disclosure specific to this capability. Pre-booking has no stated success
criterion, because no hypothesis defines one — so the pilot has no pre-agreed
way to tell whether it created value, only whether it functioned. If the
Product Owner wants to *learn* something from it rather than merely ship it,
the honest instrument is a hypothesis registered **before** the first pilot
session (about whether a passenger who can pre-book returns unprompted, for
example), not a conclusion drawn afterwards from usage. This ADR names that and
does not do it.

## Context

### What the Product Owner asked for

A passenger can book a ride for a future date and time ("завтра в 18:00")
rather than only for now. Mobile browser only, no app install, no new
dependency.

### What exists today, verified in this repository

- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt`
  lines 79–87: the aggregate carries `id`, `status`, `origin`, `destination`,
  `passengerName`, `createdAt`, `pickupAddress`. **No time concept other than
  `createdAt`**, which lines 62–63 and 133–135 state is *"always the server's
  own clock at the moment of submission, never caller-supplied."*
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/OrderStatus.kt`
  lines 13–17: exactly `SUBMITTED`, `COMPLETED`, `CANCELLED`. Its own KDoc
  (lines 7–11): *"the assigned, accepted, and ride states an order passes
  through once Dispatch allocates a driver ... are outside this task's scope;
  they are not modeled here."*
- `Order.kt` lines 99 and 113: **both** `complete()` and `cancel()` guard on
  `check(status == OrderStatus.SUBMITTED)`.
- `Order.kt` lines 67–77 (`pickupAddress`, Sprint H5) is the additive-optional-field
  precedent, and states the mechanical rule this ADR reuses: *"appended as this
  class's own last constructor parameter (not inserted before `createdAt`) so
  every existing positional call site ... continues to compile unchanged."*
- `api/SubmitOrderRequest.kt` lines 34–39: every field after `passengerReference`
  is optional with a default, for the reason lines 10–15 record — a mandatory
  field on this exact contract already broke its one real caller
  (`passenger-experience`'s `RestClientOrderSubmissionClient`) once.
- The last order-management migration is `V8__add_order_pickup_address.sql`;
  the next free version is **V9**.
- `frontend/src/pages/RideRequest/RideRequest.tsx` lines 22–27 and 426 onward:
  once `POST /v1/orders` succeeds, the same submit handler immediately calls
  Dispatch's `POST /v1/proposals` for the driver the passenger arrived through.
  There is no notion today of an order that is not proposed at once.
- `backend/dispatch/.../application/ProposalLapseApplicationService.kt` line 67:
  an `OPEN` Proposal lapses after `pios.proposal.lapse.timeout-minutes`,
  default **5** — and lines 35–46 record explicitly that the value is
  configuration, *"never a value this class invents as a business rule."*
- `frontend/package.json` contains **no date library** of any kind.

## Decision

### 1. One new field on `Order`: `requestedPickupAt: Instant?`.

Optional, immutable, submission-time; appended as the aggregate's **last**
constructor parameter, after `pickupAddress`, for the positional-compatibility
reason `Order.kt` lines 67–77 already record. `null` means *"as soon as
possible"* — the behaviour every existing row and every existing caller already
has, unchanged. A non-null value means *"the passenger asked to be picked up at
this instant."*

Set once at submission; neither `complete()` nor `cancel()` touches it,
mirroring `origin`/`destination`/`passengerName`/`pickupAddress` exactly.

This is the first **caller-supplied** time on this aggregate, which is the one
respect in which it is not a pure copy of the `pickupAddress` precedent. That
difference is confined to validation (Decision item 4) and to the wire format
(Decision item 3); it changes nothing about ownership or lifecycle.

### 2. No new `OrderStatus` value. `SCHEDULED` is rejected.

This is the load-bearing part of the decision, and the reasons are structural
rather than stylistic:

- **It would break two invariant guards immediately.** `complete()` and
  `cancel()` both require `status == SUBMITTED` (`Order.kt` lines 99, 113). A
  `SCHEDULED` order that cannot be cancelled is unacceptable, so both guards
  would have to be widened — and ADR-041's Assignment-completion synchronisation
  drives `complete()` from outside this module, so the change would not be
  local to `order-management`.
- **It would re-open a ratified invariant.** `DOMAIN_MODEL.md` Section 11's
  one-current-status invariant and `OrderStatus`'s own KDoc both describe a
  deliberately minimal three-state lifecycle. Adding a state to it is exactly
  the kind of change ADR-015 requires an explicit supersession for, and nothing
  here needs one.
- **It would put dispatch timing back on `Order`.** `Order.kt` lines 10–14 and
  `OrderStatus.kt` lines 7–11 both state that states which depend on Dispatch's
  own capability *"are not modeled here."* `SCHEDULED` is precisely such a
  state: it means "Dispatch has not acted yet." This is the same boundary
  ADR-040 drew when ride progress went onto `Assignment` rather than `Order`.
- **A nullable attribute adds zero states and re-opens nothing.** Every
  invariant in `Order` remains literally true, character for character, after
  this change.

A scheduled order is therefore an ordinary `SUBMITTED` order that happens to
carry a requested time. It can be completed and cancelled by exactly the paths
that exist today, with no change to ADR-041's synchronisation or ADR-053's
cancellation-driven proposal withdrawal.

### 3. Dispatch timing is unchanged: a scheduled order is proposed immediately.

**What is deferred is the pickup, not the dispatch.** The passenger submits the
order with a requested time; the frontend's existing auto-propose fires at once,
exactly as it does today; the driver sees "к 18:00" on the proposal card and
confirms — or declines — now, inside the existing waiting window
(`ProposalLapseApplicationService.kt` line 67, default 5 minutes). Nothing in
Dispatch changes: no scheduler, no held-order concept, no new trigger, no new
status, no change to the lapse sweep, no change to `POST /v1/proposals`.

This is the correct shape for a personal-network product specifically: the
passenger is pre-booking *their own* driver and wants that person's confirmation
now, not a dispatch attempt at 17:45 whose outcome they cannot see.

**Rejected alternative — hold the order and propose at T-minus-N.** It requires
a second time-triggered capability beyond ADR-052's lapse sweep, a "held"
concept on either `Order` or `Proposal`, and an answer to *"what happens if the
driver is unavailable or does not respond at that moment"* — which is a
matching/fallback rule, excluded from architectural scope by ADR-002 and
`DOMAIN_MODEL.md` line 177, and one that `PRODUCT_DECISION_FULFILLMENT_AUTHORITY_SCOPE.md`
leaves open. It is strictly larger, and it cannot be specified without inventing
a business rule. Recorded in Evolution Path, not built.

**Consequence to state plainly to the Product Owner:** under this decision a
pre-booking is confirmed by the driver *at booking time*. If the requirement is
"the driver is asked shortly before the pickup hour," this ADR does not deliver
it and the rejected alternative above is what would be needed.

### 4. Time representation: UTC instant in storage, ISO-8601 with offset on the wire.

- **Wire:** an ISO-8601 timestamp carrying an explicit offset or `Z` — exactly
  what `new Date(value).toISOString()` produces in every mobile browser. The
  field is a `String?` on `SubmitOrderRequest`/`OrderResponse`, parsed to
  `Instant` at the application boundary; a value that does not parse is a 400,
  the same class of error every other malformed input on this controller
  already produces.
- **Storage:** `TIMESTAMPTZ`, i.e. a Kotlin `Instant` — the same shape
  `created_at` already uses (`V7__add_passenger_name_and_created_at.sql`).
- **Rejected:** a naive local-time string, a separate timezone column, and any
  client-side date library. The frontend has no date dependency today and needs
  none: a native `<input type="datetime-local">` plus `new Date(value)` resolves
  the browser's own offset locally and works on iOS and Android browsers with
  zero install and zero new package — which is what the Product Owner's "use
  only what already exists" constraint requires.

**On the single-city question, answered rather than assumed:** yes, the pilot is
single-city, and *because* that is a pilot fact rather than a ratified product
rule, it is deliberately not encoded in storage. UTC costs nothing now, and
storing local wall-clock text would make the eventual multi-city change a data
migration against real rows rather than a display change. No timezone is named
anywhere in the contract, in the schema, or in this ADR.

### 5. Validation is structural only.

If present, the value must parse as an instant. That is the entire check.

Explicitly **not** validated, because each would be a business rule PIOS has
never ratified (ADR-002; `DOMAIN_MODEL.md` line 177): "must be in the future,"
a minimum lead time, a maximum booking horizon, working hours, rounding to the
nearest five minutes, or any relationship to `createdAt`. The passenger's own
screen prevents picking a past time; the backend asserts nothing. Recorded as
Open Question 1, with a recommendation, because tightening it is the Product
Owner's call.

### 6. Exact contract.

| Surface | Change |
| --- | --- |
| `domain/Order.kt` | `+ val requestedPickupAt: Instant?` as the **last** constructor parameter; `submit(...)` gains a trailing `requestedPickupAt: Instant? = null` |
| `api/SubmitOrderRequest.kt` | `+ val requestedPickupAt: String? = null`, appended last — every existing body, including `RestClientOrderSubmissionClient`'s `passengerReference`-only body, stays valid |
| `api/OrderResponse.kt` | `+ val requestedPickupAt: String? = null`, appended last |
| Application layer | Parse the string to `Instant`; malformed → 400. No other check |
| Migration | `V9__add_order_requested_pickup_at.sql`: `ALTER TABLE orders ADD COLUMN requested_pickup_at TIMESTAMPTZ NULL;` — nullable, no `DEFAULT`, no backfill (V8's own precedent) |
| Frontend — passenger | An optional "когда" control on the existing order form: "сейчас" (default, sends nothing) or a chosen date/time. Native input only, no library |
| Frontend — driver | Display the requested time on the order card, read from the `GET /v1/orders` response the driver's screen **already polls** — joined client-side by `orderId` to the proposal it already has |

**Dispatch receives nothing new.** No field is added to `POST /v1/proposals`,
no Dispatch aggregate changes, and no cross-module contract is created: the
driver's screen already reads Order Management directly, the direct-module-call
precedent ADR-042's Context records for `DriverHome.tsx`. `passenger-experience`,
`driver-management`, `network-management` and `identity` are untouched. Every
event stays at v1 — `OrderSubmitted`'s payload is not extended, following
ADR-041's own Q4 ruling and ADR-042 Decision item 6.

## Open Questions (Product Decision — ADR-002)

1. **Should a past or absurdly distant requested time be rejected by the
   backend?** *Recommendation: no for the pilot* — the UI prevents it, and any
   threshold is an invented rule. If the Product Owner wants one, it is a
   one-line change to the application-layer parse and must be recorded as an
   amendment here.
2. **What should happen to a scheduled order nobody accepted?** Nothing new is
   decided here: the Proposal lapses on the existing 5-minute rule and the
   order stays `SUBMITTED`, exactly as an immediate order does today. Whether a
   passenger should be prompted to re-propose is a product question, not an
   architectural one, and belongs with the already-named future Product
   Decision **«Управление жизненным циклом заказа»** (ADR-042 R10 / Open
   Question 10, unstarted).
3. **Reminders before a scheduled pickup.** Not decided and not authorized —
   Notifications remains a ratified-but-unscaffolded module
   (`MODULE_STRUCTURE.md`; ADR-033), and no notification capability exists to
   build on.

## Consequences

### Positive

- A passenger can pre-book with one optional field, one nullable column, one
  migration, zero new states, zero Dispatch changes and zero new dependencies.
- Every invariant, guard, event payload and cross-module contract in the system
  is literally unchanged. ADR-040, ADR-041, ADR-052 and ADR-053 all continue to
  hold word for word.
- Cancellation of a scheduled order works on day one, because a scheduled order
  is an ordinary `SUBMITTED` order.

### Negative

- **The driver must confirm a pre-booking now, not near the pickup hour**
  (Decision item 3). This is the honest cost of not building time-triggered
  dispatch, and the Product Owner should accept it explicitly.
- **Nothing reminds anyone.** Once the driver has accepted a ride for 18:00,
  PIOS does nothing at 17:45 — no notification, no re-prompt, no surfacing. The
  commitment lives in two humans' heads. This is a real product limitation, not
  a defect, and if the pilot shows it matters, it is evidence for the
  Notifications decision rather than something to patch in a sprint.
- **`Order` gains its fifth optional context field.** ADR-042's Consequences
  already flagged this accumulation as worth a deliberate review; this ADR adds
  to it rather than addressing it.
- A driver's screen must now join two responses client-side to show the
  requested time on a proposal card. That is a UI cost, deliberately paid
  instead of adding the value to Dispatch's contract.

## Evolution Path

None of this is resolved here: time-triggered dispatch (propose at T-minus-N);
recurring or repeating bookings; reminders or notifications of any kind; any
rule about how far ahead a booking may be made; and any behaviour that treats a
scheduled order differently from an immediate one anywhere in the backend. Each
requires its own decision, and the first three require a Product Decision before
an ADR.

## What This ADR Does Not Authorize

A new `OrderStatus` value; any scheduler, timer or background job in any
module; any change to Dispatch, `Proposal`, `Assignment`, or the propose/accept
contracts; any notification, reminder or push of any kind; any date/time library
dependency in the frontend; any timezone concept in storage or contract; any
validation of the requested time beyond parseability; and any change to
`passenger-experience`, `driver-management`, `network-management` or `identity`.

## Traceability

| Source | Relationship |
| --- | --- |
| `backend/order-management/.../domain/Order.kt` lines 67–77 | The additive-optional-field precedent and its positional-compatibility rule, reused exactly |
| `backend/order-management/.../domain/OrderStatus.kt` lines 7–17 | Why `SCHEDULED` is rejected, in that enum's own words |
| `docs/ADR/ADR-040-Assignment-Ride-Lifecycle.md`, `docs/ADR/ADR-041-...md` | The Order-vs-Assignment boundary this decision respects rather than crosses |
| `docs/ADR/ADR-052-Proposal-Lapse-Resolution-Mechanism.md` | The existing waiting-period mechanism a scheduled order reuses unchanged |
| `docs/ADR/ADR-026-Deployment-Strategy.md`, `api/SubmitOrderRequest.kt` lines 10–15 | Why every added request field is optional with a default |
| `PIOS_PRODUCT_EVIDENCE.md` lines 80, 86–92 and the 2026-08-01 addendum | The gate this ADR does not amend, relax, or claim to satisfy |
| `docs/ADR/ADR-057-Driver-Stated-Time-To-Pickup.md`, "Sprint basis" | The full terms of the prerequisite-route declaration this ADR shares |
