package com.pios.dispatch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for the Dispatch module.
 *
 * This is an architectural placeholder only. It contains no business logic,
 * no assignment algorithm, no domain entity, no database model, and no API
 * endpoint. Its sole purpose is to prove that this module can be built and
 * started as its own, independently deployable unit, consistent with
 * MODULE_STRUCTURE.md and ADR-023/ADR-026. Per ADR-002, the specific
 * assignment criteria are never implemented here.
 */
@SpringBootApplication
class DispatchApplication

fun main(args: Array<String>) {
    runApplication<DispatchApplication>(*args)
}
