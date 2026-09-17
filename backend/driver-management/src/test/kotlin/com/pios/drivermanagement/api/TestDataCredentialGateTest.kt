package com.pios.drivermanagement.api

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves [TestDataCredentialGate.verify] (ADR-079) without a Spring context
 * or a database, mirroring [OwnerCredentialGateTest]'s own
 * constructor-based testing convention.
 */
class TestDataCredentialGateTest {

    private val token = "correct-test-data-token"
    private val credentialHash = sha256(token)

    private fun gate(
        credentialHashBytes: ByteArray = credentialHash,
        failureDelayMillis: Long = 0,
        maxFailuresPerWindow: Int = 1000,
        windowMillis: Long = 900_000
    ) = TestDataCredentialGate(
        configuredCredentialHash = Base64.getEncoder().encodeToString(credentialHashBytes),
        failureDelayMillis = failureDelayMillis,
        maxFailuresPerWindow = maxFailuresPerWindow,
        windowMillis = windowMillis
    )

    private fun testDataHeader(presentedToken: String): String = "PiosTest $presentedToken"

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    @Test
    fun `a correct token verifies`() {
        assertTrue(gate().verify(testDataHeader(token)))
    }

    @Test
    fun `an incorrect token does not verify`() {
        assertFalse(gate().verify(testDataHeader("wrong-token")))
    }

    @Test
    fun `a missing Authorization header does not verify`() {
        assertFalse(gate().verify(null))
    }

    @Test
    fun `a non-PiosTest scheme does not verify`() {
        assertFalse(gate().verify("Bearer $token"))
        assertFalse(gate().verify("Basic ${Base64.getEncoder().encodeToString("owner:$token".toByteArray())}"))
    }

    @Test
    fun `an empty token after the scheme does not verify`() {
        assertFalse(gate().verify("PiosTest "))
        assertFalse(gate().verify("PiosTest"))
    }

    @Test
    fun `an unconfigured gate never verifies, even with what would otherwise be the correct token`() {
        val unconfigured = gate(credentialHashBytes = ByteArray(0))

        assertFalse(unconfigured.verify(testDataHeader(token)))
        assertFalse(unconfigured.isConfigured())
    }

    @Test
    fun `a fully configured gate reports itself configured`() {
        assertTrue(gate().isConfigured())
    }

    @Test
    fun `a header that is not valid base64 configuration never verifies`() {
        val brokenConfig = TestDataCredentialGate(
            configuredCredentialHash = "not-valid-base64!!",
            failureDelayMillis = 0,
            maxFailuresPerWindow = 1000,
            windowMillis = 900_000
        )

        assertFalse(brokenConfig.verify(testDataHeader(token)))
    }

    @Test
    fun `failures beyond the cap are denied even when the token presented is correct`() {
        val limited = gate(maxFailuresPerWindow = 2, failureDelayMillis = 0)
        limited.verify(testDataHeader("wrong-1"))
        limited.verify(testDataHeader("wrong-2"))

        assertFalse(limited.verify(testDataHeader(token)))
    }
}
