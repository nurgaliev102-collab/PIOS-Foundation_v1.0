package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class GuestIdentityOutcome(
    val identity: Identity,
    val token: String,
    val expiresAt: Instant
)

/** Creates a recoverable-on-this-device passenger identity without collecting credentials. */
@Service
class CreateGuestIdentityApplicationService(
    private val identityRepository: IdentityRepository,
    private val sessionTokenIssuer: SessionTokenIssuer,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(): GuestIdentityOutcome = transactionRunner.run {
        val identity = Identity(
            id = IdentityId(UUID.randomUUID().toString()),
            phone = null,
            driverId = null,
            createdAt = Instant.now()
        )
        identityRepository.save(identity)
        val issued = sessionTokenIssuer.issueGuest(identity.id.value, identity.sessionGeneration)
        GuestIdentityOutcome(identity, issued.token, issued.expiresAt)
    }
}
