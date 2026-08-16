# ADR-057: Driver-Stated Time to Pickup — Minimal Model and Placement

## Status

**Accepted** — Product Owner ruling, 2026-08-15. Authorized as a **pilot
prerequisite on Product Owner authority**, explicitly **not** on the strength of
an `E-NNN` evidence entry and **not** on the strength of a registered
hypothesis. See "Sprint basis" below, which records the declaration in full
rather than leaving it to be inferred from this line.

Amends no existing ADR. Extends — and is explicitly *not* covered by — ADR-042,
whose own "What This ADR Does Not Authorize" block (as amended 2026-07-31)
limits Dispatch changes to *"the one nullable `Proposal` attribute, its
migration, its optional accept-request field and its response field, exactly as
specified in R2 and R5."* A second driver-stated attribute is therefore outside
ADR-042's authorization and requires this decision, however small the diff.

## Sprint basis — evidence-gate note (Product Owner ruling, 2026-08-15)

Recorded here rather than glossed, in the same place and the same form ADR-055
chose for the identical situation (that ADR's own "Evidence-gate note", line
20).

**The gate, as it actually stands.** `PIOS_PRODUCT_EVIDENCE.md` line 80: the
evidence journal is empty — no `E-NNN` entry exists, so no Sprint can consume
evidence today. The registered hypotheses are H1–H7
(`PIOS_PRODUCT_HYPOTHESES.md` lines 99–178) and **none of them is about a
stated time to pickup.** Under that log's own addendum of 2026-08-01
(*«элемент объёма, не обеспеченный ни `E-NNN`, ни зарегистрированной
гипотезой, в объём не входит»*), this capability has **no basis of either
ratified kind.**

**The ruling.** The Product Owner has authorized this work as a **pilot
prerequisite on Product Owner authority** — the same route ADR-055 took and
wrote down. What that means, stated so it can never later be misread:

- This Sprint is **not evidence-backed.** No `E-NNN` entry justifies it, and
  none may be written afterwards to make it look as though one did.
- This Sprint is **not hypothesis-backed.** No `H-NNN` is claimed, and **no
  hypothesis may be registered retroactively to cover it** —
  `PIOS_PRODUCT_HYPOTHESES.md`'s own rule (*«Гипотеза, записанная задним числом
  под уже сделанную работу, основанием не является»*) forbids exactly that, and
  this ADR does not do it.
- The evidence gate itself is **not amended, relaxed, or reinterpreted.** Its
  two ratified bases remain exactly as ratified and in full force for every
  other Sprint. This is a Product-Owner-authority exception recorded against one
  named capability, not a new general rule.
- H3, H5, H6 and H7 remain «Ожидает проверки». Nothing here changes any
  hypothesis's status, and this Sprint produces evidence for none of them.
- **What this costs, disclosed:** a capability shipped on this route carries no
  stated success criterion, so the pilot has no pre-agreed way to tell whether
  it worked. That is the price of the route, not an oversight of this ADR.

**Where the paper trail lives, and why not in the living documents.** ADR-055
recorded its identical declaration inside the ADR only; neither
`PIOS_PRODUCT_EVIDENCE.md` nor `PIOS_PRODUCT_HYPOTHESES.md` carries any entry
for it. This ADR follows that precedent exactly. Writing a prerequisite-route
entry into `PIOS_PRODUCT_EVIDENCE.md` would add a **third admissible basis** to
a document whose own text names two — a substantive change to a
Product-Owner-authority document, which only the Product Owner may make, and
which this ruling did not ask for.

## Context

### What the Product Owner asked for

A driver, when responding to a Proposal, states how long it will take to reach
the passenger, choosing from a fixed set: **2 / 5 / 7 / 10 / 15 minutes**.
GPS-derived or computed ETA is **explicitly out of scope** by the same
instruction. The passenger sees the stated value once the driver has accepted.

### What already exists, verified in this repository

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt`
  lines 65–116: `statedPrice: String?` is a **private-set property, not a
  constructor parameter**, set exactly once inside `accept(statedPrice, at)`,
  and never touched by `decline`/`lapse`/`withdraw`. Its KDoc states the reason
  the shape is load-bearing: `PostgreSQLProposalRepository.reconstruct`
  restores a persisted `Proposal` by reflectively invoking the private
  constructor by parameter count and type, so *"widening that constructor would
  break that lookup at runtime, silently, in a path no unit test that avoids
  the database would catch."*
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLProposalRepository.kt`
  line 160 replays the persisted terminal transition (`proposal.lapse(respondedAt)`
  and its sibling branches) — the accept branch must replay a persisted stated
  value rather than losing it, exactly as ADR-042 R5 already requires for
  `statedPrice`.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
  line 105: the accept endpoint's body is `@RequestBody(required = false)`;
  line 110 maps it to `AcceptProposalCommand(id, request?.statedPrice)`; lines
  127 and 143 construct `ProposalResponse` with **four** positional arguments
  in the decline/lapse paths; lines 192–201 are the single shared
  `toResponse()` mapping that serves both `?driverId=` and `?orderId=` queries.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalResponse.kt`
  lines 30–38: every field after `status` is optional with a `null` default.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/AcceptProposalCommand.kt`
  lines 24–33: validation is one structural `require` — non-blank if present,
  and nothing else, per ADR-042 R4.2.
