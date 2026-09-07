package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Update Vehicle command (PIOS
 * Group and Long-Distance Rides Roadmap, Stage 1). This service sequences
 * the command into the [Driver] aggregate's own behavior; it does not
 * decide a driver's vehicle details itself (APPLICATION_ARCHITECTURE.md
 * Section 2, "Domain Decides Business Meaning") -- mirroring
 * [DriverAvailabilityApplicationService] exactly, minus the outbox/event
 * step: no event is produced for a vehicle update (see
 * [Driver.updateVehicle]'s own KDoc for why), so there is nothing to
 * publish and no [TransactionRunner] boundary needed beyond the single
 * repository write.
 */
@Service
class UpdateVehicleApplicationService(
    private val driverRepository: DriverRepository
) {

    /**
     * Coordinates an [UpdateVehicleCommand] against the given [driver].
     * [driver] must be the one referenced by [command] -- the caller is
     * responsible for finding it, mirroring
     * [DriverAvailabilityApplicationService.handle]'s own instance-supplied
     * overload.
     */
    fun handle(driver: Driver, command: UpdateVehicleCommand): Driver {
        require(driver.id == command.driverId) {
            "Command targets driver ${command.driverId.value} but was handled against driver ${driver.id.value}"
        }
        driver.updateVehicle(command.make, command.model, command.color, command.plateNumber, command.seatCount)
        driverRepository.save(driver)
        return driver
    }

    /**
     * Coordinates an [UpdateVehicleCommand] by first restoring the targeted
     * Driver through [driverRepository]. Throws [DriverNotFoundException]
     * if no Driver identified by [UpdateVehicleCommand.driverId] has been
     * saved.
     */
    fun handle(command: UpdateVehicleCommand): Driver {
        val driver = driverRepository.findById(command.driverId) ?: throw DriverNotFoundException(command.driverId)
        return handle(driver, command)
    }
}
