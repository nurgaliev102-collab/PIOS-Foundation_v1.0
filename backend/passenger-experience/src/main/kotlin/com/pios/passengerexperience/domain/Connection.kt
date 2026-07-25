package com.pios.passengerexperience.domain

import java.time.Instant

/**
 * Records that a passenger reached PIOS through a specific driver's own
 * invitation link (Sprint 7B: Personal Network Flow MVP) -- the fact that
 * lets Ride Request later offer a new order to this driver first, before
 * any general queue. Deliberately minimal: no status, no type, nothing
 * beyond the pairing and when it was recorded -- this sprint's own scope
 * is one transparent route, not a general relationship model (see
 * `docs/PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`'s own
 * narrowing).
 */
class Connection(
    val id: ConnectionId,
    val driverId: DriverReference,
    val passengerReference: PassengerReference,
    val createdAt: Instant
)
