package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.PrimaryConnectionEventListener`)
 * translates a received `PrimaryConnectionDesignated` message into,
 * before calling [PrimaryDriverProjectionApplicationService]. Carries
 * only [eventId] (for idempotent processing), [passengerReference], and
 * [driverId] -- exactly what the Passenger Experience -> Dispatch
 * contract (ADR-062) justifies, nothing from Passenger Experience's own
 * domain types. Mirrors [DriverAvailabilityUpdateCommand]'s own exact
 * shape.
 */
data class PrimaryDriverDesignatedCommand(
    val eventId: String,
    val passengerReference: String,
    val driverId: String
)
