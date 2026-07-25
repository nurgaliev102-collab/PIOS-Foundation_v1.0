package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.PersonProfileRepository
import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.domain.PersonProfile
import com.pios.networkmanagement.domain.PersonProfileId
import com.pios.networkmanagement.domain.ProfileType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [PersonProfileRepository] (Sprint 7A:
 * PIOS Network Foundation), mirroring [PostgreSQLPersonRepository]'s own
 * style.
 */
@Repository
class PostgreSQLPersonProfileRepository(
    private val jdbcTemplate: JdbcTemplate
) : PersonProfileRepository {

    override fun save(profile: PersonProfile) {
        jdbcTemplate.update(
            "INSERT INTO person_profiles (id, person_id, type, created_at) VALUES (?, ?, ?, ?)",
            profile.id.value,
            profile.personId.value,
            profile.type.name,
            java.sql.Timestamp.from(profile.createdAt)
        )
    }

    override fun findByPersonId(personId: PersonId): List<PersonProfile> =
        jdbcTemplate.query(
            "SELECT id, person_id, type, created_at FROM person_profiles WHERE person_id = ?",
            { rs, _ ->
                PersonProfile(
                    id = PersonProfileId(rs.getString("id")),
                    personId = PersonId(rs.getString("person_id")),
                    type = ProfileType.valueOf(rs.getString("type")),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            personId.value
        )
}
