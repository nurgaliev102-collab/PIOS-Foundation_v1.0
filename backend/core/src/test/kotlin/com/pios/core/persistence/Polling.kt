package com.pios.core.persistence

/**
 * Test-only helper for asserting eventually-consistent state: consumption
 * through a listener harness happens asynchronously on the broker's own
 * delivery thread, so a test polls rather than asserting immediately after
 * publishing. Mirrors dispatch's own `awaitUntilNotNull`.
 */
internal fun <T> awaitUntilNotNull(
    maxAttempts: Int = 40,
    perAttemptDelayMillis: Long = 250L,
    poll: () -> T?
): T? {
    repeat(maxAttempts) {
        val result = poll()
        if (result != null) return result
        Thread.sleep(perAttemptDelayMillis)
    }
    return null
}
