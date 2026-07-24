package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetrieveDriverAvailabilityHandlerTest {

    private val repository = InMemoryDriverRepository()
    private val handler = RetrieveDriverAvailabilityHandler(repository)

    @Test
    fun `returns the driver when one is saved for the given id`() {
        val driver = Driver(DriverId("driver-1"), Availability.AVAILABLE)
        repository.save(driver)

        val result = handler.handle(DriverId("driver-1"))

        assertEquals(driver.id, result.id)
        assertEquals(Availability.AVAILABLE, result.availability)
    }

    @Test
    fun `throws DriverNotFoundException when no driver is saved for the given id`() {
        assertFailsWith<DriverNotFoundException> {
            handler.handle(DriverId("unknown"))
        }
    }

    @Test
    fun `handleAll returns every saved driver`() {
        val first = Driver(DriverId("list-driver-1"), Availability.AVAILABLE)
        val second = Driver(DriverId("list-driver-2"), Availability.UNAVAILABLE)
        repository.save(first)
        repository.save(second)

        val all = handler.handleAll()

        assertTrue(all.any { it.id == first.id })
        assertTrue(all.any { it.id == second.id })
    }

    @Test
    fun `handleAll on an empty repository returns an empty list`() {
        assertEquals(emptyList(), RetrieveDriverAvailabilityHandler(InMemoryDriverRepository()).handleAll())
    }
}
