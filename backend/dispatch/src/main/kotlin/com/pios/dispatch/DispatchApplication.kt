package com.pios.dispatch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Structural boundary for the Dispatch module.
 *
 * Originally an architectural placeholder proving only that this module
 * can be built and started as its own, independently deployable unit
 * (MODULE_STRUCTURE.md, ADR-023/ADR-026). It now also exposes
 * `com.pios.dispatch.api.AssignmentController`'s `POST /v1/assignments`
 * (Assign Order, Sprint FR-004 — Manual Assignment): a human coordinator
 * supplies the order/driver pairing; this module records it. No
 * assignment algorithm, selection criteria, or automated distribution
 * logic is added or implemented here — per ADR-002, those remain never
 * implemented in this module regardless of what REST surface exists.
 *
 * Also exposes `com.pios.dispatch.api.ProposalController`'s
 * `/v1/proposals` endpoints (Sprint IMPLEMENTATION-003, Proposal Vertical
 * Slice; ADR-035). No `@ComponentScan` or bean-registration change was
 * needed for either the Proposal or the Assignment REST surface: Spring
 * Boot's default component scan already covers this class's own package
 * and every subpackage, so `@RestController`/`@Service`/`@Repository`
 * classes anywhere under `com.pios.dispatch` are found automatically.
 *

 * [EnableScheduling] activates the periodic production trigger
 * (`com.pios.dispatch.application.OutboxRelayScheduler`) for the
 * already-existing outbox relay (Implementation Plan: Production Outbox
 * Relay Trigger v1.0).
 */
@SpringBootApplication
@EnableScheduling
class DispatchApplication

fun main(args: Array<String>) {
    runApplication<DispatchApplication>(*args)
}
