# PIOS Hardening Review v1.0 — After Tranche 1 and Tranche 2

Status: Control Checkpoint — Architecture Verification and Production-Readiness Review Only. This document is **not** a Product Decision, **not** an ADR, and **not** an implementation plan. It verifies, against actual committed code and the full authority chain, whether the system foundation resulting from Tranche 1 (`41b9b24`, Dispatch Event Publishing Completion) and Tranche 2 (`182520e`, Passenger Experience REST Transport) is coherent and safe for further product evolution. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this document.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated in an approved document or directly observed in committed code, cited inline.
- **[DERIVED]** — a conclusion reasoned from already-ratified material.
- **[HYPOTHESIS]** — a candidate interpretation, not adopted as fact.
- **[OPEN]** — a genuine gap this review surfaces rather than resolves.

**Inspection basis.** `docs/PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md`, `docs/IMPLEMENTATION_PLAN_TRANCHE_1_DISPATCH_EVENT_PUBLISHING.md`, `docs/IMPLEMENTATION_PLAN_TRANCHE_2_PASSENGER_REST_TRANSPORT.md`, `docs/PROJECT_CONSTITUTION.md`, `docs/PRODUCT_BASELINE_V2.md`, `docs/EVENT_CATALOG.md`, `docs/INTERFACE_CONTRACTS.md`, ADR-004, ADR-027 through ADR-034; and actual committed code in `backend/dispatch`, `backend/order-management`, `backend/driver-management`, `backend/passenger-experience` (main and test source sets, migrations, `application.yml`), read directly, not assumed from the plans that preceded implementation.

---

## Part 1 — Current Architecture Snapshot

```
Passenger
   |
   |  (no inbound endpoint on Passenger Experience -- deliberate non-goal, Tranche 2 Part 2/4)
   v
Passenger Experience (application layer, dormant -- no production caller invokes it yet)
   PassengerOrderSubmissionApplicationService.handle()
        -> OrderSubmissionRequestedPublisher.toContractPayload()
        -> OrderSubmissionCoordinator.submitOrder()
        -> RestClientOrderSubmissionClient.submit()
   |
   |  ===== SYNCHRONOUS BOUNDARY (REST, ADR-004/ADR-028) =====
   v
Order Management  (POST /v1/orders, com.pios.ordermanagement.api.OrderSubmissionController)
   OrderSubmissionRequestHandler.handle()
        -> OrderLifecycleApplicationService.submitOrder()
        -> Order.submit() -> OrderRepository.save() + OutboxRecord (same PostgreSQL transaction, ADR-032)
   |
   |  ===== ASYNCHRONOUS BOUNDARY (RabbitMQ, order-management.events, ADR-028/029/031) =====
   |  routing key: order.submitted -- no ratified consumer exists (EVENT_CATALOG.md Section 9
   |  names only Notifications/Analytics generically; neither is built)
   v
  [ nothing consumes OrderSubmitted today ]

Dispatch  (assignment decision, invoked by AssignOrderCommand -- caller/driver supplied
           externally; no automated trigger from OrderSubmitted exists or is ratified,
           per ADR-002/ADR-034's deliberately-undefined Assignment Policy)
   DispatchAssignmentApplicationService.handle() / .acceptAssignment()
        -> Assignment.create() / Assignment.accept()
        -> AssignmentRepository.save() + OutboxRecord (same PostgreSQL transaction, ADR-032)
   |
   |  ===== ASYNCHRONOUS BOUNDARY (RabbitMQ, dispatch.events, ADR-028/029/031) =====
   |  routing keys: order.assigned, assignment.accepted
   v
Order Management  (com.pios.ordermanagement.persistence.AssignmentAcceptedListener,
                    queue order-management.from-dispatch)
   -> idempotency ledger markProcessed(eventId)
   -> AssignmentAcceptedProjectionApplicationService.handle()
        -> OrderAssignmentRecognitionHandler (validates references; does NOT transition
           Order's own status -- no ratified Order state exists for this yet, by design)
```

