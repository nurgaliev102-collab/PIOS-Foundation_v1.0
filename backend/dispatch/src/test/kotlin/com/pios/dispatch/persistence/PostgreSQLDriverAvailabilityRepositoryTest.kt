package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the basic lifecycle [DriverAvailabilityRepository] describes,
 * against a real PostgreSQL database (Dispatch Consumer Foundation v1.0).
 */
class PostgreSQLDriverAvailabilityRepositoryTest {

    private val repository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `an unrecorded driver reference has no availability record`() {
        assertNull(repository.findByDriverReference(DriverReference("repo-driver-${UUID.randomUUID()}")))
    }

    @Test
    fun `upserting a record makes it findable`() {
        val driverReference = DriverReference("repo-driver-${UUID.randomUUID()}")

        repository.upsert(DriverAvailabilityRecord(driverReference, available = true))

        assertEquals(true, repository.findByDriverReference(driverReference)?.available)
    }

    @Test
    fun `upserting the same driver reference again replaces the previous value`() {
        val driverReference = DriverReference("repo-driver-${UUID.randomUUID()}")

        repository.upsert(DriverAvailabilityRecord(driverReference, available = true))
        repository.upsert(DriverAvailabilityRecord(driverReference, available = false))

        assertEquals(false, repository.findByDriverReference(driverReference)?.available)
    }

    @Test
    fun `markProcessed returns true only the first time a given eventId is recorded`() {
        val eventId = "repo-event-${UUID.randomUUID()}"

        assertTrue(repository.markProcessed(eventId))
        assertTrue(!repository.markProcessed(eventId))
    }

    // --- FR-003A: findLongestIdleAvailable ---

    /**
     * A randomized, far-past timestamp -- never a shared literal constant.
     * Two tests (in this file, or in any other file sharing this same
     * pios_dispatch_test database and run in the same suite) that both
     * force a row's `updated_at` to the *identical* instant create a tie
     * `ORDER BY updated_at ASC LIMIT 1` resolves arbitrarily -- exactly the
     * failure this randomization avoids, at negligible (~1 in 3 billion)
     * collision odds.
     */
    private fun farPastTimestamp(): java.sql.Timestamp =
        java.sql.Timestamp.from(java.time.Instant.parse("2000-01-01T00:00:00Z").minusSeconds((0..3_000_000_000L).random()))

    /**
     * `driver_availability` is never cleaned between test runs anywhere in
     * this suite (every existing test relies only on unique UUIDs per row,
     * never on the table's overall contents or order) -- harmless until a
     * query that cares about *ordering across the whole table* exists. Once
     * one does ([DriverAvailabilityRepository.findLongestIdleAvailable]),
     * leaving a far-past-dated row behind forever would make it a permanent
     * contender against every future run's own "oldest" row (an
     * accumulating collision risk across repeated runs, not just within
     * one) -- so, uniquely among this file's tests, these two clean up the
     * exact rows they create.
     */
    private fun deleteDriverAvailability(references: List<DriverReference>) {
        val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
        references.forEach { jdbcTemplate.update("DELETE FROM driver_availability WHERE driver_reference = ?", it.driverId) }
    }

    @Test
    fun `findLongestIdleAvailable returns the available driver whose record is oldest, regardless of other rows in this shared table`() {
        val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
        val oldest = DriverReference("repo-driver-oldest-${UUID.randomUUID()}")
        val newer = DriverReference("repo-driver-newer-${UUID.randomUUID()}")

        try {
            repository.upsert(DriverAvailabilityRecord(oldest, available = true))
            // Forced far into the past so this row is deterministically
            // older than anything this shared pios_dispatch_test table
            // already holds from other test runs -- the only way to make
            // this assertion reliable without depending on the table's
            // full history. Randomized (see farPastTimestamp's own KDoc),
            // and still deleted in the finally block below regardless --
            // both defenses, not either/or.
            jdbcTemplate.update(
                "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
                farPastTimestamp(),
                oldest.driverId
            )
            repository.upsert(DriverAvailabilityRecord(newer, available = true))

            assertEquals(oldest, repository.findLongestIdleAvailable())
        } finally {
            deleteDriverAvailability(listOf(oldest, newer))
        }
    }

    @Test
    fun `findLongestIdleAvailable never returns an unavailable driver, even if it is the oldest`() {
        val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
        val oldestButUnavailable = DriverReference("repo-driver-oldest-unavail-${UUID.randomUUID()}")

        try {
            repository.upsert(DriverAvailabilityRecord(oldestButUnavailable, available = false))
            jdbcTemplate.update(
                "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
                farPastTimestamp(),
                oldestButUnavailable.driverId
            )

            assertNotEquals(oldestButUnavailable, repository.findLongestIdleAvailable())
        } finally {
            deleteDriverAvailability(listOf(oldestButUnavailable))
        }
    }

    /**
     * FR-003A, decline/lapse retry. Proves the real `NOT IN (...)` SQL this
     * class's own [findLongestIdleAvailable] issues actually excludes the
     * oldest row when asked to, and falls through to the next-oldest
     * eligible one -- not merely "returns null/no match" the way an
     * unparameterized query might if the exclusion clause were silently
     * dropped or malformed.
     */
    @Test
    fun `findLongestIdleAvailable excludes the named driver, falling through to the next-oldest available one`() {
        val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
        val excluded = DriverReference("repo-driver-excluded-${UUID.randomUUID()}")
        val nextOldest = DriverReference("repo-driver-next-oldest-${UUID.randomUUID()}")

        try {
            // A random reference point (collision-avoidance against other
            // tests' own rows, same reasoning as farPastTimestamp's own
            // KDoc), with `excluded` forced a further, fixed 115 days
            // *older* than `nextOldest` -- deterministic ordering between
            // these two specific rows, not left to the same random draw
            // farPastTimestamp() on its own would give each independently
            // (which could tie or invert their relative order).
            val referenceInstant = Instant.parse("1995-01-01T00:00:00Z").minusSeconds((0..1_000_000_000L).random())
            repository.upsert(DriverAvailabilityRecord(excluded, available = true))
            jdbcTemplate.update(
                "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
                java.sql.Timestamp.from(referenceInstant.minusSeconds(10_000_000)),
                excluded.driverId
            )
            repository.upsert(DriverAvailabilityRecord(nextOldest, available = true))
            jdbcTemplate.update(
                "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
                java.sql.Timestamp.from(referenceInstant),
                nextOldest.driverId
            )

            // Without the exclusion, `excluded` (forced strictly older) would win.
            assertEquals(excluded, repository.findLongestIdleAvailable())
            assertEquals(nextOldest, repository.findLongestIdleAvailable(excluding = setOf(excluded)))
        } finally {
            deleteDriverAvailability(listOf(excluded, nextOldest))
        }
    }

    @Test
    fun `findLongestIdleAvailable never returns a driver named in excluding, even as the sole oldest candidate`() {
        // Asserts the negative precisely (never this driver), not "returns
        // null overall" -- this table is shared across this whole suite,
        // so other tests' own available=true rows may legitimately still
        // be present and eligible; that is not this test's own concern.
        val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
        val onlyDriver = DriverReference("repo-driver-only-${UUID.randomUUID()}")

        try {
            repository.upsert(DriverAvailabilityRecord(onlyDriver, available = true))
            jdbcTemplate.update(
                "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
                farPastTimestamp(),
                onlyDriver.driverId
            )

            assertNotEquals(onlyDriver, repository.findLongestIdleAvailable(excluding = setOf(onlyDriver)))
        } finally {
            deleteDriverAvailability(listOf(onlyDriver))
        }
    }
}
