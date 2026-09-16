package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchRequestApplicationServiceTest {
    private val startedAt: Instant = Instant.parse("2026-09-16T12:00:00Z")
    private val requests = InMemoryDispatchRequestRepository()
    private val proposals = InMemoryProposalRepository()
    private val availability = TestAvailabilityRepository()
    private val outbox = TestOutboxRepository()

    @Test
    fun `an order without a candidate remains pending and is offered after availability arrives`() {
        service(startedAt).routeSubmitted(command("order-retry"))
        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate("order-retry")?.state)
        assertTrue(proposals.findByOrder(OrderReference("order-retry")).isEmpty())

        availability.driver = DriverReference("driver-now-ready")
        assertEquals(1, service(startedAt.plusSeconds(5)).retryDue())
        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate("order-retry")?.state)
        assertEquals("driver-now-ready", proposals.findByOrder(OrderReference("order-retry")).single().driver.driverId)
        assertTrue(outbox.records.isEmpty())
    }

    @Test
    fun `an order with no proposal expires once and emits one durable exhaustion fact`() {
        service(startedAt).routeSubmitted(command("order-expired"))
        service(startedAt.plusSeconds(125)).retryDue()
        service(startedAt.plusSeconds(130)).retryDue()

        assertEquals(DispatchRequestState.UNFULFILLED, requests.findForUpdate("order-expired")?.state)
        assertEquals(1, outbox.records.size)
        assertEquals("dispatch.exhausted", outbox.records.single().routingKey)
        assertTrue(outbox.records.single().payload.contains("order-expired"))
        assertFalse(requests.findDueOrderIds(startedAt.plusSeconds(130), 100).contains("order-expired"))
    }

    @Test
    fun `a cancelled order cannot be routed when cancellation arrives first`() {
        requests.markCancelled("order-cancelled")
        service(startedAt).routeSubmitted(command("order-cancelled"))

        assertTrue(proposals.findByOrder(OrderReference("order-cancelled")).isEmpty())
        assertTrue(requests.cancelledOrderIds.contains("order-cancelled"))
        assertTrue(outbox.records.isEmpty())
    }

    @Test
    fun `a future named-driver order can be offered while that driver is currently offline`() {
        availability.driver = DriverReference("driver-offline")
        availability.isAvailable = false
        service(startedAt).routeSubmitted(
            command("order-future").copy(
                explicitDriverIntent = true,
                requestedDriverId = "driver-offline",
                requestedPickupAt = Instant.parse("2099-09-16T13:00:00Z")
            )
        )

        assertEquals(DispatchRequestState.OFFERED, requests.findForUpdate("order-future")?.state)
        assertEquals("driver-offline", proposals.findByOrder(OrderReference("order-future")).single().driver.driverId)
    }

    @Test
    fun `legacy explicit intent without a driver ID never falls back to a stranger and closes visibly`() {
        availability.driver = DriverReference("unrelated-driver")
        service(startedAt).routeSubmitted(command("order-legacy-intent").copy(explicitDriverIntent = true))

        assertEquals(DispatchRequestState.PENDING, requests.findForUpdate("order-legacy-intent")?.state)
        assertTrue(proposals.findByOrder(OrderReference("order-legacy-intent")).isEmpty())

        service(startedAt.plusSeconds(125)).retryDue()
        assertEquals(DispatchRequestState.UNFULFILLED, requests.findForUpdate("order-legacy-intent")?.state)
        assertTrue(proposals.findByOrder(OrderReference("order-legacy-intent")).isEmpty())
        assertEquals(1, outbox.records.size)
    }

    private fun command(orderId: String): RouteSubmittedOrderCommand = RouteSubmittedOrderCommand(
        orderId = orderId,
        passengerReference = "passenger-one",
        isTest = false,
        explicitDriverIntent = false,
        requestedDriverId = null,
        requestedPickupAt = null,
        submittedAt = startedAt
    )

    private fun service(now: Instant): DispatchRequestApplicationService {
        val proposalService = ProposalApplicationService(proposals, NoOpTransactionRunner, availability, requests)
        val firstRefusal = FirstRefusalApplicationService(InMemoryPrimaryDriverRepository(), proposalService, availability)
        val fallback = FallbackDispatchApplicationService(availability, proposalService)
        return DispatchRequestApplicationService(
            requests, proposals, proposalService, firstRefusal, fallback, outbox,
            NoOpTransactionRunner, ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    private class TestAvailabilityRepository : DriverAvailabilityRepository {
        var driver: DriverReference? = null
        var isAvailable: Boolean = true
        override fun markProcessed(eventId: String): Boolean = true
        override fun upsert(record: DriverAvailabilityRecord) = Unit
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            if (driverReference == driver) DriverAvailabilityRecord(driverReference, isAvailable, isTest = false) else null
        override fun findLongestIdleAvailable(orderIsTest: Boolean, excluding: Set<DriverReference>): DriverReference? =
            driver?.takeIf { isAvailable && !orderIsTest && it !in excluding }
    }

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
}
