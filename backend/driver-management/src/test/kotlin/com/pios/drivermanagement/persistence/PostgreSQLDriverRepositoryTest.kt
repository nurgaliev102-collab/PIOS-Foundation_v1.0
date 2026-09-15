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

    // --- Vehicle (PIOS Group and Long-Distance Rides Roadmap, Stage 1) ---

    @Test
    fun `vehicle fields round-trip through PostgreSQL`() {
        val driver = Driver(DriverId("postgres-driver-vehicle-1"))
        driver.updateVehicle(make = "Lada", model = "Vesta", color = "белый", plateNumber = "А123БВ102", seatCount = 4)

        repository.save(driver)
        val loaded = repository.findById(driver.id)

        assertEquals("Lada", loaded?.vehicleMake)
        assertEquals("Vesta", loaded?.vehicleModel)
        assertEquals("белый", loaded?.vehicleColor)
        assertEquals("А123БВ102", loaded?.vehiclePlateNumber)
        assertEquals(4, loaded?.vehicleSeatCount)
    }

    @Test
    fun `a driver saved without a vehicle loads back with every vehicle field null`() {
        val driver = Driver(DriverId("postgres-driver-vehicle-none"))

        repository.save(driver)

        val loaded = repository.findById(driver.id)
        assertNull(loaded?.vehicleMake)
        assertNull(loaded?.vehicleSeatCount)
    }

    @Test
    fun `updating a vehicle on a second save overwrites the previously persisted one, read from a fresh repository instance`() {
        val driver = Driver(DriverId("postgres-driver-vehicle-2"))
        driver.updateVehicle(make = "Lada", model = "Vesta", color = null, plateNumber = null, seatCount = null)
        repository.save(driver)

        driver.updateVehicle(make = "Kia", model = "Rio", color = null, plateNumber = null, seatCount = null)
        repository.save(driver)

        val freshRepository = PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
        assertEquals("Kia", freshRepository.findById(driver.id)?.vehicleMake)
        assertEquals("Rio", freshRepository.findById(driver.id)?.vehicleModel)
    }

    // --- Long-distance preference (PIOS Group and Long-Distance Rides Roadmap, Stage 3) ---

    @Test
    fun `acceptsLongDistanceTrips round-trips through PostgreSQL`() {
        val driver = Driver(DriverId("postgres-driver-ld-true"))
        driver.updateLongDistancePreference(true)

        repository.save(driver)

        assertEquals(true, repository.findById(driver.id)?.acceptsLongDistanceTrips)
    }

    @Test
    fun `a driver saved without a long-distance preference loads back with acceptsLongDistanceTrips false`() {
        val driver = Driver(DriverId("postgres-driver-ld-default"))

        repository.save(driver)

        assertEquals(false, repository.findById(driver.id)?.acceptsLongDistanceTrips)
    }

    // --- ADR-073: Driver-to-Driver Referral -- Single-Hop Origin Fact (V12__add_driver_invited_by.sql) ---

    @Test
    fun `invitedByDriverId round-trips through PostgreSQL, proving V12__add_driver_invited_by applied`() {
        val inviter = Driver(DriverId("postgres-driver-invited-by-inviter"))
        repository.save(inviter)
        val invited = Driver(DriverId("postgres-driver-invited-by-invited"), invitedByDriverId = inviter.id.value)

        repository.save(invited)

        assertEquals(inviter.id.value, repository.findById(invited.id)?.invitedByDriverId)
    }

    @Test
    fun `a driver saved without an inviter loads back with invitedByDriverId null`() {
        val driver = Driver(DriverId("postgres-driver-invited-by-none"))

        repository.save(driver)

        assertNull(repository.findById(driver.id)?.invitedByDriverId)
    }

    @Test
    fun `countInvitedBy counts every driver invited by the given driver, real and isTest alike, over real PostgreSQL data`() {
        val inviter = Driver(DriverId("postgres-driver-count-invited-inviter"))
        repository.save(inviter)
        repository.save(Driver(DriverId("postgres-driver-count-invited-real-1"), invitedByDriverId = inviter.id.value))
        repository.save(Driver(DriverId("postgres-driver-count-invited-real-2"), invitedByDriverId = inviter.id.value))
        repository.save(Driver(DriverId("postgres-driver-count-invited-test"), invitedByDriverId = inviter.id.value, isTest = true))
        repository.save(Driver(DriverId("postgres-driver-count-invited-unrelated")))

        assertEquals(3L, repository.countInvitedBy(inviter.id))
    }

    @Test
    fun `countInvitedBy returns zero for a driver nobody has ever named as their inviter`() {
        val driver = Driver(DriverId("postgres-driver-count-invited-lonely"))
        repository.save(driver)

        assertEquals(0L, repository.countInvitedBy(driver.id))
    }
}
