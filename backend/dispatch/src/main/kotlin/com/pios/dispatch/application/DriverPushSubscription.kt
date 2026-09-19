package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference

/**
 * A driver's Web Push delivery address (ADR-083, D-10: Driver Web Push for
 * Open Proposal and Price Confirmation) -- [endpoint] plus the two
 * encryption keys the browser's own `PushManager.subscribe()` returned.
 *
 * Deliberately **not** a record of any communication: ADR-083 Part 2 is
 * explicit that "a subscription row is a delivery address, never a record
 * that a communication occurred" -- this type carries no timestamp of any
 * send, no delivery status, no payload history. Adding any such field
 * would make this the `Notification` entity `ADR-033` reserves, by another
 * name.
 */
data class DriverPushSubscription(
    val endpoint: String,
    val driverReference: DriverReference,
    val p256dh: String,
    val auth: String
)
