package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Retrieve Driver Availability
 * query (APPLICATION_ARCHITECTURE.md Section 7; API_SPECIFICATION.md
 * Section 7; owned by Driver Management per MODULE_STRUCTURE.md and
 * INTERFACE_CONTRACTS.md, "Driver Management"). This is a read-only
 * lookup: it changes nothing, publishes no event, and touches no
 * outbox, unlike [DriverAvailabilityApplicationService]'s command
 * handling.
 *
 * Throws [DriverNotFoundException] on a miss, the same application-layer
 * error [DriverAvailabilityApplicationService.handle] already throws for
 * the same reason (see that exception's own KDoc).
 *
 * [handleAll] added by Sprint FR-002 (Driver Availability) — the same
 * query, broadened to every driver, for the coordinator's own
 * list-of-drivers view. No filtering, sorting, or pagination: this
 * sprint's own scope is a plain list, nothing more.
 */
@Service
class RetrieveDriverAvailabilityHandler(
    private val driverRepository: DriverRepository
) {

    fun handle(driverId: DriverId): Driver =
        driverRepository.findById(driverId) ?: throw DriverNotFoundException(driverId)

    fun handleAll(): List<Driver> = driverRepository.findAll()
}