**Synchronous boundaries.** Exactly one: Passenger Experience → Order Management (Submit Order, REST). This is the only synchronous cross-module boundary in the entire system **[RATIFIED — confirmed by direct grep: no other `@RestController`/`RestClient` pairing exists anywhere in `backend/`]**.

**Asynchronous boundaries.** Three RabbitMQ producer exchanges exist (`order-management.events`, `driver-management.events`, `dispatch.events`), each topic, each durable, one queue per consumer per ADR-031's topology. Driver Management → Dispatch (`DriverAvailabilityChanged`) and Dispatch → Order Management (`OrderAssigned`, `AssignmentAccepted`) are both now fully wired producer-to-consumer. Order Management's own three events (`OrderSubmitted`, `OrderCancelled`, `OrderCompleted`) are published to its exchange but have **no consumer anywhere** — this is not a defect; EVENT_CATALOG.md Section 9 and INTERFACE_CONTRACTS.md Section 5 never establish a consumer for them beyond the generic, unbuilt Notifications/Analytics contract.

**Ownership boundaries.** Unchanged by either tranche: Order Management owns order lifecycle and its own three events; Dispatch owns the assignment decision and `OrderAssigned`/`AssignmentAccepted`; Driver Management owns availability; Passenger Experience owns passenger representation and originates the Submit Order request but owns no event and no Order state **[RATIFIED — EVENT_CATALOG.md Section 8; INTERFACE_CONTRACTS.md Section 4]**.

**A structural gap that predates both tranches, confirmed still present.** Nothing connects `OrderSubmitted` to Dispatch's own `AssignOrderCommand` — there is no automated "an order was submitted, therefore dispatch it" pipeline anywhere in the codebase, and none is ratified by any document (ADR-002/ADR-034 deliberately leave the assignment trigger and criteria undefined pending a future Assignment Policy). The vertical flow described in Part 2 below is therefore a chain of independently correct, independently testable segments — not, today, one automatically firing pipeline from a passenger's request to a completed ride.

---

## Part 2 — Vertical Flow Verification

