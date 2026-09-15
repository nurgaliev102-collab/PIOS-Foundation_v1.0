package com.pios.billing.application

import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.SubscriptionStatus
import java.time.Instant

/**
 * The Record Subscription Period command (ADR-074 Part 3, Decision 1: "A
 * subscription period is recorded by a single command
 * (`RecordSubscriptionPeriodCommand` -> application service -> aggregate
 * transition). Every future inbound path lands here."). Represents an
 * authorized operator's intention to transition a driver's subscription
 * state; it does not itself decide whether the transition is permitted --
 * that remains the [com.pios.billing.domain.Subscription] aggregate's own
 * decision (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides
 * Business Meaning").
 *
 * [isTest] is honored only when this command results in a brand-new
 * record ([driverId] currently `FREE`) -- see
 * [com.pios.billing.domain.Subscription]'s own KDoc on why it is immutable
 * thereafter. Supplied by the caller that issues this command, never
 * fetched from `driver-management` (ADR-069's sourcing pattern).
 */
data class RecordSubscriptionPeriodCommand(
    val driverId: DriverReference,
    val targetStatus: SubscriptionStatus,
    val currentPeriodEnd: Instant?,
    val isTest: Boolean
)
