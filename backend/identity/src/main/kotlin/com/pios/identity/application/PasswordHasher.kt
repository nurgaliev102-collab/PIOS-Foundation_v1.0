package com.pios.identity.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes and verifies [com.pios.identity.domain.PasswordCredential]
 * passwords (ADR-055 Decision 2) — the exact PBKDF2WithHmacSHA256
 * algorithm and default iteration count
 * `com.pios.identity.api.OwnerCredentialGate` already uses. The one
 * difference: [hash] generates a fresh, random, **per-credential** salt on
 * every call, rather than reading one fixed per-deployment salt from
 * configuration, per ADR-055 Decision 2's own explicit instruction.
 *
 * [defaultIterations] is only ever used when hashing a *new* password.
 * [matches] always verifies against the iteration count the presented
 * [PasswordCredential] row itself carries — never [defaultIterations] —
 * so a future deployment can raise the default without invalidating
 * passwords already hashed under a lower count.
 */
@Component
class PasswordHasher(
    @Value("\${pios.identity.password-iterations:210000}") private val defaultIterations: Int
) {

    data class HashResult(val hash: String, val salt: String, val iterations: Int)

    /**
     * Hashes [password] with a freshly generated 16-byte salt and
     * [defaultIterations].
     */
    fun hash(password: String): HashResult {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(password.toCharArray(), salt, defaultIterations, KEY_LENGTH_BITS)
        return HashResult(
            hash = Base64.getEncoder().encodeToString(derived),
            salt = Base64.getEncoder().encodeToString(salt),
            iterations = defaultIterations
        )
    }

    /**
     * Verifies [password] against a previously stored [hash]/[salt]/[iterations]
     * triple (all base64 except [iterations]), using a constant-time
     * comparison ([MessageDigest.isEqual], as `OwnerCredentialGate`
     * already does). Malformed base64 in [hash] or [salt] is treated as a
     * non-match, never an exception.
     */
    fun matches(password: String, hash: String, salt: String, iterations: Int): Boolean {
        val saltBytes = decodeBase64OrNull(salt) ?: return false
        val expected = decodeBase64OrNull(hash) ?: return false
        val derived = deriveKey(password.toCharArray(), saltBytes, iterations, expected.size * 8)
        return MessageDigest.isEqual(derived, expected)
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

    companion object {
        private const val KEY_LENGTH_BITS = 256
    }
}
