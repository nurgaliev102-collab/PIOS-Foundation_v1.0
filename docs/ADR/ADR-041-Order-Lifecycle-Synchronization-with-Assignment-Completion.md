# ADR-041: Order Lifecycle Synchronization with Assignment Completion

## Status

Accepted.

Amends ADR-040 (Assignment Ride Lifecycle) in exactly one point: its own
"Decision" item 5. Items 1, 2, 3, 4 and 6 of ADR-040 remain in force
unchanged. ADR-040 is not superseded as a whole.

**Amendment 2026-07-30 — Product Owner decision recorded.** All four items
originally filed under "Open Questions (Product Decision Required —
ADR-002)" have been answered by the Product Owner and are now closed. The
original question text is preserved verbatim below; each carries a
`Resolved` note, and the full ruling is recorded in the new section
"Product Decision (Product Owner, 2026-07-30)". Nothing in the Decision
section is reversed by that ruling — the conditional branches this ADR had
kept open are now closed in favour of the option already implemented.

## Context

Sprint 3 ("Order Consistency") addresses a state gap that ADR-040 created
deliberately and recorded as deferred rather than dropped.

The concrete evidence in the repository today:

- **Dispatch already publishes the completion fact.**
  `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt`
  lines 205–214 write an outbox record for `AssignmentCompleted` with
  `routingKey = "assignment.completed"`, inside the same
  `transactionRunner.run` boundary as the aggregate write
  (`completeAssignment`, lines 174–181). The event carries
  `payload.orderId` and `payload.driverId` only.
- **Order Management already has the consumer infrastructure, but is not
  subscribed to that routing key.**
  `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/RabbitMQConsumerTopologyConfiguration.kt`
  declares a durable queue `order-management.from-dispatch` (line 80), a
  dead-letter queue `order-management.from-dispatch.dlq` (line 81), and a
  single binding whose KDoc states: *"Binds only the already-ratified
  routing key for AssignmentAccepted (INTERFACE_CONTRACTS.md Section 5) --
  never a wildcard, since this module is a specific, named consumer"*
  (lines 63–76). `ASSIGNMENT_ACCEPTED_ROUTING_KEY = "assignment.accepted"`
  (line 82) is the only key bound. `AssignmentCompleted` is therefore
  published and never delivered to Order Management.
- **The existing consumer deliberately does not transition the Order.**
  `application/OrderAssignmentRecognitionHandler.kt` lines 11–19: *"Order
  Management's own `OrderStatus` (SUBMITTED, COMPLETED, CANCELLED) does not
  model an 'assigned' state ... This handler therefore only recognizes the
  fact that an assignment occurred ...; it does not transition the Order
  aggregate, which has no corresponding state to transition to."*
- **Idempotent consumption is already solved in this module.**
  `application/AssignmentAcceptedProjectionApplicationService.kt` lines
  43–48 mark the envelope's `eventId` through
  `AssignmentAcceptedRepository.markProcessed` and skip the business effect
  when the id was already recorded; `persistence/PostgreSQLAssignmentAcceptedRepository.kt`
  lines 29–35 back that guarantee with `INSERT ... ON CONFLICT (event_id)
  DO NOTHING` against `order_management_processed_events`.
- **`Order.complete()` exists but is unreachable.**
  `domain/Order.kt` lines 85–91 implement `complete()` with the guard
  `check(status == OrderStatus.SUBMITTED)`, and
  `application/OrderLifecycleApplicationService.kt` lines 74–94 expose
  `completeOrder` in both caller-supplied and self-fetching forms, writing
  the `OrderCompleted` outbox record in the same transaction (lines
  130–135). But `order-management/.../api/` contains only
  `OrderSubmissionController` (`@RequestMapping("/v1/orders")` +
  `@PostMapping`) and `OrderQueryController` (`@RequestMapping("/v1/orders")`
  + `@GetMapping`). There is no complete endpoint, no cancel endpoint, and
  no consumer path that reaches `completeOrder`. **No Order in a running
  PIOS deployment can currently leave `SUBMITTED`.**
