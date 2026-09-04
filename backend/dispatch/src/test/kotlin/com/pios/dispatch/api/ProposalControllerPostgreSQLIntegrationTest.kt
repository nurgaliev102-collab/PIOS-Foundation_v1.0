package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.PostgreSQLAssignmentRepository
import com.pios.dispatch.persistence.PostgreSQLProposalRepository
import com.pios.dispatch.persistence.PostgreSQLTestDatabase
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The full Proposal vertical slice, exercised against a real PostgreSQL
 * database (Sprint IMPLEMENTATION-003, Proposal Vertical Slice):
 *
 * REST ([ProposalController]) -> Application Service
 * ([ProposalApplicationService]) -> Repository
 * ([PostgreSQLProposalRepository]) -> PostgreSQL (`proposals`, migrated by
 * `V4__proposals.sql`).
 *
 * Since Sprint IMPLEMENTATION-004 (Proposal → Assignment Orchestration),
 * also proves the full extended chain: accepting a Proposal through REST
 * creates a real Assignment, persisted through
 * [PostgreSQLAssignmentRepository] into the pre-existing `assignments`
 * table, via [ProposalAssignmentOrchestrationService].
 *
 * Constructs the controller directly, no Spring MVC context, exactly as
 * [ProposalControllerTest] and [AssignmentControllerTest] already do --
 * only the repositories underneath are the real PostgreSQL adapters
 * instead of the in-memory ones, so every assertion here proves the whole
 * chain commits to and reads back from the actual database.
 *
 * Task 21 (Proposal API Security Remediation): `createProposal`,
 * `acceptProposal`, `declineProposal`, and `lapseProposal` now require an
 * `Authorization` header (see [ProposalController]'s own KDoc). Token
 * minting and owner-credential configuration mirror
 * [ProposalControllerTest]'s own already-established pattern exactly --
 * this module has no build-time dependency on `identity` (ADR-055
 * Decision 1), so a token is minted locally, in the exact format
 * `SessionTokenIssuer` mints and this module's own [SessionTokenVerifier]
 * checks. The owner credential (previously left unconfigured here, since
 * no test yet exercised it) is now configured, matching the real
 * production shape (`pios.owner.*`), so [lapseProposal]'s own new
 * owner-only requirement can be exercised against a real, valid
 * credential rather than only its failure path.
 *
 * Fixture isolation fix (Second Security Gate, 2026-09-04): every
 * order/driver identifier below used to be a fixed literal
 * (`"postgres-vertical-order-3"`, etc.), so a fresh run of this class
 * against an already-populated `pios_dispatch_test` (left over from an
 * earlier run of this same file) collided with its own prior rows --
 * `postgres-vertical-order-3` already `ACCEPTED` from a previous run made
 * *this* run's own "create then accept" sequence see a `409 CONFLICT` it
 * did not expect, which is exactly the failure mode the Second Security
 * Gate audit root-caused via a read-only `SELECT` against the test
 * database (never production). Every identifier is now suffixed with
 * [UUID.randomUUID] -- the same fix [AssignmentControllerPostgreSQLSecurityTest]/
 * [DriverControllerPostgreSQLSecurityTest]/
 * `OrderCancellationControllerPostgreSQLSecurityTest` (order-management)
 * already used from the start, per each of their own KDoc. This changes
 * no assertion's meaning: each test still creates exactly the same
 * relationships it always did, just under identifiers unique to this one
 * run, so leftover rows from any prior run -- this file's own historical
 * ones included -- can never again collide with a fresh one.
 */
class ProposalControllerPostgreSQLIntegrationTest {

    private val repository = PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = ProposalApplicationService(repository)
    private val assignmentRepository = PostgreSQLAssignmentRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val assignmentService = DispatchAssignmentApplicationService(assignmentRepository)
    private val orchestrationService = ProposalAssignmentOrchestrationService(
        repository,
        service,
        assignmentService
    )
    private val rawSecret = "proposal-postgres-integration-test-secret".toByteArray()
    private val secret = Base64.getEncoder().encodeToString(rawSecret)
    private val ownerSalt = "proposal-postgres-integration-owner-salt".toByteArray()
    private val ownerIterations = 1000
    private val ownerPassword = "owner-password"
    private val controller = ProposalController(
        service,
        orchestrationService,
        repository,
        SessionTokenVerifier(secretBase64 = secret),
        OwnerCredentialGate(
            configuredUsername = "owner",
            configuredPasswordHash = Base64.getEncoder().encodeToString(deriveKey(ownerPassword, ownerSalt, ownerIterations)),
            configuredPasswordSalt = Base64.getEncoder().encodeToString(ownerSalt),
            iterations = ownerIterations,
            failureDelayMillis = 0,
            maxFailuresPerWindow = 1000,
            windowMillis = 900_000
        )
    )

    // --- Token minting test helper (see class KDoc; mirrors ProposalControllerTest's own) ---

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, drv: String? = null, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(rawSecret, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun bearer(token: String): String = "Bearer $token"

    private fun driverToken(driverId: String): String = bearer(issueToken(sub = "$driverId-identity", drv = driverId))

    private fun passengerToken(): String = bearer(issueToken(sub = "postgres-vertical-passenger-${UUID.randomUUID()}"))

    private fun ownerAuth(): String =
        "Basic " + Base64.getEncoder().encodeToString("owner:$ownerPassword".toByteArray())

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Fixture isolation fix (see class KDoc): a fresh, unique order id every call, never a fixed literal. */
    private fun uniqueOrderId(label: String): String = "postgres-vertical-order-$label-${UUID.randomUUID()}"

    /** Same reasoning as [uniqueOrderId], for driver identifiers. */
    private fun uniqueDriverId(label: String): String = "postgres-vertical-driver-$label-${UUID.randomUUID()}"

    @Test
    fun `creating a proposal through REST persists it to PostgreSQL, loadable by id`() {
        val order = uniqueOrderId("1")
        val driver = uniqueDriverId("1")
        val created = controller.createProposal(
            ProposeDriverRequest(order, driver),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.CREATED, created.statusCode)
        val body = assertNotNull(created.body)

        val loaded = controller.getProposal(body.proposalId)

        assertEquals(HttpStatus.OK, loaded.statusCode)
        assertEquals("OPEN", loaded.body?.status)
        assertEquals(order, loaded.body?.orderId)
        assertEquals(driver, loaded.body?.driverId)
    }

    @Test
    fun `creating a proposal through REST with no Authorization header is rejected`() {
        val response = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("1-anon"), uniqueDriverId("1"))
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `creating a second proposal through REST for an order with an open proposal already in PostgreSQL is rejected`() {
        val order = uniqueOrderId("2")
        controller.createProposal(
            ProposeDriverRequest(order, uniqueDriverId("1")),
            authorization = passengerToken()
        )

        val second = controller.createProposal(
            ProposeDriverRequest(order, uniqueDriverId("2")),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
    }

    @Test
    fun `accepting a proposal through REST persists ACCEPTED to PostgreSQL`() {
        val driver = uniqueDriverId("1")
        val created = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("3"), driver),
            authorization = passengerToken()
        ).body!!

        val accepted = controller.acceptProposal(created.proposalId, authorization = driverToken(driver))

        assertEquals(HttpStatus.OK, accepted.statusCode)
        assertEquals("ACCEPTED", accepted.body?.status)
        assertEquals("ACCEPTED", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `accepting a proposal through REST as a different driver, backed by PostgreSQL, is rejected`() {
        val driver1 = uniqueDriverId("1")
        val driver2 = uniqueDriverId("2")
        val created = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("3-idor"), driver1),
            authorization = passengerToken()
        ).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken(driver2))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("OPEN", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `accepting a proposal through REST also persists a real Assignment to PostgreSQL`() {
        val order = OrderReference(uniqueOrderId("3b"))
        val driver = uniqueDriverId("1")
        val created = controller.createProposal(
            ProposeDriverRequest(order.orderId, driver),
            authorization = passengerToken()
        ).body!!

        controller.acceptProposal(created.proposalId, authorization = driverToken(driver))

        val assignments = assignmentRepository.findByOrder(order)
        assertEquals(1, assignments.size)
        assertEquals(driver, assignments.first().driver.driverId)
    }

    @Test
    fun `accepting an already-accepted proposal through REST does not create a second Assignment in PostgreSQL`() {
        val order = OrderReference(uniqueOrderId("3c"))
        val driver = uniqueDriverId("1")
        val created = controller.createProposal(
            ProposeDriverRequest(order.orderId, driver),
            authorization = passengerToken()
        ).body!!
        controller.acceptProposal(created.proposalId, authorization = driverToken(driver))

        val second = controller.acceptProposal(created.proposalId, authorization = driverToken(driver))

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `an order with a pre-existing Assignment in PostgreSQL rejects acceptance of a new Proposal`() {
        val order = OrderReference(uniqueOrderId("3d"))
        val driver = uniqueDriverId("1")
        assignmentRepository.save(
            Assignment.create(order, DriverReference(uniqueDriverId("preexisting"))).assignment
        )
        val created = controller.createProposal(
            ProposeDriverRequest(order.orderId, driver),
            authorization = passengerToken()
        ).body!!

        val response = controller.acceptProposal(created.proposalId, authorization = driverToken(driver))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `declining a proposal through REST persists DECLINED to PostgreSQL`() {
        val driver = uniqueDriverId("1")
        val created = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("4"), driver),
            authorization = passengerToken()
        ).body!!

        val declined = controller.declineProposal(created.proposalId, authorization = driverToken(driver))

        assertEquals(HttpStatus.OK, declined.statusCode)
        assertEquals("DECLINED", declined.body?.status)
        assertEquals("DECLINED", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `declining a proposal through REST as a different driver, backed by PostgreSQL, is rejected`() {
        val driver1 = uniqueDriverId("1")
        val driver2 = uniqueDriverId("2")
        val created = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("4-idor"), driver1),
            authorization = passengerToken()
        ).body!!

        val response = controller.declineProposal(created.proposalId, authorization = driverToken(driver2))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("OPEN", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `lapsing a proposal through REST persists LAPSED to PostgreSQL`() {
        val created = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("5"), uniqueDriverId("1")),
            authorization = passengerToken()
        ).body!!

        val lapsed = controller.lapseProposal(created.proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, lapsed.statusCode)
        assertEquals("LAPSED", lapsed.body?.status)
        assertEquals("LAPSED", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `lapsing a proposal through REST with no Authorization header is rejected`() {
        val created = controller.createProposal(
            ProposeDriverRequest(uniqueOrderId("5-anon"), uniqueDriverId("1")),
            authorization = passengerToken()
        ).body!!

        val response = controller.lapseProposal(created.proposalId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("OPEN", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `a proposal resolved in PostgreSQL frees its order for a new proposal through REST`() {
        val order = uniqueOrderId("6")
        val driver1 = uniqueDriverId("1")
        val driver2 = uniqueDriverId("2")
        val first = controller.createProposal(
            ProposeDriverRequest(order, driver1),
            authorization = passengerToken()
        ).body!!
        controller.declineProposal(first.proposalId, authorization = driverToken(driver1))

        val second = controller.createProposal(
            ProposeDriverRequest(order, driver2),
            authorization = passengerToken()
        )

        assertEquals(HttpStatus.CREATED, second.statusCode)
    }

    @Test
    fun `listing proposals for an order through REST reflects PostgreSQL state`() {
        val order = uniqueOrderId("7")
        controller.createProposal(
            ProposeDriverRequest(order, uniqueDriverId("1")),
            authorization = passengerToken()
        )

        val listed = controller.listProposals(orderId = order, driverId = null)

        assertEquals(HttpStatus.OK, listed.statusCode)
        assertEquals(1, listed.body?.size)
        assertEquals(order, listed.body?.first()?.orderId)
    }

    @Test
    fun `loading a proposal id never created in PostgreSQL returns 404 through REST`() {
        val response = controller.getProposal("postgres-vertical-never-created-${UUID.randomUUID()}")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
