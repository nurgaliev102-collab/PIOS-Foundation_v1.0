package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.AssignmentNotFoundException
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