- **ADR-040 explicitly left this open.** ADR-040 "Decision" item 5:
  *"`order-management`'s own `Order.status` is **not** touched by this ADR.
  A completed ride does not (yet) mark its Order `COMPLETED` — that would
  require Dispatch → Order Management event propagation ..., which is real,
  additional infrastructure work this sprint's own acceptance criterion ...
  does not require. Tracked as deferred, not silently dropped."*

Supporting ratified documentation, unchanged by this ADR and used here as
the justification that the transition itself is already sanctioned:

- `DOMAIN_MODEL.md` Section 12: *"**Order.** Submitted, then either assigned
  by Dispatch and carried through acceptance, the ride itself, and
  completion, or cancelled at any point before completion."*
- `DOMAIN_MODEL.md` Section 12: *"**Assignment.** Made by Dispatch in
  connection with an order, then accepted; it stands until its order is
  completed or cancelled."*
- `INTERFACE_CONTRACTS.md` Section 5, Contract: Dispatch → Order
  Management: *"Purpose. Make an order's assignment outcome known so Order
  Management can track the order's status."*
- `INTERFACE_CONTRACTS.md` Section 4: Order Management's owned
  responsibility is *"An order's lifecycle from submission through
  completion or cancellation"*; Dispatch's list of *"Operations it does not
  own"* names *"An order's own status (Order Management)"*.

So the missing piece is not authority (Order Management already owns
`Order.status`, and already owns the transition) and not the event (Dispatch
already publishes it). The missing piece is the subscription and the
consumer path — plus a ratified decision on what the trigger *means*, which
is what ADR-040 item 5 withheld and this ADR now supplies.

One business question sits directly on top of this and is **not** answered
here (ADR-002: dispatch policy, pricing, commission, matching and
regulatory rules are outside architectural scope): whether the driver's own
"ride finished" action is by itself sufficient to consider the passenger's
order fulfilled, or whether passenger confirmation, a timeout, or an
exception path must intervene. See "Open Questions" below. The decision
recorded here is deliberately shaped so that it stays correct under either
answer.

> **Amendment 2026-07-30.** The paragraph above is preserved as written at
> the time of drafting, when the question was genuinely open. It has since
> been answered by the Product Owner: for the PIOS MVP the driver's own
> completion of the ride *is* sufficient. The paragraph is now historical
> context for why the design was shaped to survive either answer; it is no
> longer an outstanding question. See "Product Decision (Product Owner,
> 2026-07-30)".

## Decision

1. **`AssignmentCompleted` is the single trigger that completes an Order.**
   Order Management subscribes to Dispatch's `assignment.completed` routing
   key and, on receiving `AssignmentCompleted` v1 for an order in status
   `SUBMITTED`, transitions that Order to `COMPLETED` through its own
   already-existing `OrderLifecycleApplicationService.completeOrder`,
   emitting the already-existing `OrderCompleted` outbox record in the same
   transaction. No second trigger is introduced: **no `POST
   /v1/orders/{id}/complete` endpoint is added by this ADR.** Two
   independent authorities over the same terminal transition would make
   "what completed this order" unanswerable; if an operator-initiated
   completion is ever needed, it requires its own decision about who may
   invoke it and when, which is a product decision, not an architectural
   one.

   > **Amendment 2026-07-30 (Product Owner decision, Open Question 1
   > resolved).** This item was written to be trigger-agnostic: Open
   > Question 1 reserved the possibility that `AssignmentCompleted` alone
   > might be judged insufficient, in which case "only the trigger changes"
   > and the consumer would record the completion fact and defer
   > `Order.complete()` to a further signal. **That branch is now closed
   > and does not apply.** The Product Owner has ratified that within the
   > PIOS MVP, `AssignmentCompleted` is by itself a sufficient business
   > fact for `Order.status → COMPLETED`. Item 1 therefore stands as
   > written, unconditionally, and is no longer contingent on a pending
   > answer. Passenger confirmation, settlement, disputes and any other
   > additional precondition would each require their own new ADR; they may
   > not be introduced as an implementation detail of this one.

