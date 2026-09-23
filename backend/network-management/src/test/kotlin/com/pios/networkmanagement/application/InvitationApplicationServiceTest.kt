package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.domain.InvitationStatus
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryInvitationRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * The mandatory Sprint 7A scenario: Artur creates an Invitation, Regina
 * accepts it, and a Connection results.
 */
class InvitationApplicationServiceTest {
    private val personRepository = InMemoryPersonRepository()
    private val invitationRepository = InMemoryInvitationRepository()
    private val connectionRepository = InMemoryConnectionRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val createInvitationService = CreateInvitationApplicationService(personRepository, invitationRepository)
    private val acceptInvitationService =
        AcceptInvitationApplicationService(invitationRepository, personRepository, connectionRepository)

    @Test
    fun `Artur creates an invitation`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))

        val invitation = createInvitationService.handle(CreateInvitationCommand(artur.id))

        assertEquals(artur.id, invitation.creatorPersonId)
        assertEquals(InvitationStatus.CREATED, invitation.status)
        assertNotNull(invitationRepository.findByCode(invitation.code))
    }

    @Test
    fun `creating an invitation for an unknown person fails`() {
        assertFailsWith<PersonNotFoundException> {
            createInvitationService.handle(CreateInvitationCommand(com.pios.networkmanagement.domain.PersonId("unknown")))
        }
    }

    @Test
    fun `Regina accepting Artur's invitation creates a CONNECTED connection from Artur to Regina`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        val invitation = createInvitationService.handle(CreateInvitationCommand(artur.id))

        val connection = acceptInvitationService.handle(AcceptInvitationCommand(invitation.code, regina.id))

        assertEquals(artur.id, connection.fromPersonId)
        assertEquals(regina.id, connection.toPersonId)
        assertEquals(ConnectionType.CONNECTED, connection.type)
        assertEquals(InvitationStatus.USED, invitationRepository.findByCode(invitation.code)?.status)
    }

    @Test
    fun `accepting an unknown invitation code fails`() {
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))

        assertFailsWith<InvitationNotFoundException> {
            acceptInvitationService.handle(AcceptInvitationCommand("NOPE0000", regina.id))
        }
    }

    @Test
    fun `accepting an already-used invitation fails`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null, "test-" + java.util.UUID.randomUUID()))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        val invitation = createInvitationService.handle(CreateInvitationCommand(artur.id))
        acceptInvitationService.handle(AcceptInvitationCommand(invitation.code, regina.id))

        assertFailsWith<IllegalStateException> {
            acceptInvitationService.handle(AcceptInvitationCommand(invitation.code, regina.id))
        }
    }
}
