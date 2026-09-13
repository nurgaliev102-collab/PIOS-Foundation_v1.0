package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalMessageRepository
import com.pios.dispatch.domain.MessageSenderRole
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalMessage
import com.pios.dispatch.domain.ProposalMessageId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

/**
 * The PostgreSQL-backed adapter for [ProposalMessageRepository] (Minimal
 * In-Ride Messaging MVP). Persists exactly the ProposalMessage logical
 * entity this module owns, in its own database (`pios_dispatch`) — no
 * other module's information, no shared schema. Schema is version-
 * controlled and applied exclusively through Flyway migrations
 * (`db/migration/dispatch`), never by this class.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only — no ORM, no entity
 * annotations, no framework-specific mapping, mirroring
 * [PostgreSQLProposalRepository]'s own convention exactly.
 *
 * Unlike [PostgreSQLProposalRepository]/[PostgreSQLAssignmentRepository],
 * this class needs **no reflection**: [ProposalMessage]'s own constructor
 * is public (see that class's own KDoc for why reconstruction never
 * bypasses an invariant a private constructor would otherwise need to
 * protect) — a row read back is simply passed straight into it.
 */
@Repository
class PostgreSQLProposalMessageRepository(
    private val jdbcTemplate: JdbcTemplate
) : ProposalMessageRepository {

    override fun save(message: ProposalMessage) {
        jdbcTemplate.update(
            "INSERT INTO proposal_messages (id, proposal_id, sender_role, body, sent_at) VALUES (?, ?, ?, ?, ?)",
            message.id.value,
            message.proposalId.value,
            message.senderRole.name,
            message.body,
            Timestamp.from(message.sentAt)
        )
    }

    override fun findByProposal(proposalId: ProposalId): List<ProposalMessage> =
        jdbcTemplate.query(
            "SELECT id, proposal_id, sender_role, body, sent_at FROM proposal_messages WHERE proposal_id = ? ORDER BY sent_at ASC",
            { rs, _ ->
                ProposalMessage(
                    id = ProposalMessageId(rs.getString("id")),
                    proposalId = ProposalId(rs.getString("proposal_id")),
                    senderRole = MessageSenderRole.valueOf(rs.getString("sender_role")),
                    body = rs.getString("body"),
                    sentAt = rs.getTimestamp("sent_at").toInstant()
                )
            },
            proposalId.value
        )
}
