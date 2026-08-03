package com.pios.drivermanagement.api

/**
 * The REST response body for Retrieve Driver Availability (Sprint 5:
 * First Backend Integration; ADR-004, ADR-028). Carries exactly the
 * fields the [com.pios.drivermanagement.domain.Driver] aggregate itself
 * has: [id], [availability], and, since Sprint 7B (Personal Network Flow
 * MVP), [displayName] -- nothing beyond what the domain already models.
 *
 * [registeredAt] (ADR-043, Owner Control Center — Observation Boundary)
 * surfaces [com.pios.drivermanagement.domain.Driver.createdAt] — optional,
 * appended last, `null` for every driver registered before
 * `V4__add_driver_created_at.sql` and for any caller that omits it.
 */
data class DriverResponse(
    val id: String,
    val availability: String,
    val displayName: String? = null,
    val registeredAt: String? = null
)
