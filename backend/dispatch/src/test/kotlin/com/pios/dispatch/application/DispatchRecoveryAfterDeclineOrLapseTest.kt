package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-078 (Dispatch Recovery After Proposal Decline or Lapse) — Decisions A
 * and B, implemented together since a reopened row is only useful if the
 * resumed sweep it feeds is itself excludeDrivers-aware. Mirrors this
 * module's own established layering exactly: [ProposalApplicationServiceReopenRoutingObligationTest]
 * proves the reopen wiring itself (Decision A, mechanical requirements 1-3)
 * at the [ProposalApplicationService] level, fast and DB-free, exactly like
 * [ProposalApplicationServiceTest]'s own existing discipline;
 * [DispatchRequestApplicationServiceRecoveryTest] and
 * [ProposalLapseRoutingWindowTimingTest] prove the end-to-end sweep
 * behavior (Decision B) through the real [DispatchRequestApplicationService],
 * exactly like [DispatchRequestApplicationServiceTest]'s own existing
 * discipline.
 */
private class MultiDriverAvailabilityRepository : DriverAvailabilityRepository {
    // Mirrors FallbackDispatchApplicationServiceTest's own private fake
    // exactly (insertion order stands in for "how long ago this driver's
    // availability was last recorded").
    private val recordsInOrder = mutableListOf<DriverAvailabilityRecord>()

    override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")

    override fun upsert(record: DriverAvailabilityRecord) {
        recordsInOrder.removeAll { it.driverReference == record.driverReference }
        recordsInOrder.add(record)
    }

    override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
        recordsInOrder.lastOrNull { it.driverReference == driverReference }

    override fun findLongestIdleAvailable(orderIsTest: Boolean, excluding: Set<DriverReference>): DriverReference? =
        recordsInOrder.firstOrNull { it.available && it.driverReference !in excluding && it.isTest == orderIsTest }?.driverReference
}

/** Mirrors DispatchRequestApplicationServiceTest's own private fake exactly. */
private class TestOutboxRepository : OutboxRepository {
    val records: MutableList<OutboxRecord> = mutableListOf()
    override fun save(record: OutboxRecord): OutboxRecord {
        records.add(record)
        return record
    }
    override fun findUnpublished(): List<OutboxRecord> = records
    override fun markPublished(id: Long) = Unit
    override fun countUnpublished(): OutboxBacklog = OutboxBacklog(records.size.toLong(), null)
}

/**
 * ADR-078 Decision A, mechanical requirements 1-3: proves
 * [ProposalApplicationService.declineProposal]/[ProposalApplicationService.lapseProposal]
 * reopen an `OFFERED` [DispatchRequestRecord] to `PENDING` without touching
 * `submittedAt`/`expiresAt` (the routing window does not restart), that
 * [ProposalApplicationService.declinePriceProposal] (the passenger's own
 * price refusal, Decision C) never does so, and that a row already
 * `CANCELLED`/`UNFULFILLED` is never revived by a late decline/lapse
 * (Decision A, requirement 1 — `reopenIfOffered`'s own SQL-conditional
 * guard, mirrored here through [InMemoryDispatchRequestRepository]'s own
 * identical guard).
 */
class ProposalApplicationServiceReopenRoutingObligationTest {
    private val proposals = InMemoryProposalRepository()
    private val requests = InMemoryDispatchRequestRepository()
    private val service = ProposalApplicationService(proposals, dispatchRequestRepository = requests)
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    private fun offeredRecord(now: Instant): DispatchRequestRecord = DispatchRequestRecord(
        orderId = order.orderId,
        passengerReference = "passenger-1",
        isTest = false,
        explicitDriverIntent = false,
        requestedDriverId = null,
        requestedPickupAt = null,
        submittedAt = now,
        expiresAt = now.plusSeconds(120),
        nextAttemptAt = now
    )

    private fun givenAnOfferedRow(now: Instant = Instant.now()) {
        requests.insertIfAbsent(offeredRecord(now))
        requests.markOffered(order.orderId)
    }

