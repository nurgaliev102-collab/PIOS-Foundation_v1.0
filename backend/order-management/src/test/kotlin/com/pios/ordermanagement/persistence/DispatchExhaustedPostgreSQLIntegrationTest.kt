package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.CancelOrderCommand
import com.pios.ordermanagement.application.DispatchExhaustedApplicationService
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatchExhaustedPostgreSQLIntegrationTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val orders = PostgreSQLOrderRepository(JdbcTemplate(dataSource))
    private val outbox = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val lifecycle = OrderLifecycleApplicationService(orders, outbox, transactionRunner, ObjectMapper())
    private val service = DispatchExhaustedApplicationService(orders, lifecycle, transactionRunner)
    private val listener = DispatchExhaustedListener(service, ObjectMapper())

    @Test
    fun `a dispatch exhaustion event closes the persisted order exactly once`() {
        val order = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-${UUID.randomUUID()}"))).order
        val message = envelope(order.id.value)

        listener.onMessage(message)
        listener.onMessage(message)

        assertEquals(OrderStatus.UNFULFILLED, orders.findById(order.id)?.status)
        assertEquals(1, outbox.findUnpublished().count {
            it.aggregateId == order.id.value && it.eventType == "OrderUnfulfilled"
        })
    }

    @Test
    fun `a late exhaustion event cannot overwrite a passenger cancellation`() {
        val order = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-${UUID.randomUUID()}"))).order
        lifecycle.cancelOrder(CancelOrderCommand(order.id))

        listener.onMessage(envelope(order.id.value))

        assertEquals(OrderStatus.CANCELLED, orders.findById(order.id)?.status)
        assertEquals(0, outbox.findUnpublished().count {
            it.aggregateId == order.id.value && it.eventType == "OrderUnfulfilled"
        })
    }

    private fun envelope(orderId: String): String = ObjectMapper().writeValueAsString(
        mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to "DispatchExhausted",
            "eventVersion" to 1,
            "occurredAt" to Instant.now().toString(),
            "payload" to mapOf("orderId" to orderId, "reason" to "NO_OFFER_WITHIN_WINDOW")
        )
    )
}
