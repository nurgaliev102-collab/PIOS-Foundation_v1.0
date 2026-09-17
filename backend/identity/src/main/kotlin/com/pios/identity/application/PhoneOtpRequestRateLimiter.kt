package com.pios.identity.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-phone OTP request rate limiting (ADR-082 Part 7/D-03.7: "rate limit
 * by phone"), mirroring [GuestIdentityRateLimiter]'s exact `tryAcquire`
 * shape — a bounded, in-memory, per-key sliding window, not a distributed
 * control, the same disclosed limitation `ADR-075` already accepted for
 * guest creation. Shared by both OTP purposes (recovery and legacy
 * enrolment) since both are the same class of "how many codes may this
 * phone request" question.
 */
@Component
class PhoneOtpRequestRateLimiter(
    @Value("\${pios.identity.otp.request.max-per-window:5}") private val maxPerWindow: Int,
    @Value("\${pios.identity.otp.request.window-ms:3600000}") private val windowMillis: Long
) {
    private data class Window(val count: Int, val startedAt: Instant)
    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(phone: String): Boolean {
        var allowed = false
        windows.compute(phone) { _, current ->
            val now = Instant.now()
            if (current == null || Duration.between(current.startedAt, now).toMillis() >= windowMillis) {
                allowed = true
                Window(1, now)
            } else if (current.count < maxPerWindow) {
                allowed = true
                Window(current.count + 1, current.startedAt)
            } else {
                current
            }
        }
        return allowed
    }
}
