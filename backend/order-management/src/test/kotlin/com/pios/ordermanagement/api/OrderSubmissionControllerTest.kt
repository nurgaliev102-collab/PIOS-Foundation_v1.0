package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constructs [OrderSubmissionController] directly, with a real
 * [OrderSubmissionRequestHandler]/[OrderLifecycleApplicationService], no
 * Spring MVC context -- mirroring this project's own constructor-based
 * testing convention (Tranche 2: Passenger Experience REST Transport).
 */
class OrderSubmissionControllerTest {

    private val repository = InMemoryOrderRepository()
    private val handler = OrderSubmissionRequestHandler(OrderLifecycleApplicationService(repository))
    private val controller = OrderSubmissionController(handler)

    @Test
    fun `a valid request returns 201 with a new order id`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertTrue(assertNotNull(response.body).orderId.isNotBlank())
    }

    @Test
    fun `a blank passenger reference returns 400`() {
        val response = controller.submitOrder(SubmitOrderRequest(""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Destination (Sprint 3B: MVR Pilot Enablement -- Optional Destination) ---

    @Test
    fun `a request without a destination still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-no-destination"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.destination)
    }

    @Test
    fun `a request with a destination persists it`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-with-destination", "Аэропорт"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("Аэропорт", repository.findById(OrderId(orderId))?.destination)
    }
}
