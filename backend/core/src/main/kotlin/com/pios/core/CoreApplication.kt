package com.pios.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for the PIOS Core module — Slice 01 (Participant
 * History Projection), ratified in ADR-067.
 *
 * A new, independently deployable bounded context beside Taxi
 * (MODULE_STRUCTURE.md, ADR-017/ADR-018 extended per §8; ADR-026). Its
 * entire Slice 01 responsibility is a single read-only projection: for
 * each participant, the ordered history of platform events concerning
 * them, derived exclusively by consuming events order-management,
 * dispatch, and passenger-experience already publish.
 *
 * Consumer-only (ADR-067 Event Boundary): Spring Boot's default component
 * scan covers this class's package and every subpackage, so the three
 * `@RabbitListener` transport classes under `com.pios.core.persistence`
 * are wired automatically. There is deliberately **no** `@EnableScheduling`
 * (unlike DispatchApplication) — Core has no outbox relay and no
 * scheduled work of any kind; it only reacts to inbound messages.
 *
 * Not on the Taxi critical path (ADR-067 Failure Isolation): if this
 * process is stopped, crashed, or never deployed, every Taxi flow behaves
 * exactly as it does today.
 */
@SpringBootApplication
class CoreApplication

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}
