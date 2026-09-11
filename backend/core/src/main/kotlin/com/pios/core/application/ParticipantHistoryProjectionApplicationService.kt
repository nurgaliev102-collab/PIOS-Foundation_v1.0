package com.pios.core.application

import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantHistoryEvent
import com.pios.core.domain.ParticipantReference
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for PIOS Core's Slice 01 read-only
 * projection (ADR-067). For each of the five Current Authorized Inputs, a
 * `handle*` method records the event's idempotency marker and writes its
 * history effect together, inside one [transactionRunner] boundary
 * (ADR-032 / ADR-067 Idempotency) — so the two either both commit or both
 * roll back, and a redelivered event (RabbitMQ being at-least-once,
 * ADR-029 / ADR-031) never re-applies its effect.
 *
 * Mirrors dispatch's own `OrderCancelledApplicationService` /
 * `DriverAvailabilityProjectionApplicationService` idempotency shape
 * exactly.
 *
 * **Consumer-only, projection-only (ADR-067 Boundary / Event Boundary /
 * Write Boundary):** this service publishes nothing, writes to no
 * database but `pios_core` (through [participantHistoryRepository] and
 * [processedEventRepository]), and never reads or calls a Taxi module. If
 * an event's payload lacks a value the projection would like — for
 * `OrderCompleted` / `OrderCancelled`, the participant — it records the
 * event as processed and writes **no** history row, and does **not**
 * fetch the value from anywhere (ADR-067 Input Events "Rule").
 *
 * [processedEventRepository] and [participantHistoryRepository] have no
 * default, unlike [transactionRunner]: a Kotlin default here would let
 * Spring silently fall back to a no-op whenever no real bean can be
 * resolved, acknowledging every consumed message while never persisting
 * anything — the same silent-data-loss risk dispatch's own services
 * already guard against. Requiring the arguments means a production
 * context missing a real bean fails to start with a clear
 * `NoSuchBeanDefinitionException`.
 */
@Service
class ParticipantHistoryProjectionApplicationService(
    private val processedEventRepository: ProcessedEventRepository,
    private val participantHistoryRepository: ParticipantHistoryRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    private val logger = LoggerFactory.getLogger(ParticipantHistoryProjectionApplicationService::class.java)

    /** `OrderSubmitted` → `ORDER_SUBMITTED` for the requesting passenger. */
    fun handleOrderSubmitted(command: OrderSubmittedRecordCommand) = transactionRunner.run {
        onFirstDelivery(command.eventId) {
            val participant = ParticipantReference(command.passengerReference)
            participantHistoryRepository.ensureParticipant(participant)
            participantHistoryRepository.record(
                ParticipantHistoryEvent(
                    participant = participant,
                    kind = HistoryEventKind.ORDER_SUBMITTED,
                    occurredAt = command.occurredAt,
                    orderReference = command.orderReference,
                    sourceEventId = command.eventId
                )
            )
        }
    }

    /**
     * `OrderCompleted` → `ORDER_COMPLETED` for the participant a prior
     * `ORDER_SUBMITTED` attributed the order to. Correlation-only: if no
     * such prior row exists, the event is still marked processed but **no**
     * history row is written (ADR-067 — the missing value is not fetched).
     */
    fun handleOrderCompleted(command: OrderCompletedRecordCommand) = transactionRunner.run {
        onFirstDelivery(command.eventId) {
            recordCorrelatedOrderFact(
                orderReference = command.orderReference,
                kind = HistoryEventKind.ORDER_COMPLETED,
                occurredAt = command.occurredAt,
                sourceEventId = command.eventId
            )
        }
    }

    /** `OrderCancelled` → `ORDER_CANCELLED`, same correlation rule as [handleOrderCompleted]. */
    fun handleOrderCancelled(command: OrderCancelledRecordCommand) = transactionRunner.run {
        onFirstDelivery(command.eventId) {
            recordCorrelatedOrderFact(
                orderReference = command.orderReference,
                kind = HistoryEventKind.ORDER_CANCELLED,
                occurredAt = command.occurredAt,
                sourceEventId = command.eventId
            )
        }
    }

    /** `AssignmentCompleted` → `RIDE_COMPLETED_AS_DRIVER` for the driver. */
    fun handleAssignmentCompleted(command: AssignmentCompletedRecordCommand) = transactionRunner.run {
        onFirstDelivery(command.eventId) {
            val driver = ParticipantReference(command.driverReference)
            participantHistoryRepository.ensureParticipant(driver)
            participantHistoryRepository.record(
                ParticipantHistoryEvent(
                    participant = driver,
                    kind = HistoryEventKind.RIDE_COMPLETED_AS_DRIVER,
                    occurredAt = command.occurredAt,
                    orderReference = command.orderReference,
                    driverReference = command.driverReference,
                    sourceEventId = command.eventId
                )
            )
        }
    }

    /**
     * `PrimaryConnectionDesignated` → two facts, one per participant the
     * event concerns (ADR-067 Input Events): `PRIMARY_DRIVER_DESIGNATED`
     * for the passenger and `DESIGNATED_AS_PRIMARY_DRIVER` for the driver.
     * Both writes share the one idempotency marker and the one transaction.
     */
    fun handlePrimaryConnectionDesignated(command: PrimaryConnectionDesignatedRecordCommand) = transactionRunner.run {
        onFirstDelivery(command.eventId) {
            val passenger = ParticipantReference(command.passengerReference)
            val driver = ParticipantReference(command.driverReference)

            participantHistoryRepository.ensureParticipant(passenger)
            participantHistoryRepository.record(
                ParticipantHistoryEvent(
                    participant = passenger,
                    kind = HistoryEventKind.PRIMARY_DRIVER_DESIGNATED,
                    occurredAt = command.occurredAt,
                    driverReference = command.driverReference,
                    sourceEventId = command.eventId
                )
            )

            participantHistoryRepository.ensureParticipant(driver)
            participantHistoryRepository.record(
                ParticipantHistoryEvent(
                    participant = driver,
                    kind = HistoryEventKind.DESIGNATED_AS_PRIMARY_DRIVER,
                    occurredAt = command.occurredAt,
                    driverReference = command.driverReference,
                    sourceEventId = command.eventId
                )
            )
        }
    }

    private fun onFirstDelivery(eventId: String, effect: () -> Unit) {
        val isNewEvent = processedEventRepository.markProcessed(eventId)
        if (isNewEvent) {
            effect()
        }
    }

    private fun recordCorrelatedOrderFact(
        orderReference: String,
        kind: HistoryEventKind,
        occurredAt: java.time.Instant,
        sourceEventId: String
    ) {
        val participant = participantHistoryRepository.findParticipantByOrderReference(orderReference)
        if (participant == null) {
            // ADR-067 Input Events "Rule": Core never saw the matching
            // OrderSubmitted, so it cannot attribute this order to a
            // participant. It does NOT ask order-management. The event is
            // already marked processed (above); no history row is written.
            logger.debug(
                "{} (eventId {}) for order {} could not be correlated to any prior OrderSubmitted; recorded as processed, no history row written",
                kind,
                sourceEventId,
                orderReference
            )
            return
        }
        participantHistoryRepository.ensureParticipant(participant)
        participantHistoryRepository.record(
            ParticipantHistoryEvent(
                participant = participant,
                kind = kind,
                occurredAt = occurredAt,
                orderReference = orderReference,
                sourceEventId = sourceEventId
            )
        )
    }
}
