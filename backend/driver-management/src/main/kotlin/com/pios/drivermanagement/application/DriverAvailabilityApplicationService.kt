package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverAvailabilityChanged
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Declare Availability command
 * (APPLICATION_ARCHITECTURE.md Section 6). This service sequences the
 * command into the Driver aggregate's own behavior; it does not decide a
 * driver's availability state itself (APPLICATION_ARCHITECTURE.md
 * Section 2, "Domain Decides Business Meaning"). Once the aggregate has
 * actually changed, the service persists it through [driverRepository]
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Driver Management") — the only
 * point in this module where persistence is invoked; the [Driver]
 * aggregate itself remains entirely unaware that a repository exists.
 */
@Service
class DriverAvailabilityApplicationService(
    private val driverRepository: DriverRepository
) {

    /**
     * Coordinates a [DeclareAvailabilityCommand] against the given [driver],
     * returning the resulting [DriverAvailabilityChanged] event, or null if
     * the declaration did not change the driver's availability. Persists
     * [driver] only when its availability actually changed — there is
     * nothing new to persist otherwise.
     */
    fun handle(driver: Driver, command: DeclareAvailabilityCommand): DriverAvailabilityChanged? {
        require(driver.id == command.driverId) {
            "Command targets driver ${command.driverId.value} but was handled against driver ${driver.id.value}"
        }
        val event = driver.declareAvailability(command.requestedAvailability)
        if (event != null) {
            driverRepository.save(driver)
        }
        return event
    }

    /**
     * Coordinates a [DeclareAvailabilityCommand] by first restoring the
     * targeted Driver through [driverRepository], proving the aggregate
     * can be saved, loaded back, and continue its own domain operation
     * exactly as it would if it had never left memory. Throws
     * [DriverNotFoundException] — an application-layer error, never a
     * persistence or domain one (see that class's own KDoc) — if no
     * Driver identified by [DeclareAvailabilityCommand.driverId] has been
     * saved.
     */
    fun handle(command: DeclareAvailabilityCommand): DriverAvailabilityChanged? {
        val driver = driverRepository.findById(command.driverId) ?: throw DriverNotFoundException(command.driverId)
        return handle(driver, command)
    }
}
