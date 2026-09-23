package com.pios.identity.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Production trigger for [SmsOutboxRelay] (C-2 Decision Lock I.5).
 *
 * Cadence: [fixedDelay] 10 000 ms (10 s). `fixedDelay` (not `fixedRate`)
 * is used deliberately: Spring's scheduler does not begin the next execution
 * until the current one returns, so a slow SMS Aero response lengthens the
 * inter-tick interval rather than causing two ticks to overlap — the same
 * rationale the existing `OutboxRelayScheduler` in order-management already
 * established (see that class's own KDoc).
 *
 * 10 s vs order-management's 2 s: each SMS Aero call can take up to 7 s
 * worst-case (connect 2 s + read 5 s). With a 50-row batch, the tick could
 * take up to 350 s in the absolute worst case. The 10-second inter-tick
 * delay is an honest cadence for an external HTTP call, unlike the 2 s
 * optimised for sub-millisecond broker confirmation.
 *
 * Kept separate from [SmsOutboxRelay] itself so test classes that construct
 * [SmsOutboxRelay] outside a Spring context are completely unaffected by
 * scheduling annotations (the same isolation `OutboxRelayScheduler` uses).
 */
@Component
class SmsOutboxRelayScheduler(
    private val smsOutboxRelay: SmsOutboxRelay
) {
    @Scheduled(fixedDelayString = "\${pios.identity.sms.relay.fixed-delay-ms:10000}")
    fun triggerRelay() {
        smsOutboxRelay.relay()
    }

    companion object {
        const val FIXED_DELAY_PROPERTY = "pios.identity.sms.relay.fixed-delay-ms"
    }
}
