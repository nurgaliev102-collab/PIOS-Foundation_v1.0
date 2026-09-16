package com.pios.identity.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Bounded per-client creation window protecting the credential-free guest endpoint from database spam. */
@Component
class GuestIdentityRateLimiter(
    @Value("\${pios.identity.guest.max-per-window:10}") private val maxPerWindow: Int,
    @Value("\${pios.identity.guest.window-ms:3600000}") private val windowMillis: Long
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
