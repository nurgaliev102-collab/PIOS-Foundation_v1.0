package com.pios.drivermanagement.persistence

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * mirrors Order Management's own `Polling.kt` exactly -- consumption
 * through [AssignmentCompletedTestListenerHarness] happens asynchronously
 * on the broker's own delivery thread, so a test must poll rather than
 * assert immediately after publishing.
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
