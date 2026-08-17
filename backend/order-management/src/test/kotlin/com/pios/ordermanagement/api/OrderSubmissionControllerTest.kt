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

    // --- Passenger name (first-pilot feedback) ---

    @Test
    fun `a request with a passenger name persists it`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-named", "Аэропорт", "Мария"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("Мария", repository.findById(OrderId(orderId))?.passengerName)
    }

    @Test
    fun `a request without a passenger name still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-unnamed"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.passengerName)
    }

    // --- Pickup address (Sprint H5: Entrepreneur Working Cycle Integrity) ---

    @Test
    fun `a request with a pickup address persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-with-pickup", pickupAddress = "ул. Ленина, 10")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("ул. Ленина, 10", repository.findById(OrderId(orderId))?.pickupAddress)
    }

    @Test
    fun `a request without a pickup address still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-no-pickup"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.pickupAddress)
    }

    // --- Requested pickup time (ADR-058, Scheduled Pickup Time) ---

    @Test
    fun `a request with a requested pickup instant persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-scheduled", requestedPickupAt = "2026-08-25T06:30:00Z")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals(
            java.time.Instant.parse("2026-08-25T06:30:00Z"),
            repository.findById(OrderId(orderId))?.requestedPickupAt
        )
    }

    @Test
    fun `a request without a requested pickup instant still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-not-scheduled"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.requestedPickupAt)
    }

    @Test
    fun `a request with a malformed requested pickup instant returns 400`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-bad-schedule", requestedPickupAt = "tomorrow at 6")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `a request with isTest true persists an order marked isTest true`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-e2e", isTest = true))

        val orderId = assertNotNull(response.body).orderId
        assertEquals(true, repository.findById(OrderId(orderId))?.isTest)
    }

    @Test
    fun `a request without isTest persists an order marked isTest false -- a real order is never marked test by omission`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-real"))

        val orderId = assertNotNull(response.body).orderId
        assertEquals(false, repository.findById(OrderId(orderId))?.isTest)
    }
}
