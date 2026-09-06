package com.pios.drivermanagement.application

import org.springframework.stereotype.Service

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): application-layer
 * coordination for consuming `OrderSubmitted` purely to learn which
 * passenger an order belongs to, ahead of the `AssignmentCompleted` that
 * will eventually reference the same [OrderSubmittedUpdateCommand.orderId]
 * once a driver completes it.
 *
 * Idempotency mirrors [AssignmentCompletedApplicationService]'s own shape:
 * mark the eventId processed first (reusing the same
 * `driver_management_processed_events` ledger — event-type-agnostic,
 * keyed only on the globally unique `eventId`, per that ledger's own KDoc),
 * and only record the order→passenger mapping the first time a given
 * eventId is seen.
 *
 * Runs inside one [transactionRunner] boundary so an uncaught exception
 * also rolls back the eventId's own idempotency mark.
 */
@Service
class OrderSubmittedApplicationService(
    private val orderSubmittedRepository: OrderSubmittedRepository,
    private val orderPassengerRepository: OrderPassengerRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: OrderSubmittedUpdateCommand) = transactionRunner.run {
        val isNewEvent = orderSubmittedRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            orderPassengerRepository.recordOrderPassenger(command.orderId, command.passengerReference)
        }
    }
}
