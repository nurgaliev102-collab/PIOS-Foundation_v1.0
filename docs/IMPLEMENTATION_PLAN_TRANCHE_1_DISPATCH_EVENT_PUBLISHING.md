# Implementation Plan: Tranche 1 — Dispatch Event Publishing Completion v1.0

Status: Implementation Plan — Architecture Analysis and Planning Only. This document is not a Product Decision, not an ADR, and **not an authorization to write code**. It designs, at file and class granularity, the smallest implementation that closes the two infrastructure gaps [PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md](PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md) Part 9 identified as Implementation Tranche #1: Dispatch does not publish `OrderAssigned`/`AssignmentAccepted` through any real infrastructure, and Order Management does not really consume `AssignmentAccepted`. This is not a product change and introduces no Opportunity, Eligibility, Fairness, or Assignment Policy concept. Coding requires separate, explicit approval.

Every design decision below is drawn from patterns **actually observed in committed code** (`backend/order-management`, `backend/driver-management`, `backend/dispatch`), not assumed from documentation. Where documentation and code were compared, the comparison is stated explicitly.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated in an approved document or directly observed in committed code, cited inline.
- **[DERIVED]** — a design choice this plan makes, reasoning from already-proven, already-committed patterns to a question those patterns leave to a plan such as this one.
- **[HYPOTHESIS]** — a candidate detail (for example, an exact new file name) inferred from the existing pattern's own naming convention, not yet verified by writing the file.
- **[OPEN]** — a genuine implementation choice this plan does not resolve, left for the implementer.

## Part 1 — Current State

### Dispatch

- **Assignment creation flow.** `AssignOrderCommand(order, driver)` → `DispatchAssignmentApplicationService.handle(command, existingAssignments)` → `Assignment.create(order, driver, existingAssignments)`, which persists via `AssignmentRepository.save(assignment)` and returns `AssignmentCreated(assignment, event: OrderAssigned)`. **[RATIFIED — observed directly in `DispatchAssignmentApplicationService.kt`, `Assignment.kt`.]** No outbox write, no transaction boundary, and no event publication of any kind occurs today — `AssignmentRepository.save` is the only persistence call.
- **Acceptance flow.** `AcceptAssignmentCommand(assignmentId)` → `DispatchAssignmentApplicationService.acceptAssignment(...)` → `Assignment.accept()`, returning `AssignmentAccepted`, then `assignmentRepository.save(assignment)`. **[RATIFIED]** Same absence: no outbox, no transaction, no publication.
- **Existing domain events.** `OrderAssigned(orderId: OrderReference, driverId: DriverReference, occurredAt: Instant = Instant.now())` and `AssignmentAccepted(orderId: OrderReference, driverId: DriverReference, occurredAt: Instant = Instant.now())` — both already exist as plain Kotlin data classes, both correctly shaped, neither modified by this plan. **[RATIFIED]**
- **Existing publisher stub.** `OrderAssignedPublisher.toContractPayload(event: OrderAssigned): OrderAssignedContractPayload` — translates the domain event to a primitive-typed `{orderReference, driverReference}` payload for the ADR-027 test-scoped contract-verification mechanism only. **No AssignmentAccepted equivalent stub exists.** **[RATIFIED]** Neither this class nor its test is a real publishing path; both remain as-is.
- **Existing consumer-side infrastructure (a different flow, but the exact pattern to mirror).** `DriverAvailabilityRepository`, `TransactionRunner`/`NoOpTransactionRunner`/`SpringTransactionRunner` (already generic, already reusable as-is), `RabbitMQTopologyConfiguration` (consumer-owned queue + DLQ), `RabbitMQListenerContainerConfiguration` (bounded retry via `RetryInterceptorBuilder.stateless()`), `LoggingRejectAndDontRequeueRecoverer`, `DriverAvailabilityChangedListener`. **[RATIFIED]** This is the fully proven, fully tested pattern (9 dedicated test files) this plan's own consumer-side design (Order Management, Part 3) mirrors.
- **Missing pieces, confirmed by direct inspection, not assumed:** no `OutboxRecord`, `OutboxRepository`, `NoOpOutboxRepository`, `EventPublisher`, `NoOpEventPublisher`, or `OutboxRelay` exists anywhere in `com.pios.dispatch.application`; no `PostgreSQLOutboxRepository` or `RabbitMQEventPublisher` exists in `com.pios.dispatch.persistence`; no producer-owned exchange is declared for Dispatch's own events; no `V3` migration exists.

