package com.pios.billing.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * A self-report of this module's own process and its own datasource, for
 * the platform owner's console only, mirroring every other module's own
 * `GET /v1/health` (ADR-043/ADR-044). Never reports on, or calls, any
 * other module. Carries no outbox figure: this module owns no outbox
 * table (ADR-074's own "no RabbitMQ topology, no outbox" constraint).
 *
 * Gated by [ownerCredentialGate]: a missing or incorrect credential
 * produces a plain `401` with **no `WWW-Authenticate` header** — adding
 * one would make the browser raise its own native credential dialog ahead
 * of the console's own login screen (ADR-044 Decision 3).
 *
 * The database check is a bare `SELECT 1` through the same [JdbcTemplate]
 * every other read in this module already uses — no ORM, no ping
 * abstraction.
 */
@RestController
@RequestMapping("/v1/health/billing")
class HealthController(
    private val ownerCredentialGate: OwnerCredentialGate,
    private val jdbcTemplate: JdbcTemplate
) {

    @GetMapping
    fun health(@RequestHeader("Authorization", required = false) authorization: String?): ResponseEntity<HealthResponse> {
        if (!ownerCredentialGate.verify(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val databaseUp = isDatabaseReachable()
        val body = HealthResponse(
            module = MODULE_NAME,
            status = if (databaseUp) "UP" else "DOWN",
            database = if (databaseUp) "UP" else "DOWN",
            checkedAt = Instant.now().toString()
        )
        return if (databaseUp) {
            ResponseEntity.ok(body)
        } else {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body)
        }
    }

    private fun isDatabaseReachable(): Boolean =
        try {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            true
        } catch (ex: Exception) {
            false
        }

    companion object {
        const val MODULE_NAME = "billing"
    }
}
