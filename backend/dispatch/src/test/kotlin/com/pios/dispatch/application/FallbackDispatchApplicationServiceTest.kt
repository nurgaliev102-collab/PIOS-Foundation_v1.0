package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryProposalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * FR-003A (Fallback Dispatch). Mirrors [FirstRefusalApplicationServiceTest]'s
 * own exact shape and in-memory discipline. Covers [FallbackDispatchApplicationService.attempt]'s
 * own three outcomes end to end.
 */
class FallbackDispatchApplicationServiceTest {

    /** Adds [findLongestIdleAvailable] support on top of the interface's own default. */
    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        // Pairs of (record, insertion order) -- insertion order stands in
        // for "how long ago this driver's availability was last recorded"
        // in this in-memory fake, mirroring the real repository's own
        // updated_at-ascending ordering without needing a clock.
        private val recordsInOrder = mutableListOf<DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")

        override fun upsert(record: DriverAvailabilityRecord) {
            recordsInOrder.removeAll { it.driverReference == record.driverReference }
            recordsInOrder.add(record)
        }

        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            recordsInOrder.lastOrNull { it.driverReference == driverReference }

        override fun findLongestIdleAvailable(): DriverReference? =
            recordsInOrder.firstOrNull { it.available }?.driverReference
    }

    private val proposalRepository = InMemoryProposalRepository()
    private val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
    private val proposalApplicationService = ProposalApplicationService(
        proposalRepository,
        driverAvailabilityRepository = driverAvailabilityRepository
    )
    private val service = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)

    private val order = OrderReference("order-1")
    private val passenger = PassengerReference("passenger-1")

    @Test
    fun `an available driver receives a Fallback Dispatch proposal`() {
        val driver = DriverReference("driver-1")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(driver, available = true))

        val outcome = service.attempt(order, passenger)

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(driver, proposed.proposal.driver)
        assertEquals(order, proposed.proposal.order)
        assertEquals(ProposalStatus.OPEN, proposed.proposal.status)
        assertEquals(1, proposalRepository.findByOrder(order).size)
    }

    @Test
    fun `the longest-idle available driver is selected, not the most recently available one`() {
        val longestIdle = DriverReference("driver-longest-idle")
        val mostRecent = DriverReference("driver-most-recent")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(longestIdle, available = true))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(mostRecent, available = true))

        val outcome = service.attempt(order, passenger)

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(longestIdle, proposed.proposal.driver)
    }

    @Test
    fun `an unavailable driver is never selected, even if recorded`() {
        val driver = DriverReference("driver-1")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(driver, available = false))

        val outcome = service.attempt(order, passenger)

        assertEquals(FallbackDispatchOutcome.NoAvailableDriver, outcome)
        assertEquals(emptyList(), proposalRepository.findByOrder(order))
    }

    @Test
    fun `no driver ever recorded produces NoAvailableDriver, not an error`() {
        val outcome = service.attempt(order, passenger)

        assertEquals(FallbackDispatchOutcome.NoAvailableDriver, outcome)
        assertEquals(emptyList(), proposalRepository.findByOrder(order))
    }

    @Test
    fun `attempting Fallback Dispatch twice for the same order does not create a second proposal`() {
        val driver = DriverReference("driver-1")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(driver, available = true))

        val first = service.attempt(order, passenger)
        val second = service.attempt(order, passenger)

        assertIs<FallbackDispatchOutcome.Proposed>(first)
        assertEquals(FallbackDispatchOutcome.AlreadyAttempted, second)
        assertEquals(1, proposalRepository.findByOrder(order).size)
    }

    @Test
    fun `an order that already has an open proposal from First Refusal is left alone by Fallback Dispatch`() {
        // Simulates the real listener's own sequencing: First Refusal ran
        // first and already created a Proposal for a different (primary)
        // driver; Fallback Dispatch must never be called in that case per
        // OrderSubmittedFirstRefusalListener's own outcome-gated call --
        // this test proves that *if it somehow were* called anyway, the
        // existing Proposal.propose root invariant still protects the order
        // from a second, competing proposal.
        val primaryDriver = DriverReference("driver-primary")
        val fallbackDriver = DriverReference("driver-fallback")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))
        proposalApplicationService.handle(ProposeDriverCommand(order, primaryDriver, passengerReference = passenger))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(fallbackDriver, available = true))

        val outcome = service.attempt(order, passenger)

        assertEquals(FallbackDispatchOutcome.AlreadyAttempted, outcome)
        assertEquals(1, proposalRepository.findByOrder(order).size)
        assertEquals(primaryDriver, proposalRepository.findByOrder(order).single().driver)
    }

    @Test
    fun `isTest is carried from the command through to the created proposal`() {
        val driver = DriverReference("driver-1")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(driver, available = true))

        val outcome = service.attempt(order, passenger, isTest = true) as FallbackDispatchOutcome.Proposed

        assertEquals(true, outcome.proposal.isTest)
    }
}
