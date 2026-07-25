package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Connection
import com.pios.networkmanagement.domain.ConnectionId
import com.pios.networkmanagement.domain.ConnectionType
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Accept Invitation (Sprint 7A: PIOS
 * Network Foundation) — `Invitation ↓ Connection`, per the founder's own
 * specification. Looks up the [com.pios.networkmanagement.domain.Invitation]
 * by [AcceptInvitationCommand.code], marks it used
 * ([com.pios.networkmanagement.domain.Invitation.use], throwing
 * [IllegalStateException] if it is not currently `CREATED`), verifies the
 * accepting person exists, then creates a [Connection] from the
 * invitation's own creator to the accepting person, always
 * [ConnectionType.CONNECTED] — accepting an invitation is itself the
 * connecting event, so there is no separate pending state to pass through.
 */
@Service
class AcceptInvitationApplicationService(
    private val invitationRepository: InvitationRepository,
    private val personRepository: PersonRepository,
    private val connectionRepository: ConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: AcceptInvitationCommand): Connection = transactionRunner.run {
        val invitation = invitationRepository.findByCode(command.code)
            ?: throw InvitationNotFoundException(command.code)
        if (personRepository.findById(command.acceptingPersonId) == null) {
            throw PersonNotFoundException(command.acceptingPersonId)
        }

        invitation.use()
        invitationRepository.save(invitation)

        val connection = Connection(
            id = ConnectionId(UUID.randomUUID().toString()),
            fromPersonId = invitation.creatorPersonId,
            toPersonId = command.acceptingPersonId,
            type = ConnectionType.CONNECTED,
            createdAt = Instant.now()
        )
        connectionRepository.save(connection)
        connection
    }
}