### Order Management

- **Existing `AssignmentAccepted` handler status.** `OrderAssignmentRecognitionHandler.handle(orderReference: String, driverReference: String): String` — validates both references are non-blank and returns `orderReference`; it does not transition `Order`, does not persist anything, and is invoked today only by `OrderAssignmentContractVerificationTest`, never by any RabbitMQ listener. **[RATIFIED — its own KDoc confirms this directly: "makes no call into any other module... its compatibility... is verified only from test code."]**
- **Existing consumer patterns.** **None.** Order Management has a fully proven *producer* pattern (`OutboxRecord`, `OutboxRepository`, `EventPublisher`, `OutboxRelay`, `PostgreSQLOutboxRepository`, `RabbitMQEventPublisher`, `RabbitMQTopologyConfiguration` — all read directly, exact shapes confirmed in Part 3) but **no consumer-side infrastructure of any kind** — no listener, no consumer topology, no idempotency ledger. **[RATIFIED]**
- **Missing pieces:** everything on the consumer side — an idempotency-ledger table and repository, a consumer-owned `RabbitMQTopologyConfiguration` (queue + DLQ bound to Dispatch's own future exchange), a `RabbitMQListenerContainerConfiguration` + recoverer, a listener class, and an application service wiring `OrderAssignmentRecognitionHandler`'s existing logic behind idempotent, transactional handling.

## Part 2 — Traceability

| Change candidate | Classification | Why needed | Authority |
| --- | --- | --- | --- |
| Dispatch outbox (`OutboxRecord`/`OutboxRepository`/`NoOpOutboxRepository`) | **MISSING** | No transactional record of "an event must be published" exists for Dispatch's own events today | ADR-032; already-proven pattern in `order-management`/`driver-management` |
| Dispatch `EventPublisher`/`NoOpEventPublisher`/`OutboxRelay` | **MISSING** | No publishing boundary or relay exists | ADR-031, ADR-032 |
| Dispatch `PostgreSQLOutboxRepository`, `RabbitMQEventPublisher`, producer exchange declaration | **MISSING** | No real broker adapter exists for Dispatch's own events | ADR-029, ADR-031 |
| Dispatch `TransactionRunner`/`NoOpTransactionRunner`/`SpringTransactionRunner` | **KEEP** | Already exists, already generic, directly reusable without modification | Observed directly in `com.pios.dispatch.application.TransactionRunner` |
| `Assignment.kt`, `AssignmentStatus.kt`, `OrderAssigned.kt`, `AssignmentAccepted.kt` | **KEEP** | Domain events and lifecycle already correct (PIOS_CONSOLIDATION review, Part 3: "IMPLEMENTED") | PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Sections 5, 10 |
| `DispatchAssignmentApplicationService.handle`/`acceptAssignment` | **ADAPT** | Must gain a transaction boundary and outbox write, mirroring `OrderLifecycleApplicationService`/`DriverAvailabilityApplicationService` exactly; no change to its own decision logic | ADR-032 |
| `OrderAssignedPublisher` (existing contract-verification stub) | **KEEP** | Remains exactly as-is; ADR-027's test-scoped verification mechanism is unaffected by adding real infrastructure alongside it | INTERFACE_CONTRACTS.md Section 12 |
| Order Management idempotency ledger + consumer topology + listener | **MISSING** | No consumer-side infrastructure of any kind exists | ADR-031 (idempotent consumption requirement), mirroring Dispatch's own already-proven consumer pattern |
| `OrderAssignmentRecognitionHandler` | **ADAPT** | Its existing validation logic is correct and reused; it gains a real caller (the new listener) instead of only a test | INTERFACE_CONTRACTS.md Section 5 |
| Assignment Policy port, Opportunity, Eligibility, Fair Opportunity Policy | **DEFER** | Explicitly out of this tranche's scope (task's own instruction; PIOS_CONSOLIDATION review Part 9, Section 11, Non-Goals) | ADR-034; four Product Decisions |
| "List all available drivers" query (`DriverAvailabilityRepository`) | **DEFER** | Unrelated to this tranche; no consumer needs it yet | ADR-034 Part 6 |

