# ADR-043: Owner Control Center — Observation Boundary

## Status

Accepted. Ratified by Product Owner 2026-08-02, together with ADR-044, after
the Self Architecture Review recorded in ADR-044's own revision history.

The *direction* (build the Оптимальный tier, skip Минимальный, do not build
the Operations context) was decided by the Product Owner on 2026-08-02. This
ADR is not that decision — it records the architectural consequences of it,
and in particular the shapes this surface is **forbidden** to take. It is
filed before implementation, per `MODULE_STRUCTURE.md` Section 8 and ADR-006,
not written afterwards to justify what already happened.

### Revision 2026-08-02 (pre-ratification critical review)

This ADR was revised before ratification, following a critical self-review
requested by the Product Owner. Three changes, all recorded here rather than
made silently:

1. **Decision 2 gains a binding constraint on how outbox backlog is
   measured.** The first draft said only *what* to report. Review of
   `PostgreSQLOutboxRepository.findUnpublished()` (dispatch, lines 52–70;
   identically in `order-management` and `driver-management`) showed it
   loads **every** unpublished row, with full payload, into memory. A health
   endpoint built on it would be slowest and heaviest exactly when the
   backlog is largest — that is, precisely in the condition it exists to
   detect. An aggregate query is now required.
2. **Two factual corrections.** `WebCorsConfiguration` exists in **six**
   modules, not five (`network-management` has one too, without the pilot
   origin). And ADR-044's revised mechanism introduces **no endpoint of its
   own**, which makes Decision 3's "only new API surface" claim stronger,
   not weaker.
3. No change to Decisions 1, 4, 5, 6, 7 or 8. The observation boundary
   itself survived review unchanged.

Companion documents:

- `docs/ADR/ADR-044-Owner-Authentication-Mechanism.md` — the authentication
  gate in front of this surface. Deliberately a separate ADR; see ADR-044's
  own "Why this is not part of ADR-043".
- `docs/PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` — screens, API shapes,
  implementation order. Design detail, not architectural decision.
- `docs/PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` — the Product Decision
  required by `PIOS_PILOT_OPERATION_PROTOCOL.md` Section 6. **Not yet
  written**; it is a prerequisite of implementation, not of this ADR.

## Context

### The Product Decision that authorises this at all

`docs/PIOS_PILOT_OPERATION_PROTOCOL.md` Section 6 (line 83) forbids adding,
during Pilot Operation and without a separate Product Decision,
*«Аналитику (историю, суммы, показатели бизнеса)»*. The Product Owner has
now made that decision directly. This ADR proceeds on that basis and does
not treat the protocol as a blocker — but the Product Decision must be
written down as its own document before code is written, or the repository
will contain a ratified prohibition and an implementation contradicting it.

### What the repository actually contains today

Verified by reading the code on 2026-08-02, not from memory of an earlier
session.

- **No observability endpoint exists anywhere.** A case-insensitive search
  of `backend/` for `actuator`, `micrometer`, `management.endpoint` and
  `health` returns zero matches. No module declares
  `spring-boot-starter-actuator`; `backend/dispatch/build.gradle.kts` lines
  21–61 lists web, jackson, kotlin-reflect, jdbc, postgresql, flyway, amqp,
  spring-retry and aop, and nothing else. Liveness is currently observable
  only by issuing an ordinary `GET /v1/...` and seeing whether anything
  answers.
- **The data needed for a human-readable event feed is partly absent from
  the database, not merely unexposed.**
  - `backend/driver-management/src/main/resources/db/migration/drivermanagement/V1__initial_schema.sql`
    lines 8–11: `drivers` has `id` and `availability` only (V3 later added
    `display_name`). **No registration timestamp exists.** "Сергей
    зарегистрировался в 20:44" is not reconstructible from any source.
  - `backend/dispatch/src/main/resources/db/migration/dispatch/V4__proposals.sql`
    lines 10–15: `proposals` has `id`, `order_reference`,
    `driver_reference`, `status`. **No timestamp of any kind.** "Артур
    получил заказ" and "Артур принял заказ" are not reconstructible.
  - `backend/dispatch/src/main/resources/db/migration/dispatch/V5__assignment_ride_lifecycle.sql`
    lines 9–10 added a single nullable `status_changed_at`, overwritten on
    each transition — the time of the *latest* transition, not a history.
  - `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt`
    lines 79–87 carry `createdAt` but nothing else temporal; `complete()`
    and `cancel()` (lines 98–118) change status without recording when.
