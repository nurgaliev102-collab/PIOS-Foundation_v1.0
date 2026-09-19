package com.pios.dispatch.persistence

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * The bounded executor [WebPushDriverPushNotifier] submits each Web Push
 * send to, off the caller's own thread, strictly after the triggering
 * transaction has committed (ADR-083, D-10, Part 8).
 *
 * A small, fixed-size pool: driver push is a best-effort, fire-and-forget
 * delivery channel that must never add latency to a real request thread
 * (ADR-083 Constraint 6 -- push must never affect dispatch priority,
 * ranking, eligibility, pricing, trust, or any business metric, including
 * its own latency). [ThreadPoolExecutor.DiscardPolicy] -- not Spring's own
 * default `CallerRunsPolicy` -- is deliberate: under sustained saturation
 * the newest task is silently dropped rather than executed on whichever
 * thread submitted it (an already-committed transaction's own after-commit
 * callback). Losing a push under saturation is an accepted, disclosed
 * limitation (ADR-083 Consequences: "no delivery guarantee, no retry"),
 * never a correctness concern for Proposal/Assignment state.
 */
@Configuration
class DriverPushExecutorConfiguration {

    @Bean
    fun driverPushExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            setThreadNamePrefix("driver-push-")
            setRejectedExecutionHandler(ThreadPoolExecutor.DiscardPolicy())
            initialize()
        }
}
