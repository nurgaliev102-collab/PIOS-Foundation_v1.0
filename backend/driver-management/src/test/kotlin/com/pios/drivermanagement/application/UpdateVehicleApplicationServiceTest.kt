package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UpdateVehicleApplicationServiceTest {

    private val repository = InMemoryDriverRepository()
    private val service = UpdateVehicleApplicationService(repository)

    @Test
    fun `handling a command records the vehicle on the driver`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = UpdateVehicleCommand(DriverId("driver-1"), "Lada", "Vesta", "белый", "А123БВ102", 4)

        val result = service.handle(driver, command)

        assertEquals("Lada", result.vehicleMake)
        assertEquals(4, result.vehicleSeatCount)
    }

    @Test
    fun `handling a command persists the driver through the repository`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = UpdateVehicleCommand(DriverId("driver-1"), "Lada", "Vesta", "белый", "А123БВ102", 4)

        service.handle(driver, command)

        assertEquals("Vesta", repository.findById(DriverId("driver-1"))?.vehicleModel)
    }

    @Test
    fun `handling a command targeting a different driver is rejected`() {
        val driver = Driver(id = DriverId("driver-1"))
        val command = UpdateVehicleCommand(DriverId("driver-2"), "Lada", null, null, null, null)

        assertFailsWith<IllegalArgumentException> {
            service.handle(driver, command)
        }
    }

    @Test
    fun `handling a command for an unsaved driver throws DriverNotFoundException`() {
        val command = UpdateVehicleCommand(DriverId("never-saved"), "Lada", null, null, null, null)

        assertFailsWith<DriverNotFoundException> {
            service.handle(command)
        }
    }

    @Test
    fun `an invalid seat count surfaces as IllegalArgumentException, from the domain`() {
        val driver = Driver(id = DriverId("driver-1"))
        repository.save(driver)
        val command = UpdateVehicleCommand(DriverId("driver-1"), null, null, null, null, 0)

        assertFailsWith<IllegalArgumentException> {
            service.handle(command)
        }
    }

    @Test
    fun `handling a command by id restores the driver first, then records the vehicle`() {
        repository.save(Driver(id = DriverId("driver-4")))
        val command = UpdateVehicleCommand(DriverId("driver-4"), "Kia", "Rio", null, null, null)

        val result = service.handle(command)

        assertEquals("Kia", result.vehicleMake)
        assertNull(result.vehicleColor)
    }
}
