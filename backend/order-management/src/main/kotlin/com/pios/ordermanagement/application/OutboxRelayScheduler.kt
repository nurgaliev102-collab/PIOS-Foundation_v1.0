package com.pios.ordermanagement.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The production trigger for [OutboxRelay] (Implementation Plan: Production
 * Outbox Relay Trigger v1.0). Before this class existed, [OutboxRelay.relay]
 * was invoked only by test code -- a running deployment durably recorded
 * outbox records but never actually published them, the gap
 * PIOS_HARDENING_REVIEW_V1.md identified. This class adds no new relay
 * logic of its own; it only calls the already-existing, already-tested
 * [OutboxRelay.relay] periodically.
 *
 * Kept as a separate class from [OutboxRelay] itself, rather than annotating
 * [OutboxRelay.relay] directly, so every existing test that constructs
 * [OutboxRelay] outside a Spring context (`OutboxRelayTest`,
 * `OrderSubmittedPublicationTest`, and others) remains completely
 * unaffected by scheduling annotations that only take effect inside a real
 * [org.springframework.context.ApplicationContext].
 *
 * [FIXED_DELAY_PROPERTY]'s default (2000ms) is a configuration default, not
 * a product invariant -- overridable per deployment via `application.yml`
 * or an environment variable, without a code change. `fixedDelayString`
 * (not `fixedRateString`) is used deliberately: Spring's own `TaskScheduler`
 * does not begin the next execution until the current one returns, so a
 * slow broker lengthens the interval between ticks rather than causing two
 * ticks to run concurrently.
 *
 * Delivery guarantee this class provides, precisely: **at-least-once
 * delivery to the broker**, with effectively-once business effect only
 * because every current consumer is required to be idempotent (ADR-031).
 * This class introduces no row-claiming or locking (Implementation Plan
 * Part 4); if more than one instance of this module runs concurrently,
 * both instances' own schedulers could read and attempt to publish the
 * same pending record before either commits it published -- an accepted,
 * unmitigated characteristic of the current single-instance-per-module
 * deployment model (ADR-026), not resolved by this class, and not claimed
 * to be exactly-once.
 */
@Component
class OutboxRelayScheduler(
    private val outboxRelay: OutboxRelay
) {

    @Scheduled(fixedDelayString = "\${pios.outbox.relay.fixed-delay-ms:2000}")
    fun triggerRelay() {
        outboxRelay.relay()
    }

    companion object {
        const val FIXED_DELAY_PROPERTY = "pios.outbox.relay.fixed-delay-ms"
    }
}
