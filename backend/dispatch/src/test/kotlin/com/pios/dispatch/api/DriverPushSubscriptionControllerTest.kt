package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.persistence.InMemoryDriverPushSubscriptionRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Constructs [DriverPushSubscriptionController] directly against a real
 * [InMemoryDriverPushSubscriptionRepository], no Spring MVC context --
 * mirroring [ProposalControllerTest]'s own constructor-based testing
 * convention, including its token-minting helpers (this module has no
 * build-time dependency on `identity` and never calls it, ADR-055 Decision
 * 1, so tokens are minted locally in the exact format
 * [SessionTokenVerifier] checks).
 */
class DriverPushSubscriptionControllerTest {

    private val repository = InMemoryDriverPushSubscriptionRepository()
    private val secret = Base64.getEncoder().encodeToString("driver-push-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val objectMapper = ObjectMapper()

    private fun controller(vapidPublicKey: String = "configured-public-key") =
        DriverPushSubscriptionController(repository, sessionTokenVerifier, vapidPublicKey)

    private fun issueToken(sub: String, drv: String? = null, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(secret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun bearer(token: String): String = "Bearer $token"
    private fun driverToken(driverId: String): String = bearer(issueToken(sub = "$driverId-identity", drv = driverId))
    private fun passengerOnlyToken(): String = bearer(issueToken(sub = "passenger-test"))

    // --- GET /public-key ---

    @Test
    fun `getPublicKey without a Bearer token is 401`() {
        val response = controller().getPublicKey(authorization = null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getPublicKey with an invalid Bearer token is 401`() {
        val response = controller().getPublicKey(authorization = "Bearer not-a-real-token")
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getPublicKey with a passenger-only token (drv null) is 403`() {
        val response = controller().getPublicKey(authorization = passengerOnlyToken())
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `getPublicKey returns the configured key for a valid driver token`() {
        val response = controller("configured-public-key").getPublicKey(authorization = driverToken("driver-1"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("configured-public-key", response.body?.publicKey)
    }

    @Test
    fun `getPublicKey is 404 when VAPID is not configured -- fail-closed`() {
        val response = controller(vapidPublicKey = "").getPublicKey(authorization = driverToken("driver-1"))
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // --- POST (register) ---

    @Test
    fun `register without a Bearer token is 401`() {
        val response = controller().register(RegisterDriverPushSubscriptionRequest("e", RegisterDriverPushSubscriptionRequest.Keys("p", "a")), null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `register with a passenger-only token (drv null) is 403`() {
        val response = controller().register(
            RegisterDriverPushSubscriptionRequest("e", RegisterDriverPushSubscriptionRequest.Keys("p", "a")),
            passengerOnlyToken()
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `register with a missing endpoint is 400`() {
        val response = controller().register(
            RegisterDriverPushSubscriptionRequest(endpoint = null, keys = RegisterDriverPushSubscriptionRequest.Keys("p", "a")),
            driverToken("driver-1")
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `register with missing keys is 400`() {
        val response = controller().register(
            RegisterDriverPushSubscriptionRequest(endpoint = "https://push.example.com/x", keys = null),
            driverToken("driver-1")
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `register with a client-supplied driverId in the body is rejected 400, never silently ignored`() {
        val response = controller().register(
            RegisterDriverPushSubscriptionRequest(
                endpoint = "https://push.example.com/x",
                keys = RegisterDriverPushSubscriptionRequest.Keys("p", "a"),
                driverId = "someone-elses-driver-id"
            ),
            driverToken("driver-1")
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(repository.findByDriver(DriverReference("someone-elses-driver-id")).isEmpty())
    }

    @Test
    fun `register with a client-supplied driverReference in the body is rejected 400, never silently ignored`() {
        val response = controller().register(
            RegisterDriverPushSubscriptionRequest(
                endpoint = "https://push.example.com/x",
                keys = RegisterDriverPushSubscriptionRequest.Keys("p", "a"),
                driverReference = "someone-elses-driver-id"
            ),
            driverToken("driver-1")
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `register on the happy path stores the verified driver, never anything from the body, and returns 204`() {
        val response = controller().register(
            RegisterDriverPushSubscriptionRequest(
                endpoint = "https://push.example.com/happy-path",
                keys = RegisterDriverPushSubscriptionRequest.Keys("p256dh-value", "auth-value")
            ),
            driverToken("driver-happy")
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val stored = repository.findByDriver(DriverReference("driver-happy"))
        assertEquals(1, stored.size)
        assertEquals("https://push.example.com/happy-path", stored.single().endpoint)
        assertEquals("p256dh-value", stored.single().p256dh)
        assertEquals("auth-value", stored.single().auth)
    }

    @Test
    fun `re-registering an existing endpoint under a different driver reassigns it`() {
        val endpoint = "https://push.example.com/reassign"
        controller().register(RegisterDriverPushSubscriptionRequest(endpoint, RegisterDriverPushSubscriptionRequest.Keys("p1", "a1")), driverToken("driver-old"))

        controller().register(RegisterDriverPushSubscriptionRequest(endpoint, RegisterDriverPushSubscriptionRequest.Keys("p2", "a2")), driverToken("driver-new"))

        assertTrue(repository.findByDriver(DriverReference("driver-old")).isEmpty())
        val stored = repository.findByDriver(DriverReference("driver-new"))
        assertEquals(1, stored.size)
        assertEquals(endpoint, stored.single().endpoint)
    }

    // --- DELETE (unregister) ---

    @Test
    fun `unregister without a Bearer token is 401`() {
        val response = controller().unregister("https://push.example.com/x", null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `unregister with a passenger-only token (drv null) is 403`() {
        val response = controller().unregister("https://push.example.com/x", passengerOnlyToken())
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `driver A's DELETE of driver B's endpoint removes nothing and still returns 204`() {
        val endpoint = "https://push.example.com/driver-b-owned"
        repository.upsert(DriverPushSubscription(endpoint, DriverReference("driver-b"), "p", "a"))

        val response = controller().unregister(endpoint, driverToken("driver-a"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(1, repository.findByDriver(DriverReference("driver-b")).size)
    }

    @Test
    fun `unregister of a never-registered endpoint still returns 204 -- no existence disclosure`() {
        val response = controller().unregister("https://push.example.com/never-registered", driverToken("driver-a"))
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `unregister removes exactly the caller's own endpoint`() {
        val endpoint = "https://push.example.com/own-endpoint"
        repository.upsert(DriverPushSubscription(endpoint, DriverReference("driver-owner"), "p", "a"))

        val response = controller().unregister(endpoint, driverToken("driver-owner"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertTrue(repository.findByDriver(DriverReference("driver-owner")).isEmpty())
    }
}
