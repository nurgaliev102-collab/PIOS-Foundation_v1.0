package com.pios.passengerexperience.api

/**
 * The passenger-side view of a [com.pios.passengerexperience.domain.Connection]
 * (ADR-054, Circle of Trust) -- carries [isPrimary] in addition to the
 * driver-facing [com.pios.passengerexperience.api.ConnectionResponse]'s
 * fields, since only the passenger-side surface may ever expose primacy
 * (ADR-054 Part 4: "the driver-facing response gains nothing"). [createdAt]
 * follows [ConnectionResponse.createdAt]'s own explicit `Instant.toString()`
 * (ISO-8601) convention.
 */
data class PassengerConnectionResponse(
    val connectionId: String,
    val driverId: String,
    val createdAt: String,
    val isPrimary: Boolean
)
