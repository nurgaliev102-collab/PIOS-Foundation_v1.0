package com.pios.drivermanagement.api

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves [OwnerCredentialGate.verify] (ADR-044) without a Spring context or
 * a database, mirroring this project's own constructor-based testing
 * convention. A low [iterations] value is used throughout so these tests
 * run fast; the algorithm exercised is identical regardless of count.
 */
class OwnerCredentialGateTest {

    private val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val iterations = 1000
    private val password = "correct-horse-battery-staple"
    private val passwordHash = deriveKey(password, salt, iterations)

    private fun gate(
        username: String = "owner",
        passwordHashBytes: ByteArray = passwordHash,
        saltBytes: ByteArray = salt,
        iterations: Int = this.iterations,
        failureDelayMillis: Long = 0,
        maxFailuresPerWindow: Int = 1000,
        windowMillis: Long = 900_000
    ) = OwnerCredentialGate(
        configuredUsername = username,
        configuredPasswordHash = Base64.getEncoder().encodeToString(passwordHashBytes),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(saltBytes),
        iterations = iterations,
        failureDelayMillis = failureDelayMillis,
        maxFailuresPerWindow = maxFailuresPerWindow,
        windowMillis = windowMillis
    )

    private fun basicHeader(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    @Test
    fun `a correct credential verifies`() {
        assertTrue(gate().verify(basicHeader("owner", password)))
    }

    @Test
    fun `an incorrect password does not verify`() {
        assertFalse(gate().verify(basicHeader("owner", "wrong-password")))
    }

    @Test
    fun `an unknown username does not verify`() {
        assertFalse(gate().verify(basicHeader("someone-else", password)))
    }

    @Test
    fun `a missing Authorization header does not verify`() {
        assertFalse(gate().verify(null))
    }

    @Test
    fun `a non-Basic scheme does not verify`() {
        assertFalse(gate().verify("Bearer some-token"))
    }

    @Test
    fun `a header that is not valid base64 does not verify`() {
        assertFalse(gate().verify("Basic not-valid-base64!!"))
    }

    @Test
    fun `an unconfigured gate never verifies, even with what would otherwise be the correct credential`() {
        val unconfigured = gate(username = "", passwordHashBytes = ByteArray(0), saltBytes = ByteArray(0))

        assertFalse(unconfigured.verify(basicHeader("owner", password)))
        assertFalse(unconfigured.isConfigured())
    }

    @Test
    fun `a fully configured gate reports itself configured`() {
        assertTrue(gate().isConfigured())
    }

    @Test
    fun `failures beyond the cap are denied even when the credential presented is correct`() {
        val limited = gate(maxFailuresPerWindow = 2, failureDelayMillis = 0)
        limited.verify(basicHeader("owner", "wrong-1"))
        limited.verify(basicHeader("owner", "wrong-2"))

        assertFalse(limited.verify(basicHeader("owner", password)))
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
