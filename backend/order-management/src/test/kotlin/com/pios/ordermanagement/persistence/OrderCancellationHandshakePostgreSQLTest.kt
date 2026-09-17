package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.OrderCancellationCoordinationService
import com.pios.ordermanagement.application.OrderTerminationRecord
import com.pios.ordermanagement.application.RequestOrderCancellationCommand
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrderCancellationHandshakePostgreSQLTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val orders = PostgreSQLOrderRepository(jdbc)
    private val requests = PostgreSQLOrderCancellationRequestRepository(jdbc)
    private val outbox = PostgreSQLOutboxRepository(jdbc)
    private val lifecycle = com.pios.ordermanagement.application.OrderLifecycleApplicationService(
        orders, outbox, transactions, ObjectMapper()
    )
    private fun service() = OrderCancellationCoordinationService(
        orders, requests, lifecycle, outbox, transactions, ObjectMapper()
    )

    @Test
    fun `Dispatch commit followed by Order Management restart resolves once without optimistic cancellation`() {
        val submitted = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-${UUID.randomUUID()}")))
        val orderId = submitted.order.id.value
        val requestId = UUID.randomUUID().toString()
        val command = RequestOrderCancellationCommand(requestId, orderId, "PLANS_CHANGED", null)
        val pending = service().request(command)
        assertEquals("PENDING", pending.outcome)
        assertEquals(OrderStatus.SUBMITTED, orders.findById(submitted.order.id)?.status)
        // The replay path re-reads the row from PostgreSQL (microsecond TIMESTAMPTZ precision,
        // rounded on storage), while `pending` above is the in-memory record built from
        // clock.instant() (nanosecond JVM precision) -- comparing the two data classes directly
        // is a sub-microsecond rounding trap, not a real inequality (e.g. .5640119 in memory can
        // round to .564012 once stored). Compare every field except the timestamp exactly, and
        // assert the timestamp is the same instant to within 1 microsecond.
        val replayed = service().request(command)
        assertEquals(pending.copy(requestedAt = Instant.EPOCH), replayed.copy(requestedAt = Instant.EPOCH))
        assertTrue(
            java.time.Duration.between(pending.requestedAt, replayed.requestedAt).abs() < java.time.Duration.of(1, java.time.temporal.ChronoUnit.MICROS),
            "expected ${pending.requestedAt} and ${replayed.requestedAt} to be the same instant (within DB rounding)"
        )
        val fact = OrderTerminationRecord(
            orderId, requestId, UUID.randomUUID().toString(), "driver-1",
            "PASSENGER", "PLANS_CHANGED", null, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        )
        // A new service instance simulates consumer restart after the Dispatch transaction committed.
        service().resolveTermination(fact)
        assertEquals(OrderStatus.CANCELLED, orders.findById(submitted.order.id)?.status)
        assertEquals("TERMINATED", requests.findById(requestId)?.outcome)
        service().resolveTermination(fact)
        val cancelledEvents = jdbc.queryForObject(
            "SELECT count(*) FROM order_management_outbox WHERE aggregate_id = ? AND event_type = 'OrderCancelled'",
            Long::class.java, orderId
        )
        assertEquals(1L, cancelledEvents)
        assertFailsWith<IllegalStateException> {
            service().resolveTermination(fact.copy(reasonCode = "OTHER"))
        }
    }

    @Test
    fun `precommit cancellation finalizes only after Dispatch acknowledgement`() {
        val submitted = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-${UUID.randomUUID()}")))
        val requestId = UUID.randomUUID().toString()
        service().request(RequestOrderCancellationCommand(requestId, submitted.order.id.value, null, null))
        assertEquals(OrderStatus.SUBMITTED, orders.findById(submitted.order.id)?.status)
        service().resolveNoCommitment(requestId, submitted.order.id.value, "NO_COMMITMENT")
        assertEquals(OrderStatus.CANCELLED, orders.findById(submitted.order.id)?.status)
        service().resolveNoCommitment(requestId, submitted.order.id.value, "NO_COMMITMENT")
        assertNotNull(requests.findById(requestId)?.resolvedAt)
    }

    @Test
    fun `driver termination resolves a pending passenger request even when events are reordered`() {
        val submitted = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-${UUID.randomUUID()}")))
        val passengerRequest = UUID.randomUUID().toString()
        service().request(RequestOrderCancellationCommand(passengerRequest, submitted.order.id.value, "OTHER", null))
        val driverFact = OrderTerminationRecord(
            submitted.order.id.value, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "driver-1", "DRIVER", "CANNOT_FULFILL", null,
            Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        )
        service().resolveTermination(driverFact)
        assertEquals("ALREADY_TERMINATED", requests.findById(passengerRequest)?.outcome)
        service().resolveNoCommitment(passengerRequest, submitted.order.id.value, "ALREADY_TERMINATED")
        assertEquals(OrderStatus.CANCELLED, orders.findById(submitted.order.id)?.status)
    }
}
