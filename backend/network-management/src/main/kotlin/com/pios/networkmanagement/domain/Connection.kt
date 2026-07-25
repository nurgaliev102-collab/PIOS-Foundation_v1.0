package com.pios.networkmanagement.domain

import java.time.Instant

/**
 * A directed relationship between two [Person]s (Sprint 7A: PIOS Network
 * Foundation) — the fundamental fact this sprint exists to represent (for
 * example, Artur → Regina). Directed, not symmetric: [fromPersonId] is the
 * inviter/owner of the relationship, [toPersonId] the invitee, mirroring
 * the founder's own "Артур → Регина" framing exactly. Sprint 7A gives this
 * fact no meaning beyond its own existence — it is not read by Dispatch,
 * Order Management, or any order-routing decision (ADR-037's own boundary;
 * `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`'s own narrow
 * scope, not yet exercised further by this sprint).
 */
class Connection(
    val id: ConnectionId,
    val fromPersonId: PersonId,
    val toPersonId: PersonId,
    val type: ConnectionType,
    val createdAt: Instant
) {
    init {
        require(fromPersonId != toPersonId) { "A Connection cannot link a Person to themselves" }
    }
}
