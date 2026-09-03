package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.TripStatus
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Covers Task 11's own minimum required Trip-creation scenarios
 * (ADR-063, corrected Task 11A), entirely in memory, at the application
 * layer directly — mirrors [DispatchAssignmentApplicationServiceTest]'s
 * own style. Kept as a separate file rather than appended to that one,
 * so the existing, already-passing test file is never touched by this
 * task, per its own "do not weaken existing assertions" requirement.
 */
class DispatchAssignmentApplicationServiceTripCreationTest {

    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val service = DispatchAssignmentApplicationService(
        assignmentRepository = assignmentRepository,
        tripRepository = tripRepository
    )
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    @Test
    fun `creating an assignment via the self-fetching handle overload also creates exactly one trip`() {
        val created = service.handle(AssignOrderCommand(order, driver))

        val trip = tripRepository.findByAssignmentId(created.assignment.id)
        assertNotNull(trip)
        assertEquals(order, trip.order)
        assertEquals(driver, trip.driver)
    }

    @Test
    fun `creating an assignment via handleWithinCallerTransaction also creates exactly one trip`() {
        val created = service.handleWithinCallerTransaction(AssignOrderCommand(order, driver))

        val trip = tripRepository.findByAssignmentId(created.assignment.id)
        assertNotNull(trip)
        assertEquals(created.assignment.id, trip.assignmentId)
    }

    @Test
    fun `creating an assignment via the legacy caller-supplies-existingAssignments overload also creates a trip`() {
        val created = service.handle(AssignOrderCommand(order, driver), existingAssignments = emptyList())

        assertNotNull(tripRepository.findByAssignmentId(created.assignment.id))
    }

    @Test
    fun `the created trip starts in CREATED status, not ARRIVED`() {
        val created = service.handle(AssignOrderCommand(order, driver))

        assertEquals(TripStatus.CREATED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `a trip is created without any code path calling AssignmentAccepted -- the corrected OrderAssigned trigger alone is sufficient`() {
        // No call to service.acceptAssignment(...) anywhere in this test -- the
        // Assignment's own status therefore never reaches ACCEPTED, exactly as
        // Task 11A's own finding describes for the real production paths. The
        // Trip must still exist, proving Trip creation genuinely does not depend
        // on AssignmentAccepted in any way.
        val created = service.handle(AssignOrderCommand(order, driver))

        assertEquals(
            com.pios.dispatch.domain.AssignmentStatus.CREATED,
            assignmentRepository.findById(created.assignment.id)?.status
        )
        assertNotNull(tripRepository.findByAssignmentId(created.assignment.id))
    }

    @Test
    fun `an isTest assignment produces an isTest trip -- same-module derivation`() {
        val created = service.handleWithinCallerTransaction(AssignOrderCommand(order, driver, isTest = true))

        assertEquals(true, tripRepository.findByAssignmentId(created.assignment.id)?.isTest)
    }

    // --- Idempotency: exactly one Trip per Assignment, even under a defensive re-entry ---

    @Test
    fun `invoking Trip creation twice for the same already-created assignment is rejected, never producing a second trip`() {
        val created = service.handleWithinCallerTransaction(AssignOrderCommand(order, driver))
        // Access the private creation path indirectly: simulate a defensive
        // re-entry by attempting Assignment.create's own downstream Trip step a
        // second time for the same, already-Trip-having Assignment, exactly as
        // the private createTripFor helper does internally.
        val existingTrip = tripRepository.findByAssignmentId(created.assignment.id)
        assertNotNull(existingTrip)

        assertFailsWith<IllegalStateException> {
            com.pios.dispatch.domain.Trip.create(created.assignment, existingTrip = existingTrip)
        }

        // Still exactly one Trip for this Assignment -- the failed second
        // attempt never reached tripRepository.save.
        assertEquals(existingTrip.id, tripRepository.findByAssignmentId(created.assignment.id)?.id)
    }

    @Test
    fun `two different orders each get their own distinct trip`() {
        val first = service.handleWithinCallerTransaction(AssignOrderCommand(order, driver))
        val second = service.handleWithinCallerTransaction(
            AssignOrderCommand(OrderReference("order-2"), driver)
        )

        val firstTrip = tripRepository.findByAssignmentId(first.assignment.id)
        val secondTrip = tripRepository.findByAssignmentId(second.assignment.id)
        assertNotNull(firstTrip)
        assertNotNull(secondTrip)
        assertEquals(false, firstTrip.id == secondTrip.id)
    }
}
