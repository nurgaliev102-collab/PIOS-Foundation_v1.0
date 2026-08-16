package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryProposalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Mirrors [DispatchAssignmentApplicationServiceTest] exactly, for the
 * Proposal aggregate's own commands.
 */
class ProposalApplicationServiceTest {

    private val repository = InMemoryProposalRepository()
    private val service = ProposalApplicationService(repository)
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    @Test
    fun `handling a command with no existing proposals creates a new proposal`() {
        val command = ProposeDriverCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, result.proposal.order)
        assertEquals(driver, result.proposal.driver)
    }

    @Test
    fun `handling a command produces an OrderProposed event matching the command`() {
        val command = ProposeDriverCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, result.event.orderId)
        assertEquals(driver, result.event.driverId)
    }

    @Test
    fun `handling a command for an order that already has an open proposal is rejected`() {
        service.handle(ProposeDriverCommand(order, driver))
        val command = ProposeDriverCommand(order, DriverReference("driver-2"))

        assertFailsWith<IllegalStateException> {
            service.handle(command)
        }
    }

    @Test
    fun `handling a command for an order whose only existing proposal already resolved is allowed`() {
        val first = service.handle(ProposeDriverCommand(order, driver))
        service.acceptProposal(first.proposal, AcceptProposalCommand(first.proposal.id))

        val second = service.handle(ProposeDriverCommand(order, DriverReference("driver-2")))

        assertEquals(order, second.proposal.order)
    }

    @Test
    fun `handling a command persists the created proposal through the repository`() {
        val command = ProposeDriverCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, repository.findById(result.proposal.id)?.order)
    }

    @Test
    fun `accepting a proposal matching the command produces a ProposalAccepted event`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        val command = AcceptProposalCommand(proposal.id)

        val event = service.acceptProposal(proposal, command)

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `accepting a proposal whose id does not match the command is rejected`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        val otherProposal = service.handle(ProposeDriverCommand(OrderReference("order-2"), driver)).proposal
        val command = AcceptProposalCommand(otherProposal.id)

        assertFailsWith<IllegalArgumentException> {
            service.acceptProposal(proposal, command)
        }
    }

    @Test
    fun `accepting a proposal persists its ACCEPTED status through the repository`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.acceptProposal(proposal, AcceptProposalCommand(proposal.id))

        assertEquals(ProposalStatus.ACCEPTED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `accepting a proposal by command only restores it from the repository first`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        val event = service.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(order, event.orderId)
        assertEquals(ProposalStatus.ACCEPTED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `accepting a proposal by command only throws ProposalNotFoundException when nothing was saved`() {
        assertFailsWith<ProposalNotFoundException> {
            service.acceptProposal(AcceptProposalCommand(ProposalId("never-saved")))
        }
    }

    @Test
    fun `declining a proposal matching the command produces a ProposalDeclined event`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        val command = DeclineProposalCommand(proposal.id)

        val event = service.declineProposal(proposal, command)

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `declining a proposal persists its DECLINED status through the repository`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.declineProposal(proposal, DeclineProposalCommand(proposal.id))

        assertEquals(ProposalStatus.DECLINED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `declining a proposal by command only restores it from the repository first`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.declineProposal(DeclineProposalCommand(proposal.id))

        assertEquals(ProposalStatus.DECLINED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `declining a proposal by command only throws ProposalNotFoundException when nothing was saved`() {
        assertFailsWith<ProposalNotFoundException> {
            service.declineProposal(DeclineProposalCommand(ProposalId("never-saved")))
        }
    }

    @Test
    fun `lapsing a proposal matching the command produces a ProposalLapsed event`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        val command = LapseProposalCommand(proposal.id)

        val event = service.lapseProposal(proposal, command)

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `lapsing a proposal persists its LAPSED status through the repository`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.lapseProposal(proposal, LapseProposalCommand(proposal.id))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `lapsing a proposal by command only restores it from the repository first`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.lapseProposal(LapseProposalCommand(proposal.id))

        assertEquals(ProposalStatus.LAPSED, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `lapsing a proposal by command only throws ProposalNotFoundException when nothing was saved`() {
        assertFailsWith<ProposalNotFoundException> {
            service.lapseProposal(LapseProposalCommand(ProposalId("never-saved")))
        }
    }

    // --- Withdraw (ADR-053, Proposal Resolution on Order Cancellation) ---

    @Test
    fun `withdrawing a proposal matching the command produces a ProposalWithdrawn event`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        val command = WithdrawProposalCommand(proposal.id)

        val event = service.withdrawProposal(proposal, command)

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `withdrawing a proposal persists its WITHDRAWN status through the repository`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.withdrawProposal(proposal, WithdrawProposalCommand(proposal.id))

        assertEquals(ProposalStatus.WITHDRAWN, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `withdrawing a proposal by command only restores it from the repository first`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.withdrawProposal(WithdrawProposalCommand(proposal.id))

        assertEquals(ProposalStatus.WITHDRAWN, repository.findById(proposal.id)?.status)
    }

    @Test
    fun `withdrawing a proposal by command only throws ProposalNotFoundException when nothing was saved`() {
        assertFailsWith<ProposalNotFoundException> {
            service.withdrawProposal(WithdrawProposalCommand(ProposalId("never-saved")))
        }
    }

    @Test
    fun `accepting a proposal with a statedPrice persists it through the repository`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.acceptProposal(proposal, AcceptProposalCommand(proposal.id, statedPrice = "300"))

        assertEquals("300", repository.findById(proposal.id)?.statedPrice)
    }

    @Test
    fun `accepting an already-accepted proposal through the service is rejected`() {
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        proposal.accept()

        assertFailsWith<IllegalStateException> {
            service.acceptProposal(proposal, AcceptProposalCommand(proposal.id))
        }
    }

    // --- Driver availability gate (pilot-readiness fix) ---

    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        val records = mutableMapOf<String, DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")
        override fun upsert(record: DriverAvailabilityRecord) {
            records[record.driverReference.driverId] = record
        }
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            records[driverReference.driverId]
    }

    @Test
    fun `handling a command for a driver with no availability record is rejected`() {
        val availabilityRepository = InMemoryDriverAvailabilityRepository()
        val gatedService = ProposalApplicationService(repository, driverAvailabilityRepository = availabilityRepository)

        assertFailsWith<IllegalStateException> {
            gatedService.handle(ProposeDriverCommand(order, driver))
        }
    }

    @Test
    fun `handling a command for a driver marked unavailable is rejected`() {
        val availabilityRepository = InMemoryDriverAvailabilityRepository()
        availabilityRepository.upsert(DriverAvailabilityRecord(driver, available = false))
        val gatedService = ProposalApplicationService(repository, driverAvailabilityRepository = availabilityRepository)

        assertFailsWith<IllegalStateException> {
            gatedService.handle(ProposeDriverCommand(order, driver))
        }
    }

    @Test
    fun `handling a command for a driver marked available succeeds`() {
        val availabilityRepository = InMemoryDriverAvailabilityRepository()
        availabilityRepository.upsert(DriverAvailabilityRecord(driver, available = true))
        val gatedService = ProposalApplicationService(repository, driverAvailabilityRepository = availabilityRepository)

        val result = gatedService.handle(ProposeDriverCommand(order, driver))

        assertEquals(order, result.proposal.order)
    }

    /**
     * Full pilot-readiness verification scenario: 4 drivers, only one
     * (Артур) `AVAILABLE`, the other 3 defaulting to `UNAVAILABLE` (no
     * record ever upserted for them) -- proving the fail-closed gate end to
     * end, then proving it re-applies the moment Артур goes offline too.
     */
    @Test
    fun `proposal creation follows a driver's own availability end to end`() {
        val availabilityRepository = InMemoryDriverAvailabilityRepository()
        val gatedService = ProposalApplicationService(repository, driverAvailabilityRepository = availabilityRepository)
        val artur = DriverReference("driver-artur")
        val orderForArtur = OrderReference("order-for-artur")
        val orderForOther = OrderReference("order-for-other")
        val orderForArturSecond = OrderReference("order-for-artur-second")

        // Step 2: Артур is AVAILABLE, the other 3 stay UNAVAILABLE (default, no record).
        availabilityRepository.upsert(DriverAvailabilityRecord(artur, available = true))

        // Step 4: proposing Артур succeeds.
        val proposedArtur = gatedService.handle(ProposeDriverCommand(orderForArtur, artur))
        assertEquals(artur, proposedArtur.proposal.driver)

        // Step 5: proposing one of the other 3 (never toggled -- no record) is rejected.
        assertFailsWith<IllegalStateException> {
            gatedService.handle(ProposeDriverCommand(orderForOther, DriverReference("driver-other-1")))
        }

        // Step 6: Артур goes UNAVAILABLE.
        availabilityRepository.upsert(DriverAvailabilityRecord(artur, available = false))

        // Step 7: proposing Артур again is now rejected too.
        assertFailsWith<IllegalStateException> {
            gatedService.handle(ProposeDriverCommand(orderForArturSecond, artur))
        }
    }
}
