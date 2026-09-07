package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * The Update Long-Distance Preference command (PIOS Group and
 * Long-Distance Rides Roadmap, Stage 3). Represents a driver's own
 * declaration of willingness to take long-distance trips; it does not
 * itself decide whether the declaration is valid -- that remains the
 * [com.pios.drivermanagement.domain.Driver] aggregate's own decision
 * (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides Business
 * Meaning"), mirroring [UpdateVehicleCommand] exactly.
 */
data class UpdateLongDistancePreferenceCommand(
    val driverId: DriverId,
    val accepts: Boolean
)
