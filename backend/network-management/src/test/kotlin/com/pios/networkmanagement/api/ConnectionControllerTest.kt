package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreateConnectionApplicationService
import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ConnectionControllerTest {
    private val personRepository = InMemoryPersonRepository()
    private val connectionRepository = InMemoryConnectionRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val controller = ConnectionController(CreateConnectionApplicationService(personRepository, connectionRepository), CurrentPerson(personRepository))

    @BeforeTest fun authenticate() { NetworkSecurityContext.setIdentityId("identity-test") }
    @AfterTest fun clearAuthentication() { NetworkSecurityContext.clear() }

    @Test
    fun `creating a connection from Artur to Regina returns 201 with status CONNECTED`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        NetworkSecurityContext.setIdentityId(requireNotNull(artur.identityId))

        val response = controller.createConnection(
            CreateConnectionRequest(artur.id.value, regina.id.value, "CONNECTED")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("CONNECTED", assertNotNull(response.body).status)
    }

    @Test
    fun `creating a connection to an unknown person returns 404`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        NetworkSecurityContext.setIdentityId(requireNotNull(artur.identityId))

        assertEquals(HttpStatus.NOT_FOUND,
            assertFailsWith<ResponseStatusException> {
                controller.createConnection(CreateConnectionRequest(artur.id.value, "unknown-person", "CONNECTED"))
            }.statusCode)
    }

    @Test
    fun `creating a connection with an unrecognized type returns 400`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        NetworkSecurityContext.setIdentityId(requireNotNull(artur.identityId))

        val response = controller.createConnection(
            CreateConnectionRequest(artur.id.value, regina.id.value, "NOT_A_TYPE")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
