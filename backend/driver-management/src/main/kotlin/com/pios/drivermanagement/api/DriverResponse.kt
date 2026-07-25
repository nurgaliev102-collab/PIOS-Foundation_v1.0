package com.pios.drivermanagement.api

/**
 * The REST response body for Retrieve Driver Availability (Sprint 5:
 * First Backend Integration; ADR-004, ADR-028). Carries exactly the
 * fields the [com.pios.drivermanagement.domain.Driver] aggregate itself
 * has: [id], [availability], and, since Sprint 7B (Personal Network Flow
 * MVP), [displayName] -- nothing beyond what the domain already models.
 */
data class DriverResponse(val id: String, val availability: String, val displayName: String? = null)
