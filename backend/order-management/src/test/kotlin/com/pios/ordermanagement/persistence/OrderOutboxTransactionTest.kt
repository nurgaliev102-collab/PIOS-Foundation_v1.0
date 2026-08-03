package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OutboxRecord
import com.pios.ordermanagement.application.OutboxRepository
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves the core guarantee Outbox Foundation v1.0 exists to establish
 * (ADR-032): an Order's own state change and its corresponding outbox
 * record are saved in one atomic PostgreSQL transaction, against a real
 * database — not merely that each repository works in isolation.
 */
class OrderOutboxTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val orderRepository = PostgreSQLOrderRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val objectMapper = ObjectMapper()
    private val service = OrderLifecycleApplicationService(orderRepository, outboxRepository, transactionRunner, objectMapper)
    private val origin = OrderOrigin("origin-1")

    @Test
    fun `submitting an order persists both the order and a matching outbox record together`() {
        val submitted = service.submitOrder(SubmitOrderCommand(origin))

        assertEquals(OrderStatus.SUBMITTED, orderRepository.findById(submitted.order.id)?.status)
        val records = outboxRepository.findUnpublished().filter { it.aggregateId == submitted.order.id.value }
        assertEquals(1, records.size)
        assertEquals("OrderSubmitted", records.single().eventType)
        assertEquals("order.submitted", records.single().routingKey)
    }

    @Test
    fun `completing an order persists both the updated order and a matching outbox record together`() {
        val submitted = service.submitOrder(SubmitOrderCommand(origin))

        service.completeOrder(CompleteOrderCommand(submitted.order.id))

        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(submitted.order.id)?.status)
        val records = outboxRepository.findUnpublished()
            .filter { it.aggregateId == submitted.order.id.value && it.eventType == "OrderCompleted" }
        assertEquals(1, records.size)
        assertEquals("order.completed", records.single().routingKey)
    }

    @Test
    fun `if the outbox save fails, the order's own state change is rolled back too`() {
        val submitted = service.submitOrder(SubmitOrderCommand(origin))

        val failingOutboxRepository = object : OutboxRepository {
            override fun save(record: OutboxRecord): OutboxRecord = throw RuntimeException("simulated outbox failure")
            override fun findUnpublished(): List<OutboxRecord> = emptyList()
            override fun markPublished(id: Long) = Unit
            override fun countUnpublished(): com.pios.ordermanagement.application.OutboxBacklog =
                com.pios.ordermanagement.application.OutboxBacklog(pending = 0, oldestPendingCreatedAt = null)
        }
        val failingService = OrderLifecycleApplicationService(orderRepository, failingOutboxRepository, transactionRunner, objectMapper)

        assertFailsWith<RuntimeException> {
            failingService.completeOrder(CompleteOrderCommand(submitted.order.id))
        }

        // The order's status must still be SUBMITTED in the database --
        // the failed outbox save must have rolled back the order's own
        // status update within the same transaction, not just its own.
        assertEquals(OrderStatus.SUBMITTED, orderRepository.findById(submitted.order.id)?.status)
        assertTrue(outboxRepository.findUnpublished().none { it.aggregateId == submitted.order.id.value && it.eventType == "OrderCompleted" })
    }
}
