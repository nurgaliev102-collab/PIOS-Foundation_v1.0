package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.ConnectionRepository
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [ConnectionRepository] -- a fast,
 * dependency-free test double, mirroring
 * `com.pios.drivermanagement.persistence.InMemoryDriverRepository`'s own
 * role. Not a production storage mechanism.
 *
 * [onConnectionDeleted] (ADR-054, Circle of Trust) replicates, in this
 * test double, what the real database's `primary_connections`
 * `ON DELETE CASCADE` foreign key does automatically
 * (`V2__create_primary_connections.sql`): when a connection is deleted,
 * any primary designation pointing at it must be cleared too. There is no
 * cross-repository call in production code -- the callback is supplied
 * only by test wiring (see [InMemoryPrimaryConnectionRepository.removeIfPointingAt]),
 * to keep this in-memory double behaviorally honest with the real
 * cascade, per this module's own precedent of documenting such fidelity
 * requirements (see `CreateConnectionApplicationService`'s own KDoc on
 * matching real constraint behavior).
 */
class InMemoryConnectionRepository(
    private val onConnectionDeleted: (ConnectionId) -> Unit = {}
) : ConnectionRepository {
    private val store = ConcurrentHashMap<String, Connection>()

    override fun save(connection: Connection) {
        store[connection.id.value] = connection
    }

    override fun findByDriverAndPassenger(driverId: DriverReference, passengerReference: PassengerReference): Connection? =
        store.values.firstOrNull { it.driverId == driverId && it.passengerReference == passengerReference }

    override fun findByDriver(driverId: DriverReference): List<Connection> =
        store.values.filter { it.driverId == driverId }

    override fun findByPassenger(passengerReference: PassengerReference): List<Connection> =
        store.values.filter { it.passengerReference == passengerReference }

    override fun findById(id: ConnectionId): Connection? = store[id.value]

    override fun deleteById(id: ConnectionId) {
        store.remove(id.value)
        onConnectionDeleted(id)
    }
}
