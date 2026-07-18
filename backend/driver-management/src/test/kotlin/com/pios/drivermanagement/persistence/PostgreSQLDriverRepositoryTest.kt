package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves the save/load lifecycle described by [DriverRepository] against
 * a real PostgreSQL database, not merely in memory (PostgreSQL
 * Persistence Driver Management v1.0; ADR-025).
 */
class PostgreSQLDriverRepositoryTest {

    private val repository = PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `a driver saved to PostgreSQL can be loaded back with its identity and availability preserved`() {
        val driver = Driver(DriverId("postgres-driver-1"), availability = Availability.AVAILABLE)

        repository.save(driver)
        val loaded = repository.findById(driver.id)

        assertEquals(driver.id, loaded?.id)
        assertEquals(Availability.AVAILABLE, loaded?.availability)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(DriverId("postgres-repository-test-never-saved")))
    }

    @Test
    fun `saving again after an availability change overwrites the previously persisted row`() {
        val driver = Driver(DriverId("postgres-driver-2"))
        repository.save(driver)

        driver.declareAvailability(Availability.AVAILABLE)
        repository.save(driver)

        assertEquals(Availability.AVAILABLE, repository.findById(driver.id)?.availability)
    }
}
