package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.DriverAvailabilityChangedListener`)
 * translates a received DriverAvailabilityChanged message into, before
 * calling [DriverAvailabilityProjectionApplicationService]. Carries only
 * [eventId] (for idempotent processing), [driverReference], [available],
 * and [isTest] -- exactly what the Driver Management -> Dispatch contract
 * (INTERFACE_CONTRACTS.md Section 5) justifies, nothing from Driver
 * Management's own domain types.
 *
 * [isTest] (ADR-069) defaults to `null`, matching
 * [DriverAvailabilityRecord.isTest]'s own "unknown, not false" meaning --
 * the transport boundary passes `null` whenever the source envelope's
 * `payload.isTest` is absent or not a boolean, never coercing that
 * absence into `false`.
 */
data class DriverAvailabilityUpdateCommand(
    val eventId: String,
    val driverReference: String,
    val available: Boolean,
    val isTest: Boolean? = null
)
