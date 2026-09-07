package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateLongDistancePreferenceApplicationServiceTest {

    private val repository = InMemoryDriverRepository()
    private val service = UpdateLongDistancePreferenceApplicationService(repository)

    @Test
    fun `handling a command records the preference on the driver`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = UpdateLongDistancePreferenceCommand(DriverId("driver-1"), true)

        val result = service.handle(driver, command)

        assertEquals(true, result.acceptsLongDistanceTrips)
    }

    @Test
    fun `handling a command persists the driver through the repository`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = UpdateLongDistancePreferenceCommand(DriverId("driver-1"), true)

        service.handle(driver, command)

        assertEquals(true, repository.findById(DriverId("driver-1"))?.acceptsLongDistanceTrips)
    }

    @Test
    fun `handling a command targeting a different driver is rejected`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = UpdateLongDistancePreferenceCommand(DriverId("driver-2"), true)

        assertFailsWith<IllegalArgumentException> {
            service.handle(driver, command)
        }
    }

    @Test
    fun `handling a command for an unsaved driver throws DriverNotFoundException`() {
        val command = UpdateLongDistancePreferenceCommand(DriverId("never-saved"), true)

        assertFailsWith<DriverNotFoundException> {
            service.handle(command)
        }
    }

    @Test
    fun `handling a command by id restores the driver first, then records the preference`() {
        repository.save(Driver(id = DriverId("driver-4")))
        val command = UpdateLongDistancePreferenceCommand(DriverId("driver-4"), true)

        val result = service.handle(command)

        assertEquals(true, result.acceptsLongDistanceTrips)
    }
}
