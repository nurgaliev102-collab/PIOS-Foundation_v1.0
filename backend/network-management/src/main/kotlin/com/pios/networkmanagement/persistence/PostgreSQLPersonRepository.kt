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
        jdbcTemplate.update(
            """
            INSERT INTO persons (id, name, phone, created_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, phone = EXCLUDED.phone
            """.trimIndent(),
            person.id.value,
            person.name,
            person.phone,
            java.sql.Timestamp.from(person.createdAt)
        )
    }

    override fun findById(id: PersonId): Person? {
        val rows = jdbcTemplate.query(
            "SELECT id, name, phone, created_at FROM persons WHERE id = ?",
            { rs, _ ->
                Person(
                    id = PersonId(rs.getString("id")),
                    name = rs.getString("name"),
                    phone = rs.getString("phone"),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            id.value
        )
        return rows.firstOrNull()
    }
}
