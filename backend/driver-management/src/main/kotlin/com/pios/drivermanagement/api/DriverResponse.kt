package com.pios.drivermanagement.api

/**
 * The REST response body for Retrieve Driver Availability (Sprint 5:
 * First Backend Integration; ADR-004, ADR-028). Carries exactly the two
 * fields the [com.pios.drivermanagement.domain.Driver] aggregate itself
 * has — [id] and [availability] — nothing else. No name, profile, or
 * other attribute is exposed here because none exists on the aggregate
 * (see that class's own KDoc): this transport-layer type introduces no
 * field beyond what the domain already models.
 */
data class DriverResponse(val id: String, val availability: String)
