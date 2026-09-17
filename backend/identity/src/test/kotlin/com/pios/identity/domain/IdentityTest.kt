package com.pios.identity.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** ADR-082 (D-03) — the new [Identity] fields/methods, in isolation. */
class IdentityTest {
    private fun freshIdentity(): Identity = Identity(
        id = IdentityId("identity-test-1"),
        phone = Phone("+79990000000"),
        driverId = null,
        createdAt = Instant.EPOCH
    )

    @Test
    fun `phoneVerifiedAt defaults to null and sessionGeneration defaults to zero`() {
        val identity = freshIdentity()
        assertNull(identity.phoneVerifiedAt)
        assertEquals(0, identity.sessionGeneration)
    }

    @Test
    fun `withPhoneVerified sets the timestamp once`() {
        val verifiedAt = Instant.parse("2026-09-18T00:00:00Z")
        val verified = freshIdentity().withPhoneVerified(verifiedAt)
        assertEquals(verifiedAt, verified.phoneVerifiedAt)
    }

    @Test
    fun `withPhoneVerified cannot be called twice`() {
        val verified = freshIdentity().withPhoneVerified(Instant.EPOCH)
        assertFailsWith<IllegalStateException> {
            verified.withPhoneVerified(Instant.EPOCH.plusSeconds(1))
        }
    }

    @Test
    fun `withSessionGenerationBumped increments by exactly one and is repeatable`() {
        val identity = freshIdentity()
        val once = identity.withSessionGenerationBumped()
        val twice = once.withSessionGenerationBumped()
        assertEquals(1, once.sessionGeneration)
        assertEquals(2, twice.sessionGeneration)
    }

    @Test
    fun `withDriverId and withPhone preserve phoneVerifiedAt and sessionGeneration`() {
        val verifiedAt = Instant.parse("2026-09-18T00:00:00Z")
        val identity = freshIdentity().withPhoneVerified(verifiedAt).withSessionGenerationBumped()

        val withDriver = identity.withDriverId("driver-1")
        assertEquals(verifiedAt, withDriver.phoneVerifiedAt)
        assertEquals(1, withDriver.sessionGeneration)

        val withNewPhone = identity.withPhone(Phone("+79991111111"))
        assertEquals(verifiedAt, withNewPhone.phoneVerifiedAt)
        assertEquals(1, withNewPhone.sessionGeneration)
    }
}
