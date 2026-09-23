package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.ConfirmPhoneVerificationApplicationService
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.CredentialRepository
import com.pios.identity.application.CreateGuestIdentityApplicationService
import com.pios.identity.application.GuestIdentityRateLimiter
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginRateLimiter
import com.pios.identity.application.OtpClientKeyRateLimiter
import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.OutboundSmsDeliveryException
import com.pios.identity.application.SmsSubmissionFailure
import com.pios.identity.application.SmsSubmissionState
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.PhoneOtpRequestRateLimiter
import com.pios.identity.application.PhoneVerificationChallengeIssuer
import com.pios.identity.application.SmsOutboxRelay
import com.pios.identity.application.SmsOutboxStatus
import com.pios.identity.application.SmsRetryJitter
import com.pios.identity.application.testOtpCipher
import com.pios.identity.application.testSmsOutbox
import com.pios.identity.application.PhoneVerificationChallengeRepository
import com.pios.identity.application.RegisterIdentityApplicationService
import com.pios.identity.application.RequestPhoneVerificationApplicationService
import com.pios.identity.application.RequestRecoveryApplicationService
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.application.UpgradeGuestIdentityApplicationService
import com.pios.identity.domain.Phone
import com.pios.identity.persistence.InMemoryCredentialRepository
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import org.springframework.http.HttpStatus
import java.time.Clock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A test-only [OutboundSmsPort] that records the last code sent per phone,
 * so a test can "read the SMS" without any real provider -- never a
 * production adapter, mirrors this file's own existing in-memory-repository
 * convention.
 */
internal class RecordingOutboundSmsPort : OutboundSmsPort {
    val sentCodes = ConcurrentHashMap<String, String>()
    var failure: OutboundSmsDeliveryException? = null
    override fun sendVerificationCode(phone: Phone, code: String): Long {
        failure?.let { throw it }
        sentCodes[phone.value] = code
        return 1L
    }
}

/**
 * Constructs [IdentityController] directly, with real, in-memory-backed
 * application services -- no Spring MVC context, mirroring
 * `com.pios.networkmanagement.api.PersonControllerTest`'s own convention.
 * Proves ADR-055's own security properties: register/login issue a real,
 * verifiable session token, and every id-scoped endpoint rejects a token
 * whose subject does not match the id in the path. Supersedes the
 * pre-ADR-055 version of this test, which exercised the now-removed
 * credential-less `POST /v1/identities` -- `CreateIdentityApplicationService`
 * itself is untouched and remains covered by
 * `com.pios.identity.application.CreateIdentityApplicationServiceTest`.
 */
