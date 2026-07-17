package com.pios.drivermanagement.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DriverTest {

    @Test
    fun `a newly created driver starts unavailable`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertEquals(Availability.UNAVAILABLE, driver.availability)
    }

    @Test
    fun `declaring a different availability changes the driver's current state`() {
        val driver = Driver(id = DriverId("driver-1"), availability = Availability.UNAVAILABLE)

        driver.declareAvailability(Availability.AVAILABLE)

        assertEquals(Availability.AVAILABLE, driver.availability)
    }

    @Test
    fun `declaring a different availability produces a DriverAvailabilityChanged event`() {
        val driver = Driver(id = DriverId("driver-1"), availability = Availability.UNAVAILABLE)

        val event = driver.declareAvailability(Availability.AVAILABLE)

        assertNotNull(event)
        assertEquals(DriverId("driver-1"), event.driverId)
        assertEquals(Availability.AVAILABLE, event.availability)
    }

    @Test
    fun `declaring the same availability produces no event`() {
        val driver = Driver(id = DriverId("driver-1"), availability = Availability.AVAILABLE)

        val event = driver.declareAvailability(Availability.AVAILABLE)

        assertNull(event)
    }

    @Test
    fun `a driver has exactly one current availability state at any time`() {
        val driver = Driver(id = DriverId("driver-1"), availability = Availability.UNAVAILABLE)

        driver.declareAvailability(Availability.AVAILABLE)
        driver.declareAvailability(Availability.UNAVAILABLE)

        assertEquals(Availability.UNAVAILABLE, driver.availability)
    }
}
