package com.pios.networkmanagement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for the Network Management module (Sprint 7A: PIOS
 * Network Foundation; ADR-037). Exposes Create/Retrieve Person, Create/
 * Retrieve Profile, Create/Retrieve Connection, and Create/Accept
 * Invitation. No scheduling, outbox relay, or message broker is enabled —
 * this module publishes and consumes no event (ADR-037's own boundary:
 * zero integration with any existing module in Sprint 7A).
 */
@SpringBootApplication
class NetworkManagementApplication

fun main(args: Array<String>) {
    runApplication<NetworkManagementApplication>(*args)
}
