package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository
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

    // --- FR-003A: Fallback Dispatch after a primary driver's own lapse ---

    /** Adds [findLongestIdleAvailable] support, mirroring [FallbackDispatchApplicationServiceTest]'s own private fake exactly. */
    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        private val recordsInOrder = mutableListOf<DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")

        override fun upsert(record: DriverAvailabilityRecord) {
            recordsInOrder.removeAll { it.driverReference == record.driverReference }
            recordsInOrder.add(record)
        }

        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            recordsInOrder.lastOrNull { it.driverReference == driverReference }

        override fun findLongestIdleAvailable(excluding: Set<DriverReference>): DriverReference? =
            recordsInOrder.firstOrNull { it.available && it.driverReference !in excluding }?.driverReference
    }

    private val passenger = PassengerReference("passenger-1")

    @Test
    fun `a primary driver's own proposal lapsing triggers Fallback Dispatch for an available driver`() {
        val primaryDriverRepository = InMemoryPrimaryDriverRepository()
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, driver))
        val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
        val fallbackDriver = DriverReference("driver-fallback")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(fallbackDriver, available = true))
        val fallbackDispatchApplicationService = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver, passengerReference = passenger)).proposal
        val service = ProposalLapseApplicationService(
            repository, proposalApplicationService, primaryDriverRepository, fallbackDispatchApplicationService, timeoutMinutes = 5
        )

        service.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
        val proposalsForOrder = repository.findByOrder(order)
        assertEquals(2, proposalsForOrder.size, "the lapsed primary proposal, plus a new Fallback Dispatch proposal")
        val fallbackProposal = proposalsForOrder.single { it.id != proposal.id }
        assertEquals(fallbackDriver, fallbackProposal.driver)
        assertEquals(ProposalStatus.OPEN, fallbackProposal.status)
    }

    @Test
    fun `a lapsed proposal for a driver who is not the current primary does not trigger Fallback Dispatch`() {
        // Simulates Fallback Dispatch's own proposal lapsing (or a manually
        // created one for a non-primary driver) -- FallbackDispatchApplicationService's
        // own "No retry on decline/lapse" scope boundary: this must not
        // automatically try a second, third, ... driver.
        val primaryDriverRepository = InMemoryPrimaryDriverRepository()
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, DriverReference("driver-actual-primary")))
        val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-would-be-fallback"), available = true))
        val fallbackDispatchApplicationService = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
        // `driver` ("driver-1") is not the passenger's primary above.
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver, passengerReference = passenger)).proposal
        val service = ProposalLapseApplicationService(
            repository, proposalApplicationService, primaryDriverRepository, fallbackDispatchApplicationService, timeoutMinutes = 5
        )

        service.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
        assertEquals(1, repository.findByOrder(order).size, "no second, Fallback Dispatch proposal must be created")
    }

    @Test
    fun `a lapsed proposal with no passengerReference does not attempt Fallback Dispatch`() {
        val primaryDriverRepository = InMemoryPrimaryDriverRepository()
        val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-fallback"), available = true))
        val fallbackDispatchApplicationService = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal // no passengerReference
        val service = ProposalLapseApplicationService(
            repository, proposalApplicationService, primaryDriverRepository, fallbackDispatchApplicationService, timeoutMinutes = 5
        )

        service.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
        assertEquals(1, repository.findByOrder(order).size)
    }

    @Test
    fun `without primaryDriverRepository or fallbackDispatchApplicationService supplied, lapsing still succeeds and attempts no fallback`() {
        // The default-null backward-compatibility case this class's own
        // KDoc names -- every existing caller/test of this class before
        // this parameter pair's own addition.
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver, passengerReference = passenger)).proposal
        val service = ProposalLapseApplicationService(repository, proposalApplicationService, timeoutMinutes = 5)

        service.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
        assertEquals(1, repository.findByOrder(order).size)
    }

    @Test
    fun `a primary driver's own proposal lapsing with no driver currently available yields no second proposal, and the lapse itself still succeeds`() {
        val primaryDriverRepository = InMemoryPrimaryDriverRepository()
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, driver))
        val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository() // no driver ever recorded
        val fallbackDispatchApplicationService = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver, passengerReference = passenger)).proposal
        val service = ProposalLapseApplicationService(
            repository, proposalApplicationService, primaryDriverRepository, fallbackDispatchApplicationService, timeoutMinutes = 5
        )

        service.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
        assertEquals(1, repository.findByOrder(order).size)
    }
}
