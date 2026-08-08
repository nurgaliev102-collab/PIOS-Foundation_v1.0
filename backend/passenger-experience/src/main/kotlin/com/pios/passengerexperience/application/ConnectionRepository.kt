package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference

/**
 * The persistence boundary for the Connection concept (Sprint 7B:
 * Personal Network Flow MVP), following the same Domain-Owned Persistence
 * discipline as every other module's own repository interface
 * (PERSISTENCE_ARCHITECTURE.md Section 2).
 *
 * [findByPassenger], [findById] and [deleteById] were added by ADR-054
 * (Circle of Trust) to serve the passenger-side read/write surface --
 * the existing three operations keep their original signatures unchanged,
 * per that ADR's own additive-only requirement.
 */
interface ConnectionRepository {
    fun save(connection: Connection)
    fun findByDriverAndPassenger(driverId: DriverReference, passengerReference: PassengerReference): Connection?
    fun findByDriver(driverId: DriverReference): List<Connection>
    fun findByPassenger(passengerReference: PassengerReference): List<Connection>
    fun findById(id: ConnectionId): Connection?
    fun deleteById(id: ConnectionId)
}
