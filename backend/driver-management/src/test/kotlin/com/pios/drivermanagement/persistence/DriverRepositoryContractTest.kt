package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A shared behavioral contract every [DriverRepository] implementation
 * must satisfy, run identically against [InMemoryDriverRepository] and
 * [PostgreSQLDriverRepository] (PostgreSQL Persistence Driver Management
 * v1.0). Proves [com.pios.drivermanagement.application.DriverAvailabilityApplicationService]
 * can depend on either without any change to its own code
 * (APPLICATION_ARCHITECTURE.md Section 2) — the repository interface is
 * unchanged and both adapters honor it identically.
 */
abstract class DriverRepositoryContractTest {

    abstract fun createRepository(): DriverRepository

    @Test
    fun `a saved driver can be found by its id with its availability intact`() {
        val repository = createRepository()
        val driver = Driver(DriverId("contract-test-driver-1"), availability = Availability.AVAILABLE)

        repository.save(driver)

        assertEquals(Availability.AVAILABLE, repository.findById(driver.id)?.availability)
    }

    @Test
    fun `an id that was never saved returns null rather than throwing`() {
        val repository = createRepository()

        assertNull(repository.findById(DriverId("driver-repository-contract-test-never-saved")))
    }

    @Test
    fun `saving the same driver id again overwrites its previously persisted availability`() {
        val repository = createRepository()
        val driver = Driver(DriverId("contract-test-driver-2"))
        repository.save(driver)

        driver.declareAvailability(Availability.AVAILABLE)
        repository.save(driver)

        assertEquals(Availability.AVAILABLE, repository.findById(driver.id)?.availability)
    }
}

class InMemoryDriverRepositoryContractTest : DriverRepositoryContractTest() {
    override fun createRepository(): DriverRepository = InMemoryDriverRepository()
}

class PostgreSQLDriverRepositoryContractTest : DriverRepositoryContractTest() {
    override fun createRepository(): DriverRepository =
        PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
