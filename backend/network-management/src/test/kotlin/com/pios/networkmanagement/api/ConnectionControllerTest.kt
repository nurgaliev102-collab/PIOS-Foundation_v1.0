package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreateConnectionApplicationService
import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConnectionControllerTest {
    private val personRepository = InMemoryPersonRepository()
    private val connectionRepository = InMemoryConnectionRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val controller = ConnectionController(CreateConnectionApplicationService(personRepository, connectionRepository))

    @Test
    fun `creating a connection from Artur to Regina returns 201 with status CONNECTED`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        val response = controller.createConnection(
            CreateConnectionRequest(artur.id.value, regina.id.value, "CONNECTED")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("CONNECTED", assertNotNull(response.body).status)
    }

    @Test
    fun `creating a connection to an unknown person returns 404`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))

        val response = controller.createConnection(
            CreateConnectionRequest(artur.id.value, "unknown-person", "CONNECTED")
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `creating a connection with an unrecognized type returns 400`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        val response = controller.createConnection(
            CreateConnectionRequest(artur.id.value, regina.id.value, "NOT_A_TYPE")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
