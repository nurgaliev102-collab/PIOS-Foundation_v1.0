package com.pios.billing.application

import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.Subscription

/**
 * The persistence boundary for the [Subscription] aggregate
 * (PERSISTENCE_ARCHITECTURE.md Section 2, Domain-Owned Persistence). Names
 * only what Billing's own domain needs -- finding and saving a
 * [Subscription] by its owning driver -- and says nothing about how or
 * where it is actually stored. Exactly one current subscription record per
 * driver (ADR-074 Part 2): [findByDriverId] returning `null` means the
 * driver is `FREE` by construction, not that a lookup failed.
 *
 * Only Billing persists subscription state; no other module implements or
 * depends on this interface.
 */
interface SubscriptionRepository {
    fun findByDriverId(driverId: DriverReference): Subscription?
    fun save(subscription: Subscription)
}
