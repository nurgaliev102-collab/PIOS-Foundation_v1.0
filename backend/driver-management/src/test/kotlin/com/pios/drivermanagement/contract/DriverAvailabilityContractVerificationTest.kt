package com.pios.drivermanagement.contract

import com.pios.dispatch.application.DriverAvailabilityNotificationHandler
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverAvailabilityChangedPublisher
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract verification for Driver Management -> Dispatch
 * (INTERFACE_CONTRACTS.md Section 5; ADR-027). Proves that this module's
 * provider representation of DriverAvailabilityChanged
 * (`DriverAvailabilityChangedPublisher`) and Dispatch's consumer handler
 * (`DriverAvailabilityNotificationHandler`) agree on the same primitive
 * contract shape.
 *
 * This test alone justifies driver-management's test-scoped dependency
 * on dispatch (see build.gradle.kts); no production code in either
 * module depends on the other (ADR-027).
 */
class DriverAvailabilityContractVerificationTest {

    private val service = DriverAvailabilityApplicationService(InMemoryDriverRepository())
    private val publisher = DriverAvailabilityChangedPublisher()
    private val consumerHandler = DriverAvailabilityNotificationHandler()

    @Test
    fun `driver management's provider payload is accepted by Dispatch's consumer handler`() {
        val driver = Driver(DriverId("driver-1"))
        val event = service.handle(driver, DeclareAvailabilityCommand(driver.id, Availability.AVAILABLE))!!

        val payload = publisher.toContractPayload(event)
        val result = consumerHandler.handle(payload.driverReference, payload.available)

        assertEquals("driver-1", result)
    }

    @Test
    fun `the contract payload reflects the driver's declared availability`() {
        val driver = Driver(DriverId("driver-2"))
        val event = service.handle(driver, DeclareAvailabilityCommand(driver.id, Availability.AVAILABLE))!!

        val payload = publisher.toContractPayload(event)

        assertEquals("driver-2", payload.driverReference)
        assertTrue(payload.available)
    }

    @Test
    fun `the contract payload reflects a driver becoming unavailable`() {
        val driver = Driver(DriverId("driver-3"), availability = Availability.AVAILABLE)
        val event = service.handle(driver, DeclareAvailabilityCommand(driver.id, Availability.UNAVAILABLE))!!

        val payload = publisher.toContractPayload(event)

        assertEquals(false, payload.available)
    }
}