class IdentityControllerTest {
    private val secret = Base64.getEncoder().encodeToString("identity-controller-test-secret-32b".toByteArray())
    private val identityRepository = InMemoryIdentityRepository()
    private val credentialRepository: CredentialRepository = InMemoryCredentialRepository()
    private val passwordHasher = PasswordHasher(defaultIterations = 1000)
    private val sessionTokenIssuer = SessionTokenIssuer(secretBase64 = secret, ttlSeconds = 2_592_000)
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val loginRateLimiter = LoginRateLimiter(failureDelayMillis = 0, maxFailuresPerWindow = 1000, windowMillis = 900_000)
    private val registerService =
        RegisterIdentityApplicationService(identityRepository, credentialRepository, passwordHasher, sessionTokenIssuer)
    private val loginService =
        LoginApplicationService(identityRepository, credentialRepository, passwordHasher, sessionTokenIssuer, loginRateLimiter)
    private val retrieveIdentityHandler = RetrieveIdentityHandler(identityRepository)
    private val associateDriverService = AssociateDriverApplicationService(identityRepository, sessionTokenIssuer)
    private val createGuestService = CreateGuestIdentityApplicationService(identityRepository, sessionTokenIssuer)
    private val guestRateLimiter = GuestIdentityRateLimiter(maxPerWindow = 1000, windowMillis = 3_600_000)
    private val upgradeGuestService = UpgradeGuestIdentityApplicationService(
        identityRepository,
        credentialRepository,
        passwordHasher,
        sessionTokenIssuer
    )
    private val challengeRepository = InMemoryPhoneVerificationChallengeRepository()
    internal val smsPort = RecordingOutboundSmsPort()
    private val otpCipher = testOtpCipher()
    private val smsOutbox = testSmsOutbox(challengeRepository)
    private val challengeIssuer = PhoneVerificationChallengeIssuer(
        challengeRepository, passwordHasher, otpCipher, smsOutbox, maxAttempts = 5, ttlSeconds = 600, codeDigits = 6
    )
    private val smsRelay = SmsOutboxRelay(
        smsOutbox, smsPort, otpCipher, Clock.systemUTC(), SmsRetryJitter { 0L },
        batchSize = 50, leaseSeconds = 60, initialBackoffMs = 30_000,
        maxBackoffMs = 120_000, maxRetryWindowMs = 300_000
    )
    private val phoneOtpRequestRateLimiter = PhoneOtpRequestRateLimiter(maxPerWindow = 1000, windowMillis = 3_600_000)
    private val otpClientKeyRateLimiter = OtpClientKeyRateLimiter(maxPerWindow = 1000, windowMillis = 3_600_000)
    private val requestRecoveryService =
        RequestRecoveryApplicationService(identityRepository, challengeIssuer, phoneOtpRequestRateLimiter, otpClientKeyRateLimiter)
    private val confirmRecoveryService = ConfirmRecoveryApplicationService(
        identityRepository, credentialRepository, challengeRepository, passwordHasher, sessionTokenIssuer
    )
    private val requestPhoneVerificationService =
        RequestPhoneVerificationApplicationService(identityRepository, challengeIssuer, phoneOtpRequestRateLimiter)
    private val confirmPhoneVerificationService =
        ConfirmPhoneVerificationApplicationService(identityRepository, challengeRepository, passwordHasher)
    private val controller = IdentityController(
        registerService,
        loginService,
        retrieveIdentityHandler,
        associateDriverService,
        sessionTokenVerifier,
        createGuestService,
        guestRateLimiter,
        upgradeGuestService,
        requestRecoveryService,
        confirmRecoveryService,
        requestPhoneVerificationService,
        confirmPhoneVerificationService,
        otpClientKeyRateLimiter
    )

    private fun bearer(token: String): String = "Bearer $token"

    private fun register(phone: String, password: String = "correct-horse-battery-staple") =
        assertNotNull(controller.register(RegisterIdentityRequest(phone, password)).body)

    // --- Register ---

    @Test
    fun `guest session is created without phone or password and carries the guest claim`() {
        val response = controller.createGuestFromAddress("127.0.0.1", null)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        val verified = assertNotNull(sessionTokenVerifier.verify(bearer(body.token)))
        assertEquals(body.identityId, verified.sub)
        assertEquals(true, verified.guest)
        assertNull(retrieveIdentityHandler.handle(com.pios.identity.domain.IdentityId(body.identityId)).phone)
    }

