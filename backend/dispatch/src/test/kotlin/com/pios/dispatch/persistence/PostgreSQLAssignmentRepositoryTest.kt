package com.pios.dispatch.persistence

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
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

    // --- ADR-043: arrivedAt/startedAt/completedAt reconstruction ---

    /**
     * The specific bug ADR-043 requires this class's own [reconstruct] to
     * avoid: before this fix, only the *last* transition in a replayed
     * chain received its real persisted timestamp, and every earlier one
     * was silently given an unobserved `Instant.now()`. A COMPLETED
     * assignment reloaded from a fresh repository instance must show its
     * own real, distinct `arrivedAt` and `startedAt` — not two
     * indistinguishable "just now" values fabricated by the reload itself.
     */
    @Test
    fun `a completed assignment reloaded from a fresh repository instance preserves its own distinct arrivedAt, startedAt and completedAt`() {
        val created = Assignment.create(OrderReference("postgres-order-lifecycle-1"), DriverReference("postgres-driver-lifecycle-1"))
        val arrivedAt = Instant.parse("2026-08-02T20:19:00Z")
        val startedAt = Instant.parse("2026-08-02T20:20:00Z")
        val completedAt = Instant.parse("2026-08-02T20:28:00Z")
        created.assignment.arrive(arrivedAt)
        created.assignment.start(startedAt)
        created.assignment.complete(completedAt)
        repository.save(created.assignment)

        val freshRepository = PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
        val reloaded = freshRepository.findById(created.assignment.id)

        assertEquals(arrivedAt, reloaded?.arrivedAt)
        assertEquals(startedAt, reloaded?.startedAt)
        assertEquals(completedAt, reloaded?.completedAt)
        assertEquals(completedAt, reloaded?.statusChangedAt)
    }

    @Test
    fun `an assignment that has only arrived reloads with startedAt and completedAt still null`() {
        val created = Assignment.create(OrderReference("postgres-order-lifecycle-2"), DriverReference("postgres-driver-lifecycle-2"))
        val arrivedAt = Instant.parse("2026-08-02T20:19:00Z")
        created.assignment.arrive(arrivedAt)
        repository.save(created.assignment)

        val reloaded = repository.findById(created.assignment.id)

        assertEquals(arrivedAt, reloaded?.arrivedAt)
        assertNull(reloaded?.startedAt)
        assertNull(reloaded?.completedAt)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `isTest true round-trips through PostgreSQL`() {
        val created = Assignment.create(
            OrderReference("postgres-order-is-test-true"),
            DriverReference("postgres-driver-is-test-true"),
            isTest = true
        )

        repository.save(created.assignment)

        assertEquals(true, repository.findById(created.assignment.id)?.isTest)
    }

    @Test
    fun `an assignment saved without isTest loads back with isTest false`() {
        val created = Assignment.create(OrderReference("postgres-order-is-test-default"), DriverReference("postgres-driver-is-test-default"))

        repository.save(created.assignment)

        assertEquals(false, repository.findById(created.assignment.id)?.isTest)
    }
}
