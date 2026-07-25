package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.ConnectionRepository
import com.pios.networkmanagement.domain.Connection
import com.pios.networkmanagement.domain.ConnectionId
import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.domain.PersonId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [ConnectionRepository] (Sprint 7A:
 * PIOS Network Foundation), mirroring [PostgreSQLPersonRepository]'s own
 * style.
 */
@Repository
class PostgreSQLConnectionRepository(
    private val jdbcTemplate: JdbcTemplate
) : ConnectionRepository {

    override fun save(connection: Connection) {
        jdbcTemplate.update(
            "INSERT INTO connections (id, from_person_id, to_person_id, type, created_at) VALUES (?, ?, ?, ?, ?)",
            connection.id.value,
            connection.fromPersonId.value,
            connection.toPersonId.value,
            connection.type.name,
            java.sql.Timestamp.from(connection.createdAt)
        )
    }

    override fun findByFromPerson(personId: PersonId): List<Connection> =
        jdbcTemplate.query(
            "SELECT id, from_person_id, to_person_id, type, created_at FROM connections WHERE from_person_id = ?",
            { rs, _ ->
                Connection(
                    id = ConnectionId(rs.getString("id")),
                    fromPersonId = PersonId(rs.getString("from_person_id")),
                    toPersonId = PersonId(rs.getString("to_person_id")),
                    type = ConnectionType.valueOf(rs.getString("type")),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            personId.value
        )
}