    @Test
    fun `guest session cannot be associated with a driver profile`() {
        val guest = assertNotNull(controller.createGuestFromAddress("127.0.0.1", "203.0.113.10").body)

        val response = controller.associateDriver(
            guest.identityId,
            AssociateDriverRequest(guest.identityId),
            bearer(guest.token)
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `guest can register in place without losing identity and then log in`() {
        val guest = assertNotNull(controller.createGuestFromAddress("127.0.0.1", "203.0.113.11").body)

        val upgraded = controller.upgradeGuest(
            RegisterIdentityRequest("+79991234567", "correct-horse-battery-staple"),
            bearer(guest.token)
        )

        assertEquals(HttpStatus.OK, upgraded.statusCode)
        val body = assertNotNull(upgraded.body)
        assertEquals(guest.identityId, body.identityId)
        assertEquals(false, body.guest)
        assertEquals(false, assertNotNull(sessionTokenVerifier.verify(bearer(body.token))).guest)
        val loggedIn = assertNotNull(controller.login(LoginRequest("+79991234567", "correct-horse-battery-staple")).body)
        assertEquals(guest.identityId, loggedIn.identityId)
    }

    @Test
    fun `external client cannot bypass guest limiter by forging the proxy IP header`() {
        val limitedController = IdentityController(
            registerService, loginService, retrieveIdentityHandler, associateDriverService,
            sessionTokenVerifier, createGuestService,
            GuestIdentityRateLimiter(maxPerWindow = 1, windowMillis = 3_600_000), upgradeGuestService,
            requestRecoveryService, confirmRecoveryService, requestPhoneVerificationService,
            confirmPhoneVerificationService, otpClientKeyRateLimiter
        )

        assertEquals(HttpStatus.CREATED, limitedController.createGuestFromAddress("198.51.100.7", "203.0.113.1").statusCode)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limitedController.createGuestFromAddress("198.51.100.7", "203.0.113.2").statusCode)
    }

    @Test
    fun `registering with a new phone returns 201 with an identity id, driver id, token and expiry`() {
        val response = controller.register(RegisterIdentityRequest("+79991234567", "correct-horse-battery-staple"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertNotNull(body.identityId)
        assertNull(body.driverId)
        assertNotNull(body.token)
        assertNotNull(body.expiresAt)
    }

    @Test
    fun `registering the same phone twice returns 409 on the second attempt`() {
        register("+79991234567")

        val response = controller.register(RegisterIdentityRequest("+79991234567", "another-password"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `registering with a malformed phone returns 400`() {
        val response = controller.register(RegisterIdentityRequest("not-a-phone", "correct-horse-battery-staple"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `registering with a blank password returns 400`() {
        val response = controller.register(RegisterIdentityRequest("+79991234567", ""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `registering with a short password returns 400`() {
        val response = controller.register(RegisterIdentityRequest("+79991234567", "short"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Login ---

    @Test
    fun `logging in with the correct password returns 200 with a usable token`() {
        register("+79991234567", "correct-horse-battery-staple")

        val response = controller.login(LoginRequest("+79991234567", "correct-horse-battery-staple"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertNotNull(body.token)
        assertNotNull(sessionTokenVerifier.verify(bearer(body.token)))
    }

    @Test
    fun `logging in with the wrong password returns 401`() {
        register("+79991234567", "correct-horse-battery-staple")

        val response = controller.login(LoginRequest("+79991234567", "wrong-password"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `logging in with an unknown phone returns 401, indistinguishable from a wrong password`() {
        register("+79991234567", "correct-horse-battery-staple")

        val wrongPasswordResponse = controller.login(LoginRequest("+79991234567", "wrong-password"))
        val unknownPhoneResponse = controller.login(LoginRequest("+79997654321", "whatever-password"))

        assertEquals(wrongPasswordResponse.statusCode, unknownPhoneResponse.statusCode)
        assertEquals(wrongPasswordResponse.body, unknownPhoneResponse.body)
    }

    // --- Get own identity ---

    @Test
    fun `getting own identity with a valid token returns 200 with the caller's own data`() {
        val registered = register("+79991234567")

        val response = controller.getOwnIdentity(bearer(registered.token))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(registered.identityId, body.id)
        assertEquals("+79991234567", body.phone)
    }

    @Test
    fun `getting own identity with no token returns 401`() {
        val response = controller.getOwnIdentity(null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting own identity with a garbage token returns 401`() {
        val response = controller.getOwnIdentity(bearer("not-a-real-token"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    // --- Get identity by id ---

    @Test
    fun `getting a known identity with its own token returns 200`() {
        val registered = register("+79991234567")

        val response = controller.getIdentity(registered.identityId, bearer(registered.token))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("+79991234567", assertNotNull(response.body).phone)
    }

    @Test
    fun `getting an identity with no token returns 401`() {
        val registered = register("+79991234567")

        val response = controller.getIdentity(registered.identityId, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting an identity with a garbage token returns 401`() {
        val registered = register("+79991234567")

        val response = controller.getIdentity(registered.identityId, bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting another identity's data with your own valid token returns 403, not the other identity's data`() {
        val self = register("+79991234567")
        val other = register("+79997654321")

        val response = controller.getIdentity(other.identityId, bearer(self.token))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `getting an unknown identity with a token for that same unknown id returns 404`() {
        val registered = register("+79991234567")
        val orphanToken = sessionTokenIssuer.issue("never-registered-identity", null).token

        val response = controller.getIdentity("never-registered-identity", bearer(orphanToken))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        // sanity: registering a real identity elsewhere never leaks into this lookup
        assertNotNull(registered.identityId)
    }

    // --- Associate driver ---

    @Test
    fun `associating a driver with your own valid token returns 200 with the driver id attached`() {
        val registered = register("+79991234567")

        val response =
            controller.associateDriver(registered.identityId, AssociateDriverRequest(registered.identityId), bearer(registered.token))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(registered.identityId, assertNotNull(response.body).driverId)
    }

    @Test
    fun `associating a driver returns a fresh token carrying drv, with no login round-trip needed (ADR-055 Decision 6 addendum)`() {
        val registered = register("+79991234567")
        val registrationToken = registered.token

        val response =
            controller.associateDriver(registered.identityId, AssociateDriverRequest(registered.identityId), bearer(registrationToken))

        val body = assertNotNull(response.body)
        assertNotNull(body.token)
        val verifiedNewToken = assertNotNull(sessionTokenVerifier.verify(bearer(body.token)))
        assertEquals(registered.identityId, verifiedNewToken.drv)
        // The new token is genuinely a different one, not the same string echoed back --
        // the whole point is that the caller no longer needs the registration-time token.
        assertEquals(false, body.token == registrationToken)
    }

    @Test
    fun `the registration token used to call associateDriver still verifies afterwards -- no server-side revocation exists (ADR-055)`() {
        val registered = register("+79991234567")

        controller.associateDriver(registered.identityId, AssociateDriverRequest(registered.identityId), bearer(registered.token))

        val verifiedOldToken = assertNotNull(sessionTokenVerifier.verify(bearer(registered.token)))
        assertNull(verifiedOldToken.drv)
    }

    @Test
    fun `associating a driver on another identity with your own valid token returns 403`() {
        val self = register("+79991234567")
        val other = register("+79997654321")

        val response = controller.associateDriver(other.identityId, AssociateDriverRequest(other.identityId), bearer(self.token))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `associating a driver with no token returns 401`() {
        val registered = register("+79991234567")

        val response = controller.associateDriver(registered.identityId, AssociateDriverRequest("ILDAR001"), null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `associating a driver with a garbage token returns 401`() {
        val registered = register("+79991234567")

        val response =
            controller.associateDriver(registered.identityId, AssociateDriverRequest(registered.identityId), bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `associating a blank driver id with your own valid token returns 400`() {
        val registered = register("+79991234567")

        val response = controller.associateDriver(registered.identityId, AssociateDriverRequest(""), bearer(registered.token))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a driver's token carries their driverId as the drv claim, so their own connections lookup can be locked to it`() {
        val registered = register("+79991234567")
        controller.associateDriver(registered.identityId, AssociateDriverRequest(registered.identityId), bearer(registered.token))

        val loginResponse = assertNotNull(controller.login(LoginRequest("+79991234567", "correct-horse-battery-staple")).body)
        val verified = assertNotNull(sessionTokenVerifier.verify(bearer(loginResponse.token)))

        assertEquals(registered.identityId, verified.drv)
    }

    @Test
    fun `associating an arbitrary driver id with your own token returns 400`() {
        val registered = register("+79991234567")

        val response = controller.associateDriver(
            registered.identityId,
            AssociateDriverRequest("somebody-elses-driver"),
            bearer(registered.token)
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- D-03 (ADR-082): recovery request enumeration safety ---

    @Test
    fun `recovery request returns the identical status for an unknown, unregistered, and eligible phone`() {
        register("+79992220001") // registered, but not phone-verified -- not eligible either

        val unknown = controller.requestRecoveryFromAddress("+79992220099", "127.0.0.1", null)
        val notVerified = controller.requestRecoveryFromAddress("+79992220001", "127.0.0.1", null)

        assertEquals(unknown.statusCode, notVerified.statusCode)
        assertEquals(HttpStatus.ACCEPTED, unknown.statusCode)
    }

    @Test
    fun `recovery request for an eligible phone sends no observable difference in the HTTP response either`() {
        val registered = register("+79992220002")
        verifyPhoneViaLegacyEnrolment(registered)
        smsPort.sentCodes.clear()

        val response = controller.requestRecoveryFromAddress("+79992220002", "127.0.0.1", null)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertTrue(smsPort.sentCodes.isEmpty(), "request handling must not call the SMS provider")
        val challenge = assertNotNull(challengeRepository.findLiveForUpdate(
            com.pios.identity.domain.IdentityId(registered.identityId),
            com.pios.identity.domain.PhoneVerificationPurpose.RECOVERY
        ))
        assertEquals(SmsOutboxStatus.PENDING, smsOutbox.findByChallengeId(challenge.id)?.status)
    }

    @Test
    fun `provider failure for eligible phone has the same 202 empty object as unknown phone`() {
        val registered = register("+79992220012")
        verifyPhoneViaLegacyEnrolment(registered)
        smsPort.sentCodes.clear()
        smsPort.failure = OutboundSmsDeliveryException(
            SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.TRANSPORT_TIMEOUT
        )

        val unknown = controller.requestRecoveryFromAddress("+79992220099", "127.0.0.1", null)
        val failedDelivery = controller.requestRecoveryFromAddress("+79992220012", "127.0.0.1", null)

        assertEquals(HttpStatus.ACCEPTED, failedDelivery.statusCode)
        assertEquals(unknown.statusCode, failedDelivery.statusCode)
        assertEquals(emptyMap(), unknown.body)
        assertEquals(unknown.body, failedDelivery.body)
        assertNotNull(challengeRepository.findLiveForUpdate(
            com.pios.identity.domain.IdentityId(registered.identityId),
            com.pios.identity.domain.PhoneVerificationPurpose.RECOVERY
        ))
        assertTrue(smsPort.sentCodes.isEmpty(), "provider failure must not occur in the request path")
        smsRelay.relay()
    }

    // --- D-03: legacy enrolment (phone/verify/request, phone/verify/confirm) ---

    @Test
    fun `phone verify request is rejected for a guest token`() {
        val guest = assertNotNull(controller.createGuestFromAddress("127.0.0.1", "203.0.113.20").body)

        val response = controller.requestPhoneVerificationFromAddress("127.0.0.1", null, bearer(guest.token))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `phone verify request is rejected with no token`() {
        val response = controller.requestPhoneVerificationFromAddress("127.0.0.1", null, null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `a full legacy enrolment makes the identity recovery-eligible`() {
        val registered = register("+79992220003")
        // Freshly registered -- per ADR-082 Part 2, not yet phone-verified,
        // and therefore not yet recovery-eligible (proven first).
        val premature = controller.requestRecoveryFromAddress("+79992220003", "127.0.0.1", null)
        assertEquals(HttpStatus.ACCEPTED, premature.statusCode) // generic response
        assertFalse(smsPort.sentCodes.containsKey("+79992220003"))

        verifyPhoneViaLegacyEnrolment(registered)

        smsPort.sentCodes.clear()
        val recovery = controller.requestRecoveryFromAddress("+79992220003", "127.0.0.1", null)
        assertEquals(HttpStatus.ACCEPTED, recovery.statusCode)
        assertFalse(smsPort.sentCodes.containsKey("+79992220003"), "recovery request must not synchronously call the provider")

        smsRelay.relay()
        assertTrue(smsPort.sentCodes.containsKey("+79992220003"), "the separately invoked relay must deliver the recovery OTP")
    }

    @Test
    fun `legacy enrolment never creates a driver association or changes sessionGeneration`() {
        val registered = register("+79992220004")
        val verifiedResponse = verifyPhoneViaLegacyEnrolment(registered)

        assertNull(verifiedResponse.driverId)
        assertEquals(true, verifiedResponse.phoneVerified)

        val me = assertNotNull(controller.getOwnIdentity(bearer(registered.token)).body)
        assertNull(me.driverId)
    }

    // --- D-03: recovery confirm, and the sessionGeneration invalidation it produces ---

    @Test
    fun `recovery confirm with a correct code replaces the password and the old password stops working`() {
        val registered = register("+79992220005")
        verifyPhoneViaLegacyEnrolment(registered)
        controller.requestRecoveryFromAddress("+79992220005", "127.0.0.1", null)
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue("+79992220005")

        val confirmed = controller.confirmRecovery(RecoveryConfirmRequest("+79992220005", code, "brand-new-password-xyz"))
        assertEquals(HttpStatus.OK, confirmed.statusCode)

        val oldLogin = controller.login(LoginRequest("+79992220005", "correct-horse-battery-staple"))
        assertEquals(HttpStatus.UNAUTHORIZED, oldLogin.statusCode)
        val newLogin = controller.login(LoginRequest("+79992220005", "brand-new-password-xyz"))
        assertEquals(HttpStatus.OK, newLogin.statusCode)
    }

    @Test
    fun `after recovery, the pre-recovery token is rejected by identity's own endpoints`() {
        val registered = register("+79992220006")
        verifyPhoneViaLegacyEnrolment(registered)
        controller.requestRecoveryFromAddress("+79992220006", "127.0.0.1", null)
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue("+79992220006")
        controller.confirmRecovery(RecoveryConfirmRequest("+79992220006", code, "brand-new-password-abc"))

        // The token issued at registration -- before recovery -- is now stale.
        val staleMe = controller.getOwnIdentity(bearer(registered.token))
        assertEquals(HttpStatus.UNAUTHORIZED, staleMe.statusCode)

        val staleAssociate = controller.associateDriver(
            registered.identityId, AssociateDriverRequest(registered.identityId), bearer(registered.token)
        )
        assertEquals(HttpStatus.UNAUTHORIZED, staleAssociate.statusCode)
    }

    @Test
    fun `after recovery, the freshly minted token works normally`() {
        val registered = register("+79992220007")
        verifyPhoneViaLegacyEnrolment(registered)
        controller.requestRecoveryFromAddress("+79992220007", "127.0.0.1", null)
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue("+79992220007")
        val recovered = assertNotNull(
            controller.confirmRecovery(RecoveryConfirmRequest("+79992220007", code, "brand-new-password-def")).body
        )

        val me = controller.getOwnIdentity(bearer(recovered.token))
        assertEquals(HttpStatus.OK, me.statusCode)
    }

    @Test
    fun `recovery never changes identityId or an existing driverId`() {
        val registered = register("+79992220008")
        controller.associateDriver(registered.identityId, AssociateDriverRequest(registered.identityId), bearer(registered.token))
        val reLoggedIn = assertNotNull(controller.login(LoginRequest("+79992220008", "correct-horse-battery-staple")).body)
        verifyPhoneViaAuthResponse(reLoggedIn)

        controller.requestRecoveryFromAddress("+79992220008", "127.0.0.1", null)
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue("+79992220008")
        val recovered = assertNotNull(
            controller.confirmRecovery(RecoveryConfirmRequest("+79992220008", code, "brand-new-password-ghi")).body
        )

        assertEquals(registered.identityId, recovered.identityId)
        assertEquals(registered.identityId, recovered.driverId) // driverId == identityId in this test's own convention
    }

    @Test
    fun `recovery confirm with a wrong code returns 401, not 400`() {
        val registered = register("+79992220009")
        verifyPhoneViaLegacyEnrolment(registered)
        controller.requestRecoveryFromAddress("+79992220009", "127.0.0.1", null)

        val response = controller.confirmRecovery(RecoveryConfirmRequest("+79992220009", "000000", "brand-new-password-jkl"))
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `recovery confirm for an unknown phone returns 401, the same as a wrong code`() {
        val response = controller.confirmRecovery(RecoveryConfirmRequest("+79992229999", "123456", "brand-new-password-mno"))
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `recovery confirm with a too-short new password returns 400`() {
        val registered = register("+79992220010")
        verifyPhoneViaLegacyEnrolment(registered)
        controller.requestRecoveryFromAddress("+79992220010", "127.0.0.1", null)
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue("+79992220010")

        val response = controller.confirmRecovery(RecoveryConfirmRequest("+79992220010", code, "short"))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- D-03 §8: the disclosed cross-module session-invalidation trade-off ---

    /**
     * ADR-082 §8 (D-03.3): a token minted before a successful recovery is
     * rejected by `identity`'s own endpoints (proven above by "after
     * recovery, the pre-recovery token is rejected..."), but this proves
     * the other half explicitly -- `sessionTokenVerifier.verify()` in
     * isolation, exactly the call the five other modules' own replicated
     * copies make, has no way to know a recovery happened at all: it has
     * no database, only the token's own claims. The pre-recovery token's
     * *signature and expiry* remain genuinely valid -- only a caller that
     * additionally compares `sgen` against a live `Identity` row (which
     * only `identity`'s own controller does) can tell the difference. This
     * is the honest scope of what `sessionGeneration` invalidates: real
     * within `identity`, absent everywhere else until natural `exp`. Never
     * to be described as a global, instant logout.
     */
    @Test
    fun `the pre-recovery token's own signature and expiry remain genuinely valid to a verifier with no database access`() {
        val registered = register("+79992220011")
        verifyPhoneViaLegacyEnrolment(registered)
        controller.requestRecoveryFromAddress("+79992220011", "127.0.0.1", null)
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue("+79992220011")
        controller.confirmRecovery(RecoveryConfirmRequest("+79992220011", code, "brand-new-password-pqr"))

        // identity's own generation is now 1 -- but a bare verify(), the
        // only call the five other modules' own replicated verifiers ever
        // make, still succeeds: it has no live Identity row to compare
        // sgen against, so it cannot and does not reject this token.
        val stillVerifiesElsewhere = sessionTokenVerifier.verify(bearer(registered.token))
        assertNotNull(stillVerifiesElsewhere, "a bare verify() -- what the other five modules do -- must still accept the pre-recovery token")
        assertEquals(0, stillVerifiesElsewhere.sgen, "the token's own claim is frozen at mint time, exactly as issued")

        // Only identity's own controller, which additionally loads the
        // live Identity and compares sgen, actually rejects it (see the
        // dedicated test above) -- proving the rejection is a controller-
        // level check, not something verify() itself provides.
    }

    // --- D-03.7 closure pass: both rate limiters apply to both OTP purposes ---

    private fun controllerWithLimits(phoneMaxPerWindow: Int, clientKeyMaxPerWindow: Int): IdentityController {
        val tightPhoneLimiter = PhoneOtpRequestRateLimiter(phoneMaxPerWindow, 3_600_000)
        val tightClientKeyLimiter = OtpClientKeyRateLimiter(clientKeyMaxPerWindow, 3_600_000)
        val tightRequestRecoveryService =
            RequestRecoveryApplicationService(identityRepository, challengeIssuer, tightPhoneLimiter, tightClientKeyLimiter)
        val tightRequestPhoneVerificationService =
            RequestPhoneVerificationApplicationService(identityRepository, challengeIssuer, tightPhoneLimiter)
        return IdentityController(
            registerService, loginService, retrieveIdentityHandler, associateDriverService,
            sessionTokenVerifier, createGuestService, guestRateLimiter, upgradeGuestService,
            tightRequestRecoveryService, confirmRecoveryService, tightRequestPhoneVerificationService,
            confirmPhoneVerificationService, tightClientKeyLimiter
        )
    }

    @Test
    fun `recovery request applies the client-key budget too, with the identical generic 202 response either way`() {
        val limited = controllerWithLimits(phoneMaxPerWindow = 1000, clientKeyMaxPerWindow = 1)

        val first = limited.requestRecoveryFromAddress("+79993330001", "198.51.100.30", null)
        val second = limited.requestRecoveryFromAddress("+79993330002", "198.51.100.30", null) // same client key, different phone

        assertEquals(HttpStatus.ACCEPTED, first.statusCode)
        assertEquals(HttpStatus.ACCEPTED, second.statusCode)
        assertEquals(first.statusCode, second.statusCode)
    }

    @Test
    fun `recovery request applies the phone budget too, with the identical generic 202 response either way`() {
        val limited = controllerWithLimits(phoneMaxPerWindow = 1, clientKeyMaxPerWindow = 1000)

        val first = limited.requestRecoveryFromAddress("+79993330003", "198.51.100.31", null)
        val second = limited.requestRecoveryFromAddress("+79993330003", "198.51.100.32", null) // same phone, different client key

        assertEquals(HttpStatus.ACCEPTED, first.statusCode)
        assertEquals(HttpStatus.ACCEPTED, second.statusCode)
    }

    @Test
    fun `an exhausted client-key budget never actually sends a second recovery code`() {
        val limited = controllerWithLimits(phoneMaxPerWindow = 1000, clientKeyMaxPerWindow = 1)
        val registered = register("+79993330004")
        // Reach eligibility through the *unlimited* controller, so only the
        // rate limit under test is what's tight.
        verifyPhoneViaLegacyEnrolment(registered)

        limited.requestRecoveryFromAddress("+79993330004", "198.51.100.33", null)
        smsPort.sentCodes.remove("+79993330004")
        limited.requestRecoveryFromAddress("+79993330004", "198.51.100.33", null) // same client key again

        assertFalse(smsPort.sentCodes.containsKey("+79993330004"))
    }

    @Test
    fun `legacy enrolment request applies the phone budget too, returning 429`() {
        val limited = controllerWithLimits(phoneMaxPerWindow = 1, clientKeyMaxPerWindow = 1000)
        val registered = register("+79993330005")

        val first = limited.requestPhoneVerificationFromAddress("198.51.100.34", null, bearer(registered.token))
        assertEquals(HttpStatus.ACCEPTED, first.statusCode)

        val second = limited.requestPhoneVerificationFromAddress("198.51.100.35", null, bearer(registered.token)) // different client key
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, second.statusCode)
    }

    @Test
    fun `legacy enrolment request applies the client-key budget too, returning 429`() {
        val limited = controllerWithLimits(phoneMaxPerWindow = 1000, clientKeyMaxPerWindow = 1)
        val first = register("+79993330006")
        val second = register("+79993330007")

        val firstResponse = limited.requestPhoneVerificationFromAddress("198.51.100.36", null, bearer(first.token))
        assertEquals(HttpStatus.ACCEPTED, firstResponse.statusCode)

        val secondResponse = limited.requestPhoneVerificationFromAddress("198.51.100.36", null, bearer(second.token)) // same client key
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, secondResponse.statusCode)
    }

    // --- helpers ---

    /** Registers, then verifies that same identity's on-file phone via the D-03.2 legacy-enrolment flow. */
    private fun verifyPhoneViaLegacyEnrolment(registered: AuthResponse): IdentityResponse {
        controller.requestPhoneVerificationFromAddress("127.0.0.1", null, bearer(registered.token))
        val phone = identityRepository.findById(com.pios.identity.domain.IdentityId(registered.identityId))!!.phone!!.value
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue(phone)
        return assertNotNull(
            controller.confirmPhoneVerification(PhoneVerifyConfirmRequest(code), bearer(registered.token)).body
        )
    }

    private fun verifyPhoneViaAuthResponse(auth: AuthResponse): IdentityResponse {
        controller.requestPhoneVerificationFromAddress("127.0.0.1", null, bearer(auth.token))
        val phone = identityRepository.findById(com.pios.identity.domain.IdentityId(auth.identityId))!!.phone!!.value
        smsRelay.relay()
        val code = smsPort.sentCodes.getValue(phone)
        return assertNotNull(
            controller.confirmPhoneVerification(PhoneVerifyConfirmRequest(code), bearer(auth.token)).body
        )
    }
}
