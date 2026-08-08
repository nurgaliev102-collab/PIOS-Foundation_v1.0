package com.pios.identity.persistence

import com.pios.identity.application.CredentialRepository
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [CredentialRepository] (ADR-055
 * Decision 2), in this module's own database (`pios_identity`) — no other
 * module's information. Plain JDBC via [JdbcTemplate] only, no ORM,
 * mirroring [PostgreSQLIdentityRepository]'s own style.
 *
 * [save] performs a plain `INSERT`, with no `ON CONFLICT` clause: a
 * credential is written once, at registration, and password reset is
 * explicitly out of scope for this ADR — a second `save` for the same
 * `identity_id` is not an update path this class silently allows, it is
 * `identity_credentials`'s own primary key rejecting an unexpected write.
 */
@Repository
class PostgreSQLCredentialRepository(
    private val jdbcTemplate: JdbcTemplate
) : CredentialRepository {

    override fun save(credential: PasswordCredential) {
        jdbcTemplate.update(
            """
            INSERT INTO identity_credentials (identity_id, password_hash, password_salt, iterations, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            credential.identityId.value,
            credential.passwordHash,
            credential.passwordSalt,
            credential.iterations,
            java.sql.Timestamp.from(credential.createdAt)
        )
    }

    override fun findByIdentityId(identityId: IdentityId): PasswordCredential? {
        val rows = jdbcTemplate.query(
            "SELECT identity_id, password_hash, password_salt, iterations, created_at FROM identity_credentials WHERE identity_id = ?",
            { rs, _ ->
                PasswordCredential(
                    identityId = IdentityId(rs.getString("identity_id")),
                    passwordHash = rs.getString("password_hash"),
                    passwordSalt = rs.getString("password_salt"),
                    iterations = rs.getInt("iterations"),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            identityId.value
        )
        return rows.firstOrNull()
    }
}
