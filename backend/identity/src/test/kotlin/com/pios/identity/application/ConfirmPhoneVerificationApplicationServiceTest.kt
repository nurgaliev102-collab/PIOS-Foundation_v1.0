package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ADR-082 Part 2 (D-03.2, D-03.6 item 8) — legacy enrolment's confirm
 * step: proves a phone already on file, sets `phoneVerifiedAt`, and
 * nothing else -- no credential change, no driver association, no
 * sessionGeneration bump.
 */
class ConfirmPhoneVerificationApplicationServiceTest {
    private val identityRepository = InMemoryIdentityRepository()
    private val challengeRepository = InMemoryPhoneVerificationChallengeRepository()
    private val passwordHasher = PasswordHasher(defaultIterations = 1000)
    private val service = ConfirmPhoneVerificationApplicationService(identityRepository, challengeRepository, passwordHasher)

    private fun issueAndCapture(id: IdentityId, phone: Phone): String {
        PhoneVerificationChallengeIssuer(
            challengeRepository, passwordHasher, testOtpCipher(), testSmsOutbox(challengeRepository),
            ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
        ).issue(id, phone, PhoneVerificationPurpose.LEGACY_ENROLLMENT)
        val challenge = challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.LEGACY_ENROLLMENT)!!
        return testOtpCipher().decrypt(challenge.otpCiphertext!!, challenge.otpNonce!!)
    }

    @Test
    fun `a correct code verifies the phone and nothing else`() {
        val id = IdentityId("legacy-confirm-1")
        val phone = Phone("+79997770001")
        identityRepository.save(Identity(id, phone, "driver-untouched", Instant.EPOCH))
        val code = issueAndCapture(id, phone)

        val outcome = service.handle(ConfirmPhoneVerificationCommand(id.value, code))

        val success = assertIs<ConfirmPhoneVerificationOutcome.Success>(outcome)
        assertNotNull(success.identity.phoneVerifiedAt)
        assertEquals("driver-untouched", success.identity.driverId, "confirm must never touch driverId")
        assertEquals(0, success.identity.sessionGeneration, "confirm must never bump sessionGeneration")
    }

    @Test
    fun `a wrong code fails and leaves the phone unverified`() {
        val id = IdentityId("legacy-confirm-2")
        val phone = Phone("+79997770002")
        identityRepository.save(Identity(id, phone, null, Instant.EPOCH))
        issueAndCapture(id, phone)

        val outcome = service.handle(ConfirmPhoneVerificationCommand(id.value, "000000"))

        assertEquals(ConfirmPhoneVerificationOutcome.Failure, outcome)
        assertNull(identityRepository.findById(id)!!.phoneVerifiedAt)
    }

    @Test
    fun `an unknown identity fails generically rather than throwing`() {
        val outcome = service.handle(ConfirmPhoneVerificationCommand("never-registered", "123456"))
        assertEquals(ConfirmPhoneVerificationOutcome.Failure, outcome)
    }

    @Test
    fun `a stale presented generation throws StaleSessionException`() {
        val id = IdentityId("legacy-confirm-3")
        val phone = Phone("+79997770003")
        identityRepository.save(Identity(id, phone, null, Instant.EPOCH, sessionGeneration = 2))
        val code = issueAndCapture(id, phone)

        assertFailsWith<StaleSessionException> {
            service.handle(ConfirmPhoneVerificationCommand(id.value, code, presentedGeneration = 0))
        }
    }
}
