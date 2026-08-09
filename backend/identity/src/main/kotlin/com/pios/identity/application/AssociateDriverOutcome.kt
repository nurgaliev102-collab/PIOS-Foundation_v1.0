package com.pios.identity.application

import com.pios.identity.domain.Identity
import java.time.Instant

/**
 * The result of [AssociateDriverApplicationService.handle] (ADR-055
 * Decision 6 addendum) — the updated [identity] plus a freshly issued
 * session [token] carrying the newly attached `driverId` as its `drv`
 * claim, so the caller does not need a separate login round-trip before
 * its own `driverId`-scoped calls (e.g. `GET /v1/connections?driverId=`)
 * stop being rejected. Mirrors [RegisterIdentityOutcome] exactly — same
 * shape, same reasoning: a mutation that changes what a token should
 * assert issues a new one immediately, rather than leaving the caller to
 * discover the old one is stale by trial and error.
 */
data class AssociateDriverOutcome(val identity: Identity, val token: String, val expiresAt: Instant)
