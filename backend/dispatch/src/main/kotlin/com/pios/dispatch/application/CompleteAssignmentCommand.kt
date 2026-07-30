package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId

/**
 * The Complete Assignment command (ADR-040, Assignment Ride Lifecycle).
 * Represents a driver's report that the ride has finished. Mirrors
 * [AcceptAssignmentCommand]'s own shape and scope exactly.
 */
data class CompleteAssignmentCommand(
    val assignmentId: AssignmentId
)
