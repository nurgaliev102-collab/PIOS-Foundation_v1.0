package com.pios.passengerexperience

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for the Passenger Experience module.
 *
 * This is an architectural placeholder only. It contains no business logic,
 * no domain entity, no database model, and no API endpoint. Its sole purpose
 * is to prove that this module can be built and started as its own,
 * independently deployable unit, consistent with MODULE_STRUCTURE.md and
 * ADR-023/ADR-026.
 */
@SpringBootApplication
class PassengerExperienceApplication

fun main(args: Array<String>) {
    runApplication<PassengerExperienceApplication>(*args)
}
