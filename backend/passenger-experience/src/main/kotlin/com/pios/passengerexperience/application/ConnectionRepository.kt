package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference

/**
 * The persistence boundary for the Connection concept (Sprint 7B:
 * Personal Network Flow MVP), following the same Domain-Owned Persistence
 * discipline as every other module's own repository interface
 * (PERSISTENCE_ARCHITECTURE.md Section 2).
 */
interface ConnectionRepository {
    fun save(connection: Connection)
    fun findByDriverAndPassenger(driverId: DriverReference, passengerReference: PassengerReference): Connection?
    fun findByDriver(driverId: DriverReference): List<Connection>
}
