package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.PersonRepository
import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [PersonRepository] (Sprint 7A: PIOS
 * Network Foundation; ADR-037), in this module's own database
 * (`pios_network_management`) — no other module's information. Plain JDBC
 * via [JdbcTemplate] only, no ORM, mirroring
 * `com.pios.drivermanagement.persistence.PostgreSQLDriverRepository`'s own
 * style. [Person]'s constructor is public, so reconstruction from a row
 * uses it directly — no reflection needed.
 */
@Repository
class PostgreSQLPersonRepository(
    private val jdbcTemplate: JdbcTemplate
) : PersonRepository {

    override fun save(person: Person) {
        val changed = jdbcTemplate.update(
            """
            INSERT INTO persons (id, name, phone, created_at, identity_id, identity_bound_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, phone = EXCLUDED.phone
            WHERE persons.identity_id IS NOT DISTINCT FROM EXCLUDED.identity_id
              AND persons.identity_bound_at IS NOT DISTINCT FROM EXCLUDED.identity_bound_at
            """.trimIndent(),
            person.id.value,
            person.name,
            person.phone,
            java.sql.Timestamp.from(person.createdAt),
            person.identityId,
            person.identityBoundAt?.let(java.sql.Timestamp::from)
        )
        check(changed == 1) { "Person identity binding is immutable" }
    }

    override fun insertBoundIfAbsent(person: Person): Boolean {
        requireNotNull(person.identityId)
        return jdbcTemplate.update(
            """
            INSERT INTO persons (id, name, phone, created_at, identity_id, identity_bound_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (identity_id) WHERE identity_id IS NOT NULL DO NOTHING
            """.trimIndent(),
            person.id.value, person.name, person.phone,
            java.sql.Timestamp.from(person.createdAt), person.identityId,
            java.sql.Timestamp.from(requireNotNull(person.identityBoundAt))
        ) == 1
    }

    override fun findById(id: PersonId): Person? {
        val rows = jdbcTemplate.query(
            "SELECT id, name, phone, created_at, identity_id, identity_bound_at FROM persons WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByIdentityId(identityId: String): Person? = jdbcTemplate.query(
        "SELECT id, name, phone, created_at, identity_id, identity_bound_at FROM persons WHERE identity_id = ?",
        { rs, _ -> mapRow(rs) }, identityId
    ).firstOrNull()

    private fun mapRow(rs: java.sql.ResultSet): Person = Person(
        id = PersonId(rs.getString("id")),
        name = rs.getString("name"),
        phone = rs.getString("phone"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        identityId = rs.getString("identity_id"),
        identityBoundAt = rs.getTimestamp("identity_bound_at")?.toInstant()
    )
}