- **A real, timestamped event history does exist — inside each module's own
  private database.** `dispatch/.../V3__outbox.sql` lines 16–24 defines
  `dispatch_outbox` with `event_type`, `aggregate_id`, `payload`,
  `created_at`, `published_at`; `order_management_outbox` and
  `driver_management_outbox` are structurally identical. It has no REST
  surface and is consumed only by that module's own relay (ADR-031,
  ADR-032).
- **Existing read endpoints are narrow by design.**
  `AssignmentController.listAssignments` (lines 124–134) rejects a call
  without `orderId`; `ProposalController.listProposals` (lines 175–190)
  requires exactly one of `orderId`/`driverId`. `GET /v1/orders`
  (`OrderQueryController` lines 34–49) and `GET /v1/drivers`
  (`DriverController` lines 96–101) are the only unparameterised listings
  in the platform.
- **`ProposalResponse` already carries `statedPrice`**
  (`ProposalController.toResponse`, line 193) — a monetary value is
  therefore already in a payload this surface will read.
- **All five pilot services are already reachable through one browser
  origin.** `frontend/vite.config.ts` lines 23–30 proxies `/v1/drivers`,
  `/v1/connections`, `/v1/orders`, `/v1/proposals`, `/v1/assignments` and
  `/v1/identities` to ports 8081–8084 and 8086, with `network-management`
  (8085) deliberately not routed. Its own comment states that module "is
  excluded from the pilot flow (ADR-037)".
- **CORS is configured identically in six modules and is narrow.**
  `driver-management/.../api/WebCorsConfiguration.kt` lines 34–40 map only
  `/v1/**`, permit only `GET` and `POST`, and allow the Vite dev origin
  plus `PIOS_PILOT_FRONTEND_ORIGIN`. Its own KDoc (lines 14–23) records
  that this replication is "a minimal operational necessity, not an
  architectural decision" — the precedent this ADR reuses for replicated
  edge configuration. `network-management` carries a sixth, slightly
  different copy (lines 14–24: dev origin only, no pilot origin),
  consistent with its exclusion from the pilot.
- **An outbox read seam already exists in the three modules that have an
  outbox.** `OutboxRepository.findUnpublished()` is declared in each
  module's own application layer and implemented over that module's own
  table (`dispatch/.../PostgreSQLOutboxRepository.kt` lines 52–70). It
  returns every unpublished row, with payload, ordered by id — built for a
  relay that intends to publish them all, not for a cheap measurement.

### The shape this must not take

The obvious implementation of a business-owner dashboard is a service that
holds connections to all five databases and answers one aggregate query.
That shape is forbidden by ADR-005 and ADR-009 and would make the isolation
this repository has maintained through forty-two ADRs immediately false. It
is not forbidden anywhere *specifically enough to stop someone reaching for
it under time pressure*, which is the main reason this ADR exists: to
foreclose it in writing before the convenience of it is felt.

## Problem

Where does a cross-context, read-only operational view of PIOS live, given
that no module may read another module's data, no module may hold a copy of
another module's data, no new bounded context is being ratified, and the
view must correlate facts owned by five different modules?

## Decision

### 1. The Owner Control Center is a browser-side read-only aggregation. No aggregating backend component is created.

Correlation across contexts happens in the operator's browser, from
responses to ordinary public reads, and is never persisted anywhere.
Concretely, the following are all rejected and none may be introduced as an
implementation detail of this MVP:

- a new module, service, or deployable of any kind;
- a backend-for-frontend, gateway, or aggregation endpoint that fans out to
  other modules;
- any module reading another module's database, directly or through a
  replica;
- any module calling another module synchronously to answer a Control
  Center request;
- any new event, event version, or cross-module contract.

The browser holds an `orderId` obtained from Order Management and asks
Dispatch about it. That is the reference-not-ownership rule (ADR-005,
ADR-019; first stated for `network-management` in ADR-037) applied at the
presentation layer: a plain string crosses, nothing else, and neither module
learns that the other exists. `INTERFACE_CONTRACTS.md` Section 3's set of
justified cross-module relationships is unchanged by this ADR — no
relationship is added.

**Why the browser rather than a server.** The correlation this surface
performs — "these orders, those proposals, that driver" — is a *view*
concern with no invariant, no lifecycle, and no authority. A server-side
aggregator would have to hold, however briefly, a joined representation of
five contexts' data, which is precisely the thing ADR-005 and ADR-009 exist
to prevent. Doing it in the browser makes the join structurally incapable of
being persisted, queried by anything else, or mistaken for a source of
truth.

