package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.FallbackDispatchApplicationService
import com.pios.dispatch.application.FirstRefusalApplicationService
import com.pios.dispatch.application.FirstRefusalOutcome
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Dispatch's consumption of Order Management's
 * `OrderSubmitted` (ADR-062, Primary Driver / First Refusal; Task 16,
 * First Refusal Runtime Integration). The only place in this class a
 * RabbitMQ/Spring AMQP type ([org.springframework.amqp.rabbit.annotation.RabbitListener])
 * appears — mirrors [OrderCancelledListener]/[DriverAvailabilityChangedListener]'s
 * own exact discipline, on its own, separate queue
 * ([RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME]).
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything, for the identical reason and with the identical
 * consequence [OrderCancelledListener]'s own KDoc already documents: an
 * exhausted retry (`RabbitMQListenerContainerConfiguration`, ADR-031)
 * routes the message to this queue's own dead-letter queue rather than
 * discarding or misinterpreting it.
 *
 * ## Calls [FirstRefusalApplicationService.attempt] directly — no
 * intermediate application service
 *
 * Unlike [DriverAvailabilityChangedListener]/[OrderCancelledListener],
 * which each translate into a primitive-typed command before calling a
 * *dedicated* consumer-side application service, this listener calls
 * [FirstRefusalApplicationService.attempt] directly — that method is
 * itself already the correct, already-existing (Task 14), already-proven
 * (Task 15C) application-layer entry point for exactly this decision;
 * introducing a second, wrapping application service here would only
 * indirect through it for no reason, mirroring how `ProposalController`
 * (a controller, the same transport-boundary role) already constructs
 * [OrderReference]/[com.pios.dispatch.domain.DriverReference] directly at
 * its own boundary before calling straight into `ProposalApplicationService.handle`.
 *
 * ## Deliberately no new idempotency ledger (Task 16's own Phase 3 —
 * "do not create another ad-hoc deduplication mechanism unless the
 * existing event-consumer architecture requires it")
 *
 * [DriverAvailabilityChangedListener]'s and [OrderCancelledListener]'s own
 * consumers each need a `markProcessed(eventId)` ledger because their own
 * effects (re-toggling availability; withdrawing a Proposal that may have
 * since moved on to a different state) would be **wrong** to reapply
 * blindly on redelivery. [FirstRefusalApplicationService.attempt]'s own
 * effect — creating a Proposal — has no such hazard: it is already
 * idempotent by construction, layered exactly as Task 14/15C built and
 * proved it — `Proposal.propose`'s own in-memory root invariant, backed
 * by `proposals_one_open_per_order` (V14) at the database level — so a
 * redelivered `OrderSubmitted` reaching this listener a second time,
 * while the Proposal the first delivery created is still `OPEN`, produces
 * [com.pios.dispatch.application.FirstRefusalOutcome.AlreadyAttempted]
 * cleanly, with no new mechanism required. A new ledger here would be
 * exactly the ad-hoc, unnecessary deduplication this task's own
 * instructions warn against adding.
 *
 * **Disclosed, narrow residual gap, not closed by this design**: if a
 * redelivery of the *same* `eventId` arrives only *after* the Proposal
 * the first delivery created has already resolved (accepted, declined, or
 * lapsed) — a redelivery window far longer than
 * `RabbitMQListenerContainerConfiguration`'s own bounded retry policy
 * (3 attempts, exponential backoff capped at 2000ms) would ordinarily
 * allow, and far longer than any real driver's own response time — the
 * Root Invariant no longer blocks a *second* Proposal for the same
 * primary driver, since the order's only Proposal is no longer `OPEN`.
 * This is the same class of at-least-once-delivery tolerance ADR-031
 * already accepts platform-wide ("effectively-once business effect only
 * because every current consumer is required to be idempotent") — not a
 * new risk this listener introduces, and not one this task's own scope
 * (Section 22 of its own final report) treats as blocking.
 *
 * Extracts `payload.orderId`, `payload.passengerReference`,
 * `payload.explicitDriverIntent`, and `payload.isTest` — exactly what
 * ADR-062 and this task's own Phase 0 finding (Task 15C omitted `isTest`)
 * justify, nothing from Order Management's own domain types. Never
 * imports any Order Management type.
 *
 * ## Fallback Dispatch (FR-003A)
 *
 * After [firstRefusalApplicationService] returns, [fallbackDispatchApplicationService]
 * is attempted for exactly the two outcomes that mean "no Proposal exists
 * yet and none is coming from First Refusal itself":
 * [FirstRefusalOutcome.NoPrimaryDriver] and
 * [FirstRefusalOutcome.PrimaryDriverIneligible]. The other three outcomes
 * skip it: [FirstRefusalOutcome.Proposed] and
 * [FirstRefusalOutcome.AlreadyAttempted] already mean a Proposal exists;
 * [FirstRefusalOutcome.ExplicitDriverIntentDeclared] means the passenger is
 * choosing their own driver through a separate flow, which Fallback Dispatch
 * must not preempt.
 */
@Component
class OrderSubmittedFirstRefusalListener(
    private val firstRefusalApplicationService: FirstRefusalApplicationService,
    private val fallbackDispatchApplicationService: FallbackDispatchApplicationService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OrderSubmittedFirstRefusalListener::class.java)

    @RabbitListener(
        queues = [RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME}: '$eventType'"
        }

        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion == SUPPORTED_EVENT_VERSION) {
            "Unsupported $SUPPORTED_EVENT_TYPE eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSION)"
        }

        val eventId = envelope.get("eventId")?.asText()
        require(!eventId.isNullOrBlank()) { "Missing eventId" }

        val data = envelope.get("payload")
        requireNotNull(data) { "Missing payload" }

        val orderId = data.get("orderId")?.asText()
        require(!orderId.isNullOrBlank()) { "Missing payload.orderId" }

        val passengerReference = data.get("passengerReference")?.asText()
        require(!passengerReference.isNullOrBlank()) { "Missing payload.passengerReference" }

        val explicitDriverIntent = data.get("explicitDriverIntent")?.asBoolean() ?: false
        val isTest = data.get("isTest")?.asBoolean() ?: false

        val orderReference = OrderReference(orderId)
        val passengerRef = PassengerReference(passengerReference)

        val outcome = firstRefusalApplicationService.attempt(
            order = orderReference,
            passengerReference = passengerRef,
            isTest = isTest,
            explicitDriverIntentDeclared = explicitDriverIntent
        )
        logger.info(
            "OrderSubmitted (eventId {}) processed for order {}: First Refusal outcome {}",
            eventId,
            orderId,
            outcome::class.simpleName
        )

        if (outcome is FirstRefusalOutcome.NoPrimaryDriver || outcome is FirstRefusalOutcome.PrimaryDriverIneligible) {
            val fallbackOutcome = fallbackDispatchApplicationService.attempt(
                order = orderReference,
                passengerReference = passengerRef,
                isTest = isTest
            )
            logger.info(
                "OrderSubmitted (eventId {}) order {}: Fallback Dispatch outcome {}",
                eventId,
                orderId,
                fallbackOutcome::class.simpleName
            )
        }
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "OrderSubmitted"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
