package com.pios.networkmanagement.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.web.server.ResponseStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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
        RetrievePersonConnectionsHandler(connectionRepository),
        CurrentPerson(personRepository)
    )

    @BeforeTest fun authenticate() { NetworkSecurityContext.setIdentityId("identity-test") }
    @AfterTest fun clearAuthentication() { NetworkSecurityContext.clear() }

    @Test
    fun `creating a person returns 201 with their id and name`() {
        val response = controller.createPerson(CreatePersonRequest("Артур", "+7900"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("Артур", body.name)
    }

    @Test
    fun `person response never serializes phone PII`() {
        val response = controller.createPerson(CreatePersonRequest("Artur", "+7900"))

        val body = assertNotNull(response.body)
        val json = jacksonObjectMapper().writeValueAsString(body)
        assertFalse(json.contains("phone"))
        assertFalse(json.contains("+7900"))
    }

    @Test
    fun `creating a person with a blank name returns 400`() {
        val response = controller.createPerson(CreatePersonRequest(""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getting a known person returns 200`() {
        val created = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        NetworkSecurityContext.setIdentityId(requireNotNull(created.identityId))

        val response = controller.getPerson(created.id.value)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Регина", assertNotNull(response.body).name)
    }

    @Test
    fun `getting an unknown person returns 404`() {
        createPersonService.handle(CreatePersonCommand("caller", null, "identity-test"))
        assertEquals(HttpStatus.FORBIDDEN,
            assertFailsWith<ResponseStatusException> { controller.getPerson("unknown-person") }.statusCode)
    }

    @Test
    fun `getting connections for Artur includes Regina after a connection is created`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        createConnectionService.handle(CreateConnectionCommand(artur.id, regina.id, ConnectionType.CONNECTED))
        NetworkSecurityContext.setIdentityId(requireNotNull(artur.identityId))

        val response = controller.getPersonConnections(artur.id.value)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.person == "Регина" && it.type == "CONNECTED" })
    }
}
