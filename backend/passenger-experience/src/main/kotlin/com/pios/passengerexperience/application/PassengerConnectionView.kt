package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection

/**
 * A [connection] paired with whether it is currently the passenger's
 * [isPrimary] designation (ADR-054, Circle of Trust) -- the shape
 * [RetrieveConnectionsForPassengerHandler] returns, since neither fact
 * alone answers the passenger-side "list my circle" question.
 */
data class PassengerConnectionView(val connection: Connection, val isPrimary: Boolean)
