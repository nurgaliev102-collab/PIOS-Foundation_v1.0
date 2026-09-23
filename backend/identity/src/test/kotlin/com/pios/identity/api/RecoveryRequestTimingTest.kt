package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.ConfirmPhoneVerificationApplicationService
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.CreateGuestIdentityApplicationService
import com.pios.identity.application.GuestIdentityRateLimiter
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginRateLimiter
import com.pios.identity.application.OtpClientKeyRateLimiter
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.PhoneOtpRequestRateLimiter
import com.pios.identity.application.PhoneVerificationChallengeIssuer
import com.pios.identity.application.RequestPhoneVerificationApplicationService
import com.pios.identity.application.RequestRecoveryApplicationService
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.application.UpgradeGuestIdentityApplicationService
import com.pios.identity.application.testOtpCipher
import com.pios.identity.application.testSmsOutbox
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryCredentialRepository
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecoveryRequestTimingTest {
    @Test
    fun `monotonic timing pads only the remaining floor and never delays an overrun`() {
        var nanos = 1_000_000_000L
        val sleeps = mutableListOf<Long>()
        val timing = MonotonicRecoveryRequestTiming({ nanos }, { sleeps += it })

        val started = timing.startedAtNanos()
        nanos += 250_000_000L
        timing.padToFloor(started, 1_000)
        nanos += 1_100_000_000L
        timing.padToFloor(started, 1_000)

        assertEquals(listOf(750L), sleeps)
    }

    @Test
    fun `valid unknown unverified and eligible recovery requests are padded while malformed and limited requests are not`() {
        val fixture = Fixture()

        fixture.controller.requestRecoveryFromAddress("+79995551001", "198.51.100.1", null)
        assertEquals(listOf(1_000L), fixture.timing.floors)

        fixture.timing.reset()
        val unverifiedPhone = Phone("+79995551002")
        fixture.identities.save(Identity(IdentityId("unverified"), unverifiedPhone, null, Instant.EPOCH))
        fixture.controller.requestRecoveryFromAddress(unverifiedPhone.value, "198.51.100.2", null)
        assertEquals(listOf(1_000L), fixture.timing.floors)
        assertNull(fixture.challenges.findLiveForUpdate(IdentityId("unverified"), PhoneVerificationPurpose.RECOVERY))

        fixture.timing.reset()
        val eligiblePhone = Phone("+79995551003")
        val eligible = Identity(IdentityId("eligible"), eligiblePhone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        fixture.identities.save(eligible)
        fixture.controller.requestRecoveryFromAddress(eligiblePhone.value, "198.51.100.3", null)
        assertEquals(listOf(1_000L), fixture.timing.floors)
        val challenge = assertNotNull(fixture.challenges.findLiveForUpdate(eligible.id, PhoneVerificationPurpose.RECOVERY))
        assertNotNull(fixture.outbox.findByChallengeId(challenge.id))

        fixture.timing.reset()
        fixture.controller.requestRecoveryFromAddress("not-a-phone", "198.51.100.4", null)
        assertEquals(emptyList(), fixture.timing.floors)

        val limited = Fixture(phoneLimit = 1)
        limited.controller.requestRecoveryFromAddress("+79995551004", "198.51.100.5", null)
        assertEquals(listOf(1_000L), limited.timing.floors)
        limited.timing.reset()
        limited.controller.requestRecoveryFromAddress("+79995551004", "198.51.100.5", null)
        assertEquals(emptyList(), limited.timing.floors)
    }

    @Test
    fun `application yaml declares the provisional one-second timing floor`() {
        val yaml = YamlPropertiesFactoryBean().apply {
            setResources(ClassPathResource("application.yml"))
            afterPropertiesSet()
        }
        val properties = yaml.getObject()!!
        assertEquals("1000", properties.getProperty("pios.identity.recovery.timing.floor-ms"))
    }

    private class RecordingTiming : RecoveryRequestTiming {
        val floors = mutableListOf<Long>()
        override fun startedAtNanos(): Long = 123L
        override fun padToFloor(startedAtNanos: Long, floorMillis: Long) {
            floors += floorMillis
        }
        fun reset() = floors.clear()
    }

    private class Fixture(phoneLimit: Int = 100) {
        val identities = InMemoryIdentityRepository()
        private val credentials = InMemoryCredentialRepository()
        val challenges = InMemoryPhoneVerificationChallengeRepository()
        val outbox = testSmsOutbox(challenges)
        val passwordHasher = PasswordHasher(defaultIterations = 1_000)
        private val secret = Base64.getEncoder().encodeToString(ByteArray(32) { 5 })
        private val issuer = PhoneVerificationChallengeIssuer(
            challenges, passwordHasher, testOtpCipher(), outbox,
            ttlSeconds = 600, maxAttempts = 10, codeDigits = 6
        )
        val timing = RecordingTiming()
        private val sessionIssuer = SessionTokenIssuer(secret, 2_592_000)
        private val sessionVerifier = SessionTokenVerifier(secret)
        private val phoneLimiter = PhoneOtpRequestRateLimiter(phoneLimit, 3_600_000)
        private val clientLimiter = OtpClientKeyRateLimiter(100, 3_600_000)
        val controller = IdentityController(
            com.pios.identity.application.RegisterIdentityApplicationService(identities, credentials, passwordHasher, sessionIssuer),
            LoginApplicationService(identities, credentials, passwordHasher, sessionIssuer, LoginRateLimiter(0, 100, 3_600_000)),
            RetrieveIdentityHandler(identities), AssociateDriverApplicationService(identities, sessionIssuer), sessionVerifier,
            CreateGuestIdentityApplicationService(identities, sessionIssuer), GuestIdentityRateLimiter(100, 3_600_000),
            UpgradeGuestIdentityApplicationService(identities, credentials, passwordHasher, sessionIssuer),
            RequestRecoveryApplicationService(identities, issuer, phoneLimiter, clientLimiter),
            ConfirmRecoveryApplicationService(identities, credentials, challenges, passwordHasher, sessionIssuer),
            RequestPhoneVerificationApplicationService(identities, issuer, phoneLimiter),
            ConfirmPhoneVerificationApplicationService(identities, challenges, passwordHasher),
            clientLimiter, timing, 1_000
        )
    }
}
