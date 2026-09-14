package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for consuming `ConnectionEstablished` and
 * `ConnectionRemoved` (ADR-068, Relationship-Ordered Fallback Dispatch —
 * Trusted → Network → Open Marketplace, Part 1), following
 * [PrimaryDriverProjectionApplicationService]'s own exact precedent. Never
 * decides a Fallback Dispatch outcome itself; this service only maintains
 * Dispatch's own local `TrustedDriverRecord` projection.
 *
 * [handleEstablished]/[handleRemoved] each record their own command's
 * [eventId] and apply the local projection effect together, inside one
 * [transactionRunner] boundary -- so the two either both commit or both
 * roll back. If an [eventId] has already been recorded (a redelivery of
 * an event already handled, RabbitMQ being an at-least-once broker), the
 * local effect is skipped entirely. Both commands share one idempotency
 * ledger ([TrustedDriverRepository.markProcessed]) keyed by `eventId`
 * alone -- event ids are globally unique regardless of event type, one
 * ledger safely covers both, mirroring
 * [PrimaryDriverProjectionApplicationService]'s own identical reasoning.
 *
 * [trustedDriverRepository] has no default, for the identical reason
 * [PrimaryDriverProjectionApplicationService]'s own repository parameter
 * has none: a Kotlin default here would let Spring silently fall back to
 * a no-op whenever no real bean can be resolved, acknowledging every
 * consumed message while never actually persisting anything. A production
 * context missing a real [TrustedDriverRepository] bean must fail to
 * start instead.
 */
@Service
class TrustedDriverProjectionApplicationService(
    private val trustedDriverRepository: TrustedDriverRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    fun handleEstablished(command: TrustedDriverEstablishedCommand) = transactionRunner.run {
        val isNewEvent = trustedDriverRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            trustedDriverRepository.add(
                TrustedDriverRecord(
                    passengerReference = PassengerReference(command.passengerReference),
                    driverId = DriverReference(command.driverId)
                )
            )
        }
    }

    fun handleRemoved(command: TrustedDriverRemovedCommand) = transactionRunner.run {
        val isNewEvent = trustedDriverRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            trustedDriverRepository.remove(
                PassengerReference(command.passengerReference),
                DriverReference(command.driverId)
            )
        }
    }
}
