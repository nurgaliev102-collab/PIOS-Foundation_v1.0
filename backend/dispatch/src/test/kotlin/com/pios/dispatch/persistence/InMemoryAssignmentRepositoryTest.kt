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

class InMemoryAssignmentRepositoryTest {

    private val repository = InMemoryAssignmentRepository()
    private val service = DispatchAssignmentApplicationService()

    @Test
    fun `create, accept, save, and load preserves an assignment's status`() {
        val created = service.handle(AssignOrderCommand(OrderReference("order-1"), DriverReference("driver-1")))
        repository.save(created.assignment)

        service.acceptAssignment(created.assignment, AcceptAssignmentCommand(created.assignment.id))
        repository.save(created.assignment)

        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(AssignmentId("never-saved")))
    }
}
