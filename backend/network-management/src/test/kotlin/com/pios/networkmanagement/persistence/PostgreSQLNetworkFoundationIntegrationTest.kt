package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.AcceptInvitationApplicationService
import com.pios.networkmanagement.application.AcceptInvitationCommand
import com.pios.networkmanagement.application.CreateConnectionApplicationService
import com.pios.networkmanagement.application.CreateConnectionCommand
import com.pios.networkmanagement.application.CreateInvitationApplicationService
import com.pios.networkmanagement.application.CreateInvitationCommand
import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.RetrievePersonConnectionsHandler
import com.pios.networkmanagement.domain.ConnectionType
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves Sprint 7A's own mandatory integration scenario (specification
 * Section 10) against a real PostgreSQL database, not merely in memory:
 * create Person "Артур", create Person "Регина", create a Connection
 * Артур → Регина, then `GET /persons/{arturId}/connections`-equivalent
 * returns Регина. Mirrors
 * `com.pios.drivermanagement.persistence.PostgreSQLCreateDriverTest`'s own
 * pattern.
 */
class PostgreSQLNetworkFoundationIntegrationTest {

    private val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
    private val personRepository = PostgreSQLPersonRepository(jdbcTemplate)
    private val connectionRepository = PostgreSQLConnectionRepository(jdbcTemplate)
    private val invitationRepository = PostgreSQLInvitationRepository(jdbcTemplate)
    private val createPersonService = CreatePersonApplicationService(personRepository)
    private val createConnectionService = CreateConnectionApplicationService(personRepository, connectionRepository)
    private val createInvitationService = CreateInvitationApplicationService(personRepository, invitationRepository)
    private val acceptInvitationService =
        AcceptInvitationApplicationService(invitationRepository, personRepository, connectionRepository)
    private val connectionsHandler = RetrievePersonConnectionsHandler(connectionRepository)

    @Test
    fun `Artur connects to Regina through PostgreSQL, and Artur's own connections include Regina`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        createConnectionService.handle(CreateConnectionCommand(artur.id, regina.id, ConnectionType.CONNECTED))

        val arturConnections = connectionsHandler.handle(artur.id)
        assertTrue(arturConnections.any { it.toPersonId == regina.id && it.type == ConnectionType.CONNECTED })
    }

    @Test
    fun `Artur invites Regina through PostgreSQL, and accepting produces the same connection`() {
        val artur = createPersonService.handle(CreatePersonCommand("Артур", null))
        val regina = createPersonService.handle(CreatePersonCommand("Регина", null))

        val invitation = createInvitationService.handle(CreateInvitationCommand(artur.id))
        val connection = acceptInvitationService.handle(AcceptInvitationCommand(invitation.code, regina.id))

        assertEquals(artur.id, connection.fromPersonId)
        assertEquals(regina.id, connection.toPersonId)
        assertEquals(ConnectionType.CONNECTED, connection.type)

        val arturConnections = connectionsHandler.handle(artur.id)
        assertTrue(arturConnections.any { it.id == connection.id })
    }
}
