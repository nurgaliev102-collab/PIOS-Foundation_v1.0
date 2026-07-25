package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreateConnectionApplicationService
import com.pios.networkmanagement.application.CreateConnectionCommand
import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.RetrievePersonConnectionsHandler
import com.pios.networkmanagement.application.RetrievePersonHandler
import com.pios.networkmanagement.application.RetrievePersonProfilesHandler
import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryPersonProfileRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [PersonController] directly, with real, in-memory-backed
 * application services -- no Spring MVC context, mirroring
 * `com.pios.drivermanagement.api.DriverControllerTest`'s own convention.
 */
class PersonControllerTest {
    private val personRepository = InMemoryPersonRepository()
    private val connectionRepository = InMemoryConnectionRepository()
    private val profileRepository = InMemoryPersonProfileRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val createConnectionService = CreateConnectionApplicationService(personRepository, connectionRepository)
    private val controller = PersonController(
        createPersonService,
        RetrievePersonHandler(personRepository),
        RetrievePersonProfilesHandler(profileRepository),
        RetrievePersonConnectionsHandler(connectionRepository)
    )

    @Test
    fun `creating a person returns 201 with their id and name`() {
        val response = controller.createPerson(CreatePersonRequest("Артур", "+7900"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("Артур", body.name)
        assertEquals("+7900", body.phone)
    }

    @Test
    fun `creating a person with a blank name returns 400`() {
        val response = controller.createPerson(CreatePersonRequest(""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getting a known person returns 200`() {
        val created = createPersonService.handle(CreatePersonCommand("Регина", null))

        val response = controller.getPerson(created.id.value)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Регина", assertNotNull(response.body).name)
    }

    @Test
    fun `getting an unknown person returns 404`() {
        val response = controller.getPerson("unknown-person")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getting connections for Artur includes Regina after a connection is created`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))
        createConnectionService.handle(CreateConnectionCommand(artur.id, regina.id, ConnectionType.CONNECTED))

        val response = controller.getPersonConnections(artur.id.value)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.person == "Регина" && it.type == "CONNECTED" })
    }
}