- `backend/dispatch/src/main/resources/db/migration/dispatch/V6__proposal_stated_price.sql`:
  one additive nullable column, no `DEFAULT`, no backfill. The next free
  Dispatch version is **V10** (`V9__order_cancelled_idempotency.sql` is the
  last).
- ADR-042 R9: the passenger already reads `GET /v1/proposals?orderId=` on the
  confirmed-order screen (`RideRequest.tsx`), and the driver already reads
  `GET /v1/proposals?driverId=` (`DriverHome.tsx`). Both read paths exist.

### Why this needs a decision rather than being additive

Three reasons, each citable:

1. ADR-042's authorization block (above) explicitly does not extend to a second
   Dispatch attribute.
2. A *duration* is a comparable number in a way `statedPrice`'s opaque string is
   not. ADR-042 R4.3 calls comparison across proposals *"the single most
   important prohibition in this ADR"* because it turns stated values into an
   auction; the same risk applies to "fastest driver wins," which is a
   **matching criterion** — excluded from architectural scope by ADR-002 and
   `DOMAIN_MODEL.md` line 177. That prohibition must be restated against a real
   field, not left implicit.
3. Placement (Proposal vs. Assignment) touches the boundary ADR-040 and ADR-041
   spent two sprints establishing, and a time-to-pickup *looks* like ride
   progress. It is not, and the reason has to be written down.

## Decision

### 1. PIOS records a *stated* duration. It computes nothing.

