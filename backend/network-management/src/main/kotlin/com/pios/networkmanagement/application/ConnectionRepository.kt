package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Connection
import com.pios.networkmanagement.domain.PersonId

/**
 * The persistence boundary for the Connection aggregate (Sprint 7A: PIOS
 * Network Foundation). [findByFromPerson] backs "Get connections for a
 * person" (`GET /v1/persons/{id}/connections`) from that person's own,
 * inviter side — the only direction Sprint 7A's own scenario (Artur →
 * Regina) needs; a symmetric "connections where I am the invitee" query is
 * not added, since nothing in this sprint's own scope calls for it.
 */
interface ConnectionRepository {
    fun save(connection: Connection)
    fun findByFromPerson(personId: PersonId): List<Connection>
}
