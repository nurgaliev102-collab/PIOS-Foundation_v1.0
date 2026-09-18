package com.pios.dispatch.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TripTest {

    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    private fun assignment(isTest: Boolean = false) =
        Assignment.create(order, driver, isTest = isTest).assignment

    // --- Creation (Task 11, Trip Domain Foundation; ADR-063) ---

    @Test
    fun `creating a trip from an assignment references that assignment's order and driver`() {
        val a = assignment()

        val created = Trip.create(a)

        assertEquals(order, created.trip.order)
        assertEquals(driver, created.trip.driver)
    }

    @Test
    fun `creating a trip references the originating assignment's own id`() {
        val a = assignment()

        val created = Trip.create(a)

        assertEquals(a.id, created.trip.assignmentId)
    }

    @Test
    fun `each created trip has a distinct identity`() {
        val first = Trip.create(assignment())
        val second = Trip.create(Assignment.create(OrderReference("order-2"), driver).assignment)

        assertNotEquals(first.trip.id, second.trip.id)
    }

    @Test
    fun `a newly created trip starts in CREATED status, not ARRIVED -- creation does not mean the ride has started`() {
        val trip = Trip.create(assignment()).trip

        assertEquals(TripStatus.CREATED, trip.status)
    }

    @Test
    fun `a trip derives isTest from its originating assignment`() {
        val trip = Trip.create(assignment(isTest = true)).trip

        assertEquals(true, trip.isTest)
    }

    @Test
    fun `a real (non-test) assignment produces a real (non-test) trip`() {
        val trip = Trip.create(assignment(isTest = false)).trip

        assertEquals(false, trip.isTest)
    }

    // --- Invariant: at most one Trip per Assignment ---

    @Test
    fun `creating a second trip for an assignment that already has one is rejected`() {
        val a = assignment()
        val first = Trip.create(a).trip

        assertFailsWith<IllegalStateException> {
            Trip.create(a, existingTrip = first)
        }
    }

    @Test
    fun `creating a trip for a different assignment succeeds even with an existing trip present`() {
        val a = assignment()
        val existing = Trip.create(a).trip
        val otherAssignment = Assignment.create(OrderReference("order-2"), driver).assignment

        val second = Trip.create(otherAssignment)

        assertEquals(otherAssignment.id, second.trip.assignmentId)
        // existing is simply not passed as this call's own existingTrip -- confirms the
        // check is per-assignment, not a global "any trip exists anywhere" guard.
        assertNotEquals(existing.id, second.trip.id)
    }

    // --- Ride lifecycle (mirrors Assignment's own ARRIVED/IN_PROGRESS/COMPLETED exactly) ---

    @Test
    fun `arriving a created trip transitions it to ARRIVED`() {
        val trip = Trip.create(assignment()).trip

        trip.arrive()

        assertEquals(TripStatus.ARRIVED, trip.status)
    }

    @Test
    fun `arriving produces a TripArrived event for the same order and driver`() {
        val trip = Trip.create(assignment()).trip

        val event = trip.arrive()

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `arriving an already-arrived trip is rejected`() {
        val trip = Trip.create(assignment()).trip
        trip.arrive()

        assertFailsWith<IllegalStateException> {
            trip.arrive()
        }
    }

    @Test
    fun `starting requires ARRIVED -- a created trip cannot start directly`() {
        val trip = Trip.create(assignment()).trip

        assertFailsWith<IllegalStateException> {
            trip.start()
        }
    }

    @Test
    fun `starting an arrived trip transitions it to IN_PROGRESS`() {
        val trip = Trip.create(assignment()).trip
        trip.arrive()

        val event = trip.start()

        assertEquals(TripStatus.IN_PROGRESS, trip.status)
        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `completing requires IN_PROGRESS -- an arrived trip cannot complete directly`() {
        val trip = Trip.create(assignment()).trip
        trip.arrive()

        assertFailsWith<IllegalStateException> {
            trip.complete()
        }
    }

    @Test
    fun `completing an in-progress trip transitions it to COMPLETED`() {
        val trip = Trip.create(assignment()).trip
        trip.arrive()
        trip.start()

        val event = trip.complete()

        assertEquals(TripStatus.COMPLETED, trip.status)
        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `completing an already-completed trip is rejected`() {
        val trip = Trip.create(assignment()).trip
        trip.arrive()
        trip.start()
        trip.complete()

        assertFailsWith<IllegalStateException> {
            trip.complete()
        }
    }

    // --- Timestamps ---

    @Test
    fun `statusChangedAt, arrivedAt, startedAt and completedAt are all null on a freshly created trip`() {
        val trip = Trip.create(assignment()).trip

        assertNull(trip.statusChangedAt)
        assertNull(trip.arrivedAt)
        assertNull(trip.startedAt)
        assertNull(trip.completedAt)
    }

    @Test
    fun `arriving stamps arrivedAt and statusChangedAt with the moment given, leaving startedAt and completedAt null`() {
        val trip = Trip.create(assignment()).trip
        val at = Instant.parse("2026-09-03T12:00:00Z")

        trip.arrive(at)

        assertEquals(at, trip.arrivedAt)
        assertEquals(at, trip.statusChangedAt)
        assertNull(trip.startedAt)
        assertNull(trip.completedAt)
    }

    @Test
    fun `completing stamps completedAt without touching arrivedAt or startedAt`() {
        val trip = Trip.create(assignment()).trip
        val arrivedAt = Instant.parse("2026-09-03T12:00:00Z")
        val startedAt = Instant.parse("2026-09-03T12:05:00Z")
        val completedAt = Instant.parse("2026-09-03T12:20:00Z")
        trip.arrive(arrivedAt)
        trip.start(startedAt)

        trip.complete(completedAt)

        assertEquals(arrivedAt, trip.arrivedAt)
        assertEquals(startedAt, trip.startedAt)
        assertEquals(completedAt, trip.completedAt)
        assertEquals(completedAt, trip.statusChangedAt)
    }

    // --- Agreed amount (D-06, Settlement as Evidence) ---

    @Test
    fun `a trip created with no agreedAmount argument has a null agreedAmount`() {
        val trip = Trip.create(assignment()).trip

        assertNull(trip.agreedAmount)
    }

    @Test
    fun `creating a trip captures the agreedAmount supplied by the caller`() {
        val created = Trip.create(assignment(), agreedAmount = "350")

        assertEquals("350", created.trip.agreedAmount)
    }

    @Test
    fun `agreedAmount is unaffected by the full ride lifecycle -- arrive, start, complete never touch it`() {
        val trip = Trip.create(assignment(), agreedAmount = "500").trip

        trip.arrive()
        trip.start()
        trip.complete()

        assertEquals("500", trip.agreedAmount)
    }

    @Test
    fun `agreedAmount is unaffected by termination`() {
        val trip = Trip.create(assignment(), agreedAmount = "500").trip

        trip.terminate(
            Termination(
                requestId = "req-1",
                initiator = TerminationInitiator.PASSENGER,
                reasonCode = TerminationReasonCode.PLANS_CHANGED,
                terminatedAt = Instant.parse("2026-09-18T12:00:00Z"),
                note = null
            )
        )

        assertEquals("500", trip.agreedAmount)
    }

    @Test
    fun `two trips for the same order at different times can carry different agreedAmounts -- each Trip's own amount is its own`() {
        val a1 = assignment()
        val trip1 = Trip.create(a1, agreedAmount = "300").trip
        val a2 = Assignment.create(order, DriverReference("driver-2")).assignment

        val trip2 = Trip.create(a2, agreedAmount = "450").trip

        assertEquals("300", trip1.agreedAmount)
        assertEquals("450", trip2.agreedAmount)
    }
}
