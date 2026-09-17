package com.pios.identity.persistence

import com.pios.identity.application.IdentityRepository
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [IdentityRepository] (ADR-038), in this
 * module's own database (`pios_identity`) — no other module's information.
 * Plain JDBC via [JdbcTemplate] only, no ORM, mirroring
 * `com.pios.networkmanagement.persistence.PostgreSQLPersonRepository`'s own
 * style.
 */
@Repository
class PostgreSQLIdentityRepository(
    private val jdbcTemplate: JdbcTemplate
) : IdentityRepository {

    override fun save(identity: Identity) {
        jdbcTemplate.update(
            """
            INSERT INTO identities (id, phone, driver_id, created_at, phone_verified_at, session_generation)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                phone = EXCLUDED.phone,
                driver_id = EXCLUDED.driver_id,
                phone_verified_at = EXCLUDED.phone_verified_at,
                session_generation = EXCLUDED.session_generation
            """.trimIndent(),
            identity.id.value,
            identity.phone?.value,
            identity.driverId,
            java.sql.Timestamp.from(identity.createdAt),
            identity.phoneVerifiedAt?.let(java.sql.Timestamp::from),
            identity.sessionGeneration
        )
    }

    override fun findById(id: IdentityId): Identity? {
        val rows = jdbcTemplate.query(
            "SELECT id, phone, driver_id, created_at, phone_verified_at, session_generation FROM identities WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByPhone(phone: Phone): Identity? {
        val rows = jdbcTemplate.query(
            "SELECT id, phone, driver_id, created_at, phone_verified_at, session_generation FROM identities WHERE phone = ?",
            { rs, _ -> mapRow(rs) },
            phone.value
        )
        return rows.firstOrNull()
    }

    private fun mapRow(rs: java.sql.ResultSet): Identity = Identity(
        id = IdentityId(rs.getString("id")),
        phone = rs.getString("phone")?.let(::Phone),
        driverId = rs.getString("driver_id"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        phoneVerifiedAt = rs.getTimestamp("phone_verified_at")?.toInstant(),
        sessionGeneration = rs.getInt("session_generation")
    )
}
