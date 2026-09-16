package com.pios.identity.domain

import java.time.Instant

/**
 * The Identity aggregate (ADR-038) — an authentication anchor for one human,
 * independent of any role (Driver, Passenger) and independent of
 * `network-management`'s own [com.pios.networkmanagement.domain.Person]
 * (a *relationship* identity, not an authentication one — see ADR-038's
 * self-critique section for why the two are kept separate rather than
 * merged).
 *
 * [phone] is optional and, today, unverified — creating an Identity does
 * not prove anyone controls the number supplied. Verifying it is exactly
 * the capability this ADR does *not* authorize; [phone] exists here only so
 * the shape an eventual verification flow would update already exists,
 * rather than being bolted on later as a schema migration against live
 * data.
 *
 * [driverId] (ADR-039) is this Identity's first real cross-module
 * reference: a plain string pointing at a `driver-management`-owned
 * `DriverId`, never a foreign key and never ownership — the same
 * "reference, not ownership" rule `ADR-037` already established for
 * `network-management`. `driver-management` itself has no column, table,
 * or API knowledge of `Identity` at all; the reference is legible only
 * from this side.
 *
 * No [Credential], [Device], or [Session] reference lives on this class:
 * those remain unpersisted domain shapes (see the sibling files in this
 * package) until a future ADR actually wires one of them to a real
 * application service.
 */
class Identity(
    val id: IdentityId,
    val phone: Phone?,
    val driverId: String?,
    val createdAt: Instant
) {
    /**
     * Returns a copy with [driverId] set (ADR-039) — an Identity's driver
     * reference is set once, at driver-profile creation, and this method
     * exists so [com.pios.identity.application.AssociateDriverApplicationService]
     * does not need to reach into this class's private state to change it.
     */
    fun withDriverId(driverId: String): Identity = Identity(id, phone, driverId, createdAt)

    fun withPhone(phone: Phone): Identity = Identity(id, phone, driverId, createdAt)
}
