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
 */
data class CreateDriverRequest(val driverId: String, val displayName: String? = null)
