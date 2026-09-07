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
 *
 * [isTest] (Owner Control Center test/production data separation,
 * 2026-08-17) surfaces [com.pios.drivermanagement.domain.Driver.isTest]
 * unchanged — `false` for every real driver and for every driver that
 * existed before this field was introduced (the migration's own
 * `DEFAULT FALSE`). Read by the Owner Control Center's own frontend
 * filtering, never by this module.
 *
 * [vehicleMake]/[vehicleModel]/[vehicleColor]/[vehiclePlateNumber]/
 * [vehicleSeatCount] (PIOS Group and Long-Distance Rides Roadmap, Stage 1)
 * surface [com.pios.drivermanagement.domain.Driver]'s own same-named
 * properties, appended last, `null` for every driver who has not declared
 * a vehicle. Deliberately on the same public, unauthenticated
 * `GET /v1/drivers`/`GET /v1/drivers/{id}` response [displayName] already
 * rides on: a passenger needs to see which car to look for from the same
 * invite-preview screen that already shows the driver's name, before any
 * login exists to gate behind.
 *
 * [acceptsLongDistanceTrips] (PIOS Group and Long-Distance Rides Roadmap,
 * Stage 3) surfaces [com.pios.drivermanagement.domain.Driver]'s own
 * same-named property, on the same public, unauthenticated response
 * [vehicleMake] already rides on -- a passenger deciding whether to ask
 * this driver about a long trip needs this before any login exists to
 * gate behind, the same reasoning as the vehicle fields.
 */
data class DriverResponse(
    val id: String,
    val availability: String,
    val displayName: String? = null,
    val registeredAt: String? = null,
    val isTest: Boolean = false,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val vehicleColor: String? = null,
    val vehiclePlateNumber: String? = null,
    val vehicleSeatCount: Int? = null,
    val acceptsLongDistanceTrips: Boolean = false
)
