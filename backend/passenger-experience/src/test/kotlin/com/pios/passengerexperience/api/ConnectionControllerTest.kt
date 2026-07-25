package com.pios.passengerexperience.api

import com.pios.passengerexperience.application.CreateConnectionApplicationService
import com.pios.passengerexperience.application.RetrieveConnectionsForDriverHandler
import com.pios.passengerexperience.persistence.InMemoryConnectionRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Constructs [ConnectionController] directly, with a real, in-memory
 * -backed application service -- no Spring MVC context, mirroring
 * `com.pios.drivermanagement.api.DriverControllerTest`'s own convention.
 */
class ConnectionControllerTest {
    private val repository = InMemoryConnectionRepository()
    private val controller = ConnectionController(
        CreateConnectionApplicationService(repository),
        RetrieveConnectionsForDriverHandler(repository)
    )

    @Test
    fun `creating a connection for the first time returns 201`() {
        val response = controller.createConnection(CreateConnectionRequest("artur", "regina"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("regina", assertNotNull(response.body).passengerReference)
    }

    @Test
    fun `opening the same link a second time returns 200, not 409`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"))

        val response = controller.createConnection(CreateConnectionRequest("artur", "regina"))

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `creating a connection with a blank driverId returns 400`() {
        val response = controller.createConnection(CreateConnectionRequest("", "regina"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getting connections for a driver returns every connected passenger`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"))

        val response = controller.getConnectionsForDriver("artur")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(listOf("regina"), body.map { it.passengerReference })
    }

    @Test
    fun `getting connections for a driver with none returns an empty list`() {
        val response = controller.getConnectionsForDriver("nobody")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }
}
