package com.pios.dispatch.domain

import java.time.Instant

/**
 * ProposalWithdrawn. Owned exclusively by Dispatch (ADR-053, Proposal
 * Resolution on Order Cancellation, Part 4). Records that the order this
 * proposal was for has been cancelled before any driver acceptance —
 * distinct from [ProposalDeclined] (the driver's own refusing act) and
 * [ProposalLapsed] (a timeout) as neither actor-driven nor time-driven,
 * but a reflection of a fact recorded one level up, in Order Management.
 * Dispatch-internal only, mirroring [ProposalLapsed]'s own treatment — no
 * other domain currently consumes it, and no event infrastructure,
 * broker, or schema is implied by this representation, consistent with
 * ADR-003.
 */
data class ProposalWithdrawn(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
