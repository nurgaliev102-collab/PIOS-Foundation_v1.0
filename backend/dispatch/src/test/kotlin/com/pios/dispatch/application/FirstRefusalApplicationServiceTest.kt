package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Task 14 (First Refusal Foundation), Tests F, G, H, I. Covers
 * [FirstRefusalApplicationService.attempt]'s own four outcomes end to
 * end, entirely in memory.
 */
class FirstRefusalApplicationServiceTest {

    /** Mirrors [ProposalApplicationServiceTest]'s own identical private fake. */
    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        val records = mutableMapOf<String, DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")
        override fun upsert(record: DriverAvailabilityRecord) {
            records[record.driverReference.driverId] = record
        }
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            records[driverReference.driverId]
    }

    private val proposalRepository = InMemoryProposalRepository()
    private val primaryDriverRepository = InMemoryPrimaryDriverRepository()
    private val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
    private val proposalApplicationService = ProposalApplicationService(
        proposalRepository,
        driverAvailabilityRepository = driverAvailabilityRepository
    )
    private val service = FirstRefusalApplicationService(
        primaryDriverRepository,
        proposalApplicationService,
        driverAvailabilityRepository
    )

    private val order = OrderReference("order-1")
    private val passenger = PassengerReference("passenger-1")
    private val primaryDriver = DriverReference("driver-primary")

    // --- F: passenger with primary driver + eligible driver -> primary driver selected ---

    @Test
    fun `Test F -- a passenger with an eligible primary driver gets a First Refusal proposal for that driver`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))

        val outcome = service.attempt(order, passenger)

        val proposed = assertIs<FirstRefusalOutcome.Proposed>(outcome)
        assertEquals(primaryDriver, proposed.proposal.driver)
        assertEquals(order, proposed.proposal.order)
        assertEquals(ProposalStatus.OPEN, proposed.proposal.status)
    }

    @Test
    fun `primary test driver is ineligible for a real order`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = true))

        assertIs<FirstRefusalOutcome.PrimaryDriverIneligible>(service.attempt(order, passenger, isTest = false))
        assertEquals(emptyList(), proposalRepository.findByOrder(order))
    }

    @Test
    fun `primary driver may receive an advance request while off-line`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = false, isTest = false))

        val outcome = service.attempt(
            order,
            passenger,
            requestedPickupAt = java.time.Instant.parse("2099-08-25T06:30:00Z")
        )

        assertIs<FirstRefusalOutcome.Proposed>(outcome)
        assertEquals(primaryDriver, outcome.proposal.driver)
    }

    @Test
    fun `Test F -- the created proposal uses the existing OPEN status, no new vocabulary`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))

        val outcome = service.attempt(order, passenger) as FirstRefusalOutcome.Proposed

        assertEquals(1, proposalRepository.findByOrder(order).size)
        assertEquals(ProposalStatus.OPEN, proposalRepository.findByOrder(order).single().status)
        assertEquals(outcome.proposal.id, proposalRepository.findByOrder(order).single().id)
    }

    // --- G: passenger with no primary driver -> existing behavior unchanged ---

    @Test
    fun `Test G -- a passenger with no primary driver produces no proposal at all`() {
        val outcome = service.attempt(order, passenger)

        assertEquals(FirstRefusalOutcome.NoPrimaryDriver, outcome)
        assertEquals(emptyList(), proposalRepository.findByOrder(order))
    }

    // --- H: primary driver unavailable -> existing fallback behavior unchanged ---

    @Test
    fun `Test H -- an unavailable primary driver produces no proposal, leaving the order free for normal dispatch`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = false, isTest = false))

        val outcome = service.attempt(order, passenger)

        val ineligible = assertIs<FirstRefusalOutcome.PrimaryDriverIneligible>(outcome)
        assertEquals(primaryDriver, ineligible.driver)
        assertEquals(emptyList(), proposalRepository.findByOrder(order))

        // The order is genuinely free -- a normal (non-First-Refusal)
        // proposal for a different, available driver still works exactly
        // as today (ProposalApplicationService's own pre-existing
        // availability gate, unchanged by this task).
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-fallback"), available = true, isTest = false))
        val fallback = proposalApplicationService.handle(ProposeDriverCommand(order, DriverReference("driver-fallback")))
        assertEquals(ProposalStatus.OPEN, fallback.proposal.status)
    }

    @Test
    fun `Test H -- a primary driver with no availability record at all is treated as ineligible, not a bypass`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        // No DriverAvailabilityRecord upserted for primaryDriver at all.

        val outcome = service.attempt(order, passenger)

        assertIs<FirstRefusalOutcome.PrimaryDriverIneligible>(outcome)
        assertEquals(emptyList(), proposalRepository.findByOrder(order))
    }

    // --- I: duplicate proposal creation does not create duplicate First Refusal state ---

    @Test
    fun `Test I -- attempting First Refusal twice for the same order does not create a second proposal`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))

        val first = service.attempt(order, passenger)
        val second = service.attempt(order, passenger)

        assertIs<FirstRefusalOutcome.Proposed>(first)
        assertEquals(FirstRefusalOutcome.AlreadyAttempted, second)
        assertEquals(1, proposalRepository.findByOrder(order).size)
    }

    @Test
    fun `no primary-driver record ever exists for a passenger this service was never told about`() {
        assertNull(primaryDriverRepository.findByPassenger(PassengerReference("nobody")))
    }

    // --- Explicit driver intent (Task 15C: First Refusal Contract Completion and Concurrency Safety) ---
    // Test 3: automatic First Refusal must not select Primary Driver Y when explicit intent is declared.
    // Test 4: no explicit choice leaves the order eligible for First Refusal (already Test F/above; restated here for completeness).

    @Test
    fun `Test 3 -- declared explicit driver intent skips First Refusal entirely, even with an eligible primary driver`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))

        val outcome = service.attempt(order, passenger, explicitDriverIntentDeclared = true)

        assertEquals(FirstRefusalOutcome.ExplicitDriverIntentDeclared, outcome)
        assertEquals(emptyList(), proposalRepository.findByOrder(order))
    }

    @Test
    fun `Test 3 -- declared explicit driver intent makes no read of the primary-driver projection at all`() {
        // A primaryDriverRepository whose own findByPassenger would fail
        // the test if ever called -- proves attempt() short-circuits
        // before touching this collaborator, not merely before creating a
        // Proposal.
        val poisonedRepository = object : PrimaryDriverRepository {
            override fun markProcessed(eventId: String) = throw AssertionError("must not be called")
            override fun upsert(record: PrimaryDriverRecord) = throw AssertionError("must not be called")
            override fun clear(passengerReference: PassengerReference) = throw AssertionError("must not be called")
            override fun findByPassenger(passengerReference: PassengerReference): PrimaryDriverRecord? =
                throw AssertionError("must not be called -- explicit intent should short-circuit before this read")
        }
        val poisonedService = FirstRefusalApplicationService(poisonedRepository, proposalApplicationService, driverAvailabilityRepository)

        val outcome = poisonedService.attempt(order, passenger, explicitDriverIntentDeclared = true)

        assertEquals(FirstRefusalOutcome.ExplicitDriverIntentDeclared, outcome)
    }

    @Test
    fun `Test 4 -- no explicit intent declared (the default) leaves an eligible primary driver free to be proposed to`() {
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))

        val outcome = service.attempt(order, passenger, explicitDriverIntentDeclared = false)

        assertIs<FirstRefusalOutcome.Proposed>(outcome)
        assertEquals(primaryDriver, outcome.proposal.driver)
    }
}
