package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DispatchAssignmentApplicationServiceTest {

    private val service = DispatchAssignmentApplicationService()
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
}
