package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.TrustedDriverEventListener`) translates
 * a received `ConnectionEstablished` message into, before calling
 * [TrustedDriverProjectionApplicationService]. Carries only [eventId] (for
 * idempotent processing), [passengerReference], and [driverId] -- exactly
 * what the Passenger Experience -> Dispatch contract (ADR-068 Part 1)
 * justifies, nothing from Passenger Experience's own domain types. Mirrors
 * [PrimaryDriverDesignatedCommand]'s own exact shape.
 */
data class TrustedDriverEstablishedCommand(
    val eventId: String,
    val passengerReference: String,
    val driverId: String
)
