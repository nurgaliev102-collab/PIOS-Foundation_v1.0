package com.pios.dispatch.persistence

/**
 * Test-only helper for asserting eventually-consistent state: consumption
 * through [DispatchTestListenerHarness] happens asynchronously on the
 * broker's own delivery thread, so a test must poll rather than assert
 * immediately after publishing. Mirrors the retry-loop pattern already
 * used by this project's own producer-side observer queues
 * (`receiveMessageContaining`).
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
