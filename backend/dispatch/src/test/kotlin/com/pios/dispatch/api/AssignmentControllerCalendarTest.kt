package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.DispatchRequestRecord
import com.pios.dispatch.application.DispatchRequestRepository
import com.pios.dispatch.application.InMemoryDispatchRequestRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.Assignment
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-11.B (Driver Calendar — read-only, informational view; `ADR-084`).
 * Constructs [AssignmentController] directly, with real in-memory
 * repositories, no Spring MVC context -- mirroring [AssignmentControllerTest]'s
 * own established convention exactly, including its token-minting helper
 * (Task 21/23's own already-established pattern).
 *
 * [dispatchRequestRepository] is the one new collaborator this ADR adds --
 * a plain, dependency-free [InMemoryDispatchRequestRepository] (the same
 * test double already shared across this module's `application`-package
 * tests), giving [requestedPickupAt][DispatchRequestRecord.requestedPickupAt]
 * facts to join against [AssignmentRepository.findByDriver]'s own results,
 * exactly as `AssignmentController.driverCalendar`'s own KDoc describes.
 */
class AssignmentControllerCalendarTest {

    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val dispatchRequestRepository = InMemoryDispatchRequestRepository()
    private val service = DispatchAssignmentApplicationService(assignmentRepository, tripRepository = tripRepository)
    private val secret = Base64.getEncoder().encodeToString("assignment-calendar-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = AssignmentController(
        service, assignmentRepository, tripRepository, sessionTokenVerifier,
        dispatchRequestRepository = dispatchRequestRepository
    )

    // --- Token minting test helper (mirrors AssignmentControllerTest's own) ---

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

    private fun driverToken(driverId: String): String = bearer(issueToken(sub = "$driverId-identity", drv = driverId))

    /** Creates a real, committed Assignment for [driverId]/[orderId] via the application service, exactly like a real accepted Proposal would. */
    private fun acceptedAssignment(orderId: String, driverId: String): Assignment {
        val created = service.handle(AssignOrderCommand(OrderReference(orderId), DriverReference(driverId)))
        return created.assignment
    }

    /** Records [orderId]'s own `requestedPickupAt` in [dispatchRequestRepository], the same fact `V19__dispatch_requests.sql` durably stores in production. */
    private fun routeWithPickup(orderId: String, pickupAt: Instant, now: Instant = Instant.now()) {
        dispatchRequestRepository.insertIfAbsent(
            DispatchRequestRecord(
                orderId = orderId,
                passengerReference = "passenger-calendar",
                isTest = true,
                explicitDriverIntent = false,
                requestedDriverId = null,
                requestedPickupAt = pickupAt,
                submittedAt = now,
                expiresAt = now.plusSeconds(600),
                nextAttemptAt = now
            )
        )
    }

    // --- 1/9: a driver sees their own accepted future rides ---

    @Test
    fun `an authenticated driver sees their own accepted future ride`() {
        acceptedAssignment("order-cal-1", "driver-cal-1")
        routeWithPickup("order-cal-1", Instant.now().plusSeconds(3600))

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-1"), driverId = "driver-cal-1")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(1, body.size)
        assertEquals("order-cal-1", body.single().orderId)
        assertNotNull(body.single().requestedPickupAt)
    }

    // --- 2: the single highest-priority test -- IDOR by driverId manipulation ---

    @Test
    fun `a driver cannot retrieve another driver's calendar by naming a different driverId -- IDOR`() {
        acceptedAssignment("order-cal-victim", "driver-cal-victim")
        routeWithPickup("order-cal-victim", Instant.now().plusSeconds(3600))

        val response = controller.driverCalendarHttp(
            authorization = driverToken("driver-cal-attacker"),
            driverId = "driver-cal-victim"
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(response.body)
    }

    // --- 3: unauthenticated request rejected ---

    @Test
    fun `an unauthenticated request is rejected`() {
        acceptedAssignment("order-cal-anon", "driver-cal-anon")
        routeWithPickup("order-cal-anon", Instant.now().plusSeconds(3600))

        val response = controller.driverCalendarHttp(authorization = null, driverId = "driver-cal-anon")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `a missing driverId is rejected as a malformed request, not a 403`() {
        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-noparam"), driverId = null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- 4/5/6: declined/lapsed/withdrawn proposals never produce a calendar entry (no Assignment is ever created for them) ---

    @Test
    fun `a declined proposal's order is absent from the calendar`() {
        val proposal = com.pios.dispatch.domain.Proposal.propose(
            OrderReference("order-cal-declined"), DriverReference("driver-cal-declined")
        ).proposal
        proposal.decline()

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-declined"), driverId = "driver-cal-declined")

        assertTrue(response.body.orEmpty().isEmpty())
    }

    @Test
    fun `a lapsed proposal's order is absent from the calendar`() {
        val proposal = com.pios.dispatch.domain.Proposal.propose(
            OrderReference("order-cal-lapsed"), DriverReference("driver-cal-lapsed")
        ).proposal
        proposal.lapse()

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-lapsed"), driverId = "driver-cal-lapsed")

        assertTrue(response.body.orEmpty().isEmpty())
    }

    @Test
    fun `a withdrawn proposal's order is absent from the calendar`() {
        val proposal = com.pios.dispatch.domain.Proposal.propose(
            OrderReference("order-cal-withdrawn"), DriverReference("driver-cal-withdrawn")
        ).proposal
        proposal.withdraw()

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-withdrawn"), driverId = "driver-cal-withdrawn")

        assertTrue(response.body.orEmpty().isEmpty())
    }

    // --- 7: a cancelled (terminated) assignment is absent ---

    @Test
    fun `a cancelled -- TERMINATED -- assignment is absent from the calendar`() {
        val assignment = acceptedAssignment("order-cal-terminated", "driver-cal-terminated")
        routeWithPickup("order-cal-terminated", Instant.now().plusSeconds(3600))
        assignment.terminate()
        assignmentRepository.save(assignment)

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-terminated"), driverId = "driver-cal-terminated")

        assertTrue(response.body.orEmpty().isEmpty())
    }

    // --- 8: a historical (past) accepted ride is absent ---

    @Test
    fun `a past accepted ride is absent from the calendar`() {
        acceptedAssignment("order-cal-past", "driver-cal-past")
        routeWithPickup("order-cal-past", Instant.now().minusSeconds(3600))

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-past"), driverId = "driver-cal-past")

        assertTrue(response.body.orEmpty().isEmpty())
    }

    // --- 10/11/12: the ratified 60-minutes-inclusive overlap threshold ---

    @Test
    fun `pickups exactly 60 minutes apart produce a warning -- inclusive boundary`() {
        val base = Instant.now().plusSeconds(7200)
        acceptedAssignment("order-cal-b1", "driver-cal-boundary")
        routeWithPickup("order-cal-b1", base)
        acceptedAssignment("order-cal-b2", "driver-cal-boundary")
        routeWithPickup("order-cal-b2", base.plusSeconds(3600))

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-boundary"), driverId = "driver-cal-boundary")

        val body = assertNotNull(response.body)
        assertEquals(2, body.size)
        assertTrue(body.all { it.hasPotentialOverlap })
    }

    @Test
    fun `pickups less than 60 minutes apart produce a warning`() {
        val base = Instant.now().plusSeconds(7200)
        acceptedAssignment("order-cal-c1", "driver-cal-close")
        routeWithPickup("order-cal-c1", base)
        acceptedAssignment("order-cal-c2", "driver-cal-close")
        routeWithPickup("order-cal-c2", base.plusSeconds(45 * 60))

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-close"), driverId = "driver-cal-close")

        val body = assertNotNull(response.body)
        assertTrue(body.all { it.hasPotentialOverlap })
    }

    @Test
    fun `pickups more than 60 minutes apart produce no warning`() {
        val base = Instant.now().plusSeconds(7200)
        acceptedAssignment("order-cal-d1", "driver-cal-far")
        routeWithPickup("order-cal-d1", base)
        acceptedAssignment("order-cal-d2", "driver-cal-far")
        routeWithPickup("order-cal-d2", base.plusSeconds(61 * 60))

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-far"), driverId = "driver-cal-far")

        val body = assertNotNull(response.body)
        assertEquals(2, body.size)
        assertFalse(body.any { it.hasPotentialOverlap })
    }

    // --- 13: same-driver-only -- another driver's overlapping ride never warns this driver ---

    @Test
    fun `another driver's overlapping-time ride never creates a warning for this driver`() {
        val pickupAt = Instant.now().plusSeconds(7200)
        acceptedAssignment("order-cal-mine", "driver-cal-mine")
        routeWithPickup("order-cal-mine", pickupAt)
        acceptedAssignment("order-cal-other", "driver-cal-other")
        routeWithPickup("order-cal-other", pickupAt)

        val response = controller.driverCalendarHttp(authorization = driverToken("driver-cal-mine"), driverId = "driver-cal-mine")

        val body = assertNotNull(response.body)
        assertEquals(1, body.size)
        assertFalse(body.single().hasPotentialOverlap)
    }

    // --- 14: viewing the calendar performs no write of any kind ---

    @Test
    fun `viewing the calendar performs no write of any kind`() {
        acceptedAssignment("order-cal-nowrite", "driver-cal-nowrite")
        routeWithPickup("order-cal-nowrite", Instant.now().plusSeconds(3600))

        // Delegates every read to the real repositories but fails the test
        // outright if any write method is ever invoked -- the same
        // delegation-guard idiom already established by
        // `ProposalDeclineReopensDispatchRequestTransactionTest`'s own
        // `failingDispatchRequestRepository`.
        val guardedAssignmentRepository = object : com.pios.dispatch.application.AssignmentRepository by assignmentRepository {
            override fun save(assignment: Assignment) = error("unexpected write: AssignmentRepository.save during a calendar read")
        }
        val guardedDispatchRequestRepository = object : DispatchRequestRepository by dispatchRequestRepository {
            override fun insertIfAbsent(record: DispatchRequestRecord) = error("unexpected write: insertIfAbsent during a calendar read")
            override fun markOffered(orderId: String) = error("unexpected write: markOffered during a calendar read")
            override fun markUnfulfilled(orderId: String) = error("unexpected write: markUnfulfilled during a calendar read")
            override fun markCancelled(orderId: String) = error("unexpected write: markCancelled during a calendar read")
            override fun scheduleRetry(orderId: String, nextAttemptAt: Instant) = error("unexpected write: scheduleRetry during a calendar read")
            override fun reopenIfOffered(orderId: String, nextAttemptAt: Instant) = error("unexpected write: reopenIfOffered during a calendar read")
        }
        val guardedController = AssignmentController(
            service, guardedAssignmentRepository, tripRepository, sessionTokenVerifier,
            dispatchRequestRepository = guardedDispatchRequestRepository
        )

        val response = guardedController.driverCalendarHttp(authorization = driverToken("driver-cal-nowrite"), driverId = "driver-cal-nowrite")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.size)
    }

    // --- 15: the calendar cannot influence dispatch -- a new proposal to an "overlapping" driver still succeeds normally ---

    @Test
    fun `a driver with overlapping calendar entries can still be proposed a new order normally`() {
        val base = Instant.now().plusSeconds(7200)
        acceptedAssignment("order-cal-e1", "driver-cal-dispatch")
        routeWithPickup("order-cal-e1", base)
        acceptedAssignment("order-cal-e2", "driver-cal-dispatch")
        routeWithPickup("order-cal-e2", base.plusSeconds(600))
        // Confirm the overlap is real before asserting it has no effect.
        val calendar = assertNotNull(
            controller.driverCalendarHttp(authorization = driverToken("driver-cal-dispatch"), driverId = "driver-cal-dispatch").body
        )
        assertTrue(calendar.all { it.hasPotentialOverlap })

        // A brand-new Proposal to the same driver -- untouched by
        // `ProposalApplicationService.handle`'s own availability check
        // (ADR-084 Part 4 names this application service explicitly as
        // unaffected by this ADR).
        val proposalApplicationService = ProposalApplicationService(com.pios.dispatch.persistence.InMemoryProposalRepository())
        val created = proposalApplicationService.handle(
            ProposeDriverCommand(OrderReference("order-cal-e3-new"), DriverReference("driver-cal-dispatch"))
        )

        assertEquals(com.pios.dispatch.domain.ProposalStatus.OPEN, created.proposal.status)
    }

    // --- 16: the calendar cannot influence proposal acceptance ---

    @Test
    fun `a driver with overlapping calendar entries can still accept a new proposal normally`() {
        val base = Instant.now().plusSeconds(7200)
        acceptedAssignment("order-cal-f1", "driver-cal-accept")
        routeWithPickup("order-cal-f1", base)
        acceptedAssignment("order-cal-f2", "driver-cal-accept")
        routeWithPickup("order-cal-f2", base.plusSeconds(600))

        val proposalRepository = com.pios.dispatch.persistence.InMemoryProposalRepository()
        val proposalApplicationService = ProposalApplicationService(proposalRepository)
        val orchestration = com.pios.dispatch.application.ProposalAssignmentOrchestrationService(
            proposalRepository, proposalApplicationService, service
        )
        val proposal = proposalApplicationService.handle(
            ProposeDriverCommand(OrderReference("order-cal-f3-new"), DriverReference("driver-cal-accept"))
        ).proposal

        val outcome = orchestration.acceptProposal(com.pios.dispatch.application.AcceptProposalCommand(proposal.id))

        assertEquals(com.pios.dispatch.domain.ProposalStatus.ACCEPTED, outcome.proposal.status)
    }
}
