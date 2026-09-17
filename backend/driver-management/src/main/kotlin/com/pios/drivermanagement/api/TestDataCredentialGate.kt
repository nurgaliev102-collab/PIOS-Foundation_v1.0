package com.pios.drivermanagement.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * Checks the one credential a scriptable, non-owner caller may present to
 * `POST /v1/drivers` to create a synthetic (`isTest = true`) driver row
 * (`ADR-079`: Test-Data Credential for Synthetic (`isTest`) Driver
 * Creation). Its own `Authorization` scheme is `PiosTest <token>` --
 * deliberately not `Basic` (that is [OwnerCredentialGate]'s own scheme) and
 * not `Bearer` (that is [SessionTokenVerifier]'s own scheme), so the three
 * gates can never be confused with one another at the header level
 * (`ADR-079` Decision 2).
 *
 * **Never a replacement for, or a route through, [OwnerCredentialGate].**
 * `ADR-079` Decision 2 is explicit that a `PiosTest` credential must never
 * reach [OwnerCredentialGate.verify]: that gate records a failure and
 * sleeps on every non-matching credential and locks out after repeated
 * failures, and routing test-credential traffic through it would let a
 * wrong or abused test token lock the real production owner console out.
 * This class keeps its own, entirely independent failure window instead.
 *
 * **SHA-256 of a high-entropy token, not PBKDF2** (`ADR-079` Decision 3,
 * where the full reasoning lives) -- a deliberate divergence from
 * [OwnerCredentialGate]'s PBKDF2-HMAC-SHA256 shape. PBKDF2's iteration
 * count exists to make offline brute force expensive against a
 * human-chosen, low-entropy secret; this credential is machine-generated
 * (a >=256-bit token from a CSPRNG, base64), so offline brute force against
 * its SHA-256 digest is infeasible by entropy alone, and neither the
 * iteration cost nor a salt buys anything additional. Hashing is retained
 * anyway (over plaintext `==`) so a leaked configuration file yields an
 * unusable digest rather than a live credential.
 *
 * No table, no migration, no session, no token issuance, no rotation
 * state -- the same "no new persistent surface" property `ADR-044`
 * Decision 2 established for [OwnerCredentialGate]. The credential lives in
 * one `pios.*` configuration value, checked per request:
 * - `pios.test-data.credential-hash` -- base64(SHA-256(token))
 *
 * If unset, [verify] always returns `false` -- closed by default, the same
 * posture [OwnerCredentialGate.isConfigured] chooses in that silence
 * (`ADR-079` Decision 6).
 *
 * Brute-force resistance is deliberate and limited, exactly as
 * [OwnerCredentialGate]'s own KDoc discloses for its own throttle: a fixed
 * delay after a failed verification ([failureDelayMillis]), and a cap on
 * failures per rolling window ([maxFailuresPerWindow] within
 * [windowMillis]), held in memory in this process only -- resets on
 * restart.
 *
 * Never logs the presented token.
 */
@Component
class TestDataCredentialGate(
    @Value("\${pios.test-data.credential-hash:}") private val configuredCredentialHash: String,
    @Value("\${pios.test-data.auth.failure-delay-ms:500}") private val failureDelayMillis: Long,
    @Value("\${pios.test-data.auth.max-failures-per-window:20}") private val maxFailuresPerWindow: Int,
    @Value("\${pios.test-data.auth.window-ms:900000}") private val windowMillis: Long
) {

    private data class FailureWindow(val count: Int, val startedAt: Instant)

    private val failureWindow = AtomicReference(FailureWindow(0, Instant.now()))

    /**
     * `true` only if the test-data credential hash is configured. A caller
     * presenting a `PiosTest` credential to a module where this is `false`
     * sees a `401` indistinguishable from a wrong token -- deliberately, so
     * an unconfigured deployment fails closed rather than open.
     */
    fun isConfigured(): Boolean = configuredCredentialHash.isNotBlank()

    /**
     * Verifies [authorizationHeader] (the raw `Authorization` request
     * header value, or `null` if absent) against the configured test-data
     * credential. Returns `true` only for a correct, configured token
     * presented within the current failure-attempt budget.
     */
    fun verify(authorizationHeader: String?): Boolean {
        if (!isConfigured()) {
            return false
        }
        if (tooManyRecentFailures()) {
            return false
        }
        val valid = matchesConfiguredToken(authorizationHeader)
        return if (valid) {
            true
        } else {
            recordFailure()
            Thread.sleep(failureDelayMillis)
            false
        }
    }

    private fun tooManyRecentFailures(): Boolean {
        val current = failureWindow.get()
        val withinWindow = Duration.between(current.startedAt, Instant.now()).toMillis() < windowMillis
        return withinWindow && current.count >= maxFailuresPerWindow
    }

    private fun recordFailure() {
        failureWindow.updateAndGet { current ->
            val now = Instant.now()
            if (Duration.between(current.startedAt, now).toMillis() >= windowMillis) {
                FailureWindow(1, now)
            } else {
                FailureWindow(current.count + 1, current.startedAt)
            }
        }
    }

    private fun matchesConfiguredToken(header: String?): Boolean {
        if (header == null || !header.startsWith("PiosTest ")) {
            return false
        }
        val presentedToken = header.removePrefix("PiosTest ").trim()
        if (presentedToken.isEmpty()) {
            return false
        }
        val expected = decodeBase64OrNull(configuredCredentialHash) ?: return false
        val presentedHash = MessageDigest.getInstance("SHA-256").digest(presentedToken.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(presentedHash, expected)
    }

    private fun decodeBase64OrNull(value: String): ByteArray? =
        try {
            Base64.getDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            null
        }
}