2. **`Order.status` remains the single carrier of an Order's completion
   state.** Order Management does not gain a mirrored assignment status
   field, a ride-state column, a completion timestamp copied from Dispatch,
   or any other projection of Dispatch-owned data (ADR-005, ADR-019, and
   the reference-not-ownership rule). What crosses the boundary is a plain
   `orderId` string in the event payload, resolved locally through
   `OrderRepository.findById` — never a foreign key and never a
   server-to-server call on the write path (ADR-027). The
   `order_management_processed_events` ledger continues to hold event ids
   only; it is an idempotency device, not a second state store.
   Correspondingly, `AssignmentStatus.COMPLETED` in Dispatch remains the
   authority on the *ride*, and `OrderStatus.COMPLETED` in Order Management
   remains the authority on the *order*. They are two different facts owned
   by two different modules that happen to be causally linked, not one fact
   stored twice.

3. **Cancellation is unchanged by this ADR, and the two paths are
   reconciled defensively rather than by a new rule.**
   - `Order.cancel()` (`domain/Order.kt` lines 99–105) remains a command
     inside Order Management's own boundary. This ADR creates no
     Dispatch → Order Management cancellation flow, because Dispatch has no
     cancellation capability to propagate: `Assignment.kt` lines 12–16
     state that *"Cancellation of an assignment (DOMAIN_MODEL.md Section 6,
     'Cancellation') is a separate capability and is explicitly out of this
     task's scope"*, and no `AssignmentCancelled` event exists.
   - The reverse direction — an Order cancelled while its Assignment is
     still live — is likewise not resolved here; nothing in this ADR
     notifies Dispatch of a cancellation.
   - **Conflict handling (technical, not a business rule):** when
     `AssignmentCompleted` arrives for an Order whose status is not
     `SUBMITTED`, the consumer must **not** call `Order.complete()` (it
     would throw by design, `Order.kt` line 86) and must **not** overwrite
     the existing status. The message is treated as *handled* — the
     `eventId` is recorded in the idempotency ledger and the message is
     acknowledged — and the conflict is recorded as an observable anomaly
     (log at warning level with orderId, eventId and the observed status;
     ADR-012). Rationale: this is a business conflict, not a transient
     failure, so retry-then-dead-letter would only defer it forever.
     Specifically:
     - already `COMPLETED` → no-op (a redelivery, or a second assignment
       for the same order); the outcome is already what the event asks for.
     - `CANCELLED` → no transition, anomaly recorded. Whether a completed
       ride should override a prior cancellation is a business question
       (Open Question 3).

       > **Amendment 2026-07-30 (Product Owner decision, Open Question 3
       > resolved).** Confirmed as final, not provisional. A `CANCELLED`
       > Order that receives `AssignmentCompleted` stays `CANCELLED`; there
       > is no automatic correction. The anomaly is recorded (log/metric),
       > and the `eventId` **is** written to the idempotency ledger anyway,
       > so that a redelivery of the same event does not re-evaluate it.
       > The "if the product answer is 'the completed ride wins'" branch
       > named in Open Question 3 is closed and does not apply.

   - **Distinguish anomaly from failure.** An event referencing an
     `orderId` that does not exist in this module's database is *not* a
     business conflict — it is a data or integration fault. It must
     propagate the exception, so the existing bounded retry and dead-letter
     policy (`RabbitMQListenerContainerConfiguration`, 3 attempts then
     `order-management.from-dispatch.dlq`) applies, exactly as it does for
     `AssignmentAccepted` today.

   > **Amendment 2026-07-30 (Product Owner decision, Open Question 2
   > resolved): cancellation after ride start is out of Sprint 3.** No
   > `IN_PROGRESS → CANCELLED` path, implicit or explicit, is introduced by
   > this ADR or by its implementation. Cancellation behaviour stays exactly
   > as it is today, and the reconciliation described in this item remains
   > defensive only. See the Product Decision section for the Product
   > Owner's own reasoning.

4. **Ride-progress states still do not move to Order.** `OrderStatus`
   remains `SUBMITTED / COMPLETED / CANCELLED`. `ARRIVED`, `IN_PROGRESS`
   and the rest stay exclusively on `Assignment` in `dispatch`, per ADR-040
   items 1–4 and `Order.kt`'s own KDoc (lines 10–14: *"The assigned,
   accepted, and ride states ... remain outside this scope; they are not
   modeled here"*). Concretely, Order Management binds **only**
   `assignment.completed` in addition to the existing
   `assignment.accepted`; it does **not** bind `assignment.arrived` or
   `assignment.started`. ADR-040 item 5 is amended solely to permit the
   `SUBMITTED → COMPLETED` transition on receipt of `AssignmentCompleted`;
   nothing else in ADR-040 changes.

