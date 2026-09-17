package com.pios.dispatch.domain

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TripTerminationTest {
    private fun createTrip(): Pair<Assignment, Trip> {
        val assignment = Assignment.create(
            OrderReference(UUID.randomUUID().toString()), DriverReference("driver-1")
        ).assignment
        return assignment to Trip.create(assignment).trip
    }

    private fun fact(
        initiator: TerminationInitiator = TerminationInitiator.PASSENGER,
        reason: TerminationReasonCode = TerminationReasonCode.PLANS_CHANGED
    ): Termination = Termination(UUID.randomUUID().toString(), initiator, reason, Instant.now())

    @Test
    fun `created arrived and in progress trips may terminate but never progress afterward`() {
        for (progress in 0..2) {
            val (assignment, trip) = createTrip()
            if (progress >= 1) trip.arrive()
            if (progress >= 2) trip.start()
            val termination = fact()
            trip.terminate(termination)
            assignment.terminate(termination.terminatedAt)
            assertEquals(TripStatus.TERMINATED, trip.status)
            assertEquals(AssignmentStatus.TERMINATED, assignment.status)
            assertEquals(termination, trip.termination)
            assertNull(trip.completedAt)
            assertFailsWith<IllegalStateException> { trip.arrive() }
            assertFailsWith<IllegalStateException> { trip.start() }
            assertFailsWith<IllegalStateException> { trip.complete() }
            assertFailsWith<IllegalStateException> { trip.terminate(fact()) }
        }
    }

    @Test
    fun `completed trip cannot terminate`() {
        val (_, trip) = createTrip()
        trip.arrive()
        trip.start()
        trip.complete()
        assertFailsWith<IllegalStateException> { trip.terminate(fact()) }
        assertEquals(TripStatus.COMPLETED, trip.status)
    }

    @Test
    fun `reason codes are closed and actor specific`() {
        Termination.PASSENGER_REASONS.forEach { Termination("id", TerminationInitiator.PASSENGER, it, Instant.now()) }
        Termination.DRIVER_REASONS.forEach { Termination("id", TerminationInitiator.DRIVER, it, Instant.now()) }
        Termination("id", TerminationInitiator.SYSTEM, TerminationReasonCode.SYSTEM_TIMEOUT, Instant.now())
        assertFailsWith<IllegalArgumentException> {
            fact(TerminationInitiator.PASSENGER, TerminationReasonCode.CANNOT_FULFILL)
        }
        assertFailsWith<IllegalArgumentException> {
            fact(TerminationInitiator.DRIVER, TerminationReasonCode.PLANS_CHANGED)
        }
        assertFailsWith<IllegalArgumentException> {
            fact(TerminationInitiator.SYSTEM, TerminationReasonCode.OTHER)
        }
    }
}
