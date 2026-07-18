package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverNotFoundException
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the full Driver lifecycle through the real PostgreSQL adapter:
 * create, persist, restore by id (via the id-only overload introduced in
 * Persistence Quality Foundation v1.0), and continue declaring
 * availability, persisting again — end to end against an actual
 * database, not merely in memory.
 */
class PostgreSQLDriverLifecycleTest {

    private val repository = PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = DriverAvailabilityApplicationService(repository)

    @Test
    fun `a driver declared available through PostgreSQL can be restored by id and continue declaring availability`() {
        val driver = Driver(DriverId("postgres-lifecycle-driver-1"))
        service.handle(driver, DeclareAvailabilityCommand(driver.id, Availability.AVAILABLE))

        val event = service.handle(DeclareAvailabilityCommand(driver.id, Availability.UNAVAILABLE))

        assertEquals(Availability.UNAVAILABLE, event?.availability)
        assertEquals(Availability.UNAVAILABLE, repository.findById(driver.id)?.availability)
    }

    @Test
    fun `declaring availability for a driver id that was never saved raises DriverNotFoundException`() {
        assertFailsWith<DriverNotFoundException> {
            service.handle(DeclareAvailabilityCommand(DriverId("postgres-lifecycle-never-saved"), Availability.AVAILABLE))
        }
    }
}
