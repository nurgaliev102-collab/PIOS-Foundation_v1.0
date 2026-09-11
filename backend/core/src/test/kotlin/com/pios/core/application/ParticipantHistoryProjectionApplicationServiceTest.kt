package com.pios.core.application

import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantReference
import com.pios.core.persistence.InMemoryParticipantHistoryRepository
import com.pios.core.persistence.InMemoryProcessedEventRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Constructor-based unit tests for the Slice 01 projection (ADR-067). No
 * Spring context, no database, no broker — the project's own convention
 * of constructing collaborators directly. Covers the task's test list
 * items: event → History, idempotency, duplicate delivery, correlation,
 * missing correlation, participant_reference mapping, retry-safe
 * behaviour, and "missing field never invents data".
 */
class ParticipantHistoryProjectionApplicationServiceTest {

    private lateinit var processed: InMemoryProcessedEventRepository
    private lateinit var history: InMemoryParticipantHistoryRepository
    private lateinit var service: ParticipantHistoryProjectionApplicationService

    private val t0 = Instant.parse("2026-09-10T10:00:00Z")

    @BeforeTest
    fun setUp() {
        processed = InMemoryProcessedEventRepository()
        history = InMemoryParticipantHistoryRepository()
        service = ParticipantHistoryProjectionApplicationService(processed, history, NoOpTransactionRunner)
    }

    // ---- OrderSubmitted ----

    @Test
    fun `OrderSubmitted records ORDER_SUBMITTED for the passenger reference`() {
        service.handleOrderSubmitted(
            OrderSubmittedRecordCommand("e1", passengerReference = "identity-P", orderReference = "order-1", occurredAt = t0)
        )

        val events = history.history(ParticipantReference("identity-P"))
        assertEquals(1, events.size)
        assertEquals(HistoryEventKind.ORDER_SUBMITTED, events[0].kind)
        assertEquals("identity-P", events[0].participant.value)
        assertEquals("order-1", events[0].orderReference)
        assertEquals(t0, events[0].occurredAt)
        assertEquals("e1", events[0].sourceEventId)
        assertTrue(history.participantExists(ParticipantReference("identity-P")))
    }

