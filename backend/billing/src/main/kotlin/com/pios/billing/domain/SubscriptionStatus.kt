package com.pios.billing.domain

/**
 * The four states of a driver's subscription (ADR-074 Part 2).
 *
 * [FREE] is **conceptual only** and is never persisted: the load-bearing
 * property of this design is that the absence of any [Subscription] record
 * for a driver *is* [FREE], by construction, not by a stored value. No row
 * is ever written with this status. [Subscription] itself can never hold
 * it (see that class's own constructor guard) -- only
 * [com.pios.billing.application.SubscriptionView], the read-side shape
 * that represents "no record" explicitly, ever carries it.
 *
 * [TRIAL], [ACTIVE], and [EXPIRED] are the only statuses an actual
 * [Subscription] row can hold. The permitted transitions among all four
 * (including the conceptual [FREE]) are enforced by [Subscription] --
 * see its own KDoc for the exact table (ADR-074 Part 2).
 */
enum class SubscriptionStatus {
    FREE,
    TRIAL,
    ACTIVE,
    EXPIRED
}
