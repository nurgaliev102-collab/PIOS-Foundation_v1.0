package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderLifecycleApplicationServiceTest {

    private val repository = InMemoryOrderRepository()
    private val service = OrderLifecycleApplicationService(repository)

    @Test
    fun `submitting an order through the application service returns a new submitted order`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        assertEquals(OrderStatus.SUBMITTED, submitted.order.status)
        assertEquals(submitted.order.id, submitted.event.orderId)
    }

    @Test
    fun `submitting an order persists it through the repository`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        assertEquals(OrderStatus.SUBMITTED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `completing an order through the application service delegates to the domain`() {
        val order = Order.submit().order
        val command = CompleteOrderCommand(order.id)

        val event = service.completeOrder(order, command)

        assertEquals(OrderStatus.COMPLETED, order.status)
        assertEquals(order.id, event.orderId)
    }

    @Test
    fun `completing an order persists its new status through the repository`() {
        val order = Order.submit().order
        val command = CompleteOrderCommand(order.id)

        service.completeOrder(order, command)

        assertEquals(OrderStatus.COMPLETED, repository.findById(order.id)?.status)
    }

    @Test
    fun `completing rejects a command targeting a different order`() {
        val order = Order.submit().order
        val command = CompleteOrderCommand(OrderId("some-other-order"))

        assertFailsWith<IllegalArgumentException> {
            service.completeOrder(order, command)
        }
    }

    @Test
    fun `cancelling an order through the application service delegates to the domain`() {
        val order = Order.submit().order
        val command = CancelOrderCommand(order.id)

        val event = service.cancelOrder(order, command)

        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(order.id, event.orderId)
    }

    @Test
    fun `cancelling an order persists its new status through the repository`() {
        val order = Order.submit().order
        val command = CancelOrderCommand(order.id)

        service.cancelOrder(order, command)

        assertEquals(OrderStatus.CANCELLED, repository.findById(order.id)?.status)
    }

    @Test
    fun `cancelling rejects a command targeting a different order`() {
        val order = Order.submit().order
        val command = CancelOrderCommand(OrderId("some-other-order"))

        assertFailsWith<IllegalArgumentException> {
            service.cancelOrder(order, command)
        }
    }
}