    @Test
    fun `declining a proposal reopens its OFFERED row to PENDING without changing submittedAt or expiresAt`() {
        val now = Instant.now()
        givenAnOfferedRow(now)
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.declineProposal(proposal, DeclineProposalCommand(proposal.id))

        val reopened = requests.findForUpdate(order.orderId)
        assertEquals(DispatchRequestState.PENDING, reopened?.state)
        assertEquals(now, reopened?.submittedAt)
        assertEquals(now.plusSeconds(120), reopened?.expiresAt)
    }

    @Test
    fun `lapsing a proposal reopens its OFFERED row to PENDING without changing submittedAt or expiresAt`() {
        val now = Instant.now()
        givenAnOfferedRow(now)
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.lapseProposal(proposal, LapseProposalCommand(proposal.id))

        val reopened = requests.findForUpdate(order.orderId)
        assertEquals(DispatchRequestState.PENDING, reopened?.state)
        assertEquals(now, reopened?.submittedAt)
        assertEquals(now.plusSeconds(120), reopened?.expiresAt)
    }

    @Test
    fun `a passenger's declinePriceProposal does not reopen the row (ADR-078 Decision C)`() {
        givenAnOfferedRow()
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        service.proposePrice(proposal, ProposePriceCommand(proposal.id, "500"))

        service.declinePriceProposal(proposal, DeclinePriceCommand(proposal.id))

        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate(order.orderId)?.state)
    }

    @Test
    fun `an already-CANCELLED row is never reopened even if a late decline arrives`() {
        givenAnOfferedRow()
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        // Simulates the row independently reaching CANCELLED (ADR-053) --
        // e.g. an OrderCancelled tombstone -- before this late decline
        // (which resolves a Proposal that was already OPEN before the
        // cancellation) is processed.
        requests.markCancelled(order.orderId)

        service.declineProposal(proposal, DeclineProposalCommand(proposal.id))

        assertEquals(ProposalStatus.DECLINED, proposals.findById(proposal.id)?.status)
        assertEquals(DispatchRequestState.CANCELLED, requests.findForUpdate(order.orderId)?.state)
    }

    @Test
    fun `an already-UNFULFILLED row is never reopened even if a late lapse arrives`() {
        givenAnOfferedRow()
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal
        // Simulates the row independently reaching UNFULFILLED (ADR-077)
        // before this late lapse is processed.
        requests.markUnfulfilled(order.orderId)

        service.lapseProposal(proposal, LapseProposalCommand(proposal.id))

        assertEquals(ProposalStatus.LAPSED, proposals.findById(proposal.id)?.status)
        assertEquals(DispatchRequestState.UNFULFILLED, requests.findForUpdate(order.orderId)?.state)
    }

    @Test
    fun `a PENDING row is left PENDING by a decline, not disturbed by reopenIfOffered's own OFFERED-only guard`() {
        val now = Instant.now()
        requests.insertIfAbsent(offeredRecord(now)) // stays PENDING -- never marked OFFERED
        val proposal = service.handle(ProposeDriverCommand(order, driver)).proposal

        service.declineProposal(proposal, DeclineProposalCommand(proposal.id))

        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate(order.orderId)?.state)
    }
}

/**
 * ADR-078 Decision B end to end, through the real
 * [DispatchRequestApplicationService] sweep: scenarios (a) and (b) from the
 * implementation task -- a decline reopens the row and a resumed sweep
 * offers a different eligible driver, excluding the decliner; and a
 * named-driver order whose named driver declined is never substituted,
 * reaching `UNFULFILLED` at window expiry instead (Proposal.kt's "PIOS does
 * not substitute a random driver for the one a client came to" principle).
 */