The driver states it; PIOS stores it verbatim. No estimation, derivation,
prediction, default, pre-fill, suggestion or recalculation of any kind. No
location, distance, route or positioning concept is introduced or implied —
`EVENT_CATALOG.md` Section 7 (*"No location, tracking, or positioning data is
described or implied"*) stands unamended, and the Product Owner's own exclusion
of GPS-based ETA is carried here as a binding constraint, not a preference.

### 2. Placement: an attribute of `Proposal`, in `dispatch`. Not `Assignment`.

Set exactly once, inside `Proposal.accept()`, at the moment of acceptance;
absent (`null`) for every proposal that predates this change and every accept
call that does not supply one. `decline()`, `lapse()` and `withdraw()` never set
it — a duration attached to a refusal is a counter-offer, excluded for the same
reason ADR-042 Open Question 6 already derived for price.

ADR-042 R2's four reasons for `Proposal` over `Assignment` apply here verbatim
and are not re-argued: provenance matches the aggregate (the value is an
attribute of the driver's own response act, and `Assignment` is that act's
consequence); ADR-041 item 4 reserves `Assignment` for ride-progress *state*
(`ARRIVED`, `IN_PROGRESS`, `COMPLETED`) and loading it with non-state
attributes erodes that boundary; both read paths already reach `Proposal`
without a new endpoint; and one fact gets one home — the value is **not**
copied onto `Assignment` when the Assignment is created in the same ADR-036
transaction.

**The distinction that decides it:** a stated time to pickup is a *statement
made once, at acceptance*, and never revised. It is not a ride-progress state
and never changes as the ride proceeds. If the Product Owner later wants a
driver to *update* their ETA while en route ("running 5 minutes late"), that is
genuinely ride progress, belongs on `Assignment` under ADR-040's own lifecycle,
and requires its own decision. It is not authorized here and may not be added
as an implementation detail of this one.

### 3. Shape: `statedEtaMinutes: Int?` — a nullable whole number of minutes.

**Why a typed `Int` here, where ADR-042 R5 deliberately chose an opaque
`String` for the price.** The reason R5 gave for the string was specific and
still holds for price: currency, units, precision and rounding were unratified
(Open Question 2, still open), so any typed representation would have asserted
a business rule nobody had made. **No analogous unratified dimension exists for
this field:** the Product Owner stated the unit (minutes) and stated the values
(whole numbers). An `Int` therefore asserts exactly what was ratified and
nothing more. This is **not** a precedent for typing the price, and must not be
cited as one.

Mechanics, all inherited from ADR-042 R5 rather than re-invented:

- **A private-set property, not a constructor parameter.** The reflective
  constructor lookup in `PostgreSQLProposalRepository` must not be widened by
  this change.
- **Reconstruction must replay the persisted value** through `accept(...)`, so
  a round-tripped Proposal carries what was stored rather than losing it.
- **Signature:** `accept(statedPrice: String? = null, statedEtaMinutes: Int? = null, at: Instant = Instant.now())`.
  The new parameter is inserted **before** `at` so both stated values sit
  together; the developer must update `PostgreSQLProposalRepository`'s accept
  branch, which passes `at` positionally today. A type mismatch makes that a
  compile error rather than a silent one, which is the only reason this
  insertion is acceptable.

### 4. Validation: structural only, and the fixed set lives in the UI.

The application layer checks one thing, in `AcceptProposalCommand`'s existing
`init` block: if present, `statedEtaMinutes` must be greater than zero and no
greater than **240**. That bound is a shape guard against absurd input, in the
same spirit as R4.2's *"non-blank and within a stated maximum length"* — it is
an operational parameter, **not** a ratified business rule, and no meaning
attaches to the number 240.

**The fixed set {2, 5, 7, 10, 15} is enforced in the driver's own screen, not
in the domain.** The set is a presentation choice about what is easy to tap; it
is not an invariant of the fact being recorded. Keeping it in the UI means the
Product Owner can change the offered options without a domain change, a
contract change, or a migration. Recorded as Open Question 1 below, with a
recommendation, because it is the Product Owner's call and not this document's.

### 5. Binding prohibitions.

Each is checkable in review. The first six restate ADR-042 R4 against this
field; the seventh and eighth are specific to a duration and are the real
reason this ADR exists.

1. **No production of a value.** No computation, estimation, derivation,
   default, pre-fill or recommendation. Absent input means `null`, never zero.
2. **No validation beyond the shape in item 4.** No "too optimistic", no
   "too long", no comparison against anything.
3. **No comparison, ordering or aggregation.** Stated durations are never
   compared across proposals, drivers, orders or time; never sorted or ranked;
   never summed or averaged. No repository query may filter or order by this
   column.
4. **Never an input to selection, ordering, priority or eligibility.** Excluded
   from any Assignment Policy (ADR-034 Part 3) and from any future Fair
   Opportunity Policy implementation. `ADR-034` Part 2's forbidden-inputs list
   is unamended, and — as with the stated price — the prohibition is reinforced
   by temporal ordering: the value does not exist until the driver responds to
   a Proposal, and a Proposal is only ever made to a driver already selected.
5. **No propagation.** It appears in no event payload, no outbox record, no
   cross-module contract and no other module's database. Every event stays at
   v1 (ADR-042 Decision item 6, unchanged). It does not travel to `Assignment`
   or to `order-management`.
6. **No PIOS behaviour depends on it.** It gates nothing and blocks nothing. A
   ride completes exactly as ADR-041 ratified, with or without a stated
   duration.
7. **It is not a countdown, a timer or a promise the platform enforces.** No
   expiry, no automatic transition, no "the driver is late" state. Nothing in
   PIOS reacts to the clock reaching it.
8. **It is never compared against actual arrival.** Deriving punctuality,
   reliability, accuracy or any score from the difference between a stated
   duration and a real `ARRIVED` timestamp is a **reputation system**,
   excluded by name in `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 3
   (*"composite driver trust score, opaque ranking, or any reputation
   system"*). This is the most likely future misuse of this field and is
   forbidden here explicitly so that a later reader cannot mistake silence for
   permission.

### 6. Exact contract.

| Surface | Change |
| --- | --- |
| `domain/Proposal.kt` | `var statedEtaMinutes: Int? = null; private set` — private-set property, set only by `accept()`; `accept` gains `statedEtaMinutes: Int? = null` before `at` |
| `application/AcceptProposalCommand.kt` | `+ val statedEtaMinutes: Int? = null`, with one `require(statedEtaMinutes == null \|\| statedEtaMinutes in 1..240)` |
| `api/AcceptProposalRequest.kt` | `+ val statedEtaMinutes: Int? = null` — body stays `required = false`; a missing body, an absent field and an explicit null all mean "not stated" |
| `api/ProposalResponse.kt` | `+ val statedEtaMinutes: Int? = null`, **appended last**, so the two four-argument constructions at `ProposalController.kt` lines 127 and 143 keep compiling |
| `api/ProposalController.kt` | pass the new field into the command; add it to the single shared `toResponse()` mapping (lines 192–201). **No new endpoint.** |
| `persistence/PostgreSQLProposalRepository.kt` | persist the column on write; replay it into `accept(...)` on reconstruct. **The reflective constructor lookup is not widened.** |
| Migration | `V10__proposal_stated_eta.sql`: `ALTER TABLE proposals ADD COLUMN stated_eta_minutes INTEGER NULL;` — nullable, no `DEFAULT`, no backfill (V6's own precedent) |
| Frontend — driver | The fixed choice set {2, 5, 7, 10, 15} on the accept action; sent with `statedPrice` in the same existing accept call |
| Frontend — passenger | Display-only on the confirmed-order screen, beside the stated price (ADR-042 R9's own placement). **No passenger-side control of any kind** — ADR-042 R10's exclusion applies unchanged and in full |

No new module, aggregate, event, event version, endpoint, cross-module contract
or boundary change. `order-management`, `driver-management`,
`passenger-experience`, `network-management` and `identity` are untouched.

## Open Questions (Product Decision — ADR-002)

1. **Should the backend enforce the exact set {2, 5, 7, 10, 15}?**
   *Recommendation: no* — see Decision item 4. If the Product Owner wants the
   set enforced server-side, that is a ratifiable answer and the only change is
   the `require` in `AcceptProposalCommand`; it must be recorded as an
   amendment here, not decided by an implementation task.
2. **Is the stated duration shown to the passenger before acceptance?**
   Answered by construction: there is nothing to show. The driver creates the
   value at the moment of acceptance, exactly as with the stated price
   (ADR-042 Open Question 4's own dissolution).
3. **May the driver revise it after accepting?** Not authorized here. That is
   ride progress on `Assignment` (Decision item 2) and needs its own decision.

## Consequences

### Positive

- The passenger learns when the driver will arrive without a phone call, using
  an existing endpoint, an existing screen and an existing poll.
- No new dependency of any kind — the Product Owner's "no new paid external
  dependencies" constraint is satisfied trivially, because nothing external is
  involved at any layer.
- ADR-034, ADR-035, ADR-036, ADR-040, ADR-041 and ADR-042 are all left
  unmodified; ADR-034 Part 2 is reinforced by the same temporal argument
  ADR-042 R3 already made.
- Backward compatible by construction: bodyless accept calls, existing rows and
  existing response consumers are all unaffected.

### Negative

- **Dispatch now stores a genuinely comparable number.** Unlike the opaque
  price string, `SELECT ... ORDER BY stated_eta_minutes` is trivially
  expressible. Prohibition 5.3/5.4 is therefore procedural for this field where
  it was partly structural for price. This is the principal residual risk of
  this ADR and is named rather than buried.
- `Proposal` gains its second non-structural attribute. ADR-042's own
  Consequences already flagged the accumulation of optional context fields as
  a pattern worth a deliberate review; this ADR adds to it rather than
  addressing it.
- A stated duration that turns out to be wrong has no consequence anywhere in
  the system (prohibition 5.6/5.8). That is deliberate, and it is also a real
  limitation the Product Owner should see stated plainly: this field creates an
  expectation in a passenger that PIOS itself does nothing to hold anyone to.

## What This ADR Does Not Authorize

GPS, geolocation, distance or route calculation of any kind; any computed or
suggested ETA; a live-updating or re-stated ETA; any countdown, timer, reminder
or notification; any punctuality, accuracy or reliability metric; any
comparison or ranking of stated durations; any change to `Assignment`,
`AssignmentStatus`, `Assignment.create`, `AssignOrderCommand`, the availability
projection, any outbox record or event payload, any Assignment Policy, or any
endpoint other than the existing accept endpoint and the existing proposal
queries; and any change to `order-management`, `driver-management`,
`passenger-experience`, `network-management` or `identity`.

## Traceability

| Source | Relationship |
| --- | --- |
| `docs/ADR/ADR-042-Stated-Ride-Price-Minimal-Model.md` | R2 placement reasoning, R4 prohibitions and R5 mechanics reused wholesale; its authorization block is what makes this ADR necessary |
| `docs/ADR/ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md` | Item 4's Assignment-is-ride-progress boundary, respected not eroded |
| `docs/ADR/ADR-040-Assignment-Ride-Lifecycle.md` | Where a *revised*, en-route ETA would belong if it is ever wanted |
| `docs/ADR/ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md` | Part 2 forbidden inputs — unamended, reinforced |
| `docs/PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 3 | Reputation systems excluded — the source of prohibition 5.8 |
| `PIOS_PRODUCT_EVIDENCE.md` lines 80, 86–92 and the 2026-08-01 addendum | The gate this ADR does not amend, relax, or claim to satisfy |
| `docs/ADR/ADR-055-Session-Authentication-and-Password-Credential.md` line 20 | The prerequisite-route precedent, in form and in placement |
