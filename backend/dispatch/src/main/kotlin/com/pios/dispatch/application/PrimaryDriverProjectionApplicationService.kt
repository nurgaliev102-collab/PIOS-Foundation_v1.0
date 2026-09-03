package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for consuming `PrimaryConnectionDesignated`
 * and `PrimaryConnectionCleared` (ADR-062; Task 14, First Refusal
 * Foundation), following [DriverAvailabilityProjectionApplicationService]'s
 * own exact precedent. Never decides a First Refusal outcome itself; this
 * service only maintains Dispatch's own local `PrimaryDriverRecord`
 * projection.
 *
 * [handleDesignated]/[handleCleared] each record their own command's
 * [eventId] and apply the local projection effect together, inside one
 * [transactionRunner] boundary -- so the two either both commit or both
 * roll back. If an [eventId] has already been recorded (a redelivery of
 * an event already handled, RabbitMQ being an at-least-once broker), the
 * local effect is skipped entirely: the business effect of a given event
 * occurs exactly once, no matter how many times it is delivered. Both
 * commands share one idempotency ledger
 * ([PrimaryDriverRepository.markProcessed]) keyed by `eventId` alone --
 * event ids are globally unique regardless of event type, so one ledger
 * safely covers both.
 *
 * [primaryDriverRepository] has no default, for the identical reason
 * [DriverAvailabilityProjectionApplicationService]'s own repository
 * parameter has none: a Kotlin default here would let Spring silently
 * fall back to a no-op whenever no real bean can be resolved,
 * acknowledging every consumed message while never actually persisting
 * anything. A production context missing a real
 * [PrimaryDriverRepository] bean must fail to start instead.
 */
@Service
class PrimaryDriverProjectionApplicationService(
    private val primaryDriverRepository: PrimaryDriverRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    fun handleDesignated(command: PrimaryDriverDesignatedCommand) = transactionRunner.run {
        val isNewEvent = primaryDriverRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            primaryDriverRepository.upsert(
                PrimaryDriverRecord(
                    passengerReference = PassengerReference(command.passengerReference),
                    primaryDriverId = DriverReference(command.driverId)
                )
            )
        }
    }

    fun handleCleared(command: PrimaryDriverClearedCommand) = transactionRunner.run {
        val isNewEvent = primaryDriverRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            primaryDriverRepository.clear(PassengerReference(command.passengerReference))
        }
    }
}