| # | Step | Classification | Evidence |
| --- | --- | --- | --- |
| 1 | Submit Order REST request | **WORKING** | `OrderSubmissionController` (`backend/order-management/.../api`), real HTTP round-trip proven by `PassengerOrderSubmissionEndToEndTest`, `RestClientOrderSubmissionClientTest`. Controller construction and delegation proven by `OrderSubmissionControllerTest`. |
| 2 | Order creation | **WORKING** | `Order.submit()`, `OrderLifecycleApplicationService`, `PostgreSQLOrderRepository` — proven since before either tranche, unmodified. |
| 3 | OrderSubmitted publication | **PARTIAL / RISK** | The outbox write is durable and transactionally consistent with the Order write (`OrderOutboxTransactionTest`), and `RabbitMQEventPublisher`/`OutboxRelay` are fully implemented and unit/integration-tested. **But nothing in any module's production code ever calls `OutboxRelay.relay()`** — no `@Scheduled` method, no `@EnableScheduling`, no scheduler bean exists anywhere in `backend/` (confirmed by repository-wide grep). `relay()` is invoked only from test code (`OutboxRelayTest` and the three publication-test suites). In a real running deployment, every outbox record ever written durably sits in its table forever unless some future, not-yet-built mechanism invokes the relay. This is not new to Tranche 1/2 — it already existed for Order Management's and Driver Management's own events — but Tranche 1 extended the identical, unscheduled pattern to Dispatch, so the gap now affects all three producer modules identically. |
| 4 | Dispatch processing (an order becomes an assignment) | **MISSING, by design** | No consumer of `OrderSubmitted` exists in Dispatch or anywhere else; `AssignOrderCommand` must be invoked directly by an external caller supplying both order and driver references. This is not an oversight: ADR-002 and ADR-034 deliberately leave the assignment trigger and criteria undecided pending a future Assignment Policy, and the Tranche 1/2 plans both explicitly scoped this out. Recorded here as **MISSING** relative to a fully automatic pipeline, **not** as a defect relative to what is currently ratified. |
| 5 | Assignment creation | **WORKING** | `Assignment.create()`, `DispatchAssignmentApplicationService.handle()`, tested since before Tranche 1; now also durably outboxed in the same transaction (`AssignmentOutboxTransactionTest`). |
| 6 | OrderAssigned publication | **PARTIAL / RISK** | Same relay-scheduling gap as Step 3 — the write is correct and tested; the actual publish to RabbitMQ in a running deployment depends on a trigger that does not exist. |
| 7 | Assignment acceptance | **WORKING** | `Assignment.accept()`, tested; durably outboxed in the same transaction. |
| 8 | AssignmentAccepted publication | **PARTIAL / RISK** | Same relay-scheduling gap. |
| 9 | Order Management consumption | **WORKING, with one named limitation** | The transport machinery is fully working and the most thoroughly tested flow added by either tranche: `AssignmentAcceptedListener` → idempotency ledger → `AssignmentAcceptedProjectionApplicationService` → `OrderAssignmentRecognitionHandler`, proven by dedicated consumer, idempotency, transaction-rollback, and dead-letter integration tests (all against real PostgreSQL and RabbitMQ). **Named limitation, not a defect:** `OrderAssignmentRecognitionHandler` validates and idempotently records the fact, but does **not** transition `Order`'s own status — no ratified Order state for "assigned" or "accepted" exists yet (its own KDoc states this explicitly, and Tranche 1's plan confirmed it deliberately, Part 3). Order Management therefore durably *knows* an assignment was accepted, but does not yet *reflect* that fact in anything a consumer of Order's own status would observe. |

**Overall verdict for Part 2.** Every individual segment that Tranche 1 and Tranche 2 were scoped to build is genuinely **WORKING** at the unit/integration-test level, verified against real infrastructure. The chain does not yet function as one continuous, automatically firing pipeline end-to-end in a live deployment, for two reasons that are architecturally distinct and must not be conflated: (a) the relay-scheduling gap (Step 3/6/8, an infrastructure completeness gap, arguably in scope for hardening), and (b) the absent Order→Dispatch trigger (Step 4, a deliberate, ratified-as-open product/architecture gap, out of scope for hardening).

---

## Part 3 — Event Flow Review

**Event ownership.** Unchanged. Only `DispatchAssignmentApplicationService` creates `OrderAssigned`/`AssignmentAccepted` (confirmed by direct code read); Order Management's new listener never constructs either event, only deserializes the primitive fields the contract already authorizes (`payload.orderId`, `payload.driverId`) **[RATIFIED — EVENT_CATALOG.md Section 6; ADR-030's exclusive schema-ownership rule]**.

