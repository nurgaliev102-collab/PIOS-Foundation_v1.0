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
import kotlin.test.assertTrue

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

    @Test
    fun `findByOrder returns only assignments for that order`() {
        val repository = createRepository()
        val order = OrderReference("contract-test-findbyorder-order")
        val matching = Assignment.create(order, DriverReference("contract-test-findbyorder-driver-1"))
        val other = Assignment.create(OrderReference("contract-test-findbyorder-other-order"), DriverReference("contract-test-findbyorder-driver-2"))
        repository.save(matching.assignment)
        repository.save(other.assignment)

        val found = repository.findByOrder(order)

        assertTrue(found.any { it.id == matching.assignment.id })
        assertTrue(found.none { it.id == other.assignment.id })
    }

    @Test
    fun `findByOrder returns an empty list when the order has no assignment`() {
        val repository = createRepository()

        assertEquals(emptyList(), repository.findByOrder(OrderReference("contract-test-findbyorder-never-assigned")))
    }

    @Test
    fun `findByOrders returns assignments across every named order and none outside it`() {
        val repository = createRepository()
        val orderA = OrderReference("contract-test-findbyorders-order-a")
        val orderB = OrderReference("contract-test-findbyorders-order-b")
        val orderC = OrderReference("contract-test-findbyorders-order-c")
        val inA = Assignment.create(orderA, DriverReference("contract-test-findbyorders-driver-a"))
        val inB = Assignment.create(orderB, DriverReference("contract-test-findbyorders-driver-b"))
        val inC = Assignment.create(orderC, DriverReference("contract-test-findbyorders-driver-c"))
        repository.save(inA.assignment)
        repository.save(inB.assignment)
        repository.save(inC.assignment)

        val found = repository.findByOrders(listOf(orderA, orderB))

        assertTrue(found.any { it.id == inA.assignment.id })
        assertTrue(found.any { it.id == inB.assignment.id })
        assertTrue(found.none { it.id == inC.assignment.id })
    }

    @Test
    fun `findByOrders returns an empty list for an empty order list, never every assignment`() {
        val repository = createRepository()
        val created = Assignment.create(
            OrderReference("contract-test-findbyorders-empty-order"),
            DriverReference("contract-test-findbyorders-empty-driver")
        )
        repository.save(created.assignment)

        assertEquals(emptyList(), repository.findByOrders(emptyList()))
    }

    /** D-08 (Handoff Observation Foundation) -- [AssignmentRepository.findByDriver]'s own denominator source. */
    @Test
    fun `findByDriver returns every assignment for that driver and none belonging to another`() {
        val repository = createRepository()
        val driver = DriverReference("contract-test-findbydriver-driver")
        val matchingFirst = Assignment.create(OrderReference("contract-test-findbydriver-order-1"), driver)
        val matchingSecond = Assignment.create(OrderReference("contract-test-findbydriver-order-2"), driver)
        val other = Assignment.create(OrderReference("contract-test-findbydriver-other-order"), DriverReference("contract-test-findbydriver-other-driver"))
        repository.save(matchingFirst.assignment)
        repository.save(matchingSecond.assignment)
        repository.save(other.assignment)

        val found = repository.findByDriver(driver)

        assertTrue(found.any { it.id == matchingFirst.assignment.id })
        assertTrue(found.any { it.id == matchingSecond.assignment.id })
        assertTrue(found.none { it.id == other.assignment.id })
    }

    @Test
    fun `findByDriver returns an empty list when the driver has no assignment`() {
        val repository = createRepository()

        assertEquals(emptyList(), repository.findByDriver(DriverReference("contract-test-findbydriver-never-assigned")))
    }

    // --- D-09.1 (Tier 1 Visible): viaTrustedFallback persists/reconstructs ---

    @Test
    fun `an assignment created with viaTrustedFallback true can be found with that fact intact`() {
        val repository = createRepository()
        val created = Assignment.create(
            OrderReference("contract-test-order-tier1-1"),
            DriverReference("contract-test-driver-tier1-1"),
            viaTrustedFallback = true
        )

        repository.save(created.assignment)

        assertEquals(true, repository.findById(created.assignment.id)?.viaTrustedFallback)
    }

    @Test
    fun `an assignment created with no viaTrustedFallback argument can be found as false`() {
        val repository = createRepository()
        val created = Assignment.create(OrderReference("contract-test-order-tier1-2"), DriverReference("contract-test-driver-tier1-2"))

        repository.save(created.assignment)

        assertEquals(false, repository.findById(created.assignment.id)?.viaTrustedFallback)
    }

    @Test
    fun `viaTrustedFallback survives reconstruction through an accepted, then re-saved, assignment`() {
        val repository = createRepository()
        val created = Assignment.create(
            OrderReference("contract-test-order-tier1-3"),
            DriverReference("contract-test-driver-tier1-3"),
            viaTrustedFallback = true
        )
        repository.save(created.assignment)

        created.assignment.accept()
        repository.save(created.assignment)

        assertEquals(true, repository.findById(created.assignment.id)?.viaTrustedFallback)
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
