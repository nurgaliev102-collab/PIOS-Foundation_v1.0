package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestPhoneVerificationApplicationServiceTest {
    private val identityRepository = InMemoryIdentityRepository()
    private val challengeRepository = InMemoryPhoneVerificationChallengeRepository()
    private val challengeIssuer = PhoneVerificationChallengeIssuer(
        challengeRepository, PasswordHasher(defaultIterations = 1000), OutboundSmsPort { _, _ -> },
        ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
    )
    private val service = RequestPhoneVerificationApplicationService(identityRepository, challengeIssuer)

    @Test
    fun `an unknown identity throws IdentityNotFoundException`() {
        assertFailsWith<IdentityNotFoundException> {
            service.handle(IdentityId("never-registered"))
        }
    }

    @Test
    fun `an already-verified identity throws PhoneAlreadyVerifiedException and issues nothing new`() {
        val id = IdentityId("already-verified")
        identityRepository.save(Identity(id, Phone("+79996660001"), null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH))

        assertFailsWith<PhoneAlreadyVerifiedException> { service.handle(id) }
    }

    @Test
    fun `an unverified legacy identity with a phone on file becomes eligible to issue a LEGACY_ENROLLMENT challenge`() {
        val id = IdentityId("legacy-to-enroll")
        identityRepository.save(Identity(id, Phone("+79996660002"), null, Instant.EPOCH))

        service.handle(id)

        val live = challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.LEGACY_ENROLLMENT)!!
        assertTrue(live.isLive)
    }

    @Test
    fun `issuing a LEGACY_ENROLLMENT challenge never creates a RECOVERY one`() {
        val id = IdentityId("legacy-to-enroll-2")
        identityRepository.save(Identity(id, Phone("+79996660003"), null, Instant.EPOCH))

        service.handle(id)

        assertTrue(challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.RECOVERY) == null)
    }
}
