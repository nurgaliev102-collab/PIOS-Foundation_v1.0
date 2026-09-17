package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderCancellationRequestRecord
import com.pios.ordermanagement.application.OrderCancellationRequestRepository
import com.pios.ordermanagement.application.OrderTerminationRecord
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Repository
class PostgreSQLOrderCancellationRequestRepository(private val jdbc: JdbcTemplate) : OrderCancellationRequestRepository {
    override fun findById(requestId: String): OrderCancellationRequestRecord? = jdbc.query(
        "SELECT * FROM order_cancellation_requests WHERE request_id = ?",
        { rs, _ -> request(rs) }, requestId
    ).firstOrNull()

    override fun findPendingByOrder(orderId: String): OrderCancellationRequestRecord? = jdbc.query(
        "SELECT * FROM order_cancellation_requests WHERE order_id = ? AND outcome = 'PENDING'",
        { rs, _ -> request(rs) }, orderId
    ).firstOrNull()

    override fun findLatestByOrder(orderId: String): OrderCancellationRequestRecord? = jdbc.query(
        "SELECT * FROM order_cancellation_requests WHERE order_id = ? ORDER BY requested_at DESC LIMIT 1",
        { rs, _ -> request(rs) }, orderId
    ).firstOrNull()

    override fun save(record: OrderCancellationRequestRecord) {
        jdbc.update(
            """INSERT INTO order_cancellation_requests
               (request_id, order_id, reason_code, note, outcome, requested_at, resolved_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            record.requestId, record.orderId, record.reasonCode, record.note, record.outcome,
            Timestamp.from(record.requestedAt), record.resolvedAt?.let(Timestamp::from)
        )
    }

    override fun resolve(requestId: String, outcome: String, at: Instant) {
        jdbc.update(
            """UPDATE order_cancellation_requests SET outcome = ?, resolved_at = ?
               WHERE request_id = ? AND outcome = 'PENDING'""",
            outcome, Timestamp.from(at), requestId
        )
    }

    override fun findTermination(orderId: String): OrderTerminationRecord? = jdbc.query(
        "SELECT * FROM order_terminations WHERE order_id = ?",
        { rs, _ -> termination(rs) }, orderId
    ).firstOrNull()

    override fun saveTermination(record: OrderTerminationRecord) {
        jdbc.update(
            """INSERT INTO order_terminations
               (order_id, request_id, assignment_id, driver_id, initiator, reason_code, note, terminated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (order_id) DO NOTHING""",
            record.orderId, record.requestId, record.assignmentId, record.driverId,
            record.initiator, record.reasonCode, record.note, Timestamp.from(record.terminatedAt)
        )
    }

    private fun request(rs: ResultSet): OrderCancellationRequestRecord = OrderCancellationRequestRecord(
        rs.getString("request_id"), rs.getString("order_id"), rs.getString("reason_code"),
        rs.getString("note"), rs.getString("outcome"), rs.getTimestamp("requested_at").toInstant(),
        rs.getTimestamp("resolved_at")?.toInstant()
    )

    private fun termination(rs: ResultSet): OrderTerminationRecord = OrderTerminationRecord(
        rs.getString("order_id"), rs.getString("request_id"), rs.getString("assignment_id"),
        rs.getString("driver_id"), rs.getString("initiator"), rs.getString("reason_code"),
        rs.getString("note"), rs.getTimestamp("terminated_at").toInstant()
    )
}