## Part 3 — Implementation Design

### 1. Dispatch Outbox Integration

**Where should events be persisted?** In a new `dispatch_outbox` table, in Dispatch's own PostgreSQL database — the exact structural mirror of `order_management_outbox`/`driver_management_outbox` (`id BIGSERIAL`, `aggregate_id`, `event_type`, `routing_key`, `payload`, `created_at`, `published_at`). **[DERIVED, from the identical shape already used twice.]**

**When should events be created?** Inside `DispatchAssignmentApplicationService.handle` and `acceptAssignment`, wrapped in a `TransactionRunner.run { }` block, exactly where `Assignment.create()`/`assignment.accept()` and `assignmentRepository.save(assignment)` already happen — an outbox record for `OrderAssigned` or `AssignmentAccepted` is written in the **same transaction** as the Assignment's own state change, per ADR-032's dual-write closure. **[DERIVED, directly mirroring `OrderLifecycleApplicationService.submitOrder`/`completeOrder`/`cancelOrder`'s own already-proven structure.]**

**How does this match existing patterns?** Exactly — `outboxRepository`/`transactionRunner` become new constructor parameters on `DispatchAssignmentApplicationService`, defaulted to `NoOpOutboxRepository`/`NoOpTransactionRunner` **only for `outboxRepository`'s Kotlin default is questionable** — per the already-established Hardening Review v1.0 rule (reused verbatim in Driver Management's own `DriverAvailabilityProjectionApplicationService`), the *outbox* dependency should have **no default**, so a production context missing a real bean fails to start rather than silently discarding events; `transactionRunner` may keep a `NoOpTransactionRunner` default, mirroring `OrderLifecycleApplicationService`'s own choice, since Spring's constructor injection always supplies the real bean regardless. **[DERIVED, applying the already-ratified Hardening Review rule to a dependency it did not originally cover.]**

### 2. RabbitMQ Publishing

