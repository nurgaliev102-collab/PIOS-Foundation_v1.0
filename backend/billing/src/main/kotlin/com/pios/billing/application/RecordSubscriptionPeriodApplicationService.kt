package com.pios.billing.application

import com.pios.billing.domain.Subscription
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Application-layer coordination for the Record Subscription Period
 * command (ADR-074 Part 3). This is the one seam every future inbound
 * path -- today's owner-gated `POST /v1/subscriptions/{driverId}/periods`,
 * and any real payment provider's webhook adapter later -- lands on
 * (ADR-074 Part 3, Decision 3). It sequences the command into the
 * [Subscription] aggregate's own behavior; it does not decide a driver's
 * subscription state itself.
 *
 * A driver with no existing record is `FREE` by construction
 * ([SubscriptionRepository.findByDriverId] returning `null`) -- this
 * service starts a brand-new [Subscription] for that case
 * ([Subscription.start]); an existing record is transitioned in place
 * ([Subscription.recordPeriod]). Either path ends the same way: the
 * resulting aggregate is saved and returned.
 *
 * This module owns no outbox and no transactional boundary type
 * (ADR-074's own "no RabbitMQ topology, no outbox, no consumer, no event
 * listener" constraint) -- unlike `DriverAvailabilityApplicationService`
 * in `driver-management`, there is nothing here to keep atomic with a
 * second write, since there is no second write. `save` is the only
 * persistence call this service makes.
 */
@Service
class RecordSubscriptionPeriodApplicationService(
    private val subscriptionRepository: SubscriptionRepository
) {
    fun handle(command: RecordSubscriptionPeriodCommand): Subscription {
        val now = Instant.now()
        val existing = subscriptionRepository.findByDriverId(command.driverId)
        val subscription = if (existing == null) {
            Subscription.start(
                driverId = command.driverId,
                targetStatus = command.targetStatus,
                currentPeriodEnd = command.currentPeriodEnd,
                isTest = command.isTest,
                now = now
            )
        } else {
            existing.recordPeriod(command.targetStatus, command.currentPeriodEnd, now)
            existing
        }
        subscriptionRepository.save(subscription)
        return subscription
    }
}
