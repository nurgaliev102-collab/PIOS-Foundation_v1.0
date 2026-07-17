package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DriverAvailabilityApplicationServiceTest {

    private val service = DriverAvailabilityApplicationService()

    @Test
    fun `handling a command that changes availability returns the resulting event`() {
        val driver = Driver(id = DriverId("driver-1"), availability = Availability.UNAVAILABLE)
        val command = DeclareAvailabilityCommand(DriverId("driver-1"), Availability.AVAILABLE)

        val event = service.handle(driver, command)

        assertNotNull(event)
        assertEquals(Availability.AVAILABLE, driver.availability)
    }

    @Test
    fun `handling a command targeting a different driver is rejected`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = DeclareAvailabilityCommand(DriverId("driver-2"), Availability.AVAILABLE)

        assertFailsWith<IllegalArgumentException> {
            service.handle(driver, command)
        }
    }

    @Test
    fun `handling a command with no actual change returns no event`() {
        val driver = Driver(id = DriverId("driver-1"), availability = Availability.AVAILABLE)
        val command = DeclareAvailabilityCommand(DriverId("driver-1"), Availability.AVAILABLE)

        val event = service.handle(driver, command)

        assertNull(event)
    }
}
