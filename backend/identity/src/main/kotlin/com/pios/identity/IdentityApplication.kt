package com.pios.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for the Identity module (ADR-038). Exposes
 * Create/Retrieve Identity only. No scheduling, outbox relay, or message
 * broker is enabled — this module publishes and consumes no event
 * (ADR-038's own boundary: zero integration with any existing module,
 * mirroring `network-management`'s own Sprint 7A posture).
 */
@SpringBootApplication
class IdentityApplication

fun main(args: Array<String>) {
    runApplication<IdentityApplication>(*args)
}
