package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.CreatePersonProfileApplicationService
import com.pios.networkmanagement.persistence.InMemoryPersonProfileRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProfileControllerTest {
    private val personRepository = InMemoryPersonRepository()
    private val profileRepository = InMemoryPersonProfileRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val controller = ProfileController(CreatePersonProfileApplicationService(personRepository, profileRepository))

    @Test
    fun `creating a profile for a known person returns 201`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))

        val response = controller.createProfile(CreateProfileRequest(artur.id.value, "DRIVER"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(artur.id.value, body.personId)
        assertEquals("DRIVER", body.type)
    }

    @Test
    fun `creating a profile for an unknown person returns 404`() {
        val response = controller.createProfile(CreateProfileRequest("unknown-person", "DRIVER"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `creating a profile with an unrecognized type returns 400`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))

        val response = controller.createProfile(CreateProfileRequest(artur.id.value, "NOT_A_TYPE"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
