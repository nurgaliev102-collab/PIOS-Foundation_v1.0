package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.TripStatus
import com.pios.dispatch.persistence.PostgreSQLAssignmentRepository
import com.pios.dispatch.persistence.PostgreSQLTestDatabase
import com.pios.dispatch.persistence.PostgreSQLTripRepository
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
 * Task 23 (Assignment API Security Remediation): proves, against the real,
 * isolated `pios_dispatch_test` PostgreSQL database -- not the in-memory
 * repositories [AssignmentControllerTest] otherwise uses -- that an
 * unauthorized `arrive`/`start`/`complete` call does not mutate the real,
 * persisted Trip, and that an authorized one still does. Mirrors
 * [PostgreSQLAssignmentLifecycleTest]'s own construction exactly
 * (`PostgreSQLAssignmentRepository`/`PostgreSQLTripRepository` over
 * `PostgreSQLTestDatabase.dataSource`), and
 * `ProposalControllerPostgreSQLIntegrationTest`'s own token-minting
 * pattern (Task 21).
 *
 * Every identifier here is randomized ([UUID.randomUUID]), not a fixed
 * literal -- this module's own known, repeated residual-test-data
 * collision pattern (`postgres-vertical-*`, `postgres-lifecycle-*`) is a
 * consequence of exactly the opposite choice in older tests; this file is
 * new, so it is written correctly from the start.
 */
class AssignmentControllerPostgreSQLSecurityTest {

    private val repository = PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val tripRepository = PostgreSQLTripRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = DispatchAssignmentApplicationService(repository, tripRepository = tripRepository)
    private val secret = Base64.getEncoder().encodeToString("assignment-postgres-security-test-secret".toByteArray())
    private val controller = AssignmentController(service, repository, tripRepository, SessionTokenVerifier(secretBase64 = secret))

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
    fun `arrive through REST as a different driver, backed by PostgreSQL, does not mutate the real Trip`() {
        val order = OrderReference("assignment-sec-order-${UUID.randomUUID()}")
        val victimDriver = DriverReference("assignment-sec-driver-victim-${UUID.randomUUID()}")
        val created = service.handle(AssignOrderCommand(order, victimDriver))

        val response = controller.arrive(created.assignment.id.value, authorization = driverToken("assignment-sec-driver-attacker-${UUID.randomUUID()}"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        // Task 11's own trigger creates a Trip (in CREATED status) the moment
        // the Assignment itself is created (service.handle above) -- so a
        // Trip already exists by this point regardless of this test's own
        // rejected arrive call. What this assertion actually proves is that
        // the rejected call did not advance it past CREATED.
        assertEquals(TripStatus.CREATED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `arrive through REST with no Authorization header, backed by PostgreSQL, does not mutate the real Trip`() {
        val order = OrderReference("assignment-sec-order-${UUID.randomUUID()}")
        val driver = DriverReference("assignment-sec-driver-${UUID.randomUUID()}")
        val created = service.handle(AssignOrderCommand(order, driver))

        val response = controller.arrive(created.assignment.id.value)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(TripStatus.CREATED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `the correct driver's own token still moves the real Trip through arrive, start and complete in PostgreSQL`() {
        val order = OrderReference("assignment-sec-order-${UUID.randomUUID()}")
        val driverId = "assignment-sec-driver-${UUID.randomUUID()}"
        val created = service.handle(AssignOrderCommand(order, DriverReference(driverId)))
        val token = driverToken(driverId)

        val arrived = controller.arrive(created.assignment.id.value, authorization = token)
        assertEquals(HttpStatus.OK, arrived.statusCode)
        assertEquals(TripStatus.ARRIVED, tripRepository.findByAssignmentId(created.assignment.id)?.status)

        val started = controller.start(created.assignment.id.value, authorization = token)
        assertEquals(HttpStatus.OK, started.statusCode)
        assertEquals(TripStatus.IN_PROGRESS, tripRepository.findByAssignmentId(created.assignment.id)?.status)

        val completed = controller.complete(created.assignment.id.value, authorization = token)
        assertEquals(HttpStatus.OK, completed.statusCode)
        assertEquals(TripStatus.COMPLETED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `wrong driver cannot move an already-ARRIVED real Trip to IN_PROGRESS`() {
        val order = OrderReference("assignment-sec-order-${UUID.randomUUID()}")
        val driverId = "assignment-sec-driver-${UUID.randomUUID()}"
        val created = service.handle(AssignOrderCommand(order, DriverReference(driverId)))
        controller.arrive(created.assignment.id.value, authorization = driverToken(driverId))

        val response = controller.start(created.assignment.id.value, authorization = driverToken("assignment-sec-driver-attacker-${UUID.randomUUID()}"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(TripStatus.ARRIVED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `wrong driver cannot move an already-IN_PROGRESS real Trip to COMPLETED`() {
        val order = OrderReference("assignment-sec-order-${UUID.randomUUID()}")
        val driverId = "assignment-sec-driver-${UUID.randomUUID()}"
        val created = service.handle(AssignOrderCommand(order, DriverReference(driverId)))
        val token = driverToken(driverId)
        controller.arrive(created.assignment.id.value, authorization = token)
        controller.start(created.assignment.id.value, authorization = token)

        val response = controller.complete(created.assignment.id.value, authorization = driverToken("assignment-sec-driver-attacker-${UUID.randomUUID()}"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(TripStatus.IN_PROGRESS, tripRepository.findByAssignmentId(created.assignment.id)?.status)
        assertNotNull(response)
    }
}
