package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ProposalTest {

    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    // --- Creation ---

    @Test
    fun `proposing a driver connects the given order and driver`() {
        val created = Proposal.propose(order, driver)

        assertEquals(order, created.proposal.order)
        assertEquals(driver, created.proposal.driver)
    }

    @Test
    fun `proposing a driver produces an OrderProposed event for the same order and driver`() {
        val created = Proposal.propose(order, driver)

        assertEquals(order, created.event.orderId)
        assertEquals(driver, created.event.driverId)
    }

    @Test
    fun `each proposed driver has a distinct identity`() {
        val first = Proposal.propose(order, driver)
        val second = Proposal.propose(OrderReference("order-2"), driver)

        assertNotEquals(first.proposal.id, second.proposal.id)
    }

    @Test
    fun `a newly proposed driver starts in OPEN status`() {
        val proposal = Proposal.propose(order, driver).proposal

        assertEquals(ProposalStatus.OPEN, proposal.status)
    }

    // --- Root Invariant: an order may have at most one open proposal at a time ---

    @Test
    fun `proposing a second driver for an order that already has an open proposal is rejected`() {
        val first = Proposal.propose(order, driver)

        assertFailsWith<IllegalStateException> {
            Proposal.propose(order, DriverReference("driver-2"), existingProposals = listOf(first.proposal))
        }
    }

    @Test
    fun `proposing a driver for a different order succeeds even with existing open proposals`() {
        val first = Proposal.propose(order, driver)
        val otherOrder = OrderReference("order-2")

        val second = Proposal.propose(otherOrder, driver, existingProposals = listOf(first.proposal))

        assertEquals(otherOrder, second.proposal.order)
    }

    @Test
    fun `a driver may be referenced by more than one proposal for different orders`() {
        val first = Proposal.propose(order, driver)
        val otherOrder = OrderReference("order-2")

        val second = Proposal.propose(otherOrder, driver, existingProposals = listOf(first.proposal))

        assertEquals(driver, second.proposal.driver)
    }

    @Test
    fun `proposing a driver for an order whose only existing proposal was already accepted is allowed`() {
        val first = Proposal.propose(order, driver).proposal
        first.accept()

        val second = Proposal.propose(order, DriverReference("driver-2"), existingProposals = listOf(first))

        assertEquals(order, second.proposal.order)
    }

    @Test
    fun `proposing a driver for an order whose only existing proposal was already declined is allowed`() {
        val first = Proposal.propose(order, driver).proposal
        first.decline()

        val second = Proposal.propose(order, DriverReference("driver-2"), existingProposals = listOf(first))

        assertEquals(order, second.proposal.order)
    }

    @Test
    fun `proposing a driver for an order whose only existing proposal already lapsed is allowed`() {
        val first = Proposal.propose(order, driver).proposal
        first.lapse()

        val second = Proposal.propose(order, DriverReference("driver-2"), existingProposals = listOf(first))

        assertEquals(order, second.proposal.order)
    }

    // --- Acceptance ---

    @Test
    fun `accepting an open proposal transitions it to ACCEPTED`() {
        val proposal = Proposal.propose(order, driver).proposal

        proposal.accept()

        assertEquals(ProposalStatus.ACCEPTED, proposal.status)
    }

    @Test
    fun `accepting an open proposal produces a ProposalAccepted event for the same order and driver`() {
        val proposal = Proposal.propose(order, driver).proposal

        val event = proposal.accept()

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `accepting an already-accepted proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.accept()

        assertFailsWith<IllegalStateException> {
            proposal.accept()
        }
    }

    @Test
    fun `accepting an already-declined proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.decline()

        assertFailsWith<IllegalStateException> {
            proposal.accept()
        }
    }

    @Test
    fun `accepting an already-lapsed proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.lapse()

        assertFailsWith<IllegalStateException> {
            proposal.accept()
        }
    }

    // --- Decline ---

    @Test
    fun `declining an open proposal transitions it to DECLINED`() {
        val proposal = Proposal.propose(order, driver).proposal

        proposal.decline()

        assertEquals(ProposalStatus.DECLINED, proposal.status)
    }

    @Test
    fun `declining an open proposal produces a ProposalDeclined event for the same order and driver`() {
        val proposal = Proposal.propose(order, driver).proposal

        val event = proposal.decline()

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `declining an already-declined proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.decline()

        assertFailsWith<IllegalStateException> {
            proposal.decline()
        }
    }

    @Test
    fun `declining an already-accepted proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.accept()

        assertFailsWith<IllegalStateException> {
            proposal.decline()
        }
    }

    @Test
    fun `declining an already-lapsed proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.lapse()

        assertFailsWith<IllegalStateException> {
            proposal.decline()
        }
    }

    // --- Lapse ---

    @Test
    fun `lapsing an open proposal transitions it to LAPSED`() {
        val proposal = Proposal.propose(order, driver).proposal

        proposal.lapse()

        assertEquals(ProposalStatus.LAPSED, proposal.status)
    }

    @Test
    fun `lapsing an open proposal produces a ProposalLapsed event for the same order and driver`() {
        val proposal = Proposal.propose(order, driver).proposal

        val event = proposal.lapse()

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `lapsing an already-lapsed proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.lapse()

        assertFailsWith<IllegalStateException> {
            proposal.lapse()
        }
    }

    @Test
    fun `lapsing an already-accepted proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.accept()

        assertFailsWith<IllegalStateException> {
            proposal.lapse()
        }
    }

    @Test
    fun `lapsing an already-declined proposal is rejected`() {
        val proposal = Proposal.propose(order, driver).proposal
        proposal.decline()

        assertFailsWith<IllegalStateException> {
            proposal.lapse()
        }
    }
}
