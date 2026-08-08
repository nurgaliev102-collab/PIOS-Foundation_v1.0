package com.pios.passengerexperience.api

/**
 * [createdAt] (Sprint "Driver Growth Snapshot", H6) carries
 * [com.pios.passengerexperience.domain.Connection.createdAt] as its own
 * `Instant.toString()` (ISO-8601) — the same explicit-`.toString()`
 * convention [com.pios.ordermanagement.api.OrderResponse.createdAt]
 * already uses, rather than depending on Jackson's automatic `Instant`
 * (de)serialization. Never null: every [com.pios.passengerexperience.domain.Connection]
 * has one from the moment it is created.
 */
data class ConnectionResponse(val passengerReference: String, val createdAt: String)
