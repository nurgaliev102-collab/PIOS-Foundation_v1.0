package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Create Connection (Sprint 7B:
 * Personal Network Flow MVP). Idempotent by design: opening the same
 * driver's invitation link more than once must not fail -- if a
 * Connection for this exact `(driverId, passengerReference)` pair already
 * exists, it is returned unchanged ([CreateConnectionOutcome.created] =
 * `false`) rather than duplicated or rejected. The schema's own
 * `UNIQUE (driver_id, passenger_reference)` constraint
 * (`V1__create_connections.sql`) is the actual guarantee against a race
 * between the existence check and the insert; this check-then-create
 * sequence mirrors `CreateDriverApplicationService`'s own "explicit
 * existence-check-before-save" caution, adapted to return the existing
 * record instead of throwing.
 */
@Service
class CreateConnectionApplicationService(
    private val connectionRepository: ConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: CreateConnectionCommand): CreateConnectionOutcome = transactionRunner.run {
        val existing = connectionRepository.findByDriverAndPassenger(command.driverId, command.passengerReference)
        if (existing != null) {
            return@run CreateConnectionOutcome(existing, created = false)
        }
        val connection = Connection(
            id = ConnectionId(UUID.randomUUID().toString()),
            driverId = command.driverId,
            passengerReference = command.passengerReference,
            createdAt = Instant.now()
        )
        connectionRepository.save(connection)
        CreateConnectionOutcome(connection, created = true)
    }
}
