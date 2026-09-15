package com.pios.billing.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.billing.application.RecordSubscriptionPeriodApplicationService
import com.pios.billing.application.RetrieveSubscriptionHandler
import com.pios.billing.domain.DriverReference
import com.pios.billing.persistence.PostgreSQLSubscriptionRepository
import com.pios.billing.persistence.PostgreSQLTestDatabase
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves, against the real, isolated `pios_billing_test` PostgreSQL
 * database -- not the in-memory repository [SubscriptionControllerTest]
 * otherwise uses -- that the owner-gated write and driver-scoped read
 * authorization boundaries hold end to end. Mirrors
 * `com.pios.drivermanagement.api.DriverControllerPostgreSQLSecurityTest`'s
 * own shape exactly. Every identifier here is randomized
 * ([UUID.randomUUID]), not a fixed literal.
 */
class SubscriptionControllerPostgreSQLSecurityTest {

    private val repository = PostgreSQLSubscriptionRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val recordService = RecordSubscriptionPeriodApplicationService(repository)
    private val retrieveHandler = RetrieveSubscriptionHandler(repository)

    private val ownerUsername = "owner"
    private val ownerPassword = "owner-password"
    private val ownerSalt = "subscription-postgres-security-test-salt".toByteArray()
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

    private val sessionSecret = Base64.getEncoder().encodeToString("subscription-postgres-security-test-secret".toByteArray())
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

    @Test
    fun `recording a period through REST with no Authorization header, backed by PostgreSQL, does not create a real record`() {
        val driverId = "billing-sec-${UUID.randomUUID()}"

        val response = controller.recordPeriod(driverId, RecordSubscriptionPeriodRequest("TRIAL", periodEnd, isTest = true))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(repository.findByDriverId(DriverReference(driverId)))
    }

    @Test
    fun `recording a period through REST with a driver's own token, backed by PostgreSQL, is rejected and does not create a real record -- only the owner credential authorizes a write`() {
        val driverId = "billing-sec-${UUID.randomUUID()}"

        val response = controller.recordPeriod(driverId, RecordSubscriptionPeriodRequest("TRIAL", periodEnd, isTest = true), authorization = driverToken(driverId))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(repository.findByDriverId(DriverReference(driverId)))
    }

    @Test
    fun `the owner credential records a period through REST for a real driver in PostgreSQL`() {
        val driverId = "billing-sec-${UUID.randomUUID()}"

        val response = controller.recordPeriod(driverId, RecordSubscriptionPeriodRequest("TRIAL", periodEnd, isTest = true), authorization = ownerHeader())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("TRIAL", repository.findByDriverId(DriverReference(driverId))?.status?.name)
        assertEquals(true, repository.findByDriverId(DriverReference(driverId))?.isTest)
    }

    @Test
    fun `reading a real, persisted subscription as a different driver, backed by PostgreSQL, is rejected 403`() {
        val driverId = "billing-sec-${UUID.randomUUID()}"
        controller.recordPeriod(driverId, RecordSubscriptionPeriodRequest("ACTIVE", periodEnd, isTest = true), authorization = ownerHeader())

        val response = controller.getSubscription(driverId, authorization = driverToken("billing-sec-attacker-${UUID.randomUUID()}"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `reading a real, persisted subscription with no Authorization header, backed by PostgreSQL, is rejected 401`() {
        val driverId = "billing-sec-${UUID.randomUUID()}"
        controller.recordPeriod(driverId, RecordSubscriptionPeriodRequest("ACTIVE", periodEnd, isTest = true), authorization = ownerHeader())

        val response = controller.getSubscription(driverId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `the driver's own token reads their own real, persisted subscription from PostgreSQL`() {
        val driverId = "billing-sec-${UUID.randomUUID()}"
        controller.recordPeriod(driverId, RecordSubscriptionPeriodRequest("ACTIVE", periodEnd, isTest = true), authorization = ownerHeader())

        val response = controller.getSubscription(driverId, authorization = driverToken(driverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACTIVE", response.body?.status)
        assertEquals(periodEnd, response.body?.currentPeriodEnd)
    }

    @Test
    fun `a driver never recorded in PostgreSQL reads as FREE through their own token`() {
        val driverId = "billing-sec-never-recorded-${UUID.randomUUID()}"

        val response = controller.getSubscription(driverId, authorization = driverToken(driverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("FREE", response.body?.status)
        assertNull(response.body?.currentPeriodEnd)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
