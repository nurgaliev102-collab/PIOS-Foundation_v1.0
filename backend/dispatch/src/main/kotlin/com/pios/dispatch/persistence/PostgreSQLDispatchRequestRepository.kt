package com.pios.dispatch.persistence

import com.pios.dispatch.application.DispatchRequestRecord
import com.pios.dispatch.application.DispatchRequestRepository
import com.pios.dispatch.application.DispatchRequestState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class PostgreSQLDispatchRequestRepository(private val jdbcTemplate: JdbcTemplate) : DispatchRequestRepository {
    override fun insertIfAbsent(record: DispatchRequestRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO dispatch_requests (
                order_id, passenger_reference, is_test, explicit_driver_intent,
                requested_driver_id, requested_pickup_at, state, submitted_at,
                expires_at, next_attempt_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
            ON CONFLICT (order_id) DO NOTHING
            """.trimIndent(),
            record.orderId, record.passengerReference, record.isTest, record.explicitDriverIntent,
            record.requestedDriverId, record.requestedPickupAt?.let(Timestamp::from),
            Timestamp.from(record.submittedAt), Timestamp.from(record.expiresAt), Timestamp.from(record.nextAttemptAt)
        )
    }

    override fun findForUpdate(orderId: String): DispatchRequestRecord? = jdbcTemplate.query(
        """
        SELECT order_id, passenger_reference, is_test, explicit_driver_intent,
               requested_driver_id, requested_pickup_at, submitted_at, expires_at,
               next_attempt_at, state
        FROM dispatch_requests WHERE order_id = ? FOR UPDATE
        """.trimIndent(),
        { rs, _ ->
            val state: DispatchRequestState = DispatchRequestState.valueOf(rs.getString("state"))
            DispatchRequestRecord(
                orderId = rs.getString("order_id"),
                passengerReference = rs.getString("passenger_reference") ?: "",
                isTest = rs.getObject("is_test") as? Boolean ?: false,
                explicitDriverIntent = rs.getObject("explicit_driver_intent") as? Boolean ?: false,
                requestedDriverId = rs.getString("requested_driver_id"),
                requestedPickupAt = rs.getTimestamp("requested_pickup_at")?.toInstant(),
                submittedAt = rs.getTimestamp("submitted_at")?.toInstant() ?: Instant.EPOCH,
                expiresAt = rs.getTimestamp("expires_at")?.toInstant() ?: Instant.EPOCH,
                nextAttemptAt = rs.getTimestamp("next_attempt_at")?.toInstant() ?: Instant.EPOCH,
                state = state
            )
        },
        orderId
    ).firstOrNull()

    override fun findDueOrderIds(now: Instant, limit: Int): List<String> = jdbcTemplate.queryForList(
        """
        SELECT order_id FROM dispatch_requests
        WHERE state = 'PENDING' AND next_attempt_at <= ?
        ORDER BY next_attempt_at, order_id LIMIT ?
        """.trimIndent(),
        String::class.java,
        Timestamp.from(now), limit
    )

    override fun markOffered(orderId: String) = updateState(orderId, "OFFERED")
    override fun markUnfulfilled(orderId: String) = updateState(orderId, "UNFULFILLED")
    override fun scheduleRetry(orderId: String, nextAttemptAt: Instant) {
        jdbcTemplate.update(
            "UPDATE dispatch_requests SET next_attempt_at = ? WHERE order_id = ? AND state = 'PENDING'",
            Timestamp.from(nextAttemptAt), orderId
        )
    }

    override fun reopenIfOffered(orderId: String, nextAttemptAt: Instant) {
        jdbcTemplate.update(
            "UPDATE dispatch_requests SET state = 'PENDING', next_attempt_at = ? WHERE order_id = ? AND state = 'OFFERED'",
            Timestamp.from(nextAttemptAt), orderId
        )
    }

    override fun markCancelled(orderId: String) {
        jdbcTemplate.update(
            """
            INSERT INTO dispatch_requests (order_id, state)
            VALUES (?, 'CANCELLED')
            ON CONFLICT (order_id) DO UPDATE
            SET state = 'CANCELLED', next_attempt_at = NULL
            """.trimIndent(),
            orderId
        )
    }

    /**
     * D-11.B (Driver Calendar, `ADR-084` Part 2): the same `IN (...)`
     * batching technique [com.pios.dispatch.persistence.PostgreSQLAssignmentRepository.findByOrders]
     * already uses, applied here to read only [requested_pickup_at]
     * instead of a full row. `requested_pickup_at IS NOT NULL` in the
     * `WHERE` clause is what makes "absent from the map" the one signal
     * for "no known pickup time", per this method's own KDoc on
     * [DispatchRequestRepository].
     */
    override fun findPickupTimesForOrders(orderIds: List<String>): Map<String, Instant> {
        if (orderIds.isEmpty()) return emptyMap()
        val placeholders = orderIds.joinToString(", ") { "?" }
        return jdbcTemplate.query(
            """
            SELECT order_id, requested_pickup_at FROM dispatch_requests
            WHERE order_id IN ($placeholders) AND requested_pickup_at IS NOT NULL
            """.trimIndent(),
            { rs, _ -> rs.getString("order_id") to rs.getTimestamp("requested_pickup_at").toInstant() },
            *orderIds.toTypedArray()
        ).toMap()
    }

    private fun updateState(orderId: String, state: String) {
        jdbcTemplate.update(
            "UPDATE dispatch_requests SET state = ?, next_attempt_at = NULL WHERE order_id = ? AND state = 'PENDING'",
            state, orderId
        )
    }
}
