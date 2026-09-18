package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.HandoffApplicationService
import com.pios.dispatch.application.ProposeHandoffCommand
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryHandoffRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Authorization tests for [HandoffController] (D-07, Handoff Protocol),
 * constructed directly with real, in-memory-backed application services
 * — no Spring MVC context, mirroring [AssignmentControllerTest]'s own
 * established convention exactly, including its own token-minting helper.
 */
class HandoffControllerTest {

    private class InMemoryDriverAvailabilityRepository : com.pios.dispatch.application.DriverAvailabilityRepository {
        private val records = mutableMapOf<DriverReference, DriverAvailabilityRecord>()
        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")
        override fun upsert(record: DriverAvailabilityRecord) {
            records[record.driverReference] = record
        }
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            records[driverReference]
    }

    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val handoffRepository = InMemoryHandoffRepository()
    private val proposalRepository = InMemoryProposalRepository()
    private val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
    private val assignmentService = DispatchAssignmentApplicationService(assignmentRepository, tripRepository = tripRepository)
    private val handoffApplicationService = HandoffApplicationService(
        assignmentRepository, tripRepository, handoffRepository, proposalRepository, driverAvailabilityRepository
    )
    private val secret = Base64.getEncoder().encodeToString("handoff-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = HandoffController(
        handoffApplicationService, handoffRepository, assignmentRepository, proposalRepository, sessionTokenVerifier
    )

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
    private fun passengerToken(passengerId: String): String = bearer(issueToken(sub = passengerId, drv = null))

    private val order = OrderReference("order-1")
    private val originalDriverId = "driver-original"
    private val substituteDriverId = "driver-substitute"
    private val passengerId = "passenger-1"

