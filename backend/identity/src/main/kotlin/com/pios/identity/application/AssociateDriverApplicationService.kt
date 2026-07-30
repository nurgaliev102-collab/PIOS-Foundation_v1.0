package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Associate Driver (ADR-039) —
 * `identity`'s first real capability beyond Create/Retrieve. Rejects a
 * blank [AssociateDriverCommand.driverId] the same way
 * [com.pios.identity.domain.IdentityId]'s own constructor already rejects
 * a blank id, for the same reason: an empty reference is not a reference.
 */
@Service
class AssociateDriverApplicationService(
    private val identityRepository: IdentityRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: AssociateDriverCommand): Identity = transactionRunner.run {
        require(command.driverId.isNotBlank()) { "driverId must not be blank" }
        val identityId = IdentityId(command.identityId)
        val identity = identityRepository.findById(identityId) ?: throw IdentityNotFoundException(identityId)
        val updated = identity.withDriverId(command.driverId)
        identityRepository.save(updated)
        updated
    }
}
