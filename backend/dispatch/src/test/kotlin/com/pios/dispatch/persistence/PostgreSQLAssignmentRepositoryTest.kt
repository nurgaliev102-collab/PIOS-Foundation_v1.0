package com.pios.dispatch.persistence

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the save/load lifecycle described by [AssignmentRepository]
 * against a real PostgreSQL database, not merely in memory (PostgreSQL
 * Persistence Dispatch v1.0; ADR-025).
 */
class PostgreSQLAssignmentRepositoryTest {

    private val repository = PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `an assignment saved to PostgreSQL can be loaded back with its identity, references, and status preserved`() {
        val created = Assignment.create(OrderReference("postgres-order-1"), DriverReference("postgres-driver-1"))

        repository.save(created.assignment)
        val loaded = repository.findById(created.assignment.id)

        assertEquals(created.assignment.id, loaded?.id)
        assertEquals(OrderReference("postgres-order-1"), loaded?.order)
        assertEquals(DriverReference("postgres-driver-1"), loaded?.driver)
        assertEquals(AssignmentStatus.CREATED, loaded?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(AssignmentId("postgres-repository-test-never-saved")))
    }

    @Test
    fun `saving again after acceptance overwrites the previously persisted row's status`() {
        val created = Assignment.create(OrderReference("postgres-order-2"), DriverReference("postgres-driver-2"))
        repository.save(created.assignment)

        created.assignment.accept()
        repository.save(created.assignment)

        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(created.assignment.id)?.status)
    }

    @Test
    fun `findByOrder returns an assignment saved to PostgreSQL for that order`() {
        val order = OrderReference("postgres-findbyorder-order-1")
        val created = Assignment.create(order, DriverReference("postgres-findbyorder-driver-1"))

        repository.save(created.assignment)

        assertTrue(repository.findByOrder(order).any { it.id == created.assignment.id })
    }
}
