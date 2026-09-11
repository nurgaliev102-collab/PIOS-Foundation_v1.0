package com.pios.core.application

import java.time.Instant

/**
 * The primitive-typed command for a received `AssignmentCompleted`
 * message (dispatch, legacy routing key `assignment.completed` — ADR-067
 * binds the legacy key, not `trip.*`). The `assignment.completed`
 * envelope's payload carries `orderId`, `driverId`, and `statedPrice`
 * (verified against `DispatchAssignmentApplicationService.outboxRecordFor`).
 *
 * Slice 01 records exactly one fact from this event —
 * `RIDE_COMPLETED_AS_DRIVER` for [driverReference] — so the command
 * carries [eventId], [driverReference], [orderReference] (as a bare
 * correlation reference on that fact), and [occurredAt]. `statedPrice` is
 * deliberately not carried: it is a price, not a participant-history
 * fact, and copying it would be denormalised aggregate data (ADR-067
 * Data Model Scope).
 */
data class AssignmentCompletedRecordCommand(
    val eventId: String,
    val driverReference: String,
    val orderReference: String,
    val occurredAt: Instant
)
