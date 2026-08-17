package com.pios.drivermanagement.api

/**
 * The REST request body for Create Driver (Sprint 3A: MVR Pilot
 * Enablement). Carries only [driverId] -- the sole field
 * [com.pios.drivermanagement.application.CreateDriverCommand] needs.
 * Unlike Declare Availability, the driver does not exist yet at the time
 * of this call, so its id cannot be a path variable the way
 * [DeclareAvailabilityRequest]'s own driver id is -- it arrives in the
 * body instead.
 *
 * [displayName] (Sprint 7B: Personal Network Flow MVP) is optional, so
 * every existing caller of this endpoint remains unaffected.
 *
 * [isTest] (Owner Control Center test/production data separation,
 * 2026-08-17) lets an automated/manual technical verification mark the
 * driver it creates as such -- optional, defaulting to `false`, so no
 * existing caller (a real driver's own registration) is affected.
 */
data class CreateDriverRequest(val driverId: String, val displayName: String? = null, val isTest: Boolean = false)
