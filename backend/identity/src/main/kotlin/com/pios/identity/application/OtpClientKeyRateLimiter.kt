package com.pios.identity.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-client-key OTP request rate limiting (ADR-082 Part 7/D-03.7: "rate
 * limit by IP/client-key"), the same shape as [GuestIdentityRateLimiter],
 * as a separate bean/config/counter — request budgets for guest creation
 * and OTP requests are deliberately not shared, so exhausting one never
 * silently starves the other. The client-key itself is derived by the
 * caller ([com.pios.identity.api.IdentityController]) reusing exactly the
 * same, already-hardened socket-address/loopback-proxy-override logic
 * `createGuestFromAddress` already established (`ADR-075` Decision 6) —
 * this class only holds the window, not the derivation.
 */
@Component
class OtpClientKeyRateLimiter(
    @Value("\${pios.identity.otp.client.max-per-window:20}") private val maxPerWindow: Int,
    @Value("\${pios.identity.otp.client.window-ms:3600000}") private val windowMillis: Long
) {
    private data class Window(val count: Int, val startedAt: Instant)
    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(clientKey: String): Boolean {
        var allowed = false
        windows.compute(clientKey) { _, current ->
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
