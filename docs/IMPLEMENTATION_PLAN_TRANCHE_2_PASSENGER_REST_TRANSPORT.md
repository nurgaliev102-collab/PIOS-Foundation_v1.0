# Implementation Plan: Tranche 2 — Passenger Experience REST Transport v1.0

Status: Implementation Plan — Architecture Analysis and Planning Only. This document is not a Product Decision, not an ADR, and **not an authorization to write code**. It designs the smallest implementation that closes the Tranche 2 gap [PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md](PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md) identified: the Passenger Experience → Order Management contract still runs entirely on ADR-027's provisional, test-only mechanism, never on ADR-028's selected REST transport. This is not a new passenger product, mobile application, marketplace feature, or ordering model. Coding requires separate, explicit approval.

Every design decision below is checked against committed code (`backend/passenger-experience`, `backend/order-management`) and against the exact text of ADR-004, ADR-027, and ADR-028 — not assumed from either alone.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated in an approved document or directly observed in committed code, cited inline.
- **[DERIVED]** — a design choice this plan makes, reasoning from already-ratified material to a question those sources leave to a plan such as this one.
- **[HYPOTHESIS]** — a candidate detail (an exact endpoint path, class name) consistent with observed convention, not yet verified by writing the file.
- **[OPEN]** — a genuine ambiguity this plan surfaces rather than silently resolving.

## Part 1 — Current State

### Passenger Experience

- **Current responsibilities.** Representing the originating passenger/corporate-customer context for a submitted order (ADR-018; MODULE_STRUCTURE.md). **[RATIFIED]**
- **Existing inbound interfaces.** **None.** `spring-boot-starter-web` is already a dependency (`build.gradle.kts`) and `application.yml` already configures `server.port: 8082`, but no `@RestController`, `@RequestMapping`, or any HTTP endpoint exists anywhere in this module — confirmed by direct inspection, consistent with PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md Part 2's own finding that no REST controller exists anywhere in this repository.
- **Existing outbound communication.** **None.** No REST client, no HTTP call to any other module exists in production code.
- **Current test-only transport.** ADR-027's mechanism exactly: `OrderSubmissionRequestedPublisher.toContractPayload(event: OrderSubmissionRequested): OrderSubmissionContractPayload` (provider side, primitive-typed `passengerReference: String`) is proven compatible with Order Management's `OrderSubmissionRequestHandler.handle(passengerReference: String): String` (consumer side) **only** by `OrderSubmissionContractVerificationTest`, which holds a `testImplementation(project(":order-management"))` Gradle dependency — test-scoped only, never a main-source dependency, per ADR-027's own rule.
- **Missing REST pieces.** Everything: a controller (if any is needed — Part 2 below), a REST client, request/response models, error handling, timeout/retry configuration, and an integration test against a real running Order Management instance.

### Order Management

