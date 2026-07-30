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
    private val controller = AssignmentController(service, repository)

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

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle) ---

    @Test
    fun `listing assignments without orderId returns 400`() {
        val response = controller.listAssignments(null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing assignments by orderId returns every assignment for that order`() {
        controller.assignOrder(AssignOrderRequest("order-7", "driver-7"))

        val response = controller.listAssignments("order-7")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.orderId == "order-7" && it.driverId == "driver-7" && it.status == "CREATED" })
    }

    @Test
    fun `arriving a created assignment returns 200 with ARRIVED status`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-8", "driver-8")).body)

        val response = controller.arrive(created.assignmentId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ARRIVED", assertNotNull(response.body).status)
    }

    @Test
    fun `arriving an unknown assignment id returns 404`() {
        val response = controller.arrive("never-created")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `starting before arriving returns 409`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-9", "driver-9")).body)

        val response = controller.start(created.assignmentId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `the full arrive-start-complete sequence returns 200 at every step, ending COMPLETED`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-10", "driver-10")).body)

        assertEquals(HttpStatus.OK, controller.arrive(created.assignmentId).statusCode)
        assertEquals(HttpStatus.OK, controller.start(created.assignmentId).statusCode)
        val completed = controller.complete(created.assignmentId)

        assertEquals(HttpStatus.OK, completed.statusCode)
        assertEquals("COMPLETED", assertNotNull(completed.body).status)
    }
}