class DispatchRequestApplicationServiceRecoveryTest {
    private val requests = InMemoryDispatchRequestRepository()
    private val proposals = InMemoryProposalRepository()
    private val availability = MultiDriverAvailabilityRepository()
    private val outbox = TestOutboxRepository()
    private val proposalService = ProposalApplicationService(proposals, NoOpTransactionRunner, availability, requests)
    private val firstRefusal = FirstRefusalApplicationService(InMemoryPrimaryDriverRepository(), proposalService, availability)
    private val fallback = FallbackDispatchApplicationService(availability, proposalService)

    private fun dispatchService(now: Instant): DispatchRequestApplicationService = DispatchRequestApplicationService(
        requests, proposals, proposalService, firstRefusal, fallback, outbox,
        NoOpTransactionRunner, ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC)
    )

    private fun command(orderId: String, startedAt: Instant, requestedDriverId: String? = null): RouteSubmittedOrderCommand =
        RouteSubmittedOrderCommand(
            orderId = orderId,
            passengerReference = "passenger-1",
            isTest = false,
            explicitDriverIntent = requestedDriverId != null,
            requestedDriverId = requestedDriverId,
            requestedPickupAt = null,
            submittedAt = startedAt
        )

    @Test
    fun `a decline reopens the row and a resumed sweep offers a different eligible driver, excluding the decliner`() {
        val startedAt = Instant.now()
        val orderId = "order-decline-resume"
        val driverA = DriverReference("driver-a")
        val driverB = DriverReference("driver-b")
        availability.upsert(DriverAvailabilityRecord(driverA, available = true, isTest = false))
        availability.upsert(DriverAvailabilityRecord(driverB, available = true, isTest = false))

        dispatchService(startedAt).routeSubmitted(command(orderId, startedAt))

        val order = OrderReference(orderId)
        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate(orderId)?.state)
        val firstProposal = proposals.findByOrder(order).single()
        assertEquals(driverA, firstProposal.driver, "the longest-idle available driver (A, inserted first) receives the first offer")

        proposalService.declineProposal(firstProposal, DeclineProposalCommand(firstProposal.id))
        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate(orderId)?.state)

        val dueCount = dispatchService(startedAt.plusSeconds(5)).retryDue()

        assertEquals(1, dueCount)
        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate(orderId)?.state)
        val proposalsForOrder = proposals.findByOrder(order)
        assertEquals(2, proposalsForOrder.size, "the declined proposal, plus exactly one resumed offer")
        val secondProposal = proposalsForOrder.single { it.id != firstProposal.id }
        assertEquals(driverB, secondProposal.driver, "driver A (the decliner) must be excluded from the resumed attempt")
        assertEquals(ProposalStatus.OPEN, secondProposal.status)

        // The window does not restart (ADR-078 Decision B): submitted_at/expires_at are untouched by the reopen.
        val reopened = requests.findForUpdate(orderId)
        assertEquals(startedAt, reopened?.submittedAt)
        assertEquals(startedAt.plusSeconds(120), reopened?.expiresAt)
    }

    @Test
    fun `a decline when the named driver was the one who declined does not retry with a substitute -- the order proceeds to UNFULFILLED at window expiry`() {
        val startedAt = Instant.now()
        val orderId = "order-named-decline"
        val driverA = DriverReference("driver-a")
        val driverB = DriverReference("driver-b")
        availability.upsert(DriverAvailabilityRecord(driverA, available = true, isTest = false))
        availability.upsert(DriverAvailabilityRecord(driverB, available = true, isTest = false))

        dispatchService(startedAt).routeSubmitted(command(orderId, startedAt, requestedDriverId = driverA.driverId))

        val order = OrderReference(orderId)
        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate(orderId)?.state)
        val namedProposal = proposals.findByOrder(order).single()
        assertEquals(driverA, namedProposal.driver)

        proposalService.declineProposal(namedProposal, DeclineProposalCommand(namedProposal.id))
        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate(orderId)?.state)

        // A resumed sweep, comfortably inside the still-open window, must
        // not substitute driver B for the passenger's own named choice.
        dispatchService(startedAt.plusSeconds(5)).retryDue()
        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate(orderId)?.state)
        assertEquals(1, proposals.findByOrder(order).size, "no substitute driver is ever offered for a named-driver order")

        // Past the 2-minute window, the order proceeds to UNFULFILLED -- never a second offer.
        dispatchService(startedAt.plusSeconds(125)).retryDue()
        assertEquals(DispatchRequestState.UNFULFILLED, requests.findForUpdate(orderId)?.state)
        assertEquals(1, proposals.findByOrder(order).size)
        assertEquals(1, outbox.records.size)
    }
}

