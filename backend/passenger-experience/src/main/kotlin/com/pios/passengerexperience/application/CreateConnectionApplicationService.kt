package com.pios.passengerexperience.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionEstablished
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
 *
 * ## Event publication (ADR-068, Relationship-Ordered Fallback Dispatch —
 * Trusted → Network → Open Marketplace, Part 1)
 *
 * [handle] now also publishes [ConnectionEstablished] through
 * [outboxRepository], inside the same [transactionRunner] boundary as the
 * `connections` insert itself -- so the row and the outbox record either
 * both commit or both roll back (ADR-032), mirroring
 * [SetPrimaryConnectionApplicationService]'s own identical discipline for
 * `PrimaryConnectionDesignated`. Fired only on the actual-creation branch
 * ([CreateConnectionOutcome.created] = `true`) -- the idempotent
 * "already exists" path publishes nothing, since Dispatch's own
 * `TrustedDriverRecord` projection would gain nothing from a repeat event
 * for a fact it already has. [outboxRepository] defaults to
 * [NoOpOutboxRepository] for the same reason every other event-publishing
 * application service in this codebase does -- existing tests exercising
 * only the creation logic continue to work unmodified.
 */
@Service
class CreateConnectionApplicationService(
    private val connectionRepository: ConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val objectMapper: ObjectMapper = ObjectMapper()
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
        val event = ConnectionEstablished(
            passengerReference = connection.passengerReference,
            driverId = connection.driverId
        )
        outboxRepository.save(outboxRecordFor(connection.passengerReference.passengerId, event))
        CreateConnectionOutcome(connection, created = true)
    }

    /**
     * Builds the outbox record for [event] -- the minimum, ADR-068-
     * justified payload (`passengerReference`, `driverId`), nothing else.
     * Mirrors [SetPrimaryConnectionApplicationService]'s own
     * `outboxRecordFor`/`envelopeFor` shape exactly (stable `eventId`,
     * explicit `eventVersion`, the domain's own business `occurredAt`).
     */
    private fun outboxRecordFor(aggregateId: String, event: ConnectionEstablished): OutboxRecord = OutboxRecord(
        aggregateId = aggregateId,
        eventType = "ConnectionEstablished",
        routingKey = "connection.established",
        payload = envelopeFor(
            eventType = "ConnectionEstablished",
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
