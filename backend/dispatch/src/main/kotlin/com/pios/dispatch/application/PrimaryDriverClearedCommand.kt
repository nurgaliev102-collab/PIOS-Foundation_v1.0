package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.PrimaryConnectionEventListener`)
 * translates a received `PrimaryConnectionCleared` message into, before
 * calling [PrimaryDriverProjectionApplicationService]. Carries only
 * [eventId] and [passengerReference] -- clearing means "this passenger no
 * longer has a primary driver," which needs no driver identity at all.
 */
data class PrimaryDriverClearedCommand(
    val eventId: String,
    val passengerReference: String
)
