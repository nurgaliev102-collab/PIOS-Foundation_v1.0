package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId

/**
 * The Start Assignment command (ADR-040, Assignment Ride Lifecycle).
 * Represents a driver's report that the ride itself has begun. Mirrors
 * [AcceptAssignmentCommand]'s own shape and scope exactly.
 */
data class StartAssignmentCommand(
    val assignmentId: AssignmentId
)
