package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Invitation
import com.pios.networkmanagement.domain.InvitationId
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I, avoids visual ambiguity on a shared link
private const val CODE_LENGTH = 8

/**
 * Application-layer coordination for Create Invitation (Sprint 7A: PIOS
 * Network Foundation). Verifies the creator exists, then generates a
 * short, shareable [Invitation.code] via [SecureRandom]. The schema's own
 * `UNIQUE (code)` constraint (`V1__initial_schema.sql`) is the actual
 * guarantee against collision; at this sprint's pilot scale, a single
 * generation attempt is accepted as sufficient (no retry loop is built) —
 * the same "small, manually-coordinated pilot, not a general guarantee"
 * tradeoff `CreateDriverApplicationService`'s own KDoc already accepts for
 * a different race.
 */
@Service
class CreateInvitationApplicationService(
    private val personRepository: PersonRepository,
    private val invitationRepository: InvitationRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    private val random = SecureRandom()

    fun handle(command: CreateInvitationCommand): Invitation = transactionRunner.run {
        if (personRepository.findById(command.creatorPersonId) == null) {
            throw PersonNotFoundException(command.creatorPersonId)
        }
        val invitation = Invitation(
            id = InvitationId(UUID.randomUUID().toString()),
            creatorPersonId = command.creatorPersonId,
            code = generateCode(),
            createdAt = Instant.now()
        )
        invitationRepository.save(invitation)
        invitation
    }

    private fun generateCode(): String =
        (1..CODE_LENGTH)
            .map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
            .joinToString(separator = "")
}
