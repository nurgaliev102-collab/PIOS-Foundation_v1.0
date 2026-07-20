package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.DriverAvailabilityChangedListener`)
 * translates a received DriverAvailabilityChanged message into, before
 * calling [DriverAvailabilityProjectionApplicationService]. Carries only
 * [eventId] (for idempotent processing), [driverReference], and
 * [available] -- exactly what the Driver Management -> Dispatch contract
 * (INTERFACE_CONTRACTS.md Section 5) justifies, nothing from Driver
 * Management's own domain types.
 */
data class DriverAvailabilityUpdateCommand(
    val eventId: String,
    val driverReference: String,
    val available: Boolean
)
