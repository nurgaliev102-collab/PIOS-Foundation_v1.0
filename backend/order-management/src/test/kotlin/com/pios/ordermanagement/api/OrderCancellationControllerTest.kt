package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Constructs [OrderCancellationController] directly, with a real
 * [OrderLifecycleApplicationService], no Spring MVC context — mirroring
 * [OrderSubmissionControllerTest]'s own constructor-based testing
 * convention (P0-2 Tier 1; ADR-053, Proposal Resolution on Order
 * Cancellation — Accepted).
 */
class OrderCancellationControllerTest {

    private val repository = InMemoryOrderRepository()
    private val orderLifecycleApplicationService = OrderLifecycleApplicationService(repository)
    private val controller = OrderCancellationController(orderLifecycleApplicationService)

    private fun submittedOrderId(): String =
        orderLifecycleApplicationService.submitOrder(SubmitOrderCommand(OrderOrigin("passenger-1"))).order.id.value

    @Test
    fun `cancelling a submitted order returns 200 with CANCELLED status`() {
        val orderId = submittedOrderId()

        val response = controller.cancelOrder(orderId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(orderId, response.body?.orderId)
        assertEquals(OrderStatus.CANCELLED.name, response.body?.status)
    }

    @Test
    fun `cancelling a submitted order persists CANCELLED through the repository`() {
        val orderId = submittedOrderId()

        controller.cancelOrder(orderId)

        assertEquals(OrderStatus.CANCELLED, repository.findById(OrderId(orderId))?.status)
    }

    @Test
    fun `cancelling an unknown order returns 404`() {
        val response = controller.cancelOrder("never-submitted")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `cancelling an already-cancelled order returns 409`() {
        val orderId = submittedOrderId()
        controller.cancelOrder(orderId)

        val response = controller.cancelOrder(orderId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `cancelling an already-completed order returns 409, never overwriting COMPLETED`() {
        val orderId = submittedOrderId()
        orderLifecycleApplicationService.completeOrder(CompleteOrderCommand(OrderId(orderId)))

        val response = controller.cancelOrder(orderId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(OrderStatus.COMPLETED, repository.findById(OrderId(orderId))?.status)
    }
}
