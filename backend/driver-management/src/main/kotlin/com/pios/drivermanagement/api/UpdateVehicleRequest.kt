package com.pios.drivermanagement.api

/**
 * The REST request body for Update Vehicle (PIOS Group and Long-Distance
 * Rides Roadmap, Stage 1). Every field is optional and independently
 * absent-able -- a driver may fill in only what they know, or clear the
 * record entirely by sending every field blank/absent
 * ([com.pios.drivermanagement.domain.Driver.updateVehicle]'s own KDoc).
 * [seatCount] is validated by the domain, not here (a blank vs. an invalid
 * value are different failure shapes -- see [DriverController]'s own
 * mapping).
 */
data class UpdateVehicleRequest(
    val make: String? = null,
    val model: String? = null,
    val color: String? = null,
    val plateNumber: String? = null,
    val seatCount: Int? = null
)
