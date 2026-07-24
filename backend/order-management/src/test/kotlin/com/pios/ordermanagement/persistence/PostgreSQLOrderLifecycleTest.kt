package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.CancelOrderCommand
import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the full Order lifecycle through the real PostgreSQL adapter:
 * submit, persist, restore by id (via the id-only overloads introduced
 * in Persistence Quality Foundation v1.0), and continue to completion or
 * cancellation, persisting again — end to end against an actual
 * database, not merely in memory.
 */
class PostgreSQLOrderLifecycleTest {

    private val repository = PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = OrderLifecycleApplicationService(repository)
    private val origin = OrderOrigin("origin-1")

    @Test
    fun `an order submitted through PostgreSQL can be restored by id and completed`() {
        val submitted = service.submitOrder(SubmitOrderCommand(origin))

        val event = service.completeOrder(CompleteOrderCommand(submitted.order.id))

        assertEquals(submitted.order.id, event.orderId)
        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `an order submitted through PostgreSQL can be restored by id and cancelled`() {
        val submitted = service.submitOrder(SubmitOrderCommand(origin))

        val event = service.cancelOrder(CancelOrderCommand(submitted.order.id))

        assertEquals(submitted.order.id, event.orderId)
        assertEquals(OrderStatus.CANCELLED, repository.findById(submitted.order.id)?.status)
    }
}
