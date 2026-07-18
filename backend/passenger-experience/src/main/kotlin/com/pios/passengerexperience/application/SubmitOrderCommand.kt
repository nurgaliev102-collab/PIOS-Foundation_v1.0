package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.PassengerReference

/**
 * The Submit Order command as received from a passenger or corporate
 * customer through Passenger Experience (DOMAIN_MODEL.md Section 9,
 * "Submit Order"; UC-001, "Request Transportation"). Represents
 * [passenger]'s own request; it does not itself submit an order — that
 * remains Order Management's exclusive responsibility. This command only
 * carries Passenger Experience's own originating-context representation
 * forward.
 */
data class SubmitOrderCommand(val passenger: PassengerReference)
