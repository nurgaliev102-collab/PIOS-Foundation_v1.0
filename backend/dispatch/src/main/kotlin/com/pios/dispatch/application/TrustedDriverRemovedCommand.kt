package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.TrustedDriverEventListener`) translates
 * a received `ConnectionRemoved` message into, before calling
 * [TrustedDriverProjectionApplicationService]. Carries [eventId],
 * [passengerReference], and [driverId] -- unlike [PrimaryDriverClearedCommand],
 * removal from a *set* must identify which member left it.
 */
data class TrustedDriverRemovedCommand(
    val eventId: String,
    val passengerReference: String,
    val driverId: String
)
