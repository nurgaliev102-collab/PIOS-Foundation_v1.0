package com.pios.core.persistence

import com.pios.core.application.ParticipantHistoryRepository
import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantHistoryEvent
import com.pios.core.domain.ParticipantReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * The PostgreSQL-backed adapter for [ParticipantHistoryRepository]
 * (ADR-067 Data Model Scope). Persists exactly Core's own Slice 01
 * read-model, in Core's own database (`pios_core`) — three tables
 * (`participants`, `participant_history_events`, plus the ledger owned by
 * [PostgreSQLProcessedEventRepository]), no other module's information, no
 * shared schema, no cross-database FK. Plain JDBC via [JdbcTemplate] only
 * — no ORM.
 *
 * Writes only to `pios_core` (ADR-067 Write Boundary). Reads only from
 * `pios_core` — [findParticipantByOrderReference] is a query against
 * Core's own `participant_history_events`, never a lookup into
 * order-management.
 */
@Repository
class PostgreSQLParticipantHistoryRepository(
    private val jdbcTemplate: JdbcTemplate
) : ParticipantHistoryRepository {

    override fun ensureParticipant(reference: ParticipantReference) {
        jdbcTemplate.update(
            "INSERT INTO participants (participant_reference) VALUES (?) ON CONFLICT (participant_reference) DO NOTHING",
            reference.value
        )
    }

    override fun record(event: ParticipantHistoryEvent) {
        jdbcTemplate.update(
            """
            INSERT INTO participant_history_events
                (participant_reference, kind, occurred_at, order_reference, driver_reference, source_event_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            event.participant.value,
            event.kind.name,
            Timestamp.from(event.occurredAt),
            event.orderReference,
            event.driverReference,
            event.sourceEventId
        )
    }

    override fun findParticipantByOrderReference(orderReference: String): ParticipantReference? {
        val references = jdbcTemplate.query(
            """
            SELECT participant_reference
            FROM participant_history_events
            WHERE order_reference = ? AND kind = ?
            ORDER BY id
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> rs.getString("participant_reference") },
            orderReference,
            HistoryEventKind.ORDER_SUBMITTED.name
        )
        return references.firstOrNull()?.let { ParticipantReference(it) }
    }

    override fun history(reference: ParticipantReference): List<ParticipantHistoryEvent> =
        jdbcTemplate.query(
            """
            SELECT participant_reference, kind, occurred_at, order_reference, driver_reference, source_event_id
            FROM participant_history_events
            WHERE participant_reference = ?
            ORDER BY occurred_at, id
            """.trimIndent(),
            ROW_MAPPER,
            reference.value
        )

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            ParticipantHistoryEvent(
                participant = ParticipantReference(rs.getString("participant_reference")),
                kind = HistoryEventKind.valueOf(rs.getString("kind")),
                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                orderReference = rs.getString("order_reference"),
                driverReference = rs.getString("driver_reference"),
                sourceEventId = rs.getString("source_event_id")
            )
        }
    }
}
