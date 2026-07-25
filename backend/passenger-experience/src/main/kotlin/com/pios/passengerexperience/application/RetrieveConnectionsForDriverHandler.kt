package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.DriverReference
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Retrieve Connections for Driver
 * (Sprint 7B: Personal Network Flow MVP) -- read-only, mirroring
 * `com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler`'s
 * own shape.
 */
@Service
class RetrieveConnectionsForDriverHandler(private val connectionRepository: ConnectionRepository) {
    fun handle(driverId: DriverReference): List<Connection> = connectionRepository.findByDriver(driverId)
}
