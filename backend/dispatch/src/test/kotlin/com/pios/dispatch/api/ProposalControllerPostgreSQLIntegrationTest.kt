package com.pios.dispatch.api

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
    private val controller = ProposalController(
        service,
        orchestrationService,
        repository,
        SessionTokenVerifier(secretBase64 = "proposal-postgres-integration-test-secret"),
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

    @Test
    fun `creating a proposal through REST persists it to PostgreSQL, loadable by id`() {
        val created = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-1", "postgres-vertical-driver-1"))

        assertEquals(HttpStatus.CREATED, created.statusCode)
        val body = assertNotNull(created.body)

        val loaded = controller.getProposal(body.proposalId)

        assertEquals(HttpStatus.OK, loaded.statusCode)
        assertEquals("OPEN", loaded.body?.status)
        assertEquals("postgres-vertical-order-1", loaded.body?.orderId)
        assertEquals("postgres-vertical-driver-1", loaded.body?.driverId)
    }

    @Test
    fun `creating a second proposal through REST for an order with an open proposal already in PostgreSQL is rejected`() {
        controller.createProposal(ProposeDriverRequest("postgres-vertical-order-2", "postgres-vertical-driver-1"))

        val second = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-2", "postgres-vertical-driver-2"))

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
    }

    @Test
    fun `accepting a proposal through REST persists ACCEPTED to PostgreSQL`() {
        val created = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-3", "postgres-vertical-driver-1")).body!!

        val accepted = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.OK, accepted.statusCode)
        assertEquals("ACCEPTED", accepted.body?.status)
        assertEquals("ACCEPTED", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `accepting a proposal through REST also persists a real Assignment to PostgreSQL`() {
        val order = OrderReference("postgres-vertical-order-3b")
        val created = controller.createProposal(ProposeDriverRequest(order.orderId, "postgres-vertical-driver-1")).body!!

        controller.acceptProposal(created.proposalId)

        val assignments = assignmentRepository.findByOrder(order)
        assertEquals(1, assignments.size)
        assertEquals("postgres-vertical-driver-1", assignments.first().driver.driverId)
    }

    @Test
    fun `accepting an already-accepted proposal through REST does not create a second Assignment in PostgreSQL`() {
        val order = OrderReference("postgres-vertical-order-3c")
        val created = controller.createProposal(ProposeDriverRequest(order.orderId, "postgres-vertical-driver-1")).body!!
        controller.acceptProposal(created.proposalId)

        val second = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `an order with a pre-existing Assignment in PostgreSQL rejects acceptance of a new Proposal`() {
        val order = OrderReference("postgres-vertical-order-3d")
        assignmentRepository.save(
            Assignment.create(order, DriverReference("postgres-vertical-driver-preexisting")).assignment
        )
        val created = controller.createProposal(ProposeDriverRequest(order.orderId, "postgres-vertical-driver-1")).body!!

        val response = controller.acceptProposal(created.proposalId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `declining a proposal through REST persists DECLINED to PostgreSQL`() {
        val created = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-4", "postgres-vertical-driver-1")).body!!

        val declined = controller.declineProposal(created.proposalId)

        assertEquals(HttpStatus.OK, declined.statusCode)
        assertEquals("DECLINED", declined.body?.status)
        assertEquals("DECLINED", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `lapsing a proposal through REST persists LAPSED to PostgreSQL`() {
        val created = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-5", "postgres-vertical-driver-1")).body!!

        val lapsed = controller.lapseProposal(created.proposalId)

        assertEquals(HttpStatus.OK, lapsed.statusCode)
        assertEquals("LAPSED", lapsed.body?.status)
        assertEquals("LAPSED", controller.getProposal(created.proposalId).body?.status)
    }

    @Test
    fun `a proposal resolved in PostgreSQL frees its order for a new proposal through REST`() {
        val first = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-6", "postgres-vertical-driver-1")).body!!
        controller.declineProposal(first.proposalId)

        val second = controller.createProposal(ProposeDriverRequest("postgres-vertical-order-6", "postgres-vertical-driver-2"))

        assertEquals(HttpStatus.CREATED, second.statusCode)
    }

    @Test
    fun `listing proposals for an order through REST reflects PostgreSQL state`() {
        controller.createProposal(ProposeDriverRequest("postgres-vertical-order-7", "postgres-vertical-driver-1"))

        val listed = controller.listProposals(orderId = "postgres-vertical-order-7", driverId = null)

        assertEquals(HttpStatus.OK, listed.statusCode)
        assertEquals(1, listed.body?.size)
        assertEquals("postgres-vertical-order-7", listed.body?.first()?.orderId)
    }

    @Test
    fun `loading a proposal id never created in PostgreSQL returns 404 through REST`() {
        val response = controller.getProposal("postgres-vertical-never-created")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
