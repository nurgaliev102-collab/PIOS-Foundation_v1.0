package com.pios.ordermanagement.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderCancelled
import com.pios.ordermanagement.domain.OrderCompleted
import com.pios.ordermanagement.domain.OrderSubmitted
import com.pios.ordermanagement.domain.SubmittedOrder
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Application-layer coordination for Order Management's lifecycle commands
 * (APPLICATION_ARCHITECTURE.md Section 6). This service sequences each
 * command into the Order aggregate's own behavior; it does not decide an
 * order's status itself (APPLICATION_ARCHITECTURE.md Section 2, "Domain
 * Decides Business Meaning"). After each transition, the service persists
 * the affected [Order] *and* a corresponding [OutboxRecord] together,
 * inside one [transactionRunner] boundary (ADR-032, Outbox Foundation
 * v1.0) — so the two either both commit or both roll back, closing the
 * dual-write gap ADR-031 identified. The [Order] aggregate itself remains
 * entirely unaware that a repository or an outbox exists.
 *
 * [transactionRunner] and [outboxRepository] default to no-ops so that
 * existing tests exercising only order lifecycle behavior (not the
 * outbox) continue to work unchanged; a real, Spring-wired instance of
 * this service always receives the real
 * [com.pios.ordermanagement.persistence.SpringTransactionRunner] and
 * [com.pios.ordermanagement.persistence.PostgreSQLOutboxRepository] beans
 * instead, since Spring's constructor injection always supplies an
 * argument for a parameter it can resolve a bean for, regardless of a
 * Kotlin default being present.
 *
 * [transactionRunner] deliberately names only this module's own
 * [TransactionRunner] abstraction, never a transaction-management
 * framework type directly — this application-layer class stays
 * framework-independent, exactly as [OrderRepository]'s own KDoc already
 * requires of persistence. It is also what lets this class remain
 * constructible from another module's test code across a project
 * dependency (see [com.pios.ordermanagement.persistence.SpringTransactionRunner]'s
 * KDoc for why this matters concretely).
 *
 * [completeOrder] and [cancelOrder] are each overloaded: one form still
 * operates on an [Order] instance supplied by the caller, the other
 * restores the [Order] from [orderRepository] by id first, proving the
 * aggregate can be saved, loaded back, and continue its own domain
 * operation exactly as it would if it had never left memory.
 */
@Service
class OrderLifecycleApplicationService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {

    /**
     * Coordinates a [SubmitOrderCommand], producing a new order and the
     * [com.pios.ordermanagement.domain.OrderSubmitted] event recording it,
     * persisting the new order and its outbox record together.
     */
    fun submitOrder(command: SubmitOrderCommand): SubmittedOrder = transactionRunner.run {
        val submitted = Order.submit(
            command.origin,
            command.destination,
            command.passengerName,
            command.pickupAddress,
            command.requestedPickupAt,
            command.isTest
        )
        orderRepository.save(submitted.order)
        outboxRepository.save(outboxRecordFor(submitted.event))
        submitted
    }

    /**
     * Coordinates a [CompleteOrderCommand] against the given [order],
     * returning the resulting [OrderCompleted] event and persisting the
     * order's new status and its outbox record together.
     */
    fun completeOrder(order: Order, command: CompleteOrderCommand): OrderCompleted = transactionRunner.run {
        require(order.id == command.orderId) {
            "Command targets order ${command.orderId.value} but was handled against order ${order.id.value}"
        }
        val event = order.complete()
        orderRepository.save(order)
        outboxRepository.save(outboxRecordFor(event))
        event
    }

    /**
     * Coordinates a [CompleteOrderCommand] by first restoring the
     * targeted Order through [orderRepository]. Throws
     * [OrderNotFoundException] — an application-layer error, never a
     * persistence or domain one (see that class's own KDoc) — if no
     * Order identified by [CompleteOrderCommand.orderId] has been saved.
     */
    fun completeOrder(command: CompleteOrderCommand): OrderCompleted = transactionRunner.run {
        val order = orderRepository.findById(command.orderId) ?: throw OrderNotFoundException(command.orderId)
        completeOrder(order, command)
    }

    /**
     * Coordinates a [CancelOrderCommand] against the given [order],
     * returning the resulting [OrderCancelled] event and persisting the
     * order's new status and its outbox record together.
     */
    fun cancelOrder(order: Order, command: CancelOrderCommand): OrderCancelled = transactionRunner.run {
        require(order.id == command.orderId) {
            "Command targets order ${command.orderId.value} but was handled against order ${order.id.value}"
        }
        val event = order.cancel()
        orderRepository.save(order)
        outboxRepository.save(outboxRecordFor(event))
        event
    }

    /**
     * Coordinates a [CancelOrderCommand] by first restoring the targeted
     * Order through [orderRepository]. Throws [OrderNotFoundException] —
     * an application-layer error, never a persistence or domain one (see
     * that class's own KDoc) — if no Order identified by
     * [CancelOrderCommand.orderId] has been saved.
     */
    fun cancelOrder(command: CancelOrderCommand): OrderCancelled = transactionRunner.run {
        val order = orderRepository.findById(command.orderId) ?: throw OrderNotFoundException(command.orderId)
        cancelOrder(order, command)
    }

    private fun outboxRecordFor(event: OrderSubmitted): OutboxRecord = OutboxRecord(
        aggregateId = event.orderId.value,
        eventType = "OrderSubmitted",
        routingKey = "order.submitted",
        payload = envelopeFor("OrderSubmitted", event.orderId.value, event.occurredAt.toString())
    )

    private fun outboxRecordFor(event: OrderCompleted): OutboxRecord = OutboxRecord(
        aggregateId = event.orderId.value,
        eventType = "OrderCompleted",
        routingKey = "order.completed",
        payload = envelopeFor("OrderCompleted", event.orderId.value, event.occurredAt.toString())
    )

    private fun outboxRecordFor(event: OrderCancelled): OutboxRecord = OutboxRecord(
        aggregateId = event.orderId.value,
        eventType = "OrderCancelled",
        routingKey = "order.cancelled",
        payload = envelopeFor("OrderCancelled", event.orderId.value, event.occurredAt.toString())
    )

    /**
     * Builds the minimum transport envelope RabbitMQ Event Publishing
     * Foundation v1.0 requires: a stable [eventId] identifying this one
     * occurrence of the event (generated exactly once, here, at event
     * creation — never regenerated by the relay or publisher, so a retried
     * publish of the same persisted outbox record always carries the same
     * eventId); an explicit [eventVersion] (ADR-030: version is a property
     * of the event itself); the domain's own business [occurredAt],
     * distinct from the outbox row's own persistence timestamps
     * ([OutboxRecord.createdAt]/[OutboxRecord.publishedAt]); and the
     * event-specific data nested under `payload`, keeping the envelope
     * fields themselves free of any Order-specific shape.
     */
    private fun envelopeFor(eventType: String, orderId: String, occurredAt: String): String =
        objectMapper.writeValueAsString(
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to eventType,
                "eventVersion" to 1,
                "occurredAt" to occurredAt,
                "payload" to mapOf("orderId" to orderId)
            )
        )
}
