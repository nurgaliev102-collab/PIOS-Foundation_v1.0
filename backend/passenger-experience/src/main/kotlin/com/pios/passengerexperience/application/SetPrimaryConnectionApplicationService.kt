package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Application-layer coordination for Set Primary Connection (ADR-054,
 * Circle of Trust). Setting a primary is a **designation, never a
 * creation** (ADR-054 Part 4): this service only ever looks up an
 * existing [Connection] by [connectionId] and records it as the
 * passenger's primary -- it never constructs a new [Connection]. The
 * composite foreign key on `primary_connections`
 * (`V2__create_primary_connections.sql`) enforces, at the storage level,
 * that the designated connection belongs to that same passenger's own
 * circle.
 */
@Service
class SetPrimaryConnectionApplicationService(
    private val connectionRepository: ConnectionRepository,
    private val primaryConnectionRepository: PrimaryConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(connectionId: ConnectionId): Connection = transactionRunner.run {
        val connection = connectionRepository.findById(connectionId)
            ?: throw ConnectionNotFoundException(connectionId)
        primaryConnectionRepository.setPrimary(connection.passengerReference, connectionId, Instant.now())
        connection
    }
}