### 2. Each module reports only on itself. A new `GET /v1/health` is added to each of the five pilot modules.

Shape and semantics:

- Reports **only that module's own state**: whether its own process is
  serving, whether its own datasource answers, and — for the three modules
  that have an outbox (`order-management`, `driver-management`, `dispatch`)
  — the number of its own unpublished outbox rows and the age of its own
  oldest one.
- Never reports on another module. Never calls another module. A module
  that cannot reach RabbitMQ says so about *its own* publication backlog,
  which it can see in its own table (`published_at IS NULL`), and says
  nothing about the broker or about anyone else's queues.
- Is measured with an **aggregate query**, never by loading rows. The
  existing `OutboxRepository.findUnpublished()` must **not** be used for
  this: it materialises every unpublished record with its payload, so a
  health endpoint built on it degrades in proportion to the backlog it is
  reporting — slowest and heaviest precisely in the condition it exists to
  detect, and capable of turning a publication delay into a second,
  self-inflicted failure. The count and the oldest timestamp are obtained
  in one `SELECT count(*), min(created_at) ... WHERE published_at IS NULL`
  against that module's own outbox table, exposed as a new method on the
  module's own already-existing `OutboxRepository` interface. The seam is
  reused; the method is not.
- Is served under `/v1/` and not through Spring Boot Actuator. Two reasons,
  both concrete. First, `/v1/**` is the only path mapped by the six
  existing `WebCorsConfiguration` classes (line 36), so an actuator path
  would be blocked from the browser or would require editing files on the
  live driver-and-passenger request path. Second, and more important,
  Actuator brings an entire management surface into a platform that has had
  no authentication at all until ADR-044; adding `env`, `beans`,
  `configprops` and `mappings` to a product in that state is a security
  regression obtained in exchange for convenience. A hand-written endpoint
  exposes exactly the four facts named above and nothing else, which is
  ADR-011's Least Privilege applied to an endpoint rather than asserted
  about one.

This is the mechanism by which the Product Owner's constraint — that the
operator must never need to read a log, open a terminal, or inspect
RabbitMQ — is met for stuck-queue conditions specifically. The outbox
backlog is the only cross-service failure signal reachable without those
tools, and it is reachable because each module can see its own table.

