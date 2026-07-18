package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [InMemoryAssignmentRepository] directly (repository-level
 * save/load) as well as through [DispatchAssignmentApplicationService],
 * which now persists automatically after assignment creation and
 * acceptance (Application Persistence Wiring v1.0).
 */
class InMemoryAssignmentRepositoryTest {

    private val repository = InMemoryAssignmentRepository()
    private val service = DispatchAssignmentApplicationService(repository)

    @Test
    fun `creating an assignment through the service persists it, loadable by id`() {
        val created = service.handle(AssignOrderCommand(OrderReference("order-1"), DriverReference("driver-1")))

        assertEquals(AssignmentStatus.CREATED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `accepting an assignment through the service persists its ACCEPTED status`() {
        val created = service.handle(AssignOrderCommand(OrderReference("order-2"), DriverReference("driver-2")))

        service.acceptAssignment(created.assignment, AcceptAssignmentCommand(created.assignment.id))

        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(AssignmentId("never-saved")))
    }
}
