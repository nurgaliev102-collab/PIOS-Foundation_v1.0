package com.pios.dispatch.persistence

import com.pios.dispatch.application.TripRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripId
import com.pios.dispatch.domain.TripStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A shared behavioral contract every [TripRepository] implementation
 * must satisfy, run identically against [InMemoryTripRepository] and
 * [PostgreSQLTripRepository] -- mirrors [AssignmentRepositoryContractTest]
 * exactly (ADR-063; Task 11, Trip Domain Foundation).
 */
abstract class TripRepositoryContractTest {

    abstract fun createRepository(): TripRepository

    /**
     * Makes [assignment] exist wherever this test's own [TripRepository]
     * expects an Assignment to be found -- a no-op for the in-memory
     * variant, a real [PostgreSQLAssignmentRepository.save] for the
     * PostgreSQL variant, since `trips.assignment_id` carries a real
     * foreign key to `assignments.id` (V12 migration) that PostgreSQL
     * actually enforces, unlike the in-memory double.
     */
    abstract fun persistAssignment(assignment: Assignment)

    private fun newAssignment(orderSuffix: String): Assignment {
        val assignment = Assignment.create(
            OrderReference("trip-contract-test-order-$orderSuffix"),
            DriverReference("trip-contract-test-driver-$orderSuffix")
        ).assignment
        persistAssignment(assignment)
        return assignment
    }

    @Test
    fun `a saved trip can be found by its id with its status intact`() {
        val repository = createRepository()
        val created = Trip.create(newAssignment("1"))

        repository.save(created.trip)

        assertEquals(TripStatus.CREATED, repository.findById(created.trip.id)?.status)
    }

    @Test
    fun `an id that was never saved returns null rather than throwing`() {
        val repository = createRepository()

        assertNull(repository.findById(TripId("trip-repository-contract-test-never-saved")))
    }

    @Test
    fun `saving the same trip id again overwrites its previously persisted status`() {
        val repository = createRepository()
        val created = Trip.create(newAssignment("2"))
        repository.save(created.trip)

        created.trip.arrive()
        repository.save(created.trip)

        assertEquals(TripStatus.ARRIVED, repository.findById(created.trip.id)?.status)
    }

    @Test
    fun `findByAssignmentId returns the trip connected to that assignment`() {
        val repository = createRepository()
        val assignment = newAssignment("3")
        val created = Trip.create(assignment)
        repository.save(created.trip)

        val found = repository.findByAssignmentId(assignment.id)

        assertEquals(created.trip.id, found?.id)
    }

    @Test
    fun `findByAssignmentId returns null when the assignment has no trip`() {
        val repository = createRepository()

        assertNull(repository.findByAssignmentId(newAssignment("4").id))
    }

    @Test
    fun `findByExecutingDriver follows a reassigned executor and not the immutable committer`() {
        val repository = createRepository()
        val created = Trip.create(newAssignment("5"))
        val substitute = DriverReference("trip-contract-test-substitute-${created.trip.id.value}")
        created.trip.assignExecutingDriver(substitute)
        repository.save(created.trip)

        assertEquals(listOf(created.trip.id), repository.findByExecutingDriver(substitute).map { it.id })
        assertEquals(emptyList(), repository.findByExecutingDriver(created.trip.driver))
    }
}

class InMemoryTripRepositoryContractTest : TripRepositoryContractTest() {
    override fun createRepository(): TripRepository = InMemoryTripRepository()
    override fun persistAssignment(assignment: Assignment) = Unit
}

class PostgreSQLTripRepositoryContractTest : TripRepositoryContractTest() {
    private val assignmentRepository = PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    override fun createRepository(): TripRepository =
        PostgreSQLTripRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    override fun persistAssignment(assignment: Assignment) = assignmentRepository.save(assignment)
}
