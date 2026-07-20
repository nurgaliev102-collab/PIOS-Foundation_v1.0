package com.pios.ordermanagement.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.ordermanagement.persistence.AssignmentAcceptedListener`)
 * translates a received AssignmentAccepted message into, before calling
 * [AssignmentAcceptedProjectionApplicationService]. Carries only
 * [eventId] (for idempotent processing), [orderReference], and
 * [driverReference] -- exactly what the Dispatch -> Order Management
 * contract (INTERFACE_CONTRACTS.md Section 5) justifies, nothing from
 * Dispatch's own domain types. Mirrors Dispatch's own
 * `DriverAvailabilityUpdateCommand` exactly.
 */
data class AssignmentAcceptedUpdateCommand(
    val eventId: String,
    val orderReference: String,
    val driverReference: String
)
