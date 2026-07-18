package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverNotFoundException
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the full repository lifecycle for the Driver aggregate: create,
 * save, restore by id, and continue a further domain operation — not
 * merely that data round-trips, but that the restored aggregate is fully
 * functional. Also proves the consistent, non-exceptional-by-default
 * handling of a missing aggregate: [DriverAvailabilityApplicationService]
 * raises [DriverNotFoundException] (an application-layer error) rather
 * than letting a null propagate or a persistence-specific exception leak
 * through — [InMemoryDriverRepository.findById] itself never throws.
 */
class DriverRepositoryLifecycleTest {

    private val repository = InMemoryDriverRepository()
    private val service = DriverAvailabilityApplicationService(repository)

    @Test
    fun `a driver created and saved can be restored by id and continue declaring availability`() {
        val driver = Driver(DriverId("driver-1"))
        service.handle(driver, DeclareAvailabilityCommand(driver.id, Availability.AVAILABLE))

        val event = service.handle(DeclareAvailabilityCommand(driver.id, Availability.UNAVAILABLE))

        assertEquals(Availability.UNAVAILABLE, event?.availability)
        assertEquals(Availability.UNAVAILABLE, repository.findById(driver.id)?.availability)
    }

    @Test
    fun `declaring availability for a driver id that was never saved raises DriverNotFoundException`() {
        val exception = assertFailsWith<DriverNotFoundException> {
            service.handle(DeclareAvailabilityCommand(DriverId("never-saved"), Availability.AVAILABLE))
        }
        assertEquals(DriverId("never-saved"), exception.driverId)
    }
}
