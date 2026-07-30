package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId

/**
 * The Arrive Assignment command (ADR-040, Assignment Ride Lifecycle).
 * Represents a driver's report that they have reached the passenger.
 * Mirrors [AcceptAssignmentCommand]'s own shape and scope exactly: it does
 * not itself decide whether the report is valid — that remains the
 * Assignment aggregate's own decision.
 */
data class ArriveAssignmentCommand(
    val assignmentId: AssignmentId
)
