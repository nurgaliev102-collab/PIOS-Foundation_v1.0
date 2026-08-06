package com.pios.platformops.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * T-3's own deliverable, and nothing more: proof this deployable exists,
 * is closed by default, and is reachable. No target list, no SCM query, no
 * log reader -- those arrive in T-5 onward.
 *
 * Gated by [opsCredentialGate]: a missing or incorrect credential produces
 * a plain `401` with **no `WWW-Authenticate` header** -- adding one would
 * make the browser raise its own native credential dialog ahead of the
 * console's own login screen, the same implementation trap
 * `OwnerCredentialGate`'s own callers already avoid for ADR-044 Decision 3.
 * This class never sets that header, on either branch.
 */
@RestController
@RequestMapping("/v1/ops/health")
class HealthController(
    private val opsCredentialGate: OpsCredentialGate
) {

    @GetMapping
    fun health(@RequestHeader("Authorization", required = false) authorization: String?): ResponseEntity<HealthResponse> {
        if (!opsCredentialGate.verify(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.ok(HealthResponse(status = "UP", checkedAt = Instant.now().toString()))
    }
}

data class HealthResponse(
    val status: String,
    val checkedAt: String
)
