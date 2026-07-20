package com.pios.ordermanagement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Structural boundary for the Order Management module.
 *
 * This is an architectural placeholder only. It contains no business logic,
 * no domain entity, no database model, and no API endpoint. Its sole purpose
 * is to prove that this module can be built and started as its own,
 * independently deployable unit, consistent with MODULE_STRUCTURE.md and
 * ADR-023/ADR-026.
 *
 * [EnableScheduling] activates the periodic production trigger
 * (`com.pios.ordermanagement.application.OutboxRelayScheduler`) for the
 * already-existing outbox relay (Implementation Plan: Production Outbox
 * Relay Trigger v1.0).
 */
@SpringBootApplication
@EnableScheduling
class OrderManagementApplication

fun main(args: Array<String>) {
    runApplication<OrderManagementApplication>(*args)
}
