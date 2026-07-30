package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Create Identity (ADR-038). No
 * verification of [CreateIdentityCommand.phone] happens here or anywhere
 * else in this module — creating an Identity records a claimed phone
 * number, it does not prove it. [IdentityId] is generated fresh on every
 * call, mirroring
 * `com.pios.networkmanagement.application.CreatePersonApplicationService`'s
 * own reasoning for why no existence check is needed first.
 */
@Service
class CreateIdentityApplicationService(
    private val identityRepository: IdentityRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: CreateIdentityCommand): Identity = transactionRunner.run {
        val identity = Identity(
            id = IdentityId(UUID.randomUUID().toString()),
            phone = command.phone?.let(::Phone),
            driverId = null,
            createdAt = Instant.now()
        )
        identityRepository.save(identity)
        identity
    }
}
