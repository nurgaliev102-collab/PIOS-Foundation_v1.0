package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.OrderSubmissionIntent
import com.pios.passengerexperience.domain.OrderSubmissionRequested
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Submit Order command, as
 * received through Passenger Experience (APPLICATION_ARCHITECTURE.md
 * Section 6). Sequences the command into an [OrderSubmissionIntent] and
 * raises the [OrderSubmissionRequested] representation from it. Contains
 * no persistence (out of scope) and no dependency on Order Management —
 * Order Management's own Submit Order handling and OrderSubmitted event
 * remain entirely its own responsibility.
 */
@Service
class PassengerOrderSubmissionApplicationService {

    fun handle(command: SubmitOrderCommand): OrderSubmissionRequested {
        val intent = OrderSubmissionIntent(passenger = command.passenger)
        return intent.raise()
    }
}
