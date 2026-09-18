package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import java.time.Instant

/**
 * The Propose Driver command (Domain Design — Pre-Commitment Aggregate,
 * Step 6). Represents the intention to propose a specific driver for a
 * specific order, prior to that driver's own confirmation. Mirrors
 * [AssignOrderCommand] exactly: it does not itself decide whether the
 * proposal is valid — that remains the Proposal aggregate's own decision.
 * The order and driver references are supplied by the caller: which order
 * is submitted and which driver is available remain Order Management's
 * and Driver Management's own responsibility, not Dispatch's.
 *
 * [passengerReference] (ADR-066, Proposal Participant Authorization)
 * defaults to `null` so every existing caller/test continues to compile
 * unchanged; a real caller now supplies it (see [Proposal.propose]'s own
 * KDoc for the three sources).
 */
data class ProposeDriverCommand(
    val order: OrderReference,
    val driver: DriverReference,
    val isTest: Boolean = false,
    val passengerReference: PassengerReference? = null,
    val requestedPickupAt: Instant? = null,
    val viaTrustedFallback: Boolean = false
)