5. **Consumer topology: a second queue, not a second binding on the
   existing one.** The new subscription gets its own durable queue and its
   own dead-letter queue, bound to `dispatch.events` with
   `assignment.completed`, and its own listener class, reusing the existing
   `rabbitListenerContainerFactory` retry/DLQ advice unchanged. Adding
   `assignment.completed` as a second binding to the existing
   `order-management.from-dispatch` queue is explicitly rejected:
   `AssignmentAcceptedListener.onMessage` (lines 55–58) hard-requires
   `eventType == "AssignmentAccepted"` and throws otherwise, so a shared
   queue would dead-letter every completion event until that listener were
   rewritten into a type dispatcher — a larger change to a working,
   already-verified path, and one that would couple the failure of one
   event type's handling to the other's. Two independent queues also keep
   the two subscriptions independently observable and independently
   drainable.

6. **Transactional and idempotency constraints on the implementation.**
   Idempotency mark, Order transition, and `OrderCompleted` outbox write
   occur inside one transaction (ADR-032) — the same shape already proven
   by `AssignmentAcceptedProjectionApplicationService`. The transition must
   go through `OrderLifecycleApplicationService.completeOrder`, never
   through direct aggregate or repository manipulation, precisely because
   that service is what writes the `OrderCompleted` outbox record; a
   completion that does not emit `OrderCompleted` would silently break
   `EVENT_CATALOG.md` Section 5. The consumer never publishes a
   Dispatch-owned event and never writes to Dispatch's database.

## Open Questions (Product Decision Required — ADR-002) — ALL RESOLVED 2026-07-30

**Status of this section: closed.** All four questions were answered by the
Product Owner on 2026-07-30. The original question text below is preserved
exactly as it was written while the questions were open (`CLAUDE.md`:
"Never Delete Documentation"); each now carries a `Resolved` note stating
the ruling. The consolidated ruling, including the Product Owner's own
verbatim wording, is in the next section.

These are business rules. They are recorded here, unanswered, rather than
assumed. None of them blocks the decision above; each of them, when
answered, changes exactly one identified place in the design.

1. **Is the driver's completion of a ride sufficient to consider the order
   fulfilled?** Alternatives include passenger confirmation, a settlement
   step, or a timeout. If the answer turns out to be "not sufficient", the
   change is confined to Decision item 1's trigger: the consumer would
   record the completion fact and defer the `Order.complete()` call to
   whatever additional signal is ratified. Nothing in items 2–6 changes,
   because they concern ownership, topology and transactionality, not the
   trigger's sufficiency.

   **Resolved — Product Owner decision, 2026-07-30: YES, sufficient.**
   Within the PIOS MVP the driver's completion of the ride is by itself a
   sufficient business fact to complete the Order. The "not sufficient"
   branch above does not apply and the trigger in Decision item 1 stands
   unconditionally. Any later precondition (payment, rating, dispute,
   passenger confirmation) requires its own separate ADR.

2. **May a passenger cancel an order after the ride has started?**
   `Order.cancel()` today accepts any `SUBMITTED` Order regardless of the
   connected Assignment's ride state, and the two modules cannot see each
   other's status by design. `DOMAIN_MODEL.md` Section 11 states only *"A
   cancellation may occur only before an order or assignment is
   completed"*, which does not distinguish "before the ride started" from
   "during the ride". Answering this may require a Dispatch-side capability
   (assignment cancellation) that does not exist yet.

   **Resolved — Product Owner decision, 2026-07-30: unchanged in Sprint 3.**
   No ride-cancellation model exists, and none is to be created here. A
   hidden `IN_PROGRESS → CANCELLED` transition must not be introduced
   without first deciding who initiates it, who pays, what happens to the
   driver, and who bears liability. Current behaviour is left exactly as
   it is. This is a deferral with a stated reason, not an answered rule:
   the underlying business question remains open beyond Sprint 3 and will
   need its own Product Decision plus, if it changes architecture, its own
   ADR.

