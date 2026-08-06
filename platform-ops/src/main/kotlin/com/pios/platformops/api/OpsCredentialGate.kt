package com.pios.platformops.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Checks the operations credential every endpoint under `/v1/ops/` (and any
 * of its sub-paths) requires, including reads (ADR-047 Decision 1: "every
 * endpoint, including read ones, is behind the ops credential").
 *
 * **Deliberately not shared with, nor derived from, any PIOS module's own
 * `OwnerCredentialGate`** (e.g.
 * `backend/identity/.../api/OwnerCredentialGate.kt`). Two independent
 * reasons, both load-bearing:
 * - `MODULE_STRUCTURE.md` Section 4 forbids shared business code between
 *   modules, and the five modules' own `OwnerCredentialGate` copies already
 *   established replication, not extraction, as this repository's answer
 *   to that constraint (see that class's own KDoc).
 * - ADR-047 Decision 5 (mirroring ADR-046 Decision 3): this deployable has
 *   no client for any module's API, and must not come to depend on one --
 *   importing a class from `backend/` would create exactly that dependency
 *   even if the class itself never made a network call.
 *
 * The credential itself is a **second, independent secret** from the
 * owner's (ADR-047 Decision 1): compromising the observation password must
 * not grant any ability to act. It lives in three `pios.ops.*`
 * configuration values, checked per request, the same shape
 * `OwnerCredentialGate` already established for `pios.owner.*`:
 * - `pios.ops.username`
 * - `pios.ops.password-hash` -- PBKDF2-HMAC-SHA256, base64
 * - `pios.ops.password-salt` -- base64, generated once per deployment
 *
 * If any of the three is unset, [verify] always returns `false` -- closed
 * by default, exactly as `OwnerCredentialGate.isConfigured()` already
 * chose for the same silence in ADR-044's own text.
 *
 * Presenting the **owner's** credential here fails by construction, not by
 * a special check: it is verified against `pios.ops.*`, a value the owner
 * credential was never generated against, so the two are only ever equal
 * by deployment accident. T-3's Definition of Done requires this be proven
 * with an explicit negative test, not assumed from this reasoning.
 */
@Component
class OpsCredentialGate(
    @Value("\${pios.ops.username:}") private val configuredUsername: String,
    @Value("\${pios.ops.password-hash:}") private val configuredPasswordHash: String,
    @Value("\${pios.ops.password-salt:}") private val configuredPasswordSalt: String,
    @Value("\${pios.ops.password-iterations:210000}") private val iterations: Int,
    @Value("\${pios.ops.auth.failure-delay-ms:500}") private val failureDelayMillis: Long,
    @Value("\${pios.ops.auth.max-failures-per-window:20}") private val maxFailuresPerWindow: Int,
    @Value("\${pios.ops.auth.window-ms:900000}") private val windowMillis: Long
) {

    private data class FailureWindow(val count: Int, val startedAt: Instant)

    private val failureWindow = AtomicReference(FailureWindow(0, Instant.now()))

    /**
     * `true` only if the three required configuration values are present.
     * A caller reaching this deployable while this is `false` sees a `401`
     * indistinguishable from a wrong password -- a misconfigured
     * deployment fails closed rather than open.
     */
    fun isConfigured(): Boolean =
        configuredUsername.isNotBlank() && configuredPasswordHash.isNotBlank() && configuredPasswordSalt.isNotBlank()

    /**
     * Verifies [authorizationHeader] (the raw `Authorization` request
     * header value, or `null` if absent) against the configured operations
     * credential. Returns `true` only for a correct, fully-configured
     * credential presented within the current failure-attempt budget.
     */
    fun verify(authorizationHeader: String?): Boolean {
        if (!isConfigured()) {
            return false
        }
        if (tooManyRecentFailures()) {
            return false
        }
        val credential = decodeBasicCredential(authorizationHeader)
        val valid = credential != null && credential.first == configuredUsername && matchesConfiguredPassword(credential.second)
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

    private fun matchesConfiguredPassword(presentedPassword: String): Boolean {
        val salt = decodeBase64OrNull(configuredPasswordSalt) ?: return false
        val expected = decodeBase64OrNull(configuredPasswordHash) ?: return false
        val derived = deriveKey(presentedPassword.toCharArray(), salt, iterations, expected.size * 8)
        return MessageDigest.isEqual(derived, expected)
    }

    private fun decodeBasicCredential(header: String?): Pair<String, String>? {
        if (header == null || !header.startsWith("Basic ")) {
            return null
        }
        val decodedBytes = decodeBase64OrNull(header.removePrefix("Basic ").trim()) ?: return null
        val decoded = String(decodedBytes, Charsets.UTF_8)
        val separatorIndex = decoded.indexOf(':')
        if (separatorIndex < 0) {
            return null
        }
        return decoded.substring(0, separatorIndex) to decoded.substring(separatorIndex + 1)
    }

    private fun decodeBase64OrNull(value: String): ByteArray? =
        try {
            Base64.getDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            null
        }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
