package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.persistence.InMemoryProposalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves ADR-083 (D-10, Driver Web Push for Open Proposal and Price
 * Confirmation)'s trigger contract at the application layer, entirely in
 * memory, with a spy [DriverPushNotifier] standing in for
 * [com.pios.dispatch.persistence.WebPushDriverPushNotifier] -- mirroring
 * [ProposalApplicationServiceTest]'s own direct-construction convention.
 *
 * The single most important test in this file (ADR-083's own words) is
 * `confirmPrice fires N2, acceptProposal (OPEN to ACCEPTED) never does` --
 * these are two different methods on [Proposal] (`confirmPrice`, line
 * ~241, vs. `accept`, line ~174) and only the former may ever call
 * [DriverPushNotifier.priceConfirmed].
 */
class DriverPushTriggerTest {

    private class SpyDriverPushNotifier : DriverPushNotifier {
        val offerCreatedCalls = mutableListOf<Pair<ProposalId, DriverReference>>()
        val priceConfirmedCalls = mutableListOf<Pair<ProposalId, DriverReference>>()

        override fun offerCreated(proposalId: ProposalId, driver: DriverReference) {
            offerCreatedCalls.add(proposalId to driver)
        }

        override fun priceConfirmed(proposalId: ProposalId, driver: DriverReference) {
            priceConfirmedCalls.add(proposalId to driver)
        }
    }

    private val repository = InMemoryProposalRepository()
    private val notifier = SpyDriverPushNotifier()
    private val service = ProposalApplicationService(repository, driverPushNotifier = notifier)
    private val order = OrderReference("push-order-1")
    private val driver = DriverReference("push-driver-1")

    // --- N1 ---

    @Test
    fun `N1 fires exactly once on successful OPEN-proposal creation, carrying the created proposal's driver`() {
        val created = service.handle(ProposeDriverCommand(order, driver))

        assertEquals(1, notifier.offerCreatedCalls.size)
        assertEquals(created.proposal.id to driver, notifier.offerCreatedCalls.single())
    }

    @Test
    fun `N1 does not fire when handle throws before the save commits (duplicate OPEN)`() {
        service.handle(ProposeDriverCommand(order, driver))
        notifier.offerCreatedCalls.clear()

        assertFailsWith<IllegalStateException> {
            service.handle(ProposeDriverCommand(order, DriverReference("push-driver-2")))
        }

        assertTrue(notifier.offerCreatedCalls.isEmpty())
    }

    @Test
    fun `N1 does not fire when handle is rejected by the availability gate`() {
        val availabilityRepository = object : DriverAvailabilityRepository {
            override fun markProcessed(eventId: String) = throw UnsupportedOperationException("not used")
            override fun upsert(record: DriverAvailabilityRecord) = throw UnsupportedOperationException("not used")
            override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? = null
        }
        val gatedNotifier = SpyDriverPushNotifier()
        val gatedService = ProposalApplicationService(
            repository,
            driverAvailabilityRepository = availabilityRepository,
            driverPushNotifier = gatedNotifier
        )

        assertFailsWith<IllegalStateException> {
            gatedService.handle(ProposeDriverCommand(OrderReference("push-order-gated"), driver))
        }

        assertTrue(gatedNotifier.offerCreatedCalls.isEmpty())
    }

    // --- N2: the single most important test ---

    @Test
    fun `confirmPrice fires N2 exactly once -- acceptProposal (OPEN to ACCEPTED) never fires N2`() {
        // Path A: propose price, then confirmPrice -- PRICE_PROPOSED -> ACCEPTED.
        val proposalA = service.handle(ProposeDriverCommand(order, driver)).proposal
        service.proposePrice(proposalA, ProposePriceCommand(proposalA.id, statedPrice = "500"))

        service.confirmPrice(proposalA, ConfirmPriceCommand(proposalA.id))

        assertEquals(1, notifier.priceConfirmedCalls.size)
        assertEquals(proposalA.id to driver, notifier.priceConfirmedCalls.single())

        // Path B: a separate proposal accepted directly -- OPEN -> ACCEPTED,
        // via Proposal.accept(), never Proposal.confirmPrice().
        val otherOrder = OrderReference("push-order-2")
        val proposalB = service.handle(ProposeDriverCommand(otherOrder, driver)).proposal

        service.acceptProposal(proposalB, AcceptProposalCommand(proposalB.id))

        // Still exactly one N2 call overall -- acceptProposal never added a second.
        assertEquals(1, notifier.priceConfirmedCalls.size)
    }

    @Test
    fun `N2 does not fire on decline, declinePriceProposal, lapse, or withdraw`() {
        val declined = service.handle(ProposeDriverCommand(OrderReference("push-decline"), driver)).proposal
        service.declineProposal(declined, DeclineProposalCommand(declined.id))

        val priceDeclined = service.handle(ProposeDriverCommand(OrderReference("push-price-decline"), driver)).proposal
        service.proposePrice(priceDeclined, ProposePriceCommand(priceDeclined.id, statedPrice = "400"))
        service.declinePriceProposal(priceDeclined, DeclinePriceCommand(priceDeclined.id))

        val lapsed = service.handle(ProposeDriverCommand(OrderReference("push-lapse"), driver)).proposal
        service.lapseProposal(lapsed, LapseProposalCommand(lapsed.id))

        val withdrawn = service.handle(ProposeDriverCommand(OrderReference("push-withdraw"), driver)).proposal
        service.withdrawProposal(withdrawn, WithdrawProposalCommand(withdrawn.id))

        assertTrue(notifier.priceConfirmedCalls.isEmpty())
    }
}