- **Existing order submission entry points.** `OrderSubmissionRequestHandler.handle(passengerReference: String): String` — validates `passengerReference` is non-blank, discards it (never attached to the Order aggregate, per its own KDoc), and calls `OrderLifecycleApplicationService.submitOrder(SubmitOrderCommand())`. **[RATIFIED — observed directly.]** Invoked today only by `OrderSubmissionContractVerificationTest`, never by any production caller.
- **Application service boundary.** `OrderLifecycleApplicationService.submitOrder(command: SubmitOrderCommand): SubmittedOrder` — already fully implemented, tested, and (since Tranche 1's predecessor work) wired to the outbox/RabbitMQ pipeline publishing `OrderSubmitted`. Order Management's own `SubmitOrderCommand` is parameterless — distinct from Passenger Experience's own, differently-shaped `SubmitOrderCommand(passenger: PassengerReference)`.
- **Expected request model.** Exactly the primitive shape ADR-027 already fixed: a single `passengerReference: String`, required non-blank, nothing else. No richer request model exists or is implied by any ratified document.
- **No REST controller exists** in Order Management either — confirmed by the same repository-wide inspection.

## Part 2 — Authority Check

**What ADR-027 decided.** A provisional, explicitly non-runtime mechanism: each module gains a primitive-typed application-layer boundary (provider translates its domain event to a primitive payload; consumer accepts that primitive payload), verified exclusively through a test-scoped Gradle dependency — never a production dependency, never actual message delivery between independently running services. ADR-027's own text: *"The MVP's running services do not yet actually deliver events to one another at runtime... A future ADR selecting a real transport is required."*

**What ADR-028 decided.** Transport *category* by interaction type, not by module: **events go through a message broker** (six catalogued events: OrderSubmitted, OrderCancelled, OrderCompleted, OrderAssigned, AssignmentAccepted, DriverAvailabilityChanged); **direct, request-driven interaction goes through REST**, following ADR-004's Resource-Oriented/Versioned/Consistent principles, explicitly "between a capability and its callers, **whether external clients or other capabilities**, without distinction."

**Where Submit Order falls.** DOMAIN_MODEL.md Section 9 lists "Submit Order" as a **command**, not an event; APPLICATION_ARCHITECTURE.md Section 8 confirms a command uses "synchronous interaction... (ADR-004)," distinct from "reaction to events... (ADR-003)." Confirming this independently: `OrderSubmissionRequested` (Passenger Experience's own representation) is **not** one of the six events EVENT_CATALOG.md catalogues — it was never assigned to the message-broker category at all. Submit Order is therefore, by ADR-003/004/028's own category split, squarely a **direct, request-driven interaction — REST, never a message broker.**

**Does the repository reflect the selected decision?** No. This is the one remaining MVP flow still exclusively on ADR-027's provisional mechanism. (Contrast: Driver Management → Dispatch, and — since Tranche 1 — Dispatch → Order Management, both now run for real on ADR-028's *other* category, RabbitMQ.) ADR-028's REST category, though decided, has zero implementation anywhere for this specific contract.

**Endpoint-ownership tension, surfaced, not silently resolved.** USE_CASE_CATALOG.md UC-001 narrates that "Passenger Experience represents the passenger's interaction with the platform throughout" — readable as Passenger Experience being the passenger-facing entry point. But three more specific, more directly-on-point sources converge on a different attribution: INTERFACE_CONTRACTS.md Section 9 ("**Passenger.** Interacts with Order Management (UC-001) and Passenger Experience (UC-002)"); API_SPECIFICATION.md Section 6 ("**Submit Order** — toward Order Management, available to Passenger and Corporate Customer"); and API_SPECIFICATION.md Section 5's own Passenger Experience entry ("No dedicated command or query is currently established beyond the originating context it provides to Order Management's Submit Order... **this contract is limited to representation, not a discrete transactional interaction**"). **[OPEN]** — this document does not resolve which literal external client (a Passenger's own app) calls Passenger Experience or Order Management first; that is an External Boundary question (API_SPECIFICATION.md Section 3) outside this tranche's charter. What this document *does* design, unambiguously, is the **internal** Passenger Experience → Order Management leg, whose direction is already fixed by the existing, already-implemented contract: Passenger Experience is the **provider/caller** of the `OrderSubmissionRequested`-shaped payload; Order Management is the **consumer/receiver**. Closing that internal transport gap does not require resolving the external question first.

| Item | Classification | Reasoning |
| --- | --- | --- |
| `OrderLifecycleApplicationService`, `OrderSubmissionRequestHandler`, Order Management's existing outbox/RabbitMQ pipeline | **KEEP** | Already correct, already tested; no logic change needed |
| `PassengerOrderSubmissionApplicationService`, `OrderSubmissionIntent`, `OrderSubmissionRequested`, `OrderSubmissionRequestedPublisher` | **KEEP** | Already correct domain/application modeling; gains a real caller, not new logic |
| `OrderSubmissionContractVerificationTest` | **KEEP** | Verifies application-layer shape compatibility, unaffected by adding real transport alongside it |
| Order Management's Submit Order REST endpoint | **MISSING** | Confirmed absent by direct inspection |
| Passenger Experience's REST client to Order Management | **MISSING** | Confirmed absent by direct inspection |
| Request/response DTOs, error handling, timeout/retry config | **MISSING** | None exist anywhere in either module |
| Which external client calls Passenger Experience vs. Order Management first | **DEFER** | External Boundary question, not required to close this tranche's internal gap |
| Any richer Submit Order request model (Order Origin, pricing) | **DEFER** | Unrelated to transport; out of scope per Part 4 |

## Part 3 — Implementation Design

### 1. Order Management's REST Endpoint (Submit Order)

**Endpoint ownership.** **[DERIVED]** Order Management, not Passenger Experience — per Part 2's converging evidence (API_SPECIFICATION.md Sections 5–6; INTERFACE_CONTRACTS.md Section 9). Passenger Experience's own ratified contract is explicitly "not a discrete transactional interaction"; it gains no new inbound endpoint in this tranche (Part 4 confirms this as a non-goal, not an oversight).

**Request/response model.** **[DERIVED]** from the already-fixed ADR-027 primitive shape: request `{ "passengerReference": "<string>" }`; response `{ "orderId": "<string>" }`, mirroring `OrderSubmissionRequestHandler.handle`'s own existing return value exactly. No field beyond these two is introduced.

**Validation boundary.** **[DERIVED]** The controller performs only a transport-level presence check (is the field there at all); the already-existing, already-tested business validation (`require(passengerReference.isNotBlank())` inside `OrderSubmissionRequestHandler`) remains the sole authority — never duplicated, consistent with APPLICATION_ARCHITECTURE.md Section 2's No Business Logic Duplication.

**Error handling.** **[DERIVED]** A blank/missing `passengerReference` surfaces as the `IllegalArgumentException` `OrderSubmissionRequestHandler` already throws; the controller maps this to HTTP 400, consistent with API_SPECIFICATION.md Section 10 ("communicates that a request could not proceed without exposing the internal reasoning… no HTTP status code… defined [there]" — this plan is the first document to fix one, scoped narrowly to this one endpoint). No other domain-specific error case exists today (`Order.submit()` itself never throws).

### 2. Communication with Order Management (from Passenger Experience)

**REST client approach.** **[DERIVED]** Spring's own `RestClient` (Spring Framework 6.1+, synchronous, already transitively available via `spring-boot-starter-web`/`spring-web` — already a dependency in both modules; **no new dependency required**), mirroring this codebase's own established convention of using Spring's simplest built-in mechanism rather than a third-party client library (`RabbitTemplate` for messaging, plain `JdbcTemplate` for persistence).

**Transport abstraction.** **[DERIVED]** A small, Passenger-Experience-local port, mirroring the already-proven `EventPublisher`/`TransactionRunner` pattern exactly: an application-layer interface (`OrderSubmissionClient`, one method: `fun submit(passengerReference: String): String`) that keeps the application layer framework-independent; a `RestClientOrderSubmissionClient` adapter in a new `persistence` package (Passenger Experience currently has none, since it has no database — this adapter is the first non-database occupant of that package, consistent with how `RabbitMQEventPublisher` already lives in each other module's own `persistence` package despite not being a database adapter either) performs the actual HTTP call.

**Timeout/retry behavior.** **[DERIVED]** A bounded connect/read timeout (a few seconds, the same order of magnitude as the RabbitMQ publisher-confirm timeout, 5000ms, already used elsewhere in this codebase), configured on the underlying `ClientHttpRequestFactory`. **No automatic retry** — Submit Order is not naturally idempotent at the HTTP layer without a client-generated idempotency key, which does not exist today (Part 9's central risk); adding blind retry now would risk creating duplicate orders, contradicting No Premature Optimization by solving a problem without first establishing the idempotency mechanism it would need.

**Failure handling.** **[DERIVED]** A connection failure, timeout, or non-2xx response propagates as a clear exception to whatever invoked the client — no silent swallowing, no fallback order-creation path.

### 3. Existing Application Services

**What can be reused, unchanged.** `PassengerOrderSubmissionApplicationService`, `OrderSubmissionIntent`, `OrderSubmissionRequested`, `OrderSubmissionRequestedPublisher` (Passenger Experience); `OrderLifecycleApplicationService`, `OrderSubmissionRequestHandler`, `SubmitOrderCommand` (Order Management) — none needs any logic change; each gains a real caller, never new business logic.

**What must remain unchanged.** `Order.submit()`'s own domain logic; Tranche 1's outbox/RabbitMQ publishing pipeline; every existing test.

## Part 4 — Domain Safety

**Confirmed no changes to:** Order meaning, Assignment meaning, Dispatch, any event (`OrderSubmitted`, `OrderAssigned`, `AssignmentAccepted`, `DriverAvailabilityChanged`), or any driver concept — this tranche adds only transport plumbing around an already-fixed, already-tested application-layer contract.

**Confirmed the transport layer introduces none of:** Opportunity, Eligibility, Fairness, pricing, or payment logic. The REST request/response carries exactly the `passengerReference` string ADR-027 already fixed — nothing more. **Explicit non-goal:** no new inbound Passenger Experience endpoint is created (Part 3.1); resolving the external-boundary question (Part 2) is deferred, not attempted here.

## Part 5 — File Impact

**Files likely modified.** Order Management's `build.gradle.kts` and Passenger Experience's `build.gradle.kts` — likely **no change needed**; `spring-boot-starter-web`/`spring-web` (providing both the server side and `RestClient`) is already present in both.

**Files likely created (Order Management):**

- A new `com.pios.ordermanagement.api` package (no API/controller layer exists anywhere in this codebase yet — this is the first) containing a controller (for example, `OrderSubmissionController.kt` — **[HYPOTHESIS]**, exposing `POST /v1/orders` — **[HYPOTHESIS]**, exact path not fixed by any document), a request DTO (`SubmitOrderRequest(passengerReference: String)`), a response DTO (`SubmitOrderResponse(orderId: String)`), reusing `OrderSubmissionRequestHandler` unchanged as the controller's only collaborator.

**Files likely created (Passenger Experience):**

- `OrderSubmissionClient.kt` (application-layer port).
- A new `com.pios.passengerexperience.persistence` package (does not exist yet) containing `RestClientOrderSubmissionClient.kt` (the adapter) and a small HTTP configuration class providing the configured `RestClient` bean.

**Files intentionally untouched.** Every domain class in both modules; `OrderLifecycleApplicationService.kt`, `Order.kt`, everything in `dispatch`/`driver-management`; every existing migration; every existing test — including `OrderSubmissionContractVerificationTest`, which remains valid per ADR-027's own "remains the only [mechanism] actually in effect" language until this real transport is proven end-to-end.

## Part 6 — Database Impact

**No migration required.** Order Management's existing `orders` table is already sufficient — `Order.submit()` is unchanged. Passenger Experience has no database at all, and none is required: the REST call is a pure pass-through with no state to persist on Passenger Experience's own side.

## Part 7 — Test Plan

- **Controller tests (Order Management).** Constructing the new controller directly with a real `OrderLifecycleApplicationService`/`OrderSubmissionRequestHandler` (mirroring this project's own constructor-based, no-Spring-context convention): a valid request returns success with an order id; a blank `passengerReference` returns 400.
- **REST client tests (Passenger Experience).** Running the new controller against a real, separately-started embedded server (mirroring how this project's existing RabbitMQ/PostgreSQL integration tests already run against a real, separately-started service rather than a mock) to prove `RestClientOrderSubmissionClient` actually reaches it and returns the submitted order id.
- **Integration tests.** An end-to-end test starting Order Management's actual embedded server and exercising Passenger Experience's client against it for the full round trip.
- **Failure scenarios.** Connection refused (Order Management not running) → a clear, distinct exception; timeout → the same; a validation failure (blank passenger reference) → HTTP 400, distinguishable from a connection failure.
- **Contract verification.** `OrderSubmissionContractVerificationTest` remains unmodified and continues to pass — it verifies shape compatibility, which this tranche does not change, only adds real transport alongside.

## Part 8 — Implementation Order

1. Define the REST request/response shapes (Order Management's own, matching the already-fixed ADR-027 primitive contract).
2. Implement Order Management's Submit Order controller, reusing `OrderSubmissionRequestHandler` unchanged.
3. Implement Passenger Experience's `OrderSubmissionClient` port and `RestClientOrderSubmissionClient` adapter.
4. Wire a real caller of `PassengerOrderSubmissionApplicationService` to invoke the client after raising `OrderSubmissionRequested`, using `OrderSubmissionRequestedPublisher`'s own existing payload shape as the request body.
5. Add tests (controller, client, integration).
6. Verify end-to-end: start both services, submit an order via Passenger Experience's own application layer, confirm Order Management actually creates the Order and Tranche 1's own outbox/RabbitMQ pipeline still fires `OrderSubmitted` unchanged.

## Part 9 — Risks

- **Contract mismatch.** Low — the REST shape must exactly match what `OrderSubmissionRequestHandler`/`OrderSubmissionRequestedPublisher` already fixed; both already exist and are already tested, so the new controller/client are thin wrappers, not new logic.
- **Transport failures.** Connection refused or timeout if Order Management isn't running — must propagate clearly, never silently succeed.
- **Duplicate order submission.** **The central, genuine risk.** A retried REST call (client-side retry, or a real double-submission) creates **two distinct orders** today, since `OrderLifecycleApplicationService.submitOrder` has no idempotency concept at all — unlike the event-consumption side (Tranche 1), which already has `markProcessed`. This is exactly why Part 3.2 recommends **no automatic retry** in this tranche; idempotent Submit Order (for example, a client-supplied idempotency key) is named here as a real, separate, future concern, not silently solved.
- **Timeout handling.** An unbounded timeout could make Passenger Experience's own caller hang indefinitely; a bounded timeout (Part 3.2) is required.
- **Validation drift.** The controller's own check and `OrderSubmissionRequestHandler`'s own existing check must never diverge — mitigated by having the controller perform only transport-level presence checking, leaving all business validation to the already-existing handler.

## Part 10 — Acceptance Criteria

- A passenger request can enter through a real, running Order Management REST endpoint — not only test code.
- Passenger Experience's own application layer can reach that endpoint via a real HTTP call, using only the already-fixed ADR-027 primitive contract shape.
- Order Management's existing order lifecycle, and Tranche 1's own outbox/RabbitMQ publishing, remain completely unchanged in behavior.
- A connection failure or timeout is handled distinctly from a validation failure; neither fails silently.
- Every existing test, including `OrderSubmissionContractVerificationTest`, continues to pass unmodified.
- No Opportunity, Eligibility, Fairness, pricing, or payment concept appears anywhere in the diff.

## Files Changed (This Planning Task)

`docs/IMPLEMENTATION_PLAN_TRANCHE_2_PASSENGER_REST_TRANSPORT.md` (new) is the only content file this task creates. `docs/README.md` receives one minimal traceability pointer. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this task.

---

Where this plan states a **[HYPOTHESIS]**-graded detail (an exact endpoint path, class, or package name) or an **[OPEN]** item, it is a recommendation or a surfaced ambiguity, not a ratified or verified fact — the implementer confirms or resolves it before writing code. Acting on this plan requires separate, explicit implementation approval.
