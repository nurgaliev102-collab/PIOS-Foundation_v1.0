package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A shared behavioral contract every [AssignmentRepository]
 * implementation must satisfy, run identically against
 * [InMemoryAssignmentRepository] and [PostgreSQLAssignmentRepository]
 * (PostgreSQL Persistence Dispatch v1.0). Proves
 * [com.pios.dispatch.application.DispatchAssignmentApplicationService]
 * can depend on either without any change to its own code
 * (APPLICATION_ARCHITECTURE.md Section 2) — the repository interface is
 * unchanged and both adapters honor it identically.
 */
abstract class AssignmentRepositoryContractTest {

    abstract fun createRepository(): AssignmentRepository

    @Test
    fun `a saved assignment can be found by its id with its status intact`() {
        val repository = createRepository()
        val created = Assignment.create(OrderReference("contract-test-order-1"), DriverReference("contract-test-driver-1"))

        repository.save(created.assignment)

        assertEquals(AssignmentStatus.CREATED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `an id that was never saved returns null rather than throwing`() {
        val repository = createRepository()

        assertNull(repository.findById(AssignmentId("assignment-repository-contract-test-never-saved")))
    }

    @Test
    fun `saving the same assignment id again overwrites its previously persisted status`() {
        val repository = createRepository()
        val created = Assignment.create(OrderReference("contract-test-order-2"), DriverReference("contract-test-driver-2"))
        repository.save(created.assignment)

        created.assignment.accept()
        repository.save(created.assignment)

        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }
}

class InMemoryAssignmentRepositoryContractTest : AssignmentRepositoryContractTest() {
    override fun createRepository(): AssignmentRepository = InMemoryAssignmentRepository()
}

class PostgreSQLAssignmentRepositoryContractTest : AssignmentRepositoryContractTest() {
    override fun createRepository(): AssignmentRepository =
        PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