**Which existing publisher abstraction should be reused?** The `EventPublisher` interface shape (`fun publish(routingKey: String, payload: String)`) — copied into `com.pios.dispatch.application`, exactly as it exists in `order-management` and `driver-management` (both are independent, module-local copies of the same shape, never a shared library — consistent with this project's own no-shared-module rule, MODULE_STRUCTURE.md Section 2). **[RATIFIED pattern, DERIVED application to Dispatch.]**

**What routing keys/exchange conventions exist?** The established convention is one durable topic exchange per producing module, named `"<module-name>.events"` (`order-management.events`, `driver-management.events`, both observed directly). Dispatch's own producer exchange would be **`dispatch.events`** — **[HYPOTHESIS, following the naming convention exactly, not yet created]**. Routing keys observed follow a dotted, past-tense business-fact convention: `order.submitted`, `order.completed`, `order.cancelled`, `driver.availability.changed`. Following this convention precisely: `order.assigned` for `OrderAssigned`, `assignment.accepted` for `AssignmentAccepted` — **[HYPOTHESIS, consistent with the observed convention, not yet ratified as the literal string]**. The implementer should confirm no existing document has already fixed a different literal string before finalizing.

**What serialization format exists?** Plain JSON via Jackson `ObjectMapper.writeValueAsString`, building a `Map` literal with `eventId` (fresh `UUID`, generated once at event-creation time, never regenerated on retry), `eventType`, `eventVersion` (always `1`), `occurredAt` (the domain event's own `Instant`, not the outbox row's persistence timestamp), and `payload` (a nested map of only the fields the ratified contract actually justifies) — observed identically in `OrderLifecycleApplicationService.envelopeFor` and `DriverAvailabilityApplicationService.envelopeFor`. **[RATIFIED pattern.]** For `OrderAssigned`, `payload` would be `{orderId, driverId}`; for `AssignmentAccepted`, the same two fields — matching exactly what `OrderAssignedPublisher`'s existing `OrderAssignedContractPayload` already justifies and INTERFACE_CONTRACTS.md Section 5 already authorizes, no more.

### 3. Order Management Consumer

**Where should `AssignmentAccepted` be consumed?** A new `@Component` listener in `com.pios.ordermanagement.persistence`, `@RabbitListener`-annotated, bound to a new consumer-owned queue (for example, `order-management.from-dispatch`, mirroring Dispatch's own `dispatch.from-driver-management` naming exactly — **[HYPOTHESIS, following the observed convention]**) on Dispatch's future `dispatch.events` exchange, filtering on the `assignment.accepted` routing key only — never a wildcard, mirroring Dispatch's own "never a wildcard... a specific, named consumer" rule verbatim (`RabbitMQTopologyConfiguration.kt`'s own KDoc, Dispatch). **[DERIVED, directly reusing the same justification already written for Dispatch's own consumer.]**

**How is idempotency handled?** A new idempotency-ledger table (for example, `order_management_processed_events`, mirroring Dispatch's own `driver_availability_processed_events` exactly — **[HYPOTHESIS]**) with a single `markProcessed(eventId): Boolean` operation (`INSERT ... ON CONFLICT (event_id) DO NOTHING`), called inside the same `TransactionRunner.run { }` boundary as whatever business effect `AssignmentAccepted` triggers — mirroring `DriverAvailabilityProjectionApplicationService.handle`'s own already-proven idempotent-consumption structure exactly. **[RATIFIED pattern, DERIVED application.]**

**How are retries handled?** Reusing Dispatch's own already-proven, generic pattern verbatim: a `RabbitMQListenerContainerConfiguration` overriding `rabbitListenerContainerFactory` with `RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(200L, 2.0, 2000L).recoverer(...)`, and a `LoggingRejectAndDontRequeueRecoverer` (or an Order-Management-local copy of the identical class) routing an exhausted retry to a new dead-letter queue via the same `x-dead-letter-exchange`/`x-dead-letter-routing-key` queue arguments Dispatch's own topology already uses. **[RATIFIED pattern, copied module-locally, never shared, consistent with this project's no-shared-module convention.]**

**What does the consumer actually do with the event?** Reuses `OrderAssignmentRecognitionHandler`'s own existing validation logic (`orderReference`/`driverReference` non-blank checks) as the business effect inside the new application service's `handle` — consistent with that class's own KDoc ("does not transition the Order aggregate, which has no corresponding state to transition to"). **No new `Order` state is introduced; this plan does not move Commitment earlier or invent a new lifecycle state (Part 5).**

## Part 4 — File Impact

**Files likely modified:**

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` — add `outboxRepository`/`transactionRunner` constructor parameters and wrap `handle`/`acceptAssignment` bodies in a transaction with an outbox write.
- `backend/dispatch/build.gradle.kts` — confirm `spring-boot-starter-amqp` is already present (it is, for the existing consumer side); no new dependency expected for the producer side.
- `backend/order-management/build.gradle.kts` — likely needs `spring-retry`/`spring-boot-starter-aop` added for `RetryInterceptorBuilder`, mirroring `backend/dispatch/build.gradle.kts`'s own existing dependencies (Order Management has never needed retry infrastructure before, since it has never consumed anything).
- `backend/order-management/src/main/resources/application.yml` — add `spring.rabbitmq` consumer configuration, mirroring `backend/dispatch/src/main/resources/application.yml`'s own existing block.

**Files likely created (Dispatch, publishing side — application layer):** `OutboxRecord.kt`, `OutboxRepository.kt`, `NoOpOutboxRepository.kt`, `EventPublisher.kt`, `NoOpEventPublisher.kt`, `OutboxRelay.kt` — each a near-verbatim, module-local copy of the already-proven shape in `order-management`/`driver-management` (Part 1's exact interfaces quoted above).

**Files likely created (Dispatch, publishing side — persistence layer):** `PostgreSQLOutboxRepository.kt`, `RabbitMQEventPublisher.kt` (exchange name `dispatch.events`), and either a new bean method on the existing `RabbitMQTopologyConfiguration.kt` or a separate producer-topology file declaring that exchange — **[OPEN, implementer's choice; the existing file already declares the consumer's re-declared producer exchange for Driver Management, so adding Dispatch's own producer exchange to the same file, or a new file, are both consistent with the existing pattern]**.

**Files likely created (Order Management, consuming side — application layer):** an application service (for example, `AssignmentAcceptedProjectionApplicationService.kt`, mirroring `DriverAvailabilityProjectionApplicationService.kt`'s own name and shape — **[HYPOTHESIS]**), a `DriverAvailabilityRepository`-equivalent idempotency port (for example, `AssignmentAcceptedRepository.kt` exposing only `markProcessed(eventId): Boolean`, since no local projection state beyond idempotency is needed — `OrderAssignmentRecognitionHandler` already establishes there is no Order state to transition to), `TransactionRunner.kt`/`NoOpTransactionRunner.kt` (Order Management does not yet have a generic one reusable from its own producer side — **verify before assuming; if `OrderLifecycleApplicationService` already has one, reuse it instead of creating a duplicate**).

**Files likely created (Order Management, consuming side — persistence layer):** `PostgreSQLAssignmentAcceptedRepository.kt` (or equivalently named), a consumer-owned `RabbitMQTopologyConfiguration.kt` (queue + DLQ + binding, mirroring Dispatch's own), `RabbitMQListenerContainerConfiguration.kt`, `LoggingRejectAndDontRequeueRecoverer.kt` (module-local copy), `AssignmentAcceptedListener.kt`.

**New migrations:** `backend/dispatch/src/main/resources/db/migration/dispatch/V3__outbox.sql`; `backend/order-management/src/main/resources/db/migration/ordermanagement/V3__assignment_accepted_idempotency.sql`.

**Files intentionally NOT modified:** `Assignment.kt`, `AssignmentStatus.kt`, `OrderAssigned.kt`, `AssignmentAccepted.kt`, `AssignOrderCommand.kt`, `AcceptAssignmentCommand.kt`, `AssignmentRepository.kt`, `Order.kt`, `OrderStatus.kt`, any existing migration (`V1`, `V2` in either module), `OrderAssignedPublisher.kt`, `OrderAssignmentRecognitionHandler.kt`'s own validation logic (reused, not rewritten), any file in `driver-management` or `passenger-experience`, any ADR, any Product Decision, EVENT_CATALOG.md, INTERFACE_CONTRACTS.md.

## Part 5 — Event Safety

**`OrderAssigned` meaning, verified against EVENT_CATALOG.md Section 6:** "Dispatch has connected an order to a driver... Records the outcome of the assignment decision that only Dispatch may make." **Unchanged by this plan** — the event is already produced correctly by `Assignment.create()`; this tranche only adds *transport*, never touches *when* or *why* the event is produced.

**`AssignmentAccepted` meaning, verified against the same section:** "A proposed assignment has been confirmed... Marks the point at which an assignment is confirmed to proceed, which Order Management depends on to know the order will proceed toward a ride." **Unchanged.** `Assignment.accept()` is not modified by this plan; the event continues to fire at exactly the same moment it already does today (when a driver's own acceptance is recorded), never earlier.

**Confirmed: no semantic change, no event contract change.** Both events keep their existing Kotlin shape (`orderId`, `driverId`, `occurredAt`), their existing meaning, and their existing firing point. The only change is that they become durably persisted and actually transported, where today they are constructed and immediately discarded once `DispatchAssignmentApplicationService` returns.

**Explicit confirmation regarding commitment.** `AssignmentAccepted` represents Commitment (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Sections 5, 10) — this plan does **not** move that moment earlier. The outbox write for `AssignmentAccepted` occurs in the same transaction as `assignment.accept()`, which is the existing, already-correct commitment-creating call; nothing in this plan introduces a pre-acceptance publication of `AssignmentAccepted`, and `OrderAssigned` (which already, correctly, fires before acceptance, per that same document's own Central Finding) is not reinterpreted as creating any obligation — this plan changes its transport, not its meaning (Section 3 of that document remains exactly as it was).

## Part 6 — Database Impact

Additive only, consistent with the KEEP classification every existing table already received in the PIOS_CONSOLIDATION review (Part 7):

- **New:** `dispatch_outbox` (Dispatch) — structurally identical to `order_management_outbox`/`driver_management_outbox`.
- **New:** an idempotency-ledger table (Order Management) — structurally identical to Dispatch's own `driver_availability_processed_events`.
- **Unchanged:** `orders`, `assignments`, `drivers`, `driver_availability`, `driver_availability_processed_events`, `order_management_outbox`, `driver_management_outbox` — none is read, written, or altered by this tranche.

No migration is created by this planning document; the two `V3` files named in Part 4 are recommendations for a future implementation task.

## Part 7 — Test Plan

Mirroring the two already-proven suites exactly — no new testing philosophy, per ADR-013 (constructor-based, no Spring context):

**Unit tests:**
- `DispatchAssignmentApplicationServiceTest` (existing) extended to assert an outbox record is produced alongside `AssignmentCreated`/`AssignmentAccepted`, mirroring `OrderLifecycleApplicationServiceTest`'s own existing assertions.

**Integration tests (Dispatch, publishing side — mirroring `order-management`'s own suite exactly):**
- A `PostgreSQLOutboxRepositoryTest`-equivalent for `dispatch_outbox`.
- A `RabbitMQEventPublisherTest`-equivalent confirming publish-and-confirm against a real RabbitMQ instance.
- An `OutboxRelayTest`-equivalent confirming relay-and-mark-published behavior, including the "one failure does not block the rest of the batch" case already proven in Order Management's own relay.
- An `OrderAssignedPublicationTest`/`AssignmentAcceptedPublicationTest`-equivalent (envelope shape, `eventId` stability across retries — mirroring `OrderSubmittedEnvelopeTest`).
- A transaction test proving the Assignment write and the outbox write commit or roll back together (mirroring `OrderOutboxTransactionTest`/`DriverAvailabilityOutboxTransactionTest`).

**Integration tests (Order Management, consuming side — mirroring Dispatch's own four-test suite exactly):**
- A consumer integration test (message published → consumer receives and processes it).
- An idempotency test (the same `eventId` delivered twice produces the effect exactly once — mirroring `DriverAvailabilityIdempotencyIntegrationTest`).
- A transaction-rollback test (mirroring `DriverAvailabilityProjectionTransactionTest`).
- A dead-letter test — malformed message and/or unsupported `eventVersion` both reach the new DLQ (mirroring `DriverAvailabilityDeadLetterIntegrationTest`'s two named cases).

**Contract tests:** `OrderAssignmentContractVerificationTest` (existing) is unaffected and continues to pass unmodified — this plan adds a real transport path *alongside* it, never replacing the ADR-027 verification mechanism.

**End-to-end verification:** extend or verify `MvpVerticalSliceScenarioTest` actually exercises the new flow end-to-end (create assignment → accept → observe Order Management's consumer act on it) — per the PIOS_CONSOLIDATION review's own caution (Part 8) not to assume this test already covers a flow that did not previously exist.

## Part 8 — Implementation Steps

1. Add Dispatch's own outbox application-layer types (`OutboxRecord`, `OutboxRepository`, `NoOpOutboxRepository`, `EventPublisher`, `NoOpEventPublisher`, `OutboxRelay`), reusing `TransactionRunner` as-is.
2. Add the `V3__outbox.sql` migration and `PostgreSQLOutboxRepository` for Dispatch.
3. Add `RabbitMQEventPublisher` and the `dispatch.events` producer exchange declaration for Dispatch.
4. Wire `DispatchAssignmentApplicationService` to write an outbox record inside the same transaction as `create()`/`accept()`.
5. Add Order Management's consumer-side infrastructure: idempotency migration + repository, `TransactionRunner` (if not already reusable from the producer side), `RabbitMQTopologyConfiguration` (queue + DLQ + binding to `dispatch.events`), `RabbitMQListenerContainerConfiguration`, recoverer.
6. Add the new application service wiring `OrderAssignmentRecognitionHandler`'s existing logic behind idempotent, transactional handling, and the `AssignmentAcceptedListener`.
7. Add tests (Part 7), mirroring both existing proven suites file-for-file.
8. Verify end-to-end: an Assignment created and accepted in Dispatch is observed, exactly once, by Order Management's new consumer, including under simulated redelivery and simulated processing failure (DLQ).

## Part 9 — Risks

- **Hidden coupling.** None identified beyond what already exists: Order Management's new consumer depends only on the routing key and payload shape already authorized by INTERFACE_CONTRACTS.md Section 5 — never on any Dispatch domain type, mirroring the existing `OrderAssignmentRecognitionHandler`'s own already-correct primitive-only boundary.
- **Event contract mismatch.** Low risk — the payload shape is already fixed by `OrderAssignedContractPayload`'s own existing fields; the main risk is the routing-key literal strings (`order.assigned`, `assignment.accepted`) being only a **[HYPOTHESIS]** in this plan — the implementer must confirm no other document has already fixed a different string before finalizing.
- **Duplicate processing.** Mitigated by the idempotency ledger, mirroring Dispatch's own already-proven `markProcessed` pattern exactly; the same risk profile as the already-working `DriverAvailabilityChanged` flow.
- **Ordering problems.** `OrderAssigned` and `AssignmentAccepted` for the same Assignment could, in principle, be relayed out of commit order if the relay batches non-atomically across multiple outbox rows — the existing relay already handles this per-record, in commit order (`findUnpublished()` returns rows `ORDER BY id ASC`), so no new ordering risk is introduced beyond what the existing, proven relay already manages.
- **Transaction boundaries.** The main new risk surface: `DispatchAssignmentApplicationService.acceptAssignment`'s two overloads (one taking an `Assignment` directly, one restoring it from the repository first) must both wrap their outbox write in the *same* transaction as `assignmentRepository.save(assignment)` — mirroring `OrderLifecycleApplicationService.completeOrder`'s own two-overload handling of exactly this concern.

## Part 10 — Acceptance Criteria

- Dispatch emits `OrderAssigned` and `AssignmentAccepted` through a real, durable outbox, in the same transaction as the corresponding `Assignment` state change.
- A running relay publishes both events to RabbitMQ, confirmed by the broker (`waitForConfirmsOrDie`), mirroring the existing publisher's own guarantee.
- Order Management's new consumer receives `AssignmentAccepted`, processes it idempotently, and a redelivered message produces no duplicate effect.
- A message that cannot be processed (malformed, or an unsupported `eventVersion`) reaches Order Management's own new dead-letter queue after the bounded retry policy is exhausted.
- Every existing test in `order-management`, `driver-management`, `dispatch`, and `passenger-experience` continues to pass unmodified — no existing behavior changes.
- `OrderAssignmentContractVerificationTest` and `OrderAssignmentRecognitionHandler`'s existing validation behavior are unaffected.
- No Opportunity, Eligibility, Fair Opportunity Policy, or Assignment Policy concept appears anywhere in the diff.

## Part 11 — Files Changed (This Planning Task)

`docs/IMPLEMENTATION_PLAN_TRANCHE_1_DISPATCH_EVENT_PUBLISHING.md` (new) is the only content file this task creates. `docs/README.md` receives one minimal traceability pointer. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this task.

---

Where this plan states a **[HYPOTHESIS]**-graded detail (an exact file, table, exchange, or routing-key name), it is a recommendation consistent with the observed naming convention, not a ratified or verified fact — the implementer confirms or adjusts it before writing code. Where this plan finds no evidence, it says so rather than inventing a default. Acting on this plan requires separate, explicit implementation approval.
