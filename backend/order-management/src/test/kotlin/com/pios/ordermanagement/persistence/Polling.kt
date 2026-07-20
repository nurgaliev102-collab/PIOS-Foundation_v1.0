package com.pios.ordermanagement.persistence

/**
 * Test-only helper for asserting eventually-consistent state: consumption
 * through [OrderManagementTestListenerHarness] happens asynchronously on
 * the broker's own delivery thread, so a test must poll rather than
 * assert immediately after publishing. Mirrors Dispatch's own
 * `awaitUntilNotNull` exactly (Tranche 1: Dispatch Event Publishing
 * Completion).
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
