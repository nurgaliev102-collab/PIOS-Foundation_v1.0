package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OutboxRecord
import com.pios.ordermanagement.application.OutboxRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [OutboxRepository] (ADR-032). Reads
 * and writes Order Management's own `order_management_outbox` table
 * (`db/migration/ordermanagement/V2__outbox.sql`), in the same
 * `pios_order_management` database [com.pios.ordermanagement.persistence.PostgreSQLOrderRepository]
 * already uses -- [save] participates in whatever transaction is already
 * active on the calling thread when invoked through the same [JdbcTemplate]
 * / [javax.sql.DataSource], which is exactly what lets
 * [com.pios.ordermanagement.application.OrderLifecycleApplicationService]
 * save an [com.pios.ordermanagement.domain.Order] and an outbox record
 * together, atomically.
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
                // PostgreSQL's JDBC driver returns every column of the
                // inserted row when asked via Statement.RETURN_GENERATED_KEYS,
                // not just the key -- which makes GeneratedKeyHolder.getKey()
                // fail (it requires exactly one key column). Naming the
                // generated column explicitly avoids that ambiguity.
                val statement = connection.prepareStatement(
                    """
                    INSERT INTO order_management_outbox (aggregate_id, event_type, routing_key, payload)
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
            FROM order_management_outbox
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
            "UPDATE order_management_outbox SET published_at = now() WHERE id = ?",
            id
        )
    }
}
