package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Retrieve Connections for Passenger
 * (ADR-054, Circle of Trust) -- read-only, mirroring
 * [RetrieveConnectionsForDriverHandler]'s own shape. Pairs each of the
 * passenger's connections with whether it is currently their primary.
 */
@Service
class RetrieveConnectionsForPassengerHandler(
    private val connectionRepository: ConnectionRepository,
    private val primaryConnectionRepository: PrimaryConnectionRepository
) {
    fun handle(passengerReference: PassengerReference): List<PassengerConnectionView> {
        val connections = connectionRepository.findByPassenger(passengerReference)
        val primaryConnectionId = primaryConnectionRepository.findByPassenger(passengerReference)
        return connections.map { connection ->
            PassengerConnectionView(connection, isPrimary = connection.id == primaryConnectionId)
        }
    }
}
