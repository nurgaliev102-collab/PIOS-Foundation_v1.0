package com.pios.aiadvisor.api

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
 * Checks the same owner credential the five pilot modules' own
 * `OwnerCredentialGate` copies already check (ADR-044: Owner
 * Authentication), replicated into this sixth process rather than shared —
 * `MODULE_STRUCTURE.md` Section 4 forbids shared business code between
 * modules, and the five existing copies already established replication as
 * this repository's own answer to that constraint. ADR-056 Decision 4:
 * this is deliberately **not** a second, independent secret the way
 * `platform-ops`'s own `OpsCredentialGate` is for `pios.ops.*` — the AI
 * Advisor is read-only and answers to the same owner who already reads
 * `/v1/health`, so it checks the same `pios.owner.*` values, not a new
 * credential.
 *
 * No table, no migration, no session, no token (ADR-044 Decision 2). The
 * credential lives in three `pios.owner.*` configuration values, checked
 * per request — field-for-field identical to every existing copy (e.g.
 * `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/OwnerCredentialGate.kt`).
 *
 * Never logs the presented credential, and never distinguishes "unknown
 * username" from "wrong password" in what it returns — both simply
 * produce `false` (ADR-044 Decision 3).
 */
@Component
class OwnerCredentialGate(
    @Value("\${pios.owner.username:}") private val configuredUsername: String,
    @Value("\${pios.owner.password-hash:}") private val configuredPasswordHash: String,
    @Value("\${pios.owner.password-salt:}") private val configuredPasswordSalt: String,
    @Value("\${pios.owner.password-iterations:210000}") private val iterations: Int,
    @Value("\${pios.owner.auth.failure-delay-ms:500}") private val failureDelayMillis: Long,
    @Value("\${pios.owner.auth.max-failures-per-window:20}") private val maxFailuresPerWindow: Int,
    @Value("\${pios.owner.auth.window-ms:900000}") private val windowMillis: Long
) {

    private data class FailureWindow(val count: Int, val startedAt: Instant)

    private val failureWindow = AtomicReference(FailureWindow(0, Instant.now()))

    /**
     * `true` only if the three required configuration values are present.
     * A caller reaching this deployable while this is `false` sees a `401`
     * indistinguishable from a wrong password — deliberately, so a
     * misconfigured deployment fails closed rather than open.
     */
    fun isConfigured(): Boolean =
        configuredUsername.isNotBlank() && configuredPasswordHash.isNotBlank() && configuredPasswordSalt.isNotBlank()

    /**
     * Verifies [authorizationHeader] (the raw `Authorization` request
     * header value, or `null` if absent) against the configured owner
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
