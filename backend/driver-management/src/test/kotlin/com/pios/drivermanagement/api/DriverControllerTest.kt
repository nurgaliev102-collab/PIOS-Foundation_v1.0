package com.pios.drivermanagement.api

import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [DriverController] directly, with a real
 * [RetrieveDriverAvailabilityHandler]/[DriverAvailabilityApplicationService]/
 * [CreateDriverApplicationService], no Spring MVC context -- mirroring
 * this project's own constructor-based testing convention (Tranche 2:
 * Passenger Experience REST Transport).
 */
class DriverControllerTest {

    private val repository = InMemoryDriverRepository()
    private val handler = RetrieveDriverAvailabilityHandler(repository)
    private val availabilityService = DriverAvailabilityApplicationService(repository)
    private val createDriverService = CreateDriverApplicationService(repository)
    private val controller = DriverController(handler, availabilityService, createDriverService)

    @Test
    fun `creating a driver returns 201 with the new driver's id and default availability`() {
        val response = controller.createDriver(CreateDriverRequest("new-driver-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("new-driver-1", body.id)
        assertEquals("UNAVAILABLE", body.availability)
        assertEquals(Availability.UNAVAILABLE, repository.findById(DriverId("new-driver-1"))?.availability)
    }

    @Test
    fun `creating a driver for an id that already exists returns 409`() {
        repository.save(Driver(DriverId("existing-driver"), Availability.AVAILABLE))

        val response = controller.createDriver(CreateDriverRequest("existing-driver"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(Availability.AVAILABLE, repository.findById(DriverId("existing-driver"))?.availability)
    }

    @Test
    fun `creating a driver with a blank id returns 400`() {
        val response = controller.createDriver(CreateDriverRequest(""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a known driver id returns 200 with id and availability`() {
        repository.save(Driver(DriverId("driver-1"), Availability.AVAILABLE))

        val response = controller.getDriver("driver-1")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("driver-1", body.id)
        assertEquals("AVAILABLE", body.availability)
    }

    @Test
    fun `an unknown driver id returns 404`() {
        val response = controller.getDriver("unknown")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `a blank driver id returns 400`() {
        val response = controller.getDriver("")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing drivers returns every saved driver`() {
        repository.save(Driver(DriverId("list-1"), Availability.AVAILABLE))
        repository.save(Driver(DriverId("list-2"), Availability.UNAVAILABLE))

        val response = controller.listDrivers()

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.id == "list-1" && it.availability == "AVAILABLE" })
        assertTrue(body.any { it.id == "list-2" && it.availability == "UNAVAILABLE" })
    }

    @Test
    fun `listing drivers when none exist returns an empty list`() {
        val response = controller.listDrivers()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `declaring availability for a known driver returns 200 with the new availability`() {
        repository.save(Driver(DriverId("driver-2"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-2", DeclareAvailabilityRequest("AVAILABLE"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("driver-2", body.id)
        assertEquals("AVAILABLE", body.availability)
        assertEquals(Availability.AVAILABLE, repository.findById(DriverId("driver-2"))?.availability)
    }

    @Test
    fun `declaring availability for an unknown driver returns 404`() {
        val response = controller.declareAvailability("unknown", DeclareAvailabilityRequest("AVAILABLE"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `declaring an unrecognized availability value returns 400`() {
        repository.save(Driver(DriverId("driver-3"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-3", DeclareAvailabilityRequest("BUSY"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