3. **When a cancelled Order receives `AssignmentCompleted`, which fact
   wins?** Decision item 3 chooses the only option that invents no rule:
   keep the existing status, record the anomaly, acknowledge the message.
   If the product answer is "the completed ride wins" (or "a human
   reconciles it"), the change is confined to the single non-`SUBMITTED`
   branch identified in item 3.

   **Resolved — Product Owner decision, 2026-07-30: the existing status
   wins; the proposed technical handling is confirmed as final.** The Order
   stays `CANCELLED`, there is no automatic correction, the anomaly is
   recorded (log/metric), and the idempotency-ledger row is written anyway
   so that a redelivery does not re-process the same event. Decision item 3
   is now a ratified rule rather than a provisional default.

4. **Does anything downstream need to distinguish an order completed by a
   ride from an order completed some other way?** This ADR keeps
   `OrderCompleted`'s existing v1 payload (`{orderId}`) unchanged, so no
   provenance is carried. Adding provenance later is an event-versioning
   exercise under ADR-030, not a change to this decision.

   **Resolved — Product Owner decision, 2026-07-30: not now.** The
   `OrderCompleted` payload is not extended. It remains v1 `{orderId}`.

## Product Decision (Product Owner, 2026-07-30)

Authority: Product Owner. Scope: the four questions filed above under "Open
Questions (Product Decision Required — ADR-002)". This section records a
business ruling, not an architectural one; it was made by the Product
Owner, not by the architecture role, in keeping with ADR-002 (pricing,
commission, matching and regulatory rules are outside architectural scope).
It adds no new architectural decision — it closes the branches this ADR had
deliberately left open.

The Product Owner's own formulation, recorded verbatim as requested:

```
Decision:

В рамках PIOS MVP событие AssignmentCompleted является достаточным
бизнес-фактом для перехода Order.status в COMPLETED.

Подтверждение пассажира, финансовое закрытие,
споры и альтернативные причины завершения находятся вне Sprint 3.
```

### Q1 — Is driver completion sufficient to fulfil the order? **Yes.**

In the MVP, the fact that the driver completed the ride is sufficient
grounds to complete the order. Product Owner's reasoning, verbatim:

> Да. В MVP факт завершения поездки водителем является достаточным
> основанием для завершения заказа. Обоснование: PIOS сейчас не платёжная
> система, нет подтверждения пассажиром, нет расчёта стоимости, нет
> механизма споров. Добавление подтверждения пассажира сейчас было бы
> созданием нового бизнес-процесса без доказательств необходимости. Если
> позже появятся оплата, рейтинг, спор или подтверждение клиента —
> потребуется отдельный ADR.

Effect on this ADR: Decision item 1 stands unconditionally. Its
trigger-agnostic hedge ("if insufficient, only the trigger changes") is
retained as drafted for the record but no longer describes a live
possibility. **A future payment, rating, dispute or passenger-confirmation
capability would require its own ADR** — it may not be added as an
implementation detail under ADR-041.

### Q2 — Cancellation after the ride has started? **Not changed in Sprint 3.**

> Не менять в Sprint 3. Полноценной модели отмены поездки сейчас нет, и не
> нужно создавать скрытый переход IN_PROGRESS → CANCELLED без решения, кто
> инициатор, кто оплачивает, что происходит с водителем, кто несёт
> ответственность. Оставить как есть.

Effect on this ADR: none on Decision items 1–6; the existing behaviour is
explicitly ratified as the Sprint 3 behaviour. No `IN_PROGRESS → CANCELLED`
path is created, and Dispatch gains no cancellation capability. Note that
this closes the question **for Sprint 3**; the underlying business question
(who initiates, who pays, driver treatment, liability) is deferred, not
answered, and remains a prerequisite for ever exposing a cancel endpoint
(see Consequences, last bullet on `CANCELLED` reachability).

### Q3 — `AssignmentCompleted` for a `CANCELLED` Order? **Existing status wins.**

The technical handling proposed in Decision item 3 is confirmed as final:

> Order остаётся CANCELLED, автоматического исправления нет, аномалия
> фиксируется (лог/метрика), запись в таблице идемпотентности всё равно
> делается, чтобы повторная доставка не пересчитывала это же событие
> заново.

Effect on this ADR: Decision item 3's non-`SUBMITTED` branch is promoted
from "the only option that invents no rule" to a ratified rule. Two
constraints binding on the implementation follow explicitly: (a) the
anomaly must be observable — log at warning level with `orderId`, `eventId`
and observed status, and/or a metric (ADR-012); (b) the idempotency-ledger
write happens even in the conflict branch, so a redelivery is not
re-evaluated.

### Q4 — Distinguish completion causes in the payload? **Not now.**

`OrderCompleted`'s payload is not extended. It stays v1 `{orderId}`. Adding
provenance later remains an event-versioning exercise under ADR-030.

### What this Product Decision does not authorize

Payment, cost calculation, ratings, dispute handling, passenger
confirmation, ride cancellation semantics, and alternative completion
causes are all explicitly outside Sprint 3. None of them may be introduced
under ADR-041; each needs its own Product Decision and, where it touches
architecture, its own ADR.

## Consequences

- Order Management gains a second inbound subscription and stops being a
  module whose orders can never leave `SUBMITTED`. `Order.complete()` and
  `OrderLifecycleApplicationService.completeOrder`, both already written,
  tested and unreachable, become reachable without being modified.
- ADR-040's "deferred, not silently dropped" item is discharged. ADR-040's
  own text is preserved verbatim; a pointer to this ADR is added to it
  (`CLAUDE.md`: "Never Delete Documentation").
- `AssignmentCompleted` becomes a cross-domain business event under
  ADR-003, since a second domain now relies on it. Ratified documentation
  must record that: `EVENT_CATALOG.md` Section 6 (Dispatch domain events)
  and Section 9 (cross-domain events) currently list neither
  `AssignmentCompleted` nor `AssignmentArrived`/`AssignmentStarted` at all,
  and `INTERFACE_CONTRACTS.md` Section 5's "Contract: Dispatch → Order
  Management" names only `OrderAssigned` and `AssignmentAccepted`, with
  Section 7's event table matching. This is the same documentation-sync
  debt ADR-040 already left behind for the three ride events; it is
  assigned to Sprint 3's Documentation Hygiene item and is a prerequisite
  for closing that item, not optional cleanup.
- The frontend's passenger-side view can, for the first time, show an order
  reaching a terminal state without polling Dispatch. This ADR does not
  require any frontend change and does not add a ride-history screen
  (ADR-040 item 6 stands).
- A new dead-letter queue exists and must be monitored alongside the
  existing one; an operational anomaly ("completed ride for a cancelled
  order") is deliberately visible in logs rather than in the DLQ, because
  the message was correctly handled and is not retryable.
- Two of Order Management's three `OrderStatus` values now have exactly one
  reachable path each (`SUBMITTED` at submission, `COMPLETED` by this
  event). `CANCELLED` remains unreachable through any API — no cancel
  endpoint exists. That gap is knowingly left open here, since exposing
  cancellation requires answering Open Question 2 first.
- Dispatch is entirely unchanged by this ADR: no new event, no payload
  change, no new endpoint, no awareness that Order Management now listens.

**Amendment 2026-07-30 — consequences of the Product Decision.** The
bullets above are unchanged and remain accurate. The Product Owner ruling
adds the following:

- The design no longer has a conditional trigger. `AssignmentCompleted` is
  the ratified, sufficient business fact for `SUBMITTED → COMPLETED`;
  implementation may proceed without waiting on any further business input.
- The `CANCELLED` + `AssignmentCompleted` handling is a ratified rule, not
  a defensive default. Its two implementation constraints are now binding:
  the anomaly must be observable (log/metric, ADR-012) and the idempotency
  row must be written in the conflict branch as well.
- `OrderCompleted` remains v1 `{orderId}`. No event-versioning work enters
  Sprint 3.
- The bullet above about `CANCELLED` being unreachable through any API
  stands, and is now explicitly sanctioned rather than merely observed:
  Open Question 2 was closed as "not in Sprint 3", so no cancel endpoint is
  added and the underlying business question is carried forward beyond this
  sprint.
- Anything that would make driver completion insufficient — payment,
  ratings, disputes, passenger confirmation — is now formally gated behind
  a new ADR. It cannot be slipped in as a refinement of ADR-041.