/**
 * Scenario (c) from the implementation task: a lapse reopens the row
 * (Decision A applies unconditionally), but the real, unchanged defaults --
 * a 5-minute lapse timeout against a 2-minute routing window -- mean the
 * window has always already expired by the time a lapse can fire, so the
 * very next sweep reaches `UNFULFILLED` directly and never makes a second
 * offer. This is confirmed here by actually running both real timeout
 * values against each other, not merely asserted in prose (ADR-078,
 * "One timing fact that changes the shape of this ADR").
 */
class ProposalLapseRoutingWindowTimingTest {
    private val requests = InMemoryDispatchRequestRepository()
    private val proposals = InMemoryProposalRepository()
    private val availability = MultiDriverAvailabilityRepository()
    private val outbox = TestOutboxRepository()
    private val proposalService = ProposalApplicationService(proposals, NoOpTransactionRunner, availability, requests)
    private val firstRefusal = FirstRefusalApplicationService(InMemoryPrimaryDriverRepository(), proposalService, availability)
    private val fallback = FallbackDispatchApplicationService(availability, proposalService)
    private val lapseService = ProposalLapseApplicationService(proposals, proposalService, timeoutMinutes = 5)

    private fun dispatchService(now: Instant): DispatchRequestApplicationService = DispatchRequestApplicationService(
        requests, proposals, proposalService, firstRefusal, fallback, outbox,
        NoOpTransactionRunner, ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC)
    )

    @Test
    fun `a lapse reopens the row, but always reaches UNFULFILLED directly -- the 5-minute lapse timeout leaves no time inside the 2-minute window`() {
        val startedAt = Instant.now()
        val orderId = "order-lapse-timing"
        val driverA = DriverReference("driver-a")
        availability.upsert(DriverAvailabilityRecord(driverA, available = true, isTest = false))

        dispatchService(startedAt).routeSubmitted(
            RouteSubmittedOrderCommand(
                orderId = orderId,
                passengerReference = "passenger-1",
                isTest = false,
                explicitDriverIntent = false,
                requestedDriverId = null,
                requestedPickupAt = null,
                submittedAt = startedAt
            )
        )
        val order = OrderReference(orderId)
        val proposal = proposals.findByOrder(order).single()
        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate(orderId)?.state)

        // The timing fact itself, confirmed with the real, unchanged
        // configuration values (ADR-078 does not authorize changing
        // either): a proposal cannot lapse before minute 5, but the
        // routing window closes at minute 2.
        val lapseFiresAt = startedAt.plusSeconds(5 * 60 + 1)
        assertTrue(
            lapseFiresAt.isAfter(startedAt.plusSeconds(120)),
            "sanity: the 5-minute lapse timeout default fires strictly after the 2-minute routing window has already closed"
        )

        lapseService.lapseStaleProposals(now = lapseFiresAt)
        assertEquals(ProposalStatus.LAPSED, proposals.findById(proposal.id)?.status)
        // Decision A reopens the row unconditionally, even though this
        // particular reopen can never produce a second offer.
        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate(orderId)?.state)

        // The very next sweep, evaluated at/after that same elapsed point, finds the window already expired.
        val dueCount = dispatchService(lapseFiresAt.plusSeconds(1)).retryDue()

        assertEquals(1, dueCount)
        assertEquals(DispatchRequestState.UNFULFILLED, requests.findForUpdate(orderId)?.state)
        assertEquals(1, outbox.records.size)
        assertEquals(1, proposals.findByOrder(order).size, "never a second offer")
    }
}
