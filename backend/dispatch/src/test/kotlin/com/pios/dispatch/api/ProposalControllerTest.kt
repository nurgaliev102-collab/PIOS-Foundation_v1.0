package com.pios.dispatch.api

import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.DriverAvailabilityRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
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
 *
 * Task 21 (Proposal API Security Remediation): `createProposal`,
 * `acceptProposal`, `declineProposal`, and `lapseProposal` now require an
 * `Authorization` header too (see [ProposalController]'s own KDoc) --
 * [driverToken], [passengerToken], and [ownerAuth] below (all built on the
 * same [issueToken]/[bearer]/[basicHeader] helpers this class already had
 * for the `?driverId=` case) supply it throughout every test in this file
 * that reaches an endpoint now requiring it. A blank `orderId`/`driverId`
 * on [createProposal] still throws before the new authorization check
 * runs (the value objects' own construction happens first, unchanged), so
 * the two "blank returns 400" tests below deliberately still call
 * `createProposal` with no `authorization` argument at all -- proving that
 * case is unaffected by this task, not merely convenient.
 *
 * ADR-066 (Proposal Participant Authorization): `createProposal` now
 * requires `passengerReference` in the body too -- [passengerToken] always
 * mints `sub = "passenger-test"`, so [proposeRequest] below defaults
 * `passengerReference` to that same value, keeping every pre-existing test
 * call site a one-line change (add `authorization = passengerToken()`
 * stays; the body gains a matching reference for free). `confirmPrice`/
 * `declinePrice` now require the caller to be that same passenger (or the
 * owner); `listProposals`'s `?orderId=` branch and `getProposal` (a sixth
 * surface this ADR closes, beyond the five Task 20/21 named) now require a
 * credential too. See each new section below for the full matrix.
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

    /** Task 21: a valid session token naming [driverId] as its own `drv` -- what `DriverHome.tsx` now sends on accept/decline. */
    private fun driverToken(driverId: String): String = bearer(issueToken(sub = "$driverId-identity", drv = driverId))

    /** Task 21: a valid session token for some authenticated passenger, `drv == null` -- what `RideRequest.tsx` now sends on create. Fixed `sub`, matched by [proposeRequest]'s own default `passengerReference` (ADR-066). */
    private fun passengerToken(): String = bearer(issueToken(sub = "passenger-test"))

    /** ADR-066: a valid session token for a passenger other than [passengerToken]'s own -- for mismatch/IDOR cases. */
    private fun otherPassengerToken(): String = bearer(issueToken(sub = "passenger-other"))

    /** Task 21: the owner/coordinator credential -- what `Coordinator.tsx` now sends on create, and the only credential `lapseProposal` accepts. */
    private fun ownerAuth(): String = basicHeader("owner", ownerPassword)

    /** ADR-066: [ProposeDriverRequest] defaulting `passengerReference` to [passengerToken]'s own `sub`, so every pre-existing call site needs no further change. */
    private fun proposeRequest(
        orderId: String,
        driverId: String,
        isTest: Boolean = false,
        passengerReference: String? = "passenger-test"
    ): ProposeDriverRequest = ProposeDriverRequest(orderId, driverId, isTest, passengerReference)

    @Test
    fun `creating a proposal returns 201 with a new proposal id and OPEN status`() {
        val response = controller.createProposal(proposeRequest("order-1", "driver-1"), authorization = passengerToken())

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.proposalId.isNotBlank())
        assertEquals("order-1", body.orderId)
        assertEquals("driver-1", body.driverId)
        assertEquals("OPEN", body.status)
    }

    @Test
    fun `creating a proposal stamps createdAt, and respondedAt stays null until it is resolved`() {
        val response = controller.createProposal(
            proposeRequest("order-created-at", "driver-created-at"),
            authorization = passengerToken()
        )

        val body = assertNotNull(response.body)
        assertNotNull(body.createdAt)
        assertEquals(null, body.respondedAt)
    }

    @Test
    fun `accepting a proposal returns a respondedAt`() {
        val created = assertNotNull(
            controller.createProposal(
                proposeRequest("order-responded-at", "driver-responded-at"),
                authorization = passengerToken()
            ).body
        )

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-responded-at"))

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
        controller.createProposal(proposeRequest("order-2", "driver-1"), authorization = passengerToken())

        val response = controller.createProposal(proposeRequest("order-2", "driver-2"), authorization = passengerToken())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `accepting a proposal returns 200 with ACCEPTED status`() {
        val created = controller.createProposal(proposeRequest("order-3", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACCEPTED", response.body?.status)
    }

    // --- Stated price (ADR-042) ---

    @Test
    fun `accepting a proposal with a statedPrice returns it in the response`() {
        val created = controller.createProposal(proposeRequest("order-3c", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(
            created.proposalId,
            AcceptProposalRequest(statedPrice = "750"),
            authorization = driverToken("driver-1")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("750", response.body?.statedPrice)
    }

    @Test
    fun `accepting a proposal with no request body succeeds with no statedPrice and still creates an Assignment`() {
        val created = controller.createProposal(proposeRequest("order-3d", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACCEPTED", response.body?.status)
        assertEquals(null, response.body?.statedPrice)
        assertEquals(1, assignmentRepository.findByOrder(OrderReference("order-3d")).size)
    }

    @Test
    fun `accepting a proposal with a request body but no statedPrice field succeeds with no statedPrice`() {
        val created = controller.createProposal(proposeRequest("order-3e", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(created.proposalId, AcceptProposalRequest(), authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedPrice)
    }

    @Test
    fun `accepting a proposal with a blank statedPrice returns 400`() {
        val created = controller.createProposal(proposeRequest("order-3f", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(
            created.proposalId,
            AcceptProposalRequest(statedPrice = "   "),
            authorization = driverToken("driver-1")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `declining a proposal never returns a statedPrice`() {
        val created = controller.createProposal(proposeRequest("order-3g", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.declineProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedPrice)
    }

    // --- Stated time to pickup (ADR-057) ---

    @Test
    fun `accepting a proposal with a statedEtaMinutes returns it in the response`() {
        val created = controller.createProposal(proposeRequest("order-3h", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(
            created.proposalId,
            AcceptProposalRequest(statedEtaMinutes = 5),
            authorization = driverToken("driver-1")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(5, response.body?.statedEtaMinutes)
    }

    @Test
    fun `accepting a proposal with no request body succeeds with no statedEtaMinutes`() {
        val created = controller.createProposal(proposeRequest("order-3i", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedEtaMinutes)
    }

    @Test
    fun `accepting a proposal with a statedEtaMinutes of zero returns 400`() {
        val created = controller.createProposal(proposeRequest("order-3j", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(
            created.proposalId,
            AcceptProposalRequest(statedEtaMinutes = 0),
            authorization = driverToken("driver-1")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `accepting a proposal with a statedEtaMinutes above 240 returns 400`() {
        val created = controller.createProposal(proposeRequest("order-3k", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(
            created.proposalId,
            AcceptProposalRequest(statedEtaMinutes = 241),
            authorization = driverToken("driver-1")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `declining a proposal never returns a statedEtaMinutes`() {
        val created = controller.createProposal(proposeRequest("order-3l", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.declineProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(null, response.body?.statedEtaMinutes)
    }

    @Test
    fun `accepting a proposal through REST also creates an Assignment for the same order and driver`() {
        val created = controller.createProposal(proposeRequest("order-3b", "driver-1"), authorization = passengerToken()).body!!

        controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        val assignments = assignmentRepository.findByOrder(OrderReference("order-3b"))
        assertEquals(1, assignments.size)
        assertEquals("driver-1", assignments.first().driver.driverId)
    }

    @Test
    fun `accepting an unknown proposal id returns 404`() {
        val response = controller.acceptProposal("never-created", authorization = driverToken("driver-x"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `accepting an already-accepted proposal returns 409`() {
        val created = controller.createProposal(proposeRequest("order-4", "driver-1"), authorization = passengerToken()).body!!
        controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `accepting an already-accepted proposal through REST does not create a second Assignment`() {
        val created = controller.createProposal(proposeRequest("order-4b", "driver-1"), authorization = passengerToken()).body!!
        controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(1, assignmentRepository.findByOrder(OrderReference("order-4b")).size)
    }

    @Test
    fun `declining a proposal through REST never creates an Assignment`() {
        val created = controller.createProposal(proposeRequest("order-4c", "driver-1"), authorization = passengerToken()).body!!

        controller.declineProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertTrue(assignmentRepository.findByOrder(OrderReference("order-4c")).isEmpty())
    }

    @Test
    fun `lapsing a proposal through REST never creates an Assignment`() {
        val created = controller.createProposal(proposeRequest("order-4d", "driver-1"), authorization = passengerToken()).body!!

        controller.lapseProposal(created.proposalId, authorization = ownerAuth())

        assertTrue(assignmentRepository.findByOrder(OrderReference("order-4d")).isEmpty())
    }

    @Test
    fun `accepting a proposal for an order that already has an Assignment returns 409`() {
        val order = OrderReference("order-4e")
        assignmentRepository.save(Assignment.create(order, DriverReference("driver-preexisting")).assignment)
        val created = controller.createProposal(proposeRequest("order-4e", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `declining a proposal returns 200 with DECLINED status`() {
        val created = controller.createProposal(proposeRequest("order-5", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.declineProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("DECLINED", response.body?.status)
    }

    @Test
    fun `declining an unknown proposal id returns 404`() {
        val response = controller.declineProposal("never-created", authorization = driverToken("driver-x"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `lapsing a proposal returns 200 with LAPSED status`() {
        val created = controller.createProposal(proposeRequest("order-6", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.lapseProposal(created.proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("LAPSED", response.body?.status)
    }

    @Test
    fun `lapsing an unknown proposal id returns 404`() {
        val response = controller.lapseProposal("never-created", authorization = ownerAuth())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // --- getProposal (ADR-066, P0 remediation -- [PO DECISION 2], accepted: this endpoint is a sixth surface, beyond the five Task 20/21 named) ---

    @Test
    fun `getting a proposal by id returns 200 with its current state, as the proposal's own passenger`() {
        val created = controller.createProposal(proposeRequest("order-7", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.getProposal(created.proposalId, authorization = passengerToken())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(created.proposalId, response.body?.proposalId)
    }

    @Test
    fun `getting a proposal by id returns 200 with its current state, as the proposal's own driver`() {
        val created = controller.createProposal(proposeRequest("order-7d", "driver-7d"), authorization = passengerToken()).body!!

        val response = controller.getProposal(created.proposalId, authorization = driverToken("driver-7d"))

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getting a proposal by id returns 200 for the owner credential`() {
        val created = controller.createProposal(proposeRequest("order-7o", "driver-7o"), authorization = passengerToken()).body!!

        val response = controller.getProposal(created.proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getting an unknown proposal id returns 404`() {
        val response = controller.getProposal("never-created", authorization = passengerToken())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getting a proposal with no Authorization header returns 401`() {
        val created = controller.createProposal(proposeRequest("order-7-anon", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.getProposal(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting a proposal as neither its passenger nor its driver is forbidden -- IDOR`() {
        val created = controller.createProposal(
            proposeRequest("order-7-idor", "driver-victim-7"),
            authorization = passengerToken()
        ).body!!

        val response = controller.getProposal(created.proposalId, authorization = otherPassengerToken())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `getting a proposal with a bad owner Basic credential returns 401`() {
        val created = controller.createProposal(proposeRequest("order-7-bad-owner", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.getProposal(created.proposalId, authorization = basicHeader("owner", "wrong-password"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting a proposal with a blank id returns 400`() {
        val response = controller.getProposal("", authorization = passengerToken())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- listProposals?orderId= (ADR-066, P0 remediation -- was ADR-060 Decision 4's own named blocker, closed here) ---

    @Test
    fun `listing proposals for an order returns only that order's proposals, to the owner credential`() {
        controller.createProposal(proposeRequest("order-8", "driver-1"), authorization = passengerToken())
        controller.createProposal(proposeRequest("order-9", "driver-2"), authorization = passengerToken())

        val response = controller.listProposals(authorization = ownerAuth(), orderId = "order-8", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.all { it.orderId == "order-8" })
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `listing proposals for an order, as its own passenger, returns that proposal`() {
        controller.createProposal(proposeRequest("order-8p", "driver-1"), authorization = passengerToken())

        val response = controller.listProposals(authorization = passengerToken(), orderId = "order-8p", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(assertNotNull(response.body).isNotEmpty())
    }

    @Test
    fun `listing proposals for an order, as its own driver, returns that proposal`() {
        controller.createProposal(proposeRequest("order-8dr", "driver-8dr"), authorization = passengerToken())

        val response = controller.listProposals(authorization = driverToken("driver-8dr"), orderId = "order-8dr", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(assertNotNull(response.body).isNotEmpty())
    }

    @Test
    fun `listing proposals for an order, as a stranger, returns an empty list -- no existence oracle`() {
        controller.createProposal(proposeRequest("order-8s", "driver-victim-8s"), authorization = passengerToken())

        val response = controller.listProposals(authorization = otherPassengerToken(), orderId = "order-8s", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `listing proposals for an order with no Authorization header returns 401 -- no longer unauthenticated`() {
        controller.createProposal(proposeRequest("order-8-anon", "driver-1"), authorization = passengerToken())

        val response = controller.listProposals(orderId = "order-8-anon", driverId = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `listing proposals for an order with a bad owner Basic credential returns 401`() {
        val response = controller.listProposals(
            authorization = basicHeader("owner", "wrong-password"),
            orderId = "order-8-bad-owner",
            driverId = null
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `listing proposals for an order with none returns an empty list`() {
        val response = controller.listProposals(authorization = ownerAuth(), orderId = "order-never-proposed", driverId = null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `listing proposals with a blank orderId returns 400`() {
        val response = controller.listProposals(authorization = ownerAuth(), orderId = "", driverId = null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing proposals for a driver returns only that driver's proposals`() {
        controller.createProposal(proposeRequest("order-10", "driver-10"), authorization = passengerToken())
        controller.createProposal(proposeRequest("order-11", "driver-11"), authorization = passengerToken())

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

    // --- Authorization on ?driverId= (ADR-060 Decision 4, unchanged and non-regression-tested by ADR-066) ---

    @Test
    fun `driverId with no Authorization header returns 401`() {
        val response = controller.listProposals(orderId = null, driverId = "driver-10")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `driverId for another driver is forbidden -- IDOR`() {
        controller.createProposal(proposeRequest("order-12", "driver-12"), authorization = passengerToken())

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
        controller.createProposal(proposeRequest("order-14", "driver-14"), authorization = passengerToken())

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

    // --- Task 21: Proposal API Security Remediation ---

    @Test
    fun `HTTP create -- a passenger cannot manufacture a proposal for an arbitrary order`() {
        val response = controller.createProposalHttp(
            proposeRequest("order-http-secure", "driver-not-in-any-relationship"),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(repository.findByOrder(OrderReference("order-http-secure")).isEmpty())
    }

    @Test
    fun `HTTP create -- owner coordinator can create a manual proposal`() {
        val response = controller.createProposalHttp(
            proposeRequest("order-http-owner", "driver-owner-selected"),
            authorization = ownerAuth()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `create -- anonymous, no Authorization header, is rejected`() {
        val response = controller.createProposal(proposeRequest("order-sec-create-1", "driver-1"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertTrue(repository.findByOrder(OrderReference("order-sec-create-1")).isEmpty())
    }

    @Test
    fun `create -- any authenticated passenger token succeeds, naming any driverId, deliberately unrestricted`() {
        val response = controller.createProposal(
            proposeRequest("order-sec-create-2", "driver-not-in-any-relationship"),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `create -- the owner credential succeeds, exactly as Coordinator's own manual-assignment flow needs`() {
        val response = controller.createProposal(
            proposeRequest("order-sec-create-3", "driver-1", passengerReference = "coordinator-selected-passenger"),
            authorization = ownerAuth()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `create -- a malformed Authorization header is rejected exactly like a missing one`() {
        val response = controller.createProposal(
            proposeRequest("order-sec-create-4", "driver-1"),
            authorization = "not-a-real-scheme"
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    // --- Proposal Participant Authorization (ADR-066, P0 remediation) ---

    @Test
    fun `create -- a missing passengerReference returns 400, for an otherwise-valid Bearer caller`() {
        val response = controller.createProposal(
            ProposeDriverRequest("order-sec-passenger-missing", "driver-1"),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(repository.findByOrder(OrderReference("order-sec-passenger-missing")).isEmpty())
    }

    @Test
    fun `create -- a blank passengerReference returns 400`() {
        val response = controller.createProposal(
            proposeRequest("order-sec-passenger-blank", "driver-1", passengerReference = "   "),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `create -- a missing passengerReference returns 400, even for the owner credential`() {
        val response = controller.createProposal(
            ProposeDriverRequest("order-sec-passenger-missing-owner", "driver-1"),
            authorization = ownerAuth()
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `create -- a passengerReference not matching the Bearer token's own sub is rejected -- IDOR, no proposal is created under a false identity`() {
        val response = controller.createProposal(
            proposeRequest("order-sec-passenger-mismatch", "driver-1", passengerReference = "passenger-victim"),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(repository.findByOrder(OrderReference("order-sec-passenger-mismatch")).isEmpty())
    }

    @Test
    fun `create -- the owner credential is not compared against sub -- it has none (ADR-044 Decision 6)`() {
        val response = controller.createProposal(
            proposeRequest("order-sec-passenger-owner-any", "driver-1", passengerReference = "any-passenger-the-coordinator-selected"),
            authorization = ownerAuth()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `create -- a matching passengerReference is persisted on the Proposal`() {
        val created = controller.createProposal(
            proposeRequest("order-sec-passenger-persisted", "driver-1"),
            authorization = passengerToken()
        ).body!!

        assertEquals(
            PassengerReference("passenger-test"),
            repository.findById(com.pios.dispatch.domain.ProposalId(created.proposalId))?.passengerReference
        )
    }

    @Test
    fun `create -- passengerReference never appears in the response body -- ADR-066 Decision 6`() {
        val response = controller.createProposal(proposeRequest("order-sec-passenger-not-returned", "driver-1"), authorization = passengerToken())

        // ProposalResponse has no passengerReference field at all -- this
        // test documents the intent (Decision 6) at the call site, not just
        // relying on the type system to make it impossible to regress.
        assertNotNull(response.body)
    }

    @Test
    fun `accept -- driver accepts their own Proposal, succeeds`() {
        val created = controller.createProposal(proposeRequest("order-sec-accept-own", "driver-own"), authorization = passengerToken()).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-own"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACCEPTED", response.body?.status)
    }

    @Test
    fun `accept -- a different driver's Proposal is rejected -- IDOR`() {
        val created = controller.createProposal(
            proposeRequest("order-sec-accept-other", "driver-victim"),
            authorization = passengerToken()
        ).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken("driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("OPEN", repository.findByOrder(OrderReference("order-sec-accept-other")).single().status.name)
    }

    @Test
    fun `accept -- no Authorization header at all is rejected before any proposal lookup`() {
        val created = controller.createProposal(
            proposeRequest("order-sec-accept-anon", "driver-1"),
            authorization = passengerToken()
        ).body!!

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `accept -- a passenger-only token (drv null) is rejected, even for a real proposal`() {
        val created = controller.createProposal(
            proposeRequest("order-sec-accept-passenger-token", "driver-1"),
            authorization = passengerToken()
        ).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = passengerToken())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `decline -- driver declines their own Proposal, succeeds`() {
        val created = controller.createProposal(proposeRequest("order-sec-decline-own", "driver-own"), authorization = passengerToken()).body!!

        val response = controller.declineProposal(created.proposalId, authorization = driverToken("driver-own"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("DECLINED", response.body?.status)
    }

    @Test
    fun `decline -- a different driver's Proposal is rejected -- IDOR`() {
        val created = controller.createProposal(
            proposeRequest("order-sec-decline-other", "driver-victim"),
            authorization = passengerToken()
        ).body!!

        val response = controller.declineProposal(created.proposalId, authorization = driverToken("driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("OPEN", repository.findByOrder(OrderReference("order-sec-decline-other")).single().status.name)
    }

    @Test
    fun `decline -- no Authorization header at all is rejected`() {
        val created = controller.createProposal(
            proposeRequest("order-sec-decline-anon", "driver-1"),
            authorization = passengerToken()
        ).body!!

        val response = controller.declineProposal(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `lapse -- the owner credential succeeds`() {
        val created = controller.createProposal(proposeRequest("order-sec-lapse-owner", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.lapseProposal(created.proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("LAPSED", response.body?.status)
    }

    @Test
    fun `lapse -- no Authorization header is rejected`() {
        val created = controller.createProposal(proposeRequest("order-sec-lapse-anon", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.lapseProposal(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("OPEN", repository.findByOrder(OrderReference("order-sec-lapse-anon")).single().status.name)
    }

    @Test
    fun `lapse -- the named driver's own token is not sufficient -- only the owner may lapse`() {
        val created = controller.createProposal(proposeRequest("order-sec-lapse-driver", "driver-1"), authorization = passengerToken()).body!!

        val response = controller.lapseProposal(created.proposalId, authorization = driverToken("driver-1"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("OPEN", repository.findByOrder(OrderReference("order-sec-lapse-driver")).single().status.name)
    }

    // --- confirm-price / decline-price (ADR-066, P0 remediation -- previously the largest gap: any authenticated account could act on any passenger's price) ---

    @Test
    fun `confirm-price -- the proposal's own passenger succeeds`() {
        val created = controller.createProposal(proposeRequest("order-sec-confirm-own", "driver-cp-1"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-cp-1"))

        val response = controller.confirmPrice(created.proposalId, authorization = passengerToken())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACCEPTED", response.body?.status)
    }

    @Test
    fun `confirm-price -- a different passenger is rejected -- IDOR, this was the largest single gap`() {
        val created = controller.createProposal(proposeRequest("order-sec-confirm-idor", "driver-cp-2"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-cp-2"))

        val response = controller.confirmPrice(created.proposalId, authorization = otherPassengerToken())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("PRICE_PROPOSED", repository.findByOrder(OrderReference("order-sec-confirm-idor")).single().status.name)
    }

    @Test
    fun `confirm-price -- the owner credential succeeds, with no sub to compare`() {
        val created = controller.createProposal(proposeRequest("order-sec-confirm-owner", "driver-cp-3"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-cp-3"))

        val response = controller.confirmPrice(created.proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `confirm-price -- no Authorization header is rejected before any lookup`() {
        val created = controller.createProposal(proposeRequest("order-sec-confirm-anon", "driver-cp-4"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-cp-4"))

        val response = controller.confirmPrice(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `confirm-price -- an unknown proposal id returns 404`() {
        val response = controller.confirmPrice("never-created", authorization = passengerToken())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    /**
     * ADR-066 Decision 8 -- [PO DECISION 1], accepted: a proposal created
     * before this deploys has `passengerReference == null` and fails
     * closed. Constructed directly via the domain factory with no
     * passenger reference (mirroring how a legacy row reconstructs), not
     * through [controller], since every real create path now requires one.
     */
    @Test
    fun `confirm-price -- a legacy proposal with no passengerReference fails closed (403), never treated as anyone-may-act`() {
        val legacyCreated = com.pios.dispatch.domain.Proposal.propose(
            OrderReference("order-sec-confirm-legacy"),
            DriverReference("driver-cp-legacy"),
            passengerReference = null
        )
        repository.save(legacyCreated.proposal)
        legacyCreated.proposal.proposePrice("500")
        repository.save(legacyCreated.proposal)

        val response = controller.confirmPrice(legacyCreated.proposal.id.value, authorization = passengerToken())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `decline-price -- the proposal's own passenger succeeds`() {
        val created = controller.createProposal(proposeRequest("order-sec-declineprice-own", "driver-dp-1"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-dp-1"))

        val response = controller.declinePrice(created.proposalId, authorization = passengerToken())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("DECLINED", response.body?.status)
    }

    @Test
    fun `decline-price -- a different passenger is rejected -- IDOR`() {
        val created = controller.createProposal(proposeRequest("order-sec-declineprice-idor", "driver-dp-2"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-dp-2"))

        val response = controller.declinePrice(created.proposalId, authorization = otherPassengerToken())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("PRICE_PROPOSED", repository.findByOrder(OrderReference("order-sec-declineprice-idor")).single().status.name)
    }

    @Test
    fun `decline-price -- the owner credential succeeds`() {
        val created = controller.createProposal(proposeRequest("order-sec-declineprice-owner", "driver-dp-3"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-dp-3"))

        val response = controller.declinePrice(created.proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `decline-price -- no Authorization header is rejected`() {
        val created = controller.createProposal(proposeRequest("order-sec-declineprice-anon", "driver-dp-4"), authorization = passengerToken()).body!!
        controller.proposePrice(created.proposalId, ProposePriceRequest("500"), authorization = driverToken("driver-dp-4"))

        val response = controller.declinePrice(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `decline-price -- a legacy proposal with no passengerReference fails closed (403)`() {
        val legacyCreated = com.pios.dispatch.domain.Proposal.propose(
            OrderReference("order-sec-declineprice-legacy"),
            DriverReference("driver-dp-legacy"),
            passengerReference = null
        )
        repository.save(legacyCreated.proposal)
        legacyCreated.proposal.proposePrice("500")
        repository.save(legacyCreated.proposal)

        val response = controller.declinePrice(legacyCreated.proposal.id.value, authorization = passengerToken())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
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
     *
     * Task 21: holds its own [rawSecret] and [authorization] token-minting
     * helper (mirroring the outer class's own [issueToken] exactly) so its
     * three tests below can supply a valid `Authorization` header too --
     * previously this fixture's own [SessionTokenVerifier] was constructed
     * with a plain string, not valid base64, which [SessionTokenVerifier]
     * would have silently failed to decode had anything ever tried to
     * verify a token against it (never exercised until this task, since no
     * prior test in this fixture needed authorization at all).
     */
    private class GatedFixture {
        val rawSecret = "gated-fixture-secret".toByteArray()
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
            SessionTokenVerifier(secretBase64 = Base64.getEncoder().encodeToString(rawSecret)),
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

        private val objectMapper = ObjectMapper()

        fun passengerAuthorization(): String {
            val payloadNode = objectMapper.createObjectNode()
            payloadNode.put("sub", "gated-fixture-passenger")
            payloadNode.putNull("drv")
            payloadNode.put("exp", Instant.now().plusSeconds(3600).epochSecond)
            val encodedPayload =
                Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payloadNode))
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(rawSecret, "HmacSHA256"))
            val encodedSignature =
                Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
            return "Bearer $encodedPayload.$encodedSignature"
        }
    }

    @Test
    fun `creating a proposal for a driver with no availability record returns 409`() {
        val fixture = GatedFixture()

        val response = fixture.controller.createProposal(
            ProposeDriverRequest("order-avail-1", "driver-never-toggled", passengerReference = "gated-fixture-passenger"),
            authorization = fixture.passengerAuthorization()
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `creating a proposal for a driver marked unavailable returns 409`() {
        val fixture = GatedFixture()
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-unavailable"), available = false, isTest = false))

        val response = fixture.controller.createProposal(
            ProposeDriverRequest("order-avail-2", "driver-unavailable", passengerReference = "gated-fixture-passenger"),
            authorization = fixture.passengerAuthorization()
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `creating a proposal for a driver marked available returns 201`() {
        val fixture = GatedFixture()
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-available"), available = true, isTest = false))

        val response = fixture.controller.createProposal(
            ProposeDriverRequest("order-avail-3", "driver-available", passengerReference = "gated-fixture-passenger"),
            authorization = fixture.passengerAuthorization()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `available test driver cannot receive a real order`() {
        val fixture = GatedFixture()
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-test"), available = true, isTest = true))

        val response = fixture.controller.createProposal(
            ProposeDriverRequest("order-real", "driver-test", isTest = false, passengerReference = "gated-fixture-passenger"),
            authorization = fixture.passengerAuthorization()
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `available driver with unknown classification cannot receive a real order`() {
        val fixture = GatedFixture()
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-unknown"), available = true))

        val response = fixture.controller.createProposal(
            ProposeDriverRequest("order-real", "driver-unknown", isTest = false, passengerReference = "gated-fixture-passenger"),
            authorization = fixture.passengerAuthorization()
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `creating a proposal with isTest true returns and persists isTest true`() {
        val response = controller.createProposal(
            proposeRequest("order-e2e", "driver-e2e", isTest = true),
            authorization = passengerToken()
        )

        assertEquals(true, assertNotNull(response.body).isTest)
        assertEquals(true, repository.findByOrder(OrderReference("order-e2e")).single().isTest)
    }

    @Test
    fun `creating a proposal without isTest defaults to isTest false -- a real proposal is never marked test by omission`() {
        val response = controller.createProposal(proposeRequest("order-real", "driver-real"), authorization = passengerToken())

        assertEquals(false, assertNotNull(response.body).isTest)
    }

    // --- FR-003A: Fallback Dispatch after an explicit decline ---

    /** Adds [findLongestIdleAvailable] support, mirroring [FallbackDispatchApplicationServiceTest]'s own private fake exactly. */
    private class InMemoryFallbackDriverAvailabilityRepository : DriverAvailabilityRepository {
        private val recordsInOrder = mutableListOf<DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")

        override fun upsert(record: DriverAvailabilityRecord) {
            recordsInOrder.removeAll { it.driverReference == record.driverReference }
            recordsInOrder.add(record)
        }

        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            recordsInOrder.lastOrNull { it.driverReference == driverReference }

        // ADR-069 Part 3: fail-closed, strict-equality predicate, mirroring
        // FallbackDispatchApplicationServiceTest's own fake exactly.
        override fun findLongestIdleAvailable(orderIsTest: Boolean, excluding: Set<DriverReference>): DriverReference? =
            recordsInOrder.firstOrNull { it.available && it.driverReference !in excluding && it.isTest == orderIsTest }?.driverReference
    }

    /**
     * A third [ProposalController], wired with a real
     * [com.pios.dispatch.application.PrimaryDriverRepository]/
     * [com.pios.dispatch.application.FallbackDispatchApplicationService]
     * this time -- kept separate from [controller] above for the identical
     * reason [GatedFixture] already is (none of this file's other tests
     * concern Fallback Dispatch).
     */
    private class FallbackDispatchFixture {
        val availabilityRepository = InMemoryFallbackDriverAvailabilityRepository()
        val primaryDriverRepository = com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository()
        val repository = InMemoryProposalRepository()
        val service = ProposalApplicationService(repository, driverAvailabilityRepository = availabilityRepository)
        val assignmentRepository = InMemoryAssignmentRepository()
        val assignmentService = DispatchAssignmentApplicationService(assignmentRepository)
        val orchestrationService = ProposalAssignmentOrchestrationService(repository, service, assignmentService)
        val fallbackDispatchApplicationService = com.pios.dispatch.application.FallbackDispatchApplicationService(availabilityRepository, service)
        val rawSecret = "fallback-dispatch-fixture-secret".toByteArray()
        val controller = ProposalController(
            service,
            orchestrationService,
            repository,
            SessionTokenVerifier(secretBase64 = Base64.getEncoder().encodeToString(rawSecret)),
            OwnerCredentialGate(
                configuredUsername = "",
                configuredPasswordHash = "",
                configuredPasswordSalt = "",
                iterations = 1000,
                failureDelayMillis = 0,
                maxFailuresPerWindow = 1000,
                windowMillis = 900_000
            ),
            primaryDriverRepository,
            fallbackDispatchApplicationService
        )

        private val objectMapper = ObjectMapper()

        private fun token(sub: String, drv: String?): String {
            val payloadNode = objectMapper.createObjectNode()
            payloadNode.put("sub", sub)
            if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
            payloadNode.put("exp", Instant.now().plusSeconds(3600).epochSecond)
            val encodedPayload =
                Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payloadNode))
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(rawSecret, "HmacSHA256"))
            val encodedSignature =
                Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
            return "Bearer $encodedPayload.$encodedSignature"
        }

        fun passengerAuthorization(passengerId: String = "fallback-fixture-passenger"): String = token(passengerId, null)
        fun driverAuthorization(driverId: String): String = token("$driverId-identity", driverId)
    }

    @Test
    fun `declining as the primary driver triggers a real Fallback Dispatch proposal for an available driver`() {
        val fixture = FallbackDispatchFixture()
        val passenger = "fallback-fixture-passenger"
        val primaryDriver = "driver-primary"
        val fallbackDriver = DriverReference("driver-fallback")
        fixture.primaryDriverRepository.upsert(
            com.pios.dispatch.application.PrimaryDriverRecord(PassengerReference(passenger), DriverReference(primaryDriver))
        )
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference(primaryDriver), available = true, isTest = false))
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(fallbackDriver, available = true, isTest = false))
        val created = fixture.controller.createProposal(
            ProposeDriverRequest("order-decline-fallback", primaryDriver, passengerReference = passenger),
            authorization = fixture.passengerAuthorization()
        ).body!!

        val response = fixture.controller.declineProposal(created.proposalId, authorization = fixture.driverAuthorization(primaryDriver))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("DECLINED", response.body?.status)
        val proposalsForOrder = fixture.repository.findByOrder(OrderReference("order-decline-fallback"))
        assertEquals(2, proposalsForOrder.size, "the declined primary proposal, plus a new Fallback Dispatch proposal")
        val fallbackProposal = proposalsForOrder.single { it.id.value != created.proposalId }
        assertEquals(fallbackDriver, fallbackProposal.driver)
        assertEquals("OPEN", fallbackProposal.status.name)
    }

    @Test
    fun `declining as a driver who is not the current primary does not trigger Fallback Dispatch`() {
        // Simulates a Fallback Dispatch driver's own decline -- must not
        // automatically try a second, third, ... driver (FallbackDispatchApplicationService's
        // own "No retry on decline/lapse" scope boundary).
        val fixture = FallbackDispatchFixture()
        val passenger = "fallback-fixture-passenger"
        fixture.primaryDriverRepository.upsert(
            com.pios.dispatch.application.PrimaryDriverRecord(PassengerReference(passenger), DriverReference("driver-actual-primary"))
        )
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-not-primary"), available = true, isTest = false))
        fixture.availabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-would-be-fallback"), available = true, isTest = false))
        val created = fixture.controller.createProposal(
            ProposeDriverRequest("order-decline-no-fallback", "driver-not-primary", passengerReference = passenger),
            authorization = fixture.passengerAuthorization()
        ).body!!

        val response = fixture.controller.declineProposal(created.proposalId, authorization = fixture.driverAuthorization("driver-not-primary"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, fixture.repository.findByOrder(OrderReference("order-decline-no-fallback")).size, "no second, Fallback Dispatch proposal must be created")
    }
}
