package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryDriverRepositoryTest {

    private val repository = InMemoryDriverRepository()

    @Test
    fun `create, save, and load preserves a driver's declared availability`() {
        val driver = Driver(DriverId("driver-1"))
        driver.declareAvailability(Availability.AVAILABLE)

        repository.save(driver)
        val loaded = repository.findById(DriverId("driver-1"))

        assertEquals(Availability.AVAILABLE, loaded?.availability)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(DriverId("never-saved")))
    }

    @Test
    fun `saving again after a further availability change overwrites the previous record`() {
        val driver = Driver(DriverId("driver-2"))
        repository.save(driver)

        driver.declareAvailability(Availability.AVAILABLE)
        repository.save(driver)

        assertEquals(Availability.AVAILABLE, repository.findById(DriverId("driver-2"))?.availability)
    }

    @Test
    fun `findAll returns every saved driver`() {
        val first = Driver(DriverId("driver-list-1"), availability = Availability.AVAILABLE)
        val second = Driver(DriverId("driver-list-2"), availability = Availability.UNAVAILABLE)
        repository.save(first)
        repository.save(second)

        val all = repository.findAll()

        assertTrue(all.any { it.id == first.id })
        assertTrue(all.any { it.id == second.id })
    }

    @Test
    fun `findAll on an empty repository returns an empty list`() {
        assertEquals(emptyList(), InMemoryDriverRepository().findAll())
    }
}
