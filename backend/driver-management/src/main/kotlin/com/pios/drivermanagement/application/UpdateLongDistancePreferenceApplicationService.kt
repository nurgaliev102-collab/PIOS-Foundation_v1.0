package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Update Long-Distance Preference
 * command (PIOS Group and Long-Distance Rides Roadmap, Stage 3). This
 * service sequences the command into the [Driver] aggregate's own
 * behavior; it does not decide a driver's preference itself
 * (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides Business
 * Meaning") -- mirroring [UpdateVehicleApplicationService] exactly.
 */
@Service
class UpdateLongDistancePreferenceApplicationService(
    private val driverRepository: DriverRepository
) {

    /**
     * Coordinates an [UpdateLongDistancePreferenceCommand] against the
     * given [driver]. [driver] must be the one referenced by [command] --
     * the caller is responsible for finding it, mirroring
     * [UpdateVehicleApplicationService.handle]'s own instance-supplied
     * overload.
     */
    fun handle(driver: Driver, command: UpdateLongDistancePreferenceCommand): Driver {
        require(driver.id == command.driverId) {
            "Command targets driver ${command.driverId.value} but was handled against driver ${driver.id.value}"
        }
        driver.updateLongDistancePreference(command.accepts)
        driverRepository.save(driver)
        return driver
    }

    /**
     * Coordinates an [UpdateLongDistancePreferenceCommand] by first
     * restoring the targeted Driver through [driverRepository]. Throws
     * [DriverNotFoundException] if no Driver identified by
     * [UpdateLongDistancePreferenceCommand.driverId] has been saved.
     */
    fun handle(command: UpdateLongDistancePreferenceCommand): Driver {
        val driver = driverRepository.findById(command.driverId) ?: throw DriverNotFoundException(command.driverId)
        return handle(driver, command)
    }
}
