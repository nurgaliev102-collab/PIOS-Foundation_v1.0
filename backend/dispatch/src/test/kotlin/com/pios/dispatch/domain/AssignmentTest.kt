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

    // --- Acceptance ---

    @Test
    fun `a newly created assignment starts in CREATED status`() {
        val assignment = Assignment.create(order, driver).assignment

        assertEquals(AssignmentStatus.CREATED, assignment.status)
    }

    @Test
    fun `accepting a created assignment transitions it to ACCEPTED`() {
        val assignment = Assignment.create(order, driver).assignment

        assignment.accept()

        assertEquals(AssignmentStatus.ACCEPTED, assignment.status)
    }

    @Test
    fun `accepting a created assignment produces an AssignmentAccepted event for the same order and driver`() {
        val assignment = Assignment.create(order, driver).assignment

        val event = assignment.accept()

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `accepting an already-accepted assignment is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.accept()

        assertFailsWith<IllegalStateException> {
            assignment.accept()
        }
    }

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle) ---

    @Test
    fun `a newly created assignment has no statusChangedAt yet`() {
        val assignment = Assignment.create(order, driver).assignment

        assertEquals(null, assignment.statusChangedAt)
    }

    @Test
    fun `accepting sets statusChangedAt`() {
        val assignment = Assignment.create(order, driver).assignment

        assignment.accept()

        assertNotEquals(null, assignment.statusChangedAt)
    }

    @Test
    fun `arriving a created assignment transitions it to ARRIVED -- CREATED is a valid precondition, not just ACCEPTED`() {
        val assignment = Assignment.create(order, driver).assignment

        assignment.arrive()

        assertEquals(AssignmentStatus.ARRIVED, assignment.status)
    }

    @Test
    fun `arriving an accepted assignment transitions it to ARRIVED`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.accept()

        assignment.arrive()

        assertEquals(AssignmentStatus.ARRIVED, assignment.status)
    }

    @Test
    fun `arriving produces an AssignmentArrived event for the same order and driver`() {
        val assignment = Assignment.create(order, driver).assignment

        val event = assignment.arrive()

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `arriving an already-arrived assignment is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.arrive()

        assertFailsWith<IllegalStateException> {
            assignment.arrive()
        }
    }

    @Test
    fun `starting requires ARRIVED -- a created assignment cannot start directly`() {
        val assignment = Assignment.create(order, driver).assignment

        assertFailsWith<IllegalStateException> {
            assignment.start()
        }
    }

    @Test
    fun `starting an arrived assignment transitions it to IN_PROGRESS`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.arrive()

        val event = assignment.start()

        assertEquals(AssignmentStatus.IN_PROGRESS, assignment.status)
        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `completing requires IN_PROGRESS -- an arrived assignment cannot complete directly`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.arrive()

        assertFailsWith<IllegalStateException> {
            assignment.complete()
        }
    }

    @Test
    fun `completing an in-progress assignment transitions it to COMPLETED`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.arrive()
        assignment.start()

        val event = assignment.complete()

        assertEquals(AssignmentStatus.COMPLETED, assignment.status)
        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `completing an already-completed assignment is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.arrive()
        assignment.start()
        assignment.complete()

        assertFailsWith<IllegalStateException> {
            assignment.complete()
        }
    }
}
