package com.pios.core.persistence

import com.pios.core.application.ParticipantHistoryRepository
import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantHistoryEvent
import com.pios.core.domain.ParticipantReference
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An in-memory [ParticipantHistoryRepository] for constructor-based unit
 * tests. Same observable behaviour as
 * [PostgreSQLParticipantHistoryRepository]: lazy participant creation,
 * append-only history, `ORDER_SUBMITTED`-keyed order correlation,
 * chronological reads. Not a Spring bean.
 */
class InMemoryParticipantHistoryRepository : ParticipantHistoryRepository {

    private val participantFirstSeen = ConcurrentHashMap<String, Instant>()
    private val events = CopyOnWriteArrayList<Recorded>()

    private data class Recorded(val seq: Long, val event: ParticipantHistoryEvent)

    private var sequence = 0L

    @Synchronized
    override fun ensureParticipant(reference: ParticipantReference) {
        participantFirstSeen.putIfAbsent(reference.value, Instant.now())
    }

    @Synchronized
    override fun record(event: ParticipantHistoryEvent) {
        events.add(Recorded(sequence++, event))
    }

    override fun findParticipantByOrderReference(orderReference: String): ParticipantReference? =
        events
            .filter { it.event.orderReference == orderReference && it.event.kind == HistoryEventKind.ORDER_SUBMITTED }
            .minByOrNull { it.seq }
            ?.event
            ?.participant

    override fun history(reference: ParticipantReference): List<ParticipantHistoryEvent> =
        events
            .filter { it.event.participant == reference }
            .sortedWith(compareBy({ it.event.occurredAt }, { it.seq }))
            .map { it.event }

    /** Test helper — every recorded fact, in insertion order. */
    fun all(): List<ParticipantHistoryEvent> = events.sortedBy { it.seq }.map { it.event }

    /** Test helper — whether a `participants` row was created for [reference]. */
    fun participantExists(reference: ParticipantReference): Boolean =
        participantFirstSeen.containsKey(reference.value)
}
