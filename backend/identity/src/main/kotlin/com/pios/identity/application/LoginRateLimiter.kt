package com.pios.identity.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-phone failed-login rate limiting (ADR-055 Decision 3: "Failure-rate
 * limiting reuses `OwnerCredentialGate`'s in-memory window, with its
 * already-disclosed limits"), adapted from
 * `com.pios.identity.api.OwnerCredentialGate`'s single *global* window to
 * one window **per presented phone number** — so one attacker guessing
 * one phone number's password does not lock out every other passenger or
 * driver logging in at the same time. Held in memory in this process
 * only; resets on restart, the same disclosed limitation as the class
 * this one is adapted from.
 */
@Component
class LoginRateLimiter(
    @Value("\${pios.identity.login.failure-delay-ms:500}") private val failureDelayMillis: Long,
    @Value("\${pios.identity.login.max-failures-per-window:20}") private val maxFailuresPerWindow: Int,
    @Value("\${pios.identity.login.window-ms:900000}") private val windowMillis: Long
) {
    private data class FailureWindow(val count: Int, val startedAt: Instant)

    private val failureWindows = ConcurrentHashMap<String, FailureWindow>()

    /**
     * `true` if [phone] has already exhausted its failure budget within
     * the current rolling window.
     */
    fun tooManyRecentFailures(phone: String): Boolean {
        val current = failureWindows[phone] ?: return false
        val withinWindow = Duration.between(current.startedAt, Instant.now()).toMillis() < windowMillis
        return withinWindow && current.count >= maxFailuresPerWindow
    }

    /**
     * Records one more failed login attempt for [phone], then sleeps
     * [failureDelayMillis] — the same fixed-delay discipline
     * `OwnerCredentialGate.verify` already applies after a failed
     * verification.
     */
    fun recordFailure(phone: String) {
        failureWindows.compute(phone) { _, current ->
            val now = Instant.now()
            if (current == null || Duration.between(current.startedAt, now).toMillis() >= windowMillis) {
                FailureWindow(1, now)
            } else {
                FailureWindow(current.count + 1, current.startedAt)
            }
        }
        Thread.sleep(failureDelayMillis)
    }
}
