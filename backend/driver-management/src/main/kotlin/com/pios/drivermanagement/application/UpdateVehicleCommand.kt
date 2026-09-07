package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * The Update Vehicle command (PIOS Group and Long-Distance Rides Roadmap,
 * Stage 1). Represents a driver's own declaration of their vehicle's
 * details; it does not itself decide whether the declaration is valid --
 * that remains the [com.pios.drivermanagement.domain.Driver] aggregate's
 * own decision (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides
 * Business Meaning"), mirroring [DeclareAvailabilityCommand] exactly.
 */
data class UpdateVehicleCommand(
    val driverId: DriverId,
    val make: String?,
    val model: String?,
    val color: String?,
    val plateNumber: String?,
    val seatCount: Int?
)
