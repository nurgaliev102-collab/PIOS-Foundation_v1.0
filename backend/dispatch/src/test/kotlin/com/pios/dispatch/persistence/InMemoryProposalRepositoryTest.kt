package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptProposalCommand
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [InMemoryProposalRepository] directly (repository-level
 * save/load) as well as through [ProposalApplicationService], mirroring
 * [InMemoryAssignmentRepositoryTest] exactly.
 */
class InMemoryProposalRepositoryTest {

    private val repository = InMemoryProposalRepository()
    private val service = ProposalApplicationService(repository)

    @Test
    fun `proposing a driver through the service persists it, loadable by id`() {
        val created = service.handle(ProposeDriverCommand(OrderReference("order-1"), DriverReference("driver-1")))

        assertEquals(ProposalStatus.OPEN, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `accepting a proposal through the service persists its ACCEPTED status`() {
        val created = service.handle(ProposeDriverCommand(OrderReference("order-2"), DriverReference("driver-2")))

        service.acceptProposal(created.proposal, AcceptProposalCommand(created.proposal.id))

        assertEquals(ProposalStatus.ACCEPTED, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(ProposalId("never-saved")))
    }
}
