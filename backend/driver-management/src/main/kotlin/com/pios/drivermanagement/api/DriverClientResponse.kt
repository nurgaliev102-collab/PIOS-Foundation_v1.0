package com.pios.drivermanagement.api

/**
 * Server-side "Мой бизнес" read model (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md`
 * Part 5): the REST response body for one entry of Retrieve Driver Clients
 * (`GET /v1/drivers/{driverId}/clients`) -- one per passenger this driver
 * has completed at least one ride with.
 *
 * [lastRideAt] is `null` for a ride recorded before `V14` added the column
 * (see that migration's own comment) -- no historical backfill, same
 * disclosed-gap precedent ADR-065's own `totalStatedEarnings`/
 * `unpricedRidesCount` fields already established for this module.
 *
 * [isRepeat] mirrors `driver_client_rides.ride_count >= 2` verbatim -- see
 * [com.pios.drivermanagement.domain.DriverClientRecord]'s own KDoc. Never
 * exposed to a passenger anywhere (this endpoint is `Bearer`/self-only,
 * exactly like `GET /v1/drivers/{driverId}/milestones` -- see
 * [DriverController.getMilestones]'s own KDoc on why this kind of fact is
 * private, driver-facing business data, not a public one).
 */
data class DriverClientResponse(
    val passengerReference: String,
    val rideCount: Int,
    val lastRideAt: String?,
    val isRepeat: Boolean
)
