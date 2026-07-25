package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.ConnectionRepository
import com.pios.networkmanagement.domain.Connection
import com.pios.networkmanagement.domain.PersonId
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [ConnectionRepository] — a test double
 * only, mirroring [InMemoryPersonRepository]'s own role.
 */
class InMemoryConnectionRepository : ConnectionRepository {
    private val store = ConcurrentHashMap<String, Connection>()

    override fun save(connection: Connection) {
        store[connection.id.value] = connection
    }

    override fun findByFromPerson(personId: PersonId): List<Connection> =
        store.values.filter { it.fromPersonId == personId }
}
