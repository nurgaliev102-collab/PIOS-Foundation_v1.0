package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.InvitationRepository
import com.pios.networkmanagement.domain.Invitation
import com.pios.networkmanagement.domain.InvitationId
import com.pios.networkmanagement.domain.InvitationStatus
import com.pios.networkmanagement.domain.PersonId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [InvitationRepository] (Sprint 7A:
 * PIOS Network Foundation), mirroring [PostgreSQLPersonRepository]'s own
 * style. `status` is mutable ([Invitation.use]), so [save] is an upsert on
 * `code` (the invitation's own natural key) — the same "explicit
 * existence-check-before-save" caution
 * `CreateDriverApplicationService`/[com.pios.networkmanagement.application.CreateInvitationApplicationService]
 * already apply is not needed here, since [save] is called again
 * deliberately, by [com.pios.networkmanagement.application.AcceptInvitationApplicationService],
 * specifically to persist the `USED` transition.
 */
@Repository
class PostgreSQLInvitationRepository(
    private val jdbcTemplate: JdbcTemplate
) : InvitationRepository {

    override fun save(invitation: Invitation) {
        jdbcTemplate.update(
            """
            INSERT INTO invitations (id, creator_person_id, code, status, created_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET status = EXCLUDED.status
            """.trimIndent(),
            invitation.id.value,
            invitation.creatorPersonId.value,
            invitation.code,
            invitation.status.name,
            java.sql.Timestamp.from(invitation.createdAt)
        )
    }

    override fun findByCode(code: String): Invitation? {
        val rows = jdbcTemplate.query(
            "SELECT id, creator_person_id, code, status, created_at FROM invitations WHERE code = ?",
            { rs, _ ->
                Invitation(
                    id = InvitationId(rs.getString("id")),
                    creatorPersonId = PersonId(rs.getString("creator_person_id")),
                    code = rs.getString("code"),
                    status = InvitationStatus.valueOf(rs.getString("status")),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            code
        )
        return rows.firstOrNull()
    }
}
