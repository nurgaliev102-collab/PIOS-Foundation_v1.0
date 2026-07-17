package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AssignmentTest {

    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    // --- Creation ---

    @Test
    fun `creating an assignment connects the given order and driver`() {
        val created = Assignment.create(order, driver)

        assertEquals(order, created.assignment.order)
        assertEquals(driver, created.assignment.driver)
    }

    @Test
    fun `creating an assignment produces an OrderAssigned event for the same order and driver`() {
        val created = Assignment.create(order, driver)

        assertEquals(order, created.event.orderId)
        assertEquals(driver, created.event.driverId)
    }

    @Test
    fun `each created assignment has a distinct identity`() {
        val first = Assignment.create(order, driver)
        val second = Assignment.create(OrderReference("order-2"), driver)

        assertNotEquals(first.assignment.id, second.assignment.id)
    }

    // --- Invariant: an order cannot be connected to more than one active assignment ---

    @Test
    fun `creating a second assignment for an order that already has one is rejected`() {
        val first = Assignment.create(order, driver)

        assertFailsWith<IllegalStateException> {
            Assignment.create(order, DriverReference("driver-2"), existingAssignments = listOf(first.assignment))
        }
    }

    @Test
    fun `creating an assignment for a different order succeeds even with existing assignments`() {
        val first = Assignment.create(order, driver)
        val otherOrder = OrderReference("order-2")

        val second = Assignment.create(otherOrder, driver, existingAssignments = listOf(first.assignment))

        assertEquals(otherOrder, second.assignment.order)
    }

    @Test
    fun `a driver may be referenced by more than one assignment for different orders`() {
        val first = Assignment.create(order, driver)
        val otherOrder = OrderReference("order-2")

        val second = Assignment.create(otherOrder, driver, existingAssignments = listOf(first.assignment))

        assertEquals(driver, second.assignment.driver)
    }
}
