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
    private val service = DispatchAssignmentApplicationService(repository)

    @Test
    fun `an assignment created through PostgreSQL can be restored by id and accepted`() {
        val created = service.handle(AssignOrderCommand(OrderReference("postgres-lifecycle-order-1"), DriverReference("postgres-lifecycle-driver-1")))

        val event = service.acceptAssignment(AcceptAssignmentCommand(created.assignment.id))

        assertEquals(OrderReference("postgres-lifecycle-order-1"), event.orderId)
        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `accepting an assignment id that was never saved raises AssignmentNotFoundException`() {
        assertFailsWith<AssignmentNotFoundException> {
            service.acceptAssignment(AcceptAssignmentCommand(AssignmentId("postgres-lifecycle-never-saved")))
        }
    }

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle) ---
    //
    // This is the one test in this module that actually exercises
    // PostgreSQLAssignmentRepository.reconstruct()'s replayed chain for
    // IN_PROGRESS/COMPLETED against a real database, not just CREATED or
    // ACCEPTED -- a bug in that replay (wrong order, wrong precondition,
    // the persisted status_changed_at not surviving the round trip) would
    // only be caught here, not by the in-memory variant.

    @Test
    fun `an assignment can be carried through the full ride lifecycle and restored at each step`() {
        val created = service.handle(
            AssignOrderCommand(OrderReference("postgres-lifecycle-order-2"), DriverReference("postgres-lifecycle-driver-2"))
        )

        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        assertEquals(AssignmentStatus.ARRIVED, repository.findById(created.assignment.id)?.status)

        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        assertEquals(AssignmentStatus.IN_PROGRESS, repository.findById(created.assignment.id)?.status)

        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))
        val final = repository.findById(created.assignment.id)
        assertEquals(AssignmentStatus.COMPLETED, final?.status)
        assertNotNull(final?.statusChangedAt)
    }
}
