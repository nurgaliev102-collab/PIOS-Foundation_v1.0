package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.CredentialRepository
import com.pios.identity.application.CreateGuestIdentityApplicationService
import com.pios.identity.application.GuestIdentityRateLimiter
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginRateLimiter
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.RegisterIdentityApplicationService
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.application.UpgradeGuestIdentityApplicationService
import com.pios.identity.persistence.InMemoryCredentialRepository
import com.pios.identity.persistence.InMemoryIdentityRepository
import org.springframework.http.HttpStatus
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    private val controller = IdentityController(
        registerService,
        loginService,
        retrieveIdentityHandler,
        associateDriverService,
        sessionTokenVerifier,
        createGuestService,
        guestRateLimiter,
        upgradeGuestService
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
            GuestIdentityRateLimiter(maxPerWindow = 1, windowMillis = 3_600_000), upgradeGuestService
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
}
