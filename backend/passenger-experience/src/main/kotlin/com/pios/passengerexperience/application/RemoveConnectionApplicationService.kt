package com.pios.passengerexperience.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.PrimaryConnectionCleared
import org.springframework.stereotype.Service
import java.util.UUID

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
 *
 * ## Event publication (ADR-062; Task 14, First Refusal Foundation)
 *
 * [handle] now publishes [PrimaryConnectionCleared] -- but **only** when
 * the connection being removed was, at the moment of removal, the
 * passenger's own primary. This requires looking the connection up
 * *before* deleting it (both to obtain its `passengerReference`, needed
 * for the event, and to compare its id against
 * [primaryConnectionRepository]'s own current designation) -- the delete
 * itself, and the cascade that follows it, is what actually clears the
 * designation; this lookup is read-only, purely to decide whether an
 * event is warranted, and changes no existing behavior when it was never
 * primary (no event, exactly as before this task). Removing an unknown
 * [connectionId] still succeeds silently with no event, preserving the
 * pre-existing idempotent-DELETE contract exactly.
 */
@Service
class RemoveConnectionApplicationService(
    private val connectionRepository: ConnectionRepository,
    private val primaryConnectionRepository: PrimaryConnectionRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    fun handle(connectionId: ConnectionId) = transactionRunner.run {
        val connection = connectionRepository.findById(connectionId)
        val wasPrimary = connection != null &&
            primaryConnectionRepository.findByPassenger(connection.passengerReference) == connectionId

        connectionRepository.deleteById(connectionId)

        if (wasPrimary && connection != null) {
            val event = PrimaryConnectionCleared(passengerReference = connection.passengerReference)
            outboxRepository.save(outboxRecordFor(connection.passengerReference.passengerId, event))
        }
    }

    private fun outboxRecordFor(aggregateId: String, event: PrimaryConnectionCleared): OutboxRecord = OutboxRecord(
        aggregateId = aggregateId,
        eventType = "PrimaryConnectionCleared",
        routingKey = "primary.connection.cleared",
        payload = envelopeFor(
            eventType = "PrimaryConnectionCleared",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("passengerReference" to event.passengerReference.passengerId)
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
