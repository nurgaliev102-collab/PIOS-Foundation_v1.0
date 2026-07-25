package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.AcceptInvitationApplicationService
import com.pios.networkmanagement.application.CreateInvitationApplicationService
import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryInvitationRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvitationControllerTest {
    private val personRepository = InMemoryPersonRepository()
    private val invitationRepository = InMemoryInvitationRepository()
    private val connectionRepository = InMemoryConnectionRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val controller = InvitationController(
        CreateInvitationApplicationService(personRepository, invitationRepository),
        AcceptInvitationApplicationService(invitationRepository, personRepository, connectionRepository)
    )

    @Test
    fun `Artur creates an invitation and receives a code and link`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))

        val response = controller.createInvitation(CreateInvitationRequest(artur.id.value))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.code.isNotBlank())
        assertTrue(body.link.endsWith(body.code))
    }

    @Test
    fun `creating an invitation for an unknown creator returns 404`() {
        val response = controller.createInvitation(CreateInvitationRequest("unknown-person"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `Regina accepting Artur's invitation returns 200 with a CONNECTED connection`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))
        val created = controller.createInvitation(CreateInvitationRequest(artur.id.value))
        val code = assertNotNull(created.body).code

        val response = controller.acceptInvitation(code, AcceptInvitationRequest(regina.id.value))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("CONNECTED", assertNotNull(response.body).status)
    }

    @Test
    fun `accepting an unknown invitation code returns 404`() {
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        val response = controller.acceptInvitation("NOPE0000", AcceptInvitationRequest(regina.id.value))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `accepting an already-used invitation returns 409`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))
        val created = controller.createInvitation(CreateInvitationRequest(artur.id.value))
        val code = assertNotNull(created.body).code
        controller.acceptInvitation(code, AcceptInvitationRequest(regina.id.value))

        val response = controller.acceptInvitation(code, AcceptInvitationRequest(regina.id.value))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
