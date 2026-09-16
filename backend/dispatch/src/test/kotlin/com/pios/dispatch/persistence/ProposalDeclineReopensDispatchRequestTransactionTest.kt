package com.pios.dispatch.persistence

import com.pios.dispatch.application.DeclineProposalCommand
import com.pios.dispatch.application.DispatchRequestRecord
import com.pios.dispatch.application.DispatchRequestRepository
import com.pios.dispatch.application.DispatchRequestState
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves ADR-078 Decision A's own transactional requirement ("the reopen
 * must be written in the same transaction as the proposal's own
 * resolution") against a real PostgreSQL database and a real
 * [SpringTransactionRunner] -- mirroring
 * [ProposalAssignmentOrchestrationTransactionTest]'s own established
 * rollback-proof pattern exactly: real repositories, a real transaction,
 * and assertions made directly against what actually committed, since an
 * in-memory fake (unlike a real transaction manager) has no rollback
 * mechanism at all and cannot show this.
 */
class ProposalDeclineReopensDispatchRequestTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val dispatchRequestRepository = PostgreSQLDispatchRequestRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))

    private fun routedRecord(orderId: String, now: Instant) = DispatchRequestRecord(
        orderId = orderId,
        passengerReference = "passenger-tx",
        isTest = true,
        explicitDriverIntent = false,
        requestedDriverId = null,
        requestedPickupAt = null,
        submittedAt = now,
        expiresAt = now.plusSeconds(120),
        nextAttemptAt = now
    )

    @Test
    fun `declining a proposal commits the DECLINED status and the row's reopen to PENDING together`() {
        val orderId = "tx-decline-${UUID.randomUUID()}"
        val order = OrderReference(orderId)
        val driver = DriverReference("tx-driver-${UUID.randomUUID()}")
        val proposalApplicationService = ProposalApplicationService(
            proposalRepository, transactionRunner, dispatchRequestRepository = dispatchRequestRepository
        )
        transactionRunner.run { dispatchRequestRepository.insertIfAbsent(routedRecord(orderId, Instant.now())) }
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        transactionRunner.run { dispatchRequestRepository.markOffered(orderId) }

        proposalApplicationService.declineProposal(proposal, DeclineProposalCommand(proposal.id))

        assertEquals(ProposalStatus.DECLINED, proposalRepository.findById(proposal.id)?.status)
        assertEquals(
            DispatchRequestState.PENDING,
            transactionRunner.run { dispatchRequestRepository.findForUpdate(orderId)?.state }
        )
    }

    @Test
    fun `a transient infrastructure failure reopening the row rolls the DECLINED status back too, leaving the row OFFERED`() {
        val orderId = "tx-decline-fail-${UUID.randomUUID()}"
        val order = OrderReference(orderId)
        val driver = DriverReference("tx-driver-${UUID.randomUUID()}")
        val plainProposalApplicationService = ProposalApplicationService(
            proposalRepository, transactionRunner, dispatchRequestRepository = dispatchRequestRepository
        )
        transactionRunner.run { dispatchRequestRepository.insertIfAbsent(routedRecord(orderId, Instant.now())) }
        val proposal = plainProposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        transactionRunner.run { dispatchRequestRepository.markOffered(orderId) }

        // The proposal's own DECLINED write happens first, inside the
        // transaction, then this failing reopen call throws -- proving the
        // whole transaction (both writes) rolls back together, not just the
        // second write in isolation.
        val failingDispatchRequestRepository = object : DispatchRequestRepository by dispatchRequestRepository {
            override fun reopenIfOffered(orderId: String, nextAttemptAt: Instant) {
                throw RuntimeException("simulated transient infrastructure failure")
            }
        }
        val failingProposalApplicationService = ProposalApplicationService(
            proposalRepository, transactionRunner, dispatchRequestRepository = failingDispatchRequestRepository
        )

        assertFailsWith<RuntimeException> {
            failingProposalApplicationService.declineProposal(proposal, DeclineProposalCommand(proposal.id))
        }

        // Not just a business-rule rejection: a plain infrastructure
        // failure on the reopen side rolls the Proposal's own DECLINED
        // write back too -- proven here against the real, unmodified
        // Proposal repository and the real shared transaction.
        assertEquals(ProposalStatus.OPEN, proposalRepository.findById(proposal.id)?.status)
        assertEquals(
            DispatchRequestState.OFFERED,
            transactionRunner.run { dispatchRequestRepository.findForUpdate(orderId)?.state }
        )
    }
}
