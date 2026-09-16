package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatchExhaustedApplicationServiceTest {
    private val orders = InMemoryOrderRepository()
    private val lifecycle = OrderLifecycleApplicationService(orders)
    private val service = DispatchExhaustedApplicationService(orders, lifecycle, NoOpTransactionRunner)

    @Test
    fun `an unmatched submitted order becomes unfulfilled and redelivery is idempotent`() {
        val order = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-one"))).order

        service.handle(order.id.value)
        service.handle(order.id.value)

        assertEquals(OrderStatus.UNFULFILLED, orders.findById(order.id)?.status)
    }

    @Test
    fun `a cancelled order remains cancelled when a late exhaustion event arrives`() {
        val order = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-one"))).order
        lifecycle.cancelOrder(CancelOrderCommand(order.id))

        service.handle(order.id.value)

        assertEquals(OrderStatus.CANCELLED, orders.findById(order.id)?.status)
    }

    @Test
    fun `an unfulfilled order cannot later be completed or cancelled`() {
        val order = lifecycle.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-one"))).order
        service.handle(order.id.value)

        kotlin.test.assertFailsWith<IllegalStateException> {
            lifecycle.completeOrder(CompleteOrderCommand(order.id))
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            lifecycle.cancelOrder(CancelOrderCommand(order.id))
        }
    }
}
