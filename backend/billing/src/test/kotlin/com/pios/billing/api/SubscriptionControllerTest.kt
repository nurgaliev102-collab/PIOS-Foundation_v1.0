package com.pios.billing.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.billing.application.RecordSubscriptionPeriodApplicationService
import com.pios.billing.application.RetrieveSubscriptionHandler
import com.pios.billing.persistence.InMemorySubscriptionRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves `SubscriptionController`'s own authorization boundary (ADR-074
 * Part 3/Part 4) with an in-memory repository, no Spring context, no
 * database — mirrors this codebase's own constructor-based controller
 * testing convention (e.g. `com.pios.drivermanagement.api.DriverControllerTest`).
 */
class SubscriptionControllerTest {

    private val repository = InMemorySubscriptionRepository()
    private val recordService = RecordSubscriptionPeriodApplicationService(repository)
    private val retrieveHandler = RetrieveSubscriptionHandler(repository)

    private val ownerUsername = "owner"
    private val ownerPassword = "owner-password"
    private val ownerSalt = "subscription-controller-test-salt".toByteArray()
    private val ownerIterations = 1000
    private val ownerPasswordHash = deriveKey(ownerPassword, ownerSalt, ownerIterations)

    private val ownerCredentialGate = OwnerCredentialGate(
        configuredUsername = ownerUsername,
        configuredPasswordHash = Base64.getEncoder().encodeToString(ownerPasswordHash),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(ownerSalt),
        iterations = ownerIterations,
        failureDelayMillis = 0,
        maxFailuresPerWindow = 1000,
        windowMillis = 900_000
    )

    private val sessionSecret = Base64.getEncoder().encodeToString("subscription-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = sessionSecret)

    private val controller = SubscriptionController(recordService, retrieveHandler, ownerCredentialGate, sessionTokenVerifier)

    private val objectMapper = ObjectMapper()
    private val periodEnd = "2026-10-15T00:00:00Z"

    private fun ownerHeader(): String = "Basic " + Base64.getEncoder().encodeToString("$ownerUsername:$ownerPassword".toByteArray())

    private fun issueToken(sub: String, drv: String? = null, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(sessionSecret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun driverToken(driverId: String): String = "Bearer " + issueToken(sub = "$driverId-identity", drv = driverId)

    // --- POST /v1/subscriptions/{driverId}/periods -- owner-gated write ---

    @Test
    fun `recording a period with no Authorization header is rejected 401 and records nothing`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "TRIAL", currentPeriodEnd = periodEnd, isTest = false)
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(retrieveHandler.handle(com.pios.billing.domain.DriverReference("driver-1")).currentPeriodEnd)
    }

    @Test
    fun `recording a period with an incorrect owner credential is rejected 401`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "TRIAL", currentPeriodEnd = periodEnd, isTest = false),
            authorization = "Basic " + Base64.getEncoder().encodeToString("$ownerUsername:wrong".toByteArray())
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `recording a period with a driver's own session token instead of the owner credential is rejected 401 -- only the owner may write`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "TRIAL", currentPeriodEnd = periodEnd, isTest = false),
            authorization = driverToken("driver-1")
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `the owner credential records a period and the response reflects it`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "TRIAL", currentPeriodEnd = periodEnd, isTest = false),
            authorization = ownerHeader()
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("TRIAL", response.body?.status)
        assertEquals(periodEnd, response.body?.currentPeriodEnd)
    }

    @Test
    fun `recording FREE as a target status is rejected 400 -- FREE is never a write target`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "FREE", currentPeriodEnd = null, isTest = false),
            authorization = ownerHeader()
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `an unknown status name is rejected 400`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "PLATINUM", currentPeriodEnd = null, isTest = false),
            authorization = ownerHeader()
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a malformed currentPeriodEnd is rejected 400`() {
        val response = controller.recordPeriod(
            "driver-1",
            RecordSubscriptionPeriodRequest(status = "TRIAL", currentPeriodEnd = "not-a-date", isTest = false),
            authorization = ownerHeader()
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a transition the domain forbids is rejected 409 and does not mutate the persisted record`() {
        controller.recordPeriod("driver-1", RecordSubscriptionPeriodRequest("ACTIVE", periodEnd, isTest = false), authorization = ownerHeader())

        val response = controller.recordPeriod("driver-1", RecordSubscriptionPeriodRequest("TRIAL", periodEnd, isTest = false), authorization = ownerHeader())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("ACTIVE", retrieveHandler.handle(com.pios.billing.domain.DriverReference("driver-1")).status.name)
    }

    // --- GET /v1/subscriptions/{driverId} -- session-token-gated read ---

    @Test
    fun `reading a subscription with no Authorization header is rejected 401`() {
        val response = controller.getSubscription("driver-2")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `reading a subscription with a different driver's own token is rejected 403`() {
        val response = controller.getSubscription("driver-2", authorization = driverToken("driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `reading a subscription with a passenger-only token (no drv) is rejected 403`() {
        val response = controller.getSubscription("driver-2", authorization = "Bearer " + issueToken(sub = "some-passenger"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `the owner credential alone, with no Bearer token, is rejected 401 on the read path -- only the driver's own token authorizes a read`() {
        val response = controller.getSubscription("driver-2", authorization = ownerHeader())

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `the driver's own token reads FREE with a null currentPeriodEnd for a driver with no record`() {
        val response = controller.getSubscription("driver-2", authorization = driverToken("driver-2"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("FREE", response.body?.status)
        assertNull(response.body?.currentPeriodEnd)
    }

    @Test
    fun `the driver's own token reads the real recorded status after the owner records one`() {
        controller.recordPeriod("driver-3", RecordSubscriptionPeriodRequest("ACTIVE", periodEnd, isTest = false), authorization = ownerHeader())

        val response = controller.getSubscription("driver-3", authorization = driverToken("driver-3"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACTIVE", response.body?.status)
        assertEquals(periodEnd, response.body?.currentPeriodEnd)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
