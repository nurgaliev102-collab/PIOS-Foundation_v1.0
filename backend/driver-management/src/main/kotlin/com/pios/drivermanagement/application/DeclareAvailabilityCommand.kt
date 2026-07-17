package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.DriverId

/**
 * The Declare Availability command (DOMAIN_MODEL.md Section 9;
 * MODULE_STRUCTURE.md, "Driver Management Module", Owned capabilities).
 * Represents a driver's intention to declare their own readiness; it does
 * not itself decide whether that declaration takes effect — that remains
 * the Driver aggregate's own decision (APPLICATION_ARCHITECTURE.md
 * Section 2, "Domain Decides Business Meaning").
 */
data class DeclareAvailabilityCommand(
    val driverId: DriverId,
    val requestedAvailability: Availability
)
