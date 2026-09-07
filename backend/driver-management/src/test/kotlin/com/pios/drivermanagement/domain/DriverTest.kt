package com.pios.drivermanagement.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DriverTest {

    @Test
    fun `a newly created driver starts unavailable`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertEquals(Availability.UNAVAILABLE, driver.availability)
    }

    @Test
    fun `createdAt defaults to null when not supplied`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertNull(driver.createdAt)
    }

    @Test
    fun `createdAt carries whatever moment it is constructed with`() {
        val createdAt = Instant.parse("2026-08-02T15:44:10Z")

        val driver = Driver(id = DriverId("driver-1"), createdAt = createdAt)

        assertEquals(createdAt, driver.createdAt)
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

    // --- Vehicle (PIOS Group and Long-Distance Rides Roadmap, Stage 1) ---

    @Test
    fun `vehicle fields default to null when not supplied`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertNull(driver.vehicleMake)
        assertNull(driver.vehicleModel)
        assertNull(driver.vehicleColor)
        assertNull(driver.vehiclePlateNumber)
        assertNull(driver.vehicleSeatCount)
    }

    @Test
    fun `updateVehicle records every field`() {
        val driver = Driver(id = DriverId("driver-1"))

        driver.updateVehicle(make = "Lada", model = "Vesta", color = "белый", plateNumber = "А123БВ102", seatCount = 4)

        assertEquals("Lada", driver.vehicleMake)
        assertEquals("Vesta", driver.vehicleModel)
        assertEquals("белый", driver.vehicleColor)
        assertEquals("А123БВ102", driver.vehiclePlateNumber)
        assertEquals(4, driver.vehicleSeatCount)
    }

    @Test
    fun `updateVehicle trims whitespace and treats a blank string as not provided`() {
        val driver = Driver(id = DriverId("driver-1"))

        driver.updateVehicle(make = "  Lada  ", model = "   ", color = null, plateNumber = "", seatCount = null)

        assertEquals("Lada", driver.vehicleMake)
        assertNull(driver.vehicleModel)
        assertNull(driver.vehicleColor)
        assertNull(driver.vehiclePlateNumber)
    }

    @Test
    fun `updateVehicle replaces the whole record, not merging with the previous one`() {
        val driver = Driver(id = DriverId("driver-1"))
        driver.updateVehicle(make = "Lada", model = "Vesta", color = "белый", plateNumber = "А123БВ102", seatCount = 4)

        driver.updateVehicle(make = "Kia", model = null, color = null, plateNumber = null, seatCount = null)

        assertEquals("Kia", driver.vehicleMake)
        assertNull(driver.vehicleModel)
        assertNull(driver.vehicleColor)
        assertNull(driver.vehiclePlateNumber)
        assertNull(driver.vehicleSeatCount)
    }

    @Test
    fun `updateVehicle rejects a zero seat count`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertFailsWith<IllegalArgumentException> {
            driver.updateVehicle(make = null, model = null, color = null, plateNumber = null, seatCount = 0)
        }
    }

    @Test
    fun `updateVehicle rejects a negative seat count`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertFailsWith<IllegalArgumentException> {
            driver.updateVehicle(make = null, model = null, color = null, plateNumber = null, seatCount = -1)
        }
    }

    // --- Long-distance preference (PIOS Group and Long-Distance Rides Roadmap, Stage 3) ---

    @Test
    fun `acceptsLongDistanceTrips defaults to false when not supplied`() {
        val driver = Driver(id = DriverId("driver-1"))

        assertEquals(false, driver.acceptsLongDistanceTrips)
    }

    @Test
    fun `updateLongDistancePreference records the driver's declaration`() {
        val driver = Driver(id = DriverId("driver-1"))

        driver.updateLongDistancePreference(true)

        assertEquals(true, driver.acceptsLongDistanceTrips)
    }

    @Test
    fun `updateLongDistancePreference can withdraw a previous declaration`() {
        val driver = Driver(id = DriverId("driver-1"))
        driver.updateLongDistancePreference(true)

        driver.updateLongDistancePreference(false)

        assertEquals(false, driver.acceptsLongDistanceTrips)
    }
}
