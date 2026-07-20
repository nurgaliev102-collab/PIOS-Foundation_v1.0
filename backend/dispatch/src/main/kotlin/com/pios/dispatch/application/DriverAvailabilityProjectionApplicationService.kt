package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for consuming DriverAvailabilityChanged
 * (Dispatch Consumer Foundation v1.0; INTERFACE_CONTRACTS.md Section 5:
 * "Make a driver's current availability known so Dispatch can determine
 * an assignment"). Never decides an assignment itself (ADR-002); this
 * service only maintains Dispatch's own local availability projection.
 *
 * [handle] records [DriverAvailabilityUpdateCommand.eventId] and applies
 * the local availability effect together, inside one [transactionRunner]
 * boundary (ADR-032, applying the pattern already proven in Order
 * Management's and Driver Management's own outbox work) -- so the two
 * either both commit or both roll back. If [eventId][DriverAvailabilityUpdateCommand.eventId]
 * has already been recorded (a redelivery of an event already handled,
 * RabbitMQ being an at-least-once broker per ADR-029/ADR-031), the local
 * availability effect is skipped entirely: the business effect of a given
 * event occurs exactly once, no matter how many times it is delivered.
 *
 * [driverAvailabilityRepository] has no default, unlike [transactionRunner]:
 * a Kotlin default here would let Spring silently fall back to a no-op
 * whenever no real [DriverAvailabilityRepository] bean can be resolved,
 * acknowledging every consumed message while never actually persisting
 * anything -- the same silent-data-loss risk already identified and fixed
 * for [com.pios.dispatch.persistence] "RabbitMQ Event Publishing
 * Foundation v1.0 Hardening Review"'s OutboxRelay, applied here from the
 * start. Requiring the argument means a production context missing a
 * real repository bean fails to start with a clear
 * `NoSuchBeanDefinitionException` instead.
 */
@Service
class DriverAvailabilityProjectionApplicationService(
    private val driverAvailabilityRepository: DriverAvailabilityRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    fun handle(command: DriverAvailabilityUpdateCommand) = transactionRunner.run {
        val isNewEvent = driverAvailabilityRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            driverAvailabilityRepository.upsert(
                DriverAvailabilityRecord(
                    driverReference = DriverReference(command.driverReference),
                    available = command.available
                )
            )
        }
    }
}
