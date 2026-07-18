package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DispatchAssignmentApplicationServiceTest {

    private val repository = InMemoryAssignmentRepository()
    private val service = DispatchAssignmentApplicationService(repository)
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    @Test
    fun `handling a command with no existing assignments creates a new assignment`() {
        val command = AssignOrderCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, result.assignment.order)
        assertEquals(driver, result.assignment.driver)
    }

    @Test
    fun `handling a command produces an OrderAssigned event matching the command`() {
        val command = AssignOrderCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, result.event.orderId)
        assertEquals(driver, result.event.driverId)
    }

    @Test
    fun `handling a command for an order that already has an assignment is rejected`() {
        val existing = Assignment.create(order, driver).assignment
        val command = AssignOrderCommand(order, DriverReference("driver-2"))

        assertFailsWith<IllegalStateException> {
            service.handle(command, existingAssignments = listOf(existing))
        }
    }

    @Test
    fun `handling a command persists the created assignment through the repository`() {
        val command = AssignOrderCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, repository.findById(result.assignment.id)?.order)
    }

    @Test
    fun `accepting an assignment matching the command produces an AssignmentAccepted event`() {
        val assignment = Assignment.create(order, driver).assignment
        val command = AcceptAssignmentCommand(assignment.id)

        val event = service.acceptAssignment(assignment, command)

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `accepting an assignment whose id does not match the command is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        val otherAssignment = Assignment.create(OrderReference("order-2"), driver).assignment
        val command = AcceptAssignmentCommand(otherAssignment.id)

        assertFailsWith<IllegalArgumentException> {
            service.acceptAssignment(assignment, command)
        }
    }

    @Test
    fun `accepting an already-accepted assignment through the service is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.accept()
        val command = AcceptAssignmentCommand(assignment.id)

        assertFailsWith<IllegalStateException> {
            service.acceptAssignment(assignment, command)
        }
    }

    @Test
    fun `accepting an assignment persists its ACCEPTED status through the repository`() {
        val assignment = Assignment.create(order, driver).assignment

        service.acceptAssignment(assignment, AcceptAssignmentCommand(assignment.id))

        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(assignment.id)?.status)
    }
}