    @Test
    fun `occurredAt is taken verbatim from the event, never Instant now`() {
        val past = Instant.parse("2020-01-01T00:00:00Z")
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("e1", "identity-P", "order-1", past))
        assertEquals(past, history.all().single().occurredAt)
    }

    // ---- Idempotency / duplicate delivery ----

    @Test
    fun `re-delivering the same eventId creates no second History fact`() {
        val cmd = OrderSubmittedRecordCommand("dup-event", "identity-P", "order-1", t0)
        service.handleOrderSubmitted(cmd)
        service.handleOrderSubmitted(cmd)
        service.handleOrderSubmitted(cmd)

        assertEquals(1, history.all().size)
    }

    @Test
    fun `two different events for the same participant both record`() {
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("e1", "identity-P", "order-1", t0))
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("e2", "identity-P", "order-2", t0.plusSeconds(60)))
        assertEquals(2, history.history(ParticipantReference("identity-P")).size)
    }

    // ---- OrderCompleted / OrderCancelled correlation ----

    @Test
    fun `OrderCompleted correlates to the participant of the prior OrderSubmitted`() {
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("s1", "identity-P", "order-1", t0))
        service.handleOrderCompleted(OrderCompletedRecordCommand("c1", "order-1", t0.plusSeconds(600)))

        val events = history.history(ParticipantReference("identity-P"))
        assertEquals(listOf(HistoryEventKind.ORDER_SUBMITTED, HistoryEventKind.ORDER_COMPLETED), events.map { it.kind })
        assertEquals("order-1", events[1].orderReference)
    }

    @Test
    fun `OrderCancelled correlates the same way`() {
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("s1", "identity-P", "order-1", t0))
        service.handleOrderCancelled(OrderCancelledRecordCommand("x1", "order-1", t0.plusSeconds(120)))
        assertEquals(HistoryEventKind.ORDER_CANCELLED, history.history(ParticipantReference("identity-P"))[1].kind)
    }

    @Test
    fun `OrderCompleted with no prior OrderSubmitted marks processed but writes NO history row`() {
        // ADR-067 Input Events "Rule" / task item: missing correlation must
        // not invent data and must not fetch from another DB/API.
        service.handleOrderCompleted(OrderCompletedRecordCommand("orphan", "unknown-order", t0))

        assertEquals(0, history.all().size, "no history row written for an uncorrelated OrderCompleted")
        // ...but it IS recorded as processed, so a redelivery is a no-op:
        assertTrue(!processed.markProcessed("orphan"), "the orphan event was marked processed")
    }

    @Test
    fun `an uncorrelated OrderCancelled behaves the same`() {
        service.handleOrderCancelled(OrderCancelledRecordCommand("orphan-x", "unknown-order", t0))
        assertEquals(0, history.all().size)
        assertTrue(!processed.markProcessed("orphan-x"))
    }

    // ---- AssignmentCompleted ----

    @Test
    fun `AssignmentCompleted records RIDE_COMPLETED_AS_DRIVER for the driver id`() {
        service.handleAssignmentCompleted(
            AssignmentCompletedRecordCommand("a1", driverReference = "driver-9", orderReference = "order-1", occurredAt = t0)
        )
        val events = history.history(ParticipantReference("driver-9"))
        assertEquals(1, events.size)
        assertEquals(HistoryEventKind.RIDE_COMPLETED_AS_DRIVER, events[0].kind)
        assertEquals("driver-9", events[0].participant.value)
        assertEquals("driver-9", events[0].driverReference)
        assertEquals("order-1", events[0].orderReference)
    }

    @Test
    fun `AssignmentCompleted does not record anything for the passenger - that is OrderCompleted's job`() {
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("s1", "identity-P", "order-1", t0))
        service.handleAssignmentCompleted(AssignmentCompletedRecordCommand("a1", "driver-9", "order-1", t0.plusSeconds(600)))

        // passenger history unchanged by the assignment event
        assertEquals(listOf(HistoryEventKind.ORDER_SUBMITTED), history.history(ParticipantReference("identity-P")).map { it.kind })
    }

    // ---- PrimaryConnectionDesignated ----

    @Test
    fun `PrimaryConnectionDesignated records two facts - one per participant`() {
        service.handlePrimaryConnectionDesignated(
            PrimaryConnectionDesignatedRecordCommand("p1", passengerReference = "identity-P", driverReference = "driver-9", occurredAt = t0)
        )

        assertEquals(
            listOf(HistoryEventKind.PRIMARY_DRIVER_DESIGNATED),
            history.history(ParticipantReference("identity-P")).map { it.kind }
        )
        assertEquals(
            listOf(HistoryEventKind.DESIGNATED_AS_PRIMARY_DRIVER),
            history.history(ParticipantReference("driver-9")).map { it.kind }
        )
        assertTrue(history.participantExists(ParticipantReference("identity-P")))
        assertTrue(history.participantExists(ParticipantReference("driver-9")))
    }

    @Test
    fun `re-delivering PrimaryConnectionDesignated does not double either fact`() {
        val cmd = PrimaryConnectionDesignatedRecordCommand("p1", "identity-P", "driver-9", t0)
        service.handlePrimaryConnectionDesignated(cmd)
        service.handlePrimaryConnectionDesignated(cmd)
        assertEquals(2, history.all().size)
    }

    // ---- participant_reference mapping ----

    @Test
    fun `passenger-sourced facts key on identityId, driver-sourced facts key on driver id - not unified`() {
        // ADR-067 Explicit Non-Decisions: the two id spaces are NOT reconciled.
        service.handleOrderSubmitted(OrderSubmittedRecordCommand("s1", "identity-P", "order-1", t0))
        service.handleAssignmentCompleted(AssignmentCompletedRecordCommand("a1", "driver-9", "order-1", t0.plusSeconds(600)))

        assertTrue(history.participantExists(ParticipantReference("identity-P")))
        assertTrue(history.participantExists(ParticipantReference("driver-9")))
        assertEquals(1, history.history(ParticipantReference("identity-P")).size)
        assertEquals(1, history.history(ParticipantReference("driver-9")).size)
    }

    // ---- chronological ordering ----

    @Test
    fun `history for a participant is returned oldest-first`() {
        val p = "identity-P"
        service.handleOrderSubmitted(OrderSubmittedRecordCommand(UUID.randomUUID().toString(), p, "order-2", t0.plusSeconds(200)))
        service.handleOrderSubmitted(OrderSubmittedRecordCommand(UUID.randomUUID().toString(), p, "order-1", t0.plusSeconds(100)))
        assertEquals(
            listOf("order-1", "order-2"),
            history.history(ParticipantReference(p)).map { it.orderReference }
        )
    }
}