`network-management` (8085) receives no health endpoint and appears nowhere
on this surface. It is isolated (ADR-037), absent from the pilot proxy
(`vite.config.ts` line 19–22's own comment), and participates in no user
scenario. Adding an endpoint to it for a dashboard would consume its
isolation for no operational benefit.

### 3. Existing endpoint contracts are not loosened. The client fans out instead.

`GET /v1/assignments` keeps its required `orderId`;
`GET /v1/proposals` keeps its exactly-one-of `orderId`/`driverId`. The
Control Center issues one call per driver and one per order of interest.

This was the one place the earlier three-tier analysis proposed widening a
public contract, and it is rejected on reflection. Making those parameters
optional converts "you must already know an identifier" into "anyone may
enumerate everything", which is a real increase in exposure of a surface
that ADR-044 explicitly leaves unauthenticated. The cost of the alternative
is bounded and small: with the pilot's three to eight drivers and its daily
order volume, the fan-out is a few dozen small reads per refresh. When the
pilot outgrows that, widening the contract becomes a decision made on its
own merits rather than as a side effect of building a dashboard.

Consequently the **only new API surface introduced by this entire MVP** is
the five `GET /v1/health` endpoints. ADR-044's revised mechanism adds no
endpoint of its own — the credential is checked on the health endpoint
itself, and the login screen establishes a session by calling it. Every
other endpoint in the platform keeps its current contract, unchanged.

### 4. Missing event timestamps are added as optional, nullable columns alongside existing state — never replacing it.

- `drivers.created_at` (nullable), surfaced as `registeredAt` on
  `GET /v1/drivers`.
- `proposals.created_at` and `proposals.responded_at` (both nullable),
  surfaced on `GET /v1/proposals`.
- `assignments.arrived_at`, `assignments.started_at`,
  `assignments.completed_at` (all nullable), surfaced on
  `GET /v1/assignments`.

These are additive optional attributes on existing aggregates, following the
precedent of `destination` on `Order` (Sprint 3B), `passengerName`/
`createdAt` (`V7__add_passenger_name_and_created_at.sql`) and
`Assignment.statusChangedAt` (ADR-040, `V5__assignment_ride_lifecycle.sql`).
They establish no cross-module relationship, change no invariant, and break
no caller: every existing row satisfies the new columns without a default,
and every existing client ignores unknown response fields.

**Binding constraint on the implementation:** `assignments.status_changed_at`
is **kept**, not replaced. The new columns sit beside it. Replacing it would
alter the model ADR-040 ratified and would require its own ADR; adding
beside it does not. A Developer task under this ADR may not remove or
repurpose that column.

**Disclosed limitation, not to be papered over:** every record created
before these migrations — including Артур's own registration and every order
placed to date — has `NULL` in the new columns permanently. The event feed
is complete only from the deployment forward. The UI must render a missing
timestamp as an absent entry, never as a guessed or back-filled one.

### 5. This surface is read-only, permanently, and that is what keeps it outside ADR-002.

The Control Center calls no endpoint that changes state. It has no button
that assigns, cancels, completes, proposes, changes availability, restarts
anything, or edits anything. It expresses no matching criterion, no pricing
rule, no dispatch policy — so ADR-002's exclusion of business rules from
architectural scope is not approached, let alone crossed.

This is a decision about the surface's nature, not a description of its
first version. A future capability to *act* from this screen is not a
refinement of ADR-043; it is a different thing, requiring its own Product
Decision (who may act, on whose behalf, with what authority) and its own
ADR.

### 6. No money appears on this surface, anywhere, in any tier.

ADR-042 R4.3 forbids amounts being "compared across proposals, orders,
drivers, or time; never sorted or ranked; never summed, averaged, or
otherwise aggregated", and names this "the single most important prohibition
in this ADR".

Because `ProposalResponse` already carries `statedPrice` (line 193), the
constraint is concrete rather than theoretical: the Control Center will
receive that field in every proposal it reads. It must **discard it on
receipt** — not render it, not total it, not display it per-ride, not
include it in the diagnostic report. No revenue figure, no average, no
"заработано сегодня", no chart of anything monetary, now or in any later
iteration of this surface without ADR-042 itself being revisited by the
Product Owner.

### 7. The "Сегодня" counters are operational observation, bounded so that they do not become analytics.

Permitted: counts of drivers registered, drivers currently available, and
orders created today by their current status. These answer "is my business
working right now", which is the question the surface exists for.

Not permitted under this ADR: history beyond the current day, trends,
comparisons between days or drivers, rankings, per-driver performance
figures, retention or conversion measures, or any chart. Those are the
"аналитика (история, суммы, показатели бизнеса)" that
`PIOS_PILOT_OPERATION_PROTOCOL.md` Section 6 defers, and this ADR does not
discharge that deferral — it stays in force for everything beyond the
current-day counters named here.

**Disclosed measurement limitation.** Because `Order` records no completion
time (Decision 4 adds completion time to `Assignment`, not to `Order`),
"orders today" means *orders created today*, classified by their current
status. An order created yesterday and completed today is not counted. The
label on the screen must say what is actually being counted; it may not say
"поездок завершено сегодня" while counting something else.

### 8. The Operations bounded context is not created, and this ADR is what stands between now and creating it accidentally.

No `operations`, `observability`, `monitoring`, `reporting` or `analytics`
module is scaffolded. If PIOS later needs correlated cross-context
diagnostics, event-stream-backed timelines, incident history, or
notification delivery while no browser is open, the correct shape is an
event-consuming module owning no domain data — structurally what ADR-033
already defines for Notifications — and it requires ratification of a new
bounded context under `MODULE_STRUCTURE.md` Section 8 (line 116) *before*
scaffolding, plus a decision on the cross-context correlation mechanism that
ADR-012 (line 17) explicitly left to a later ADR, plus a Product Decision on
retaining passenger names and addresses in a new store.

None of that is authorised here, and no part of this MVP may be built in a
way that presumes it.

## Alternatives Considered

- **A dashboard service that reads the five databases directly.** Rejected
  outright: it violates ADR-005 and ADR-009 in the most direct way
  available, and would make the module isolation asserted throughout this
  repository false in practice while remaining true on paper. Named
  explicitly here because it is the shortcut most likely to be reached for.
- **A backend-for-frontend that fans out over HTTP to the five modules.**
  Rejected: less severe than reading databases, but it still creates a
  component whose reason to exist is holding a joined view of five
  contexts, it makes every module a runtime dependency of one new service,
  and it requires ratifying a new bounded context (Section 8) for a view
  concern with no invariant of its own. The browser already fans out today
  (`Coordinator.tsx` lines 11–16 and 103–140 call three modules from one
  page); this decision extends an existing, working pattern instead of
  introducing a new component.
- **Spring Boot Actuator for health.** Rejected — Decision 2. Convenience
  bought at the price of exposing a broad management surface on a platform
  with no authentication, plus a CORS change to files on the live pilot
  path.
- **Exposing the outbox tables as an events API.** Attractive, because they
  are the one place a true chronological history already exists with real
  timestamps. Rejected: it would turn an internal integration mechanism
  into a public read contract, and the outbox `payload` is an event
  representation whose schema is explicitly still an open decision
  (ADR-030). Adding nullable timestamp columns to the aggregates (Decision
  4) obtains the same feed from data the domain already owns, with no new
  contract over an internal mechanism.
- **Loosening `/v1/proposals` and `/v1/assignments` into full listings.**
  Rejected — Decision 3.
- **Building the Минимальный tier first as a separate step.** Considered
  and recommended in the prior three-tier analysis; overruled by the
  Product Owner on 2026-08-02 in favour of going straight to Оптимальный.
  Recorded here because the reasoning for it — that a frontend-only tier
  touches no backend and therefore carries no risk to Артур's live work —
  becomes a *risk* of the chosen path rather than disappearing. It is
  carried into the design document's implementation order (migrations last,
  outside working hours) rather than being lost.

