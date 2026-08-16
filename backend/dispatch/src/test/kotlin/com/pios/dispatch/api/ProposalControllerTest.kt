package com.pios.dispatch.api

import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.DriverAvailabilityRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constructs [ProposalController] directly, with a real
 * [ProposalApplicationService]/[ProposalAssignmentOrchestrationService]/
 * [InMemoryProposalRepository] (and, since Sprint IMPLEMENTATION-004, a
 * real [InMemoryAssignmentRepository] the orchestrator also writes to),
 * no Spring MVC context -- mirroring [AssignmentControllerTest]'s own
 * constructor-based testing convention exactly.
 *
 * ADR-060 Decision 4: `?driverId=` now requires a `Bearer` token whose own
 * `drv` matches, or a valid owner `Basic` credential. Token minting mirrors
 * `passenger-experience`'s own `ConnectionControllerTest` and this
 * module's own `OrderQueryControllerTest` (order-management): this module
 * has no build-time dependency on `identity` and never calls it (ADR-055
 * Decision 1), so tokens are minted locally, in the exact format
 * `SessionTokenIssuer` mints and this module's own [SessionTokenVerifier]
 * checks.
 */
class ProposalControllerTest {

    private val repository = InMemoryProposalRepository()
    private val service = ProposalApplicationService(repository)
    private val assignmentRepository = InMemoryAssignmentRepository()
    private val assignmentService = DispatchAssignmentApplicationService(assignmentRepository)
    private val orchestrationService = ProposalAssignmentOrchestrationService(
        repository,
        service,
        assignmentService
    )
    private val secret = Base64.getEncoder().encodeToString("proposal-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val ownerSalt = "proposal-controller-owner-salt".toByteArray()
    private val ownerIterations = 1000
    private val ownerPassword = "owner-password"
    private val ownerCredentialGate = OwnerCredentialGate(
        configuredUsername = "owner",
        configuredPasswordHash = Base64.getEncoder().encodeToString(deriveKey(ownerPassword, ownerSalt, ownerIterations)),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(ownerSalt),
        iterations = ownerIterations,
        failureDelayMillis = 0,
        maxFailuresPerWindow = 1000,
        windowMillis = 900_000
    )
    private val controller = ProposalController(service, orchestrationService, repository, sessionTokenVerifier, ownerCredentialGate)

    // --- Token minting test helper (see class KDoc) ---

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

    private fun bearer(token: String): String = "Bearer $token"

    private fun basicHeader(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    @Test
    fun `creating a proposal returns 201 with a new proposal id and OPEN status`() {
        val response = controller.createProposal(ProposeDriverRequest("order-1", "driver-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.proposalId.isNotBlank())
        assertEquals("order-1", body.orderId)
        assertEquals("driver-1", body.driverId)
        assertEquals("OPEN", body.status)
    }

    @Test
    fun `creating a proposal stamps createdAt, and respondedAt stays null until it is resolved`() {
        val response = controller.createProposal(ProposeDriverRequest("order-created-at", "driver-created-at"))

        val body = assertNotNull(response.body)
        assertNotNull(body.createdAt)
        assertEquals(null, body.respondedAt)
    }

    @Test
    fun `accepting a proposal returns a respondedAt`() {
        val created = assertNotNull(controller.createProposal(ProposeDriverRequest("order-responded-at", "driver-responded-at")).body)

        val response = controller.acceptProposal(created.proposalId)

        assertNotNull(assertNotNull(response.body).respondedAt)
    }

    @Test
    fun `a blank orderId returns 400`() {
        val response = controller.createProposal(ProposeDriverRequest("", "driver-1"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a blank driverId returns 400`() {
        val response = controller.createProposal(ProposeDriverRequest("order-1", ""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `creating a proposal for an order that already has an open proposal returns 409`() {
        controller.createProposal(ProposeDriverRequest("order-2", "driver-1"))

        val response = controller.createProposal(ProposeDriverRequest("order-2", "driver-2"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `accepting a proposal returns 200 with ACCEPTED status`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACCEPTED", response.body?.status)
    }

    // --- Stated price (ADR-042) ---

    @Test
    fun `accepting a proposal with a statedPrice returns it in the response`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3c", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest(statedPrice = "750"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("750", response.body?.statedPrice)
    }

    @Test
    fun `accepting a proposal with no request body succeeds with no statedPrice and still creates an Assignment`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3d", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACCEPTED", response.body?.status)
        assertEquals(null, response.body?.statedPrice)
        assertEquals(1, assignmentRepository.findByOrder(OrderReference("order-3d")).size)
    }

    @Test
    fun `accepting a proposal with a request body but no statedPrice field succeeds with no statedPrice`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3e", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedPrice)
    }

    @Test
    fun `accepting a proposal with a blank statedPrice returns 400`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3f", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest(statedPrice = "   "))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `declining a proposal never returns a statedPrice`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3g", "driver-1")).body!!

        val response = controller.declineProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedPrice)
    }

    // --- Stated time to pickup (ADR-057) ---

    @Test
    fun `accepting a proposal with a statedEtaMinutes returns it in the response`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3h", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest(statedEtaMinutes = 5))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(5, response.body?.statedEtaMinutes)
    }

    @Test
    fun `accepting a proposal with no request body succeeds with no statedEtaMinutes`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3i", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedEtaMinutes)
    }

    @Test
    fun `accepting a proposal with a statedEtaMinutes of zero returns 400`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3j", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest(statedEtaMinutes = 0))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `accepting a proposal with a statedEtaMinutes above 240 returns 400`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3k", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest(statedEtaMinutes = 241))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `declining a proposal never returns a statedEtaMinutes`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3l", "driver-1")).body!!

        val response = controller.declineProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedEtaMinutes)
    }

    @Test
    fun `accepting a proposal through REST also creates an Assignment for the same order and driver`() {
        val created = controller.createProposal(ProposeDriverRequest("order-3b", "driver-1")).body!!

        controller.acceptProposal(created.proposalId)

        val assignments = assignmentRepository.findByOrder(OrderReference("order-3b"))
        assertEquals(1, assignments.size)
        assertEquals("driver-1", assignments.first().driver.driverId)
    }

    @Test
    fun `accepting an unknown proposal id returns 404`() {
        val response = controller.acceptProposal("never-created")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `accepting an already-accepted proposal returns 409`() {
        val created = controller.createProposal(ProposeDriverRequest("order-4", "driver-1")).body!!
        controller.acceptProposal(created.proposalId)

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `accepting an already-accepted proposal through REST does not create a second Assignment`() {
        val created = controller.createProposal(ProposeDriverRequest("order-4b", "driver-1")).body!!
        controller.acceptProposal(created.proposalId)

        controller.acceptProposal(created.proposalId)

        assertEquals(1, assignmentRepository.findByOrder(OrderReference("order-4b")).size)
    }

    @Test
    fun `declining a proposal through REST never creates an Assignment`() {
        val created = controller.createProposal(ProposeDriverRequest("order-4c", "driver-1")).body!!

        controller.declineProposal(created.proposalId)

        assertTrue(assignmentRepository.findByOrder(OrderReference("order-4c")).isEmpty())
    }

    @Test
    fun `lapsing a proposal through REST never creates an Assignment`() {
        val created = controller.createProposal(ProposeDriverRequest("order-4d", "driver-1")).body!!

        controller.lapseProposal(created.proposalId)

        assertTrue(assignmentRepository.findByOrder(OrderReference("order-4d")).isEmpty())
    }

    @Test
    fun `accepting a proposal for an order that already has an Assignment returns 409`() {
        val order = OrderReference("order-4e")
        assignmentRepository.save(Assignment.create(order, DriverReference("driver-preexisting")).assignment)
        val created = controller.createProposal(ProposeDriverRequest("order-4e", "driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `declining a proposal returns 200 with DECLINED status`() {
        val created = controller.createProposal(ProposeDriverRequest("order-5", "driver-1")).body!!

        val response = controller.declineProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("DECLINED", response.body?.status)
    }

    @Test
    fun `declining an unknown proposal id returns 404`() {
        val response = controller.declineProposal("never-created")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `lapsing a proposal returns 200 with LAPSED status`() {
        val created = controller.createProposal(ProposeDriverRequest("order-6", "driver-1")).body!!

        val response = controller.lapseProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("LAPSED", response.body?.status)
    }

    @Test
    fun `lapsing an unknown proposal id returns 404`() {
        val response = controller.lapseProposal("never-created")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getting a proposal by id returns 200 with its current state`() {
        val created = controller.createProposal(ProposeDriverRequest("order-7", "driver-1")).body!!

        val response = controller.getProposal(created.proposalId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(created.proposalId, response.body?.proposalId)
    }

    @Test
    fun `getting an unknown proposal id returns 404`() {
        val response = controller.getProposal("never-created")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `listing proposals for an order returns only that order's proposals`() {
        controller.createProposal(ProposeDriverRequest("order-8", "driver-1"))
        controller.createProposal(ProposeDriverRequest("order-9", "driver-2"))

        val response = controller.listProposals(orderId = "order-8", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.all { it.orderId == "order-8" })
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `listing proposals for an order with none returns an empty list`() {
        val response = controller.listProposals(orderId = "order-never-proposed", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `listing proposals with a blank orderId returns 400`() {
        val response = controller.listProposals(orderId = "", driverId = null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing proposals for a driver returns only that driver's proposals`() {
        controller.createProposal(ProposeDriverRequest("order-10", "driver-10"))
        controller.createProposal(ProposeDriverRequest("order-11", "driver-11"))

        val response = controller.listProposals(
            authorization = bearer(issueToken(sub = "driver-10-identity", drv = "driver-10")),
            orderId = null,
            driverId = "driver-10"
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.all { it.driverId == "driver-10" })
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `listing proposals for a driver with none returns an empty list`() {
        val response = controller.listProposals(
            authorization = bearer(issueToken(sub = "never-proposed-identity", drv = "driver-never-proposed")),
            orderId = null,
            driverId = "driver-never-proposed"
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `listing proposals with neither orderId nor driverId returns 400`() {
        val response = controller.listProposals(orderId = null, driverId = null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing proposals with both orderId and driverId returns 400`() {
        val response = controller.listProposals(orderId = "order-1", driverId = "driver-1")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Authorization on ?driverId= (ADR-060 Decision 4) ---

    @Test
    fun `driverId with no Authorization header returns 401`() {
        val response = controller.listProposals(orderId = null, driverId = "driver-10")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `driverId for another driver is forbidden -- IDOR`() {
        controller.createProposal(ProposeDriverRequest("order-12", "driver-12"))

        val response = controller.listProposals(
            authorization = bearer(issueToken(sub = "driver-13-identity", drv = "driver-13")),
            orderId = null,
            driverId = "driver-12"
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `driverId with a passenger-only token (drv null) is forbidden`() {
        val response = controller.listProposals(
            authorization = bearer(issueToken(sub = "some-passenger")),
            orderId = null,
            driverId = "driver-10"
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `driverId with a valid owner Basic credential returns any named driver's proposals`() {
        controller.createProposal(ProposeDriverRequest("order-14", "driver-14"))

        val response = controller.listProposals(
            authorization = basicHeader("owner", ownerPassword),
            orderId = null,
            driverId = "driver-14"
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.isNotEmpty() == true)
    }

    @Test
    fun `driverId with a bad owner Basic credential returns 401`() {
        val response = controller.listProposals(
            authorization = basicHeader("owner", "wrong-password"),
            orderId = null,
            driverId = "driver-10"
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `orderId stays unauthenticated -- no Authorization header still returns 200, deliberately`() {
        controller.createProposal(ProposeDriverRequest("order-15", "driver-15"))

        val response = controller.listProposals(orderId = "order-15", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getting a proposal with a blank id returns 400`() {
        val response = controller.getProposal("")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Driver availability gate (pilot-readiness fix) ---

    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        val records = mutableMapOf<String, DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")
        override fun upsert(record: DriverAvailabilityRecord) {
            records[record.driverReference.driverId] = record
        }
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            records[driverReference.driverId]
    }

    /**
     * A second [ProposalController], wired with a real
     * [DriverAvailabilityRepository] this time -- kept separate from
     * [controller] above so every other test in this file (none of which
     * concerns availability) keeps constructing its proposals exactly as
     * before, unaffected by this gate.
     */
    private class GatedFixture {
        val availabilityRepository = InMemoryDriverAvailabilityRepository()
        val repository = InMemoryProposalRepository()
        val service = ProposalApplicationService(repository, driverAvailabilityRepository = availabilityRepository)
        val assignmentRepository = InMemoryAssignmentRepository()
        val assignmentService = DispatchAssignmentApplicationService(assignmentRepository)
        val orchestrationService = ProposalAssignmentOrchestrationService(repository, service, assignmentService)
        val controller = ProposalController(
            service,
            orchestrationService,
            repository,
            SessionTokenVerifier(secretBase64 = "gated-fixture-secret"),
            OwnerCredentialGate(
                configuredUsername = "",
                configuredPasswordHash = "",
                configuredPasswordSalt = "",
                iterations = 1000,
                failureDelayMillis = 0,
                maxFailuresPerWindow = 1000,
                windowMillis = 900_000
            )
        )
    }

    @Test
    fun `creating a proposal for a driver with no availability record returns 409`() {
        val fixture = GatedFixture()

        val response = fixture.controller.createProposal(ProposeDriverRequest("order-avail-1", "driver-never-toggled"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `creating a proposal for a driver marked unavailable returns 409`() {
        val fixture = GatedFixture()
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-unavailable"), available = false))

        val response = fixture.controller.createProposal(ProposeDriverRequest("order-avail-2", "driver-unavailable"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `creating a proposal for a driver marked available returns 201`() {
        val fixture = GatedFixture()
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-available"), available = true))

        val response = fixture.controller.createProposal(ProposeDriverRequest("order-avail-3", "driver-available"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }
}
