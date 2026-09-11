package com.pios.core.persistence

import com.pios.core.application.ProcessedEventRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [ProcessedEventRepository] (ADR-067
 * Idempotency; ADR-031). Persists exactly Core's own idempotency ledger
 * over consumed events, in Core's own database (`pios_core`) — no other
 * module's information, no shared schema. Plain JDBC via [JdbcTemplate]
 * only — no ORM. Mirrors dispatch's own
 * `PostgreSQLOrderCancelledRepository.markProcessed` exactly.
 *
 * [markProcessed] relies on `processed_events`'s primary key on
 * `event_id` and `ON CONFLICT ... DO NOTHING`: the insert affects exactly
 * one row the first time a given event id is seen, and zero rows on every
 * subsequent redelivery of the same id — the PostgreSQL-enforced
 * uniqueness the idempotency guarantee is actually built on, not merely
 * an application-level check.
 */
@Repository
class PostgreSQLProcessedEventRepository(
    private val jdbcTemplate: JdbcTemplate
) : ProcessedEventRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }
}
