package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.AssignmentCompletedApplicationService
import com.pios.ordermanagement.application.CancelOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves ADR-041's own end-to-end goal for Order Management's second
 * consumer: a real AssignmentCompleted-shaped message, published to the
 * real `dispatch.events` exchange exactly as Dispatch's own publisher
 * would, is consumed by Order Management's real listener on its own,
 * separate queue and completes the referenced Order in the real
 * PostgreSQL database. Mirrors [AssignmentAcceptedConsumerIntegrationTest]
 * exactly, adapted to this flow's own observable effect: unlike
 * AssignmentAccepted's handler, this consumer actually transitions
 * `Order.status`.
 */
class AssignmentCompletedConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLAssignmentCompletedRepository(JdbcTemplate(dataSource))
    private val orderRepository = PostgreSQLOrderRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val orderLifecycleApplicationService =
        OrderLifecycleApplicationService(orderRepository, outboxRepository, transactionRunner)
    private val applicationService = AssignmentCompletedApplicationService(
        repository,
        orderRepository,
        orderLifecycleApplicationService,
        transactionRunner
    )
    private val listener = AssignmentCompletedListener(applicationService, ObjectMapper())
    private val publisher = AssignmentCompletedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = AssignmentCompletedTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `a valid AssignmentCompleted message completes the referenced SUBMITTED order in PostgreSQL`() {
        val submitted = orderLifecycleApplicationService.submitOrder(
            SubmitOrderCommand(OrderOrigin("consumer-passenger-completed"))
        )
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(
            orderReference = submitted.order.id.value,
            eventId = eventId
        )

        val processed = awaitUntilNotNull { repository.isProcessed(eventId).takeIf { it } }
        assertEquals(true, processed)
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `two distinct AssignmentCompleted messages both complete their own orders`() {
        val firstOrder = orderLifecycleApplicationService.submitOrder(
            SubmitOrderCommand(OrderOrigin("consumer-passenger-completed-1"))
        )
        val secondOrder = orderLifecycleApplicationService.submitOrder(
            SubmitOrderCommand(OrderOrigin("consumer-passenger-completed-2"))
        )
        val firstEventId = UUID.randomUUID().toString()
        val secondEventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(orderReference = firstOrder.order.id.value, eventId = firstEventId)
        publisher.publishAssignmentCompleted(orderReference = secondOrder.order.id.value, eventId = secondEventId)

        assertEquals(true, awaitUntilNotNull { repository.isProcessed(firstEventId).takeIf { it } })
        assertEquals(true, awaitUntilNotNull { repository.isProcessed(secondEventId).takeIf { it } })
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(firstOrder.order.id)?.status)
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(secondOrder.order.id)?.status)
    }

    // --- ADR-041 Decision item 3 / Q3 (Product Owner ruling): AssignmentCompleted
    // arriving for an order that is not SUBMITTED is a business conflict, not a
    // transient failure -- the existing status wins, no exception, no DLQ. ---

    @Test
    fun `an AssignmentCompleted message for an already-CANCELLED order leaves it CANCELLED`() {
        val submitted = orderLifecycleApplicationService.submitOrder(
            SubmitOrderCommand(OrderOrigin("consumer-passenger-completed-cancelled"))
        )
        orderLifecycleApplicationService.cancelOrder(CancelOrderCommand(submitted.order.id))
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(orderReference = submitted.order.id.value, eventId = eventId)

        // The idempotency-ledger row is still written for a conflict (ADR-041
        // Q3) -- this is what proves the message was actually received and
        // handled, not merely that nothing crashed.
        assertEquals(true, awaitUntilNotNull { repository.isProcessed(eventId).takeIf { it } })
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `redelivering the same AssignmentCompleted eventId does not reprocess it`() {
        val submitted = orderLifecycleApplicationService.submitOrder(
            SubmitOrderCommand(OrderOrigin("consumer-passenger-completed-redelivered"))
        )
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(orderReference = submitted.order.id.value, eventId = eventId)
        assertEquals(true, awaitUntilNotNull { repository.isProcessed(eventId).takeIf { it } })
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(submitted.order.id)?.status)

        // Redelivery of the exact same eventId (e.g. broker-level at-least-once
        // redelivery): must not throw (the order is no longer SUBMITTED, so a
        // naive re-run would hit Order.complete()'s own precondition check) and
        // must not change anything further.
        publisher.publishAssignmentCompleted(orderReference = submitted.order.id.value, eventId = eventId)
        Thread.sleep(500)
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(submitted.order.id)?.status)
    }
}
