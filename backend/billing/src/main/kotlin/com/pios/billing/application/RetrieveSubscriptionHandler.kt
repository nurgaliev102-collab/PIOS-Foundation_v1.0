package com.pios.billing.application

import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.SubscriptionStatus
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Retrieve Subscription query
 * (ADR-074 Part 4's one authorized future seam). Read-only: changes
 * nothing, publishes nothing. A driver with no persisted
 * [com.pios.billing.domain.Subscription] record is [SubscriptionStatus.FREE]
 * by construction -- this handler never throws a not-found error, since
 * "no record" is itself a valid, meaningful answer for every driver who
 * has never interacted with this module (ADR-074 Part 2).
 */
@Service
class RetrieveSubscriptionHandler(
    private val subscriptionRepository: SubscriptionRepository
) {
    fun handle(driverId: DriverReference): SubscriptionView {
        val subscription = subscriptionRepository.findByDriverId(driverId)
            ?: return SubscriptionView(SubscriptionStatus.FREE, null)
        return SubscriptionView(subscription.status, subscription.currentPeriodEnd)
    }
}
