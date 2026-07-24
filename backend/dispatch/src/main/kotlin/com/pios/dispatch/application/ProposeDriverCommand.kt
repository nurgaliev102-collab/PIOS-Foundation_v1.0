package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference

/**
 * The Propose Driver command (Domain Design — Pre-Commitment Aggregate,
 * Step 6). Represents the intention to propose a specific driver for a
 * specific order, prior to that driver's own confirmation. Mirrors
 * [AssignOrderCommand] exactly: it does not itself decide whether the
 * proposal is valid — that remains the Proposal aggregate's own decision.
 * The order and driver references are supplied by the caller: which order
 * is submitted and which driver is available remain Order Management's
 * and Driver Management's own responsibility, not Dispatch's.
 */
data class ProposeDriverCommand(
    val order: OrderReference,
    val driver: DriverReference
)
