package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.CancelOrderCommand
import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderNotFoundException
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the full repository lifecycle for the Order aggregate: create,
 * save, restore by id, and continue a further domain operation — not
 * merely that data round-trips, but that the restored aggregate is fully
 * functional. Also proves the consistent, non-exceptional-by-default
 * handling of a missing aggregate: [OrderLifecycleApplicationService]
 * raises [OrderNotFoundException] (an application-layer error) rather
 * than letting a null propagate or a persistence-specific exception leak
 * through — [InMemoryOrderRepository.findById] itself never throws.
 */
class OrderRepositoryLifecycleTest {

    private val repository = InMemoryOrderRepository()
    private val service = OrderLifecycleApplicationService(repository)

    @Test
    fun `an order submitted and saved can be restored by id and continue to completion`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        val event = service.completeOrder(CompleteOrderCommand(submitted.order.id))

        assertEquals(submitted.order.id, event.orderId)
        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `an order submitted and saved can be restored by id and continue to cancellation`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        val event = service.cancelOrder(CancelOrderCommand(submitted.order.id))

        assertEquals(submitted.order.id, event.orderId)
        assertEquals(OrderStatus.CANCELLED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `completing an order id that was never saved raises OrderNotFoundException`() {
        val exception = assertFailsWith<OrderNotFoundException> {
            service.completeOrder(CompleteOrderCommand(OrderId("never-saved")))
        }
        assertEquals(OrderId("never-saved"), exception.orderId)
    }

    @Test
    fun `cancelling an order id that was never saved raises OrderNotFoundException`() {
        assertFailsWith<OrderNotFoundException> {
            service.cancelOrder(CancelOrderCommand(OrderId("never-saved")))
        }
    }
}
