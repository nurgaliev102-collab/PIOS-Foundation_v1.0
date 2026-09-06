package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * application-layer coordination for consuming `AssignmentCompleted` to
 * grow a driver's own milestone counts (total completed rides, consecutive
 * weeks with at least one ride, and — Phase 2 extension, Section 2.1's own
 * disclosed gap, now closed — repeat clients) — the numbers behind the
 * streak badge and ride count already shipped, as static mock data, on the
 * "Мой бизнес" design exploration; this wires them to something real.
 *
 * Mirrors Order Management's own `AssignmentCompletedApplicationService`
 * idempotency shape exactly: mark the eventId processed first, and only
 * apply the business effect if this is genuinely the first time this
 * eventId has been seen. Unlike that service, there is no order-status
 * branch here — Driver Management has no notion of Order's own lifecycle,
 * only (via [orderPassengerRepository]) which passenger a given order was
 * for.
 *
 * The repeat-client step is best-effort: if no passenger has been recorded
 * yet for [AssignmentCompletedUpdateCommand.orderId] (this order's own
 * `OrderSubmitted` has not been consumed yet, or predates this feature),
 * this is logged and skipped — the ride/streak counts above are still
 * recorded regardless, exactly the same disclosed-gap tolerance
 * `OrderSubmittedFirstRefusalListener` (Dispatch) already accepts for its
 * own, unrelated purpose.
 *
 * Runs inside one [transactionRunner] boundary so an uncaught exception
 * also rolls back the eventId's own idempotency mark — a genuinely failed
 * attempt is never mistaken for an already-handled duplicate on retry.
 */
@Service
class AssignmentCompletedApplicationService(
    private val assignmentCompletedRepository: AssignmentCompletedRepository,
    private val driverMilestonesRepository: DriverMilestonesRepository,
    private val orderPassengerRepository: OrderPassengerRepository,
    private val driverClientsRepository: DriverClientsRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    private val logger = LoggerFactory.getLogger(AssignmentCompletedApplicationService::class.java)

    fun handle(command: AssignmentCompletedUpdateCommand) = transactionRunner.run {
        val isNewEvent = assignmentCompletedRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            val driverId = DriverId(command.driverId)
            driverMilestonesRepository.recordCompletedRide(driverId, command.occurredAt)

            val passengerReference = orderPassengerRepository.findPassengerReference(command.orderId)
            if (passengerReference != null) {
                val becameRepeatClient = driverClientsRepository.recordRideForClient(driverId, passengerReference)
                if (becameRepeatClient) {
                    driverMilestonesRepository.incrementRepeatClientsCount(driverId)
                }
            } else {
                logger.warn(
                    "No known passenger for order {} while recording completed ride for driver {} " +
                        "(eventId {}) -- repeat-client count not updated for this ride",
                    command.orderId,
                    command.driverId,
                    command.eventId
                )
            }
        }
    }
}
