package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [AssignmentController] directly, with a real
 * [DispatchAssignmentApplicationService]/[InMemoryAssignmentRepository],
 * no Spring MVC context -- mirroring this project's own constructor-based
 * testing convention (Tranche 2: Passenger Experience REST Transport).
 *
 * [tripRepository] is shared between [service] and [controller] (Task 12,
 * Trip Ride-Progress Convergence) -- the two must see the same Trip store,
 * exactly as the real Spring-wired beans do, or the controller's own
 * response projection can never observe a ride-progress transition the
 * service just made.
 *
 * Task 23 (Assignment API Security Remediation): `arrive`/`start`/`complete`
 * now require an `Authorization` header (see [AssignmentController]'s own
 * KDoc). Token minting mirrors `ProposalControllerTest`'s own already-
 * established pattern exactly (Task 21) -- this module has no build-time
 * dependency on `identity` and never calls it (ADR-055 Decision 1), so a
 * token is minted locally, in the exact format `SessionTokenIssuer` mints
 * and this module's own [SessionTokenVerifier] checks. `assignOrder` and
 * `listAssignments` are untouched by Task 23 (see that task's own scope);
 * every test exercising them below is therefore unchanged from before this
 * task.
 */
class AssignmentControllerTest {

    private val repository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val service = DispatchAssignmentApplicationService(repository, tripRepository = tripRepository)
    private val secret = Base64.getEncoder().encodeToString("assignment-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = AssignmentController(service, repository, tripRepository, sessionTokenVerifier)

    // --- Token minting test helper (mirrors ProposalControllerTest's own) ---

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

    /** A valid session token naming [driverId] as its own `drv` -- what `DriverHome.tsx` now sends on arrive/start/complete. */
    private fun driverToken(driverId: String): String = bearer(issueToken(sub = "$driverId-identity", drv = driverId))

    @Test
    fun `HTTP manual assignment is closed without owner credentials`() {
        val response = controller.assignOrderHttp(AssignOrderRequest("order-http", "driver-http"), authorization = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertTrue(repository.findByOrder(OrderReference("order-http")).isEmpty())
    }

    @Test
    fun `HTTP assignment listing is closed to anonymous callers`() {
        repository.save(Assignment.create(OrderReference("order-private"), DriverReference("driver-private")).assignment)

        val response = controller.listAssignmentsHttp(authorization = null, orderId = "order-private")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `HTTP assignment listing returns only the authenticated driver's assignment`() {
        repository.save(Assignment.create(OrderReference("order-own"), DriverReference("driver-own")).assignment)

        val own = controller.listAssignmentsHttp(
            authorization = driverToken("driver-own"),
            orderId = "order-own"
        )
        val other = controller.listAssignmentsHttp(
            authorization = driverToken("driver-other"),
            orderId = "order-own"
        )

        assertEquals(1, own.body?.size)
        assertTrue(other.body?.isEmpty() == true)
    }

    @Test
    fun `assigning an order to a driver returns 201 with a new assignment id and CREATED status`() {
        val response = controller.assignOrder(AssignOrderRequest("order-1", "driver-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.assignmentId.isNotBlank())
        assertEquals("CREATED", body.status)
    }

    @Test
    fun `assigning an order persists the assignment, findable by order`() {
        controller.assignOrder(AssignOrderRequest("order-2", "driver-2"))

        val found = repository.findByOrder(OrderReference("order-2"))

        assertTrue(found.any { it.driver == DriverReference("driver-2") })
    }

    @Test
    fun `a blank orderId returns 400`() {
        val response = controller.assignOrder(AssignOrderRequest("", "driver-3"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a blank driverId returns 400`() {
        val response = controller.assignOrder(AssignOrderRequest("order-4", ""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `assigning an order that already has an active assignment returns 409`() {
        val order = OrderReference("order-5")
        repository.save(Assignment.create(order, DriverReference("driver-5")).assignment)

        val response = controller.assignOrder(AssignOrderRequest("order-5", "driver-6"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle) ---

    @Test
    fun `listing assignments without orderId returns 400`() {
        val response = controller.listAssignments(null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing assignments by orderId returns every assignment for that order`() {
        controller.assignOrder(AssignOrderRequest("order-7", "driver-7"))

        val response = controller.listAssignments("order-7")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.orderId == "order-7" && it.driverId == "driver-7" && it.status == "CREATED" })
    }

    // --- orderIds batch mode (Client CRM depth / N+1 fix, purely additive) ---

    @Test
    fun `listing assignments by orderIds returns assignments across every named order`() {
        controller.assignOrder(AssignOrderRequest("order-batch-1", "driver-batch-1"))
        controller.assignOrder(AssignOrderRequest("order-batch-2", "driver-batch-2"))
        controller.assignOrder(AssignOrderRequest("order-batch-3", "driver-batch-3"))

        val response = controller.listAssignments(orderIds = "order-batch-1,order-batch-2")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.orderId == "order-batch-1" && it.driverId == "driver-batch-1" })
        assertTrue(body.any { it.orderId == "order-batch-2" && it.driverId == "driver-batch-2" })
        assertTrue(body.none { it.orderId == "order-batch-3" })
    }

    @Test
    fun `passing both orderId and orderIds returns 400`() {
        val response = controller.listAssignments(orderId = "order-x", orderIds = "order-y,order-z")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a blank id inside orderIds returns 400`() {
        val response = controller.listAssignments(orderIds = "order-batch-1,,order-batch-2")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `arriving a created assignment returns 200 with ARRIVED status`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-8", "driver-8")).body)

        val response = controller.arrive(created.assignmentId, authorization = driverToken("driver-8"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ARRIVED", assertNotNull(response.body).status)
    }

    @Test
    fun `arriving an unknown assignment id returns 404`() {
        val response = controller.arrive("never-created", authorization = driverToken("driver-x"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `starting before arriving returns 409`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-9", "driver-9")).body)

        val response = controller.start(created.assignmentId, authorization = driverToken("driver-9"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `the full arrive-start-complete sequence returns 200 at every step, ending COMPLETED`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-10", "driver-10")).body)

        assertEquals(HttpStatus.OK, controller.arrive(created.assignmentId, authorization = driverToken("driver-10")).statusCode)
        assertEquals(HttpStatus.OK, controller.start(created.assignmentId, authorization = driverToken("driver-10")).statusCode)
        val completed = controller.complete(created.assignmentId, authorization = driverToken("driver-10"))

        assertEquals(HttpStatus.OK, completed.statusCode)
        assertEquals("COMPLETED", assertNotNull(completed.body).status)
    }

    @Test
    fun `the arrive-start-complete sequence stamps arrivedAt, startedAt and completedAt at each step`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-11", "driver-11")).body)

        val arrived = assertNotNull(controller.arrive(created.assignmentId, authorization = driverToken("driver-11")).body)
        assertNotNull(arrived.arrivedAt)

        val started = assertNotNull(controller.start(created.assignmentId, authorization = driverToken("driver-11")).body)
        assertNotNull(started.arrivedAt)
        assertNotNull(started.startedAt)

        val completed = assertNotNull(controller.complete(created.assignmentId, authorization = driverToken("driver-11")).body)
        assertNotNull(completed.arrivedAt)
        assertNotNull(completed.startedAt)
        assertNotNull(completed.completedAt)
    }

    // --- Task 23: Assignment API Security Remediation ---

    @Test
    fun `arrive -- no Authorization header is rejected before any lookup`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-arrive-anon", "driver-sec-1")).body)

        val response = controller.arrive(created.assignmentId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("CREATED", repository.findById(AssignmentId(created.assignmentId))?.status?.name)
    }

    @Test
    fun `arrive -- a malformed Authorization header is rejected exactly like a missing one`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-arrive-malformed", "driver-sec-2")).body)

        val response = controller.arrive(created.assignmentId, authorization = "not-a-real-scheme")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `arrive -- the correct driver's own token succeeds`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-arrive-own", "driver-sec-own")).body)

        val response = controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-own"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ARRIVED", response.body?.status)
    }

    @Test
    fun `arrive -- a different driver's token is rejected -- IDOR`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-arrive-other", "driver-sec-victim")).body)

        val response = controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("CREATED", repository.findById(AssignmentId(created.assignmentId))?.status?.name)
    }

    @Test
    fun `arrive -- a passenger-only token (drv null) is rejected, even for a real assignment`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-arrive-passenger", "driver-sec-3")).body)

        val response = controller.arrive(created.assignmentId, authorization = bearer(issueToken(sub = "some-passenger")))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `start -- no Authorization header is rejected`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-start-anon", "driver-sec-4")).body)
        controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-4"))

        val response = controller.start(created.assignmentId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `start -- a different driver's token is rejected -- IDOR`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-start-other", "driver-sec-victim2")).body)
        controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-victim2"))

        val response = controller.start(created.assignmentId, authorization = driverToken("driver-sec-attacker2"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("ARRIVED", repository.findById(AssignmentId(created.assignmentId))?.let {
            tripRepository.findByAssignmentId(it.id)
        }?.status?.name)
    }

    @Test
    fun `start -- the correct driver's own token succeeds`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-start-own", "driver-sec-5")).body)
        controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-5"))

        val response = controller.start(created.assignmentId, authorization = driverToken("driver-sec-5"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("IN_PROGRESS", response.body?.status)
    }

    @Test
    fun `complete -- no Authorization header is rejected`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-complete-anon", "driver-sec-6")).body)
        controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-6"))
        controller.start(created.assignmentId, authorization = driverToken("driver-sec-6"))

        val response = controller.complete(created.assignmentId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `complete -- a different driver's token is rejected -- IDOR`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-complete-other", "driver-sec-victim3")).body)
        controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-victim3"))
        controller.start(created.assignmentId, authorization = driverToken("driver-sec-victim3"))

        val response = controller.complete(created.assignmentId, authorization = driverToken("driver-sec-attacker3"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        val assignment = repository.findById(AssignmentId(created.assignmentId))
        assertEquals("IN_PROGRESS", assignment?.let { tripRepository.findByAssignmentId(it.id) }?.status?.name)
    }

    @Test
    fun `complete -- the correct driver's own token succeeds`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-complete-own", "driver-sec-7")).body)
        controller.arrive(created.assignmentId, authorization = driverToken("driver-sec-7"))
        controller.start(created.assignmentId, authorization = driverToken("driver-sec-7"))

        val response = controller.complete(created.assignmentId, authorization = driverToken("driver-sec-7"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("COMPLETED", response.body?.status)
    }

    @Test
    fun `arrive -- an unknown assignment id with a valid token still returns 404, not 403`() {
        val response = controller.arrive("never-created-sec", authorization = driverToken("driver-does-not-matter"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `start -- existing business validation (wrong current status) is unchanged by the new auth check`() {
        val created = assertNotNull(controller.assignOrder(AssignOrderRequest("order-sec-start-conflict", "driver-sec-8")).body)

        // Correct driver, correct token -- but the assignment has not arrived yet, so the
        // pre-existing 409 (Trip.start's own precondition) must still fire, unchanged.
        val response = controller.start(created.assignmentId, authorization = driverToken("driver-sec-8"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
