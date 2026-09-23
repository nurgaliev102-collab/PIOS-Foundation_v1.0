package com.pios.identity.api

import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Monotonic timing seam for recovery-response equalization. */
fun interface RecoveryRequestMonotonicClock {
    fun nanoTime(): Long
}

/** Sleeping seam kept outside controller logic so timing tests never sleep. */
fun interface RecoveryRequestSleeper {
    fun sleepMillis(millis: Long)
}

interface RecoveryRequestTiming {
    fun startedAtNanos(): Long
    fun padToFloor(startedAtNanos: Long, floorMillis: Long)
}

/** Testable implementation. It deliberately measures only monotonic time. */
class MonotonicRecoveryRequestTiming(
    private val monotonicClock: RecoveryRequestMonotonicClock,
    private val sleeper: RecoveryRequestSleeper
) : RecoveryRequestTiming {
    override fun startedAtNanos(): Long = monotonicClock.nanoTime()

    override fun padToFloor(startedAtNanos: Long, floorMillis: Long) {
        require(floorMillis >= 0) { "pios.identity.recovery.timing.floor-ms must not be negative" }
        val elapsedNanos = (monotonicClock.nanoTime() - startedAtNanos).coerceAtLeast(0)
        val remainingNanos = TimeUnit.MILLISECONDS.toNanos(floorMillis) - elapsedNanos
        if (remainingNanos > 0) {
            // Round up so the minimum floor is never undershot by truncation.
            sleeper.sleepMillis((remainingNanos + 999_999L) / 1_000_000L)
        }
    }
}

/** Spring production binding. */
@Component
class SystemRecoveryRequestTiming : RecoveryRequestTiming by MonotonicRecoveryRequestTiming(
    RecoveryRequestMonotonicClock(System::nanoTime),
    RecoveryRequestSleeper { millis ->
        try {
            Thread.sleep(millis)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
)

/** Used only by direct unit/controller construction that does not supply the Spring bean. */
object NoOpRecoveryRequestTiming : RecoveryRequestTiming {
    override fun startedAtNanos(): Long = 0L
    override fun padToFloor(startedAtNanos: Long, floorMillis: Long) = Unit
}
