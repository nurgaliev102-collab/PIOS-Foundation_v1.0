package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `findAll includes every saved driver`() {
        val repository = createRepository()
        val first = Driver(DriverId("contract-test-findall-1"), availability = Availability.AVAILABLE)
        val second = Driver(DriverId("contract-test-findall-2"), availability = Availability.UNAVAILABLE)
        repository.save(first)
        repository.save(second)

        val all = repository.findAll()

        assertTrue(all.any { it.id == first.id && it.availability == Availability.AVAILABLE })
        assertTrue(all.any { it.id == second.id && it.availability == Availability.UNAVAILABLE })
    }

    // --- ADR-073: Driver-to-Driver Referral -- Single-Hop Origin Fact ---

    @Test
    fun `countInvitedBy counts every driver naming the given driver as invitedByDriverId, real and isTest alike`() {
        // Correction, 2026-09-15, found via live production E2E: an earlier
        // version of this repository excluded isTest drivers, which made
        // the feature permanently unverifiable through this project's own
        // isTest E2E convention. ADR-073 Consequences named that filter as
        // a developer recommendation, not a business rule, with an
        // explicit escalation condition this crossed -- see
        // DriverRepository.countInvitedBy's own KDoc.
        val repository = createRepository()
        val inviter = Driver(DriverId("contract-test-invited-by-inviter"))
        repository.save(inviter)
        repository.save(Driver(DriverId("contract-test-invited-by-real"), invitedByDriverId = inviter.id.value))
        repository.save(Driver(DriverId("contract-test-invited-by-test"), invitedByDriverId = inviter.id.value, isTest = true))
        repository.save(Driver(DriverId("contract-test-invited-by-unrelated")))

        assertEquals(2L, repository.countInvitedBy(inviter.id))
    }

    @Test
    fun `countInvitedBy is zero for a driver with no invitees`() {
        val repository = createRepository()
        val driver = Driver(DriverId("contract-test-invited-by-none"))
        repository.save(driver)

        assertEquals(0L, repository.countInvitedBy(driver.id))
    }
}

class InMemoryDriverRepositoryContractTest : DriverRepositoryContractTest() {
    override fun createRepository(): DriverRepository = InMemoryDriverRepository()
}

class PostgreSQLDriverRepositoryContractTest : DriverRepositoryContractTest() {
    override fun createRepository(): DriverRepository =
        PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
