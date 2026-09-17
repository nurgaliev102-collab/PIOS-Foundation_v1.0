package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryCredentialRepository
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ADR-082 (D-03) — `ConfirmRecoveryApplicationService`'s own invariants,
 * in isolation against in-memory repositories. Real PostgreSQL concurrency
 * proof lives in `PhoneVerificationChallengePostgreSQLTest`.
 */
class ConfirmRecoveryApplicationServiceTest {
    private val identityRepository = InMemoryIdentityRepository()
    private val credentialRepository = InMemoryCredentialRepository()
    private val challengeRepository = InMemoryPhoneVerificationChallengeRepository()
    private val passwordHasher = PasswordHasher(defaultIterations = 1000)
    private val secret = Base64.getEncoder().encodeToString("confirm-recovery-test-secret-32bytes".toByteArray())
    private val sessionTokenIssuer = SessionTokenIssuer(secretBase64 = secret, ttlSeconds = 2_592_000)
    private val sessionTokenVerifier = com.pios.identity.api.SessionTokenVerifier(secretBase64 = secret)
    private val maxAttempts = 3
    private val service = ConfirmRecoveryApplicationService(
        identityRepository, credentialRepository, challengeRepository, passwordHasher, sessionTokenIssuer
    )

    private fun registerVerifiedIdentity(phone: String, driverId: String? = null): Identity {
        val id = IdentityId("id-${phone}")
        val identity = Identity(id, Phone(phone), driverId, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        credentialRepository.save(
            PasswordCredentialFixture.of(id, passwordHasher, "original-password-123")
        )
        return identity
    }

    private fun issueAndCapture(identityId: IdentityId, phone: Phone): String {
        var captured = ""
        val capturingIssuer = PhoneVerificationChallengeIssuer(
            challengeRepository, passwordHasher, OutboundSmsPort { _, code -> captured = code },
            ttlSeconds = 600, maxAttempts = maxAttempts, codeDigits = 6
        )
        capturingIssuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        return captured
    }

    @Test
    fun `a correct code replaces the credential, bumps generation, and mints a fresh valid token`() {
        val identity = registerVerifiedIdentity("+79990010001")
        val code = issueAndCapture(identity.id, identity.phone!!)

        val outcome = service.handle(ConfirmRecoveryCommand(identity.phone!!.value, code, "brand-new-password-1"))

        val success = assertIs<ConfirmRecoveryOutcome.Success>(outcome)
        assertEquals(identity.id, success.identity.id)
        assertEquals(1, success.identity.sessionGeneration)

        // Credential actually replaced: old password no longer works, new one does.
        val loginService = LoginApplicationService(
            identityRepository, credentialRepository, passwordHasher, sessionTokenIssuer,
            LoginRateLimiter(failureDelayMillis = 0, maxFailuresPerWindow = 1000, windowMillis = 900_000)
        )
        assertEquals(LoginOutcome.Failure, loginService.handle(LoginCommand(identity.phone!!.value, "original-password-123")))
        val relogged = loginService.handle(LoginCommand(identity.phone!!.value, "brand-new-password-1"))
        assertIs<LoginOutcome.Success>(relogged)

        // Fresh token carries the new generation and verifies.
        val verified = sessionTokenVerifier.verify("Bearer ${success.token}")!!
        assertEquals(1, verified.sgen)
    }

    @Test
    fun `identity id and driverId are unchanged by recovery`() {
        val identity = registerVerifiedIdentity("+79990010002", driverId = "driver-preserved")
        val code = issueAndCapture(identity.id, identity.phone!!)

        val success = assertIs<ConfirmRecoveryOutcome.Success>(
            service.handle(ConfirmRecoveryCommand(identity.phone!!.value, code, "brand-new-password-2"))
        )

        assertEquals(identity.id, success.identity.id)
        assertEquals("driver-preserved", success.identity.driverId)
    }

    @Test
    fun `wrong code fails generically and counts as an attempt`() {
        val identity = registerVerifiedIdentity("+79990010003")
        issueAndCapture(identity.id, identity.phone!!)

        val outcome = service.handle(ConfirmRecoveryCommand(identity.phone!!.value, "000000", "brand-new-password-3"))
        assertEquals(ConfirmRecoveryOutcome.Failure, outcome)

        val stillLive = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!
        assertEquals(1, stillLive.attemptCount)
    }

    @Test
    fun `exceeding max attempts permanently fails that challenge, even with the correct code afterward`() {
        val identity = registerVerifiedIdentity("+79990010004")
        val correctCode = issueAndCapture(identity.id, identity.phone!!)

        // maxAttempts wrong guesses exhaust the budget.
        repeat(maxAttempts) {
            val outcome = service.handle(ConfirmRecoveryCommand(identity.phone!!.value, "000000", "brand-new-password-4"))
            assertEquals(ConfirmRecoveryOutcome.Failure, outcome)
        }
        val exhausted = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!
        assertTrue(exhausted.attemptsExhausted)

        // The genuinely correct code no longer works -- attempts-exhausted
        // is checked before the compare, so this must still fail.
        val afterExhaustion = service.handle(ConfirmRecoveryCommand(identity.phone!!.value, correctCode, "brand-new-password-4"))
        assertEquals(ConfirmRecoveryOutcome.Failure, afterExhaustion)
    }

    @Test
    fun `a consumed challenge cannot be reused`() {
        val identity = registerVerifiedIdentity("+79990010005")
        val code = issueAndCapture(identity.id, identity.phone!!)

        val first = service.handle(ConfirmRecoveryCommand(identity.phone!!.value, code, "brand-new-password-5"))
        assertIs<ConfirmRecoveryOutcome.Success>(first)

        val second = service.handle(ConfirmRecoveryCommand(identity.phone!!.value, code, "another-new-password-5"))
        assertEquals(ConfirmRecoveryOutcome.Failure, second)
    }

    @Test
    fun `an unverified legacy phone cannot recover even with the correct-shaped request`() {
        val id = IdentityId("id-legacy-unverified")
        val phone = Phone("+79990010006")
        identityRepository.save(Identity(id, phone, null, Instant.EPOCH)) // phoneVerifiedAt stays null
        credentialRepository.save(PasswordCredentialFixture.of(id, passwordHasher, "legacy-password"))

        val outcome = service.handle(ConfirmRecoveryCommand(phone.value, "123456", "brand-new-password-6"))
        assertEquals(ConfirmRecoveryOutcome.Failure, outcome)
    }

    @Test
    fun `an unknown phone fails the same generic way as a wrong code`() {
        val outcome = service.handle(ConfirmRecoveryCommand("+79990019999", "123456", "brand-new-password-7"))
        assertEquals(ConfirmRecoveryOutcome.Failure, outcome)
    }

    @Test
    fun `a too-short new password is rejected before any challenge is touched`() {
        val identity = registerVerifiedIdentity("+79990010008")
        val code = issueAndCapture(identity.id, identity.phone!!)

        assertFailsWith<IllegalArgumentException> {
            service.handle(ConfirmRecoveryCommand(identity.phone!!.value, code, "short"))
        }
        // The challenge is untouched -- no attempt was recorded.
        val stillLive = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!
        assertEquals(0, stillLive.attemptCount)
        assertFalse(stillLive.consumedAt != null)
    }
}

/** Shared test fixture: a real, hashed [PasswordCredential] for a given plaintext. */
internal object PasswordCredentialFixture {
    fun of(identityId: IdentityId, hasher: PasswordHasher, password: String): com.pios.identity.domain.PasswordCredential {
        val hashed = hasher.hash(password)
        return com.pios.identity.domain.PasswordCredential(identityId, hashed.hash, hashed.salt, hashed.iterations, Instant.EPOCH)
    }
}
