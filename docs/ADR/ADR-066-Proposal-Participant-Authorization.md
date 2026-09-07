# ADR-066: Proposal Participant Authorization — `passengerReference` on `Proposal`, and Who May Create, Read, or Act on One

## Status

**Recommendation, ready for Product Owner ratification as written.** The answer to
the architectural question (should `passengerReference` be added to `Proposal`) is
a clear **yes**, not a close call, so this ADR does not present competing options
in ADR-059's "Options as presented to the Product Owner" shape. Two items *do*
need an explicit Product Owner decision and are marked **[PO DECISION]** inline —
neither changes the architecture, both change what a real person experiences:

- **[PO DECISION 1]** — Decision 8: proposals persisted before this change have no
  passenger binding and become unconfirmable/unreadable by their passenger.
  Recommendation: accept, deploy at a quiet moment.
- **[PO DECISION 2]** — Decision 7: `GET /v1/proposals/{proposalId}` is a sixth
  unauthenticated surface this review found, beyond the five the task named.
  Recommendation: close it in the same change.

Everything else here is ratified architecture applied to a confirmed P0 gap and
requires no product ruling.

**Evidence-gate note, recorded rather than glossed.** No `E-NNN` entry in the
Evidence Log motivates this work and no `docs/PRODUCT_DECISION_*.md` requires it.
It proceeds on Product Owner authority as remediation of a confirmed
vulnerability — the identical basis and identical disclosure ADR-060's own
"Evidence-gate note" recorded for itself, and for the same reason: no
evidence-analyst pass is required for a remediation that *removes* disclosure and
adds no capability. It is not a product-scope item and must never later be read as
evidence-backed. An evidence pass **would** be required before any item under
"Deliberately not done here".

**No production code is written or authorized to be written by the act of
publishing this ADR.** It specifies a change precisely enough to be implemented;
implementation is a separate, subsequent developer task.

This ADR consumes, at last, the gap two ratified documents each named and each
declined to close:

- **ADR-059 S3**: *"Closing that requires one additional narrow reference —
  `passengerReference` as a plain string on `Proposal` … That is permissible under
  reference-not-ownership … but it is a **breaking change to an existing
  contract** and must be named in whatever ADR authorizes it, never slipped in as
  an implementation detail."* This is that ADR. This is that naming.
- **ADR-060 Decision 4**: *"`?orderId=` is **left open** by this ADR. Locking it
  requires a `passengerReference` on `Proposal` … It is not authorized here."*
  It is authorized here.

## Context

### The gap, in the code's own words

`backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
already documents this gap against itself, twice. Its class KDoc (lines 98–101)
records what Task 21 deliberately did not close:

> *"…what this change deliberately does not attempt to close (verifying that an
> authenticated passenger actually owns the order they are proposing on —
> `Proposal` carries no `passengerReference`, and closing that gap is a named,
> separate architectural decision, not part of this remediation)."*

And `createProposal` itself (lines 121–134):

> *"Deliberately does NOT verify the token's `sub` against the order's own
> passenger … Proposal carries no `passengerReference`."*

The single verified line that produces the gap, `ProposalController.kt` line 131:

```kotlin
val authorized = sessionTokenVerifier.verify(authorization) != null || ownerCredentialGate.verify(authorization)
```

`!= null` — *any* valid token, any `sub`. The identical line appears at line 282
(`confirmPrice`) and line 311 (`declinePrice`), which are the **passenger's own
two decision points** in the live 2026-09-05 price flow. Today, any registered
account can confirm or decline any other passenger's price on any proposal whose
id it holds.

`listProposals` (line 442) takes the `?orderId=` branch with no credential check
of any kind.

### Why Dispatch cannot answer "is this the order's passenger?" today

`Proposal` (`domain/Proposal.kt` lines 50–56) holds exactly `id`,
`order: OrderReference`, `driver: DriverReference`, `createdAt`, `isTest`. There
is nothing to compare `token.sub` against. This is precisely what ADR-059 S3 said
in its own words: *"**Dispatch does not know who the passenger is.** It can check
a driver (`token.drv == assignment.driver`) but has nothing to compare a
passenger's `token.sub` against."*

That statement was true when written and is still true of `Proposal`. It is **no
longer true of Dispatch as a module**, and that changes the answer — see next.

### What changed, and what the last full review of this got wrong

Three facts, each verified in this repository for this ADR, that were not all true
when ADR-059 S3 and ADR-060 Decision 4 were written:

**1. Dispatch already holds passenger identifiers, in its own database, today.**
`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/PassengerReference.kt`
exists as a Dispatch-owned `@JvmInline value class` over a plain String, and
`db/migration/dispatch/V13__primary_driver.sql` persists it:

```sql
CREATE TABLE primary_driver_records (
    passenger_reference TEXT PRIMARY KEY,
    ...
```

ADR-062 ratified this. So "may Dispatch hold a passenger identifier at all" is
already answered *yes*, in production, in Dispatch's own schema. This ADR does not
open that question; it reuses the answer.

**2. Order Management's `OrderSubmitted` event already carries the value into
Dispatch, and Dispatch already parses it.**
`OrderSubmittedFirstRefusalListener.kt` lines 116–117 read
`payload.passengerReference` and require it non-blank;
`OrderSubmitted.kt` lines 21–28 state it is *"the same value `Order.origin`
already carries … serialized on the wire as the opaque
`payload.passengerReference` string … never as a richer representation."*

**3. Since 2026-09-07, `Order.origin` is enforced, not merely declared.**
`OrderSubmissionController.kt` lines 80–84 now reject a `POST /v1/orders` whose
`passengerReference != token.sub` with 403, and any request without a valid token
with 401. ADR-060 Decision 2 *declared* `Order.origin` to be the authenticated
passenger's `identityId`; nothing enforced it until this fix. It is now enforced
at the only write path.

**The consequence of (3) for this ADR, stated exactly.** `Order.origin` is now a
trustworthy record of the authenticated passenger. It is **not** a value Dispatch
may read synchronously (see Decision 3). What Dispatch can rely on is narrower and
sufficient: the claim `token.sub`, verified by the same replicated
`SessionTokenVerifier` and signed by the same `identity` secret, is *the same
claim* Order Management verified when it wrote `Order.origin`. Dispatch does not
need to ask Order Management what the answer is; it can check the same signed
assertion Order Management checked.

### The First Refusal path already solved half of this, and it is not a false friend

`FirstRefusalApplicationService.attempt` (`application/FirstRefusalApplicationService.kt`
lines 124–145) receives a `PassengerReference` — sourced from the event, therefore
from `Order.origin` — and, at line 142, calls straight into
`proposalApplicationService.handle(ProposeDriverCommand(order, driver, isTest))`,
**dropping it on the floor** because `ProposeDriverCommand` (lines 16–20) has
nowhere to put it. That service's own KDoc (lines 22–30) says so in its own words:
*"`ProposalApplicationService.handle`/`ProposeDriverCommand` carry no passenger
identity at all today … Determining 'does this order's passenger have a primary
driver' structurally requires a `PassengerReference` no existing entry point
provides."*

**Assessment: this generalizes, for the proposals it creates, and only for those.**
It is a genuine, already-proven, event-sourced, Order-Management-authoritative
source for exactly the field this ADR adds — and it costs nothing to use, because
the value is already in scope at the call site. It is a *false friend* only if
mistaken for a general solution: it covers proposals created by First Refusal, and
covers neither the passenger's direct `POST /v1/proposals` (`RideRequest.tsx` line
720) nor the coordinator's (`Coordinator.tsx` line 260). Those need their own
source. Hence Decision 3's two sources, one field.

Nothing in this ADR changes what First Refusal *does*: the same outcome, the same
availability gate, the same `AlreadyAttempted` semantics, the same
`ExplicitDriverIntentDeclared` no-op. The Proposal it creates merely now records
who it was created for.

### The three real callers, verified

| Caller | File / line | Credential held at call site today | Sends it today? |
| --- | --- | --- | --- |
| Passenger, direct propose | `frontend/src/pages/RideRequest/RideRequest.tsx` 720–728 | `identity.token` (Bearer) | **Yes** (line 727) |
| Coordinator/owner, manual propose | `frontend/src/pages/Coordinator/Coordinator.tsx` 260–265 | owner `Basic` | **Yes** (line 262) |
| Passenger, status poll | `RideRequest.tsx` 471 (`?orderId=`) | `identity.token` | **No — no header at all** |
| Coordinator, per-order proposal history | `Coordinator.tsx` (`fetchProposalsForOrder`) | owner `Basic` | **No** — ADR-061 Decision 2 line 122 records it sends none |
| First Refusal (automatic) | `OrderSubmittedFirstRefusalListener.kt` 122 | n/a — in-process, no HTTP | n/a |

Both write paths already send a credential. Only the two read paths do not, and
both hold one at the call site. This is why the frontend cost of this ADR is small
and entirely additive.

## Decision

### 1. `passengerReference` is added to `Proposal` as an optional, reference-only identifier. **[Answer to question A: yes.]**

`Proposal` gains one field:

```
val passengerReference: PassengerReference? = null
```

reusing Dispatch's **existing** `com.pios.dispatch.domain.PassengerReference`
(ADR-062) unchanged — no new type is introduced. Nullable strictly for the legacy
reason `createdAt`'s own KDoc already establishes for this same aggregate (lines
33–38: *"`null` only for a proposal reconstructed from a row persisted before
`V7__proposal_timestamps.sql` existed"*), never as an ordinary permitted state for
a new proposal.

### 2. Why this does not violate Dispatch's bounded context. **[Answer to question B.i and B.ii.]**

Not by citing the rule, but by testing this field against each of its three
prohibitions in turn (ADR-005, ADR-009, ADR-019; established for
`network-management` in ADR-037 and reused for `Identity` in ADR-039; restated in
ADR-059 S1 as *"Only identifiers cross module lines"*):

**(a) Is it a foreign key?** No, and structurally cannot be. `pios_dispatch` and
`pios_ordermanagement` are separate databases (`MODULE_STRUCTURE.md` Section 2; no
shared schema). `passenger_reference TEXT NULL` can no more carry a constraint
against another module's table than `order_reference` and `driver_reference`
already do — and those two have been plain, unconstrained TEXT since
`V4__proposals.sql`.

**(b) Is it a copy of another module's data?** No. The distinction that matters is
between an *identifier for* an entity and the *data of* that entity. Order
Management's passenger-side data is `passengerName`, `pickupAddress`,
`destination`, `requestedPickupAt`. **None of those come.** What comes is the
opaque string `Order.origin` already carries — which is itself only an identifier
pointing at `identity`. Dispatch gains no ability it did not have to render,
search, or reason about a passenger; it gains the ability to compare one opaque
string to another opaque string. `Proposal.statedPrice`'s own KDoc already
establishes this codebase's standard for such a value: *"A plain, opaque,
unvalidated string: PIOS does not compute, parse, compare, or assign any meaning
to it."* The same holds here, with the single, explicitly enumerated exception of
equality against a verified token claim.

**(c) Does it bake a server-to-server call into the write path?** No — and this is
the prohibition that actually had teeth here, because the obvious naive
implementation ("Dispatch asks Order Management who the passenger is") violates it
outright. Neither of Decision 3's two sources makes any cross-module call:
source (a) is a value the caller already holds and presents in its own request;
source (b) is an event Dispatch already consumes. Decision 3 exists specifically
to keep this true.

**(d) Precedent it is identical to.** `PassengerReference.kt`'s own KDoc already
states the exact shape and the exact justification for a passenger identifier
inside Dispatch — *"a distinct, module-local value carrying only the plain
identity string, mirroring `OrderReference`/`DriverReference`'s own exact
convention. Dispatch does not verify that this passenger exists, and carries no
passenger profile, contact, or Personal Client Relationship information beyond
this bare identifier."* Every word of that remains true of this field. This ADR
adds a second holder of an identifier Dispatch is already ratified to hold; it
does not introduce a new category of information into the module.

**Therefore: yes, `passengerReference` is a legitimate reference-only identifier
under the rule.** Not because ADR-059 S3 pre-approved the shape — that is
corroboration, not the argument — but because it fails to trip any of the three
prohibitions when each is tested against it individually.

### 3. Source of truth is Order Management's `Order.origin`. Dispatch acquires it through two existing channels and never by calling Order Management. **[Answers to B.iii and B.iv.]**

**Source of truth: confirmed, with one precision.** Order Management's
`Order.origin` is and remains the sole source of truth for "who is this order's
passenger" (ADR-060 Decision 2, now enforced by the 2026-09-07 fix). The precision
that matters: *source of truth* is not the same as *runtime authority for this
check*. Dispatch never reads `Order.origin`. It compares against the same signed
`sub` claim Order Management itself compared against. Both modules trust one
issuer (`identity`) and one secret (ADR-055 Decision 1); neither trusts the other
at request time. That is the property that keeps this change inside the boundary.

**Channel A — the direct write path (`POST /v1/proposals`, Bearer caller).**
`ProposeDriverRequest` gains `passengerReference: String?`. The check, in
`createProposal`, is `ConnectionController.createConnection`
(`backend/passenger-experience/.../api/ConnectionController.kt` lines 99–103)
copied exactly — the same pattern ADR-055 Decision 4 ratified, that ADR-059 S3
named as the one to mirror, and that `OrderSubmissionController` lines 80–84
already applies in Order Management:

```
verify token       -> null      => 401   (before any lookup, before any validation)
request.passengerReference != verified.sub => 403
```

`orderId` and `driverId` remain unverified references, exactly as
`ConnectionController`'s own KDoc says of `driverId` (*"stays an unverified
reference, since this module does not own drivers"*) and exactly as
`ProposalController`'s own KDoc already says of both (lines 51–54).

**Channel B — the owner/coordinator write path (`Basic` caller).** The owner has
no `sub` and never will (ADR-044 Decision 6; ADR-060 Decision 5). The owner
therefore **supplies `passengerReference` and no `== sub` comparison is applied**.
This is not a loophole: the owner already reads `OrderResponse.origin` for every
order (ADR-060 Answer 3 keeps all eight fields, Mode 3 returns every row), so
`Coordinator.tsx` already has the correct value in hand and merely passes it
through. The owner credential is the trust anchor on this branch, as it already is
for `lapseProposal` (`ProposalController.kt` lines 367–376).

**Channel C — the First Refusal path (in-process, no HTTP).**
`ProposeDriverCommand` gains `passengerReference: PassengerReference? = null`, and
`FirstRefusalApplicationService.attempt` passes the one it already holds
(line 142). This value is the strongest of the three: it came from
`OrderSubmitted`, which came from `Order.origin`, with no client in the loop at
all. No behavioural change to First Refusal (see Context).

**Rejected: Dispatch reads Order Management at write time.** This would be the
first Dispatch → Order Management synchronous edge, closing a dependency cycle
between two bounded contexts that today have exactly one edge, asynchronous and
one-directional (`MODULE_STRUCTURE.md` Section 5). ADR-060 Decision 3 rejected the
mirror-image call (*"it would place Dispatch's uptime in front of Order
Management's reads"*) on reasoning that applies symmetrically, and ADR-062 already
rejected a synchronous Dispatch → Passenger Experience read for the primary-driver
lookup, building `primary_driver_records` instead. Consistency with two ratified
rejections, not novelty.

### 4. No personal data reaches Dispatch. **[Answer to B.v.]**

Stated explicitly and bindingly: **under this proposal, no name, no phone number,
and no address ever reaches Dispatch.** The field is a `@JvmInline value class`
over `String` whose only validation is `isNotBlank()`. Concretely and
enumerably:

- `ProposeDriverRequest` gains exactly one new field, a `String?` identifier.
- `proposals` gains exactly one new column, `passenger_reference TEXT NULL`.
- **`ProposalResponse` gains nothing.** The response shape is unchanged — see
  Decision 6 for why this is a deliberate choice and not an omission.
- No event payload changes. No outbox record changes.
- ADR-059 **S1 and S6 remain in force, unamended, and are not relaxed by one
  word.** No phone number may appear in `ProposalResponse` or any other response,
  and nothing in this ADR authorizes any contact disclosure of any kind. ADR-059's
  Option C ruling of 2026-08-15 stands entirely untouched.

### 5. Does this conflate authorization with contact disclosure? No — and here is the test that distinguishes them.

The question is fair, because ADR-059 S3 specified this same field for a different
consumer. The distinction:

`passengerReference` is a **fact**, not a permission. It records *who this proposal
is for*. Authorization is not the field; authorization is the *comparison* of that
fact against a verified claim, performed independently at each endpoint by that
endpoint's own rule. Two different gates comparing against one shared fact is
normal and is already how this codebase works — `Proposal.driver` is one fact, and
`acceptProposal`, `declineProposal`, `proposePrice`, and
`listProposals?driverId=` each compare `token.drv` against it under four separate
rules.

The test that would prove conflation: does the field carry permission semantics —
a flag, a scope, a role, a "may see contact" bit? It does not, and Decision 4
forbids it ever acquiring one. If a future ride-contact disclosure is ever
authorized (which requires all three steps in ADR-059 "How this decision may be
revisited", none of which this ADR performs), it would gate on this same fact plus
its own separate rule. That is reuse of a fact, not reuse of a permission.

So: one field, two consumers, kept structurally separate at the point where it
matters — the comparison, not the storage.

### 6. `ProposalResponse` is unchanged. `passengerReference` is never returned.

Row-level and action-level authorization only, no new field on the wire — the same
choice ADR-060 Answer 3 made for `OrderResponse`, and here it is strictly easier to
justify: after Decisions 7–9 every caller of a proposal-returning endpoint is
already either that proposal's own passenger, its own driver, or the owner, so
returning the value would tell each caller only something it already knows.
Returning it would also hand any driver a passenger's `identityId` from a new
surface for no gain. Changing the shape would break `RideRequest.tsx`'s and
`Coordinator.tsx`'s parsers for no security benefit.

### 7. Final authorization policy for every Proposal surface. **[Answer to question D.]**

`Bearer*` = a token passing `SessionTokenVerifier.verify`. Absent, wrong-scheme,
malformed, bad-signature and expired all collapse to one indistinguishable 401
(that class's own KDoc lines 38–46). Check order everywhere: **scheme → credential
(401) → request shape (400) → existence (404) → subject comparison (403)**, so an
unauthenticated caller never learns whether an id exists — the ordering
`acceptProposal` (lines 186–194) already established in this controller.

| # | Surface | Requirement | Exact check |
| --- | --- | --- | --- |
| 1 | `POST /v1/proposals` | Bearer **or** owner `Basic`; body must carry `passengerReference` | Bearer: 401 if unverified; **400** if `passengerReference` absent/blank; **403** if `!= sub`. `Basic`: 401 if invalid; 400 if `passengerReference` absent/blank; **no `sub` comparison**. |
| 2 | `GET /v1/proposals?orderId=` | **No longer unauthenticated.** Bearer or owner `Basic` | 401 without a valid credential. `Basic` → every proposal for that order. Bearer → **only** proposals where `passengerReference == sub` **or** `driver == drv`. Non-matching rows are **omitted**, never 403 — a list returns what matches (ADR-060's own `?ids=` reasoning). |
| 3 | `GET /v1/proposals?driverId=` | **Unchanged from ADR-060 Decision 4** | 401 without valid credential; 403 if `driverId != drv`; owner `Basic` may name any driver. Not touched, not regressed. |
| 4 | `POST /{id}/accept`, `POST /{id}/decline` | **Unchanged** — driver-token-gated | 401; then 404 if unknown; then 403 if `verified.drv != proposal.driver.driverId`. Not touched. Also unchanged: `POST /{id}/propose-price` (driver) and `POST /{id}/lapse` (owner `Basic` only). |
| 5 | `POST /{id}/confirm-price`, `POST /{id}/decline-price` | **Gap closed.** Bearer matching the proposal's own passenger, or owner `Basic` | 401 if unverified; then **404** if the proposal is unknown; then **403** if `proposal.passengerReference == null` **or** `!= verified.sub`. Owner `Basic` bypasses the comparison (it has no `sub`). |
| 6 | `GET /v1/proposals/{proposalId}` | **[PO DECISION 2]** — recommend: same rule as row 2 | Recommended: 401 without a valid credential; 404 if unknown; 403 unless `passengerReference == sub`, `driver == drv`, or owner `Basic`. |

**On row 6.** `ProposalController.kt` lines 390–397 take **no `Authorization`
parameter at all**. This surface is not among the five the task named and was found
during this review. It returns `orderId`, `driverId`, `status`, `statedPrice` and
`statedEtaMinutes` to anyone holding a `proposalId`. Leaving it open while closing
row 2 would be incoherent: the same disclosure, one id at a time. It is
nevertheless flagged for the Product Owner rather than folded in silently, because
closing it **amends ADR-061 Decision 4**, which explicitly ruled that Coordinator's
"Check status" call *"was never gated by ADR-060, and neither gains a credential
here… only the page itself is gated, not these two actions."* Closing row 6
requires `Coordinator.tsx` to send the credential it already holds on that call.
Note the parallel precedent: ADR-061 Decision 4 made the same statement about
Coordinator's `POST /v1/proposals`, and Task 21 later gated it anyway —
`Coordinator.tsx` line 262 now sends `toBasicAuthorizationHeader(credential)`. That
half of ADR-061 Decision 4 is therefore *already* superseded in fact; this ADR
proposes superseding the other half deliberately and in writing rather than by
accident.

**Recommendation: close row 6 in the same change.** It is one credential on one
existing call, on a screen that already holds the credential.

### 8. Migration for existing rows. **[Answer to B.vi.]** **[PO DECISION 1]**

**Precedent followed exactly**: `V7__proposal_timestamps.sql`, which solved this
identical problem for this identical aggregate — *"additive, nullable … Every
proposal row that exists before this migration has NULL in both columns
permanently."*

New migration `backend/dispatch/src/main/resources/db/migration/dispatch/V15__proposal_passenger_reference.sql`
(V14 is the current highest in this module):

```sql
ALTER TABLE proposals ADD COLUMN passenger_reference TEXT NULL;
```

No backfill is possible and none may be attempted. Dispatch has no way to learn
which passenger a historical proposal belonged to without the cross-module read
Decision 3 rejects — and inventing one would be exactly the fabrication
`CLAUDE.md` forbids. This mirrors ADR-060 Decision 2's own disclosed consequence
(*"No migration can fix this; nothing ever proved who those values belonged to"*).

**Consequence, requiring the Product Owner's acceptance.** A proposal created
before this deploys has `passengerReference == null` and therefore, under Decision
7 rows 2 and 5, **fails closed**: its passenger cannot confirm or decline its
price, and cannot poll its status. In practice this affects only proposals still
unresolved at the moment of deployment — the live pilot's proposals resolve within
minutes, and `ProposalLapseScheduler` resolves the rest.

- **Recommended: accept, and deploy at a quiet moment.** Fail-closed is the
  correct direction for an authorization fix and matches ADR-060's own posture
  (*"A misconfigured deployment fails closed… This is the correct failure
  direction and it is stated so nobody is surprised by it at 2am"*). The affected
  passenger's remedy is to submit a new order.
- **Rejected: treat `null` as "anyone authenticated may act"** — that would
  preserve today's vulnerability indefinitely on every legacy row and hand an
  attacker a reason to hunt for old proposals.

### 9. `passengerReference` is a primary-constructor parameter, and the reflective reconstruction lookup must be widened with it.

This is the single highest-risk implementation detail in this ADR and is stated as
a binding constraint, not a note.

It belongs in `Proposal`'s **primary constructor** (alongside `createdAt`, not
alongside `statedPrice`) for that field's own stated reason: it is *"fixed at
construction and independent of `status`"* (`Proposal.kt` lines 30–33).

`PostgreSQLProposalRepository.reconstruct` (lines 143–158) restores a persisted
`Proposal` by reflectively locating the private constructor **by parameter count
and type**. Both `Proposal.kt` (lines 86–95) and
`PostgreSQLProposalRepository.kt` (lines 59–65) already warn about this in their
own KDocs — *"an un-widened lookup would throw `NoSuchMethodException` at the
first real database read, not at compile time."* The lookup **must** be widened
from five parameters to six.

**A second, subtler trap the developer must resolve before writing the lookup.**
The existing lookup passes `String::class.java` for `id`, `order` and `driver`
because `ProposalId`/`OrderReference`/`DriverReference` are non-null
`@JvmInline value class`es and erase to their underlying `String`. A **nullable**
inline value class does *not* erase that way — Kotlin boxes it, so a
`PassengerReference?` parameter is expected to appear in the JVM signature as
`PassengerReference`, not `String`. The sixth lookup argument is therefore
expected to be `PassengerReference::class.java`, with
`rs.getString("passenger_reference")?.let { PassengerReference(it) }` passed to
`newInstance`.

**This must be proven by an actual test run against the database, not assumed** —
it is precisely the class of failure that is invisible to any unit test avoiding
PostgreSQL, which is why both KDocs already warn about it. `PostgreSQLProposalRepositoryTest`
must cover both a NULL legacy row and a populated row.

### 10. Deliberately not closed: the order↔passenger binding itself. Disclosed, with its closure specified.

**Stated plainly, so nobody reads this ADR as having achieved more than it did.**

Decision 3 Channel A binds a Proposal to an **authenticated human**, not to the
**order's** passenger. Dispatch verifies that the caller is who they claim to be;
it does not verify that the `orderId` they named is theirs. So an authenticated
passenger B, in possession of passenger A's `orderId`, can still create a Proposal
on A's order carrying B's own `passengerReference` — and, having created it, act
on it.

**What that costs and what it does not.** It does not disclose anything of A's: B
learns no name, address, destination or phone from doing it (`ProposalResponse`
carries none — Decision 6), and A's own order data stays behind ADR-060's Mode 1.
What B gains is nuisance: consuming A's one-open-proposal-per-order slot
(`proposals_one_open_per_order`, V14) and creating a proposal a driver may act on.
It is a griefing and denial-of-service vector, not a data-disclosure one.

**Why it is not closed here.** The only two mechanisms that close it are:

1. A synchronous Dispatch → Order Management read — rejected in Decision 3 on two
   ratified precedents.
2. A local, event-sourced `order_passenger_records` projection built from
   `OrderSubmitted`, exactly as `primary_driver_records` (V13) was built from
   `PrimaryConnectionDesignated` under ADR-062. Architecturally sound and already
   proven in this module.

Option 2 is the correct eventual answer and **is blocked today by an ordering
race, not by taste**: `RideRequest.tsx` calls `POST /v1/proposals` immediately
after `POST /v1/orders` (line 720, in `attemptProposal`), while `OrderSubmitted`
travels Order Management's outbox → `OutboxRelayScheduler` poll → RabbitMQ →
Dispatch's listener. The proposal routinely arrives before the event does. Making
the projection a **precondition** would fail-closed on the pilot's main path and
break every direct ride request. Making it advisory (check when known, allow when
not) buys only "the attacker must win a race against the outbox relay" — real
hardening, but not a closure, and not worth the migration and the new consumer
write inside a P0 remediation.

**Named target for a future ADR**, so its closure has a specification and is not
improvised: an `order_passenger_records` projection populated by
`OrderSubmittedFirstRefusalListener` (or a sibling consumer), plus an explicit,
decided policy for the race window. That is a separate decision. This ADR does not
authorize it and no implementation task may build it under this ADR — the same
treatment ADR-060 Decision 7 gave its own deferred retention gap.

## IDOR / BOLA analysis after implementation. **[Answer to question E.]**

Threat model: an attacker holds a self-service registered account (registration is
open — ADR-055 Decision 3) and therefore a valid Bearer token with an arbitrary
`sub` and `drv == null`. They attempt to enumerate or guess `orderId`, `driverId`
or `proposalId` values to read or act on proposals they are not a participant of.

| # | Surface | Result | Why |
| --- | --- | --- | --- |
| 1 | `POST /v1/proposals` | **PARTIAL PASS** | Cannot act *as another passenger* — `passengerReference != sub` is 403, so no proposal can ever be created bearing a victim's identity. **Can still create a proposal on a guessed `orderId` under their own identity** — the residual in Decision 10. Discloses nothing; consumes the order's open-proposal slot. Honest grade: pass on impersonation and on disclosure, fail on integrity. |
| 2 | `GET /v1/proposals?orderId=` | **PASS** | Was fully open; now 401 without a credential, and a Bearer caller receives only rows where `passengerReference == sub` or `driver == drv`. Guessing a stranger's `orderId` returns `[]` — indistinguishable from an order with no proposals, so the enumeration also yields no existence oracle. |
| 3 | `GET /v1/proposals?driverId=` | **PASS — unchanged, not regressed** | ADR-060 Decision 4's rule is untouched by this ADR: 401/403/owner-`Basic`. Explicitly verified as a non-regression requirement below. |
| 4 | `accept` / `decline` (and `propose-price`, `lapse`) | **PASS — unchanged** | Already 401 → 404 → 403 on `verified.drv != proposal.driver.driverId`. A passenger-only token (`drv == null`) can never match any driver string. `lapse` remains owner-`Basic`-only. Guessing a `proposalId` yields 403. |
| 5 | `confirm-price` / `decline-price` | **PASS — this is the largest single improvement** | Today: any authenticated account can confirm or decline any proposal's price. After: 403 unless `sub == proposal.passengerReference`. A guessed `proposalId` is 403, and a legacy `null` row is also 403 (fail closed, Decision 8). |
| 6 | `GET /v1/proposals/{proposalId}` | **PASS if [PO DECISION 2] is accepted; FAIL if deferred** | If deferred it remains fully open and anyone holding a `proposalId` reads the order↔driver binding and the stated price. This is the honest reason the recommendation is to close it in the same change. |

**Systemic check — is any order identifier still obtainable anonymously?**
ADR-060 Decision 4 claimed *"no unauthenticated endpoint in PIOS returns an order
identifier"* while itself leaving `?orderId=` open (a sink, needing the id already)
and, as this review found, `GET /v1/proposals/{proposalId}` open (a **source** — it
returns `orderId`). Closing row 6 is therefore what actually makes ADR-060
Decision 4's own claim true rather than nearly true. The developer must re-run that
sweep exhaustively — including `platform-ops`, `/v1/assignments`, and `GET /v1/drivers`
— and report the result rather than assume it, exactly as ADR-060 required.

## Question C — the alternative, if the answer to A had been "no"

Not offered as a hedge; recorded because the task asked for it to be weighed, and
because rejecting it on stated grounds is what makes the "yes" defensible.

The only alternative that unambiguously determines an order's legitimate
participants without a field on `Proposal` is the **event-sourced
`order_passenger_records` projection** described in Decision 10 — Dispatch's own
local, ADR-062-shaped answer to "who is this order's passenger", consulted at every
proposal endpoint instead of storing the value on the aggregate.

It is architecturally clean and would be *stronger* on the one axis Decision 10
concedes: it binds to the **order**, not merely to an authenticated caller. It is
rejected as the primary mechanism for three reasons, in order of weight:

1. **It cannot gate `POST /v1/proposals` without breaking the live pilot** — the
   outbox-relay race in Decision 10. An authorization mechanism that must be
   bypassed on its most important path is not an authorization mechanism.
2. **It leaves the Proposal itself unattributed.** Every later read
   (`?orderId=`, `confirm-price`) would need a join through a projection that may
   not have the row, reintroducing the same race at every call site rather than at
   one.
3. **It is strictly more work for strictly less coverage today**: a new table, a
   new consumer write path (touching First Refusal's own listener, which this task
   forbids changing behaviourally), and a race policy that must be decided — versus
   one nullable column and one equality comparison.

The two are complements, not rivals: the field is the mechanism now; the projection
is the named strengthening later (Decision 10). Choosing the field first does not
foreclose the projection, and the projection would be pointless without a field to
compare against.

## What implementation must change and verify

Exact enumeration, per question B.vii.

**Backend — `backend/dispatch/`:**

| File | Change |
| --- | --- |
| `domain/Proposal.kt` | `passengerReference: PassengerReference? = null` as the 6th **primary-constructor** parameter; `propose()` gains a matching parameter; KDoc records the ADR and the nullable-for-legacy reason (mirror `createdAt`'s own wording) |
| `application/ProposeDriverCommand.kt` | `passengerReference: PassengerReference? = null` |
| `application/ProposalApplicationService.kt` | `handle` passes it through to `Proposal.propose` — no other method changes |
| `application/FirstRefusalApplicationService.kt` | line ~142: pass the `passengerReference` it already holds into `ProposeDriverCommand`. **Behaviour must be otherwise identical** — same outcomes, same availability gate, same `AlreadyAttempted`/`ExplicitDriverIntentDeclared` semantics |
| `api/ProposeDriverRequest.kt` | `passengerReference: String?` (nullable at transport, 400 if absent/blank — so an old client gets a clear 400, not a silent null) |
| `api/ProposalController.kt` | `createProposal` (Decision 7 row 1); `confirmPrice` and `declinePrice` (row 5 — replace the `!= null` line); `listProposals`'s `?orderId=` branch (row 2); `getProposal` (row 6, if approved). KDoc must **delete the "deliberately does NOT verify" disclaimers and cite this ADR** — leaving them would leave the code lying about itself |
| `persistence/PostgreSQLProposalRepository.kt` | `selectColumns` += `passenger_reference`; INSERT column list and values (**not** the `DO UPDATE SET` clause — the value is fixed at construction, like `created_at`); `reconstruct`'s reflective lookup widened to six parameters per Decision 9 |
| `persistence/InMemoryProposalRepository.kt` | Verify it round-trips the new field |
| `db/migration/dispatch/V15__proposal_passenger_reference.sql` | New (Decision 8) |

**Backend tests:**
`api/ProposalControllerTest.kt` (the largest change — every row of Decision 7's
matrix, positive and negative, plus `passengerReference` absent → 400, `!= sub`
→ 403, owner `Basic` with no `sub` → 201, and legacy-`null` proposal on
`confirm-price` → 403);
`api/ProposalControllerPostgreSQLIntegrationTest.kt`;
`persistence/PostgreSQLProposalRepositoryTest.kt` (**must** cover a NULL legacy row
and a populated row — this is what catches Decision 9's reflection trap);
`persistence/ProposalRepositoryContractTest.kt`;
`persistence/InMemoryProposalRepositoryTest.kt`;
`domain/ProposalTest.kt`;
`application/ProposalApplicationServiceTest.kt`;
`application/FirstRefusalApplicationServiceTest.kt` (assert the passenger
reference reaches the created Proposal **and** that every existing outcome
assertion is unchanged);
`persistence/OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`.

**Frontend — additive, both credentials already at the call site:**

| File | Change |
| --- | --- |
| `frontend/src/pages/RideRequest/RideRequest.tsx` line 728 | add `passengerReference: identity!.identityId` to the `POST /v1/proposals` body (the `Authorization` header at line 727 already exists) |
| `frontend/src/pages/RideRequest/RideRequest.tsx` line 471 | the `?orderId=` poll must now send `Authorization: Bearer ${identity.token}` — **its failure is not cosmetic**: this poll is how the passenger sees the driver's price and reaches the confirm/decline buttons. Test the whole submit → poll → confirm-price sequence in one session |
| `frontend/src/pages/Coordinator/Coordinator.tsx` line 263 | add `passengerReference: <the selected order's `origin`>` to the body; it already holds the order rows from `fetchOrders(credential)` |
| `frontend/src/pages/OwnerControlCenter/todayData.ts` `fetchProposalsForOrder` | must send the owner `Basic` credential on `?orderId=`. **This one fails silently** — the surrounding `.catch(() => [])` (ADR-060 Decision 5's own warning) would turn a 401 into an empty proposal history rather than an error, on both the Owner Control Center and Coordinator |
| `Coordinator.tsx` "Check status" | only if [PO DECISION 2] is accepted — send the credential it already holds |

**Explicit non-regression checks:** `?driverId=` behaviour identical to ADR-060
Decision 4 (401/403/owner) with tests unchanged; `accept`/`decline`/`propose-price`/`lapse`
untouched; `DriverHome.tsx` entirely untouched; First Refusal outcomes byte-identical;
`proposals_one_open_per_order` still enforced; the 409 mappings and the
`DataIntegrityViolationException` branch unchanged.

## Deliberately not done here

The order↔passenger binding (Decision 10) and its `order_passenger_records`
projection. `POST /v1/orders` service-to-service authentication. `GET /v1/drivers`,
every `/v1/assignments` endpoint, `POST /v1/orders/{id}/cancel`, and `platform-ops`
are untouched. `network-management` stays isolated. `ai-advisor` is not touched in
any way. No rate limiting, no audit log, no data-retention rule (still gated on the
Product Decision ADR-060 Decision 7 named), no RBAC, no role model, no pagination,
no field-level redaction. **No contact disclosure of any kind** — ADR-059 Option C
and S1–S6 stand entirely unamended. Connection, Circle of Trust and First Refusal
product behaviour are unchanged. `Order` gains nothing. Pricing, commission and
matching criteria remain out of architectural scope (ADR-002).

**Naming them is the point.** This ADR closes the passenger side of Dispatch's
authorization; it does not make Dispatch fully authorized, and nobody should read
it as having done so.

## Consequences

- **The largest concrete win**: `confirm-price` and `decline-price` — the
  passenger's two real decision points in the live flow — stop accepting any
  authenticated stranger. Today they accept anyone with an account.
- `GET /v1/proposals?orderId=` stops being an anonymous read of the order↔driver
  binding. ADR-060 Decision 4's own stated blocker for this is removed by the same
  change that removes it.
- Dispatch's `Proposal` becomes the second Dispatch aggregate to hold a passenger
  identifier, after `primary_driver_records` (ADR-062). No new category of data
  enters the module.
- **A breaking API contract change**, exactly as ADR-059 S3 required be named:
  `POST /v1/proposals` gains a required body field, and both existing callers must
  ship with it. Backend and frontend must deploy together; a lagging client gets a
  clean 400.
- **Proposals in flight at deploy time become unconfirmable by their passenger**
  ([PO DECISION 1], Decision 8). Fail-closed, disclosed, irreversible by migration.
- ADR-061 Decision 4 is amended if [PO DECISION 2] is accepted — and its other half
  is recorded as already superseded in fact by Task 21.
- **A residual integrity gap is carried forward knowingly** (Decision 10): a
  passenger can still create a proposal on a guessed `orderId` under their own
  identity. Anyone reading this later should understand it as a decision with a
  reason and a named closure, not an unnoticed hole.
- Dispatch gains no dependency on Order Management, no synchronous call, and no new
  build dependency. The single asynchronous Dispatch↔Order-Management edge is
  unchanged.

## Traceability

| Source | Relationship |
| --- | --- |
| `ADR-059` S3 | Named this exact field, called it *"a breaking change to an existing contract"* that *"must be named in whatever ADR authorizes it"*; this ADR is that authorization |
| `ADR-059` S1, S6, Option C | Unamended and in force — no contact disclosure, no phone anywhere, is authorized here |
| `ADR-060` Decision 4 | Left `?orderId=` open pending exactly this field; closed here. `?driverId=` untouched and non-regression-tested |
| `ADR-060` Decision 2 | `Order.origin` as the authenticated passenger's `identityId` — the source-of-truth this ADR confirms, now enforced by the 2026-09-07 `OrderSubmissionController` fix |
| `ADR-060` Decision 3, 7 | Why Dispatch does not call Order Management to authorize; the convention for disclosing a deferred gap with a specified closure (followed in Decision 10) |
| `ADR-062`, `V13__primary_driver.sql`, `PassengerReference.kt` | Dispatch already holds and persists passenger identifiers; the type reused unchanged; the event-sourced-projection pattern named in Decision 10 |
| `ADR-055` Decisions 1, 4, 5 | Token format, per-module replicated verification, and `ConnectionController.createConnection`'s `== sub` pattern copied in Decision 3 |
| `ADR-061` Decision 4 | Amended for `GET /v1/proposals/{proposalId}` if [PO DECISION 2] is accepted; its `POST /v1/proposals` half recorded as already superseded by Task 21 |
| `ADR-005`, `ADR-009`, `ADR-019`, `ADR-037`, `ADR-039` | Reference-not-ownership — tested prohibition by prohibition in Decision 2, unamended |
| `ADR-003`, `ADR-027`, `MODULE_STRUCTURE.md` Sections 2, 4, 5 | Why no synchronous cross-module read is introduced; per-module enforcement; no shared security code |
| `ADR-011`, `ADR-013` | Least Privilege, Defense in Depth; testing obligations in "What implementation must verify" |
| `ADR-043`, `V7__proposal_timestamps.sql` | The nullable-for-legacy-rows precedent in this same aggregate, followed exactly by Decision 8 |
| `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` rows 89–90 | The two P0 findings this ADR answers |
| `ADR-002`, `CLAUDE.md` | Why no retention rule, pricing rule, or matching rule is invented here |
