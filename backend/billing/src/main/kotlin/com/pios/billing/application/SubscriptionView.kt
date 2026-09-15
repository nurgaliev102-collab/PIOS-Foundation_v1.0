package com.pios.billing.application

import com.pios.billing.domain.SubscriptionStatus
import java.time.Instant

/**
 * The read-side shape [RetrieveSubscriptionHandler] returns -- exactly the
 * two fields ADR-074 Part 4's one authorized future seam
 * (`GET /v1/subscriptions/{driverId}`) exposes, `{status, currentPeriodEnd}`,
 * nothing else. Unlike [com.pios.billing.domain.Subscription], [status]
 * here can legitimately be [SubscriptionStatus.FREE] -- this is the one
 * place in this module that represents "no record" as an explicit value,
 * for a caller that needs to display it.
 */
data class SubscriptionView(
    val status: SubscriptionStatus,
    val currentPeriodEnd: Instant?
)