**Event creation points.** Unchanged from before Tranche 1: `Assignment.create()` fires the `OrderAssigned` event object; `Assignment.accept()` fires `AssignmentAccepted`. Tranche 1 added only a transactional outbox write alongside each, never a new firing point, never an earlier or later moment **[RATIFIED — confirmed directly in `DispatchAssignmentApplicationService.kt`, matching the Tranche 1 plan's own Part 5 "Event Safety" claim exactly]**.

**Meaning unchanged.** Both events retain their original Kotlin shape (`orderId: OrderReference`, `driverId: DriverReference`, `occurredAt: Instant`) and their EVENT_CATALOG.md Section 6 meaning, word for word, unmodified by either tranche.

**Consumers understand producers.** Verified directly: the payload Dispatch's outbox envelope carries (`{orderId, driverId}`) is exactly what `AssignmentAcceptedListener` extracts (`payload.orderId`, `payload.driverId`) — no field mismatch, no undocumented assumption on either side.

**Transport.** RabbitMQ topic exchange `dispatch.events` (new, Tranche 1), durable; queue `order-management.from-dispatch` bound with the specific routing key `assignment.accepted` only — never a wildcard, consistent with ADR-031's own "a specific, named consumer" principle and Dispatch's own prior consumer precedent.

**Serialization.** Plain JSON via Jackson, envelope `{eventId, eventType, eventVersion, occurredAt, payload}` — identical shape across all three producer modules, matching the pre-existing, already-proven convention exactly.

**Version handling.** `eventVersion` is a hardcoded literal `1` at every producer; the consumer explicitly checks `eventVersion == 1` and throws if not, which the retry/DLQ machinery then routes to the dead-letter queue (proven by `AssignmentAcceptedDeadLetterIntegrationTest`'s dedicated "an unsupported eventVersion... reaches the dead-letter queue" case). This satisfies ADR-030's versioning policy for the one version that exists today; no consumer yet demonstrates tolerant-reader behavior toward an unrecognized *additional* field, since no such field has ever been introduced to test against — an untested-because-inapplicable case, not a gap.

**Retry handling.** `RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(200L, 2.0, 2000L)`, identical configuration reused verbatim from Dispatch's own prior `DriverAvailabilityChanged` consumer.

**Dead-letter handling.** Each consumer owns its own DLQ via `x-dead-letter-exchange`/`x-dead-letter-routing-key` queue arguments — no shared DLQ, consistent with ADR-031.

**Idempotency.** `markProcessed(eventId)` via `INSERT ... ON CONFLICT (event_id) DO NOTHING`, proven both for correctness (`AssignmentAcceptedIdempotencyIntegrationTest`) and for transactional atomicity with the business effect (`AssignmentAcceptedProjectionTransactionTest`, proving a failed recognition rolls the idempotency record back too).

**Conclusion.** Every item in this Part is **WORKING** and verified against real infrastructure. The one caveat already stated in Part 2 (Step 3/6/8): none of this transport machinery self-triggers in a live deployment without the missing relay scheduler.

---

## Part 4 — Failure Scenarios

| Scenario | Classification | Evidence |
| --- | --- | --- |
| **REST — Order Management unavailable** | **HANDLED** at the client/exception level; **FUTURE WORK** for reaction | `RestClient` throws `ResourceAccessException`, uncaught, propagating to whatever calls `OrderSubmissionCoordinator.submitOrder` (`RestClientOrderSubmissionClientTest`'s connection-failure case). No real production caller exists yet to react to it — Passenger Experience gains no inbound endpoint in Tranche 2 by explicit design (its own Part 4 non-goal), so this exception currently has nowhere to surface to in a live deployment. |
| **REST — timeout** | **HANDLED** | Bounded 5000ms connect/read timeout (`OrderManagementRestClientConfiguration`); surfaces as a `RestClientException`/`ResourceAccessException` wrapping `SocketTimeoutException`, tested deterministically against a non-responding socket. |
| **REST — invalid request** | **HANDLED** | Blank `passengerReference` → controller catches the existing `IllegalArgumentException` and returns HTTP 400; client surfaces this as `HttpClientErrorException.BadRequest`, distinguishable from a transport failure — tested end-to-end over real HTTP. |
| **Messaging — RabbitMQ unavailable** | **PARTIALLY HANDLED** | `RabbitMQEventPublisher.publish` would throw (broker connection failure or missing confirm); `OutboxRelay.relay()`'s own per-record try/catch already logs and leaves the record unpublished for the next poll (verified in code and `OutboxRelayTest`). But because nothing schedules `relay()` in production (Part 2, Step 3), this resilience is proven only in test code, never actually exercised by a live, self-healing scheduled process. |
| **Messaging — publisher failure** | **PARTIALLY HANDLED** | Same reasoning: correct, tested at the unit level; not self-triggering in production. |
| **Messaging — consumer failure** | **HANDLED** | Bounded retry (3 attempts) then DLQ, proven for both `DriverAvailabilityChanged` (pre-existing) and `AssignmentAccepted` (Tranche 1) flows. |
| **Messaging — duplicate message** | **HANDLED** | Idempotency ledger, proven with a dedicated redelivery test. |
| **Messaging — malformed message** | **HANDLED** | Listener's own `require` checks on every extracted field throw on structural malformation, uncaught, triggering the same retry→DLQ path — proven by `AssignmentAcceptedDeadLetterIntegrationTest`'s "deterministically fails processing" case. |
| **Messaging — unsupported event version** | **HANDLED** | Explicit `eventVersion == 1` check, proven by a dedicated dead-letter test case. |
| **Database — transaction rollback** | **HANDLED** | Dedicated transaction tests exist for every producer (`AssignmentOutboxTransactionTest`, pre-existing equivalents for Order Management/Driver Management) and for the new consumer (`AssignmentAcceptedProjectionTransactionTest`), each proving the business write and the outbox/idempotency write commit or roll back together. |
| **Database — outbox consistency** | **HANDLED** at the write level; **NOT HANDLED** at the delivery-triggering level | Restates Part 2 Step 3's finding from the database angle: the outbox row is always consistent with the aggregate's own state (transactionally guaranteed and tested); nothing yet guarantees that row is ever actually drained to the broker in a running system. |
| **Database — idempotency persistence failure** (e.g., the ledger insert itself fails mid-processing) | **PARTIALLY HANDLED, untested as a named scenario** | Would propagate as an exception through the same `TransactionRunner.run` boundary already proven to roll back atomically (the general transaction-rollback mechanism covers it structurally), but no test explicitly names or exercises this specific failure mode (as opposed to a business-logic failure, which is what the existing rollback test actually simulates). |

---

## Part 5 — Test Coverage Review

Inspected directly: all test source files in `dispatch` (28), `order-management` (28, including the 4 new API/verification tests from Tranche 2), `driver-management` (17), `passenger-experience` (10, including the 6 new client/coordinator/end-to-end tests from Tranche 2).

| Coverage area | Status | Notes |
| --- | --- | --- |
| Happy path (order submit, assign, accept, consume) | **KEEP** | Covered at unit and integration level across all four modules; the REST leg specifically covered end-to-end by `PassengerOrderSubmissionEndToEndTest`. |
| Transaction boundaries | **KEEP** | A dedicated transaction test exists for every producer and for the new consumer; this is the most consistently applied test pattern across both tranches. |
| Retry | **KEEP** | Proven for both consumer flows (`DriverAvailabilityChanged`, `AssignmentAccepted`) via dead-letter tests that exercise the retry policy to exhaustion. |
| Idempotency | **KEEP** | Dedicated redelivery tests exist for both consumer flows. |
| Dead-letter queue | **KEEP** | Each consumer has its own dedicated DLQ test, including the specific malformed-message and unsupported-version cases. |
| REST transport | **KEEP** | Controller, client success/validation/connection-failure/timeout, and end-to-end tests all exist and pass against real HTTP. |
| End-to-end flow (the *entire* chain, Part 1's diagram, start to finish) | **MISSING** | No single test exercises Submit Order (REST) → Order creation → an operator-driven Assign/Accept in Dispatch → real RabbitMQ delivery → Order Management consumption, all through real transports in one scenario. `MvpVerticalSliceScenarioTest` (order-management) exercises the full business sequence but entirely through direct, in-process method calls — no real HTTP, no real RabbitMQ — so it verifies application-layer correctness, not the transports Tranche 1/2 actually added. The individual real-transport legs are each separately, thoroughly tested (Part 5 rows above); their composition into one running chain is not. |
| Relay-scheduling / self-triggering delivery | **MISSING** | No test exists (and none reasonably could, absent the scheduler itself) proving an outbox record is *automatically* published without a test calling `relay()` directly — because production code never calls it either (Part 2, Step 3). |
| REST failure reaction by a real caller | **ADD LATER** | The client-side exception behavior is tested; how a real, production caller of `OrderSubmissionCoordinator` should react to a thrown exception has no test, because no such caller exists yet. |

**Do not add tests** — per this task's own instruction, none were added; this Part records status only.

---

## Part 6 — Domain Boundary Review

Confirmed by direct inspection of every file both tranches touched (`OrderSubmissionController`, `SubmitOrderRequest`/`SubmitOrderResponse`, `OrderSubmissionClient`, `OrderSubmissionCoordinator`, `RestClientOrderSubmissionClient`, `DispatchAssignmentApplicationService`, `AssignmentAcceptedListener`, `AssignmentAcceptedProjectionApplicationService`) — **no occurrence anywhere** of:

- **Opportunity** — no multi-candidate structure exists; `Assignment.create()` still takes exactly one externally-supplied driver, unchanged.
- **Eligibility** — `Availability` remains the only readiness concept; no new criterion, field, or check was introduced.
- **Fair Opportunity Policy** — no selection, ranking, or scoring logic anywhere in either tranche's diff.
- **Assignment Policy** — the port ADR-034 named remains unbuilt; nothing added by either tranche substitutes for it.
- **Reciprocity, Reputation** — absent, as required.
- **Payment logic** — absent; the REST contract carries exactly `passengerReference`/`orderId`, nothing resembling money, pricing, or settlement.

**Order remains demand representation.** `Order.submit()`'s own domain logic is byte-for-byte unmodified; the new REST controller only calls the already-existing `OrderSubmissionRequestHandler`, adding no field, no status, no new meaning to Order.

**Assignment remains commitment representation.** `Assignment.create()`/`Assignment.accept()` are unmodified; Tranche 1 added only a transactional outbox write alongside each, confirmed directly in Part 3 above. The CREATED/ACCEPTED split (Opportunity/Acceptance/Commitment Product Decision's own finding) is untouched.

**Dispatch remains decision infrastructure.** No decision logic was added; Tranche 1 added only transport (outbox + publisher), never a selection criterion.

**Conclusion: zero domain-boundary violation found in either tranche.**

---

## Part 7 — Production Readiness Assessment

| Module | Classification | Reasoning |
| --- | --- | --- |
| **Order Management** | **NEEDS HARDENING** | Order lifecycle, REST endpoint, and AssignmentAccepted consumption are each genuinely solid and well-tested. The unscheduled `OutboxRelay` (Part 2/4) means `OrderSubmitted`/`OrderCompleted`/`OrderCancelled` are durably recorded but not actually delivered in a live deployment — a real gap for this module specifically, since it is the module with the most events depending on that relay. |
| **Dispatch** | **NEEDS HARDENING** | Same unscheduled-relay gap, now affecting this module too since Tranche 1. Otherwise the most thoroughly tested module in the repository. |
| **Passenger Experience** | **NEEDS HARDENING** | The REST client is solid and tested, but the module has no real production caller of its own new `OrderSubmissionCoordinator` — it remains, as before Tranche 2, invoked only by tests. This is not a defect (an inbound endpoint was an explicit non-goal), but it means Passenger Experience is not yet "production ready" in the sense of actually being reachable by anything outside a test JVM. |
| **Driver Management** | **NEEDS HARDENING** | Untouched by either tranche; carries the identical unscheduled-relay gap it already had before Tranche 1, now shared with two other modules rather than being unique to it. |
| **Event infrastructure (outbox + RabbitMQ, cross-cutting)** | **NEEDS HARDENING** | The pattern itself (transactional outbox, publisher-confirm, per-consumer queue + DLQ, bounded retry, idempotency) is proven correct and consistent across all three producer/two consumer pairings — this is genuinely strong, well-tested work. The one missing piece, common to all three modules, is the relay-scheduling trigger identified throughout this review. Nothing here is **BLOCKED**: the fix is additive (a scheduler), requires no new ADR (ADR-031/032 already anticipate a relay "running within the same domain's own service"; only its trigger mechanism is unaddressed), and touches no domain concept. |

No module and no capability reviewed here is classified **BLOCKED** — every finding is a hardening item, not a structural defect requiring redesign.

---

## Part 8 — Next Step Recommendation

**Recommendation: A — Further infrastructure hardening.**

Evidence:

- Part 2 and Part 4 both converge on the same, single, concrete, well-understood gap — no production trigger for `OutboxRelay` in any of the three producer modules — that sits directly in the path of "is this event infrastructure real yet," the exact question this review was asked to answer. It is narrow, additive, requires no new ADR (ADR-031/032 already anticipate a relay; only its scheduling trigger is unaddressed), and affects all three producer modules identically, making it a single, coherent unit of hardening work rather than three separate ones.
- **Option B (Network Pilot execution)** is a valid, ready, and entirely separate track — PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md and the four Network Pilot documents already establish it needs zero code and can run independently of this Engineering Track, exactly as PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md Part 10 already described the two tracks converging only at a future evidence gate. Recommending A does not compete with or block B; both can proceed in parallel, as they already have been.
- **Option C (new product capability)** is premature by this review's own findings: Part 1 confirms no automated Order→Dispatch trigger exists, and Part 7 finds every module still needs relay-scheduling hardening. Building new product capability on top of an event pipeline that does not yet self-trigger in production would compound, not close, the gap this review identifies.
- **Option D** — none identified; this review's evidence points specifically and only toward A.

This recommendation is evidence-based, not a task authorization; per this review's own MODE, no implementation task is created here.

---

## Part 9 — Documentation Health

- **PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md** remains largely accurate. Its Part 2 traceability table's "PARTIALLY IMPLEMENTED" status for `OrderAssigned`/`AssignmentAccepted` publication and `AssignmentAccepted` consumption is now **outdated** — both are **IMPLEMENTED** as of Tranche 1 (`41b9b24`). Likewise, "Submit Order transport... SCAFFOLDED (ADR-027 stub stage only)" is now outdated — real REST transport exists as of Tranche 2 (`182520e`). Per PROJECT_CONSTITUTION.md's Never Delete Documentation rule, this is not something this review corrects in place; it is recorded here as a drift finding for a future documentation-maintenance task.
- **ADR-027**'s own text ("Until a broker product is selected and implemented, this ADR's mechanism remains the only one actually in effect") is now materially superseded in practice for two of the three MVP flows (Dispatch→Order Management, Passenger Experience→Order Management), though ADR-027 itself is correctly left unmodified — ADR-028's own text already anticipated exactly this transition ("supersedes ADR-027's in-process/test-scoped mechanism as the actual runtime transport once implemented"), so no contradiction exists, only a now-partially-realized anticipation worth noting.
- **No missing reference found** between the two Implementation Plans and the code actually produced — both plans' own `[HYPOTHESIS]`-graded predictions (exchange name `dispatch.events`, routing keys `order.assigned`/`assignment.accepted`, queue name `order-management.from-dispatch`, REST path `/v1/orders`) were each independently confirmed correct by this review's own direct code inspection.
- **No architecture drift found** beyond the relay-scheduling gap already discussed at length (Parts 2, 4, 7) — no ADR's stated topology, ownership, or versioning policy is contradicted by any line of code either tranche produced.
- **This document does not modify any reviewed document** — findings are recorded here only, per this task's own instruction.

---

Where this review finds no evidence for a claim, it states so explicitly rather than filling the gap. Acting on Part 8's recommendation requires a further, explicit task; this review does not authorize implementation.