## Consequences

### Positive

- PIOS gains an operational view without gaining a component that knows
  about more than one bounded context. No module learns anything about
  another; no ownership moves; `INTERFACE_CONTRACTS.md` Section 3 is
  unchanged.
- The five `/v1/health` endpoints give ADR-012's "record of what the domain
  did / measure of how it is behaving" principle its first concrete
  implementation in this codebase, per-domain and self-reported, which is
  the form ADR-012 requires.
- The stuck-outbox condition — previously visible only in logs or in
  RabbitMQ — becomes visible to a non-technical operator, which is the
  specific constraint the Product Owner set.
- The wrong future shape is now forbidden in writing, cheaply, before
  anyone is under pressure to build it.

### Negative, and disclosed rather than argued away

- The event feed is complete only from deployment forward. Everything that
  has already happened in the pilot stays untimed, permanently (Decision 4).
- The Control Center depends on five separate reads succeeding. A partial
  failure produces a partially-populated screen, and the design must make
  "I could not reach this" visually distinct from "this is zero" — a
  correctness requirement, not a polish one.
- Client-side fan-out means request volume grows with drivers × orders.
  Fine at pilot scale, and a known ceiling rather than an unbounded one.
- `Order` still has no completion timestamp, so the "today" counters carry
  the measurement caveat in Decision 7. This ADR chooses to disclose that
  rather than add a column to `Order` for a dashboard's convenience.
- Three databases receive a migration while the platform is in live
  operation with a real driver. Mitigated by ordering, not eliminated.
- ADR-012's tracing requirement remains undischarged: a single interaction
  still cannot be followed across modules by any identifier. This MVP does
  not address it and does not pretend to.

## Traceability

| Subject | Source |
| --- | --- |
| Authority to add this capability at all | `PIOS_PILOT_OPERATION_PROTOCOL.md` Section 6 (line 83); Product Owner decision 2026-08-02 |
| No module reads or copies another's data | ADR-005, ADR-009, ADR-019; ADR-037 (rule first applied to a new module) |
| No server-to-server call on a request path | ADR-027; ADR-042 R2's own application of it |
| New bounded context requires prior ratification | `MODULE_STRUCTURE.md` Section 8 (line 116); ADR-017, ADR-018 |
| Per-domain self-reported observability | ADR-012 (line 17) |
| Endpoint exposes only what is needed | ADR-011 (Least Privilege, lines 19–23) |
| No money displayed, summed or compared | ADR-042 R4.3 |
| Additive nullable attribute precedent | Sprint 3B (`destination` on `Order`); ADR-040 (`statusChangedAt`); `V7__add_passenger_name_and_created_at.sql` |
| `status_changed_at` preserved | ADR-040 |
| Replicated edge configuration precedent | `WebCorsConfiguration` KDoc (lines 14–23), replicated in five modules |
| No business rule invented | ADR-002 |
| Authentication of this surface | ADR-044 |

## Files Changed

This ADR only (`docs/ADR/ADR-043-Owner-Control-Center-Observation-Boundary.md`,
new). No source file is touched by this document.

Prerequisites before any Developer task starts under this ADR:
`docs/PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` (not yet written),
ratification of this ADR and ADR-044 by the Product Owner, and the
`API_SPECIFICATION.md` / `INTERFACE_CONTRACTS.md` entries for the new
`/v1/health` endpoints — documentation before implementation, per ADR-006
and `CLAUDE.md`.
