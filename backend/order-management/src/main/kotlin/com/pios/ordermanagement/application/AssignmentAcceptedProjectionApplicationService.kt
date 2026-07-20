package com.pios.ordermanagement.application

import org.springframework.stereotype.Service

/**
 * Application-layer coordination for consuming AssignmentAccepted
 * (Tranche 1: Dispatch Event Publishing Completion; INTERFACE_CONTRACTS.md
 * Section 5: "Make an order's assignment outcome known so Order
 * Management can track the order's status"). Never decides an
 * assignment's own validity itself (ADR-002); this service only recognizes,
 * exactly once, that a specific order's assignment has been confirmed --
 * delegating that recognition to the already-existing
 * [orderAssignmentRecognitionHandler], whose own validation logic is
 * reused unchanged, not rewritten.
 *
 * [handle] records [AssignmentAcceptedUpdateCommand.eventId] and invokes
 * that recognition together, inside one [transactionRunner] boundary
 * (ADR-032, applying the pattern already proven in Dispatch's own
 * `DriverAvailabilityProjectionApplicationService`) -- so the two either
 * both commit or both roll back. If [eventId][AssignmentAcceptedUpdateCommand.eventId]
 * has already been recorded (a redelivery of an event already handled,
 * RabbitMQ being an at-least-once broker per ADR-029/ADR-031), the
 * recognition is skipped entirely: the business effect of a given event
 * occurs exactly once, no matter how many times it is delivered.
 *
 * [assignmentAcceptedRepository] has no default, unlike [transactionRunner]:
 * a Kotlin default here would let Spring silently fall back to a no-op
 * whenever no real [AssignmentAcceptedRepository] bean can be resolved,
 * acknowledging every consumed message while never actually persisting
 * anything -- the same silent-data-loss risk already identified and fixed
 * for Dispatch's own `DriverAvailabilityProjectionApplicationService`,
 * applied here from the start. Requiring the argument means a production
 * context missing a real repository bean fails to start with a clear
 * `NoSuchBeanDefinitionException` instead.
 */
@Service
class AssignmentAcceptedProjectionApplicationService(
    private val assignmentAcceptedRepository: AssignmentAcceptedRepository,
    private val orderAssignmentRecognitionHandler: OrderAssignmentRecognitionHandler,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    fun handle(command: AssignmentAcceptedUpdateCommand) = transactionRunner.run {
        val isNewEvent = assignmentAcceptedRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            orderAssignmentRecognitionHandler.handle(command.orderReference, command.driverReference)
        }
    }
}
