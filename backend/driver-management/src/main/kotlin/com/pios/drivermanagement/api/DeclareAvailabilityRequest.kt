package com.pios.drivermanagement.api

/**
 * The REST request body for Declare Availability (Sprint FR-002: Driver
 * Availability). Carries exactly the primitive shape
 * [com.pios.drivermanagement.application.DeclareAvailabilityCommand]
 * already needs beyond the driver id itself, which arrives as a path
 * variable, not a body field.
 */
data class DeclareAvailabilityRequest(val availability: String)
