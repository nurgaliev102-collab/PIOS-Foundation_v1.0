package com.pios.passengerexperience

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Structural boundary for the Passenger Experience module.
 *
 * Originally an architectural placeholder only, proving this module can be
 * built and started as its own, independently deployable unit, consistent
 * with MODULE_STRUCTURE.md and ADR-023/ADR-026. It now also owns Circle of
 * Trust (`com.pios.passengerexperience.api.ConnectionController`, ADR-054)
 * and, since Task 14 (First Refusal Foundation), publishes
 * `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` through its own
 * transactional outbox (ADR-062).
 *
 * [EnableScheduling] activates the periodic production trigger
 * (`com.pios.passengerexperience.application.OutboxRelayScheduler`) for
 * this module's own outbox relay — mirroring
 * `com.pios.dispatch.DispatchApplication`'s own identical annotation for
 * the identical reason.
 */
@SpringBootApplication
@EnableScheduling
class PassengerExperienceApplication

fun main(args: Array<String>) {
    runApplication<PassengerExperienceApplication>(*args)
}
