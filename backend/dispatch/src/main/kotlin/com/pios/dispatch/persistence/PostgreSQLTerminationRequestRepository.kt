package com.pios.dispatch.persistence

import com.pios.dispatch.application.TerminationOutcome
import com.pios.dispatch.application.TerminationRequestRecord
import com.pios.dispatch.application.TerminationRequestRepository
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PostgreSQLTerminationRequestRepository(private val jdbc: JdbcTemplate) : TerminationRequestRepository {
    override fun findById(requestId: String): TerminationRequestRecord? = jdbc.query(
        """SELECT request_id, order_reference, assignment_id, initiator, reason_code, note, outcome
           FROM dispatch_termination_requests WHERE request_id = ?""",
        { rs, _ ->
            TerminationRequestRecord(
                rs.getString("request_id"),
                rs.getString("order_reference"),
                rs.getString("assignment_id"),
                TerminationInitiator.valueOf(rs.getString("initiator")),
                rs.getString("reason_code")?.let(TerminationReasonCode::valueOf),
                rs.getString("note"),
                TerminationOutcome.valueOf(rs.getString("outcome"))
            )
        },
        requestId
    ).firstOrNull()

    override fun save(record: TerminationRequestRecord) {
        jdbc.update(
            """INSERT INTO dispatch_termination_requests
               (request_id, order_reference, assignment_id, initiator, reason_code, note, outcome)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            record.requestId,
            record.orderId,
            record.assignmentId,
            record.initiator.name,
            record.reasonCode?.name,
            record.note,
            record.outcome.name
        )
    }
}
