package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverAvailabilityChanged
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Declare Availability command
 * (APPLICATION_ARCHITECTURE.md Section 6). This service sequences the
 * command into the Driver aggregate's own behavior; it does not decide a
 * driver's availability state itself (APPLICATION_ARCHITECTURE.md
 * Section 2, "Domain Decides Business Meaning") and contains no
 * persistence, since that remains a separate, later concern
 * (PERSISTENCE_ARCHITECTURE.md), out of scope for this capability.
 */
@Service
class DriverAvailabilityApplicationService {

    /**
     * Coordinates a [DeclareAvailabilityCommand] against the given [driver],
     * returning the resulting [DriverAvailabilityChanged] event, or null if
     * the declaration did not change the driver's availability.
     */
    fun handle(driver: Driver, command: DeclareAvailabilityCommand): DriverAvailabilityChanged? {
        require(driver.id == command.driverId) {
            "Command targets driver ${command.driverId.value} but was handled against driver ${driver.id.value}"
        }
        return driver.declareAvailability(command.requestedAvailability)
    }
}
