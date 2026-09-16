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
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Proves the full Assignment lifecycle through the real PostgreSQL
 * adapter: create, persist, restore by id (via the id-only overload
 * introduced in Persistence Quality Foundation v1.0), and continue to
 * acceptance, persisting again — end to end against an actual database,
 * not merely in memory.
 */
class PostgreSQLAssignmentLifecycleTest {

    private val repository = PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val tripRepository = PostgreSQLTripRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = DispatchAssignmentApplicationService(repository, tripRepository = tripRepository)

    @Test
    fun `an assignment created through PostgreSQL can be restored by id and accepted`() {
        val orderId = OrderReference("postgres-lifecycle-order-1-${java.util.UUID.randomUUID()}")
        val created = service.handle(AssignOrderCommand(orderId, DriverReference("postgres-lifecycle-driver-1")))

        val event = service.acceptAssignment(AcceptAssignmentCommand(created.assignment.id))

        assertEquals(orderId, event.orderId)
        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `accepting an assignment id that was never saved raises AssignmentNotFoundException`() {
        assertFailsWith<AssignmentNotFoundException> {
            service.acceptAssignment(AcceptAssignmentCommand(AssignmentId("postgres-lifecycle-never-saved")))
        }
    }

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle; converged onto Trip, ADR-063/Task 12) ---
    //
    // This is the one test in this module that actually exercises
    // PostgreSQLTripRepository.reconstruct()'s replayed chain for
    // IN_PROGRESS/COMPLETED against a real database, not just CREATED --
    // a bug in that replay (wrong order, wrong precondition, the
    // persisted status_changed_at not surviving the round trip) would
    // only be caught here, not by the in-memory variant. Reads
    // [tripRepository], not [repository] (Assignment's own), for exactly
    // the fields ride-progress convergence moved onto Trip -- Assignment's
    // own status is separately proven to stay CREATED below.

    @Test
    fun `an assignment can be carried through the full ride lifecycle and its trip restored at each step`() {
        val created = service.handle(
            AssignOrderCommand(
                OrderReference("postgres-lifecycle-order-2-${java.util.UUID.randomUUID()}"),
                DriverReference("postgres-lifecycle-driver-2")
            )
        )

        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        assertEquals(TripStatus.ARRIVED, tripRepository.findByAssignmentId(created.assignment.id)?.status)

        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        assertEquals(TripStatus.IN_PROGRESS, tripRepository.findByAssignmentId(created.assignment.id)?.status)

        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))
        val final = tripRepository.findByAssignmentId(created.assignment.id)
        assertEquals(TripStatus.COMPLETED, final?.status)
        assertNotNull(final?.statusChangedAt)
    }

    @Test
    fun `the full ride lifecycle never changes the assignment's own persisted status in PostgreSQL`() {
        val created = service.handle(
            AssignOrderCommand(
                OrderReference("postgres-lifecycle-order-3-${java.util.UUID.randomUUID()}"),
                DriverReference("postgres-lifecycle-driver-3")
            )
        )

        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        assertEquals(AssignmentStatus.CREATED, repository.findById(created.assignment.id)?.status)
    }
}
