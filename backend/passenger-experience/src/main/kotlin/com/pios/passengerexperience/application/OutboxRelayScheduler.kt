package com.pios.passengerexperience.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The production trigger for [OutboxRelay] (Task 14, First Refusal
 * Foundation), mirroring Dispatch's own `OutboxRelayScheduler` exactly.
 * Adds no new relay logic of its own; it only calls the already-existing,
 * already-tested [OutboxRelay.relay] periodically.
 *
 * [FIXED_DELAY_PROPERTY]'s default (2000ms) is a configuration default,
 * not a product invariant — overridable per deployment via
 * `application.yml` or an environment variable, without a code change.
 * `fixedDelayString` (not `fixedRateString`) is used deliberately: Spring's
 * own `TaskScheduler` does not begin the next execution until the current
 * one returns, so a slow broker lengthens the interval between ticks
 * rather than causing two ticks to run concurrently.
 *
 * Requires `@EnableScheduling` on this module's own
 * `PassengerExperienceApplication` — added by this task alongside this
 * class, mirroring Dispatch's own `DispatchApplication`.
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
