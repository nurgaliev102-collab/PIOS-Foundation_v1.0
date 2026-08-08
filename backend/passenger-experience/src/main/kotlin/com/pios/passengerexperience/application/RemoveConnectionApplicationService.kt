package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.ConnectionId
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Remove Connection (ADR-054, Circle
 * of Trust). Idempotent by design, mirroring standard DELETE semantics:
 * removing an already-gone or never-existing [connectionId] succeeds
 * silently, with no exception -- [ConnectionRepository.deleteById] itself
 * never throws for a miss.
 *
 * A passenger may remove any connection, primary or not
 * (`PRODUCT_DECISION_CIRCLE_OF_TRUST.md`); if the removed connection was
 * the passenger's primary, the designation is cleared as a side effect of
 * the delete itself -- the real database's `ON DELETE CASCADE`
 * (`V2__create_primary_connections.sql`) does this automatically, with no
 * separate step here to forget.
 */
@Service
class RemoveConnectionApplicationService(
    private val connectionRepository: ConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(connectionId: ConnectionId) = transactionRunner.run {
        connectionRepository.deleteById(connectionId)
    }
}
