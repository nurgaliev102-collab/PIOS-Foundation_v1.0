package com.pios.identity.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * The only new API surface ADR-043/ADR-044 add to this module — a
 * self-report of this module's own process and its own datasource, for
 * the platform owner's console only. Never reports on, or calls, any
 * other module (ADR-043 Decision 2). Carries no outbox figure: this
 * module owns no outbox table.
 *
 * Deliberately a **separate controller** from [com.pios.identity.api.IdentityController]
 * — ADR-044 Decision 1 is explicit that owner authentication is not a
 * capability of `identity`'s own domain, and `IdentityController`'s own
 * KDoc ("No other endpoint exists on this controller") must remain
 * literally true. Nothing here is edited on that class.
 *
 * Gated by [ownerCredentialGate]: a missing or incorrect credential
 * produces a plain `401` with **no `WWW-Authenticate` header** — adding
 * one would make the browser raise its own native credential dialog
 * ahead of the console's own login screen, which ADR-044 Decision 3 names
 * as the one easy-to-miss implementation trap here. This class never sets
 * that header, on either branch, so there is nothing to remove.
 *
 * The database check is a bare `SELECT 1` through the same [JdbcTemplate]
 * every other read in this module already uses — no ORM, no ping
 * abstraction, consistent with this module's existing persistence style.
 */
@RestController
@RequestMapping("/v1/health/identity")
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
        const val MODULE_NAME = "identity"
    }
}
