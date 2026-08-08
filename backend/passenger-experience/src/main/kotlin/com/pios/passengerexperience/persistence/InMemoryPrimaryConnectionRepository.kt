package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.PrimaryConnectionRepository
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.PassengerReference
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [PrimaryConnectionRepository] -- a fast,
 * dependency-free test double, mirroring [InMemoryConnectionRepository]'s
 * own role. Not a production storage mechanism.
 */
class InMemoryPrimaryConnectionRepository : PrimaryConnectionRepository {
    private val store = ConcurrentHashMap<String, ConnectionId>()

    override fun findByPassenger(passengerReference: PassengerReference): ConnectionId? =
        store[passengerReference.passengerId]

    override fun setPrimary(passengerReference: PassengerReference, connectionId: ConnectionId, designatedAt: Instant) {
        store[passengerReference.passengerId] = connectionId
    }

    /**
     * Replicates the real `primary_connections` table's
     * `ON DELETE CASCADE` foreign key (`V2__create_primary_connections.sql`):
     * when [connectionId] is deleted from `connections`, any primary
     * designation pointing at it must be cleared too. Intended to be
     * wired as [InMemoryConnectionRepository]'s `onConnectionDeleted`
     * callback by test setup -- see that class's own KDoc.
     */
    fun removeIfPointingAt(connectionId: ConnectionId) {
        store.entries.removeIf { it.value == connectionId }
    }
}
