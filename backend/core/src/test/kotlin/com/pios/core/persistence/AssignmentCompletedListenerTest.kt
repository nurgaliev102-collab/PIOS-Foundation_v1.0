package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.NoOpTransactionRunner
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssignmentCompletedListenerTest {

    private lateinit var history: InMemoryParticipantHistoryRepository
    private lateinit var listener: AssignmentCompletedListener

    @BeforeTest
    fun setUp() {
        history = InMemoryParticipantHistoryRepository()
        val service = ParticipantHistoryProjectionApplicationService(
            InMemoryProcessedEventRepository(), history, NoOpTransactionRunner
        )
        listener = AssignmentCompletedListener(service, ObjectMapper())
    }

    private fun envelope(eventType: String, payload: String): String =
        """{"eventId":"evt-${java.util.UUID.randomUUID()}","eventType":"$eventType","eventVersion":1,"occurredAt":"2026-09-10T10:00:00Z","payload":$payload}"""

    @Test
    fun `AssignmentCompleted records RIDE_COMPLETED_AS_DRIVER for driverId, ignores statedPrice`() {
        listener.onMessage(envelope("AssignmentCompleted", """{"orderId":"o1","driverId":"d1","statedPrice":"1500"}"""))
        val e = history.history(ParticipantReference("d1")).single()
        assertEquals(HistoryEventKind.RIDE_COMPLETED_AS_DRIVER, e.kind)
        assertEquals("d1", e.driverReference)
        assertEquals("o1", e.orderReference)
    }

    @Test
    fun `a statedPrice-free payload is still accepted`() {
        listener.onMessage(envelope("AssignmentCompleted", """{"orderId":"o1","driverId":"d1","statedPrice":null}"""))
        assertEquals(1, history.all().size)
    }

    @Test
    fun `Trip and Assignment progress event types are rejected - Reserved Future Inputs`() {
        listOf("TripCompleted", "AssignmentArrived", "AssignmentStarted", "AssignmentAccepted")
            .forEach { reserved ->
                assertFailsWith<IllegalArgumentException>("$reserved must be rejected") {
                    listener.onMessage(envelope(reserved, """{"orderId":"o1","driverId":"d1"}"""))
                }
            }
    }

    @Test
    fun `a missing driverId is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("AssignmentCompleted", """{"orderId":"o1"}"""))
        }
    }
}
