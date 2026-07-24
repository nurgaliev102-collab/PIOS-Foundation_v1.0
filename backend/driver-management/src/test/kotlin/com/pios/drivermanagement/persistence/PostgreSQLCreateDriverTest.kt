package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.CreateDriverCommand
import com.pios.drivermanagement.application.DriverAlreadyExistsException
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves Create Driver (Sprint 3A: MVR Pilot Enablement) against a real
 * PostgreSQL database, not merely in memory -- mirroring
 * [PostgreSQLDriverLifecycleTest]'s own pattern. Uses a randomized driver
 * id per test, consistent with this project's own established fix for the
 * fixed-id test-pollution category documented in Sprint 1/Sprint 2's own
 * reports.
 */
class PostgreSQLCreateDriverTest {

    private val repository = PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = CreateDriverApplicationService(repository)

    @Test
    fun `a driver created through PostgreSQL can be loaded back with its default availability`() {
        val driverId = DriverId("create-driver-${UUID.randomUUID()}")

        val created = service.handle(CreateDriverCommand(driverId))

        assertEquals(driverId, created.id)
        assertEquals(Availability.UNAVAILABLE, created.availability)
        assertEquals(Availability.UNAVAILABLE, repository.findById(driverId)?.availability)
    }

    @Test
    fun `creating a driver twice for the same id raises DriverAlreadyExistsException and does not overwrite it`() {
        val driverId = DriverId("create-driver-${UUID.randomUUID()}")
        service.handle(CreateDriverCommand(driverId))
        repository.save(repository.findById(driverId)!!.also { it.declareAvailability(Availability.AVAILABLE) })

        assertFailsWith<DriverAlreadyExistsException> {
            service.handle(CreateDriverCommand(driverId))
        }

        assertEquals(Availability.AVAILABLE, repository.findById(driverId)?.availability)
    }
}
