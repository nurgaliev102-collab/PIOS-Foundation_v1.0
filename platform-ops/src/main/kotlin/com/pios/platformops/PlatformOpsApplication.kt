package com.pios.platformops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for T-3
 * (ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md): the
 * deployable exists, is closed by default, and is reachable. Nothing else.
 * No target list, no SCM adapter, no mutating endpoint — those are later
 * tasks (T-5 onward). This module is not part of PIOS's own domain
 * (ADR-046 Decision 1): it owns no capability, models no domain, holds no
 * data, and calls no other module's API (ADR-046 Decision 3).
 */
@SpringBootApplication
class PlatformOpsApplication

fun main(args: Array<String>) {
    runApplication<PlatformOpsApplication>(*args)
}
