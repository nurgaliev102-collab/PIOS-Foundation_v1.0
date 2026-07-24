package com.pios.dispatch.api

import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [AssignmentController] directly, with a real
 * [DispatchAssignmentApplicationService]/[InMemoryAssignmentRepository],
 * no Spring MVC context -- mirroring this project's own constructor-based
 * testing convention (Tranche 2: Passenger Experience REST Transport).
 */
class AssignmentControllerTest {

    private val repository = InMemoryAssignmentRepository()
    private val service = DispatchAssignmentApplicationService(repository)
    private val controller = AssignmentController(service)

    @Test
    fun `assigning an order to a driver returns 201 with a new assignment id and CREATED status`() {
        val response = controller.assignOrder(AssignOrderRequest("order-1", "driver-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.assignmentId.isNotBlank())
        assertEquals("CREATED", body.status)
    }

    @Test
    fun `assigning an order persists the assignment, findable by order`() {
        controller.assignOrder(AssignOrderRequest("order-2", "driver-2"))

        val found = repository.findByOrder(OrderReference("order-2"))

        assertTrue(found.any { it.driver == DriverReference("driver-2") })
    }

    @Test
    fun `a blank orderId returns 400`() {
        val response = controller.assignOrder(AssignOrderRequest("", "driver-3"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a blank driverId returns 400`() {
        val response = controller.assignOrder(AssignOrderRequest("order-4", ""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `assigning an order that already has an active assignment returns 409`() {
        val order = OrderReference("order-5")
        repository.save(Assignment.create(order, DriverReference("driver-5")).assignment)

        val response = controller.assignOrder(AssignOrderRequest("order-5", "driver-6"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
