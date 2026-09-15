package com.pios.billing.api

/**
 * The request body for `POST /v1/subscriptions/{driverId}/periods`
 * (ADR-074 Part 3, Decision 2). [status] must name one of
 * [com.pios.billing.domain.SubscriptionStatus]'s own values -- `FREE` is
 * rejected explicitly by [SubscriptionController] (it is never a valid
 * write target: see that enum's own KDoc), and anything else this
 * platform's [com.pios.billing.domain.Subscription] transition table does
 * not permit from the driver's current state is rejected as a 409
 * Conflict, not here. [currentPeriodEnd], if present, must be an
 * ISO-8601 instant (e.g. `2026-10-15T00:00:00Z`); `null`/absent leaves no
 * period end recorded. [isTest] is honored only when this call creates a
 * brand-new record (see [com.pios.billing.application.RecordSubscriptionPeriodCommand]'s
 * own KDoc) -- supplied explicitly by the caller, never defaulted, since
 * there is no safe default for "is this a real driver or a test one."
 */
data class RecordSubscriptionPeriodRequest(
    val status: String,
    val currentPeriodEnd: String?,
    val isTest: Boolean
)
