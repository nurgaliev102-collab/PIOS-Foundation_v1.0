package com.pios.dispatch.api

import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [ProposalController] directly, with a real
 * [ProposalApplicationService]/[ProposalAssignmentOrchestrationService]/
 * [InMemoryProposalRepository] (and, since Sprint IMPLEMENTATION-004, a
 * real [InMemoryAssignmentRepository] the orchestrator also writes to),
 * no Spring MVC context -- mirroring [AssignmentControllerTest]'s own
 * constructor-based testing convention exactly.
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
    private val controller = ProposalController(service, orchestrationService, repository)

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

        val response = controller.listProposals(orderId = null, driverId = "driver-10")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.all { it.driverId == "driver-10" })
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `listing proposals for a driver with none returns an empty list`() {
        val response = controller.listProposals(orderId = null, driverId = "driver-never-proposed")

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

    @Test
    fun `getting a proposal with a blank id returns 400`() {
        val response = controller.getProposal("")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
