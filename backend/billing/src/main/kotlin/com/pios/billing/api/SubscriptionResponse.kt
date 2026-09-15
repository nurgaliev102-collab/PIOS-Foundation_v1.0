package com.pios.billing.api

/**
 * The shape both this module's endpoints return -- `{status,
 * currentPeriodEnd}`, exactly the two fields ADR-074 Part 4's one
 * authorized future read seam names, and nothing else. `status` is one of
 * `FREE`/`TRIAL`/`ACTIVE`/`EXPIRED`; `currentPeriodEnd` is an ISO-8601
 * instant string, or `null` when none is recorded (always `null` for
 * `FREE`).
 */
data class SubscriptionResponse(
    val status: String,
    val currentPeriodEnd: String?
)
