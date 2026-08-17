package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `findAll includes a driver saved to PostgreSQL`() {
        val driver = Driver(DriverId("postgres-driver-list-1"), availability = Availability.AVAILABLE)

        repository.save(driver)

        assertTrue(repository.findAll().any { it.id == driver.id && it.availability == Availability.AVAILABLE })
    }

    @Test
    fun `createdAt round-trips through PostgreSQL`() {
        val createdAt = Instant.parse("2026-08-02T15:44:10Z")
        val driver = Driver(DriverId("postgres-driver-created-at"), createdAt = createdAt)

        repository.save(driver)

        assertEquals(createdAt, repository.findById(driver.id)?.createdAt)
    }

    @Test
    fun `a driver saved without createdAt loads back with createdAt null`() {
        val driver = Driver(DriverId("postgres-driver-no-created-at"))

        repository.save(driver)

        assertNull(repository.findById(driver.id)?.createdAt)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `isTest true round-trips through PostgreSQL`() {
        val driver = Driver(DriverId("postgres-driver-is-test-true"), isTest = true)

        repository.save(driver)

        assertEquals(true, repository.findById(driver.id)?.isTest)
    }

    @Test
    fun `a driver saved without isTest loads back with isTest false`() {
        val driver = Driver(DriverId("postgres-driver-is-test-default"))

        repository.save(driver)

        assertEquals(false, repository.findById(driver.id)?.isTest)
    }
}
