package com.pios.identity.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneVerificationChallengeTest {
    private fun challenge(
        attemptCount: Int = 0,
        maxAttempts: Int = 5,
        expiresAt: Instant = Instant.EPOCH.plusSeconds(600),
        consumedAt: Instant? = null,
        supersededAt: Instant? = null
    ) = PhoneVerificationChallenge(
        id = "challenge-1",
        identityId = IdentityId("identity-1"),
        phone = Phone("+79990000000"),
        purpose = PhoneVerificationPurpose.RECOVERY,
        codeHash = "hash",
        codeSalt = "salt",
        iterations = 1000,
        attemptCount = attemptCount,
        maxAttempts = maxAttempts,
        createdAt = Instant.EPOCH,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
        supersededAt = supersededAt
    )

    @Test
    fun `a fresh challenge is live`() {
        assertTrue(challenge().isLive)
    }

    @Test
    fun `a consumed challenge is not live`() {
        assertFalse(challenge(consumedAt = Instant.EPOCH).isLive)
    }

    @Test
    fun `a superseded challenge is not live`() {
        assertFalse(challenge(supersededAt = Instant.EPOCH).isLive)
    }

    @Test
    fun `isExpired is true at and after expiresAt, false before it`() {
        val c = challenge(expiresAt = Instant.EPOCH.plusSeconds(600))
        assertFalse(c.isExpired(Instant.EPOCH.plusSeconds(599)))
        assertTrue(c.isExpired(Instant.EPOCH.plusSeconds(600)))
        assertTrue(c.isExpired(Instant.EPOCH.plusSeconds(601)))
    }

    @Test
    fun `attemptsExhausted is true once attemptCount reaches maxAttempts`() {
        assertFalse(challenge(attemptCount = 4, maxAttempts = 5).attemptsExhausted)
        assertTrue(challenge(attemptCount = 5, maxAttempts = 5).attemptsExhausted)
        assertTrue(challenge(attemptCount = 6, maxAttempts = 5).attemptsExhausted)
    }
}
