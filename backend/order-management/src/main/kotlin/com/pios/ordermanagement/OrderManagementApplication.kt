package com.pios.ordermanagement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Structural boundary for the Order Management module.
 *
 * Originally an architectural placeholder proving only that this module
 * can be built and started as its own, independently deployable unit
 * (MODULE_STRUCTURE.md, ADR-023/ADR-026). It now also exposes REST
 * endpoints: `com.pios.ordermanagement.api.OrderSubmissionController`'s
 * `POST /v1/orders` (Submit Order, Tranche 2: Passenger Experience REST
 * Transport), and `com.pios.ordermanagement.api.OrderQueryController`'s
 * `GET /v1/orders` (Retrieve Orders, Sprint FR-003 — Order Query). No
 * command, query, or database model beyond what was already owned by
 * this module is added.
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
