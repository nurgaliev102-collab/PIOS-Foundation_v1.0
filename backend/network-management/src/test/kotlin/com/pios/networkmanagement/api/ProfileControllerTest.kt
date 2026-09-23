package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.CreatePersonProfileApplicationService
import com.pios.networkmanagement.persistence.InMemoryPersonProfileRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ProfileControllerTest {
    private val personRepository = InMemoryPersonRepository()
    private val profileRepository = InMemoryPersonProfileRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val controller = ProfileController(CreatePersonProfileApplicationService(personRepository, profileRepository), CurrentPerson(personRepository))

    @BeforeTest fun authenticate() { NetworkSecurityContext.setIdentityId("identity-test") }
    @AfterTest fun clearAuthentication() { NetworkSecurityContext.clear() }

    @Test
    fun `creating a profile for a known person returns 201`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        NetworkSecurityContext.setIdentityId(requireNotNull(artur.identityId))

        val response = controller.createProfile(CreateProfileRequest(artur.id.value, "DRIVER"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(artur.id.value, body.personId)
        assertEquals("DRIVER", body.type)
    }

    @Test
    fun `creating a profile for an unknown person returns 404`() {
        createPersonService.handle(CreatePersonCommand("caller", null, "identity-test"))
        assertEquals(HttpStatus.FORBIDDEN,
            assertFailsWith<ResponseStatusException> {
                controller.createProfile(CreateProfileRequest("unknown-person", "DRIVER"))
            }.statusCode)
    }

    @Test
    fun `creating a profile with an unrecognized type returns 400`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        NetworkSecurityContext.setIdentityId(requireNotNull(artur.identityId))

        val response = controller.createProfile(CreateProfileRequest(artur.id.value, "NOT_A_TYPE"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
