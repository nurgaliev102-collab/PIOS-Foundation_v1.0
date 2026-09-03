package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptAssignmentCommand
import com.pios.dispatch.application.ArriveAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.AssignmentNotFoundException
import com.pios.dispatch.application.CompleteAssignmentCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.StartAssignmentCommand
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.TripStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the full repository lifecycle for the Assignment aggregate:
 * create, save, restore by id, and continue a further domain operation —
 * not merely that data round-trips, but that the restored aggregate is
 * fully functional. Also proves the consistent, non-exceptional-by-default
 * handling of a missing aggregate: [DispatchAssignmentApplicationService]
 * raises [AssignmentNotFoundException] (an application-layer error)
 * rather than letting a null propagate or a persistence-specific
 * exception leak through — [InMemoryAssignmentRepository.findById]
 * itself never throws.
 */
class AssignmentRepositoryLifecycleTest {

    private val repository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val service = DispatchAssignmentApplicationService(repository, tripRepository = tripRepository)

    @Test
    fun `an assignment created and saved can be restored by id and continue to acceptance`() {
        val created = service.handle(AssignOrderCommand(OrderReference("order-1"), DriverReference("driver-1")))

        val event = service.acceptAssignment(AcceptAssignmentCommand(created.assignment.id))

        assertEquals(OrderReference("order-1"), event.orderId)
        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `accepting an assignment id that was never saved raises AssignmentNotFoundException`() {
        val exception = assertFailsWith<AssignmentNotFoundException> {
            service.acceptAssignment(AcceptAssignmentCommand(AssignmentId("never-saved")))
        }
        assertEquals(AssignmentId("never-saved"), exception.assignmentId)
    }

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle; converged onto Trip, ADR-063/Task 12) ---

    @Test
    fun `an assignment can be restored and carried through the full ride lifecycle, recorded on its trip`() {
        val created = service.handle(AssignOrderCommand(OrderReference("order-2"), DriverReference("driver-2")))

        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        assertEquals(TripStatus.COMPLETED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `arriving an assignment id that was never saved raises AssignmentNotFoundException`() {
        assertFailsWith<AssignmentNotFoundException> {
            service.arriveAssignment(ArriveAssignmentCommand(AssignmentId("never-saved-2")))
        }
    }
}
