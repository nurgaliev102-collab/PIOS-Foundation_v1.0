package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Connection
import com.pios.networkmanagement.domain.ConnectionId
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Create Connection (Sprint 7A: PIOS
 * Network Foundation) — the direct path (`POST /v1/connections`), distinct
 * from [AcceptInvitationApplicationService]'s own indirect,
 * invitation-mediated path. Verifies both referenced persons exist, same
 * reasoning as [CreatePersonProfileApplicationService]. No de-duplication
 * is performed: this sprint's own specification does not ask for it on
 * this path (unlike [Invitation.code]'s own uniqueness, which the schema
 * itself enforces) — inviting the same person more than once simply
 * records more than one Connection row, accepted as this sprint's own
 * scope, not a general guarantee.
 */
@Service
class CreateConnectionApplicationService(
    private val personRepository: PersonRepository,
    private val connectionRepository: ConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: CreateConnectionCommand): Connection = transactionRunner.run {
        if (personRepository.findById(command.fromPersonId) == null) {
            throw PersonNotFoundException(command.fromPersonId)
        }
        if (personRepository.findById(command.toPersonId) == null) {
            throw PersonNotFoundException(command.toPersonId)
        }
        val connection = Connection(
            id = ConnectionId(UUID.randomUUID().toString()),
            fromPersonId = command.fromPersonId,
            toPersonId = command.toPersonId,
            type = command.type,
            createdAt = Instant.now()
        )
        connectionRepository.save(connection)
        connection
    }
}
