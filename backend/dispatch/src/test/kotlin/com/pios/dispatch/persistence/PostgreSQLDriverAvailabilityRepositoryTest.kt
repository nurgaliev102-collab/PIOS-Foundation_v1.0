package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
