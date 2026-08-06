package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryProposalRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Decorates [InMemoryProposalRepository] to simulate the exact race
 * ADR-052's own Negative Consequences names: [findOpen] (unaffected) sees a
 * Proposal as `OPEN`, but by the time [findById] is called again inside
 * [ProposalApplicationService.lapseProposal], another actor has already
 * resolved it -- reproduced here by transitioning the Proposal, in place,
 * the first time [findById] is asked for [raceOnProposalId].
 */
private class RaceSimulatingProposalRepository(
    private val delegate: ProposalRepository,
    private val raceOnProposalId: ProposalId
) : ProposalRepository by delegate {
    private var raced = false

    override fun findById(id: ProposalId): Proposal? {
        val proposal = delegate.findById(id) ?: return null
        if (!raced && id == raceOnProposalId && proposal.status == ProposalStatus.OPEN) {
            raced = true
            proposal.accept()
        }
        return proposal
    }
}

/**
 * Mirrors [ProposalApplicationServiceTest]'s own style. Proves
 * [ProposalLapseApplicationService] performs exactly what ADR-051 (ownership)
 * and ADR-052 (mechanism) assign to Dispatch's application layer: detect an
 * `OPEN` Proposal older than the configured timeout and invoke
 * `Proposal.lapse()` through [ProposalApplicationService]'s own established
 * path -- never touching the aggregate directly.
 */
class ProposalLapseApplicationServiceTest {

    private val repository = InMemoryProposalRepository()
    private val proposalApplicationService = ProposalApplicationService(repository)
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    @Test
    fun `a proposal older than the timeout is lapsed`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        val service = ProposalLapseApplicationService(repository, proposalApplicationService, timeoutMinutes = 5)
        val wellAfterTimeout = Instant.now().plusSeconds(6 * 60)

        service.lapseStaleProposals(now = wellAfterTimeout)

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `a proposal younger than the timeout is left OPEN`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        val service = ProposalLapseApplicationService(repository, proposalApplicationService, timeoutMinutes = 5)

        service.lapseStaleProposals(now = Instant.now())

        assertEquals(ProposalStatus.OPEN, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `an already-accepted proposal is never touched even if old`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        proposalApplicationService.acceptProposal(proposal, AcceptProposalCommand(proposal.id))
        val service = ProposalLapseApplicationService(repository, proposalApplicationService, timeoutMinutes = 5)
        val wellAfterTimeout = Instant.now().plusSeconds(6 * 60)

        service.lapseStaleProposals(now = wellAfterTimeout)

        assertEquals(ProposalStatus.ACCEPTED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `an already-declined proposal is never touched even if old`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        proposalApplicationService.declineProposal(proposal, DeclineProposalCommand(proposal.id))
        val service = ProposalLapseApplicationService(repository, proposalApplicationService, timeoutMinutes = 5)
        val wellAfterTimeout = Instant.now().plusSeconds(6 * 60)

        service.lapseStaleProposals(now = wellAfterTimeout)

        assertEquals(ProposalStatus.DECLINED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `a proposal resolved by another actor between findOpen and lapseProposal is treated as already-resolved, not a failure`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        val racingRepository = RaceSimulatingProposalRepository(repository, proposal.id)
        val racingProposalApplicationService = ProposalApplicationService(racingRepository)
        val service = ProposalLapseApplicationService(racingRepository, racingProposalApplicationService, timeoutMinutes = 0)

        service.lapseStaleProposals(now = Instant.now().plusSeconds(1))

        assertEquals(ProposalStatus.ACCEPTED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `sweeping with no open proposals does nothing`() {
        val service = ProposalLapseApplicationService(repository, proposalApplicationService, timeoutMinutes = 5)

        service.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))
    }
}
