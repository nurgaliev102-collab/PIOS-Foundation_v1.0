package com.pios.dispatch.api

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
 * Checks the one credential PIOS's owner console presents on
 * `GET /v1/health` (ADR-044: Owner Authentication). Replicated identically
 * in each of the five pilot modules — `driver-management`,
 * `passenger-experience`, `order-management`, `dispatch`, `identity` — the
 * same shape and justification as `WebCorsConfiguration`'s own five (six,
 * counting `network-management`'s own unrelated copy) replicas: shared
 * business code between modules is forbidden (`MODULE_STRUCTURE.md`
 * Section 4), so replication is the precedented answer here, not a
 * shortcut.
 *
 * No table, no migration, no session, no token (ADR-044 Decision 2 and the
 * ADR's own "Revision 2026-08-02"). The credential lives in three `pios.*`
 * configuration values, checked per request:
 * - `pios.owner.username`
 * - `pios.owner.password-hash` — PBKDF2-HMAC-SHA256, base64
 * - `pios.owner.password-salt` — base64, generated once per deployment
 *
 * If any of the three is unset, [verify] always returns `false` — closed
 * by default, since an unconfigured owner credential must never be
 * treated as "anyone may pass." Not a case any ADR text names explicitly;
 * recorded here as the deliberate default this class chooses in that
 * silence.
 *
 * A username containing a colon can never authenticate, since the
 * `Authorization: Basic` scheme's own encoding uses the first colon as
 * the username/password separator (ADR-044 Decision 3) — this is a
 * self-enforcing deployment constraint, not something this class
 * additionally validates.
 *
 * Brute-force resistance (ADR-044 Decision 8) is deliberate and limited,
 * exactly as that Decision discloses: a fixed delay after a failed
 * verification ([failureDelayMillis]), and a cap on failures per rolling
 * window ([maxFailuresPerWindow] within [windowMillis]), held in memory in
 * this process only — resets on restart, and an attacker distributing
 * attempts across five processes gets five times the budget. Successful
 * verifications are never counted against the cap, since the console
 * polls continuously with a correct credential once logged in.
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
     * An owner console talking to a module where this is `false` sees a
     * `401` indistinguishable from a wrong password — deliberately, so a
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
