package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.ConnectionRepository
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [ConnectionRepository] -- a fast,
 * dependency-free test double, mirroring
 * `com.pios.drivermanagement.persistence.InMemoryDriverRepository`'s own
 * role. Not a production storage mechanism.
 */
class InMemoryConnectionRepository : ConnectionRepository {
    private val store = ConcurrentHashMap<String, Connection>()

    override fun save(connection: Connection) {
        store[connection.id.value] = connection
    }

    override fun findByDriverAndPassenger(driverId: DriverReference, passengerReference: PassengerReference): Connection? =
        store.values.firstOrNull { it.driverId == driverId && it.passengerReference == passengerReference }

    override fun findByDriver(driverId: DriverReference): List<Connection> =
        store.values.filter { it.driverId == driverId }
}
