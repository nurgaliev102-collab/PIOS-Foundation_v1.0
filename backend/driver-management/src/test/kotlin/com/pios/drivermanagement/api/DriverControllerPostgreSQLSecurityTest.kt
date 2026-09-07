package com.pios.drivermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
import com.pios.drivermanagement.application.RetrieveDriverMilestonesHandler
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.PostgreSQLDriverMilestonesRepository
import com.pios.drivermanagement.persistence.PostgreSQLDriverRepository
import com.pios.drivermanagement.persistence.PostgreSQLTestDatabase
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Task 25 (Orders Cancellation & Driver Availability Security
 * Remediation): proves, against the real, isolated `pios_driver_management_test`
 * PostgreSQL database -- not the in-memory repository [DriverControllerTest]
 * otherwise uses -- that a different driver's own token cannot mutate a
 * real, persisted driver's own availability, and that the driver's own
 * token still can. Mirrors
 * `com.pios.dispatch.api.AssignmentControllerPostgreSQLSecurityTest`'s own
 * shape exactly (Task 23).
 *
 * Every identifier here is randomized ([UUID.randomUUID]), not a fixed
 * literal, per this task's own test-data discipline.
 */
class DriverControllerPostgreSQLSecurityTest {

    private val repository = PostgreSQLDriverRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val handler = RetrieveDriverAvailabilityHandler(repository)
    private val availabilityService = DriverAvailabilityApplicationService(repository)
    private val createDriverService = CreateDriverApplicationService(repository)
    private val milestonesRepository = PostgreSQLDriverMilestonesRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val milestonesHandler = RetrieveDriverMilestonesHandler(milestonesRepository)
    private val secret = Base64.getEncoder().encodeToString("driver-postgres-security-test-secret".toByteArray())
    private val controller = DriverController(handler, availabilityService, createDriverService, milestonesHandler, SessionTokenVerifier(secretBase64 = secret))

    private val objectMapper = ObjectMapper()

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

    private fun driverToken(driverId: String): String = "Bearer " + issueToken(sub = "$driverId-identity", drv = driverId)

    @Test
    fun `declaring availability through REST as a different driver, backed by PostgreSQL, is rejected and does not mutate the real driver`() {
        val driverId = "driver-sec-victim-${UUID.randomUUID()}"
        repository.save(Driver(DriverId(driverId), Availability.UNAVAILABLE))

        val response = controller.declareAvailability(
            driverId,
            DeclareAvailabilityRequest("AVAILABLE"),
            authorization = driverToken("driver-sec-attacker-${UUID.randomUUID()}")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(Availability.UNAVAILABLE, repository.findById(DriverId(driverId))?.availability)
    }

    @Test
    fun `declaring availability through REST with no Authorization header, backed by PostgreSQL, does not mutate the real driver`() {
        val driverId = "driver-sec-${UUID.randomUUID()}"
        repository.save(Driver(DriverId(driverId), Availability.UNAVAILABLE))

        val response = controller.declareAvailability(driverId, DeclareAvailabilityRequest("AVAILABLE"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(Availability.UNAVAILABLE, repository.findById(DriverId(driverId))?.availability)
    }

    @Test
    fun `the driver's own token still declares availability for the real driver in PostgreSQL`() {
        val driverId = "driver-sec-${UUID.randomUUID()}"
        repository.save(Driver(DriverId(driverId), Availability.UNAVAILABLE))

        val response = controller.declareAvailability(driverId, DeclareAvailabilityRequest("AVAILABLE"), authorization = driverToken(driverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(Availability.AVAILABLE, repository.findById(DriverId(driverId))?.availability)
    }

    // --- ADR-065: Driver Earnings from Self-Stated Prices ---

    @Test
    fun `getting milestones through REST, backed by real PostgreSQL, returns the real earnings totals`() {
        val driverId = "driver-milestones-${UUID.randomUUID()}"
        repository.save(Driver(DriverId(driverId), Availability.UNAVAILABLE))
        milestonesRepository.recordCompletedRide(DriverId(driverId), Instant.now(), statedPriceParsed = 350L)
        milestonesRepository.recordCompletedRide(DriverId(driverId), Instant.now(), statedPriceParsed = null)

        val response = controller.getMilestones(driverId, authorization = driverToken(driverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(2L, body.completedRidesCount)
        assertEquals(350L, body.totalStatedEarnings)
        assertEquals(1, body.unpricedRidesCount)
    }
}
