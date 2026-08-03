package com.pios.dispatch.persistence

import com.pios.dispatch.application.OutboxBacklog
import com.pios.dispatch.application.OutboxRecord
import com.pios.dispatch.application.OutboxRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [OutboxRepository] (ADR-032; Tranche
 * 1: Dispatch Event Publishing Completion). Reads and writes Dispatch's
 * own `dispatch_outbox` table (`db/migration/dispatch/V3__outbox.sql`),
 * in the same `pios_dispatch` database
 * [com.pios.dispatch.persistence.PostgreSQLAssignmentRepository] already
 * uses -- [save] participates in whatever transaction is already active
 * on the calling thread when invoked through the same [JdbcTemplate] /
 * [javax.sql.DataSource], which is exactly what lets
 * [com.pios.dispatch.application.DispatchAssignmentApplicationService]
 * save an [com.pios.dispatch.domain.Assignment] and an outbox record
 * together, atomically. Mirrors Order Management's and Driver
 * Management's own already-proven adapter exactly.
 *
 * Uses plain JDBC only -- no ORM, no entity annotations.
 */
@Repository
class PostgreSQLOutboxRepository(
    private val jdbcTemplate: JdbcTemplate
) : OutboxRepository {

    override fun save(record: OutboxRecord): OutboxRecord {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                val statement = connection.prepareStatement(
                    """
                    INSERT INTO dispatch_outbox (aggregate_id, event_type, routing_key, payload)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf("id")
                )
                statement.setString(1, record.aggregateId)
                statement.setString(2, record.eventType)
                statement.setString(3, record.routingKey)
                statement.setString(4, record.payload)
                statement
            },
            keyHolder
        )
        return record.copy(id = keyHolder.key!!.toLong())
    }

    override fun findUnpublished(): List<OutboxRecord> {
        return jdbcTemplate.query(
            """
            SELECT id, aggregate_id, event_type, routing_key, payload, created_at, published_at
            FROM dispatch_outbox
            WHERE published_at IS NULL
            ORDER BY id ASC
            """.trimIndent()
        ) { rs, _ ->
            OutboxRecord(
                id = rs.getLong("id"),
                aggregateId = rs.getString("aggregate_id"),
                eventType = rs.getString("event_type"),
                routingKey = rs.getString("routing_key"),
                payload = rs.getString("payload"),
                createdAt = rs.getTimestamp("created_at")?.toInstant(),
                publishedAt = rs.getTimestamp("published_at")?.toInstant()
            )
        }
    }

    override fun markPublished(id: Long) {
        jdbcTemplate.update(
            "UPDATE dispatch_outbox SET published_at = now() WHERE id = ?",
            id
        )
    }

    override fun countUnpublished(): OutboxBacklog =
        jdbcTemplate.query(
            """
            SELECT count(*) AS pending, min(created_at) AS oldest_pending_created_at
            FROM dispatch_outbox
            WHERE published_at IS NULL
            """.trimIndent()
        ) { rs, _ ->
            OutboxBacklog(
                pending = rs.getLong("pending"),
                oldestPendingCreatedAt = rs.getTimestamp("oldest_pending_created_at")?.toInstant()
            )
        }.first()
}
