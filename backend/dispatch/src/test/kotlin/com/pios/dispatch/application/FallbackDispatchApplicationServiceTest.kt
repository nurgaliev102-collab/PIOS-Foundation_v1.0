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

        override fun findLongestIdleAvailable(excluding: Set<DriverReference>): DriverReference? =
            recordsInOrder.firstOrNull { it.available && it.driverReference !in excluding }?.driverReference
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

/**
 * ADR-068 (Relationship-Ordered Fallback Dispatch — Trusted → Network →
 * Open Marketplace), Part 2: Tier 1 (Trusted) selection, and its
 * fall-through to the existing, unchanged Tier 3 (Open Marketplace) query
 * when Tier 1 yields nothing. A separate class from
 * [FallbackDispatchApplicationServiceTest] so the pre-existing,
 * `trustedDriverRepository`-absent behavior above stays proven completely
 * unaffected by this ADR (that class's own `service` is still constructed
 * with only its original two constructor arguments), while this class
 * exercises the new three-argument constructor explicitly.
 */
class FallbackDispatchApplicationServiceTrustedTierTest {

    /**
     * Identical fake to [FallbackDispatchApplicationServiceTest]'s own
     * private `InMemoryDriverAvailabilityRepository`, plus an
     * [orderedAvailableDrivers] accessor [FakeTrustedDriverRepository]
     * needs -- duplicated here rather than shared, since the original is
     * `private` to its own test class, mirroring this codebase's own
     * per-test-class fake convention (no shared test-fixture module
     * exists for this).
     */
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

        fun orderedAvailableDrivers(): List<DriverReference> =
            recordsInOrder.filter { it.available }.map { it.driverReference }
    }

    private class FakeTrustedDriverRepository(
        private val driverAvailabilityRepository: InMemoryDriverAvailabilityRepository
    ) : TrustedDriverRepository {
        private val trustedPairs = mutableSetOf<Pair<PassengerReference, DriverReference>>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")

        override fun add(record: TrustedDriverRecord) {
            trustedPairs.add(record.passengerReference to record.driverId)
        }

        override fun remove(passengerReference: PassengerReference, driverId: DriverReference) {
            trustedPairs.remove(passengerReference to driverId)
        }

        override fun findLongestIdleTrustedAvailable(
            passengerReference: PassengerReference,
            excluding: Set<DriverReference>
        ): DriverReference? =
            driverAvailabilityRepository.orderedAvailableDrivers()
                .firstOrNull { (passengerReference to it) in trustedPairs && it !in excluding }
    }

    private val proposalRepository = InMemoryProposalRepository()
    private val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
    private val trustedDriverRepository = FakeTrustedDriverRepository(driverAvailabilityRepository)
    private val proposalApplicationService = ProposalApplicationService(
        proposalRepository,
        driverAvailabilityRepository = driverAvailabilityRepository
    )
    private val service = FallbackDispatchApplicationService(
        driverAvailabilityRepository,
        proposalApplicationService,
        trustedDriverRepository
    )

    private val order = OrderReference("order-1")
    private val passenger = PassengerReference("passenger-1")

    @Test
    fun `a trusted driver is selected over a stranger, even one who has been idle longer`() {
        val stranger = DriverReference("driver-stranger")
        val trusted = DriverReference("driver-trusted")
        // The stranger is recorded available first, so by Tier 3's own
        // longest-idle rule alone they would win -- proving Tier 1 takes
        // priority is exactly what this test is for.
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(stranger, available = true))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(trusted, available = true))
        trustedDriverRepository.add(TrustedDriverRecord(passenger, trusted))

        val outcome = service.attempt(order, passenger)

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(trusted, proposed.proposal.driver)
    }

    @Test
    fun `no trusted driver falls through to the existing longest-idle-available query unchanged`() {
        val stranger = DriverReference("driver-stranger")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(stranger, available = true))
        // No TrustedDriverRecord for this passenger at all -- Tier 1 is
        // empty, and Tier 3's own existing query must still run and
        // select the only available driver, exactly as it did before
        // ADR-068.
        val outcome = service.attempt(order, passenger)

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(stranger, proposed.proposal.driver)
    }

    @Test
    fun `a trusted driver who is not currently available also falls through to Tier 3, unchanged`() {
        val unavailableTrusted = DriverReference("driver-trusted-offline")
        val availableStranger = DriverReference("driver-stranger")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(unavailableTrusted, available = false))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(availableStranger, available = true))
        trustedDriverRepository.add(TrustedDriverRecord(passenger, unavailableTrusted))

        val outcome = service.attempt(order, passenger)

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(availableStranger, proposed.proposal.driver)
    }

    @Test
    fun `the primary driver who just declined is excluded from Tier 1, even though they are also in the trusted set`() {
        // The primary driver is, by ADR-054 Part 2's own composite foreign
        // key, always a member of the passenger's own trusted circle too
        // -- this is exactly the ADR-068 Part 2, property 2 scenario:
        // without excludeDrivers reaching Tier 1, the just-declined
        // primary driver would be re-selected as their own trusted-circle
        // fallback.
        val primaryDriver = DriverReference("driver-primary")
        val otherTrustedDriver = DriverReference("driver-other-trusted")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(otherTrustedDriver, available = true))
        trustedDriverRepository.add(TrustedDriverRecord(passenger, primaryDriver))
        trustedDriverRepository.add(TrustedDriverRecord(passenger, otherTrustedDriver))

        val outcome = service.attempt(order, passenger, excludeDrivers = setOf(primaryDriver))

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(otherTrustedDriver, proposed.proposal.driver)
    }

    @Test
    fun `the primary driver who just declined, with no other trusted driver available, falls through to Tier 3`() {
        val primaryDriver = DriverReference("driver-primary")
        val stranger = DriverReference("driver-stranger")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(stranger, available = true))
        trustedDriverRepository.add(TrustedDriverRecord(passenger, primaryDriver))

        val outcome = service.attempt(order, passenger, excludeDrivers = setOf(primaryDriver))

        val proposed = assertIs<FallbackDispatchOutcome.Proposed>(outcome)
        assertEquals(stranger, proposed.proposal.driver)
    }
}
