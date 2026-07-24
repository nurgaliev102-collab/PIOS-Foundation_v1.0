package com.pios.drivermanagement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Structural boundary for the Driver Management module.
 *
 * Originally an architectural placeholder proving only that this module
 * can be built and started as its own, independently deployable unit
 * (MODULE_STRUCTURE.md, ADR-023/ADR-026). It now also exposes REST
 * endpoints on `com.pios.drivermanagement.api.DriverController`:
 * `GET /v1/drivers/{driverId}` and `GET /v1/drivers` (Retrieve Driver
 * Availability, APPLICATION_ARCHITECTURE.md Section 7, the latter
 * broadened to a list by Sprint FR-002), and
 * `POST /v1/drivers/{driverId}/availability` (Declare Availability,
 * Section 6, Sprint FR-002 — this module's first write REST path). No
 * command, query, or database model beyond what was already owned by
 * this module is added.
 *
 * [EnableScheduling] activates the periodic production trigger
 * (`com.pios.drivermanagement.application.OutboxRelayScheduler`) for the
 * already-existing outbox relay (Implementation Plan: Production Outbox
 * Relay Trigger v1.0).
 */
@SpringBootApplication
@EnableScheduling
class DriverManagementApplication

fun main(args: Array<String>) {
    runApplication<DriverManagementApplication>(*args)
}
