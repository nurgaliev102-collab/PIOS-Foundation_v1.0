package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptProposalCommand
import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves ADR-036's actual guarantee against a real PostgreSQL database and a
 * real [SpringTransactionRunner] — the one thing
 * [com.pios.dispatch.application.ProposalAssignmentOrchestrationServiceTest]'s
 * own in-memory/[com.pios.dispatch.application.NoOpTransactionRunner]
 * harness cannot show, since it has no rollback mechanism at all. Mirrors
 * the established rollback-proof pattern already used for
 * [DriverAvailabilityProjectionTransactionTest] and
 * [AssignmentOutboxTransactionTest]: real repositories, a real transaction,
 * and assertions made directly against what actually committed.
 */
class ProposalAssignmentOrchestrationTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val assignmentRepository = PostgreSQLAssignmentRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))

    private val proposalApplicationService = ProposalApplicationService(proposalRepository, transactionRunner)
    private val dispatchAssignmentApplicationService =
        DispatchAssignmentApplicationService(assignmentRepository, transactionRunner = transactionRunner)
    private val orchestrationService = ProposalAssignmentOrchestrationService(
        proposalRepository,
        proposalApplicationService,
        dispatchAssignmentApplicationService,
        transactionRunner
    )

    @Test
    fun `accepting a proposal commits the Proposal and the Assignment together, atomically`() {
        val order = OrderReference("tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("tx-driver-${UUID.randomUUID()}")
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(ProposalStatus.ACCEPTED, proposalRepository.findById(proposal.id)?.status)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `a real Assignment invariant rejection rolls the Proposal back to OPEN instead of leaving it ACCEPTED`() {
        val order = OrderReference("tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("tx-driver-${UUID.randomUUID()}")
        assignmentRepository.save(Assignment.create(order, DriverReference("tx-driver-preexisting")).assignment)
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        assertFailsWith<IllegalStateException> {
            orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))
        }

        // Before ADR-036's implementation, this Proposal would have been left
        // ACCEPTED here, with no corresponding second Assignment -- exactly the
        // failure window ADR-036 exists to close. The shared transaction now
        // rolls the Proposal's ACCEPTED write back along with the rejected
        // Assignment creation, so it reads OPEN, unchanged, after the failure.
        assertEquals(ProposalStatus.OPEN, proposalRepository.findById(proposal.id)?.status)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `a transient infrastructure failure creating the Assignment also rolls the Proposal back to OPEN`() {
        val order = OrderReference("tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("tx-driver-${UUID.randomUUID()}")
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        val failingAssignmentRepository = object : AssignmentRepository {
            override fun save(assignment: Assignment): Unit =
                throw RuntimeException("simulated transient infrastructure failure")
            override fun findById(id: AssignmentId): Assignment? = assignmentRepository.findById(id)
            override fun findByOrder(order: OrderReference): List<Assignment> = assignmentRepository.findByOrder(order)
        }
        val failingAssignmentService =
            DispatchAssignmentApplicationService(failingAssignmentRepository, transactionRunner = transactionRunner)
        val failingOrchestrationService = ProposalAssignmentOrchestrationService(
            proposalRepository,
            proposalApplicationService,
            failingAssignmentService,
            transactionRunner
        )

        assertFailsWith<RuntimeException> {
            failingOrchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))
        }

        // Not just a business-rule rejection: a plain infrastructure failure on
        // the Assignment side rolls the Proposal back too (ADR-036, Operational
        // Consequences) -- proven here against the real, unmodified Proposal
        // repository and the real shared transaction.
        assertEquals(ProposalStatus.OPEN, proposalRepository.findById(proposal.id)?.status)
        assertEquals(0, assignmentRepository.findByOrder(order).size)
    }
}
