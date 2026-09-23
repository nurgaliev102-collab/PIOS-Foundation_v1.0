package com.pios.identity.application

import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

/** Injectable full-jitter source; tests provide a deterministic implementation. */
fun interface SmsRetryJitter {
    /** Uniformly selects a delay in the inclusive range [0, maxDelayMillis]. */
    fun nextDelayMillis(maxDelayMillis: Long): Long
}

@Component
class ThreadLocalSmsRetryJitter : SmsRetryJitter {
    override fun nextDelayMillis(maxDelayMillis: Long): Long =
        ThreadLocalRandom.current().nextLong(0, maxDelayMillis + 1)
}
