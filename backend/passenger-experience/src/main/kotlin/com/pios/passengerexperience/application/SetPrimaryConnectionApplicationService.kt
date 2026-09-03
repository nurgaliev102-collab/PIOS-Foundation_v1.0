package com.pios.passengerexperience.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.PrimaryConnectionDesignated
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

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
 *
 * ## Event publication (ADR-062; Task 14, First Refusal Foundation)
 *
 * [handle] now also publishes [PrimaryConnectionDesignated] through
 * [outboxRepository], inside the same [transactionRunner] boundary as the
 * `primary_connections` upsert itself -- so the designation and the
 * outbox record either both commit or both roll back, closing the same
 * dual-write gap already closed for every other event-publishing
 * application service in this codebase (ADR-032). This is the first
 * event this module has ever published -- ADR-054 Part 1 deliberately
 * built none at the time ("no new event, no outbox record"); ADR-062 now
 * authorizes exactly this narrow addition. [outboxRepository] defaults to
 * [NoOpOutboxRepository] for the same reason every other application
 * service in this codebase does -- existing tests exercising only the
 * designation logic continue to work unmodified; a real, Spring-wired
 * instance always receives the real
 * [com.pios.passengerexperience.persistence.PostgreSQLOutboxRepository]
 * bean instead.
 */
@Service
class SetPrimaryConnectionApplicationService(
    private val connectionRepository: ConnectionRepository,
    private val primaryConnectionRepository: PrimaryConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    fun handle(connectionId: ConnectionId): Connection = transactionRunner.run {
        val connection = connectionRepository.findById(connectionId)
            ?: throw ConnectionNotFoundException(connectionId)
        val designatedAt = Instant.now()
        primaryConnectionRepository.setPrimary(connection.passengerReference, connectionId, designatedAt)
        val event = PrimaryConnectionDesignated(
            passengerReference = connection.passengerReference,
            driverId = connection.driverId,
            occurredAt = designatedAt
        )
        outboxRepository.save(outboxRecordFor(connection.passengerReference.passengerId, event))
        connection
    }

    /**
     * Builds the outbox record for [event] -- the minimum, ADR-062-
     * justified payload (`passengerReference`, `driverId`), nothing else.
     * Mirrors Dispatch's own `outboxRecordFor`/`envelopeFor` shape exactly
     * (stable `eventId`, explicit `eventVersion`, the domain's own
     * business `occurredAt`).
     */
    private fun outboxRecordFor(aggregateId: String, event: PrimaryConnectionDesignated): OutboxRecord = OutboxRecord(
        aggregateId = aggregateId,
        eventType = "PrimaryConnectionDesignated",
        routingKey = "primary.connection.designated",
        payload = envelopeFor(
            eventType = "PrimaryConnectionDesignated",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf(
                "passengerReference" to event.passengerReference.passengerId,
                "driverId" to event.driverId.driverId
            )
        )
    )

    private fun envelopeFor(eventType: String, occurredAt: String, payload: Map<String, String>): String =
        objectMapper.writeValueAsString(
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to eventType,
                "eventVersion" to 1,
                "occurredAt" to occurredAt,
                "payload" to payload
            )
        )
}
