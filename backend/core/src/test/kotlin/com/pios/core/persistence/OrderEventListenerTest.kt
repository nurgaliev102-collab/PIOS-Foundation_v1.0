package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import com.pios.core.application.NoOpTransactionRunner
import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Transport-boundary unit tests for [OrderEventListener]: envelope
 * validation, `eventType` branching, payload extraction, and — critically
 * — rejection of every event type that is NOT one of the three authorized
 * order events (Reserved Future Inputs and `DriverAvailabilityChanged`
 * included). No broker, no Spring — the listener is called directly with
 * envelope strings, mirroring dispatch's own listener unit tests.
 */
class OrderEventListenerTest {

    private lateinit var history: InMemoryParticipantHistoryRepository
    private lateinit var listener: OrderEventListener

    @BeforeTest
    fun setUp() {
        history = InMemoryParticipantHistoryRepository()
        val service = ParticipantHistoryProjectionApplicationService(
            InMemoryProcessedEventRepository(), history, NoOpTransactionRunner
        )
        listener = OrderEventListener(service, ObjectMapper())
    }

    private fun envelope(
        eventType: String,
        eventVersion: Any = 1,
        payload: String,
        eventId: String = "evt-" + java.util.UUID.randomUUID()
    ): String =
        """{"eventId":"$eventId","eventType":"$eventType","eventVersion":$eventVersion,"occurredAt":"2026-09-10T10:00:00Z","payload":$payload}"""

    @Test
    fun `OrderSubmitted envelope extracts passengerReference and orderId only`() {
        listener.onMessage(
            envelope("OrderSubmitted", payload = """{"orderId":"o1","passengerReference":"identity-P","explicitDriverIntent":"x","isTest":true}""")
        )
        val e = history.history(ParticipantReference("identity-P")).single()
        assertEquals(HistoryEventKind.ORDER_SUBMITTED, e.kind)
        assertEquals("o1", e.orderReference)
    }

    @Test
    fun `OrderCompleted envelope needs only orderId`() {
        listener.onMessage(envelope("OrderSubmitted", payload = """{"orderId":"o1","passengerReference":"identity-P"}"""))
        listener.onMessage(envelope("OrderCompleted", payload = """{"orderId":"o1"}"""))
        assertEquals(HistoryEventKind.ORDER_COMPLETED, history.history(ParticipantReference("identity-P"))[1].kind)
    }

    @Test
    fun `a DriverAvailabilityChanged message is rejected - Slice 01 does not consume it`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("DriverAvailabilityChanged", payload = """{"driverId":"d1","availability":"AVAILABLE"}"""))
        }
    }

    @Test
    fun `a Reserved Future Input event type is rejected`() {
        listOf("OrderAssigned", "OrderProposed", "ProposalAccepted", "TripCompleted", "AssignmentArrived")
            .forEach { reserved ->
                assertFailsWith<IllegalArgumentException>("$reserved must be rejected") {
                    listener.onMessage(envelope(reserved, payload = """{"orderId":"o1"}"""))
                }
            }
    }

    @Test
    fun `an unsupported eventVersion is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("OrderSubmitted", eventVersion = 2, payload = """{"orderId":"o1","passengerReference":"identity-P"}"""))
        }
    }

    @Test
    fun `a missing payload orderId is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("OrderCancelled", payload = """{}"""))
        }
    }

    @Test
    fun `a missing occurredAt is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage("""{"eventId":"e","eventType":"OrderCancelled","eventVersion":1,"payload":{"orderId":"o1"}}""")
        }
    }

    @Test
    fun `an unparseable occurredAt is rejected - never substituted with now`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage("""{"eventId":"e","eventType":"OrderCancelled","eventVersion":1,"occurredAt":"not-a-date","payload":{"orderId":"o1"}}""")
        }
    }
}
