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

class PrimaryConnectionDesignatedListenerTest {

    private lateinit var history: InMemoryParticipantHistoryRepository
    private lateinit var listener: PrimaryConnectionDesignatedListener

    @BeforeTest
    fun setUp() {
        history = InMemoryParticipantHistoryRepository()
        val service = ParticipantHistoryProjectionApplicationService(
            InMemoryProcessedEventRepository(), history, NoOpTransactionRunner
        )
        listener = PrimaryConnectionDesignatedListener(service, ObjectMapper())
    }

    private fun envelope(eventType: String, payload: String): String =
        """{"eventId":"evt-${java.util.UUID.randomUUID()}","eventType":"$eventType","eventVersion":1,"occurredAt":"2026-09-10T10:00:00Z","payload":$payload}"""

    @Test
    fun `it records both participant facts from one designated event`() {
        listener.onMessage(envelope("PrimaryConnectionDesignated", """{"passengerReference":"identity-P","driverId":"d1"}"""))
        assertEquals(HistoryEventKind.PRIMARY_DRIVER_DESIGNATED, history.history(ParticipantReference("identity-P")).single().kind)
        assertEquals(HistoryEventKind.DESIGNATED_AS_PRIMARY_DRIVER, history.history(ParticipantReference("d1")).single().kind)
    }

    @Test
    fun `PrimaryConnectionCleared is rejected - a Reserved Future Input`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("PrimaryConnectionCleared", """{"passengerReference":"identity-P"}"""))
        }
    }

    @Test
    fun `a missing passengerReference or driverId is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("PrimaryConnectionDesignated", """{"driverId":"d1"}"""))
        }
        assertFailsWith<IllegalArgumentException> {
            listener.onMessage(envelope("PrimaryConnectionDesignated", """{"passengerReference":"identity-P"}"""))
        }
    }
}
