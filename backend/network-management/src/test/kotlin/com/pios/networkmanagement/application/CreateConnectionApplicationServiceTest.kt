package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The mandatory Sprint 7A scenario: Artur → Regina.
 */
class CreateConnectionApplicationServiceTest {
    private val personRepository = InMemoryPersonRepository()
    private val connectionRepository = InMemoryConnectionRepository()
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val createConnectionService = CreateConnectionApplicationService(personRepository, connectionRepository)
    private val connectionsHandler = RetrievePersonConnectionsHandler(connectionRepository)

    @Test
    fun `Artur connects to Regina, and the connection is retrievable from Artur's own side`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        val connection = createConnectionService.handle(
            CreateConnectionCommand(artur.id, regina.id, ConnectionType.CONNECTED)
        )

        assertEquals(artur.id, connection.fromPersonId)
        assertEquals(regina.id, connection.toPersonId)
        assertEquals(ConnectionType.CONNECTED, connection.type)

        val arturConnections = connectionsHandler.handle(artur.id)
        assertTrue(arturConnections.any { it.toPersonId == regina.id })
    }

    @Test
    fun `creating a connection from an unknown person fails`() {
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        assertFailsWith<PersonNotFoundException> {
            createConnectionService.handle(
                CreateConnectionCommand(PersonId("unknown-person"), regina.id, ConnectionType.CONNECTED)
            )
        }
    }

    @Test
    fun `creating a connection to an unknown person fails`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))

        assertFailsWith<PersonNotFoundException> {
            createConnectionService.handle(
                CreateConnectionCommand(artur.id, PersonId("unknown-person"), ConnectionType.CONNECTED)
            )
        }
    }
}