    private fun seedCommittedRide(): Assignment {
        val passenger = PassengerReference(passengerId)
        val proposal = Proposal.propose(order, DriverReference(originalDriverId), passengerReference = passenger).proposal
        proposal.accept()
        proposalRepository.save(proposal)
        val created = assignmentService.handle(AssignOrderCommand(order, DriverReference(originalDriverId)))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(DriverReference(substituteDriverId), available = true))
        return created.assignment
    }

    // --- Propose: only the original driver may ---

    @Test
    fun `propose is closed to anonymous callers`() {
        val assignment = seedCommittedRide()

        val response = controller.propose(ProposeHandoffRequest(assignment.id.value, substituteDriverId), authorization = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `propose rejects a driver who is not the assignment's own original driver`() {
        val assignment = seedCommittedRide()

        val response = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken("driver-attacker")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(0, handoffRepository.findByAssignmentId(assignment.id).size)
    }

    @Test
    fun `propose succeeds for the assignment's own original driver`() {
        val assignment = seedCommittedRide()

        val response = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("PROPOSED", response.body?.status)
    }

    @Test
    fun `a passenger token cannot propose a handoff on a driver's own assignment`() {
        val assignment = seedCommittedRide()

        val response = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = passengerToken(passengerId)
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // --- Substitute acceptance: only the named substitute may ---

    @Test
    fun `accept-substitute rejects a caller who is not the named substitute`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.acceptSubstitute(proposed.handoffId, authorization = driverToken("driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("PROPOSED", handoffRepository.findById(com.pios.dispatch.domain.HandoffId(proposed.handoffId))?.status?.name)
    }

    @Test
    fun `accept-substitute rejects even the original driver themselves -- they are not the substitute`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(originalDriverId))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `accept-substitute succeeds for the exact named substitute`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("SUBSTITUTE_ACCEPTED", response.body?.status)
    }

    // --- Substitute decline: only the named substitute may, before they accept ---

    @Test
    fun `decline-substitute rejects a caller who is not the named substitute`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.declineSubstitute(proposed.handoffId, authorization = driverToken("driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `decline-substitute rejects the original driver themselves`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.declineSubstitute(proposed.handoffId, authorization = driverToken(originalDriverId))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `decline-substitute succeeds for the exact named substitute and never touches the trip`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.declineSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("REFUSED", response.body?.status)
        assertEquals(originalDriverId, tripRepository.findByAssignmentId(assignment.id)?.executingDriver?.driverId)
    }

    // --- Consent / refusal: only the order's own passenger may ---

    @Test
    fun `consent rejects a caller who is not the order's own passenger`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))

        val response = controller.consent(proposed.handoffId, authorization = passengerToken("passenger-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `consent rejects the original driver -- only the passenger may consent`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))

        val response = controller.consent(proposed.handoffId, authorization = driverToken(originalDriverId))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `consent succeeds for the order's own passenger once the substitute has accepted`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))

        val response = controller.consent(proposed.handoffId, authorization = passengerToken(passengerId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("COMMITTED", response.body?.status)
        assertEquals(substituteDriverId, tripRepository.findByAssignmentId(assignment.id)?.executingDriver?.driverId)
    }

    @Test
    fun `consent through a guest passenger session -- no drv, only a bare sub -- still works, bound to the exact handoff`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))
        // A guest passenger session is, at this layer, indistinguishable from
        // any other passenger session: a bearer token whose `sub` matches
        // the order's own passengerReference and whose `drv` is null --
        // there is no separate "guest" authorization code path to bypass.
        val guestToken = bearer(issueToken(sub = passengerId, drv = null))

        val response = controller.consent(proposed.handoffId, authorization = guestToken)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("COMMITTED", response.body?.status)
    }

    @Test
    fun `refuse rejects a caller who is not the order's own passenger`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.refuse(proposed.handoffId, authorization = passengerToken("passenger-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // --- Withdrawal: only the original driver, and only before consent ---

    @Test
    fun `withdraw rejects a caller who is not the original driver`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.withdraw(proposed.handoffId, authorization = driverToken(substituteDriverId))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `withdraw succeeds for the original driver before consent`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!

        val response = controller.withdraw(proposed.handoffId, authorization = driverToken(originalDriverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("WITHDRAWN", response.body?.status)
    }

    @Test
    fun `withdraw is rejected once the handoff is committed, even for the original driver`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))
        controller.consent(proposed.handoffId, authorization = passengerToken(passengerId))

        val response = controller.withdraw(proposed.handoffId, authorization = driverToken(originalDriverId))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(substituteDriverId, tripRepository.findByAssignmentId(assignment.id)?.executingDriver?.driverId)
    }

    // --- Chaining: the substitute, once executing, is never the "original driver" for authorization purposes ---

    @Test
    fun `a committed substitute cannot propose their own handoff -- they are never the assignment's own original driver`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))
        controller.consent(proposed.handoffId, authorization = passengerToken(passengerId))

        val response = controller.propose(
            ProposeHandoffRequest(assignment.id.value, "driver-third"),
            authorization = driverToken(substituteDriverId)
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // --- Cross-order / listing access ---

    @Test
    fun `list rejects a substitute-driver query for a driver other than the caller themselves`() {
        val response = controller.list(substituteDriverId = "driver-someone-else", authorization = driverToken(originalDriverId))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `list by assignmentId is closed to a stranger who is neither the driver nor the passenger`() {
        val assignment = seedCommittedRide()

        val response = controller.list(assignmentId = assignment.id.value, authorization = driverToken("driver-stranger"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `list by assignmentId succeeds for the current substitute once committed, so they can learn their own handoff's final outcome`() {
        val assignment = seedCommittedRide()
        val proposed = controller.propose(
            ProposeHandoffRequest(assignment.id.value, substituteDriverId),
            authorization = driverToken(originalDriverId)
        ).body!!
        controller.acceptSubstitute(proposed.handoffId, authorization = driverToken(substituteDriverId))
        controller.consent(proposed.handoffId, authorization = passengerToken(passengerId))

        val response = controller.list(assignmentId = assignment.id.value, authorization = driverToken(substituteDriverId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("COMMITTED", response.body?.single()?.status)
    }

    @Test
    fun `list by assignmentId succeeds for the order's own passenger`() {
        val assignment = seedCommittedRide()
        controller.propose(ProposeHandoffRequest(assignment.id.value, substituteDriverId), authorization = driverToken(originalDriverId))

        val response = controller.list(assignmentId = assignment.id.value, authorization = passengerToken(passengerId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `unknown handoff id returns 404, not 500`() {
        val response = controller.consent("never-created-but-valid-id", authorization = passengerToken(passengerId))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `unknown assignment id on propose returns 404`() {
        val response = controller.propose(
            ProposeHandoffRequest("never-created", substituteDriverId),
            authorization = driverToken(originalDriverId)
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
